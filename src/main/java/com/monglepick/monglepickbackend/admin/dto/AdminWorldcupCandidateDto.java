package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 월드컵 후보 영화(WorldcupCandidate) 관리 DTO 모음.
 *
 * <p>월드컵 후보 풀을 큐레이션하는 관리자 화면 전용 DTO.</p>
 *
 * <h3>포함 DTO</h3>
 * <ul>
 *   <li>{@link CreateRequest}        — 신규 후보 등록</li>
 *   <li>{@link UpdateRequest}        — 메타 수정 (popularity/isActive/adminNote)</li>
 *   <li>{@link UpdateActiveRequest}  — 활성화 토글</li>
 *   <li>{@link DeactivateBelowRequest} — 인기도 임계값 미만 일괄 비활성화</li>
 *   <li>{@link CandidateResponse}    — 후보 단일 항목 응답</li>
 * </ul>
 */
public class AdminWorldcupCandidateDto {

    /** 신규 후보 등록 요청 */
    public record CreateRequest(
            @NotBlank(message = "영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

            @Size(max = 100, message = "카테고리는 100자 이하여야 합니다.")
            String category,

            @PositiveOrZero(message = "인기도는 0 이상이어야 합니다.")
            Double popularity,

            String adminNote
    ) {}

    /** 후보 메타 수정 요청 (movieId/category 제외) */
    public record UpdateRequest(
            @PositiveOrZero(message = "인기도는 0 이상이어야 합니다.")
            Double popularity,

            Boolean isActive,
            String adminNote
    ) {}

    /** 활성화 토글 요청 */
    public record UpdateActiveRequest(
            Boolean isActive
    ) {}

    /** 인기도 임계값 미만 일괄 비활성화 요청 */
    public record DeactivateBelowRequest(
            @PositiveOrZero(message = "임계값은 0 이상이어야 합니다.")
            Double threshold
    ) {}

    /** 후보 단일 항목 응답 */
    public record CandidateResponse(
            Long id,
            String movieId,
            String category,
            Double popularity,
            Boolean isActive,
            String addedBy,
            String adminNote,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** 일괄 비활성화 응답 (영향받은 행 수) */
    public record BulkOperationResponse(
            int affected,
            String message
    ) {}
}
