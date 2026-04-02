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
 * <p>사용자 등급(NORMAL, BRONZE, SILVER, GOLD, PLATINUM)에 대한 기준 및 쿼터 설정을
 * DB에서 관리하는 마스터 테이블이다. 관리자 페이지에서 등급 기준/쿼터를 동적으로
 * 조회할 수 있도록 {@code application.yml}의 고정 상수 방식을 보완한다.</p>
 *
 * <h3>v3.1 AI 4-단계 모델 변경</h3>
 * <ul>
 *   <li>복원: {@code monthly_ai_limit} — 월간 AI 절대 상한. 구매 이용권 포함 전 소스 합산 적용. -1이면 무제한(PLATINUM)</li>
 *   <li>제거(유지): {@code free_daily_count} — 무료 일일 횟수 개념 폐지 (daily_ai_limit 내 전부 무료). DB 컬럼은 방치</li>
 *   <li>유지: {@code daily_ai_limit} — 등급별 일일 AI 무료 사용 한도 (소진 후 구독 보너스/구매 토큰 사용)</li>
 * </ul>
 * <p>DB 컬럼(free_daily_count)은 ddl-auto=update로 자동 DROP되지 않지만,
 * Java 엔티티에서 제거하여 코드 레벨에서 사용을 완전히 차단한다.</p>
 *
 * <h3>v3.1 시드 데이터 (GradeInitializer — §4.5 기준)</h3>
 * <ul>
 *   <li>NORMAL  : minPoints=0,      dailyAiLimit=3,  monthlyAiLimit=200,  maxInputLength=200,  multiplier=1.00, dailyCap=500</li>
 *   <li>BRONZE  : minPoints=2000,   dailyAiLimit=7,  monthlyAiLimit=500,  maxInputLength=300,  multiplier=1.10, dailyCap=900</li>
 *   <li>SILVER  : minPoints=8000,   dailyAiLimit=15, monthlyAiLimit=1000, maxInputLength=500,  multiplier=1.30, dailyCap=1500</li>
 *   <li>GOLD    : minPoints=20000,  dailyAiLimit=30, monthlyAiLimit=2000, maxInputLength=1000, multiplier=1.50, dailyCap=2500</li>
 *   <li>PLATINUM: minPoints=50000,  dailyAiLimit=-1, monthlyAiLimit=-1,   maxInputLength=2000, multiplier=2.00, dailyCap=5000</li>
 * </ul>
 *
 * <h3>등급 진입 속도 (일반 사용자 월 600P 기준, SIGNUP_BONUS 200P 포함)</h3>
 * <ul>
 *   <li>BRONZE(2,000P): (2,000-200)/600 = 3개월</li>
 *   <li>SILVER(8,000P): (8,000-200)/600 = 13개월</li>
 *   <li>GOLD(20,000P) : (20,000-200)/600 = 33개월 (2.7년)</li>
 *   <li>PLATINUM(50,000P): (50,000-200)/600 = 83개월 (6.9년, 슈퍼 충성 고객)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — gradeCode(String) 기반 관리자 연동, DB 등급 마스터 테이블화</li>
 *   <li>2026-04-02: rewardMultiplier(등급별 리워드 배율), dailyEarnCap(일일 활동 리워드 총 획득 상한) 추가.
 *       NORMAL 등급 추가(5등급 체계). 기준점 전면 조정.</li>
 *   <li>2026-04-02 v3.0: free_daily_count 필드 제거. 3-소스 AI 모델로 전환.
 *       min_points 기준값 전면 상향 (BRONZE 500→2000, SILVER 2000→8000, GOLD 5000→20000, PLATINUM 15000→50000).
 *       daily_ai_limit 상향 (BRONZE 5→7, SILVER 10→15). daily_earn_cap 상향.</li>
 *   <li>2026-04-02 v3.1: monthly_ai_limit 필드 복원. 구매 이용권이 일일 한도를 우회하므로 월간 절대 상한 필요.
 *       4-단계 모델: 월간 상한 체크 → GRADE_FREE → SUB_BONUS → PURCHASED → BLOCKED.</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository
 * @see com.monglepick.monglepickbackend.domain.reward.config.GradeInitializer
 * @see com.monglepick.monglepickbackend.domain.reward.entity.UserPoint
 */
@Entity
@Table(name = "grades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자 — 외부에서 직접 생성 금지
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
     * <p>'NORMAL', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM' 중 하나이며 대문자로 관리한다.
     * QuotaService 및 PointService에서 문자열 비교로 등급을 식별한다.</p>
     */
    @Column(name = "grade_code", nullable = false, unique = true, length = 20)
    private String gradeCode;

    /**
     * 등급 한글 표시명 (VARCHAR(50)).
     *
     * <p>관리자 페이지 및 클라이언트 UI 표시용 한글 명칭.</p>
     */
    @Column(name = "grade_name", length = 50)
    private String gradeName;

    /**
     * 최소 누적 활동 포인트 (INT, NOT NULL).
     *
     * <p>이 등급에 도달하기 위한 최소 누적 활동 포인트 ({@code user_points.earned_by_activity}).
     * 결제 충전 포인트는 제외된다 — 구매→환불 반복으로 등급 악용 방지.</p>
     */
    @Column(name = "min_points", nullable = false)
    private Integer minPoints;

    /**
     * 일일 AI 추천 무료 한도 (INT).
     *
     * <p>하루에 AI 추천(채팅)을 무료로 사용할 수 있는 최대 횟수.
     * -1이면 무제한 (PLATINUM 등급).</p>
     *
     * <p>v3.1 변경: 한도 내 사용은 전부 무료 (포인트 차감 없음). 한도 초과 시
     * 구독 보너스 풀 → 구매 토큰 → 차단 순서로 처리된다.
     * 구매 이용권(PURCHASED 소스)은 이 일일 한도를 우회하지만 monthly_ai_limit에는 합산된다.</p>
     */
    @Column(name = "daily_ai_limit")
    private Integer dailyAiLimit;

    /**
     * 월간 AI 절대 상한 (INT) — v3.1 복원.
     *
     * <p>한 달에 AI 추천(채팅)을 사용할 수 있는 최대 횟수. -1이면 무제한 (PLATINUM 등급).
     * GRADE_FREE·SUB_BONUS·PURCHASED 전 소스의 사용량을 합산하여 비교한다
     * ({@code user_points.monthly_ai_used}).</p>
     *
     * <p>구매 이용권은 일일 한도를 우회할 수 있지만, 이 월간 상한은 반드시 준수해야 한다.
     * QuotaService.checkQuota()에서 가장 먼저 확인한다 (단계 0).</p>
     */
    @Column(name = "monthly_ai_limit")
    private Integer monthlyAiLimit;

    /**
     * 최대 입력 글자 수 (INT).
     *
     * <p>이 등급 사용자가 AI 채팅 메시지로 입력할 수 있는 최대 글자 수.
     * 등급이 높을수록 더 긴 메시지를 입력할 수 있다.</p>
     */
    @Column(name = "max_input_length")
    private Integer maxInputLength;

    /**
     * 등급별 리워드 배율 (DECIMAL(3,2), NOT NULL, DEFAULT 1.00).
     *
     * <p>활동 리워드(point_type='earn') 지급 시 기본 포인트에 이 배율을 곱한다.
     * 예: GOLD(1.50) 등급이 리뷰 작성(20P) 시 → floor(20 × 1.50) = 30P 지급.
     * bonus/milestone(point_type='bonus')에는 배율을 적용하지 않는다.</p>
     *
     * <p>v3.0 시드 데이터: NORMAL=1.00, BRONZE=1.10, SILVER=1.30, GOLD=1.50, PLATINUM=2.00</p>
     */
    @Column(name = "reward_multiplier", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rewardMultiplier = BigDecimal.ONE;

    /**
     * 일일 활동 리워드 총 획득 상한 (INT, NOT NULL, DEFAULT 0).
     *
     * <p>하루에 활동 리워드로 획득할 수 있는 최대 포인트.
     * 0이면 무제한. 구독 포인트/관리자 수동 지급은 상한에 미포함.
     * PLATINUM도 5,000P 상한으로 극단적 어뷰징을 방지한다.</p>
     *
     * <p>{@code user_points.daily_cap_used}와 함께 사용:
     * {@code daily_cap_used + amount > daily_earn_cap} 이면 해당 일 추가 리워드 차단.</p>
     *
     * <p>v3.0 시드 데이터: NORMAL=500, BRONZE=900, SILVER=1500, GOLD=2500, PLATINUM=5000</p>
     */
    @Column(name = "daily_earn_cap", nullable = false)
    @Builder.Default
    private Integer dailyEarnCap = 0;

    /**
     * 정렬 순서 (INT).
     *
     * <p>관리자 페이지나 등급 목록 조회 시 표시 순서. 낮은 숫자가 먼저 표시된다.
     * NORMAL=0, BRONZE=1, SILVER=2, GOLD=3, PLATINUM=4 순서로 설정한다.</p>
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 활성 여부 (TINYINT(1), 기본값 true).
     *
     * <p>비활성화된 등급은 등급 조회 시 제외된다.
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
     * <p>v3.1: monthlyAiLimit 파라미터 복원. 구매 이용권의 월간 상한 관리에 사용.</p>
     *
     * @param dailyAiLimit     새 일일 무료 한도 (null이면 변경 안 함, -1이면 무제한)
     * @param monthlyAiLimit   새 월간 절대 상한 (null이면 변경 안 함, -1이면 무제한)
     * @param maxInputLength   새 최대 입력 글자 수 (null이면 변경 안 함)
     * @param rewardMultiplier 새 리워드 배율 (null이면 변경 안 함)
     * @param dailyEarnCap     새 일일 활동 리워드 상한 (null이면 변경 안 함)
     */
    public void updateQuota(Integer dailyAiLimit, Integer monthlyAiLimit, Integer maxInputLength,
                            BigDecimal rewardMultiplier, Integer dailyEarnCap) {
        if (dailyAiLimit != null) this.dailyAiLimit = dailyAiLimit;
        if (monthlyAiLimit != null) this.monthlyAiLimit = monthlyAiLimit;
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
}
