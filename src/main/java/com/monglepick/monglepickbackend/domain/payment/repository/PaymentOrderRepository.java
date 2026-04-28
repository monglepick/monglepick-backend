package com.monglepick.monglepickbackend.domain.payment.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 주문 리포지토리 — payment_orders 테이블 데이터 접근.
 *
 * <p>결제 주문의 생성, 조회, 상태 변경을 지원한다.
 * Toss Payments 결제 플로우에서 주문 생성(PENDING) → 결제 확인(COMPLETED/FAILED) →
 * 환불(REFUNDED) 전 과정에 걸쳐 사용된다.</p>
 *
 * <h3>PK 특이사항</h3>
 * <p>payment_orders의 PK인 {@code order_id}는 VARCHAR(50)이며,
 * AUTO_INCREMENT가 아닌 UUID를 직접 생성하여 사용한다.
 * 따라서 JpaRepository의 제네릭 ID 타입은 {@code String}이다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByPaymentOrderId(String)} — 주문 UUID로 조회 (PG 결제 확인 콜백에서 사용)</li>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc(String, Pageable)} — 사용자 결제 이력 (페이징)</li>
 *   <li>{@link #sumAmountByStatusAndCreatedAtBetween} — 기간 내 완료 결제 금액 합계 (매출 통계)</li>
 *   <li>{@link #countByStatusAndCreatedAtBetween} — 기간 내 결제 건수 집계 (ARPU 계산용)</li>
 *   <li>{@link #sumAmountByStatusAndCreatedAtAfter} — 기준 시각 이후 누적 매출 (MRR 계산용)</li>
 *   <li>{@link #findDistinctPayerCountAfter} — 기간 내 결제 고유 사용자 수 (ARPU 분모)</li>
 * </ul>
 *
 * @see PaymentOrder 결제 주문 엔티티
 */
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    /**
     * 주문 UUID로 결제 주문을 조회한다.
     *
     * <p>Toss Payments 결제 확인(confirm) 콜백에서 orderId로 주문을 찾아
     * 상태를 COMPLETED 또는 FAILED로 변경할 때 사용한다.
     * PK 조회이므로 {@code findById()}와 동일하지만, 명시적인 메서드명을 제공한다.</p>
     *
     * @param orderId 주문 UUID (PG에 전달된 주문 번호)
     * @return 결제 주문 (존재하지 않으면 empty)
     */
    Optional<PaymentOrder> findByPaymentOrderId(String orderId);

    /**
     * 사용자의 결제 이력을 최신순으로 페이징 조회한다.
     *
     * <p>마이페이지의 결제 내역 화면에서 사용한다.
     * 모든 상태(PENDING, COMPLETED, FAILED, REFUNDED)의 주문이 포함되며,
     * 주문 생성 시각 기준 최신순으로 정렬된다.</p>
     *
     * <p>페이징 파라미터 예시:</p>
     * <pre>
     * Pageable pageable = PageRequest.of(0, 20);  // 첫 페이지, 20건
     * </pre>
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 정보 (페이지 번호, 페이지 크기)
     * @return 결제 주문 페이지 (최신순 정렬)
     */
    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 사용자의 결제 이력을 특정 상태 집합만 포함하여 최신순으로 페이징 조회한다.
     *
     * <p>사용자용 마이페이지 "결제 내역" 화면 전용 쿼리다.
     * PG 표준 플로우에 따라 {@code createOrder}가 호출되는 즉시 PENDING 레코드가 DB에
     * 저장되지만, 사용자가 결제창에서 이탈하거나 결제가 실패한 경우 FAILED 레코드까지
     * 사용자에게 노출되면 "결제 내역에 실패가 잔뜩 쌓여있다"는 혼란이 발생한다.</p>
     *
     * <p>따라서 사용자용 조회에서는 보통 {@code [COMPLETED, REFUNDED]} 만 허용하고,
     * PENDING/FAILED/COMPENSATION_FAILED 는 관리자 화면 전용으로 격리한다.
     * 관리자 조회는 기존 {@link #findByUserIdOrderByCreatedAtDesc} 를 그대로 사용하여
     * 모든 상태가 보이도록 유지한다.</p>
     *
     * @param userId   사용자 ID
     * @param statuses 포함할 상태 집합 (예: [COMPLETED, REFUNDED])
     * @param pageable 페이징 정보
     * @return 해당 상태만 포함된 결제 주문 페이지 (최신순)
     */
    Page<PaymentOrder> findByUserIdAndStatusInOrderByCreatedAtDesc(
            String userId,
            List<PaymentOrder.OrderStatus> statuses,
            Pageable pageable);

    /**
     * 멱등키로 기존 주문을 조회한다.
     *
     * <p>클라이언트가 동일한 Idempotency-Key로 재요청한 경우
     * 기존 주문을 반환하여 중복 생성을 방지한다.</p>
     *
     * @param idempotencyKey 클라이언트가 전달한 멱등키
     * @return 기존 주문 (없으면 empty)
     */
    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    /**
     * 주문 UUID로 결제 주문을 비관적 쓰기 잠금(SELECT ... FOR UPDATE)으로 조회한다.
     *
     * <h4>사용 목적</h4>
     * <p>2-Phase 결제 승인의 Phase 2 시작 시점에 사용한다.
     * Toss API 호출 이후 DB 상태 변경 직전에 동일 주문에 대한 동시 처리(중복 새로고침,
     * 중복 탭 결제 등)를 DB 레벨에서 완전히 차단한다.</p>
     *
     * <h4>2-Phase 분리 흐름에서의 역할</h4>
     * <ol>
     *   <li>Phase 1: 트랜잭션 없음 — Toss API 호출 (DB 커넥션 미점유)</li>
     *   <li>Phase 2: {@code @Transactional} 시작 → 이 메서드로 FOR UPDATE 잠금 획득
     *       → 상태 변경 → 커밋 시 잠금 해제</li>
     * </ol>
     *
     * <h4>왜 FOR UPDATE인가?</h4>
     * <p>Phase 1과 Phase 2 사이에 동일 orderId로 중복 요청이 도달할 수 있다.
     * FOR UPDATE 없이 단순 조회 후 PENDING 검증만 하면 두 요청이 모두 PENDING을 읽고
     * 각각 처리에 진입하는 TOCTOU(Time-of-Check-Time-of-Use) 경쟁이 발생한다.
     * FOR UPDATE는 첫 번째 요청이 커밋하기 전까지 두 번째 요청을 대기시켜
     * 한 요청만 처리하고 나머지는 COMPLETED 상태를 보고 멱등 응답을 반환하도록 강제한다.</p>
     *
     * <h4>주의사항</h4>
     * <p>반드시 {@code @Transactional} 컨텍스트 내부에서 호출해야 한다.
     * 트랜잭션 외부에서 호출하면 잠금이 즉시 해제되어 의미가 없다.</p>
     *
     * @param orderId 주문 UUID
     * @return 잠금이 걸린 결제 주문 (없으면 empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.paymentOrderId = :orderId")
    Optional<PaymentOrder> findByPaymentOrderIdForUpdate(@Param("orderId") String orderId);

    /**
     * 특정 상태와 생성 시각 범위에 해당하는 결제 주문의 금액 합계를 조회한다.
     *
     * <p>관리자 대시보드 KPI 카드 및 추이 차트에서 일별 결제 금액 집계에 사용된다.
     * 결제가 없는 경우 SUM이 NULL을 반환하므로 서비스 레이어에서 null 처리가 필요하다.</p>
     *
     * <p>예시: 오늘 COMPLETED 결제 금액 합계</p>
     * <pre>
     * Long total = repository.sumAmountByStatusAndCreatedAtBetween(
     *     PaymentOrder.OrderStatus.COMPLETED, todayStart, todayEnd);
     * long safeTotal = total != null ? total : 0L;
     * </pre>
     *
     * @param status 주문 상태 (주로 COMPLETED)
     * @param start  범위 시작 시각 (inclusive)
     * @param end    범위 종료 시각 (exclusive)
     * @return 해당 범위의 금액 합계 (결제가 없으면 null)
     */
    @Query("SELECT SUM(p.amount) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt >= :start AND p.createdAt < :end")
    Long sumAmountByStatusAndCreatedAtBetween(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 생성 시각 기준 최신 순으로 결제 주문 목록을 페이징 조회한다.
     *
     * <p>관리자 대시보드 최근 활동 피드에서 최근 결제 내역 N건을 조회할 때 사용한다.
     * Pageable의 size로 반환 건수를 제어한다 (예: PageRequest.of(0, 20)).</p>
     *
     * @param pageable 페이지 정보 (page=0, size=N 으로 상위 N건 조회)
     * @return 최신순 결제 주문 목록
     */
    @Query("SELECT p FROM PaymentOrder p ORDER BY p.createdAt DESC")
    List<PaymentOrder> findTopByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 지정된 상태와 기간 내 결제 건수를 집계한다.
     *
     * <p>관리자 통계 탭의 ARPU(Average Revenue Per User) 계산 및
     * 기간별 결제 건수 추이에 사용된다.</p>
     *
     * @param status 주문 상태 (주로 COMPLETED)
     * @param start  범위 시작 시각 (inclusive)
     * @param end    범위 종료 시각 (exclusive)
     * @return 해당 범위의 결제 건수
     */
    long countByStatusAndCreatedAtBetween(
            PaymentOrder.OrderStatus status,
            LocalDateTime start,
            LocalDateTime end);

    /**
     * 지정 시각 이후 특정 상태 결제의 금액 합계를 반환한다.
     *
     * <p>MRR(Monthly Recurring Revenue) 계산에서 이번 달 1일 이후의
     * COMPLETED 결제 합계를 구할 때 사용한다.</p>
     *
     * @param status 주문 상태 (주로 COMPLETED)
     * @param after  기준 시각 (이 시각 이후 레코드만 포함)
     * @return 해당 기간 금액 합계 (레코드 없으면 null)
     */
    @Query("SELECT SUM(p.amount) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after")
    Long sumAmountByStatusAndCreatedAtAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    /**
     * 지정 시각 이후 특정 상태로 결제한 고유 사용자 수를 반환한다.
     *
     * <p>ARPU = 총 매출 / 결제 고유 사용자 수 계산에서 분모로 사용된다.
     * DISTINCT COUNT로 동일 사용자의 중복 결제를 1명으로 처리한다.</p>
     *
     * @param status 주문 상태 (주로 COMPLETED)
     * @param after  기준 시각
     * @return 해당 기간 내 결제한 고유 사용자 수
     */
    @Query("SELECT COUNT(DISTINCT p.userId) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after")
    long findDistinctPayerCountAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    /**
     * 지정된 상태이면서 생성 시각이 기준 시각 이전인 주문 목록을 조회한다.
     *
     * <p>PENDING 주문 자동 정리 스케줄러({@code PaymentSchedulerService#cleanupExpiredPendingOrders})
     * 에서 30분 이상 방치된 PENDING 주문을 찾아 FAILED 처리할 때 사용한다.</p>
     *
     * <p>예시: 30분 이상 경과한 PENDING 주문 조회</p>
     * <pre>
     * LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
     * List&lt;PaymentOrder&gt; expired = repository.findByStatusAndCreatedAtBefore(
     *     PaymentOrder.OrderStatus.PENDING, cutoff);
     * </pre>
     *
     * @param status  주문 상태 (주로 PENDING)
     * @param cutoff  기준 시각 — 이 시각 이전에 생성된 주문만 반환
     * @return 기준 시각 이전에 생성된 지정 상태 주문 목록
     */
    List<PaymentOrder> findByStatusAndCreatedAtBefore(
            PaymentOrder.OrderStatus status,
            LocalDateTime cutoff);

    /**
     * 기간 내 결제 수단(pgProvider)별 금액 합계를 조회한다.
     *
     * <p>관리자 통계 탭의 결제 수단 분포(PaymentMethodDistribution) 계산에 사용된다.
     * 반환값은 [pgProvider, totalAmount] 형태의 Object 배열 리스트다.</p>
     *
     * @param status 주문 상태 (주로 COMPLETED)
     * @param after  기준 시각
     * @return [pgProvider (String), totalAmount (Long)] 형태의 Object[] 리스트
     */
    @Query("SELECT p.pgProvider, SUM(p.amount) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after " +
           "GROUP BY p.pgProvider")
    List<Object[]> sumAmountGroupByProviderAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    // ──────────────────────────────────────────────
    // 매출 통계 확장 쿼리 (Phase 2 — 2026-04-28)
    // ──────────────────────────────────────────────

    /**
     * 전체 누적 매출(COMPLETED) 합계.
     *
     * <p>서비스 시작 이후 모든 완료 결제 금액의 합. 환불은 차감하지 않음 — 환불은 별도 집계로 표시.</p>
     *
     * @return 누적 매출 (없으면 null)
     */
    @Query("SELECT SUM(p.amount) FROM PaymentOrder p WHERE p.status = :status")
    Long sumAmountByStatus(@Param("status") PaymentOrder.OrderStatus status);

    /**
     * 상태별 전체 건수.
     *
     * @param status 주문 상태
     * @return 건수
     */
    long countByStatus(PaymentOrder.OrderStatus status);

    /**
     * 환불 통계 — 기간 내 REFUNDED 주문의 환불 금액 합계.
     *
     * <p>refundAmount 컬럼이 null 인 경우(부분 환불 정보 누락) 주문 amount 로 폴백한다.</p>
     *
     * @param after 기준 시각
     * @return 환불 금액 합계 (없으면 null)
     */
    @Query("SELECT SUM(COALESCE(p.refundAmount, p.amount)) FROM PaymentOrder p " +
           "WHERE p.status = com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder.OrderStatus.REFUNDED " +
           "AND p.refundedAt > :after")
    Long sumRefundAmountAfter(@Param("after") LocalDateTime after);

    /**
     * 환불 건수 — 기간 내 REFUNDED 건수.
     *
     * @param after 기준 시각
     * @return 환불 건수
     */
    @Query("SELECT COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder.OrderStatus.REFUNDED " +
           "AND p.refundedAt > :after")
    long countRefundedAfter(@Param("after") LocalDateTime after);

    /**
     * 결제 수단(pgProvider)별 매출과 건수를 한 번에 반환.
     *
     * <p>반환: [pgProvider(String), sumAmount(Long), count(Long)]</p>
     *
     * @param status 주문 상태
     * @param after  기준 시각
     * @return Object[] 리스트
     */
    @Query("SELECT p.pgProvider, SUM(p.amount), COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after " +
           "GROUP BY p.pgProvider " +
           "ORDER BY SUM(p.amount) DESC")
    List<Object[]> sumAndCountGroupByProviderAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    /**
     * 주문 유형(orderType)별 매출과 건수.
     *
     * <p>POINT_PACK vs SUBSCRIPTION 분리 — "구독 매출 vs 포인트팩 매출" 비중 분석.</p>
     *
     * <p>반환: [orderType(OrderType), sumAmount(Long), count(Long)]</p>
     *
     * @param status 주문 상태
     * @param after  기준 시각
     * @return Object[] 리스트
     */
    @Query("SELECT p.orderType, SUM(p.amount), COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after " +
           "GROUP BY p.orderType " +
           "ORDER BY SUM(p.amount) DESC")
    List<Object[]> sumAndCountGroupByOrderTypeAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    /**
     * 구독 플랜별 매출과 건수.
     *
     * <p>SUBSCRIPTION 주문에 한해 plan.planCode + plan.name 별 집계.
     * 어느 구독 상품이 매출에 가장 기여하는지 파악.</p>
     *
     * <p>반환: [planCode(String), planName(String), sumAmount(Long), count(Long)]</p>
     *
     * @param after 기준 시각
     * @return Object[] 리스트
     */
    @Query("SELECT p.plan.planCode, p.plan.name, SUM(p.amount), COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder.OrderStatus.COMPLETED " +
           "AND p.orderType = com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder.OrderType.SUBSCRIPTION " +
           "AND p.createdAt > :after " +
           "AND p.plan IS NOT NULL " +
           "GROUP BY p.plan.planCode, p.plan.name " +
           "ORDER BY SUM(p.amount) DESC")
    List<Object[]> sumAndCountGroupByPlanAfter(@Param("after") LocalDateTime after);

    /**
     * 시간대(0~23시)별 매출과 건수.
     *
     * <p>HOUR(createdAt) 기준 그룹화 — "어느 시간대에 결제가 가장 많이 일어나는가" 분석.
     * MySQL 의 HOUR() 함수를 JPQL FUNCTION 으로 호출.</p>
     *
     * <p>반환: [hour(Integer 0~23), sumAmount(Long), count(Long)]</p>
     *
     * @param status 주문 상태
     * @param after  기준 시각
     * @return Object[] 리스트
     */
    @Query("SELECT FUNCTION('HOUR', p.createdAt), SUM(p.amount), COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after " +
           "GROUP BY FUNCTION('HOUR', p.createdAt) " +
           "ORDER BY FUNCTION('HOUR', p.createdAt)")
    List<Object[]> sumAndCountGroupByHourAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    /**
     * 요일별 매출과 건수.
     *
     * <p>MySQL DAYOFWEEK(): 1=일요일 ~ 7=토요일. JPQL FUNCTION 으로 호출.</p>
     *
     * <p>반환: [dayOfWeek(Integer 1~7), sumAmount(Long), count(Long)]</p>
     *
     * @param status 주문 상태
     * @param after  기준 시각
     * @return Object[] 리스트
     */
    @Query("SELECT FUNCTION('DAYOFWEEK', p.createdAt), SUM(p.amount), COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = :status AND p.createdAt > :after " +
           "GROUP BY FUNCTION('DAYOFWEEK', p.createdAt) " +
           "ORDER BY FUNCTION('DAYOFWEEK', p.createdAt)")
    List<Object[]> sumAndCountGroupByWeekdayAfter(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("after") LocalDateTime after);

    /**
     * Top 결제 사용자 — 기간 내 결제 금액 합산 상위 N명.
     *
     * <p>userId 기준 SUM(amount) 정렬 → 상위 N명. 닉네임은 서비스 레이어에서 UserMapper 로 조인.</p>
     *
     * <p>반환: [userId(String), totalAmount(Long), orderCount(Long)]</p>
     *
     * @param after    기준 시각
     * @param pageable 상위 N건 (PageRequest.of(0, N))
     * @return Object[] 리스트
     */
    @Query("SELECT p.userId, SUM(p.amount), COUNT(p) FROM PaymentOrder p " +
           "WHERE p.status = com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder.OrderStatus.COMPLETED " +
           "AND p.createdAt > :after " +
           "GROUP BY p.userId " +
           "ORDER BY SUM(p.amount) DESC")
    List<Object[]> findTopPayersAfter(@Param("after") LocalDateTime after, Pageable pageable);
}
