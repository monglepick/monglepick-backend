package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 사용자 AI 쿼터 리포지토리 — user_ai_quota 테이블 접근 계층.
 *
 * <p>v3.3에서 {@code user_points}에서 분리된 AI 쿼터 테이블에 접근한다.
 * {@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService}에서
 * AI 사용 가능 여부 확인 및 카운터 갱신 시 사용한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserId(String)} — 일반 조회 (락 없음, 읽기 전용)</li>
 *   <li>{@link #findByUserIdWithLock(String)} — 비관적 쓰기 락 (SELECT FOR UPDATE).
 *       PURCHASED 소스 소비, 자동 지급 이용권 추가 등 카운터 변경 시 사용.</li>
 *   <li>{@link #existsByUserId(String)} — 존재 여부 확인 (회원가입 초기화 멱등성 보장)</li>
 * </ul>
 *
 * <h3>동시성 전략</h3>
 * <p>AI 쿼터 소비({@code consumePurchasedToken}) 및 이용권 추가({@code addPurchasedTokens})는
 * {@link #findByUserIdWithLock}으로 비관적 락을 획득한 뒤 수행해야 한다.
 * 단순 읽기(소스 1/2 판단)는 {@link #findByUserId}를 사용하여 락 경합을 줄인다.</p>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota
 * @see com.monglepick.monglepickbackend.domain.reward.service.QuotaService
 */
public interface UserAiQuotaRepository extends JpaRepository<UserAiQuota, Long> {

    /**
     * 사용자 ID로 AI 쿼터 레코드를 조회한다.
     *
     * <p>락 없이 조회하므로 읽기 전용 용도(일일 사용량 확인, 잔여 이용권 표시)에 사용한다.
     * PURCHASED 소스 소비나 이용권 추가처럼 쓰기 작업이 필요하면
     * {@link #findByUserIdWithLock(String)}을 사용할 것.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return AI 쿼터 레코드 (없으면 Optional.empty)
     */
    Optional<UserAiQuota> findByUserId(String userId);

    /**
     * 사용자 ID의 AI 쿼터 레코드 존재 여부를 확인한다.
     *
     * <p>회원가입 시 AI 쿼터 초기화({@code initializePoint})의 멱등성 보장에 사용된다.
     * 이미 존재하면 초기화를 건너뛴다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 존재하면 true, 없으면 false
     */
    boolean existsByUserId(String userId);

    /**
     * 사용자 ID로 AI 쿼터 레코드를 비관적 쓰기 락과 함께 조회한다.
     *
     * <p>SELECT ... FOR UPDATE를 실행하여 트랜잭션 종료 시까지 해당 행을 잠근다.
     * 구매 이용권 소비({@code consumePurchasedToken}), 이용권 추가({@code addPurchasedTokens}),
     * 자동 지급 이용권 지급 등 카운터 변경 시 반드시 이 메서드를 사용해야 한다.</p>
     *
     * <p>주의: 반드시 {@code @Transactional} 컨텍스트 안에서 호출해야 하며,
     * 트랜잭션 범위를 최소화하여 데드락 위험을 줄여야 한다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return AI 쿼터 레코드 (없으면 Optional.empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM UserAiQuota q WHERE q.userId = :userId")
    Optional<UserAiQuota> findByUserIdWithLock(@Param("userId") String userId);

    // ═══ AI 서비스 통계용 집계 ═══

    /** 평균 일일 AI 사용량 */
    @Query("SELECT COALESCE(AVG(q.dailyAiUsed), 0) FROM UserAiQuota q")
    double avgDailyAiUsed();

    /** 평균 월간 쿠폰 사용량 */
    @Query("SELECT COALESCE(AVG(q.monthlyCouponUsed), 0) FROM UserAiQuota q")
    double avgMonthlyCouponUsed();

    /** 전체 구매 이용권 보유량 합계 */
    @Query("SELECT COALESCE(SUM(q.purchasedAiTokens), 0) FROM UserAiQuota q")
    long sumPurchasedAiTokens();

    /** 일일 무료 한도 소진 사용자 수 (dailyAiUsed >= 3, 기본 NORMAL 등급 한도) */
    @Query("SELECT COUNT(q) FROM UserAiQuota q WHERE q.dailyAiUsed >= 3")
    long countExhaustedUsers();

    // ══════════════════════════════════════════════
    // AI 서비스 통계 V2 — 6 등급 차등 기준 적용 (2026-04-29)
    //
    // grades.daily_ai_limit 을 LEFT JOIN 하여 실제 등급별 한도 기준으로 소진 판정.
    // -1 (DIAMOND, 무제한) 은 절대 소진 카운트에서 제외.
    // user_points 가 없는 사용자는 NORMAL 등급(한도=3) 으로 간주 (COALESCE).
    // Native SQL 사용 — JPQL 의 LEFT JOIN ON 호환성 회피 + 운영 정확성 우선.
    // ══════════════════════════════════════════════

    /**
     * 등급별 한도 기준으로 일일 무료 한도 소진 사용자 수.
     *
     * @return 등급별 daily_ai_limit 도달 사용자 수 (DIAMOND -1 무제한 제외)
     */
    @Query(value = """
            SELECT COALESCE(SUM(
                CASE WHEN COALESCE(g.daily_ai_limit, 3) <> -1
                      AND q.daily_ai_used >= COALESCE(g.daily_ai_limit, 3)
                     THEN 1 ELSE 0 END
            ), 0)
            FROM user_ai_quota q
            LEFT JOIN user_points up ON up.user_id = q.user_id
            LEFT JOIN grades g ON g.grade_id = up.grade_id
            """, nativeQuery = true)
    long countExhaustedUsersByGrade();

    /**
     * 등급별 사용자 분포 — gradeCode/gradeName/totalUsers/exhausted/avgDailyUsed.
     *
     * <p>반환 row: [gradeCode, gradeName, totalUsers, exhausted, avgDailyUsed].
     * sort_order ASC 정렬 (NORMAL → DIAMOND).</p>
     *
     * <p>user_points 가 없는 사용자는 'NORMAL' 등급 + 한도 3 으로 간주된다 (NULL 그룹 → COALESCE).</p>
     *
     * @return Object[] (gradeCode, gradeName, totalUsers, exhausted, avgDailyUsed) 리스트
     */
    @Query(value = """
            SELECT
                COALESCE(g.grade_code, 'NORMAL') AS grade_code,
                COALESCE(g.grade_name, '알갱이') AS grade_name,
                COUNT(q.user_ai_quota_id) AS total_users,
                SUM(
                    CASE WHEN COALESCE(g.daily_ai_limit, 3) <> -1
                          AND q.daily_ai_used >= COALESCE(g.daily_ai_limit, 3)
                         THEN 1 ELSE 0 END
                ) AS exhausted,
                AVG(q.daily_ai_used) AS avg_used
            FROM user_ai_quota q
            LEFT JOIN user_points up ON up.user_id = q.user_id
            LEFT JOIN grades g ON g.grade_id = up.grade_id
            GROUP BY COALESCE(g.grade_code, 'NORMAL'),
                     COALESCE(g.grade_name, '알갱이'),
                     COALESCE(g.sort_order, 1)
            ORDER BY COALESCE(g.sort_order, 1) ASC
            """, nativeQuery = true)
    java.util.List<Object[]> aggregateQuotaByGrade();
}
