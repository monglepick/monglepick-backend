package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
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
 * 도장깨기 코스 리뷰 엔티티 — course_review 테이블 매핑.
 *
 * <p>도장깨기(로드맵) 코스에서 영화를 시청한 후 작성하는 리뷰를 저장한다.
 * 동일 코스의 동일 영화에 대해 사용자당 하나의 리뷰만 작성 가능하다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code courseId} — 코스 ID (VARCHAR(50))</li>
 *   <li>{@code movieId} — 영화 ID (VARCHAR(50))</li>
 *   <li>{@code userId} — 작성자 ID (VARCHAR(50))</li>
 *   <li>{@code reviewText} — 리뷰 본문 (TEXT, nullable)</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(course_id, movie_id, user_id) — 동일 코스+영화+사용자 조합에 중복 리뷰 불가.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드(courseReviewId)는 이미 올바른 네이밍이므로 변경 없음</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>수동 createdAt 필드 및 @CreationTimestamp 제거 — BaseTimeEntity에서 상속</li>
 * </ul>
 */
@Entity
@Table(
        name = "course_review",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "movie_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CourseReview extends BaseAuditEntity {

    /** 코스 리뷰 고유 ID (BIGINT AUTO_INCREMENT PK, 변경 없음) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_review_id")
    private Long courseReviewId;

    /**
     * 코스 ID (VARCHAR(50), NOT NULL).
     * 도장깨기 코스를 식별한다.
     */
    @Column(name = "course_id", length = 50, nullable = false)
    private String courseId;

    /**
     * 영화 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 참조한다.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /**
     * 작성자 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 리뷰 본문 (TEXT 타입, nullable).
     * 텍스트 리뷰가 없이 시청 완료만 기록하는 경우 null.
     */
    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;

    // ========== Excel Table 19 기준 추가 컬럼 (2개) ==========

    /**
     * 인증 횟수 (기본값: 0).
     * 해당 리뷰(시청 완료)가 다른 사용자로부터 인증(검증)받은 횟수를 기록한다.
     * 커뮤니티 신뢰도 지표 및 ACHIEVEMENT 리워드 조건 판정에 활용된다.
     */
    @Column(name = "verified_count", nullable = false)
    @Builder.Default
    private Integer verifiedCount = 0;

    /**
     * 이 리뷰 작성으로 지급된 포인트 (기본값: 0).
     * RoadmapService가 도장깨기 코스 영화 시청 완료 리뷰 작성 시
     * 등급 배율을 적용하여 지급한 실제 포인트 금액을 기록한다.
     * 지급 이력 조회 및 정산 근거로 활용된다.
     */
    @Column(name = "award_point", nullable = false)
    @Builder.Default
    private Integer awardPoint = 0;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
