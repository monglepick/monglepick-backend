package com.monglepick.monglepickbackend.domain.recommendation.repository;

import com.monglepick.monglepickbackend.domain.recommendation.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 이벤트 로그 JPA 리포지토리 — event_logs 테이블 데이터 접근.
 *
 * <p>사용자의 클릭·조회·스킵·검색 등 행동 이벤트를 저장하고 조회한다.
 * AI Agent 추천 알고리즘 개선과 사용자 행동 분석에 활용된다.</p>
 *
 * <h3>현재 지원 연산</h3>
 * <ul>
 *   <li>단건 저장: {@code save(EventLog)}</li>
 *   <li>배치 저장: {@code saveAll(List<EventLog>)}</li>
 *   <li>활동 수준 집계: {@link #countByUserIdAndCreatedAtAfter(String, LocalDateTime)}</li>
 * </ul>
 */
public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    /**
     * 행동 프로필 배치용 — 특정 기간 이후 유저의 이벤트 발생 건수를 집계한다.
     *
     * <p>BehaviorProfileScheduler에서 최근 30일 이벤트 수를 기준으로
     * 활동 수준(dormant/casual/active/power)을 판정할 때 호출한다.</p>
     *
     * <ul>
     *   <li>0~5건   → dormant</li>
     *   <li>6~20건  → casual</li>
     *   <li>21~100건 → active</li>
     *   <li>101건+  → power</li>
     * </ul>
     *
     * @param userId 집계 대상 사용자 ID
     * @param since  집계 기준 시작 시각 (이 시각 이후 이벤트만 포함)
     * @return 해당 기간 내 이벤트 발생 건수
     */
    @Query("SELECT COUNT(e) FROM EventLog e WHERE e.userId = :userId AND e.createdAt > :since")
    long countByUserIdAndCreatedAtAfter(@Param("userId") String userId,
                                        @Param("since") LocalDateTime since);
}
