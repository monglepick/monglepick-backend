package com.monglepick.monglepickbackend.domain.payment.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
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
 * <h3>v3.0 변경 — AI 3-소스 모델</h3>
 * <p>구독의 역할이 변경되었다. 기존에는 포인트를 대량 지급하여 AI 이용을 간접 지원했으나,
 * v3.0에서는 {@code monthly_ai_bonus} 필드를 통해 AI 쿼터를 직접 증가시킨다.</p>
 * <ul>
 *   <li>추가: {@code monthlyAiBonus} — 구독 활성화 시 매월 갱신되는 AI 보너스 풀 크기</li>
 *   <li>구독 활성화 → UserSubscription.initAiBonus(monthlyAiBonus) 호출</li>
 *   <li>매월 갱신 → UserSubscription.resetAiBonusIfNeeded(today, monthlyAiBonus) 호출</li>
 * </ul>
 *
 * <h3>v3.0 시드 데이터 (SubscriptionPlanInitializer)</h3>
 * <pre>
 * monthly_basic   — 월간 Basic  (4,900원, monthly_ai_bonus=100,  points_per_period=200P)
 * monthly_premium — 월간 Premium (9,900원, monthly_ai_bonus=500,  points_per_period=500P)
 * yearly_basic    — 연간 Basic  (49,000원, monthly_ai_bonus=150,  points_per_period=300P/월)
 * yearly_premium  — 연간 Premium (99,000원, monthly_ai_bonus=700,  points_per_period=800P/월)
 * </pre>
 *
 * <h3>구독 가치 검증</h3>
 * <ul>
 *   <li>NORMAL(3/일) + monthly_basic(+100/월) ≈ 190회/월 가능</li>
 *   <li>monthly_basic: 4,900원/100회 = 49원/회 (vs AI 이용권 구매 160원/회 → 구독 유도)</li>
 *   <li>monthly_premium: 9,900원/500회 = 19.8원/회 (볼륨 할인)</li>
 *   <li>yearly 할인율: monthly×12 대비 약 17% 할인</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 *   <li>2026-03-24: PK 필드명 planId → subscriptionPlanId 로 변경</li>
 *   <li>2026-04-02 v3.0: monthlyAiBonus 필드 추가. 시드 데이터 전면 개편.
 *       pointsPerPeriod 대폭 축소 (포인트 대량 지급 → AI 쿼터 직접 부여 방식으로 전환).</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code planCode} — 상품 고유 코드 (monthly_basic 등, UNIQUE)</li>
 *   <li>{@code name} — 상품명 (UI 표시용)</li>
 *   <li>{@code periodType} — 구독 주기 (MONTHLY / YEARLY)</li>
 *   <li>{@code price} — 가격 (KRW, 원 단위)</li>
 *   <li>{@code monthlyAiBonus} — 매월 갱신되는 AI 보너스 풀 크기 (v3.0 신규)</li>
 *   <li>{@code pointsPerPeriod} — 주기마다 지급되는 포인트 수량 (부가 혜택)</li>
 *   <li>{@code description} — 상품 설명 (nullable)</li>
 *   <li>{@code isActive} — 판매 활성화 여부 (기본값: true)</li>
 * </ul>
 *
 * @see UserSubscription 사용자 구독 현황 (FK → subscription_plan_id)
 * @see PaymentOrder 결제 주문 (FK → subscription_plan_id, 구독 결제 시)
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseTimeEntity → BaseAuditEntity 변경: created_by, updated_by 컬럼 추가 관리 */
public class SubscriptionPlan extends BaseAuditEntity {

    // ──────────────────────────────────────────────
    // PK
    // ──────────────────────────────────────────────

    /**
     * 구독 상품 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 기존 필드명 'planId'에서 'subscriptionPlanId'로 변경하여 엔티티 식별 명확화.
     * 기존 컬럼명 'plan_id'에서 'subscription_plan_id'로 변경.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_plan_id")
    private Long subscriptionPlanId;

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
     * 예: "월간 Basic", "연간 Premium".
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
     * 매월 갱신되는 AI 보너스 풀 크기 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>v3.0 신규 필드. 구독 활성화 시 {@code UserSubscription.remaining_ai_bonus}에
     * 이 값이 초기화된다. 매월 1일(또는 구독 갱신 시) 이 값으로 리셋된다.</p>
     *
     * <p>사용 흐름:
     * <ol>
     *   <li>구독 결제 완료 → UserSubscription.initAiBonus(monthlyAiBonus)</li>
     *   <li>AI 요청 시 grade 일일 한도 초과 → UserSubscription.consumeAiBonus()</li>
     *   <li>매월 갱신 → UserSubscription.resetAiBonusIfNeeded(today, monthlyAiBonus)</li>
     * </ol>
     * </p>
     *
     * <p>v3.0 시드 데이터:
     * monthly_basic=100, monthly_premium=500, yearly_basic=150, yearly_premium=700</p>
     */
    @Column(name = "monthly_ai_bonus", nullable = false)
    @Builder.Default
    private Integer monthlyAiBonus = 0;

    /**
     * 주기당 지급 포인트 (INT, NOT NULL).
     *
     * <p>구독 결제 완료 시 사용자에게 지급되는 포인트 수량 (부가 혜택).
     * v3.0에서 대폭 축소됨 — AI 쿼터는 monthlyAiBonus로 직접 부여하므로
     * 포인트 대량 지급 방식을 폐지하고 소액 보너스 포인트만 지급한다.</p>
     *
     * <p>v3.0 시드 데이터: monthly_basic=200P, monthly_premium=500P,
     * yearly_basic=300P/월, yearly_premium=800P/월</p>
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
     * DB에는 대문자("MONTHLY")로 저장된다.
     * MySQL ENUM은 대소문자를 구분하지 않으므로 호환된다.</p>
     */
    public enum PeriodType {
        /** 월간 구독 */
        MONTHLY,
        /** 연간 구독 */
        YEARLY
    }
}
