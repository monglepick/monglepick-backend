package com.monglepick.monglepickbackend.domain.community.entity;

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
 * 댓글 좋아요 엔티티 — comment_likes 테이블 매핑.
 *
 * <p>사용자가 커뮤니티 게시글 댓글({@code post_comment})에 누른 좋아요를 기록한다.
 * 동일 사용자가 동일 댓글에 중복 좋아요를 누를 수 없다 (UNIQUE 제약).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code commentId} — 좋아요 대상 댓글 ID (FK → post_comment.post_comment_id)</li>
 *   <li>{@code userId}    — 좋아요를 누른 사용자 ID (FK → users.user_id)</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(comment_id, user_id) — 동일 사용자가 동일 댓글에 중복 좋아요 불가</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>{@link PostLike}와 구조가 동일하나 대상이 댓글(comment)로 분리되어 있다.
 *       게시글 좋아요와 댓글 좋아요는 별도 카운트가 필요하므로 테이블을 분리한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "comment_likes",
        uniqueConstraints = {
                /* 동일 사용자 동일 댓글 중복 좋아요 방지 */
                @UniqueConstraint(
                        name = "uk_comment_likes_comment_user",
                        columnNames = {"comment_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class CommentLike extends BaseAuditEntity {

    /**
     * 댓글 좋아요 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_like_id")
    private Long commentLikeId;

    /**
     * 좋아요 대상 댓글 ID (BIGINT, NOT NULL).
     * post_comment.post_comment_id를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    /**
     * 좋아요를 누른 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
