package com.monglepick.monglepickbackend.domain.search.dto;

import java.util.List;

/**
 * 사용자 월드컵 시작 화면용 카테고리 옵션 응답.
 */
public record WorldcupCategoryOptionResponse(
        Long categoryId,
        String categoryCode,
        String categoryName,
        String description,
        Integer displayOrder,
        int candidatePoolSize,
        List<Integer> availableRoundSizes
) {}
