package com.monglepick.monglepickbackend.domain.community.dto;

import com.monglepick.monglepickbackend.domain.community.entity.Post;

import java.time.LocalDateTime;

/**
 * 게시글 응답 DTO
 *
 * @param id 게시글 ID
 * @param title 제목
 * @param content 본문
 * @param category 카테고리
 * @param author 작성자 닉네임
 * @param viewCount 조회수
 * @param createdAt 작성 시각
 */
public record PostResponse(
        Long id,
        String title,
        String content,
        String category,
        String author,
        int viewCount,
        LocalDateTime createdAt
) {
    /**
     * Post 엔티티를 PostResponse로 변환하는 팩토리 메서드
     *
     * @param post Post 엔티티
     * @return PostResponse 인스턴스
     */
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory().name(),
                post.getUser().getNickname(),
                post.getViewCount(),
                post.getCreatedAt()
        );
    }
}
