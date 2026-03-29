package com.monglepick.monglepickbackend.domain.movie.controller;

import com.monglepick.monglepickbackend.domain.movie.dto.MovieResponse;
import com.monglepick.monglepickbackend.domain.movie.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영화 컨트롤러
 *
 * <p>영화 기본 정보 조회 API를 제공합니다.
 * GET 요청은 비로그인 사용자도 접근 가능합니다 (SecurityConfig 설정).</p>
 *
 * <p>검색 기능은 monglepick-recommend(FastAPI) 프로젝트에서 담당합니다.</p>
 */
@Tag(name = "영화", description = "영화 목록, 상세, 좋아요, 찜")
@Slf4j
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    /**
     * 인기 영화 목록 조회 API (평점순)
     *
     * <p>홈 페이지 "인기 영화" 섹션에서 호출됩니다.
     * 평점이 있는 영화만 평점 내림차순으로 반환합니다.</p>
     *
     * @param pageable 페이징 (기본 page=0, size=8)
     * @return 200 OK + 영화 목록 (Page)
     */
    @Operation(summary = "인기 영화 목록", description = "평점순 인기 영화 목록 조회 (비로그인 가능)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @SecurityRequirement(name = "")
    @GetMapping("/popular")
    public ResponseEntity<Page<MovieResponse>> getPopularMovies(
            @PageableDefault(size = 8) Pageable pageable) {
        log.debug("인기 영화 조회 - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<MovieResponse> movies = movieService.getPopularMovies(pageable);
        return ResponseEntity.ok(movies);
    }

    /**
     * 영화 상세 조회 API
     *
     * @param movieId 영화 ID (내부 DB)
     * @return 200 OK + 영화 정보
     */
    @Operation(summary = "영화 상세 조회", description = "내부 DB 영화 ID로 영화 상세 정보 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "영화 조회 성공"),
            @ApiResponse(responseCode = "404", description = "영화 없음")
    })
    @SecurityRequirement(name = "")
    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponse> getMovie(@PathVariable String movieId) {
        log.debug("영화 상세 조회 - movieId: {}", movieId);
        MovieResponse movie = movieService.getMovie(movieId);
        return ResponseEntity.ok(movie);
    }

    /**
     * TMDB ID로 영화 조회 API
     *
     * @param tmdbId TMDB 영화 ID
     * @return 200 OK + 영화 정보
     */
    @Operation(summary = "TMDB ID로 영화 조회", description = "외부 TMDB ID로 내부 DB 영화를 검색")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "영화 조회 성공"),
            @ApiResponse(responseCode = "404", description = "영화 없음")
    })
    @SecurityRequirement(name = "")
    @GetMapping("/tmdb/{tmdbId}")
    public ResponseEntity<MovieResponse> getMovieByTmdbId(@PathVariable Long tmdbId) {
        log.debug("TMDB ID로 영화 조회 - tmdbId: {}", tmdbId);
        MovieResponse movie = movieService.getMovieByTmdbId(tmdbId);
        return ResponseEntity.ok(movie);
    }
}
