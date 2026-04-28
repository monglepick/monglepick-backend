package com.monglepick.monglepickbackend.domain.review.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * MyBatis 결과 매핑용 내부 전송 객체 — 내 리뷰 목록 + 영화 제목 조인 행.
 *
 * <p>ReviewMapper.findMyReviewsWithTitle 쿼리 결과를 담는다.
 * reviews LEFT JOIN movies 로 영화 제목을 한 번에 가져오며,
 * 포인트 적립 시각은 서비스 레이어에서 별도 쿼리로 조회 후 Map 매핑한다.</p>
 *
 * <p>record 대신 @Getter/@Setter 클래스를 사용하는 이유:
 * MyBatis resultType 매핑은 기본 생성자 + setter 가 필요하기 때문이다.</p>
 *
 * <h3>필드 설명</h3>
 * <ul>
 *   <li>{@code reviewId} — reviews.review_id (BIGINT)</li>
 *   <li>{@code movieId} — reviews.movie_id (VARCHAR 50)</li>
 *   <li>{@code movieTitle} — movies.title (LEFT JOIN, null 허용)</li>
 *   <li>{@code rating} — reviews.rating (DOUBLE)</li>
 *   <li>{@code content} — reviews.contents (TEXT, null 허용)</li>
 *   <li>{@code createdAt} — reviews.created_at (DATETIME)</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class MyReviewSummaryRow {

    /** 리뷰 PK */
    private Long reviewId;

    /** 영화 ID */
    private String movieId;

    /**
     * 영화 제목 (movies.title LEFT JOIN 결과).
     * 영화 데이터가 삭제된 경우 null 일 수 있다.
     */
    private String movieTitle;

    /** 평점 (1.0~5.0) */
    private Double rating;

    /**
     * 리뷰 본문 (reviews.contents 컬럼 매핑).
     * MyReviewSummary.of() 에서 100자로 잘린다.
     */
    private String content;

    /** 리뷰 작성 시각 */
    private LocalDateTime createdAt;
}
