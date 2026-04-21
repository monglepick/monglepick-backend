package com.monglepick.monglepickbackend.domain.search.dto;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSourceType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 월드컵 시작 전 후보 풀/가능 라운드 조회 요청 DTO.
 */
public record WorldcupStartOptionsRequest(
        @NotNull(message = "월드컵 시작 방식은 필수입니다")
        WorldcupSourceType sourceType,

        Long categoryId,

        List<String> selectedGenres
) {}
