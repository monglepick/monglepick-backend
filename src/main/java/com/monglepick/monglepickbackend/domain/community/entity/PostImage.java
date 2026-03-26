package com.monglepick.monglepickbackend.domain.community.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 이미지 엔티티 — post_images 테이블 매핑.
 *
 * <p>커뮤니티 게시글에 첨부된 이미지를 저장한다 (REQ_037).
 * 하나의 게시글에 여러 이미지를 첨부할 수 있다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code postId} — 게시글 ID (FK → posts.post_id)</li>
 *   <li>{@code imageUrl} — 이미지 URL (S3 또는 로컬 스토리지 경로)</li>
 *   <li>{@code sortOrder} — 이미지 정렬 순서 (기본값: 0)</li>
 * </ul>
 */
@Entity
@Table(name = "post_images", indexes = {
        @Index(name = "idx_post_images_post", columnList = "post_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostImage extends BaseAuditEntity {

    /** 게시글 이미지 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long postImageId;

    /** 게시글 ID (BIGINT, NOT NULL). posts.post_id를 참조한다. */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** 이미지 URL (VARCHAR(500), NOT NULL). S3 또는 로컬 스토리지 경로 */
    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    /** 이미지 정렬 순서 (기본값: 0). 게시글 내 이미지 표시 순서를 결정한다. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
