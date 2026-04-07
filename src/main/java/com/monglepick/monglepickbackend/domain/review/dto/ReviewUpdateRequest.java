package com.monglepick.monglepickbackend.domain.review.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 리뷰 수정 요청 DTO.
 *
 * <p>{@code PUT /api/v1/movies/{movieId}/reviews/{reviewId}}의 요청 바디로 사용된다.
 * 클라이언트({@code reviewApi.updateReview})는 {@code content}와 {@code rating}만 전송하므로
 * 수정 대상 식별자({@code movieId}, {@code reviewId})는 경로 변수에서 추출한다.</p>
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>{@code rating} — 필수, 0.5 이상 5.0 이하 (0.5 단위 권장)</li>
 *   <li>{@code content} — 선택 (null/빈 문자열 허용, DB 컬럼은 TEXT NULL)</li>
 * </ul>
 *
 * @param rating  변경할 평점 (0.5 ~ 5.0, 필수)
 * @param content 변경할 리뷰 본문 (nullable)
 */
public record ReviewUpdateRequest(

        @NotNull(message = "평점은 필수입니다.")
        @DecimalMin(value = "0.5", message = "평점은 0.5 이상이어야 합니다.")
        @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다.")
        Double rating,

        String content

) {}
