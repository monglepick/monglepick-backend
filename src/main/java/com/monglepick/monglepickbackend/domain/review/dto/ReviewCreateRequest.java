package com.monglepick.monglepickbackend.domain.review.dto;

import com.monglepick.monglepickbackend.domain.review.entity.ReviewCategoryCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 리뷰 작성 요청 DTO
 *
 * <p>엑셀 5번 reviews 테이블 정의 기준:</p>
 * <ul>
 *   <li>{@code content}     — 행82 [4] {@code contents} (DB 컬럼) ↔ JSON 키는 호환을 위해 {@code content} 유지</li>
 *   <li>{@code reviewSource}      — 행82 [7] {@code review_source} (어디서 작성했는지의 참조 ID)</li>
 *   <li>{@code reviewCategoryCode}— 행82 [8] (영문명 누락 → {@link ReviewCategoryCode} enum, 6종 분류)</li>
 * </ul>
 *
 * @param movieId            리뷰 대상 영화 ID
 * @param rating             평점 (0.5 ~ 5.0)
 * @param content            리뷰 본문 (선택)
 * @param reviewSource       리뷰 작성 출처 — 참조 엔티티 ID (선택).
 *                           예: {@code chat_ses_001}, {@code wsh_2345_003}, {@code cup_mch_005}
 * @param reviewCategoryCode 리뷰 작성 카테고리 코드 — 분류 enum (선택).
 *                           예: {@link ReviewCategoryCode#AI_RECOMMEND}, {@link ReviewCategoryCode#WORLDCUP}
 */
public record ReviewCreateRequest(
        @NotNull(message = "영화 ID는 필수입니다.")
        String movieId,

        @NotNull(message = "평점은 필수입니다.")
        @DecimalMin(value = "0.5", message = "평점은 0.5 이상이어야 합니다.")
        @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다.")
        Double rating,

        String content,

        String reviewSource,

        ReviewCategoryCode reviewCategoryCode
) {}
