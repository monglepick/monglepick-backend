package com.monglepick.monglepickbackend.domain.review.dto;

import com.monglepick.monglepickbackend.domain.review.entity.Review;

import java.time.LocalDateTime;

/**
 * 리뷰 응답 DTO
 *
 * @param id 리뷰 ID
 * @param movieId 영화 ID
 * @param rating 평점
 * @param content 리뷰 본문
 * @param author 작성자 닉네임
 * @param createdAt 작성 시각
 */
public record ReviewResponse(
        Long id,
        Long movieId,
        Double rating,
        String content,
        String author,
        LocalDateTime createdAt
) {
    /**
     * Review 엔티티를 ReviewResponse로 변환하는 팩토리 메서드
     *
     * @param review Review 엔티티
     * @return ReviewResponse 인스턴스
     */
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getMovieId(),
                review.getRating(),
                review.getContent(),
                review.getUser().getNickname(),
                review.getCreatedAt()
        );
    }
}
