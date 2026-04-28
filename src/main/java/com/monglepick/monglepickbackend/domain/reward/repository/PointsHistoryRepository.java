package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 포인트 변동 이력 리포지토리 — points_history 테이블 접근 계층.
 *
 * <p>포인트의 모든 변동(획득, 사용, 만료, 보너스) 이력을 조회한다.
 * 이력 레코드는 한 번 생성되면 수정/삭제하지 않는다 (append-only).</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc(String, Pageable)} — 사용자별 이력 페이징 조회 (최신순)</li>
 *   <li>{@link #countByUserIdAndPointTypeAndDescriptionContaining(String, String, String, LocalDateTime, LocalDateTime)}
 *       — 특정 기간 내 특정 유형+키워드 이력 건수 (일일 쿼터, 이벤트 중복 방지용)</li>
 * </ul>
 */
public interface PointsHistoryRepository extends JpaRepository<PointsHistory, Long> {

    /**
     * 사용자의 포인트 변동 이력을 최신순으로 페이징 조회한다.
     *
     * <p>클라이언트의 "포인트 내역" 화면에서 사용된다.
     * created_at DESC 정렬이므로 가장 최근 변동이 먼저 표시된다.</p>
     *
     * @param userId   사용자 ID (VARCHAR(50))
     * @param pageable 페이징 정보 (page, size)
     * @return 포인트 변동 이력 페이지
     */
    Page<PointsHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 특정 기간 내 특정 유형과 설명 키워드를 포함하는 이력 건수를 조회한다.
     *
     * <p>일일 쿼터 계산이나 이벤트 중복 참여 방지에 사용된다.
     * 예: 오늘 "AI 추천 사용" 이력이 몇 건인지 확인하여 일일 한도를 적용할 수 있다.</p>
     *
     * <h4>사용 예시</h4>
     * <pre>{@code
     * // 오늘 AI 추천 사용 횟수 조회
     * long todayAiUsage = repository.countByUserIdAndPointTypeAndDescriptionContaining(
     *     "user123", "spend", "AI 추천",
     *     today.atStartOfDay(), today.plusDays(1).atStartOfDay()
     * );
     * }</pre>
     *
     * @param userId    사용자 ID
     * @param pointType 포인트 변동 유형 (earn, spend, expire, bonus)
     * @param keyword   설명(description)에 포함되어야 하는 키워드
     * @param start     조회 시작 시각 (inclusive)
     * @param end       조회 종료 시각 (exclusive — start 이상, end 미만)
     * @return 조건에 맞는 이력 건수
     */
    @Query("SELECT COUNT(h) FROM PointsHistory h " +
            "WHERE h.userId = :userId " +
            "AND h.pointType = :pointType " +
            "AND h.description LIKE %:keyword% " +
            "AND h.createdAt >= :start AND h.createdAt < :end")
    long countByUserIdAndPointTypeAndDescriptionContaining(
            @Param("userId") String userId,
            @Param("pointType") String pointType,
            @Param("keyword") String keyword,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 일일/월간 AI 추천 사용 횟수를 단일 쿼리로 집계한다.
     *
     * <p>QuotaService에서 dailyUsed/monthlyUsed를 각각 COUNT하던 것을
     * 하나의 쿼리로 통합하여 대용량 테이블(26M+)에서 DB 부하를 절반으로 줄인다.</p>
     *
     * @param userId     사용자 ID
     * @param pointType  포인트 변동 유형 (예: "spend")
     * @param keyword    설명에 포함되어야 하는 키워드 (예: "AI 추천")
     * @param dayStart   오늘 시작 시각 (일일 카운트 기준)
     * @param monthStart 이번 달 시작 시각 (월간 카운트 기준)
     * @param end        종료 시각 (exclusive)
     * @return [0]: 일일 사용 횟수, [1]: 월간 사용 횟수
     */
    @Query("SELECT " +
            "SUM(CASE WHEN h.createdAt >= :dayStart THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN h.createdAt >= :monthStart THEN 1 ELSE 0 END) " +
            "FROM PointsHistory h " +
            "WHERE h.userId = :userId " +
            "AND h.pointType = :pointType " +
            "AND h.description LIKE %:keyword% " +
            "AND h.createdAt >= :monthStart AND h.createdAt < :end")
    Object[] countDailyAndMonthlyUsage(
            @Param("userId") String userId,
            @Param("pointType") String pointType,
            @Param("keyword") String keyword,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("end") LocalDateTime end
    );

    /**
     * 특정 사용자의 활동 유형 + 참조 ID로 포인트 이력 단건을 조회한다.
     *
     * <p>revokeReward()에서 회수 대상 지급 이력을 조회하여 정확한 point_change 값을
     * 가져올 때 사용한다. points_history 테이블의 UNIQUE 인덱스
     * (uk_history_user_action_ref: user_id + action_type + reference_id)로
     * 중복 지급이 방지되므로 결과는 항상 0건 또는 1건이다.</p>
     *
     * <p>MySQL UNIQUE 인덱스는 NULL을 중복 허용하므로 action_type=NULL인 기존 레코드는
     * 이 인덱스의 영향을 받지 않는다. 이 메서드는 action_type이 NOT NULL인 활동 리워드
     * 이력 조회에만 사용한다.</p>
     *
     * @param userId      사용자 ID (VARCHAR(50))
     * @param actionType  활동 유형 코드 (reward_policy.action_type)
     * @param referenceId 참조 ID (예: "movie_123", "post_456", "comment_789")
     * @return 조건에 맞는 포인트 이력 (없으면 Optional.empty)
     */
    Optional<PointsHistory> findByUserIdAndActionTypeAndReferenceId(
            String userId,
            String actionType,
            String referenceId
    );

    /**
     * 사용자의 REVIEW_CREATE 적립 이력을 referenceId 목록 기준으로 배치 조회한다.
     *
     * <p>고객센터 봇 EP({@code GET /api/v1/users/me/reviews}) 에서 리뷰 목록과
     * 포인트 적립 이력을 N+1 없이 매핑할 때 사용한다.
     * referenceId 형식: "movie_{movieId}" (RewardService.grantReward 규칙).</p>
     *
     * <p>포인트 이력 없는 리뷰는 Map 에 키가 없으므로 서비스 레이어에서
     * {@code map.get(referenceId) == null} 로 판단한다.</p>
     *
     * @param userId      사용자 ID
     * @param actionType  활동 유형 코드 (예: "REVIEW_CREATE")
     * @param referenceIds referenceId 목록 (예: ["movie_tt1234", "movie_tt5678"])
     * @return 조건에 맞는 포인트 이력 목록 (referenceId 기준 IN 절 조회)
     */
    @Query("SELECT h FROM PointsHistory h " +
            "WHERE h.userId = :userId " +
            "AND h.actionType = :actionType " +
            "AND h.referenceId IN :referenceIds")
    List<PointsHistory> findByUserIdAndActionTypeAndReferenceIdIn(
            @Param("userId") String userId,
            @Param("actionType") String actionType,
            @Param("referenceIds") List<String> referenceIds
    );
}
