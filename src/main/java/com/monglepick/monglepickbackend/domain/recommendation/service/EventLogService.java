package com.monglepick.monglepickbackend.domain.recommendation.service;

import com.monglepick.monglepickbackend.domain.recommendation.dto.EventLogRequest;
import com.monglepick.monglepickbackend.domain.recommendation.entity.EventLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이벤트 로그 서비스 — 사용자 행동 이벤트 저장 비즈니스 로직.
 *
 * <p>클라이언트(monglepick-client)와 AI Agent(monglepick-agent)가
 * 전송하는 클릭·조회·스킵·검색 등 행동 이벤트를 {@code event_logs} 테이블에 기록한다.</p>
 *
 * <h3>단건 vs 배치</h3>
 * <ul>
 *   <li>{@link #logEvent} — 실시간 단건 기록 (영화 클릭, 평가 등 즉각 반응이 필요한 이벤트)</li>
 *   <li>{@link #logEventsBatch} — 배치 기록 (스크롤·호버 등 빈도 높은 이벤트를 모아서 전송)</li>
 * </ul>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 읽기 전용 기본</li>
 *   <li>쓰기 메서드: {@code @Transactional} 오버라이드 — INSERT 허용</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventLogService {

    /** 이벤트 로그 JPA 리포지토리 */
    private final EventLogRepository eventLogRepository;

    /**
     * 단건 이벤트를 기록한다.
     *
     * <p>영화 클릭, 평가, 예고편 재생 등 실시간으로 발생하는
     * 단일 이벤트를 즉시 {@code event_logs} 테이블에 저장한다.</p>
     *
     * @param userId  이벤트를 발생시킨 사용자 ID (JWT 또는 ServiceKey에서 추출)
     * @param request 이벤트 상세 정보 (eventType, movieId, recommendScore, metadata)
     */
    @Transactional
    public void logEvent(String userId, EventLogRequest request) {
        log.debug("이벤트 로그 저장 — userId={}, eventType={}, movieId={}",
                userId, request.eventType(), request.movieId());

        // 요청 DTO → EventLog 엔티티 변환 후 저장
        EventLog eventLog = EventLog.builder()
                .userId(userId)
                .movieId(request.movieId())
                .eventType(request.eventType())
                .recommendScore(request.recommendScore())
                .metadata(request.metadata())
                .build();

        eventLogRepository.save(eventLog);

        log.debug("이벤트 로그 저장 완료 — eventLogId={}, userId={}, eventType={}",
                eventLog.getEventLogId(), userId, request.eventType());
    }

    /**
     * 여러 이벤트를 배치로 기록한다.
     *
     * <p>스크롤, 호버 등 빈도가 높은 이벤트를 클라이언트에서 모아 한 번에 전송할 때 사용한다.
     * {@code saveAll}을 사용하여 단일 트랜잭션 내에서 일괄 INSERT를 수행한다.</p>
     *
     * <p>배치 크기가 0이면 저장을 건너뛰고 즉시 반환한다 (불필요한 DB 접근 방지).</p>
     *
     * @param userId   이벤트를 발생시킨 사용자 ID
     * @param requests 이벤트 요청 목록 (비어 있으면 no-op)
     */
    @Transactional
    public void logEventsBatch(String userId, List<EventLogRequest> requests) {
        // 빈 리스트 요청 시 불필요한 DB 접근 방지
        if (requests == null || requests.isEmpty()) {
            log.debug("배치 이벤트 로그 저장 건너뜀 — 요청 목록이 비어 있습니다. userId={}", userId);
            return;
        }

        log.debug("배치 이벤트 로그 저장 시작 — userId={}, count={}", userId, requests.size());

        // 요청 DTO 목록 → EventLog 엔티티 목록 변환
        List<EventLog> eventLogs = requests.stream()
                .map(request -> EventLog.builder()
                        .userId(userId)
                        .movieId(request.movieId())
                        .eventType(request.eventType())
                        .recommendScore(request.recommendScore())
                        .metadata(request.metadata())
                        .build())
                .toList();

        // 단일 트랜잭션 내 일괄 INSERT
        eventLogRepository.saveAll(eventLogs);

        log.debug("배치 이벤트 로그 저장 완료 — userId={}, savedCount={}", userId, eventLogs.size());
    }
}
