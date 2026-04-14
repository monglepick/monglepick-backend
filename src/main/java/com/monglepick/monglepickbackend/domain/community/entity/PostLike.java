package com.monglepick.monglepickbackend.domain.community.entity;

/* BaseAuditEntity로 변경 — created_at/updated_at에 더해 created_by/updated_by 자동 관리 */
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
 * 게시글 좋아요 엔티티 — post_like 테이블 매핑.
 *
 * <p>사용자가 커뮤니티 게시글에 누른 좋아요를 기록한다.
 * 동일 사용자가 동일 게시글에 중복 좋아요를 누를 수 없다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code postId} — 게시글 ID</li>
 *   <li>{@code categoryId} — 카테고리 ID (nullable)</li>
 *   <li>{@code userId} — 좋아요를 누른 사용자 ID</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(user_id, post_id) — 동일 사용자가 동일 게시글에 중복 좋아요 불가.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: likeId → postLikeId (컬럼명: post_like_id)</li>
 *   <li>BaseTimeEntity → BaseAuditEntity로 변경 (created_by/updated_by 추가)</li>
 * </ul>
 */
@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostLike extends BaseAuditEntity {

    /**
     * 게시글 좋아요 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: likeId → postLikeId (컬럼명: post_like_id)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_like_id")
    private Long postLikeId;

    /**
     * 게시글 ID (BIGINT, NOT NULL).
     * posts.id를 참조한다.
     */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /**
     * 카테고리 ID (BIGINT, nullable).
     * category.category_id를 참조한다.
     */
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * 좋아요를 누른 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
