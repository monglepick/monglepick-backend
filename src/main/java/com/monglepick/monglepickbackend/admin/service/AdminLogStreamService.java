package com.monglepick.monglepickbackend.admin.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 관리자 시스템 로그 SSE 스트리밍 서비스.
 *
 * <p>설계서 §3.5: GET /api/v1/admin/system/logs — 시스템 로그 실시간 스트리밍.
 * Logback 의 root 로거에 in-memory ring buffer 어펜더를 자동 등록하여 최근 로그를 보존하고,
 * SSE 구독자에게 실시간 push 한다.</p>
 *
 * <h3>구조</h3>
 * <ul>
 *   <li>{@link RingBufferAppender} — 최근 N건(기본 500)을 ring buffer 로 보존</li>
 *   <li>{@link CopyOnWriteArrayList} subscribers — 활성 SSE 구독자 리스트</li>
 *   <li>로그 발생 시 ring buffer 에 저장 + 모든 구독자에게 push</li>
 * </ul>
 *
 * <h3>동작</h3>
 * <ol>
 *   <li>{@code @PostConstruct} 시 root 로거에 RingBufferAppender 부착</li>
 *   <li>SSE 구독 요청 시 (1) 누적 버퍼 즉시 전송, (2) 신규 구독자 등록</li>
 *   <li>로그 이벤트 발생 시 모든 구독자에게 SseEmitter.send() 호출</li>
 *   <li>전송 실패 또는 timeout 시 자동 구독 해제</li>
 *   <li>{@code @PreDestroy} 시 어펜더 분리 + 모든 구독자 정리</li>
 * </ol>
 *
 * <h3>주의</h3>
 * <p>버퍼 크기는 500건으로 제한 (메모리 보호). 운영 환경의 로그 폭주 시 ring buffer 가
 * 가장 오래된 로그를 자동 폐기한다. 로그 단일 라인은 최대 1000자로 절단된다.</p>
 */
@Slf4j
@Service
public class AdminLogStreamService {

    /** Ring buffer 최대 보존 라인 수 */
    private static final int BUFFER_SIZE = 500;

    /** 단일 로그 라인 최대 길이 (메모리 보호) */
    private static final int MAX_LINE_LENGTH = 1000;

    /** SSE 타임아웃 — 60분 (관리자가 장시간 화면을 열어둘 수 있음) */
    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;

    /** 활성 SSE 구독자 — 동시 수정 안전한 CopyOnWriteArrayList 사용 */
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    /** Ring buffer 어펜더 인스턴스 — @PostConstruct 에서 root 로거에 부착 */
    private RingBufferAppender appender;

    /** ISO-8601 포맷 (KST 기준) */
    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.of("Asia/Seoul"));

    /**
     * Spring 컨테이너 시작 시 root 로거에 ring buffer 어펜더를 부착한다.
     */
    @PostConstruct
    public void init() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

            // 어펜더 생성 + 시작
            appender = new RingBufferAppender(this);
            appender.setContext(context);
            appender.setName("admin-ring-buffer");
            appender.start();
            rootLogger.addAppender(appender);

            log.info("[AdminLogStream] Ring buffer 어펜더 부착 완료 (size={})", BUFFER_SIZE);
        } catch (Exception e) {
            log.error("[AdminLogStream] 어펜더 부착 실패", e);
        }
    }

    /**
     * Spring 컨테이너 종료 시 어펜더 분리 + 구독자 정리.
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (appender != null) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
                rootLogger.detachAppender(appender);
                appender.stop();
            }
            // 모든 구독자 종료 신호
            for (SseEmitter emitter : subscribers) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 이미 종료된 emitter 는 무시
                }
            }
            subscribers.clear();
        } catch (Exception e) {
            log.warn("[AdminLogStream] 종료 처리 실패", e);
        }
    }

    /**
     * 새 SSE 구독자를 등록하고 emitter 를 반환한다.
     *
     * <p>구독 즉시 ring buffer 의 누적 로그를 한 번에 전송한 뒤, 이후 새로 발생하는
     * 로그 이벤트를 실시간 push 한다. 클라이언트 연결 종료/타임아웃 시 자동으로
     * 구독자 리스트에서 제거된다.</p>
     *
     * @return 구독자 emitter (Spring 이 응답으로 직렬화)
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 콜백: 종료/타임아웃/에러 시 구독자 제거
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> {
            subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(throwable -> subscribers.remove(emitter));

        // 1) 누적 버퍼 즉시 전송 (재접속 시 컨텍스트 복원)
        if (appender != null) {
            for (String pastLine : appender.snapshot()) {
                try {
                    emitter.send(SseEmitter.event().name("log").data(pastLine));
                } catch (IOException e) {
                    // 전송 실패 — 즉시 구독 해제하고 종료
                    subscribers.remove(emitter);
                    return emitter;
                }
            }
        }

        // 2) 새 구독자로 등록
        subscribers.add(emitter);
        return emitter;
    }

    /**
     * RingBufferAppender 가 로그 이벤트를 받았을 때 호출하는 콜백.
     * 모든 활성 구독자에게 비동기로 push 하며, 전송 실패한 구독자는 자동 해제한다.
     */
    void publishLog(String formattedLine) {
        if (subscribers.isEmpty()) {
            return;  // 구독자가 없으면 push 생략 (CPU 절약)
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("log").data(formattedLine));
            } catch (IOException | IllegalStateException e) {
                // 전송 실패 — 구독 해제
                subscribers.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 무시 — 이미 종료된 emitter
                }
            }
        }
    }

    /**
     * 활성 구독자 수 반환 (디버그/모니터링용).
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    // ============================================================
    // 내부 클래스 — Logback Ring Buffer Appender
    // ============================================================

    /**
     * 메모리 ring buffer 기반 Logback 어펜더.
     *
     * <p>최근 BUFFER_SIZE 건의 로그를 ArrayDeque 로 보존한다. ArrayDeque 는 단일 스레드에서만
     * 안전하므로 add/snapshot 시 synchronized 블록으로 보호한다.</p>
     */
    static class RingBufferAppender extends AppenderBase<ILoggingEvent> {

        private final AdminLogStreamService service;
        private final Deque<String> buffer = new ArrayDeque<>(BUFFER_SIZE);
        private final PatternLayout layout = new PatternLayout();

        RingBufferAppender(AdminLogStreamService service) {
            this.service = service;
        }

        @Override
        public void start() {
            // 패턴 레이아웃 설정 — Spring Boot 기본 패턴과 유사하게
            layout.setContext(getContext());
            layout.setPattern("%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n");
            layout.start();
            super.start();
        }

        @Override
        public void stop() {
            layout.stop();
            super.stop();
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                // 로그 라인 포맷 (timestamp + level + thread + logger + message)
                String formatted = String.format(
                        "%s %-5s [%s] %s - %s",
                        TS_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())),
                        event.getLevel().toString(),
                        event.getThreadName(),
                        event.getLoggerName(),
                        event.getFormattedMessage()
                );

                // 라인 길이 절단 (메모리 보호)
                if (formatted.length() > MAX_LINE_LENGTH) {
                    formatted = formatted.substring(0, MAX_LINE_LENGTH) + "…(truncated)";
                }

                // ring buffer 에 추가 (최대 크기 초과 시 가장 오래된 것 제거)
                synchronized (buffer) {
                    if (buffer.size() >= BUFFER_SIZE) {
                        buffer.pollFirst();
                    }
                    buffer.offerLast(formatted);
                }

                // 활성 구독자에게 push (서비스 콜백)
                service.publishLog(formatted);
            } catch (Exception e) {
                // 어펜더 내부에서 예외가 발생하면 안 된다 (로그 시스템 자체가 멈출 수 있음)
                // 콘솔에 직접 출력하고 무시
                System.err.println("[AdminLogStream] append 실패: " + e.getMessage());
            }
        }

        /**
         * 현재 ring buffer 의 스냅샷을 리스트로 반환한다 (구독 시 누적 로그 전송용).
         */
        List<String> snapshot() {
            synchronized (buffer) {
                return List.copyOf(buffer);
            }
        }
    }
}
