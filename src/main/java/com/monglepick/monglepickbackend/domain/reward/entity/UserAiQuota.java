package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 AI 쿼터 엔티티 — user_ai_quota 테이블 매핑.
 *
 * <p><b>v3.3 변경</b>: 기존 {@code user_points} 테이블에 혼재하던 AI 쿼터 4개 컬럼
 * ({@code daily_ai_used}, {@code monthly_coupon_used}, {@code monthly_reset},
 * {@code purchased_ai_tokens})을 별도 테이블로 분리하여 단일 책임 원칙(SRP)을 준수한다.</p>
 *
 * <h3>분리 이유</h3>
 * <ul>
 *   <li>포인트 잔액(user_points)과 AI 쿼터 카운터는 갱신 주기와 락 범위가 다르다.</li>
 *   <li>AI 쿼터 관련 로직을 {@link QuotaService}에 집중시켜 유지보수를 단순화한다.</li>
 *   <li>포인트 차감과 AI 쿼터 소비의 트랜잭션을 분리하여 데드락 위험을 줄인다.</li>
 * </ul>
 *
 * <h3>v3.2 AI 3-소스 모델과 연계되는 필드</h3>
 * <ol>
 *   <li><b>소스 1 — GRADE_FREE</b>: {@code daily_ai_used} vs {@code Grade.daily_ai_limit}</li>
 *   <li><b>소스 2 — SUB_BONUS</b>: {@code UserSubscription.remaining_ai_bonus} (이 엔티티와 무관)</li>
 *   <li><b>소스 3 — PURCHASED</b>: {@code purchased_ai_tokens} 소비, {@code monthly_coupon_used} 증가</li>
 * </ol>
 *
 * <h3>lazy reset 전략</h3>
 * <p>별도 스케줄러 없이 서비스 레이어에서 요청 시점에 날짜를 확인하여 리셋한다.
 * {@code daily_ai_reset} 날짜가 오늘과 다르면 {@link #resetDailyIfNeeded(LocalDate)}에서 리셋.
 * {@code monthly_reset} 날짜의 월/년이 오늘과 다르면 {@link #resetMonthlyIfNeeded(LocalDate)}에서 리셋.</p>
 *
 * <h3>free_daily_granted 필드 (v3.3 신규)</h3>
 * <p>등급별 매일 자동 지급 이용권({@code Grade.free_daily_count}) 지급 여부 추적.
 * {@code daily_ai_reset} 기준으로 날짜가 바뀌면 0으로 리셋된다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07 v3.3: UserPoint에서 AI 쿼터 4컬럼 분리 신규 생성.
 *       free_daily_granted(매일 이용권 자동지급 추적), last_granted_date(마지막 지급일) 추가.</li>
 * </ul>
 *
 * @see QuotaService
 * @see UserPoint
 */
@Entity
@Table(
        name = "user_ai_quota",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_ai_quota_user_id",
                columnNames = "user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class UserAiQuota extends BaseAuditEntity {

    /**
     * AI 쿼터 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 각 사용자당 하나의 쿼터 레코드만 존재한다 (user_id UNIQUE).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_ai_quota_id")
    private Long userAiQuotaId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL, UNIQUE).
     *
     * <p>users.user_id를 논리적으로 참조한다 (FK 미선언 — 도메인 간 의존 최소화).
     * 사용자 1명당 AI 쿼터 레코드는 반드시 1개만 존재해야 한다.</p>
     */
    @Column(name = "user_id", length = 50, nullable = false, unique = true)
    private String userId;

    /**
     * 오늘 AI 사용 횟수 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>소스 1(GRADE_FREE): 이 값이 {@code Grade.daily_ai_limit} 미만이면 무료 사용 가능.
     * 무료 허용 시 {@link #incrementDailyAiUsed()} 로 1 증가.</p>
     *
     * <p>{@code daily_ai_reset} 날짜가 오늘이 아니면 {@link #resetDailyIfNeeded(LocalDate)} 에서
     * 0으로 초기화된다 (lazy reset).</p>
     */
    @Column(name = "daily_ai_used", nullable = false)
    @Builder.Default
    private Integer dailyAiUsed = 0;

    /**
     * 일일 AI 사용량 lazy reset 기준일 (DATE).
     *
     * <p>이 날짜가 오늘과 다를 때 {@code daily_ai_used}와 {@code free_daily_granted}를
     * 0으로 초기화한다. 초기화 후 이 필드를 오늘로 갱신한다.</p>
     */
    @Column(name = "daily_ai_reset")
    private LocalDate dailyAiReset;

    /**
     * 이번 달 AI 이용권(쿠폰) 사용 횟수 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>소스 3(PURCHASED): 이용권 소비 시에만 카운트한다.
     * GRADE_FREE와 SUB_BONUS는 이 카운터에 포함되지 않는다.
     * {@code Grade.monthly_ai_limit} 와 비교하여 이용권 월간 상한을 적용한다.
     * -1(DIAMOND)이면 무제한.</p>
     *
     * <p>{@code monthly_reset} 월/년이 오늘과 다르면 {@link #resetMonthlyIfNeeded(LocalDate)} 에서
     * 0으로 초기화된다 (lazy reset).</p>
     */
    @Column(name = "monthly_coupon_used", nullable = false)
    @Builder.Default
    private Integer monthlyCouponUsed = 0;

    /**
     * 월간 쿠폰 카운터 lazy reset 기준일 (DATE).
     *
     * <p>이 날짜의 월/년이 오늘과 다를 때 {@code monthly_coupon_used}를 0으로 초기화한다.
     * 초기화 후 이 필드를 오늘로 갱신한다.</p>
     */
    @Column(name = "monthly_reset")
    private LocalDate monthlyReset;

    /**
     * 포인트 상점에서 구매한 AI 이용권 잔여 횟수 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>소스 3(PURCHASED): 소스 1/2가 소진된 경우에 사용된다.
     * 구매 시: {@link #addPurchasedTokens(int)} 호출.
     * 소비 시: {@link #consumePurchasedToken()} 호출.</p>
     */
    @Column(name = "purchased_ai_tokens", nullable = false)
    @Builder.Default
    private Integer purchasedAiTokens = 0;

    /**
     * 오늘 등급별 자동 지급 이용권 지급 횟수 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>{@code Grade.free_daily_count} 에 따라 매일 자동으로 이용권을 지급한다.
     * 이미 오늘 지급이 완료되었으면 0보다 크므로 중복 지급을 방지한다.
     * {@code daily_ai_reset} 기준으로 날짜가 바뀌면 0으로 리셋된다.</p>
     */
    @Column(name = "free_daily_granted", nullable = false)
    @Builder.Default
    private Integer freeDailyGranted = 0;

    /**
     * 마지막 자동 지급 이용권 지급 날짜 (DATE, nullable).
     *
     * <p>스케줄러나 관리자 페이지에서 이중 지급 여부 확인용 보조 필드.
     * {@code daily_ai_reset}과 동시에 갱신된다.</p>
     */
    @Column(name = "last_granted_date")
    private LocalDate lastGrantedDate;

    // ──────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 사용, 불변성 보장)
    // ──────────────────────────────────────────────

    /**
     * 일일 AI 사용 카운터 lazy reset.
     *
     * <p>{@code daily_ai_reset}이 오늘이 아니면:
     * <ul>
     *   <li>{@code daily_ai_used} → 0</li>
     *   <li>{@code free_daily_granted} → 0 (오늘 자동 지급 이용권 초기화)</li>
     *   <li>{@code daily_ai_reset} → today</li>
     * </ul>
     * 이미 오늘이면 아무 작업도 하지 않는다.</p>
     *
     * @param today 오늘 날짜 (서비스 레이어에서 {@code LocalDate.now()} 전달)
     */
    public void resetDailyIfNeeded(LocalDate today) {
        if (this.dailyAiReset == null || !this.dailyAiReset.equals(today)) {
            // 날짜가 바뀌었으므로 일일 AI 사용량과 자동 지급 이용권 지급 횟수를 초기화
            this.dailyAiUsed = 0;
            this.freeDailyGranted = 0;
            this.dailyAiReset = today;
        }
    }

    /**
     * 월간 쿠폰 사용 카운터 lazy reset.
     *
     * <p>{@code monthly_reset}의 월/년이 오늘과 다르면:
     * <ul>
     *   <li>{@code monthly_coupon_used} → 0</li>
     *   <li>{@code monthly_reset} → today</li>
     * </ul>
     * 이미 이번 달이면 아무 작업도 하지 않는다.</p>
     *
     * @param today 오늘 날짜 (서비스 레이어에서 {@code LocalDate.now()} 전달)
     */
    public void resetMonthlyIfNeeded(LocalDate today) {
        if (this.monthlyReset == null
                || this.monthlyReset.getYear() != today.getYear()
                || this.monthlyReset.getMonthValue() != today.getMonthValue()) {
            // 달이 바뀌었으므로 쿠폰 월한도 카운터 초기화
            this.monthlyCouponUsed = 0;
            this.monthlyReset = today;
        }
    }

    /**
     * GRADE_FREE 소스 — 일일 AI 사용 횟수 1 증가.
     *
     * <p>QuotaService에서 소스 1(GRADE_FREE) 허용 시 호출한다.
     * 호출 전에 반드시 {@link #resetDailyIfNeeded(LocalDate)}를 먼저 수행해야 한다.
     * 쿼터 초과 여부 사전 검증은 {@link QuotaService}에서 수행한다.</p>
     *
     * <p>v3.2: GRADE_FREE 소스는 monthly_coupon_used에 포함되지 않는다.</p>
     */
    public void incrementDailyAiUsed() {
        this.dailyAiUsed += 1;
    }

    /**
     * PURCHASED 소스 — 구매 이용권 1회 소비 + 쿠폰 월한도 카운터 1 증가.
     *
     * <p>QuotaService에서 소스 3(PURCHASED) 사용 시 호출한다.
     * {@code purchased_ai_tokens}가 0 이하이면 소비하지 않고 {@code false}를 반환한다 (음수 방지).</p>
     *
     * <p>v3.2: 이용권 소비와 동시에 {@code monthly_coupon_used}를 1 증가시킨다.
     * 쿠폰 월한도({@code Grade.monthly_ai_limit}) 초과 여부 사전 검증은 QuotaService에서 수행한다.</p>
     *
     * @return {@code true}이면 소비 성공, {@code false}이면 잔여 이용권 없음 (차단 필요)
     */
    public boolean consumePurchasedToken() {
        if (this.purchasedAiTokens <= 0) {
            // 구매 이용권 없음 — QuotaService에서 BLOCKED 처리 필요
            return false;
        }
        this.purchasedAiTokens -= 1;
        // PURCHASED 소스 사용 시 쿠폰 월한도 카운터 자동 증가
        this.monthlyCouponUsed += 1;
        return true;
    }

    /**
     * 포인트 상점 구매 — AI 이용권 횟수 추가.
     *
     * <p>PointShopService에서 사용자가 포인트 상점에서 이용권을 구매할 때 호출한다.
     * count가 0 이하이면 잘못된 요청으로 예외를 발생시킨다.</p>
     *
     * @param count 추가할 이용권 횟수 (양수여야 함)
     * @throws IllegalArgumentException count가 0 이하인 경우
     */
    public void addPurchasedTokens(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "추가할 이용권 횟수는 1 이상이어야 합니다: count=" + count);
        }
        this.purchasedAiTokens += count;
    }

    /**
     * 자동 지급 이용권 횟수를 기록한다 (매일 등급별 무료 지급 추적).
     *
     * <p>스케줄러 또는 QuotaService에서 매일 자동 지급 이용권을 부여한 뒤 이 메서드를 호출하여
     * 오늘 이미 지급되었음을 표시한다. {@code daily_ai_reset} 기준으로 다음 날 0으로 리셋된다.</p>
     *
     * @param grantedCount 오늘 지급한 자동 이용권 횟수
     * @param today        오늘 날짜 (last_granted_date 갱신용)
     */
    public void markFreeDailyGranted(int grantedCount, LocalDate today) {
        this.freeDailyGranted = grantedCount;
        this.lastGrantedDate = today;
    }
}
