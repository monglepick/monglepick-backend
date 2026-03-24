package com.monglepick.monglepickbackend.domain.payment.entity;

import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * 사용자 구독 현황 엔티티 — user_subscriptions 테이블 매핑.
 *
 * <p>사용자의 구독 상태(활성/취소/만료)를 관리한다.
 * 한 사용자가 동시에 active 구독을 2개 이상 가질 수 없으며,
 * 이 제약은 서비스 레이어에서 검증한다.</p>
 *
 * <h3>상태 전이 (State Transition)</h3>
 * <pre>
 * ACTIVE ──→ CANCELLED (사용자 취소 요청, cancelledAt 기록)
 * ACTIVE ──→ EXPIRED   (만료일 도래, 자동 갱신 아닌 경우)
 * CANCELLED ──→ (종료, 만료일까지 혜택 유지 후 자연 종료)
 * </pre>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 구독자 ID (FK → users.user_id)</li>
 *   <li>{@code plan} — 구독 상품 (FK → subscription_plans.plan_id, LAZY)</li>
 *   <li>{@code status} — 구독 상태 (ACTIVE / CANCELLED / EXPIRED)</li>
 *   <li>{@code startedAt} — 구독 시작 시각</li>
 *   <li>{@code expiresAt} — 만료 예정 시각</li>
 *   <li>{@code cancelledAt} — 취소 시각 (nullable, 취소 시에만 기록)</li>
 *   <li>{@code autoRenew} — 자동 갱신 여부 (기본값: true)</li>
 * </ul>
 *
 * <h3>도메인 메서드</h3>
 * <ul>
 *   <li>{@link #cancel()} — 구독 취소 (상태 CANCELLED + 자동 갱신 중지)</li>
 *   <li>{@link #expire()} — 구독 만료 (상태 EXPIRED)</li>
 *   <li>{@link #renew(LocalDateTime)} — 구독 갱신 (만료일 연장 + 상태 ACTIVE)</li>
 * </ul>
 *
 * @see SubscriptionPlan 구독 상품 마스터
 * @see PaymentOrder 결제 주문 (구독 결제 추적)
 */
@Entity
@Table(
        name = "user_subscriptions",
        indexes = {
                @Index(name = "idx_sub_user", columnList = "user_id"),
                @Index(name = "idx_sub_status", columnList = "status"),
                @Index(name = "idx_sub_expires", columnList = "expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserSubscription extends BaseTimeEntity {

    // ──────────────────────────────────────────────
    // PK
    // ──────────────────────────────────────────────

    /** 구독 레코드 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    // ──────────────────────────────────────────────
    // FK / 참조 필드
    // ──────────────────────────────────────────────

    /**
     * 구독자 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     * DDL에서는 FK 제약을 명시적으로 걸지 않았으므로
     * 서비스 레이어에서 유효성 검증을 수행한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 구독 상품 (FK → subscription_plans.plan_id).
     * LAZY 로딩으로 N+1 쿼리를 방지한다.
     * DDL의 {@code CONSTRAINT fk_sub_plan}에 대응.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    // ──────────────────────────────────────────────
    // 구독 상태
    // ──────────────────────────────────────────────

    /**
     * 구독 상태 (ENUM: ACTIVE, CANCELLED, EXPIRED).
     * 기본값: ACTIVE.
     * DDL의 {@code ENUM('active','cancelled','expired')}에 매핑된다.
     * {@link Status} 내부 enum 사용.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    /**
     * 구독 시작 시각 (TIMESTAMP, NOT NULL).
     * 결제 완료 시점에 설정된다.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * 만료 예정 시각 (TIMESTAMP, NOT NULL).
     * 월간 구독: startedAt + 1개월, 연간 구독: startedAt + 1년.
     * 자동 갱신 시 이 값이 연장된다.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 취소 시각 (TIMESTAMP, nullable).
     * 사용자가 구독을 취소한 시점. 취소 후에도 expiresAt까지 혜택 유지.
     * 정상 구독 중에는 null.
     */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * 자동 갱신 여부 (BOOLEAN, 기본값: true).
     * true이면 만료일에 자동으로 결제를 시도한다.
     * 취소 시 false로 변경되어 자동 갱신을 중지한다.
     */
    @Column(name = "auto_renew")
    @Builder.Default
    private Boolean autoRenew = true;

    // ──────────────────────────────────────────────
    // 도메인 메서드 (Lombok @Getter only, setter 대신 사용)
    // ──────────────────────────────────────────────

    /**
     * 구독 취소.
     *
     * <p>구독 상태를 {@code CANCELLED}로 변경하고, 자동 갱신을 중지하며,
     * 취소 시각을 현재 시각으로 기록한다.
     * 취소 후에도 만료일({@code expiresAt})까지 서비스 이용이 가능하다.</p>
     *
     * <p>이미 취소/만료된 구독에 대해 호출하면 {@link IllegalStateException}을 발생시킨다.</p>
     *
     * @throws IllegalStateException 이미 취소되었거나 만료된 구독인 경우
     */
    public void cancel() {
        if (this.status != Status.ACTIVE) {
            throw new IllegalStateException(
                    "취소할 수 없는 구독 상태: " + this.status
                            + " (ACTIVE 상태에서만 취소 가능)"
            );
        }
        this.status = Status.CANCELLED;
        this.autoRenew = false;
        this.cancelledAt = LocalDateTime.now();
    }

    /**
     * 구독 만료 처리.
     *
     * <p>구독 상태를 {@code EXPIRED}로 변경한다.
     * 만료일이 지나고 자동 갱신이 비활성화된 구독에 대해
     * 스케줄러 또는 배치 작업에서 호출한다.</p>
     *
     * @throws IllegalStateException 이미 만료된 구독인 경우
     */
    public void expire() {
        if (this.status == Status.EXPIRED) {
            throw new IllegalStateException("이미 만료된 구독입니다.");
        }
        this.status = Status.EXPIRED;
    }

    /**
     * 구독 갱신 (자동 결제 성공 시 호출).
     *
     * <p>만료일을 새로운 기간으로 연장하고, 상태를 {@code ACTIVE}로 복원한다.
     * 자동 결제 성공 후 호출되므로, 결제 완료 확인 후에만 사용해야 한다.</p>
     *
     * @param newExpiresAt 새로운 만료 예정 시각 (현재 만료일 + 구독 주기)
     * @throws IllegalArgumentException newExpiresAt이 null인 경우
     */
    public void renew(LocalDateTime newExpiresAt) {
        if (newExpiresAt == null) {
            throw new IllegalArgumentException("새 만료일은 null일 수 없습니다.");
        }
        this.expiresAt = newExpiresAt;
        this.status = Status.ACTIVE;
    }

    // ──────────────────────────────────────────────
    // 내부 enum
    // ──────────────────────────────────────────────

    /**
     * 구독 상태 enum.
     *
     * <p>DDL의 {@code ENUM('active','cancelled','expired')}에 대응한다.
     * {@code @Enumerated(EnumType.STRING)}으로 문자열 저장.</p>
     */
    public enum Status {
        /** 활성 구독 (서비스 이용 가능) */
        ACTIVE,
        /** 취소됨 (만료일까지 혜택 유지, 이후 종료) */
        CANCELLED,
        /** 만료됨 (서비스 이용 불가) */
        EXPIRED
    }
}
