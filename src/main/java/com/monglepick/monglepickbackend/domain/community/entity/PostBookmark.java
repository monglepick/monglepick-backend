package com.monglepick.monglepickbackend.domain.community.entity;

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
 * 게시글 북마크 엔티티 — post_bookmarks 테이블 매핑.
 *
 * <p>사용자가 커뮤니티 게시글을 북마크(저장)하여 나중에 다시 볼 수 있도록 한다.
 * 동일 사용자가 동일 게시글을 중복 북마크할 수 없다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code postId} — 게시글 ID (FK → posts.post_id)</li>
 *   <li>{@code userId} — 북마크한 사용자 ID (FK → users.user_id)</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(user_id, post_id) — 동일 사용자가 동일 게시글을 중복 북마크 불가.</p>
 */
@Entity
@Table(
        name = "post_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_post_bookmark",
                columnNames = {"user_id", "post_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostBookmark extends BaseAuditEntity {

    /** 게시글 북마크 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_bookmark_id")
    private Long postBookmarkId;

    /** 게시글 ID (BIGINT, NOT NULL). posts.post_id를 참조한다. */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** 북마크한 사용자 ID (VARCHAR(50), NOT NULL). users.user_id를 참조한다. */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;
}
