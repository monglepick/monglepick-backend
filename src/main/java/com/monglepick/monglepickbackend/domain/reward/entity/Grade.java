package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 등급 마스터 엔티티 — grades 테이블 매핑.
 *
 * <p>사용자 등급(NORMAL·BRONZE·SILVER·GOLD·PLATINUM·DIAMOND)에 대한 기준 및 쿼터 설정을
 * DB에서 관리하는 마스터 테이블이다. 관리자 페이지에서 등급 기준/쿼터를 동적으로 변경할 수 있다.</p>
 *
 * <h3>v3.2 AI 이용 흐름 — daily vs monthly 분리</h3>
 * <ul>
 *   <li>{@code daily_ai_limit}: 하루에 <b>무료</b>로 사용할 수 있는 AI 추천 횟수 (등급 기본 혜택).
 *       -1이면 무제한(DIAMOND). 이 횟수 소진 후 구독 보너스 → 구매 이용권 순으로 사용.</li>
 *   <li>{@code monthly_ai_limit}: 구매한 AI <b>이용권(쿠폰)</b>의 월간 사용 한도.
 *       이용권은 일일 무료 한도를 우회하지만, 이 월간 쿠폰 한도에는 걸림.
 *       -1이면 무제한(DIAMOND).</li>
 *   <li>{@code free_daily_count}: 구독 보너스 없이 순수 기본 무료로 적립되는 일일 AI 횟수.
 *       daily_ai_limit 내에 포함되는 하위 개념으로, 쿼터 계산 보조용 필드.</li>
 * </ul>
 *
 * <h3>v3.2 등급 체계 (엑셀 Table 27 기준, 6등급)</h3>
 * <table border="1">
 *   <tr><th>등급코드</th><th>한글명</th><th>최소포인트</th><th>일일무료</th><th>쿠폰월한도</th><th>최대입력</th><th>배율</th><th>일일상한</th><th>정렬</th><th>구독등급</th></tr>
 *   <tr><td>NORMAL  </td><td>알갱이    </td><td>0      </td><td>3 </td><td>10 </td><td>200  </td><td>1.0</td><td>45 </td><td>1</td><td>-</td></tr>
 *   <tr><td>BRONZE  </td><td>강냉이    </td><td>1,000  </td><td>5 </td><td>30 </td><td>400  </td><td>1.1</td><td>100</td><td>2</td><td>-</td></tr>
 *   <tr><td>SILVER  </td><td>팝콘      </td><td>4,000  </td><td>7 </td><td>60 </td><td>500  </td><td>1.2</td><td>150</td><td>3</td><td>basic</td></tr>
 *   <tr><td>GOLD    </td><td>카라멜팝콘</td><td>6,500  </td><td>10</td><td>80 </td><td>800  </td><td>1.3</td><td>250</td><td>4</td><td>-</td></tr>
 *   <tr><td>PLATINUM</td><td>몽글팝콘  </td><td>10,000 </td><td>15</td><td>120</td><td>3,000</td><td>1.4</td><td>500</td><td>5</td><td>premium</td></tr>
 *   <tr><td>DIAMOND </td><td>몽아일체  </td><td>20,000 </td><td>-1</td><td>-1 </td><td>-1   </td><td>1.5</td><td>0  </td><td>6</td><td>-</td></tr>
 * </table>
 *
 * <h3>구독 등급 보장 (subscriptionPlanType)</h3>
 * <ul>
 *   <li>SILVER = 'basic' → monthly_basic / yearly_basic 구독 시 즉시 SILVER 등급 보장</li>
 *   <li>PLATINUM = 'premium' → monthly_premium / yearly_premium 구독 시 즉시 PLATINUM 등급 보장</li>
 *   <li>구독 해지 시 earned_by_activity 기반 등급으로 복귀</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성</li>
 *   <li>2026-04-02 v3.0: free_daily_count 필드 제거, 3-소스 AI 모델 전환.</li>
 *   <li>2026-04-02 v3.1: monthly_ai_limit 복원, 4-단계 AI 모델.</li>
 *   <li>2026-04-03 v3.2: 엑셀 Table 27 기준 전면 재설계.
 *       DIAMOND 등급 신규 추가 (6등급 체계). 한국어명 변경(팝콘 테마).
 *       daily_ai_limit = 일일 무료 횟수로 재정의.
 *       monthly_ai_limit = AI 이용권(쿠폰) 월간 사용 한도로 재정의.
 *       free_daily_count 복원. subscriptionPlanType 신규 추가.
 *       min_points 전면 재조정, daily_earn_cap 현실화.</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository
 * @see com.monglepick.monglepickbackend.domain.reward.config.GradeInitializer
 * @see com.monglepick.monglepickbackend.domain.reward.entity.UserPoint
 */
@Entity
@Table(name = "grades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자 — 외부 직접 생성 금지
@AllArgsConstructor
@Builder
public class Grade extends BaseAuditEntity {

    /**
     * 등급 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Long gradeId;

    /**
     * 등급 코드 (VARCHAR(20), NOT NULL, UNIQUE).
     *
     * <p>NORMAL / BRONZE / SILVER / GOLD / PLATINUM / DIAMOND 중 하나 (대문자).
     * QuotaService 및 PointService에서 문자열 비교로 등급을 식별한다.</p>
     */
    @Column(name = "grade_code", nullable = false, unique = true, length = 20)
    private String gradeCode;

    /**
     * 등급 한글 표시명 (VARCHAR(50)).
     *
     * <p>클라이언트 UI 표시용 한글 명칭 (팝콘 테마 v3.2)
     * 알갱이 / 강냉이 / 팝콘 / 카라멜팝콘 / 몽글팝콘 / 몽아일체</p>
     */
    @Column(name = "grade_name", length = 50)
    private String gradeName;

    /**
     * 최소 누적 활동 포인트 (INT, NOT NULL).
     *
     * <p>이 등급에 도달하기 위한 최소 누적 활동 포인트 ({@code user_points.earned_by_activity}).
     * 결제 충전 포인트는 제외 — 구매→환불 반복으로 등급 악용 방지.
     * v3.2 기준: NORMAL=0, BRONZE=1000, SILVER=4000, GOLD=6500, PLATINUM=10000, DIAMOND=20000</p>
     */
    @Column(name = "min_points", nullable = false)
    private Integer minPoints;

    /**
     * 일일 무료 AI 추천 한도 (INT) — daily_ai_limit.
     *
     * <p>하루에 <b>무료</b>로 사용할 수 있는 AI 추천 횟수 (등급 기본 혜택).
     * 이 횟수를 소진하면 구독 보너스 → 구매 이용권(쿠폰) 순으로 전환된다.
     * -1이면 무제한 (DIAMOND 등급).
     * v3.2 기준: NORMAL=3, BRONZE=5, SILVER=7, GOLD=10, PLATINUM=15, DIAMOND=-1</p>
     *
     * <p><b>주의</b>: 구매 AI 이용권(쿠폰)은 이 일일 무료 한도를 우회한다.
     * 이용권 사용 시 적용되는 한도는 {@code monthly_ai_limit} (월간 쿠폰 한도)이다.</p>
     */
    @Column(name = "daily_ai_limit")
    private Integer dailyAiLimit;

    /**
     * AI 이용권(쿠폰) 월간 사용 제한 횟수 (INT) — monthly_ai_limit.
     *
     * <p>구매한 AI 이용권(쿠폰)을 한 달에 사용할 수 있는 최대 횟수.
     * 이용권은 {@code daily_ai_limit}(일일 무료 한도)를 우회하지만,
     * 이 월간 쿠폰 한도에는 반드시 걸린다.
     * -1이면 무제한 (DIAMOND 등급).
     * v3.2 기준: NORMAL=10, BRONZE=30, SILVER=60, GOLD=80, PLATINUM=120, DIAMOND=-1</p>
     */
    @Column(name = "monthly_ai_limit")
    private Integer monthlyAiLimit;

    /**
     * 순수 무료 일일 AI 횟수 (INT) — free_daily_count.
     *
     * <p>구독 보너스 없이 기본으로 적립되는 일일 AI 무료 횟수.
     * daily_ai_limit 내에 포함되는 하위 개념으로 쿼터 계산 보조용.
     * v3.2 기준: NORMAL=0, BRONZE=1, SILVER=2, GOLD=2, PLATINUM=4, DIAMOND=4</p>
     */
    @Column(name = "free_daily_count")
    @Builder.Default
    private Integer freeDailyCount = 0;

    /**
     * 최대 입력 글자 수 (INT).
     *
     * <p>등급 사용자가 AI 채팅 메시지로 입력할 수 있는 최대 글자 수.
     * -1이면 무제한 (DIAMOND 등급).
     * v3.2 기준: NORMAL=200, BRONZE=400, SILVER=500, GOLD=800, PLATINUM=3000, DIAMOND=-1</p>
     */
    @Column(name = "max_input_length")
    private Integer maxInputLength;

    /**
     * 등급별 리워드 배율 (DECIMAL(3,2), NOT NULL, DEFAULT 1.00).
     *
     * <p>활동 리워드(point_type='earn') 지급 시 기본 포인트에 이 배율을 곱한다.
     * bonus 타입에는 배율 미적용.
     * v3.2 기준: NORMAL=1.0, BRONZE=1.1, SILVER=1.2, GOLD=1.3, PLATINUM=1.4, DIAMOND=1.5</p>
     */
    @Column(name = "reward_multiplier", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rewardMultiplier = BigDecimal.ONE;

    /**
     * 일일 활동 리워드 총 획득 상한 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>하루에 활동 리워드로 획득할 수 있는 최대 포인트 (earn 타입만 적용).
     * 0이면 무제한 (DIAMOND).
     * v3.2 기준: NORMAL=45, BRONZE=100, SILVER=150, GOLD=250, PLATINUM=500, DIAMOND=0(무제한)</p>
     *
     * <p>{@code user_points.daily_cap_used}와 함께 사용:
     * daily_cap_used + amount {@literal >} daily_earn_cap 이면 추가 리워드 차단.</p>
     */
    @Column(name = "daily_earn_cap", nullable = false)
    @Builder.Default
    private Integer dailyEarnCap = 0;

    /**
     * 정렬 순서 (INT).
     *
     * <p>등급 목록 조회 시 표시 순서 (1-indexed).
     * NORMAL=1, BRONZE=2, SILVER=3, GOLD=4, PLATINUM=5, DIAMOND=6</p>
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 구독 상품 연동 등급 기준 코드 (VARCHAR(20), NULL 가능) — subscription_plan_type.
     *
     * <p>v3.2 신규: 어떤 구독 플랜이 이 등급을 즉시 보장하는지를 나타낸다.
     * <ul>
     *   <li>NULL : 구독으로 즉시 보장되지 않는 등급 (NORMAL, BRONZE, GOLD, DIAMOND)</li>
     *   <li>'basic'   : basic 계열 구독 시 이 등급 즉시 보장 (SILVER)</li>
     *   <li>'premium' : premium 계열 구독 시 이 등급 즉시 보장 (PLATINUM)</li>
     * </ul>
     * QuotaService에서 활성 구독 플랜의 guaranteedGradeCode와 비교하여
     * effective_grade = max(earned_grade, subscription_guaranteed_grade)로 계산.</p>
     */
    @Column(name = "subscription_plan_type", length = 20)
    private String subscriptionPlanType;

    /**
     * 활성 여부 (TINYINT(1), 기본값 true).
     *
     * <p>비활성화된 등급은 조회 시 제외된다.
     * 등급 삭제 대신 비활성화하여 이력을 보존한다.</p>
     */
    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    // ──────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드 사용)
    // ──────────────────────────────────────────────

    /**
     * 등급 쿼터 설정을 갱신한다.
     *
     * <p>관리자 페이지에서 등급별 한도를 변경할 때 사용한다.
     * null 파라미터는 기존 값을 유지한다.</p>
     *
     * @param dailyAiLimit     새 일일 무료 한도 (null이면 변경 안 함, -1이면 무제한)
     * @param monthlyAiLimit   새 쿠폰 월간 한도 (null이면 변경 안 함, -1이면 무제한)
     * @param freeDailyCount   새 일일 자동 지급 이용권 수 (null이면 변경 안 함)
     * @param maxInputLength   새 최대 입력 글자 수 (null이면 변경 안 함, -1이면 무제한)
     * @param rewardMultiplier 새 리워드 배율 (null이면 변경 안 함)
     * @param dailyEarnCap     새 일일 활동 리워드 상한 (null이면 변경 안 함, 0이면 무제한)
     */
    public void updateQuota(Integer dailyAiLimit, Integer monthlyAiLimit, Integer freeDailyCount,
                            Integer maxInputLength, BigDecimal rewardMultiplier, Integer dailyEarnCap) {
        if (dailyAiLimit != null) this.dailyAiLimit = dailyAiLimit;
        if (monthlyAiLimit != null) this.monthlyAiLimit = monthlyAiLimit;
        if (freeDailyCount != null) this.freeDailyCount = freeDailyCount;
        if (maxInputLength != null) this.maxInputLength = maxInputLength;
        if (rewardMultiplier != null) this.rewardMultiplier = rewardMultiplier;
        if (dailyEarnCap != null) this.dailyEarnCap = dailyEarnCap;
    }

    /**
     * 등급 활성화/비활성화 상태를 변경한다.
     *
     * @param active true이면 활성화, false이면 비활성화
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }

    /**
     * 구독 보장 플랜 타입({@code subscription_plan_type})을 보정한다.
     *
     * <p>2026-04-14 신설 — {@link com.monglepick.monglepickbackend.domain.reward.config.GradeInitializer}
     * 가 v3.2 이전 환경에서 NULL 로 남은 SILVER/PLATINUM 의 컬럼 값을 정합성 보정할 때 사용한다.</p>
     *
     * <p>이 메서드는 {@code GradeInitializer} 의 부팅 시 1회 보정 외 일반 비즈니스 흐름에서
     * 호출되어선 안 된다. 등급의 구독 매핑은 시드 데이터 차원에서 결정되는 값이며,
     * 운영 중 임의 변경은 결제·등급 보장 정책 전체에 파급된다.</p>
     *
     * @param type 새 subscription_plan_type 값 ('basic' / 'premium' / null)
     */
    public void assignSubscriptionPlanType(String type) {
        this.subscriptionPlanType = type;
    }
}
