package com.monglepick.monglepickbackend.domain.roadmap.scheduler;

import com.monglepick.monglepickbackend.admin.repository.AdminQuizRepository;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 퀴즈 자동 출제 배치 (2026-04-29 신규).
 *
 * <p>매일 00:00 KST 에 APPROVED 상태의 퀴즈 1건을 골라 PUBLISHED 로 전환하고
 * {@code quiz_date} 를 오늘 날짜로 세팅한다. quiz_generation 에이전트(LangGraph 7노드)
 * 가 자동 생성 후 PENDING 으로 적재한 퀴즈를 관리자가 검수(APPROVED) 하기만 하면,
 * 별도 수동 발행 없이 자연스럽게 사용자에게 노출되게 하는 운영 자동화 단계이다.</p>
 *
 * <h3>스케줄</h3>
 * <ul>
 *   <li>cron: {@code "0 0 0 * * *"}, zone: {@code "Asia/Seoul"} — 매일 00:00 KST</li>
 *   <li>{@link com.monglepick.monglepickbackend.MonglepickBackendApplication} 에
 *       {@code @EnableScheduling} 이 이미 선언되어 추가 설정 없이 동작.</li>
 * </ul>
 *
 * <h3>발행 정책 (FIFO)</h3>
 * <ol>
 *   <li>오늘 날짜의 PUBLISHED 퀴즈가 이미 있으면 <b>skip</b> — 멱등 보장.
 *       (수동 발행 + 배치 동시 실행 / 배치 두 번 트리거 / 재시도 시나리오 방어)</li>
 *   <li>APPROVED + quiz_date IS NULL 중 가장 오래된 1건을 선정.
 *       검수 통과 후 가장 오래 대기한 퀴즈를 우선 노출 — 적체 방지.</li>
 *   <li>{@link Quiz#publishOn(LocalDate)} 으로 status=PUBLISHED + quiz_date=오늘 atomic 세팅.</li>
 *   <li>APPROVED 후보가 0건이면 warn 로그 — 검수 적체로 운영자에게 알림.</li>
 * </ol>
 *
 * <h3>트랜잭션</h3>
 * <p>{@code @Transactional(REQUIRES_NEW)} — 배치 자체가 자기 트랜잭션을 가진다.
 * 이미 호출 컨텍스트에 트랜잭션이 있어도 별개 트랜잭션으로 격리되어 다른 작업에
 * 영향을 주지 않는다. 실패 시 해당 배치 1회만 롤백되고 다음 날 재시도.</p>
 *
 * <h3>운영자 수동 트리거</h3>
 * <p>{@link #manualPublish()} 를 public 으로 노출하여 관리자 페이지에서 즉시 발행하거나
 * 배치 실패 후 재시도를 위한 hook 으로 활용할 수 있다 (현재 시점에는 미연결).</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-29: 신규 — quiz_generation 에이전트 운영 자동화의 마지막 단계.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuizPublishScheduler {

    /** 한국 표준시 — 운영 사용자 기준 자정 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 관리자 전용 퀴즈 리포지토리 — 멱등 체크 + 후보 조회 + JpaRepository.save 사용 */
    private final AdminQuizRepository adminQuizRepository;

    /**
     * 매일 00:00 KST 자동 발행.
     *
     * <p>cron 표현식 {@code "0 0 0 * * *"} 은 초/분/시/일/월/요일 순으로 0/0/0 매일 매월 매요일.
     * zone 을 명시하여 서버 타임존 영향에서 분리한다.</p>
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void runDailyPublish() {
        try {
            int published = publishNextApproved();
            if (published == 1) {
                log.info("퀴즈 자동 출제 완료 — date={}", LocalDate.now(KST));
            } else {
                log.info("퀴즈 자동 출제 스킵 (이미 오늘 출제 완료 또는 후보 없음) — date={}",
                        LocalDate.now(KST));
            }
        } catch (Exception e) {
            // 배치는 다음 날 재시도 가능하므로 예외를 삼키고 로깅만.
            // 단, AlertManager 가 ERROR 로그를 캡처할 수 있도록 수준은 ERROR.
            log.error("퀴즈 자동 출제 배치 실패 — date={}", LocalDate.now(KST), e);
        }
    }

    /**
     * 운영자 수동 트리거 — 즉시 1건 발행.
     *
     * <p>관리자 페이지의 "오늘 퀴즈 강제 발행" 버튼 또는 배치 실패 후 운영자가
     * 직접 호출할 수 있도록 public 으로 노출. 멱등 가드 동일 적용.</p>
     *
     * @return 발행된 퀴즈 건수 (0 또는 1)
     */
    public int manualPublish() {
        log.warn("퀴즈 자동 출제 수동 트리거 — date={}", LocalDate.now(KST));
        return publishNextApproved();
    }

    /**
     * 핵심 발행 로직 — 멱등 + FIFO + atomic.
     *
     * <p>{@code REQUIRES_NEW} 로 새 트랜잭션을 강제. 동일 메서드를 두 번 호출하면
     * 두 번째 호출은 멱등 가드(existsByStatusAndQuizDate)에 의해 0 을 반환한다.</p>
     *
     * @return 1=발행됨, 0=스킵(이미 발행됨 또는 후보 없음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int publishNextApproved() {
        LocalDate today = LocalDate.now(KST);

        // ── 멱등 가드 ── 같은 날짜 PUBLISHED 가 이미 있으면 발행하지 않음.
        if (adminQuizRepository.existsByStatusAndQuizDate(Quiz.QuizStatus.PUBLISHED, today)) {
            log.debug("퀴즈 자동 출제 멱등 스킵 — date={} 에 이미 PUBLISHED 존재", today);
            return 0;
        }

        // ── 후보 조회 ── APPROVED + quiz_date IS NULL 중 가장 오래된 1건 (FIFO).
        Optional<Quiz> candidate = adminQuizRepository
                .findFirstByStatusAndQuizDateIsNullOrderByCreatedAtAsc(Quiz.QuizStatus.APPROVED);

        if (candidate.isEmpty()) {
            // 검수 적체 — 운영자가 PENDING 을 검수해야 할 신호.
            log.warn("퀴즈 자동 출제 후보 없음 — APPROVED 대기열이 비어있음. 검수 적체 가능성 확인 필요. date={}",
                    today);
            return 0;
        }

        // ── 발행 ── status=PUBLISHED + quiz_date=오늘 atomic 세팅.
        Quiz quiz = candidate.get();
        quiz.publishOn(today);
        // JpaRepository 의 dirty checking 이 트랜잭션 종료 시 UPDATE 발행하지만,
        // 명시적 save 호출로 의도를 분명히 한다 (코드 가독성).
        adminQuizRepository.save(quiz);

        log.info("퀴즈 자동 출제 발행 — quizId={}, movieId={}, date={}, createdAt={}",
                quiz.getQuizId(), quiz.getMovieId(), today, quiz.getCreatedAt());
        return 1;
    }
}
