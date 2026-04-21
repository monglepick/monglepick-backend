package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 월드컵 후보 카테고리(WorldcupCategory) 관리 DTO 모음.
 */
public class AdminWorldcupCategoryDto {

    public record CreateRequest(
            @NotBlank(message = "카테고리 코드는 필수입니다.")
            @Size(max = 100, message = "카테고리 코드는 100자 이하여야 합니다.")
            String categoryCode,

            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
            String categoryName,

            String description,

            String adminNote,

            Boolean enabled,

            @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
            Integer displayOrder
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
            String categoryName,

            String description,

            String adminNote,

            Boolean enabled,

            @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
            Integer displayOrder
    ) {}

    public record CategoryResponse(
            Long categoryId,
            String categoryCode,
            String categoryName,
            String description,
            String adminNote,
            boolean enabled,
            Integer displayOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
