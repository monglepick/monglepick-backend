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
 *       reviews + event_logs UNION 집계 ("봤다" 단일 진실 원본은 reviews 테이블)</li>
 *   <li>최근 시청 영화 목록: {@link #findRecentReviewedMovieIdsByUserId(String, int)} — Native Query로
 *       reviews 테이블에서 유저별 최근 작성 리뷰의 movie_id만 추출</li>
 * </ul>
 *
 * <h3>watch_history 폐기 (2026-04-08)</h3>
 * <p>WatchHistory 도메인 자체를 폐기하면서 native query의 {@code watch_history} 참조를
 * 모두 {@code reviews} 테이블로 대체했다. "리뷰 작성 = 시청 완료 확인"이라는 단일 진실
 * 원본 원칙에 맞춰 정합성을 회복한다. is_deleted=false 필터로 소프트 삭제 리뷰는 제외한다.</p>
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
     * <p>{@code reviews}와 {@code event_logs} 두 테이블을 UNION하여 중복 없이 집계한다.
     * BehaviorProfileScheduler에서 최근 90일 기준으로 호출한다.
     * reviews 쪽은 소프트 삭제 리뷰({@code is_deleted=true})를 제외한다.</p>
     *
     * @param since 활동 기준 시각 (이 시각 이후 활동이 있는 유저만 포함)
     * @return 활동이 확인된 유저 ID 목록 (중복 없음)
     */
    @Query(value = """
            SELECT DISTINCT user_id FROM reviews
            WHERE created_at > :since AND is_deleted = false
            UNION
            SELECT DISTINCT user_id FROM event_logs WHERE created_at > :since
            """, nativeQuery = true)
    List<String> findActiveUserIdsSince(@Param("since") LocalDateTime since);

    /**
     * 행동 프로필 배치용 — 특정 유저가 최근 작성한 리뷰의 영화 ID 목록만 추출한다.
     *
     * <p>"봤다" 단일 진실 원본인 {@code reviews} 테이블에서 작성 시각 역순으로 정렬한 뒤
     * {@code movie_id}만 선택해 N+1 부담 없이 ID 목록을 반환한다. 소프트 삭제 리뷰는 제외.</p>
     *
     * <p>BehaviorProfileScheduler에서 장르·감독 친화도 계산을 위해 최근 100건 단위로 호출한다.</p>
     *
     * @param userId 조회 대상 사용자 ID
     * @param limit  반환할 최대 영화 ID 수 (예: 100)
     * @return 최신순 정렬된 영화 ID 목록
     */
    @Query(value = """
            SELECT movie_id FROM reviews
            WHERE user_id = :userId AND is_deleted = false
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findRecentReviewedMovieIdsByUserId(
            @Param("userId") String userId,
            @Param("limit") int limit
    );
}
