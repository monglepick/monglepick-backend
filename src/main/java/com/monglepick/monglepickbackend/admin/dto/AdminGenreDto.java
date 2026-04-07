package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 장르 마스터(GenreMaster) 관리 DTO 모음.
 *
 * <p>장르 마스터 데이터는 추천 엔진/필터/온보딩 장르 선택 등에서 사용되는
 * 마스터 데이터이다. 관리자가 신규 장르를 추가하거나 한국어 표시명을 변경한다.</p>
 *
 * <h3>변경 불가 필드</h3>
 * <ul>
 *   <li>{@code genreCode} — 시스템 식별자, 신규 등록 시에만 입력</li>
 *   <li>{@code contentsCount} — 영화 등록/삭제 시 자동 동기화되는 비정규화 카운터</li>
 * </ul>
 */
public class AdminGenreDto {

    /**
     * 신규 장르 등록 요청 DTO.
     *
     * <p>{@code genreCode} UNIQUE — 중복 시 409.</p>
     */
    public record CreateGenreRequest(
            @NotBlank(message = "장르 코드는 필수입니다.")
            @Size(max = 50, message = "장르 코드는 50자 이하여야 합니다.")
            String genreCode,

            @NotBlank(message = "장르 한국어명은 필수입니다.")
            @Size(max = 100, message = "장르명은 100자 이하여야 합니다.")
            String genreName
    ) {}

    /**
     * 장르 한국어명 수정 요청 DTO (genreCode 제외).
     */
    public record UpdateGenreRequest(
            @NotBlank(message = "장르 한국어명은 필수입니다.")
            @Size(max = 100, message = "장르명은 100자 이하여야 합니다.")
            String genreName
    ) {}

    /**
     * 장르 단일 항목 응답 DTO.
     */
    public record GenreResponse(
            Long genreId,
            String genreCode,
            String genreName,
            Integer contentsCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
