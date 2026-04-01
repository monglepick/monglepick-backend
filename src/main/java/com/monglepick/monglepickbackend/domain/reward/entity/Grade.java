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

/**
 * 등급 마스터 엔티티 — grades 테이블 매핑.
 *
 * <p>사용자 등급(BRONZE, SILVER, GOLD, PLATINUM)에 대한 기준 및 쿼터 설정을
 * DB에서 관리하는 마스터 테이블이다. 관리자 페이지에서 등급 기준/쿼터를 동적으로
 * 조회할 수 있도록 {@code application.yml}의 고정 상수 방식을 보완한다.</p>
 *
 * <h3>기존 방식과의 차이</h3>
 * <ul>
 *   <li>기존: 등급 enum + {@code QuotaProperties} yml 고정값</li>
 *   <li>변경: {@code Grade} 엔티티 — DB에 등급 기준/쿼터 메타데이터 저장 (관리자 페이지에서 동적 관리)</li>
 * </ul>
 *
 * <h3>초기 데이터 (GradeInitializer에서 앱 시작 시 INSERT)</h3>
 * <ul>
 *   <li>BRONZE : minPoints=0,      dailyAiLimit=3,  monthlyAiLimit=30,  freeDailyCount=0,  maxInputLength=200</li>
 *   <li>SILVER : minPoints=1000,   dailyAiLimit=10, monthlyAiLimit=200, freeDailyCount=2,  maxInputLength=500</li>
 *   <li>GOLD   : minPoints=5000,   dailyAiLimit=30, monthlyAiLimit=600, freeDailyCount=5,  maxInputLength=1000</li>
 *   <li>PLATINUM: minPoints=20000, dailyAiLimit=-1, monthlyAiLimit=-1,  freeDailyCount=10, maxInputLength=2000</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — gradeCode(String) 기반 관리자 연동, DB 등급 마스터 테이블화</li>
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
     * <p>'BRONZE', 'SILVER', 'GOLD', 'PLATINUM' 중 하나이며 대문자로 관리한다.
     * QuotaService 및 PointService에서 문자열 비교로 등급을 식별한다.</p>
     */
    @Column(name = "grade_code", nullable = false, unique = true, length = 20)
    private String gradeCode;

    /**
     * 등급 한글 표시명 (VARCHAR(50)).
     *
     * <p>관리자 페이지 및 클라이언트 UI 표시용 한글 명칭. 예: '브론즈', '실버', '골드', '플래티넘'</p>
     */
    @Column(name = "grade_name", length = 50)
    private String gradeName;

    /**
     * 최소 누적 포인트 (INT, NOT NULL).
     *
     * <p>이 등급에 도달하기 위한 최소 누적 획득 포인트.
     * 누적 포인트가 이 값 이상이면 해당 등급이 부여된다.
     * 등급은 포인트를 소비해도 하락하지 않으며 누적 포인트 기준으로만 결정된다.</p>
     */
    @Column(name = "min_points", nullable = false)
    private Integer minPoints;

    /**
     * 일일 AI 추천 한도 (INT).
     *
     * <p>하루에 AI 추천(채팅)을 사용할 수 있는 최대 횟수.
     * -1이면 무제한 (PLATINUM 등급).</p>
     */
    @Column(name = "daily_ai_limit")
    private Integer dailyAiLimit;

    /**
     * 월간 AI 추천 한도 (INT).
     *
     * <p>한 달(달력 기준)에 AI 추천을 사용할 수 있는 최대 횟수.
     * -1이면 무제한 (PLATINUM 등급).</p>
     */
    @Column(name = "monthly_ai_limit")
    private Integer monthlyAiLimit;

    /**
     * 무료 일일 AI 추천 횟수 (INT).
     *
     * <p>포인트를 차감하지 않고 무료로 사용할 수 있는 하루 AI 추천 횟수.
     * 이 횟수를 초과하면 이후 사용부터 포인트가 차감된다.</p>
     */
    @Column(name = "free_daily_count")
    private Integer freeDailyCount;

    /**
     * 최대 입력 글자 수 (INT).
     *
     * <p>이 등급 사용자가 AI 채팅 메시지로 입력할 수 있는 최대 글자 수.
     * 등급이 높을수록 더 긴 메시지를 입력할 수 있다.</p>
     */
    @Column(name = "max_input_length")
    private Integer maxInputLength;

    /**
     * 정렬 순서 (INT).
     *
     * <p>관리자 페이지나 등급 목록 조회 시 표시 순서. 낮은 숫자가 먼저 표시된다.
     * BRONZE=1, SILVER=2, GOLD=3, PLATINUM=4 순서로 설정한다.</p>
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
     * @param dailyAiLimit   새 일일 한도 (null이면 변경 안 함)
     * @param monthlyAiLimit 새 월간 한도 (null이면 변경 안 함)
     * @param freeDailyCount 새 무료 일일 횟수 (null이면 변경 안 함)
     * @param maxInputLength 새 최대 입력 글자 수 (null이면 변경 안 함)
     */
    public void updateQuota(Integer dailyAiLimit, Integer monthlyAiLimit,
                            Integer freeDailyCount, Integer maxInputLength) {
        if (dailyAiLimit != null) this.dailyAiLimit = dailyAiLimit;
        if (monthlyAiLimit != null) this.monthlyAiLimit = monthlyAiLimit;
        if (freeDailyCount != null) this.freeDailyCount = freeDailyCount;
        if (maxInputLength != null) this.maxInputLength = maxInputLength;
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
