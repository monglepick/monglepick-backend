package com.monglepick.monglepickbackend.domain.review.dto;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewCategoryCode;

import java.time.LocalDateTime;

/**
 * 리뷰 응답 DTO
 *
 * <p>엑셀 5번 reviews 컬럼 정의를 반영한다. 클라이언트 호환을 위해 JSON 키({@code content})는
 * Java 필드명 그대로 사용하며, DB 컬럼명({@code contents})과는 무관하다.</p>
 *
 * @param id                 리뷰 ID
 * @param movieId            영화 ID
 * @param rating             평점
 * @param content            리뷰 본문
 * @param author             작성자 닉네임
 * @param reviewSource       리뷰 작성 출처 참조 ID (chat_ses_001, wsh_001 등)
 * @param reviewCategoryCode 리뷰 작성 카테고리 코드 — 6종 분류 enum (nullable)
 * @param createdAt          작성 시각
 */
public record ReviewResponse(
        Long id,
        String movieId,
        Double rating,
        String content,
        String author,
        String reviewSource,
        ReviewCategoryCode reviewCategoryCode,
        LocalDateTime createdAt
) {
    /**
     * Review 엔티티를 ReviewResponse로 변환하는 팩토리 메서드
     *
     * @param review Review 엔티티
     * @return ReviewResponse 인스턴스
     */
    /**
     * {@link Review#getNickname()}은 MyBatis ReviewMapper의 JOIN users 쿼리 결과로 채워진다
     * (JPA/MyBatis 하이브리드 §15). JOIN 없이 로드된 Review 객체에서는 null이며,
     * 이 경우 "알 수 없음"으로 폴백한다.
     */
    public static ReviewResponse from(Review review) {
        String nickname = review.getNickname() != null ? review.getNickname() : "알 수 없음";
        return new ReviewResponse(
                review.getReviewId(),
                review.getMovieId(),
                review.getRating(),
                review.getContent(),
                nickname,
                review.getReviewSource(),
                review.getReviewCategoryCode(),
                review.getCreatedAt()
        );
    }
}
