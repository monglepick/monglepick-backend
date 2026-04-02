package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 활동별 리워드 정책 엔티티 — reward_policy 테이블 매핑.
 *
 * <p>사용자 활동(리뷰 작성, 출석, AI 추천 등)에 대해 지급할 포인트 정책을 관리한다.
 * 관리자 페이지에서 활동별 포인트 금액, 일일 한도, 쿨다운, 최소 길이를 동적으로 조정할 수 있다.
 * 코드 변경 없이 DB 행 추가/수정만으로 새 활동 유형을 등록할 수 있다.</p>
 *
 * <h3>활동 카테고리 (action_category)</h3>
 * <ul>
 *   <li>{@code CONTENT} — 콘텐츠 생산 (리뷰, 게시글, 댓글)</li>
 *   <li>{@code ENGAGEMENT} — 참여 활동 (찜, FAQ 피드백, 상담 티켓, AI 추천)</li>
 *   <li>{@code MILESTONE} — 마일스톤/업적 (첫 리뷰, 리뷰 5회, 등급 승급 등)</li>
 *   <li>{@code ATTENDANCE} — 출석 관련</li>
 * </ul>
 *
 * <h3>point_type과 배율 적용</h3>
 * <ul>
 *   <li>{@code earn} — 반복 활동 리워드. 등급 배율(grade.reward_multiplier) 적용 대상</li>
 *   <li>{@code bonus} — 일회성/마일스톤 보너스. 등급 배율 미적용 (고정 포인트)</li>
 * </ul>
 *
 * <h3>threshold 기반 마일스톤 판정</h3>
 * <ul>
 *   <li>{@code threshold_target = NULL}: 일반 활동 — 발생 즉시 지급</li>
 *   <li>{@code threshold_target = 'TOTAL'}: 누적 마일스톤 — parent의 total_count ≥ threshold_count 시 지급</li>
 *   <li>{@code threshold_target = 'DAILY'}: 일일 달성 — parent의 daily_count == threshold_count 시 지급</li>
 *   <li>{@code threshold_target = 'STREAK'}: 연속 달성 — parent의 current_streak % threshold_count == 0 시 지급</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — activityType/activityName/pointsAmount/dailyLimit/maxCount/isActive/description</li>
 *   <li>2026-04-02: 설계서 v2.3 §4.3 기준 전면 수정 —
 *       activity_type→action_type 컬럼명 통일,
 *       9개 컬럼 추가 (action_category, point_type, cooldown_seconds, min_content_length,
 *       limit_type, threshold_count, threshold_target, parent_action_type),
 *       인덱스 추가 (action_category, is_active, parent_action_type)</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyRepository
 */
@Entity
@Table(
        name = "reward_policy",
        uniqueConstraints = {
                /* action_type은 시스템 전체에서 유일한 활동 코드여야 한다 */
                @UniqueConstraint(
                        name = "uk_reward_policy_action_type",
                        columnNames = "action_type"
                )
        },
        indexes = {
                @Index(name = "idx_policy_category", columnList = "action_category"),
                @Index(name = "idx_policy_active", columnList = "is_active"),
                @Index(name = "idx_policy_parent", columnList = "parent_action_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class RewardPolicy extends BaseAuditEntity {

    /**
     * 리워드 정책 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    /**
     * 활동 유형 코드 (VARCHAR(50), NOT NULL, UNIQUE).
     *
     * <p>영문 대문자+언더스코어 형식의 시스템 내부 식별 코드.
     * 서비스 레이어에서 포인트 지급 시 이 값으로 정책을 조회한다.</p>
     *
     * <p>예: "REVIEW_CREATE", "ATTENDANCE_BASE", "SIGNUP_BONUS", "GRADE_UP_SILVER"</p>
     *
     * <p>변경 이력: 2026-04-02 컬럼명 activity_type → action_type (설계서 v2.3 통일)</p>
     */
    @Column(name = "action_type", length = 50, nullable = false, unique = true)
    private String actionType;

    /**
     * 활동 표시명 (VARCHAR(100), NOT NULL).
     * 관리자 페이지 및 포인트 이력 화면에서 사용자에게 노출되는 한국어 이름.
     * 예: "리뷰 작성", "출석 기본", "회원가입 보너스"
     */
    @Column(name = "activity_name", length = 100, nullable = false)
    private String activityName;

    /**
     * 활동 카테고리 (VARCHAR(30), NOT NULL).
     *
     * <p>CONTENT, ENGAGEMENT, MILESTONE, ATTENDANCE 중 하나.
     * 관리자 페이지에서 카테고리별 필터링/그룹핑에 사용.</p>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "action_category", length = 30, nullable = false)
    @Builder.Default
    private String actionCategory = "CONTENT";

    /**
     * 1회 활동 시 지급 포인트 (NOT NULL).
     * 양수여야 하며, 0이면 카운팅 전용(AI_CHAT_USE 등).
     * point_type='earn'이면 등급 배율이 곱해진다.
     */
    @Column(name = "points_amount", nullable = false)
    private Integer pointsAmount;

    /**
     * 포인트 변동 유형 (VARCHAR(50), NOT NULL, DEFAULT 'earn').
     *
     * <ul>
     *   <li>{@code earn} — 반복 활동 리워드. 등급 배율 적용 대상.</li>
     *   <li>{@code bonus} — 일회성/마일스톤 보너스. 등급 배율 미적용.</li>
     * </ul>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "point_type", length = 50, nullable = false)
    @Builder.Default
    private String pointType = "earn";

    /**
     * 일일 최대 지급 횟수 (기본값: 0).
     * 0이면 일일 한도 없음(무제한).
     * 예: 리뷰 dailyLimit=3 → 하루 최대 3회만 포인트 지급.
     */
    @Column(name = "daily_limit")
    @Builder.Default
    private Integer dailyLimit = 0;

    /**
     * 평생 최대 지급 횟수 (기본값: 0).
     * 0이면 평생 횟수 제한 없음(무제한).
     * 예: SIGNUP_BONUS maxCount=1 → 평생 1회만 지급.
     */
    @Column(name = "max_count")
    @Builder.Default
    private Integer maxCount = 0;

    /**
     * 연속 실행 최소 간격 (초, 기본값: 0).
     *
     * <p>0이면 쿨다운 없음. 예: 위시리스트 추가 cooldownSeconds=10 →
     * 마지막 활동으로부터 10초 이내 재실행 시 리워드 미지급.</p>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "cooldown_seconds", nullable = false)
    @Builder.Default
    private Integer cooldownSeconds = 0;

    /**
     * 최소 콘텐츠 길이 (기본값: 0).
     *
     * <p>0이면 길이 검사 안 함. 예: 리뷰 minContentLength=10 →
     * 10자 미만 리뷰에는 리워드 미지급 (품질 기준).</p>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "min_content_length", nullable = false)
    @Builder.Default
    private Integer minContentLength = 0;

    /**
     * 보조 한도 분류 (VARCHAR(20), nullable).
     *
     * <p>ONCE, DAILY, STREAK, PER_REF 중 하나. 검색/필터 편의용 보조 분류이며,
     * 실제 한도 검사는 daily_limit/max_count/threshold 등으로 수행.</p>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "limit_type", length = 20)
    private String limitType;

    /**
     * 달성 기준 횟수 (기본값: 0).
     *
     * <p>0이면 미사용(일반 활동). 양수이면 부모 활동의 카운터와 비교하여 달성 시 지급.</p>
     * <ul>
     *   <li>REVIEW_MILESTONE_5: thresholdCount=5 → 리뷰 5회 달성 시 보너스</li>
     *   <li>DAILY_REVIEW_3: thresholdCount=3 → 오늘 리뷰 3건 달성 시 보너스</li>
     *   <li>ATTENDANCE_STREAK_7: thresholdCount=7 → 7일 연속 출석 시 보너스</li>
     * </ul>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "threshold_count", nullable = false)
    @Builder.Default
    private Integer thresholdCount = 0;

    /**
     * 비교 대상 카운터 (VARCHAR(30), nullable).
     *
     * <ul>
     *   <li>{@code NULL} — 일반 활동 (threshold 미사용)</li>
     *   <li>{@code TOTAL} — parent의 total_count ≥ threshold_count 시 지급 (누적 마일스톤)</li>
     *   <li>{@code DAILY} — parent의 daily_count == threshold_count 시 지급 (일일 달성)</li>
     *   <li>{@code STREAK} — parent의 current_streak % threshold_count == 0 시 지급 (연속 달성)</li>
     * </ul>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "threshold_target", length = 30)
    private String thresholdTarget;

    /**
     * 부모 활동 코드 (VARCHAR(50), nullable).
     *
     * <p>이 활동의 카운터 참조 대상. threshold 기반 마일스톤에서 사용.
     * 예: DAILY_REVIEW_3의 parent는 REVIEW_CREATE → REVIEW_CREATE의 progress.daily_count를 참조.</p>
     *
     * <p>일반 활동(threshold_target=NULL)이면 NULL.
     * 달성 정책의 parent_action_type도 NULL이므로 checkThresholdRewards() 재귀 시
     * findByParentActionType()이 빈 리스트 반환 → 무한 재귀 불가.</p>
     *
     * <p>설계서 v2.3 §4.3 신규 컬럼.</p>
     */
    @Column(name = "parent_action_type", length = 50)
    private String parentActionType;

    /**
     * 정책 활성화 여부 (기본값: true).
     * false이면 해당 활동이 발생해도 포인트가 지급되지 않는다.
     * 일시적 이벤트 종료나 정책 폐기 시 비활성화한다.
     * 서버 재시작 불필요한 긴급 제어 수단.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 정책 설명 (VARCHAR(500), nullable).
     * 관리자가 정책 의도나 주의사항을 메모하는 용도.
     * 예: "신규 가입 시 1회 지급. 소셜 로그인 포함."
     */
    @Column(name = "description", length = 500)
    private String description;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 정책 활성화 상태를 변경한다 (관리자 페이지 수정용).
     *
     * @param active true이면 활성화, false이면 비활성화
     */
    public void updateActiveStatus(boolean active) {
        this.isActive = active;
    }

    /**
     * 포인트 금액 및 한도를 수정한다 (관리자 페이지 수정용).
     *
     * @param pointsAmount    변경할 1회 지급 포인트
     * @param dailyLimit      변경할 일일 한도 (0 = 무제한)
     * @param maxCount        변경할 평생 한도 (0 = 무제한)
     * @param cooldownSeconds 변경할 쿨다운 (초)
     * @param minContentLength 변경할 최소 콘텐츠 길이
     * @param description     변경할 정책 설명
     */
    public void updatePolicy(Integer pointsAmount, Integer dailyLimit,
                             Integer maxCount, Integer cooldownSeconds,
                             Integer minContentLength, String description) {
        if (pointsAmount != null) this.pointsAmount = pointsAmount;
        if (dailyLimit != null) this.dailyLimit = dailyLimit;
        if (maxCount != null) this.maxCount = maxCount;
        if (cooldownSeconds != null) this.cooldownSeconds = cooldownSeconds;
        if (minContentLength != null) this.minContentLength = minContentLength;
        if (description != null) this.description = description;
    }

    /**
     * 이 정책의 일일 한도가 설정되어 있는지(0 초과) 여부를 반환한다.
     *
     * @return true이면 일일 한도 있음, false이면 무제한
     */
    public boolean hasDailyLimit() {
        return this.dailyLimit != null && this.dailyLimit > 0;
    }

    /**
     * 이 정책의 평생 횟수 한도가 설정되어 있는지(0 초과) 여부를 반환한다.
     *
     * @return true이면 평생 한도 있음, false이면 무제한
     */
    public boolean hasMaxCount() {
        return this.maxCount != null && this.maxCount > 0;
    }

    /**
     * 이 정책이 threshold 기반 마일스톤인지 여부를 반환한다.
     *
     * @return true이면 threshold 기반 (TOTAL/DAILY/STREAK), false이면 일반 활동
     */
    public boolean isThresholdBased() {
        return this.thresholdTarget != null && !this.thresholdTarget.isBlank()
                && this.thresholdCount > 0;
    }

    /**
     * 이 정책에 쿨다운이 설정되어 있는지 여부를 반환한다.
     *
     * @return true이면 쿨다운 있음 (cooldownSeconds > 0)
     */
    public boolean hasCooldown() {
        return this.cooldownSeconds != null && this.cooldownSeconds > 0;
    }

    /**
     * 이 정책에 최소 콘텐츠 길이 기준이 있는지 여부를 반환한다.
     *
     * @return true이면 길이 기준 있음 (minContentLength > 0)
     */
    public boolean hasMinContentLength() {
        return this.minContentLength != null && this.minContentLength > 0;
    }
}
