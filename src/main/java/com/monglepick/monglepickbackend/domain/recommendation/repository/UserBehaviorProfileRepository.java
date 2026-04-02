package com.monglepick.monglepickbackend.domain.recommendation.repository;

import com.monglepick.monglepickbackend.domain.recommendation.entity.UserBehaviorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 행동 프로필 JPA 리포지토리 — user_behavior_profile 테이블 데이터 접근.
 *
 * <p>BehaviorProfileScheduler의 일일 배치와 AI Agent의 추천 가중치 조회에 활용된다.</p>
 *
 * <h3>주요 조회 패턴</h3>
 * <ul>
 *   <li>단건 조회: {@link #findByUserId(String)} — PK 직접 조회와 동일하나 가독성 우선</li>
 *   <li>배치 활성 유저 목록: {@link #findActiveUserIdsSince(LocalDateTime)} — Native Query로
 *       watch_history + event_logs UNION 집계</li>
 * </ul>
 */
public interface UserBehaviorProfileRepository extends JpaRepository<UserBehaviorProfile, String> {

    /**
     * userId로 행동 프로필을 조회한다.
     *
     * <p>PK가 user_id이므로 {@code findById}와 동일하지만,
     * 서비스 레이어에서의 가독성을 위해 명시적으로 정의한다.</p>
     *
     * @param userId 조회 대상 사용자 ID
     * @return 행동 프로필 (없으면 {@link Optional#empty()})
     */
    Optional<UserBehaviorProfile> findByUserId(String userId);

    /**
     * 특정 기준 시각 이후 활동이 있는 유저 ID 목록을 조회한다.
     *
     * <p>watch_history와 event_logs 두 테이블을 UNION하여 중복 없이 집계한다.
     * BehaviorProfileScheduler에서 최근 90일 기준으로 호출한다.</p>
     *
     * @param since 활동 기준 시각 (이 시각 이후 활동이 있는 유저만 포함)
     * @return 활동이 확인된 유저 ID 목록 (중복 없음)
     */
    @Query(value = """
            SELECT DISTINCT user_id FROM watch_history WHERE watched_at > :since
            UNION
            SELECT DISTINCT user_id FROM event_logs WHERE created_at > :since
            """, nativeQuery = true)
    List<String> findActiveUserIdsSince(@Param("since") LocalDateTime since);
}
