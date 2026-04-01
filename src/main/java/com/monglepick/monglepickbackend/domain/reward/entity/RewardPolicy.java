package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
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

/**
 * 활동별 리워드 정책 엔티티 — reward_policy 테이블 매핑.
 *
 * <p>사용자 활동(채팅 질문, 리뷰 작성, 출석 체크 등)에 대해 지급할 포인트 정책을 관리한다.
 * 관리자 페이지에서 활동별 포인트 금액, 일일 한도, 최대 누적 횟수를 동적으로 조정할 수 있다.</p>
 *
 * <h3>지원 활동 유형 (activityType 예시)</h3>
 * <ul>
 *   <li>{@code CHAT_QUESTION}      — AI 채팅 질문 (1회당 포인트 지급)</li>
 *   <li>{@code REVIEW_WRITE}       — 리뷰 작성</li>
 *   <li>{@code ATTENDANCE_CHECK}   — 출석 체크</li>
 *   <li>{@code ATTENDANCE_STREAK}  — 연속 출석 보너스</li>
 *   <li>{@code QUIZ_CORRECT}       — 퀴즈 정답</li>
 *   <li>{@code COURSE_COMPLETE}    — 도장깨기 코스 완주</li>
 *   <li>{@code POST_WRITE}         — 게시글 작성</li>
 *   <li>{@code COMMENT_WRITE}      — 댓글 작성</li>
 *   <li>{@code MOVIE_LIKE}         — 영화 좋아요</li>
 *   <li>{@code WISHLIST_ADD}       — 위시리스트 추가</li>
 *   <li>{@code SIGNUP_BONUS}       — 신규 가입 보너스 (1회 한정)</li>
 *   <li>{@code POINT_PURCHASE}     — 포인트 충전 (결제 연동)</li>
 *   <li>{@code REFERRAL}           — 친구 초대</li>
 *   <li>{@code EVENT_PARTICIPATION}— 이벤트 참여</li>
 *   <li>{@code ADMIN_MANUAL}       — 관리자 수동 지급</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code activityType}  — 활동 식별 코드 (UNIQUE, 시스템 내부 키)</li>
 *   <li>{@code activityName}  — 화면 표시용 한국어 이름</li>
 *   <li>{@code pointsAmount}  — 1회 활동 시 지급 포인트</li>
 *   <li>{@code dailyLimit}    — 일일 최대 지급 횟수 (0 = 무제한)</li>
 *   <li>{@code maxCount}      — 평생 최대 지급 횟수 (0 = 무제한, 예: 가입 보너스 1회)</li>
 *   <li>{@code isActive}      — 정책 활성화 여부 (false이면 해당 활동 포인트 미지급)</li>
 *   <li>{@code description}   — 정책 설명 (관리자 메모용)</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>{@code activityType}은 영문 대문자+언더스코어 고정 코드값이며 변경 금지.
 *       변경이 필요하면 비활성화(isActive=false) 후 신규 정책을 생성한다.</li>
 *   <li>서비스 레이어({@code PointService})는 포인트 지급 전 이 테이블을 조회하여
 *       isActive, dailyLimit, maxCount를 검증한다.</li>
 *   <li>어뷰징 방지를 위한 중복 지급 검증은 {@code PointsHistory}를 통해 수행한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "reward_policy",
        uniqueConstraints = {
                /* activityType은 시스템 전체에서 유일한 활동 코드여야 한다 */
                @UniqueConstraint(
                        name = "uk_reward_policy_activity_type",
                        columnNames = "activity_type"
                )
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
     * <p>예: "CHAT_QUESTION", "REVIEW_WRITE", "ATTENDANCE_CHECK"</p>
     */
    @Column(name = "activity_type", length = 50, nullable = false, unique = true)
    private String activityType;

    /**
     * 활동 표시명 (VARCHAR(100), NOT NULL).
     * 관리자 페이지 및 포인트 이력 화면에서 사용자에게 노출되는 한국어 이름.
     * 예: "AI 채팅 질문", "리뷰 작성", "출석 체크"
     */
    @Column(name = "activity_name", length = 100, nullable = false)
    private String activityName;

    /**
     * 1회 활동 시 지급 포인트 (NOT NULL).
     * 양수여야 하며, 음수 지급(차감 정책)은 별도 로직으로 처리한다.
     */
    @Column(name = "points_amount", nullable = false)
    private Integer pointsAmount;

    /**
     * 일일 최대 지급 횟수 (기본값: 0).
     * 0이면 일일 한도 없음(무제한).
     * 예: 채팅 질문 dailyLimit=5 → 하루에 최대 5회까지만 포인트 지급.
     */
    @Column(name = "daily_limit")
    @Builder.Default
    private Integer dailyLimit = 0;

    /**
     * 평생 최대 지급 횟수 (기본값: 0).
     * 0이면 평생 횟수 제한 없음(무제한).
     * 예: 가입 보너스 maxCount=1 → 평생 1회만 지급.
     */
    @Column(name = "max_count")
    @Builder.Default
    private Integer maxCount = 0;

    /**
     * 정책 활성화 여부 (기본값: true).
     * false이면 해당 활동이 발생해도 포인트가 지급되지 않는다.
     * 일시적 이벤트 종료나 정책 폐기 시 비활성화한다.
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
     * @param pointsAmount 변경할 1회 지급 포인트
     * @param dailyLimit   변경할 일일 한도 (0 = 무제한)
     * @param maxCount     변경할 평생 한도 (0 = 무제한)
     * @param description  변경할 정책 설명
     */
    public void updatePolicy(Integer pointsAmount, Integer dailyLimit,
                             Integer maxCount, String description) {
        this.pointsAmount = pointsAmount;
        this.dailyLimit = dailyLimit;
        this.maxCount = maxCount;
        this.description = description;
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
}
