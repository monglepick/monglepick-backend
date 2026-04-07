package com.monglepick.monglepickbackend.domain.community.dto;

import com.monglepick.monglepickbackend.domain.community.entity.Post;

import java.time.LocalDateTime;

/**
 * 게시글 응답 DTO
 *
 * @param id        게시글 ID
 * @param title     제목
 * @param content   본문
 * @param category  카테고리
 * @param author    작성자 닉네임
 * @param viewCount 조회수
 * @param status    게시글 상태 (DRAFT / PUBLISHED)
 * @param createdAt 작성 시각
 */
public record PostResponse(
        Long id,
        String title,
        String content,
        String category,
        String author,
        int viewCount,
        String status,
        LocalDateTime createdAt
) {
    /**
     * Post 엔티티를 PostResponse로 변환하는 팩토리 메서드.
     *
     * <p>{@link Post#getNickname()}은 MyBatis PostMapper의 JOIN users 쿼리 결과로 채워진다
     * (JPA/MyBatis 하이브리드 §15). JOIN 없이 로드된 Post 객체에서는 null이 될 수 있으며,
     * 이 경우 "알 수 없음"으로 표시한다 (소프트 폴백).</p>
     */
    public static PostResponse from(Post post) {
        String nickname = post.getNickname() != null ? post.getNickname() : "알 수 없음";
        return new PostResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory().name(),
                nickname,
                post.getViewCount(),
                post.getStatus().name(),
                post.getCreatedAt()
        );
    }
}
