package com.monglepick.monglepickbackend.domain.review.entity;

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
 * 리뷰 좋아요 엔티티 — review_likes 테이블 매핑.
 *
 * <p>사용자가 영화 리뷰({@code reviews})에 누른 좋아요를 기록한다.
 * 동일 사용자가 동일 리뷰에 중복 좋아요를 누를 수 없다 (UNIQUE 제약).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code reviewId} — 좋아요 대상 리뷰 ID (FK → reviews.review_id)</li>
 *   <li>{@code userId}   — 좋아요를 누른 사용자 ID (FK → users.user_id)</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(review_id, user_id) — 동일 사용자가 동일 리뷰에 중복 좋아요 불가</li>
 *   <li>idx_review_likes_review — 리뷰별 좋아요 수 집계 및 목록 조회 최적화</li>
 *   <li>idx_review_likes_user   — 사용자별 좋아요한 리뷰 목록 조회 최적화</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>좋아요 수는 이 테이블을 COUNT 집계하거나, reviews 테이블의 별도 like_count
 *       컬럼(캐시)을 서비스 레이어에서 동기화하는 방식으로 관리한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "review_likes",
        uniqueConstraints = {
                /* 동일 사용자 동일 리뷰 중복 좋아요 방지 */
                @UniqueConstraint(
                        name = "uk_review_likes_review_user",
                        columnNames = {"review_id", "user_id"}
                )
        },
        indexes = {
                /* 리뷰별 좋아요 목록/카운트 조회 */
                @Index(name = "idx_review_likes_review", columnList = "review_id"),
                /* 사용자별 좋아요한 리뷰 목록 조회 */
                @Index(name = "idx_review_likes_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class ReviewLike extends BaseAuditEntity {

    /**
     * 리뷰 좋아요 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_like_id")
    private Long reviewLikeId;

    /**
     * 좋아요 대상 리뷰 ID (BIGINT, NOT NULL).
     * reviews.review_id를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    /**
     * 좋아요를 누른 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
