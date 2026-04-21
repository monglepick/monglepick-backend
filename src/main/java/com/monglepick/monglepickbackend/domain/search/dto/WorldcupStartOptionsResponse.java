package com.monglepick.monglepickbackend.domain.search.dto;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSourceType;

import java.util.List;

/**
 * 월드컵 시작 가능 라운드 조회 응답 DTO.
 */
public record WorldcupStartOptionsResponse(
        WorldcupSourceType sourceType,
        Long categoryId,
        List<String> selectedGenres,
        int candidatePoolSize,
        List<Integer> availableRoundSizes
) {}
