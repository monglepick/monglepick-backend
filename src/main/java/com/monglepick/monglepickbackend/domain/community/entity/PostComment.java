package com.monglepick.monglepickbackend.domain.community.entity;

/* BaseAuditEntity로 변경 — created_at/updated_at에 더해 created_by/updated_by 자동 관리 */
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
 * 게시글 댓글 엔티티 — post_comment 테이블 매핑.
 *
 * <p>커뮤니티 게시글에 달린 댓글을 저장한다.
 * 소프트 삭제(is_deleted)를 지원하여, 삭제된 댓글은 "삭제된 댓글입니다"로 표시한다.</p>
 *
 * <h3>대댓글 구조</h3>
 * <p>{@code parentCommentId}가 null이면 최상위 댓글, 값이 있으면 해당 댓글에 대한 답글이다.
 * 1단계 대댓글만 지원한다 (대댓글의 대댓글은 원본 댓글에 대한 답글로 처리).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code postId} — 게시글 ID (FK → posts.id)</li>
 *   <li>{@code categoryId} — 카테고리 ID (nullable)</li>
 *   <li>{@code userId} — 작성자 ID</li>
 *   <li>{@code parentCommentId} — 부모 댓글 ID (null=최상위, FK → post_comment.post_comment_id)</li>
 *   <li>{@code content} — 댓글 내용 (TEXT)</li>
 *   <li>{@code isDeleted} — 소프트 삭제 여부 (기본값: false)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: commentId → postCommentId (컬럼명: post_comment_id)</li>
 *   <li>BaseTimeEntity → BaseAuditEntity로 변경 (created_by/updated_by 추가)</li>
 *   <li>parent_comment_id 추가 — 대댓글 지원</li>
 * </ul>
 */
@Entity
@Table(name = "post_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostComment extends BaseAuditEntity {

    /**
     * 댓글 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: commentId → postCommentId (컬럼명: post_comment_id)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_comment_id")
    private Long postCommentId;

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
     * 작성자 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 부모 댓글 ID (BIGINT, nullable) — 대댓글 지원용 Self FK.
     * null이면 최상위 댓글, 값이 있으면 해당 댓글에 대한 답글이다.
     * post_comment.post_comment_id를 참조한다.
     */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    /** 댓글 내용 (TEXT 타입, NOT NULL) */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 소프트 삭제 여부.
     * 기본값: false.
     * true로 설정하면 댓글이 "삭제된 댓글입니다"로 표시된다.
     */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    /** 좋아요 수 (비정규화, 기본값: 0) */
    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
