package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 인기 검색어(PopularSearchKeyword) 관리 DTO 모음.
 *
 * <p>인기 검색어는 SearchHistory로부터 자동 집계되지만, 관리자가 수동으로
 * 강제 노출/제외할 수 있도록 별도 마스터 데이터를 둔다.</p>
 *
 * <h3>포함 DTO</h3>
 * <ul>
 *   <li>{@link CreateRequest}    — 신규 키워드 등록</li>
 *   <li>{@link UpdateRequest}    — 메타 수정 (keyword 제외)</li>
 *   <li>{@link UpdateExcludedRequest} — 제외(블랙리스트) 토글</li>
 *   <li>{@link KeywordResponse}  — 키워드 단일 항목 응답</li>
 * </ul>
 */
public class AdminPopularSearchDto {

    /** 신규 키워드 등록 요청 */
    public record CreateRequest(
            @NotBlank(message = "키워드는 필수입니다.")
            @Size(max = 200, message = "키워드는 200자 이하여야 합니다.")
            String keyword,

            Integer displayRank,

            @PositiveOrZero(message = "수동 우선순위는 0 이상이어야 합니다.")
            Integer manualPriority,

            Boolean isExcluded,

            String adminNote
    ) {}

    /** 키워드 메타 수정 요청 (keyword 제외) */
    public record UpdateRequest(
            Integer displayRank,

            @PositiveOrZero(message = "수동 우선순위는 0 이상이어야 합니다.")
            Integer manualPriority,

            Boolean isExcluded,

            String adminNote
    ) {}

    /** 블랙리스트 토글 요청 */
    public record UpdateExcludedRequest(
            Boolean isExcluded
    ) {}

    /** 키워드 단일 항목 응답 */
    public record KeywordResponse(
            Long id,
            String keyword,
            Integer displayRank,
            Integer manualPriority,
            Boolean isExcluded,
            String adminNote,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
