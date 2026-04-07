package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
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
 * 업적 유형 마스터 엔티티 — achievement_types 테이블 매핑.
 *
 * <p>사용자가 달성할 수 있는 업적의 종류(메타 정보)를 관리하는 마스터 테이블이다.
 * 관리자 페이지에서 업적 종류를 동적으로 추가·수정·비활성화할 수 있다.</p>
 *
 * <p>실제 사용자별 달성 기록은 {@link UserAchievement}에 저장되며,
 * {@code UserAchievement.achievementType} FK가 이 테이블을 참조한다.</p>
 *
 * <h3>기본 업적 유형 (앱 시작 시 자동 초기화)</h3>
 * <ul>
 *   <li>{@code course_complete} — 도장깨기 코스 완주 (보상 100P)</li>
 *   <li>{@code quiz_perfect}    — 퀴즈 만점 달성 (보상 50P)</li>
 *   <li>{@code review_count_10} — 리뷰 10개 작성 (보상 200P)</li>
 *   <li>{@code genre_explorer}  — 5개 장르 탐험 (보상 150P)</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>{@code isActive}가 false이면 새 달성 기록이 생성되지 않는다 (기존 기록은 보존).</li>
 *   <li>{@code requiredCount}는 달성 조건 횟수이며, 진행 상황은 별도 집계 로직에서 관리한다.</li>
 *   <li>{@code rewardPoints}는 업적 최초 달성 시 자동 지급되는 포인트이다.</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>최초 생성 — UserAchievement 1테이블 구조를 2테이블로 분리 시 마스터 테이블 신규 추가</li>
 * </ul>
 */
@Entity
@Table(name = "achievement_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성을 위한 protected 기본 생성자 */
@AllArgsConstructor
@Builder
public class AchievementType extends BaseAuditEntity {

    /**
     * 업적 유형 고유 ID (BIGINT AUTO_INCREMENT PK).
     * {@link UserAchievement}에서 FK로 참조한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "achievement_type_id")
    private Long achievementTypeId;

    /**
     * 업적 코드 — 시스템 내부 식별자 (UNIQUE, 최대 50자, 필수).
     *
     * <p>영문 소문자+언더스코어 형식의 고정 코드 값이다.
     * 서비스 로직에서 업적 달성 판정 시 이 값으로 조회한다.</p>
     *
     * <p>예: "course_complete", "quiz_perfect", "review_count_10", "genre_explorer"</p>
     */
    @Column(name = "achievement_code", nullable = false, unique = true, length = 50)
    private String achievementCode;

    /**
     * 업적 표시명 — 한국어 사용자 화면 노출 이름 (최대 100자, 필수).
     * 예: "코스 완주", "퀴즈 만점", "리뷰 10개 달성", "5개 장르 탐험"
     */
    @Column(name = "achievement_name", nullable = false, length = 100)
    private String achievementName;

    /**
     * 업적 설명 — 달성 조건 및 내용을 설명하는 텍스트 (TEXT 타입, 선택).
     * 예: "도장깨기 코스를 완주하면 획득할 수 있습니다."
     * v5 스펙: TEXT 타입으로 변경 (기존 VARCHAR(500) → TEXT).
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 달성 조건 횟수 (nullable).
     *
     * <p>업적 달성에 필요한 반복 횟수를 의미한다 (v5 스펙: conditionValue).
     * null이면 1회 달성으로 완료되는 업적이다.</p>
     *
     * <p>예: review_count_10 → 10, genre_explorer → 5</p>
     *
     * <p>v5 설계서에서 conditionValue로 명명되나,
     * 기존 컬럼명 required_count를 유지하여 마이그레이션 충격을 최소화한다.</p>
     */
    @Column(name = "required_count")
    private Integer requiredCount;

    /**
     * 업적 달성 시 지급되는 보상 포인트 (기본값: 0).
     *
     * <p>0이면 포인트 보상 없음.
     * {@link com.monglepick.monglepickbackend.domain.reward.entity.UserPoint}에
     * 자동 적립 처리된다 (서비스 레이어 연동).</p>
     *
     * <p>v5 스펙: nullable → 기본값 0으로 변경하여 NULL 처리 로직 단순화.</p>
     */
    @Column(name = "reward_points")
    @Builder.Default
    private Integer rewardPoints = 0;

    /**
     * 업적 아이콘 URL (최대 500자, 선택).
     * 프론트엔드에서 업적 배지 이미지를 렌더링할 때 사용한다.
     */
    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    /**
     * 업적 카테고리 — 프론트엔드 필터링용 분류값 (최대 50자, nullable).
     *
     * <p>사용자 인터페이스에서 업적을 종류별로 분류할 때 사용한다.
     * null이면 기타(미분류) 업적으로 취급한다.</p>
     *
     * <h4>정의된 카테고리 값</h4>
     * <ul>
     *   <li>{@code "VIEWING"}    — 시청 관련 업적 (예: genre_explorer)</li>
     *   <li>{@code "SOCIAL"}     — 소셜/커뮤니티 활동 업적 (예: review_count_10)</li>
     *   <li>{@code "COLLECTION"} — 수집/저장 관련 업적 (예: playlist 관련)</li>
     *   <li>{@code "CHALLENGE"}  — 도전과제 업적 (예: course_complete, quiz_perfect)</li>
     *   <li>{@code null}         — 기타/미분류 업적</li>
     * </ul>
     *
     * <p>JPA ddl-auto=update에 의해 category VARCHAR(50) 컬럼이 자동으로 추가된다.</p>
     */
    @Column(name = "category", length = 50)
    private String category; // VIEWING, SOCIAL, COLLECTION, CHALLENGE, null(기타)

    /**
     * 업적 활성화 여부 (기본값 true).
     *
     * <p>false이면 해당 업적에 대한 새 달성 기록이 생성되지 않는다.
     * 기존에 달성한 사용자의 기록({@link UserAchievement})은 그대로 보존된다.</p>
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 업적 활성화 상태를 변경한다 (관리자 페이지 수정용).
     *
     * @param active true이면 활성화, false이면 비활성화
     */
    public void updateActiveStatus(boolean active) {
        this.isActive = active;
    }

    /**
     * 업적 기본 정보를 수정한다 (관리자 페이지 수정용).
     *
     * @param achievementName 변경할 표시명
     * @param description     변경할 설명
     * @param rewardPoints    변경할 보상 포인트
     * @param iconUrl         변경할 아이콘 URL
     * @param category        변경할 카테고리 (VIEWING/SOCIAL/COLLECTION/CHALLENGE/null)
     */
    public void updateInfo(String achievementName, String description,
                           Integer rewardPoints, String iconUrl, String category) {
        this.achievementName = achievementName;
        this.description = description;
        this.rewardPoints = rewardPoints;
        this.iconUrl = iconUrl;
        this.category = category;
    }
}
