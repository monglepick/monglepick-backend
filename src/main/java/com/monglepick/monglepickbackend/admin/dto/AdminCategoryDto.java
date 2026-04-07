package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 게시글 카테고리(Category/CategoryChild) 관리 DTO 모음.
 *
 * <p>커뮤니티 게시판의 상위/하위 카테고리 마스터 데이터를 관리한다.</p>
 *
 * <h3>포함 DTO</h3>
 * <ul>
 *   <li>{@link CreateCategoryRequest}     — 상위 카테고리 신규</li>
 *   <li>{@link UpdateCategoryRequest}     — 상위 카테고리명 수정</li>
 *   <li>{@link CreateChildRequest}        — 하위 카테고리 신규</li>
 *   <li>{@link UpdateChildRequest}        — 하위 카테고리명 수정</li>
 *   <li>{@link CategoryResponse}          — 상위 카테고리 단건 (하위 목록 포함 옵션)</li>
 *   <li>{@link CategoryChildResponse}     — 하위 카테고리 단건</li>
 * </ul>
 */
public class AdminCategoryDto {

    // ─────────────────────────────────────────────
    // 상위 카테고리 (Category)
    // ─────────────────────────────────────────────

    public record CreateCategoryRequest(
            @NotBlank(message = "상위 카테고리명은 필수입니다.")
            @Size(max = 100, message = "상위 카테고리명은 100자 이하여야 합니다.")
            String upCategory
    ) {}

    public record UpdateCategoryRequest(
            @NotBlank(message = "상위 카테고리명은 필수입니다.")
            @Size(max = 100, message = "상위 카테고리명은 100자 이하여야 합니다.")
            String upCategory
    ) {}

    /**
     * 상위 카테고리 응답 DTO.
     *
     * <p>{@code children}이 null이면 단순 목록 조회, 비어있지 않으면 하위 카테고리 포함 응답.</p>
     */
    public record CategoryResponse(
            Long categoryId,
            String upCategory,
            List<CategoryChildResponse> children,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    // ─────────────────────────────────────────────
    // 하위 카테고리 (CategoryChild)
    // ─────────────────────────────────────────────

    public record CreateChildRequest(
            @NotNull(message = "상위 카테고리 ID는 필수입니다.")
            Long categoryId,

            @NotBlank(message = "하위 카테고리명은 필수입니다.")
            @Size(max = 100, message = "하위 카테고리명은 100자 이하여야 합니다.")
            String categoryChild
    ) {}

    public record UpdateChildRequest(
            @NotBlank(message = "하위 카테고리명은 필수입니다.")
            @Size(max = 100, message = "하위 카테고리명은 100자 이하여야 합니다.")
            String categoryChild
    ) {}

    public record CategoryChildResponse(
            Long categoryChildId,
            Long categoryId,
            String categoryChild,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
