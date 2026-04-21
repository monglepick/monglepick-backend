package com.monglepick.monglepickbackend.admin.dto;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
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
 *   <li>{@link UpdateRequest}        — 메타 수정 (isActive, popularity는 영화 DB값 자동 반영)</li>
 *   <li>{@link UpdateActiveRequest}  — 활성화 토글</li>
 *   <li>{@link DeactivateBelowRequest} — 인기도 임계값 미만 일괄 비활성화</li>
 *   <li>{@link CandidateResponse}    — 후보 단일 항목 응답</li>
 * </ul>
 */
public class AdminWorldcupCandidateDto {

    /** 신규 후보 등록 요청 (popularity는 하위 호환용으로만 유지, 서버에서 무시) */
    public record CreateRequest(
            @NotBlank(message = "영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

            @Size(max = 100, message = "카테고리 코드는 100자 이하여야 합니다.")
            String category,

            @PositiveOrZero(message = "인기도는 0 이상이어야 합니다.")
            Double popularity
    ) {}

    /** 후보 메타 수정 요청 (movieId/category 제외, popularity는 서버에서 movie DB로 재동기화) */
    public record UpdateRequest(
            @PositiveOrZero(message = "인기도는 0 이상이어야 합니다.")
            Double popularity,
            Boolean isActive
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

    /**
     * 월드컵 후보 등록용 영화 검색 결과 DTO.
     *
     * <p>관리자가 신규 후보 등록 시 제목으로 영화를 검색하고 movieId를 선택할 때 사용한다.
     * ES 검색 결과(recommend) 또는 MySQL fallback 결과를 공통 형태로 정규화한다.</p>
     */
    public record MovieSearchResult(
            String movieId,
            String title,
            String titleEn,
            Integer releaseYear,
            String director,
            String posterPath,
            Double popularity
    ) {
        public static MovieSearchResult from(Movie movie) {
            return new MovieSearchResult(
                    movie.getMovieId(),
                    movie.getTitle(),
                    movie.getTitleEn(),
                    movie.getReleaseYear(),
                    movie.getDirector(),
                    movie.getPosterPath(),
                    movie.getPopularityScore()
            );
        }
    }

    /** 후보 단일 항목 응답 */
    public record CandidateResponse(
            Long id,
            String movieId,
            String movieTitle,
            String movieTitleEn,
            Long categoryId,
            String category,
            String categoryName,
            Double popularity,
            Boolean isActive,
            String addedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** 일괄 비활성화 응답 (영향받은 행 수) */
    public record BulkOperationResponse(
            int affected,
            String message
    ) {}
}
