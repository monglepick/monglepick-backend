package com.monglepick.monglepickbackend.domain.payment.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 주문 엔티티 — payment_orders 테이블 매핑.
 *
 * <p>Toss Payments 결제를 추적하는 테이블.
 * {@code paymentOrderId}는 UUID로 직접 생성하여 PG에 전달하며, PK로 사용한다.
 * AUTO_INCREMENT가 아닌 VARCHAR(50) PK이므로 {@code @GeneratedValue} 없이
 * 서비스 레이어에서 UUID를 생성하여 설정한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 *   <li>2026-03-24: PK 필드명 orderId → paymentOrderId 로 변경, @Column(name = "payment_order_id") 추가</li>
 *   <li>2026-03-24: FK 필드 plan의 @JoinColumn(name = "plan_id") → @JoinColumn(name = "subscription_plan_id") 변경</li>
 * </ul>
 *
 * <h3>상태 전이 (State Transition)</h3>
 * <pre>
 * PENDING ──→ COMPLETED (PG 결제 성공, pgTransactionId 기록)
 * PENDING ──→ FAILED    (PG 결제 실패, failedReason 기록)
 * COMPLETED ──→ REFUNDED (환불 처리)
 * </pre>
 *
 * <h3>주문 유형 (OrderType)</h3>
 * <ul>
 *   <li>{@code POINT_PACK} — 포인트팩 구매 (일회성, pointsAmount에 지급 포인트 수량)</li>
 *   <li>{@code SUBSCRIPTION} — 구독 결제 (plan에 구독 상품 참조)</li>
 * </ul>
 *
 * <h3>도메인 메서드</h3>
 * <ul>
 *   <li>{@link #complete(String, String)} — 결제 완료 (PG 거래 ID + 제공자 기록)</li>
 *   <li>{@link #fail(String)} — 결제 실패 (실패 사유 기록)</li>
 *   <li>{@link #refund()} — 환불 처리</li>
 * </ul>
 *
 * @see SubscriptionPlan 구독 상품 마스터 (구독 결제 시 참조)
 */
@Entity
@Table(
        name = "payment_orders",
        indexes = {
                @Index(name = "idx_order_user", columnList = "user_id"),
                @Index(name = "idx_order_status", columnList = "status"),
                @Index(name = "idx_order_created", columnList = "created_at"),
                @Index(name = "uk_order_idempotency", columnList = "idempotency_key", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseTimeEntity → BaseAuditEntity 변경: created_by, updated_by 컬럼 추가 관리 */
public class PaymentOrder extends BaseAuditEntity {

    // ──────────────────────────────────────────────
    // PK — UUID 직접 생성 (AUTO_INCREMENT 아님)
    // ──────────────────────────────────────────────

    /**
     * 주문 UUID (VARCHAR(50), PK).
     * PG사에 전달되는 고유 주문 번호.
     * 서비스 레이어에서 {@code UUID.randomUUID().toString()} 등으로 생성한다.
     * AUTO_INCREMENT가 아니므로 {@code @GeneratedValue} 없이 직접 설정.
     * 기존 필드명 'orderId'에서 'paymentOrderId'로 변경하여 엔티티 식별 명확화.
     * 기존 컬럼명 'order_id'에서 'payment_order_id'로 변경.
     */
    @Id
    @Column(name = "payment_order_id", length = 50)
    private String paymentOrderId;

    // ──────────────────────────────────────────────
    // 주문 기본 정보
    // ──────────────────────────────────────────────

    /**
     * 주문자 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 주문 유형 (ENUM: POINT_PACK, SUBSCRIPTION).
     * DDL의 {@code ENUM('point_pack','subscription')}에 매핑된다.
     * {@link OrderType} 내부 enum 사용.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    /**
     * 결제 금액 (INT, NOT NULL, KRW 원 단위).
     * Toss Payments에 전달되는 실결제 금액.
     */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /**
     * 지급 예정 포인트 (INT, nullable).
     * 포인트팩 주문인 경우에만 설정. 구독 주문에서는 null.
     * 결제 완료 시 이 수량만큼 사용자 포인트에 적립된다.
     */
    @Column(name = "points_amount")
    private Integer pointsAmount;

    /**
     * 구독 상품 (FK → subscription_plans.subscription_plan_id, nullable).
     * 구독 결제인 경우에만 설정. 포인트팩 주문에서는 null.
     * LAZY 로딩으로 N+1 쿼리를 방지한다.
     * 기존 @JoinColumn(name = "plan_id") → @JoinColumn(name = "subscription_plan_id")로 변경.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id")
    private SubscriptionPlan plan;

    /**
     * 멱등키 (VARCHAR(100), UNIQUE, nullable).
     * 클라이언트가 Idempotency-Key 헤더로 전달하는 UUID.
     * 동일 멱등키로 중복 주문 생성을 방지하여 네트워크 재시도 안전성을 보장한다.
     */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    // ──────────────────────────────────────────────
    // 주문 상태
    // ──────────────────────────────────────────────

    /**
     * 주문 상태 (ENUM: PENDING, COMPLETED, FAILED, REFUNDED).
     * 기본값: PENDING.
     * DDL의 {@code ENUM('pending','completed','failed','refunded')}에 매핑된다.
     * {@link OrderStatus} 내부 enum 사용.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    // ──────────────────────────────────────────────
    // PG 결제 정보
    // ──────────────────────────────────────────────

    /**
     * PG사 거래 ID (VARCHAR(100), nullable).
     * Toss Payments 결제 완료 시 받는 거래 고유 ID.
     * 결제 완료 전에는 null.
     */
    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    /**
     * PG사 이름 (VARCHAR(50), nullable).
     * 결제를 처리한 PG사 식별자.
     * 예: "tosspayments", "kakaopay".
     */
    @Column(name = "pg_provider", length = 50)
    private String pgProvider;

    /**
     * 실패 사유 (VARCHAR(500), nullable).
     * 결제 실패 시 PG사가 반환한 오류 메시지.
     * 성공한 주문에서는 null.
     */
    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    /**
     * 결제 완료 시각 (TIMESTAMP, nullable).
     * PG사 결제 승인 시점. PENDING 상태에서는 null.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Toss 결제 영수증 URL (결제 완료 시 PG사에서 제공) */
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    /** 카드 정보 (마지막 4자리 + 카드사, 예: "1234 / 신한카드") */
    @Column(name = "card_info", length = 100)
    private String cardInfo;

    /** 환불 사유 (부분/전체 환불 시 관리자 또는 사용자가 입력) */
    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    /** 환불 금액 (부분 환불 시 실제 환불 금액, 전체 환불 시 amount와 동일) */
    @Column(name = "refund_amount")
    private Integer refundAmount;

    /** 환불 일시 (환불 처리 완료 시 기록) */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // ──────────────────────────────────────────────
    // 도메인 메서드 (Lombok @Getter only, setter 대신 사용)
    // ──────────────────────────────────────────────

    /**
     * 결제 완료 처리.
     *
     * <p>PG사 결제 승인 후 호출한다. 주문 상태를 {@code COMPLETED}로 변경하고,
     * PG 거래 ID, PG사 이름, 완료 시각을 기록한다.</p>
     *
     * <p>PENDING 상태에서만 호출 가능하다. 이미 완료/실패/환불된 주문에 대해
     * 호출하면 {@link IllegalStateException}을 발생시킨다.</p>
     *
     * @param pgTxId   PG사 거래 ID (Toss Payments 응답의 transactionId)
     * @param provider PG사 이름 (예: "tosspayments")
     * @throws IllegalStateException PENDING이 아닌 상태에서 호출한 경우
     */
    public void complete(String pgTxId, String provider) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "완료 처리할 수 없는 주문 상태: " + this.status
                            + " (PENDING 상태에서만 완료 가능)"
            );
        }
        this.status = OrderStatus.COMPLETED;
        this.pgTransactionId = pgTxId;
        this.pgProvider = provider;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 처리.
     *
     * <p>PG사 결제 승인 실패 시 호출한다. 주문 상태를 {@code FAILED}로 변경하고,
     * 실패 사유를 기록한다.</p>
     *
     * <p>PENDING 상태에서만 호출 가능하다.</p>
     *
     * @param reason 실패 사유 (PG사 반환 오류 메시지)
     * @throws IllegalStateException PENDING이 아닌 상태에서 호출한 경우
     */
    public void fail(String reason) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "실패 처리할 수 없는 주문 상태: " + this.status
                            + " (PENDING 상태에서만 실패 처리 가능)"
            );
        }
        this.status = OrderStatus.FAILED;
        this.failedReason = reason;
    }

    /**
     * 환불 처리.
     *
     * <p>결제 완료된 주문에 대해 환불을 처리한다.
     * 주문 상태를 {@code REFUNDED}로 변경하고 환불 관련 정보를 기록한다.
     * 실제 PG 환불 API 호출은 서비스 레이어에서 수행하며,
     * 이 메서드는 엔티티 상태만 변경한다.</p>
     *
     * <p>COMPLETED 상태에서만 호출 가능하다.</p>
     *
     * @param reason       환불 사유 (nullable)
     * @param refundAmount 환불 금액 (부분 환불 시 실제 금액, 전체 환불 시 amount와 동일)
     * @throws IllegalStateException COMPLETED가 아닌 상태에서 호출한 경우
     */
    public void refund(String reason, Integer refundAmount) {
        if (this.status != OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "환불할 수 없는 주문 상태: " + this.status
                            + " (COMPLETED 상태에서만 환불 가능)"
            );
        }
        this.status = OrderStatus.REFUNDED;
        this.refundReason = reason;
        this.refundAmount = refundAmount;
        this.refundedAt = LocalDateTime.now();
    }

    /**
     * 환불 처리 (전체 환불 — 사유 없음 오버로드).
     *
     * <p>전체 환불 시 환불 금액을 주문 금액(amount)으로 자동 설정한다.</p>
     */
    public void refund() {
        refund(null, this.amount);
    }

    /**
     * 보상 취소 실패 처리 (CRITICAL).
     *
     * <p>다음 두 가지가 모두 실패했을 때 호출한다:</p>
     * <ol>
     *   <li>Toss 결제 승인 후 DB 저장 실패</li>
     *   <li>보상 목적으로 시도한 Toss 결제 취소도 실패</li>
     * </ol>
     *
     * <p>이 상태는 사용자 카드가 청구되었으나 포인트가 미지급된 위험 상태를 의미하며,
     * 관리자의 수동 조치가 반드시 필요하다.
     * 실패 사유를 {@code failedReason} 필드에 기록하여 조치 시 참고할 수 있도록 한다.</p>
     *
     * @param reason 보상 취소 실패 사유 (Toss 에러 메시지 등)
     */
    public void markCompensationFailed(String reason) {
        // PENDING 또는 FAILED 상태에서만 보상 실패 처리 가능
        // (이미 COMPLETED·REFUNDED인 경우는 보상 대상이 아님)
        this.status = OrderStatus.COMPENSATION_FAILED;
        this.failedReason = reason;
    }

    /**
     * COMPENSATION_FAILED 주문을 COMPLETED로 복구한다 (관리자 수동 조치용).
     *
     * <p>관리자가 Toss Payments 콘솔에서 결제 이력을 확인한 뒤,
     * "포인트 수동 지급 후 완료 처리" 경로로 복구할 때 호출한다.</p>
     *
     * <h4>복구 절차</h4>
     * <ol>
     *   <li>관리자가 Toss 결제 이력에서 실제 청구 금액 확인</li>
     *   <li>PointService로 포인트 수동 지급</li>
     *   <li>이 메서드 호출로 주문 상태 COMPLETED 복구</li>
     * </ol>
     *
     * <h4>상태 전이</h4>
     * <pre>COMPENSATION_FAILED → COMPLETED</pre>
     *
     * @param adminNote 복구 메모 (관리자 조치 내용, failedReason 필드에 덮어쓴다)
     * @throws IllegalStateException COMPENSATION_FAILED 상태가 아닐 때 호출한 경우
     */
    public void markRecovered(String adminNote) {
        if (this.status != OrderStatus.COMPENSATION_FAILED) {
            throw new IllegalStateException(
                    "복구 처리할 수 없는 주문 상태: " + this.status
                            + " (COMPENSATION_FAILED 상태에서만 복구 가능)"
            );
        }
        // 상태를 COMPLETED로 복구하고, completedAt과 관리자 메모를 기록한다.
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        // failedReason 필드에 관리자 복구 메모를 덮어쓴다.
        // 기존 실패 사유는 PointsHistory 이력에 남아있으므로 감사 추적에 지장이 없다.
        this.failedReason = adminNote;
    }

    // ──────────────────────────────────────────────
    // 내부 enum
    // ──────────────────────────────────────────────

    /**
     * 주문 유형 enum.
     *
     * <p>DDL의 {@code ENUM('point_pack','subscription')}에 대응한다.</p>
     */
    public enum OrderType {
        /** 포인트팩 일회성 구매 */
        POINT_PACK,
        /** 구독 결제 (월간/연간) */
        SUBSCRIPTION
    }

    /**
     * 주문 상태 enum.
     *
     * <p>DDL의 {@code ENUM('pending','completed','failed','refunded','compensation_failed')}에 대응한다.</p>
     *
     * <h4>상태 전이 규칙</h4>
     * <ul>
     *   <li>PENDING → COMPLETED (결제 성공)</li>
     *   <li>PENDING → FAILED (결제 실패)</li>
     *   <li>COMPLETED → REFUNDED (환불)</li>
     *   <li>PENDING/FAILED → COMPENSATION_FAILED (보상 취소도 실패 — 수동 조치 필요)</li>
     * </ul>
     */
    public enum OrderStatus {
        /** 결제 대기 중 (초기 상태) */
        PENDING,
        /** 결제 완료 */
        COMPLETED,
        /** 결제 실패 */
        FAILED,
        /** 환불 완료 */
        REFUNDED,
        /**
         * 보상 취소 실패 (CRITICAL).
         *
         * <p>Toss 결제 승인은 성공했으나 DB 저장이 실패했고,
         * 보상 목적으로 시도한 Toss 결제 취소까지 실패한 상태.</p>
         *
         * <p>사용자의 카드는 청구되었으나 포인트가 지급되지 않은 상태이므로
         * 반드시 관리자가 수동으로 조치해야 한다.</p>
         *
         * <ul>
         *   <li>조치 방법 1: Toss Payments 콘솔에서 수동 환불</li>
         *   <li>조치 방법 2: 포인트 수동 지급 후 COMPLETED로 상태 변경</li>
         * </ul>
         */
        COMPENSATION_FAILED
    }
}
