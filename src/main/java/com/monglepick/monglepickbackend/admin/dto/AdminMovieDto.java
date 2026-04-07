package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 영화(Movie) 마스터 관리 DTO 모음.
 *
 * <p>영화 등록·수정·삭제 화면에서 사용하는 요청/응답 record를 정의한다.
 * Movie 엔티티는 외부 데이터 소스(TMDB/Kaggle/KMDb/KOBIS) 동기화 파이프라인에 의해
 * 주로 적재되지만, 관리자가 수동으로 영화를 추가/수정/삭제할 수 있어야 한다.</p>
 *
 * <h3>변경 불가 필드</h3>
 * <ul>
 *   <li>{@code movieId} — PK, 신규 등록 시에만 입력</li>
 *   <li>{@code source} — 외부 데이터 소스 식별자, 신규 등록 시 'admin' 고정</li>
 *   <li>{@code tmdbId} — TMDB 외부 ID. 신규 등록 시 입력 가능, 이후 동기화 충돌 방지를 위해 변경 불가</li>
 *   <li>KOBIS/KMDb 관련 필드 — 별도 동기화 파이프라인 책임</li>
 * </ul>
 *
 * <h3>JSON 컬럼 처리</h3>
 * <p>genres/castMembers/keywords/ottPlatforms/moodTags는 DB에 JSON 문자열로 저장되며,
 * DTO에서는 단순 문자열로 받는다(클라이언트가 직접 JSON 문자열을 구성). 향후 List 변환 가능.</p>
 */
public class AdminMovieDto {

    // ─────────────────────────────────────────────
    // 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 신규 영화 등록 요청 DTO.
     *
     * <p>movieId UNIQUE 제약 — 중복 시 409.
     * 관리자가 직접 등록한 영화는 source='admin' 으로 고정한다.</p>
     */
    public record CreateMovieRequest(
            @NotBlank(message = "영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

            Long tmdbId,

            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 500, message = "제목은 500자 이하여야 합니다.")
            String title,

            @Size(max = 500, message = "영문 제목은 500자 이하여야 합니다.")
            String titleEn,

            String overview,
            String genres,
            Integer releaseYear,
            LocalDate releaseDate,
            Integer runtime,
            Double rating,

            @Size(max = 500, message = "포스터 경로는 500자 이하여야 합니다.")
            String posterPath,

            String castMembers,

            @Size(max = 500, message = "감독명은 500자 이하여야 합니다.")
            String director,

            String keywords,
            String ottPlatforms,
            String moodTags,

            @Size(max = 50, message = "관람등급은 50자 이하여야 합니다.")
            String certification,

            @Size(max = 500, message = "예고편 URL은 500자 이하여야 합니다.")
            String trailerUrl,

            @Size(max = 500, message = "태그라인은 500자 이하여야 합니다.")
            String tagline,

            @Size(max = 10, message = "원어 코드는 10자 이하여야 합니다.")
            String originalLanguage,

            @Size(max = 500, message = "배경 이미지 경로는 500자 이하여야 합니다.")
            String backdropPath,

            Boolean adult,
            String awards,

            @Size(max = 500, message = "촬영 장소는 500자 이하여야 합니다.")
            String filmingLocation
    ) {}

    /**
     * 기존 영화 수정 요청 DTO (movieId/tmdbId/source/외부소스 컬럼 제외).
     */
    public record UpdateMovieRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 500, message = "제목은 500자 이하여야 합니다.")
            String title,

            @Size(max = 500, message = "영문 제목은 500자 이하여야 합니다.")
            String titleEn,

            String overview,
            String genres,
            Integer releaseYear,
            LocalDate releaseDate,
            Integer runtime,
            Double rating,

            @Size(max = 500, message = "포스터 경로는 500자 이하여야 합니다.")
            String posterPath,

            String castMembers,

            @Size(max = 500, message = "감독명은 500자 이하여야 합니다.")
            String director,

            String keywords,
            String ottPlatforms,
            String moodTags,

            @Size(max = 50, message = "관람등급은 50자 이하여야 합니다.")
            String certification,

            @Size(max = 500, message = "예고편 URL은 500자 이하여야 합니다.")
            String trailerUrl,

            @Size(max = 500, message = "태그라인은 500자 이하여야 합니다.")
            String tagline,

            @Size(max = 10, message = "원어 코드는 10자 이하여야 합니다.")
            String originalLanguage,

            @Size(max = 500, message = "배경 이미지 경로는 500자 이하여야 합니다.")
            String backdropPath,

            Boolean adult,
            String awards,

            @Size(max = 500, message = "촬영 장소는 500자 이하여야 합니다.")
            String filmingLocation
    ) {}

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 영화 단일 항목 응답 DTO (관리자 화면 테이블/상세).
     */
    public record MovieResponse(
            String movieId,
            Long tmdbId,
            String title,
            String titleEn,
            String overview,
            String genres,
            Integer releaseYear,
            LocalDate releaseDate,
            Integer runtime,
            Double rating,
            String posterPath,
            String castMembers,
            String director,
            String keywords,
            String ottPlatforms,
            String moodTags,
            String source,
            String certification,
            String trailerUrl,
            String tagline,
            String originalLanguage,
            String backdropPath,
            Boolean adult,
            Double popularityScore,
            Long voteCount,
            String awards,
            String filmingLocation,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
