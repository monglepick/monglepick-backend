package com.monglepick.monglepickbackend.domain.payment.entity;

import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구독 상품 마스터 엔티티 — subscription_plans 테이블 매핑.
 *
 * <p>Toss Payments와 연동되는 구독 상품 정의를 관리한다.
 * 운영팀이 상품을 등록/수정하며, 삭제 대신 {@code isActive=false}로
 * 비활성화하여 기존 FK 참조를 보존한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code planCode} — 상품 고유 코드 (monthly_basic 등, UNIQUE)</li>
 *   <li>{@code name} — 상품명 (UI 표시용)</li>
 *   <li>{@code periodType} — 구독 주기 (MONTHLY / YEARLY)</li>
 *   <li>{@code price} — 가격 (KRW, 원 단위)</li>
 *   <li>{@code pointsPerPeriod} — 주기마다 지급되는 포인트 수량</li>
 *   <li>{@code description} — 상품 설명 (nullable)</li>
 *   <li>{@code isActive} — 판매 활성화 여부 (기본값: true)</li>
 * </ul>
 *
 * <h3>시드 데이터 (init.sql)</h3>
 * <pre>
 * monthly_basic   — 월간 기본 (3,900원, 3,000P)
 * monthly_premium — 월간 프리미엄 (7,900원, 8,000P)
 * yearly_basic    — 연간 기본 (39,000원, 40,000P)
 * yearly_premium  — 연간 프리미엄 (79,000원, 100,000P)
 * </pre>
 *
 * @see UserSubscription 사용자 구독 현황 (FK → plan_id)
 * @see PaymentOrder 결제 주문 (FK → plan_id, 구독 결제 시)
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SubscriptionPlan extends BaseTimeEntity {

    // ──────────────────────────────────────────────
    // PK
    // ──────────────────────────────────────────────

    /** 구독 상품 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    // ──────────────────────────────────────────────
    // 상품 정보
    // ──────────────────────────────────────────────

    /**
     * 상품 코드 (VARCHAR(50), NOT NULL, UNIQUE).
     * 시스템 내부에서 상품을 식별하는 고유 코드.
     * 예: "monthly_basic", "yearly_premium".
     */
    @Column(name = "plan_code", length = 50, nullable = false, unique = true)
    private String planCode;

    /**
     * 상품명 (VARCHAR(100), NOT NULL).
     * UI에 표시되는 상품 이름.
     * 예: "월간 기본", "연간 프리미엄".
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 구독 주기 (ENUM: MONTHLY, YEARLY).
     * DDL의 ENUM('monthly','yearly')에 매핑된다.
     * {@link PeriodType} 내부 enum 사용.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    /**
     * 가격 (INT, NOT NULL, KRW 원 단위).
     * Toss Payments에 전달되는 결제 금액.
     */
    @Column(name = "price", nullable = false)
    private Integer price;

    /**
     * 주기당 지급 포인트 (INT, NOT NULL).
     * 구독 결제 완료 시 사용자에게 지급되는 포인트 수량.
     * 예: monthly_basic = 3,000P, yearly_premium = 100,000P.
     */
    @Column(name = "points_per_period", nullable = false)
    private Integer pointsPerPeriod;

    /**
     * 상품 설명 (VARCHAR(500), nullable).
     * UI에 표시되는 부가 설명 텍스트.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 판매 활성화 여부 (BOOLEAN, 기본값: true).
     * false로 설정하면 신규 구독 불가. 기존 구독자는 만료까지 유지.
     * 삭제 대신 비활성화하여 FK 참조를 보존한다.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // ──────────────────────────────────────────────
    // 내부 enum
    // ──────────────────────────────────────────────

    /**
     * 구독 주기 enum.
     *
     * <p>DDL의 {@code ENUM('monthly','yearly')}에 대응한다.
     * {@code @Enumerated(EnumType.STRING)}으로 문자열 저장되므로
     * DB에는 소문자("monthly")가 아닌 대문자("MONTHLY")로 저장된다.
     * DDL ENUM 정의와 대소문자가 다르지만, MySQL ENUM은 대소문자를
     * 구분하지 않으므로 호환된다.</p>
     */
    public enum PeriodType {
        /** 월간 구독 */
        MONTHLY,
        /** 연간 구독 */
        YEARLY
    }
}
