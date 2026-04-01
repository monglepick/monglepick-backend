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
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 *   <li>2026-03-24: PK 필드명 pointId → userPointId 로 변경, @Column(name = "user_point_id") 추가</li>
 *   <li>2026-03-31: userGrade 필드를 등급 문자열 컬럼에서 {@code Grade} 엔티티 FK로 전환.
 *       컬럼명 {@code user_grade}(VARCHAR) → {@code grade_id}(BIGINT FK).</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID (FK → users.user_id, UNIQUE)</li>
 *   <li>{@code balance} — 현재 보유 포인트</li>
 *   <li>{@code totalEarned} — 누적 획득 포인트</li>
 *   <li>{@code dailyEarned} — 오늘 획득 포인트 (일일 한도 관리용)</li>
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
     * 두 가지 방식을 함께 선언하여 JPA 레이어와 DB 레이어 모두에서 중복을 방지한다.
     * 덕분에 동시 삽입 시 DB가 {@code DataIntegrityViolationException}을 발생시키며,
     * {@link com.monglepick.monglepickbackend.domain.reward.service.PointService#initializePoint}
     * 에서 이를 포착하여 멱등 동작을 보장한다.</p>
     */
    @Column(name = "user_id", length = 50, nullable = false, unique = true)
    private String userId;

    /**
     * 현재 보유 포인트.
     * 기본값: 0.
     * 필드명 변경 이력: pointHave → balance (2026-03-31)
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
     * dailyEarned가 마지막으로 0으로 초기화된 날짜.
     * 날짜가 바뀌면 dailyEarned를 0으로 리셋한다.
     */
    @Column(name = "daily_reset")
    private LocalDate dailyReset;

    /**
     * 사용자 등급 (FK → grades.grade_id, LAZY).
     *
     * <p>기존 Enum 문자열 컬럼(user_grade VARCHAR)에서 {@code Grade} 엔티티 FK(grade_id BIGINT)로
     * 전환하였다. 이를 통해 관리자 페이지에서 등급 기준/쿼터를 동적으로 관리할 수 있다.</p>
     *
     * <p>LAZY 로딩을 사용하므로 등급 정보가 필요한 시점(트랜잭션 내)에만 조인 쿼리가 발생한다.
     * null이 허용되며, null인 경우 서비스 레이어에서 BRONZE 등급으로 fallback 처리한다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    /**
     * 일일 AI 질문 사용 횟수.
     * 기본값: 0.
     * 매일 자정에 스케줄러가 0으로 리셋한다.
     * 등급별 일일 쿼터(BRONZE 3회, SILVER 10회, GOLD 30회, PLATINUM 무제한) 초과 여부 판단에 사용.
     */
    @Column(name = "daily_ai_used")
    @Builder.Default
    private Integer dailyAiUsed = 0;

    /**
     * 월간 AI 질문 사용 횟수.
     * 기본값: 0.
     * 매월 1일 자정에 스케줄러가 0으로 리셋한다.
     * 등급별 월간 쿼터(BRONZE 30회, SILVER 200회, GOLD 600회, PLATINUM 무제한) 초과 여부 판단에 사용.
     */
    @Column(name = "monthly_ai_used")
    @Builder.Default
    private Integer monthlyAiUsed = 0;

    /**
     * 총 사용 포인트 (누적).
     * 기본값: 0.
     * 포인트 차감 시마다 증가하며, 관리자 통계 및 사용자 활동 지표로 활용된다.
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
     * @param amount 획득 포인트 (양수)
     * @param today  오늘 날짜 (일일 리셋 판단용)
     */
    public void addPoints(int amount, LocalDate today) {
        resetDailyIfNeeded(today);
        this.balance += amount;
        this.totalEarned += amount;
        this.dailyEarned += amount;
    }

    /**
     * 일일 획득량 리셋.
     *
     * <p>dailyReset 날짜가 오늘이 아니면 dailyEarned를 0으로 초기화하고
     * dailyReset을 오늘로 갱신한다. 이미 오늘이면 아무 작업도 하지 않는다.</p>
     *
     * @param today 오늘 날짜
     */
    public void resetDailyIfNeeded(LocalDate today) {
        if (this.dailyReset == null || !this.dailyReset.equals(today)) {
            this.dailyEarned = 0;
            this.dailyReset = today;
        }
    }

    /**
     * AI 질문 사용 횟수 증가.
     *
     * <p>AI 채팅 요청 시 호출한다. 일일/월간 사용 횟수를 각각 1씩 증가시킨다.
     * 쿼터 초과 여부 사전 검증은 서비스 레이어(QuotaService)에서 수행한다.</p>
     */
    public void incrementAiUsage() {
        this.dailyAiUsed += 1;
        this.monthlyAiUsed += 1;
    }

    /**
     * 일일 AI 사용 횟수 리셋 (스케줄러 — 매일 자정 호출).
     */
    public void resetDailyAiUsed() {
        this.dailyAiUsed = 0;
    }

    /**
     * 월간 AI 사용 횟수 리셋 (스케줄러 — 매월 1일 자정 호출).
     */
    public void resetMonthlyAiUsed() {
        this.monthlyAiUsed = 0;
    }

    /**
     * 등급 갱신.
     *
     * <p>사용자 등급을 새로운 {@link Grade} 엔티티로 변경한다.
     * 등급 계산 로직(누적 포인트 기반 Grade 조회)은 서비스 레이어에서 수행하며,
     * 이 메서드는 단순히 참조를 교체한다.</p>
     *
     * <h4>변경 이력</h4>
     * <ul>
     *   <li>2026-03-31: {@code updateGrade(Grade)} 방식으로 변경 —
     *       외부에서 {@link Grade} 엔티티 객체를 주입받아 FK를 교체하는 방식으로 전환.</li>
     * </ul>
     *
     * @param newGrade 새 등급 엔티티 (null 허용 — 서비스에서 BRONZE fallback 처리)
     */
    public void updateGrade(Grade newGrade) {
        this.grade = newGrade;
    }

    /**
     * 현재 등급 코드 문자열을 반환한다 (null-safe).
     *
     * <p>grade 참조가 null이거나 LAZY 로딩 전이면 "BRONZE"를 반환한다.
     * 서비스 레이어에서 등급 코드 문자열이 필요할 때 사용한다.</p>
     *
     * @return 등급 코드 (예: "BRONZE", "SILVER", "GOLD", "PLATINUM")
     */
    public String getGradeCode() {
        return this.grade != null ? this.grade.getGradeCode() : "BRONZE";
    }
}
