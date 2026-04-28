package com.monglepick.monglepickbackend.domain.payment.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 구독 리포지토리 — user_subscriptions 테이블 데이터 접근.
 *
 * <p>사용자의 구독 현황 조회, 활성 구독 검증, 만료 대상 조회 등을 제공한다.
 * 결제 서비스에서 구독 생성/갱신/취소 시, 스케줄러에서 만료 배치 처리 시 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndStatus(String, UserSubscription.Status)} — 특정 상태의 구독 조회 (주로 ACTIVE)</li>
 *   <li>{@link #findByStatusAndExpiresAtBeforeAndAutoRenewTrue(UserSubscription.Status, LocalDateTime)} — 자동 갱신 대상 조회 (스케줄러)</li>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc(String)} — 사용자 구독 이력 전체 (최신순)</li>
 * </ul>
 *
 * @see UserSubscription 사용자 구독 엔티티
 */
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * 특정 사용자의 특정 상태 구독을 조회한다.
     *
     * <p>주로 {@code Status.ACTIVE}와 함께 사용하여 현재 활성 구독이 있는지 확인한다.
     * 한 사용자는 동시에 1개의 ACTIVE 구독만 가질 수 있으므로 (서비스 레이어 검증),
     * 최대 1건이 반환된다.</p>
     *
     * <p>활용 예시:</p>
     * <ul>
     *   <li>신규 구독 시: ACTIVE 구독 존재 여부 확인 (중복 구독 방지)</li>
     *   <li>구독 취소 시: ACTIVE 구독 조회 후 {@code cancel()} 호출</li>
     *   <li>구독 혜택 확인: ACTIVE 구독의 plan 정보로 포인트 지급량 결정</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param status 구독 상태 (ACTIVE / CANCELLED / EXPIRED)
     * @return 해당 상태의 구독 (존재하지 않으면 empty)
     */
    Optional<UserSubscription> findByUserIdAndStatus(String userId, UserSubscription.Status status);

    /**
     * 자동 갱신 대상 구독 목록을 조회한다 (만료 배치/스케줄러용).
     *
     * <p>지정 시각({@code now}) 이전에 만료 예정이면서,
     * 자동 갱신이 활성화된({@code autoRenew=true}) ACTIVE 구독을 반환한다.
     * 스케줄러가 주기적으로 호출하여 자동 결제를 시도한다.</p>
     *
     * <p>SQL 동치:</p>
     * <pre>
     * SELECT * FROM user_subscriptions
     * WHERE status = 'ACTIVE'
     *   AND expires_at &lt; :now
     *   AND auto_renew = TRUE
     * </pre>
     *
     * @param status 구독 상태 (보통 {@code Status.ACTIVE})
     * @param now    현재 시각 (이 시각 이전에 만료된 구독 조회)
     * @return 자동 갱신 대상 구독 목록
     */
    List<UserSubscription> findByStatusAndExpiresAtBeforeAndAutoRenewTrue(
            UserSubscription.Status status, LocalDateTime now);

    /**
     * 사용자의 전체 구독 이력을 최신순으로 조회한다.
     *
     * <p>마이페이지의 구독 이력 화면에서 사용한다.
     * 활성/취소/만료 모든 상태의 구독이 포함되며,
     * 가장 최근에 생성된 구독이 먼저 반환된다.</p>
     *
     * @param userId 사용자 ID
     * @return 구독 이력 목록 (최신순 정렬)
     */
    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 특정 상태의 구독 수를 카운트한다.
     *
     * <p>관리자 대시보드 KPI 카드에서 현재 활성 구독 수를 집계할 때 사용한다.
     * {@code Status.ACTIVE}를 전달하면 현재 유효한 구독 건수를 반환한다.</p>
     *
     * @param status 구독 상태 (ACTIVE / CANCELLED / EXPIRED)
     * @return 해당 상태의 구독 수
     */
    long countByStatus(UserSubscription.Status status);

    /**
     * 특정 사용자의 특정 상태 구독을 비관적 쓰기 락으로 조회한다 (동시 결제 중복 구독 방지).
     *
     * <p>동일 사용자가 여러 탭/기기에서 동시에 구독 결제를 완료하면,
     * {@link #findByUserIdAndStatus(String, UserSubscription.Status)} 단순 조회로는
     * 두 트랜잭션 모두 "ACTIVE 구독 없음"을 읽고 각각 구독을 생성하는
     * TOCTOU(Time-Of-Check-To-Time-Of-Use) 경쟁 조건이 발생한다.
     * {@code SELECT FOR UPDATE}를 사용하면 첫 번째 트랜잭션이 잠금을 획득하고,
     * 두 번째 트랜잭션은 첫 번째가 커밋/롤백될 때까지 대기한 후 진입 시점에
     * 이미 ACTIVE 구독이 존재함을 확인하여 중복 생성을 차단한다.</p>
     *
     * <h4>사용 위치</h4>
     * <p>{@link com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService#createSubscription}
     * — 중복 구독 방지 체크에서 이 메서드를 사용한다.
     * 반드시 {@code @Transactional} 메서드 내에서 호출해야 한다.
     * 트랜잭션 밖에서 호출하면 잠금이 즉시 해제되어 방어 효과가 없다.</p>
     *
     * <h4>SQL 동치</h4>
     * <pre>
     * SELECT * FROM user_subscriptions
     * WHERE user_id = :userId AND status = :status
     * FOR UPDATE
     * </pre>
     *
     * @param userId 사용자 ID
     * @param status 구독 상태 (주로 {@code Status.ACTIVE})
     * @return 해당 상태의 구독 (존재하지 않으면 empty) — 조회 기간 동안 행 잠금 유지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSubscription s WHERE s.userId = :userId AND s.status = :status")
    Optional<UserSubscription> findByUserIdAndStatusForUpdate(
            @Param("userId") String userId, @Param("status") UserSubscription.Status status);

    /**
     * 사용자의 특정 상태 구독을 plan과 함께 조회한다 (N+1 방지).
     *
     * <p>plan 정보에 접근하는 getStatus(), cancelSubscription()에서 사용한다.
     * JOIN FETCH로 plan을 즉시 로딩하여 추가 쿼리를 방지한다.</p>
     */
    @Query("SELECT s FROM UserSubscription s JOIN FETCH s.plan WHERE s.userId = :userId AND s.status = :status")
    Optional<UserSubscription> findByUserIdAndStatusFetchPlan(
            @Param("userId") String userId, @Param("status") UserSubscription.Status status);

    /**
     * 사용자의 "현재 유효한" 구독을 plan과 함께 조회한다 (2026-04-28 신설).
     *
     * <p>유효 정의: status=ACTIVE 또는 (status=CANCELLED AND expiresAt &gt; now).
     * CANCELLED 상태는 사용자가 해지를 요청했지만 만료일까지 혜택이 유지되는
     * "유예 기간"이며, 본 메서드는 이를 "현재 구독 중"으로 간주한다.</p>
     *
     * <h4>왜 필요한가</h4>
     * <p>{@code SubscriptionService.getStatus()} 가 ACTIVE 만 조회해서, 해지 예약 상태의
     * 사용자가 만료일까지 혜택이 있는데도 클라이언트에는 "구독 아님"으로 표시되는 결함이 있었다.
     * 본 메서드는 ACTIVE 우선, CANCELLED&amp;유효 폴백 순서로 정렬하여 1건만 반환한다.</p>
     *
     * <h4>정렬 규칙</h4>
     * <ul>
     *   <li>ACTIVE 가 1순위 (CASE 표현식으로 0)</li>
     *   <li>같은 우선순위 내에서는 createdAt DESC (가장 최근 생성된 구독)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param now    기준 시각 (보통 LocalDateTime.now())
     * @return 현재 유효한 구독 (없으면 empty)
     */
    @Query("SELECT s FROM UserSubscription s JOIN FETCH s.plan " +
            "WHERE s.userId = :userId " +
            "AND (s.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.ACTIVE " +
            "  OR (s.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.CANCELLED " +
            "      AND s.expiresAt > :now)) " +
            "ORDER BY CASE WHEN s.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.ACTIVE THEN 0 ELSE 1 END ASC, " +
            "         s.createdAt DESC")
    List<UserSubscription> findCurrentSubscriptionsWithPlan(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    /**
     * 만료된 구독을 plan과 함께 페이징으로 조회한다 (OOM + N+1 방지).
     *
     * <p>스케줄러에서 배치 처리 시 사용한다. 100건씩 페이징하여
     * 대량 만료 구독이 있어도 메모리를 안전하게 유지한다.</p>
     */
    @Query(value = "SELECT s FROM UserSubscription s JOIN FETCH s.plan " +
            "WHERE s.status = :status AND s.expiresAt < :now AND s.autoRenew = true",
            countQuery = "SELECT COUNT(s) FROM UserSubscription s " +
                    "WHERE s.status = :status AND s.expiresAt < :now AND s.autoRenew = true")
    Page<UserSubscription> findExpiredWithPlan(
            @Param("status") UserSubscription.Status status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // ══════════════════════════════════════════════
    // 관리자 통계용 집계 쿼리 (AdminStatsService 섹션 14, 15)
    // ══════════════════════════════════════════════

    /**
     * 지정 기간 내 ACTIVE 구독으로 전환한 고유 사용자 수를 반환한다 (전환 퍼널 단계 5).
     *
     * <p>구독 생성 시각(createdAt) 기준으로 기간 내 신규 구독을 시작한 고유 사용자 수를 카운트한다.
     * DISTINCT userId 로 중복을 제거하며, 구독 상태는 ACTIVE/CANCELLED/EXPIRED 모두 포함하여
     * 기간 내 구독 전환 자체를 측정한다.</p>
     *
     * @param start 기간 시작 시각 (inclusive)
     * @param end   기간 종료 시각 (exclusive)
     * @return 해당 기간 구독 전환 고유 사용자 수
     */
    @Query("""
            SELECT COUNT(DISTINCT s.userId)
            FROM UserSubscription s
            WHERE s.createdAt >= :start AND s.createdAt < :end
            """)
    long countDistinctUserByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 지정 시각 이전에 만료되고 이후 갱신(신규 구독)이 없는 사용자 수를 반환한다 (이탈 위험 신호).
     *
     * <p>만료 후 미갱신 사용자 수를 계산한다:
     * status = EXPIRED 이고 expiresAt &lt; cutoff 인 구독 중,
     * 해당 사용자가 cutoff 이후 신규 ACTIVE 구독이 없는 경우를 카운트한다.</p>
     *
     * <p>서브쿼리로 cutoff 이후 활성 구독이 있는 userId를 제외한다.</p>
     *
     * @param cutoff 만료 기준 시각 (이 시각 이전 만료된 구독이 대상)
     * @return 만료 후 미갱신 고유 사용자 수
     */
    @Query("""
            SELECT COUNT(DISTINCT s.userId)
            FROM UserSubscription s
            WHERE s.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.EXPIRED
              AND s.expiresAt < :cutoff
              AND s.userId NOT IN (
                  SELECT s2.userId FROM UserSubscription s2
                  WHERE s2.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.ACTIVE
              )
            """)
    long countExpiredWithoutRenewal(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 구독 ID로 구독을 plan과 함께 즉시 로딩 조회한다 (N+1 방지).
     *
     * <p>관리자 구독 연장({@link com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService#extendSubscription})
     * 에서 사용한다. plan.periodType 접근 시 LazyInitializationException을 방지하기 위해
     * JOIN FETCH로 plan을 함께 로딩한다.</p>
     *
     * <h4>open-in-view=false 환경 주의</h4>
     * <p>프로젝트는 {@code spring.jpa.open-in-view=false} 설정이므로,
     * 트랜잭션 범위 밖에서 LAZY 연관 엔티티에 접근하면 LazyInitializationException이 발생한다.
     * plan에 접근이 필요한 서비스 메서드에서는 반드시 이 메서드를 사용한다.</p>
     *
     * @param subscriptionId 조회할 구독 레코드 ID (user_subscriptions.user_subscription_id)
     * @return plan이 즉시 로딩된 구독 (존재하지 않으면 empty)
     */
    @Query("SELECT s FROM UserSubscription s JOIN FETCH s.plan WHERE s.userSubscriptionId = :id")
    java.util.Optional<UserSubscription> findByIdWithPlan(@Param("id") Long id);

    /**
     * 활성 구독의 플랜 코드별 가입자 수를 반환한다 (구독 분포 통계용).
     *
     * <p>{@code UserSubscription}은 {@code plan} 필드로 {@link SubscriptionPlan}을 참조하며,
     * planCode는 SubscriptionPlan의 필드이다. JOIN을 통해 접근한다.
     * 예: "monthly_basic" → 120건, "monthly_premium" → 45건.</p>
     *
     * <p>AdminStatsService.getSubscriptionStats()에서 플랜별 분포 파이 차트 데이터 생성에 사용된다.</p>
     *
     * <p>반환 배열 구조: [planCode(String), count(Long)]</p>
     *
     * @return Object 배열 목록 — 각 원소: [planCode, 가입자 수] (가입자 수 내림차순)
     */
    @Query("""
            SELECT us.plan.planCode, COUNT(us)
            FROM UserSubscription us
            WHERE us.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.ACTIVE
            GROUP BY us.plan.planCode
            ORDER BY COUNT(us) DESC
            """)
    List<Object[]> countActiveByPlanType();

    // ──────────────────────────────────────────────
    // 매출/구독 통계 확장 (Phase 2 — 2026-04-28)
    // ──────────────────────────────────────────────

    /**
     * 기간 내 신규 구독 생성 건수 (createdAt 기준).
     *
     * <p>이번 달 또는 최근 N일 신규 구독자 카운팅. 상태 무관(ACTIVE/CANCELLED/EXPIRED 모두 포함).</p>
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 신규 구독 건수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 기간 내 취소된 구독 건수 (status=CANCELLED, cancelledAt 기준).
     *
     * <p>UserSubscription.cancelledAt 전용 컬럼을 사용하여 정확한 취소 시점 집계.</p>
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 취소 건수
     */
    @Query("""
            SELECT COUNT(s) FROM UserSubscription s
            WHERE s.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.CANCELLED
              AND s.cancelledAt >= :start AND s.cancelledAt < :end
            """)
    long countCancelledBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 활성 구독의 플랜별 가격 합계 (= 플랜별 MRR 추정).
     *
     * <p>plan.price * count(active) 형태로 플랜별 월/연 매출 기여도 계산.
     * 연간 플랜은 서비스 레이어에서 /12 환산.</p>
     *
     * <p>반환: [planCode(String), planName(String), periodType(PeriodType), price(Integer), count(Long)]</p>
     *
     * @return Object[] 리스트
     */
    @Query("""
            SELECT us.plan.planCode, us.plan.name, us.plan.periodType, us.plan.price, COUNT(us)
            FROM UserSubscription us
            WHERE us.status = com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription.Status.ACTIVE
            GROUP BY us.plan.planCode, us.plan.name, us.plan.periodType, us.plan.price
            ORDER BY us.plan.price * COUNT(us) DESC
            """)
    List<Object[]> sumMrrByPlan();
}
