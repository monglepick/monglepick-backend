package com.monglepick.monglepickbackend.domain.payment.entity;

import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
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
 * {@code orderId}는 UUID로 직접 생성하여 PG에 전달하며, PK로 사용한다.
 * AUTO_INCREMENT가 아닌 VARCHAR(50) PK이므로 {@code @GeneratedValue} 없이
 * 서비스 레이어에서 UUID를 생성하여 설정한다.</p>
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
 *   <li>{@code SUBSCRIPTION} — 구독 결제 (planId에 구독 상품 참조)</li>
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
                @Index(name = "idx_order_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentOrder extends BaseTimeEntity {

    // ──────────────────────────────────────────────
    // PK — UUID 직접 생성 (AUTO_INCREMENT 아님)
    // ──────────────────────────────────────────────

    /**
     * 주문 UUID (VARCHAR(50), PK).
     * PG사에 전달되는 고유 주문 번호.
     * 서비스 레이어에서 {@code UUID.randomUUID().toString()} 등으로 생성한다.
     * AUTO_INCREMENT가 아니므로 {@code @GeneratedValue} 없이 직접 설정.
     */
    @Id
    @Column(name = "order_id", length = 50)
    private String orderId;

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
     * 구독 상품 (FK → subscription_plans.plan_id, nullable).
     * 구독 결제인 경우에만 설정. 포인트팩 주문에서는 null.
     * LAZY 로딩으로 N+1 쿼리를 방지한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan plan;

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
     * 주문 상태를 {@code REFUNDED}로 변경한다.
     * 실제 PG 환불 API 호출은 서비스 레이어에서 수행하며,
     * 이 메서드는 엔티티 상태만 변경한다.</p>
     *
     * <p>COMPLETED 상태에서만 호출 가능하다.</p>
     *
     * @throws IllegalStateException COMPLETED가 아닌 상태에서 호출한 경우
     */
    public void refund() {
        if (this.status != OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "환불할 수 없는 주문 상태: " + this.status
                            + " (COMPLETED 상태에서만 환불 가능)"
            );
        }
        this.status = OrderStatus.REFUNDED;
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
     * <p>DDL의 {@code ENUM('pending','completed','failed','refunded')}에 대응한다.</p>
     *
     * <h4>상태 전이 규칙</h4>
     * <ul>
     *   <li>PENDING → COMPLETED (결제 성공)</li>
     *   <li>PENDING → FAILED (결제 실패)</li>
     *   <li>COMPLETED → REFUNDED (환불)</li>
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
        REFUNDED
    }
}
