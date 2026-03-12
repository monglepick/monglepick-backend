package com.monglepick.monglepickbackend.domain.movie.controller;

import com.monglepick.monglepickbackend.domain.movie.dto.MovieResponse;
import com.monglepick.monglepickbackend.domain.movie.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>※ 검색 기능(키워드/하이브리드)은 monglepick-recommend(FastAPI) 프로젝트로 이관되었습니다.
 * 검색 관련 엔드포인트는 monglepick-recommend 서비스의 /api/v1/search를 사용하세요.</p>
 *
 * <p>API 목록:</p>
 * <ul>
 *   <li>GET /api/v1/movies/{id} - 영화 상세 조회</li>
 *   <li>GET /api/v1/movies/tmdb/{tmdbId} - TMDB ID로 영화 조회</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    /**
     * 영화 상세 조회 API
     *
     * @param id 영화 ID (내부 DB)
     * @return 200 OK + 영화 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<MovieResponse> getMovie(@PathVariable Long id) {
        log.debug("영화 상세 조회 - movieId: {}", id);
        MovieResponse movie = movieService.getMovie(id);
        return ResponseEntity.ok(movie);
    }

    /**
     * TMDB ID로 영화 조회 API
     *
     * <p>외부 API(TMDB) 연동 시 TMDB ID로 내부 DB 영화를 찾을 때 사용합니다.</p>
     *
     * @param tmdbId TMDB 영화 ID
     * @return 200 OK + 영화 정보
     */
    @GetMapping("/tmdb/{tmdbId}")
    public ResponseEntity<MovieResponse> getMovieByTmdbId(@PathVariable Long tmdbId) {
        log.debug("TMDB ID로 영화 조회 - tmdbId: {}", tmdbId);
        MovieResponse movie = movieService.getMovieByTmdbId(tmdbId);
        return ResponseEntity.ok(movie);
    }

    // ※ 검색 엔드포인트(GET /api/v1/movies/search)는 삭제되었습니다.
    // 영화 검색 기능은 monglepick-recommend(FastAPI) 프로젝트에서 담당합니다.
    // - 키워드 검색: monglepick-recommend /api/v1/search
    // - 하이브리드 검색: monglepick-recommend /api/v1/search/hybrid
}
