package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 포인트 엔티티 — user_points 테이블 매핑.
 *
 * <p>사용자의 포인트 잔액 및 등급 정보를 관리한다.
 * 각 사용자당 하나의 포인트 레코드만 존재한다 (user_id UNIQUE).</p>
 *
 * <h3>v3.3 AI 쿼터 분리</h3>
 * <p>기존 이 엔티티에 있던 AI 쿼터 관련 4개 컬럼
 * ({@code daily_ai_used}, {@code monthly_coupon_used}, {@code monthly_reset},
 * {@code purchased_ai_tokens})을 {@link UserAiQuota} 엔티티로 분리하였다.
 * 이 엔티티는 포인트 잔액·등급 관리에만 집중한다.</p>
 *
 * <h3>이 엔티티의 책임 (v3.3 기준)</h3>
 * <ul>
 *   <li>포인트 잔액 관리 ({@code balance}, {@code total_earned}, {@code total_spent})</li>
 *   <li>활동 리워드 포인트 추적 ({@code earned_by_activity}, {@code daily_cap_used})</li>
 *   <li>일일 포인트 획득 관리 ({@code daily_earned}, {@code daily_reset})</li>
 *   <li>사용자 등급 FK 관리 ({@code grade_id} → {@link Grade})</li>
 * </ul>
 *
 * <h3>AI 쿼터 관련 로직</h3>
 * <p>AI 사용량 카운터 및 이용권 관리는 {@link UserAiQuota} 및
 * {@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService}를 참조한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 *   <li>2026-03-24: PK 필드명 pointId → userPointId 로 변경</li>
 *   <li>2026-03-31: userGrade 필드를 Grade 엔티티 FK로 전환</li>
 *   <li>2026-04-02: monthlyReset/earnedByActivity/dailyCapUsed 추가. addPoints() 파라미터 확장.</li>
 *   <li>2026-04-02 v3.0: purchasedAiTokens 추가 (v3.3에서 UserAiQuota로 이동).</li>
 *   <li>2026-04-02 v3.1: monthlyAiUsed/monthlyReset 복원 (v3.3에서 UserAiQuota로 이동).</li>
 *   <li>2026-04-03 v3.2: monthlyAiUsed → monthlyCouponUsed 명칭 변경 (v3.3에서 UserAiQuota로 이동).</li>
 *   <li>2026-04-07 v3.3: AI 쿼터 4컬럼(daily_ai_used/monthly_coupon_used/monthly_reset/purchased_ai_tokens)
 *       을 UserAiQuota 엔티티로 분리. 관련 도메인 메서드(incrementAiUsage/incrementMonthlyAiUsage/
 *       consumePurchasedToken/addPurchasedTokens/resetMonthlyIfNeeded/resetDailyAiUsed) 제거.
 *       resetDailyIfNeeded()에서 daily_ai_used 리셋 코드 제거.</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID (FK → users.user_id, UNIQUE)</li>
 *   <li>{@code balance} — 현재 보유 포인트</li>
 *   <li>{@code totalEarned} — 누적 획득 포인트</li>
 *   <li>{@code totalSpent} — 누적 사용 포인트</li>
 *   <li>{@code earnedByActivity} — 순수 활동 누적 포인트 (등급 산정 기준, 결제 충전 제외)</li>
 *   <li>{@code dailyEarned} — 오늘 획득 포인트 (일일 한도 관리용)</li>
 *   <li>{@code dailyCapUsed} — 오늘 활동 리워드 총 획득 (일일 상한 검사용)</li>
 *   <li>{@code dailyReset} — 일일 리셋 기준일 (DATE)</li>
 *   <li>{@code grade} — 사용자 등급 (FK → grades.grade_id, LAZY)</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_points",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseTimeEntity → BaseAuditEntity 변경: created_by, updated_by 컬럼 추가 관리 */
public class UserPoint extends BaseAuditEntity {

    /**
     * 포인트 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 기존 필드명 'pointId'에서 'userPointId'로 변경하여 엔티티 식별 명확화.
     * 기존 컬럼명 'point_id'에서 'user_point_id'로 변경.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_point_id")
    private Long userPointId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL, UNIQUE).
     *
     * <p>users.user_id를 참조한다. 사용자 1명당 포인트 레코드는 반드시 1개만 존재해야 한다.</p>
     *
     * <p>{@code unique = true}는 JPA가 DDL을 생성할 때 UK 제약을 컬럼 레벨에 추가한다.
     * 클래스 레벨의 {@code @UniqueConstraint(columnNames = "user_id")}와 동일한 효과이나,
     * 두 가지 방식을 함께 선언하여 JPA 레이어와 DB 레이어 모두에서 중복을 방지한다.</p>
     */
    @Column(name = "user_id", length = 50, nullable = false, unique = true)
    private String userId;

    /**
     * 현재 보유 포인트.
     * 기본값: 0.
     */
    @Column(name = "balance")
    @Builder.Default
    private Integer balance = 0;

    /**
     * 누적 획득 포인트.
     * 기본값: 0.
     * 가입 이후 전체 획득 포인트 합산.
     */
    @Column(name = "total_earned")
    @Builder.Default
    private Integer totalEarned = 0;

    /**
     * 오늘 획득 포인트.
     * 기본값: 0.
     * 일일 포인트 획득 한도 관리에 사용된다.
     */
    @Column(name = "daily_earned")
    @Builder.Default
    private Integer dailyEarned = 0;

    /**
     * 일일 리셋 기준일.
     * dailyEarned/dailyCapUsed가 마지막으로 0으로 초기화된 날짜.
     * 날짜가 바뀌면 위 필드들을 0으로 리셋한다.
     *
     * <p>v3.3: dailyAiUsed는 UserAiQuota.daily_ai_reset으로 분리되었다.</p>
     */
    @Column(name = "daily_reset")
    private LocalDate dailyReset;

    /**
     * 사용자 등급 (FK → grades.grade_id, LAZY).
     *
     * <p>기존 Enum 문자열 컬럼(user_grade VARCHAR)에서 {@code Grade} 엔티티 FK(grade_id BIGINT)로
     * 전환하였다. 이를 통해 관리자 페이지에서 등급 기준/쿼터를 동적으로 관리할 수 있다.</p>
     *
     * <p>null인 경우 서비스 레이어에서 NORMAL 등급으로 fallback 처리한다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    /**
     * 순수 활동으로 획득한 누적 포인트 (등급 산정 기준).
     * 기본값: 0.
     *
     * <p>RewardService.grantReward() 또는 grantRewardWithAmount()를 경유하여 지급된 포인트만 증가.
     * 결제 충전, 관리자 수동 지급은 제외된다.</p>
     *
     * <p><b>등급 산정</b>: {@code WHERE earned_by_activity >= grades.min_points}
     * (total_earned가 아님 — 결제 포인트 구매→환불 반복으로 등급 악용 방지)</p>
     */
    @Column(name = "earned_by_activity", nullable = false)
    @Builder.Default
    private Integer earnedByActivity = 0;

    /**
     * 오늘 활동 리워드로 획득한 총 포인트 (일일 상한 검사용).
     * 기본값: 0.
     *
     * <p>등급별 일일 총 포인트 상한({@code grades.daily_earn_cap})과 비교하여 극단적 어뷰징 방지.
     * 상한 초과 시 해당 일 추가 리워드 차단. 구독 포인트/관리자 지급은 미포함.</p>
     *
     * <p>dailyReset 시 0으로 리셋된다 (resetDailyIfNeeded에서 처리).</p>
     */
    @Column(name = "daily_cap_used", nullable = false)
    @Builder.Default
    private Integer dailyCapUsed = 0;

    /**
     * 총 사용 포인트 (누적).
     * 기본값: 0.
     * 포인트 차감 시마다 증가하며, 관리자 통계 및 사용자 활동 지표로 활용된다.
     * 정합성 공식: balance = total_earned - total_spent
     */
    @Column(name = "total_spent")
    @Builder.Default
    private Integer totalSpent = 0;

    // ──────────────────────────────────────────────
    // 도메인 메서드 (Lombok @Getter only, setter 대신 사용)
    // ──────────────────────────────────────────────

    /**
     * 포인트 차감.
     *
     * <p>잔액(balance)에서 요청 금액을 차감한다.
     * 서비스 레이어에서 사전 검증 후 호출하므로 여기서는 방어적 검증만 수행한다.
     * 잔액이 부족하면 음수 방지를 위해 예외를 발생시킨다.</p>
     *
     * @param amount 차감할 포인트 (양수)
     * @throws IllegalArgumentException 잔액 부족 시 (서비스 레이어에서 이미 검증하므로 정상 흐름에서는 발생하지 않음)
     */
    public void deductPoints(int amount) {
        if (this.balance < amount) {
            throw new IllegalArgumentException(
                    "잔액 부족: 보유=" + this.balance + ", 필요=" + amount
                            + " (서비스 레이어 사전 검증 누락 가능성 — InsufficientPointException 확인 필요)"
            );
        }
        this.balance -= amount;
        // 총 사용 포인트 누적 갱신
        this.totalSpent += amount;
    }

    /**
     * 포인트 획득.
     *
     * <p>보유 포인트(balance), 누적 획득(totalEarned), 일일 획득(dailyEarned)을 갱신한다.
     * 날짜가 바뀌었으면 일일 획득량을 먼저 리셋한 뒤 적용한다.</p>
     *
     * <p>{@code isActivityReward=true}이면 추가로:
     * <ul>
     *   <li>{@code earnedByActivity += amount} — 순수 활동 포인트 누적 (등급 산정 기준)</li>
     *   <li>{@code dailyCapUsed += amount} — 일일 활동 리워드 상한 추적</li>
     * </ul>
     * </p>
     *
     * @param amount           획득 포인트 (양수)
     * @param today            오늘 날짜 (일일 리셋 판단용)
     * @param isActivityReward RewardService 경유 여부. true이면 활동 포인트로 분류 (등급 반영).
     *                         false이면 결제 충전/관리자 지급 (등급 미반영).
     */
    public void addPoints(int amount, LocalDate today, boolean isActivityReward) {
        resetDailyIfNeeded(today);
        this.balance += amount;
        this.totalEarned += amount;
        this.dailyEarned += amount;
        if (isActivityReward) {
            this.earnedByActivity += amount;
            this.dailyCapUsed += amount;
        }
    }

    /**
     * 포인트 획득 (하위 호환용 오버로드 — isActivityReward=false 기본값).
     *
     * <p>결제 충전, 관리자 수동 지급 등 활동 리워드가 아닌 포인트 지급에 사용.
     * earnedByActivity와 dailyCapUsed는 변경되지 않는다.</p>
     *
     * @param amount 획득 포인트 (양수)
     * @param today  오늘 날짜 (일일 리셋 판단용)
     */
    public void addPoints(int amount, LocalDate today) {
        addPoints(amount, today, false);
    }

    /**
     * 일일 카운터 리셋.
     *
     * <p>dailyReset 날짜가 오늘이 아니면 dailyEarned, dailyCapUsed를
     * 0으로 초기화하고 dailyReset을 오늘로 갱신한다. 이미 오늘이면 아무 작업도 하지 않는다.</p>
     *
     * <p>v3.3: daily_ai_used 리셋은 {@link UserAiQuota#resetDailyIfNeeded(LocalDate)}에서 처리한다.
     * 이 메서드는 포인트 일일 획득량({@code dailyEarned}, {@code dailyCapUsed})만 관리한다.</p>
     *
     * @param today 오늘 날짜
     */
    public void resetDailyIfNeeded(LocalDate today) {
        if (this.dailyReset == null || !this.dailyReset.equals(today)) {
            // v3.3: dailyAiUsed는 UserAiQuota로 분리됨 — 여기서 리셋하지 않는다
            this.dailyEarned = 0;
            this.dailyCapUsed = 0;
            this.dailyReset = today;
        }
    }

    /**
     * 등급 갱신.
     *
     * <p>사용자 등급을 새로운 {@link Grade} 엔티티로 변경한다.
     * 등급 계산 로직(누적 포인트 기반 Grade 조회)은 서비스 레이어에서 수행하며,
     * 이 메서드는 단순히 참조를 교체한다.</p>
     *
     * @param newGrade 새 등급 엔티티 (null 허용 — 서비스에서 NORMAL fallback 처리)
     */
    public void updateGrade(Grade newGrade) {
        this.grade = newGrade;
    }

    /**
     * 현재 등급 코드 문자열을 반환한다 (null-safe).
     *
     * <p>grade 참조가 null이거나 LAZY 로딩 전이면 "NORMAL"을 반환한다.
     * v3.0 기준 기본 등급은 NORMAL (가입 시 초기 등급).</p>
     *
     * @return 등급 코드 (예: "NORMAL", "BRONZE", "SILVER", "GOLD", "PLATINUM")
     */
    public String getGradeCode() {
        return this.grade != null ? this.grade.getGradeCode() : "NORMAL";
    }
}
