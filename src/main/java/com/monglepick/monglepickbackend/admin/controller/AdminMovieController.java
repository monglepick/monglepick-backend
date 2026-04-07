package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminMovieDto.CreateMovieRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminMovieDto.MovieResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminMovieDto.UpdateMovieRequest;
import com.monglepick.monglepickbackend.admin.service.AdminMovieService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 영화(Movie) 마스터 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 영화 관리" 메뉴의 5개 엔드포인트를 제공한다.
 * 외부 데이터 소스(TMDB/Kaggle/KMDb/KOBIS) 동기화 파이프라인과는 별도로,
 * 관리자가 수동으로 영화를 추가/수정/삭제할 수 있는 경로를 제공한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/movies              — 영화 목록 조회 (페이징 + 키워드)</li>
 *   <li>GET    /api/v1/admin/movies/{movieId}    — 영화 단건 조회</li>
 *   <li>POST   /api/v1/admin/movies              — 영화 신규 등록 (source='admin')</li>
 *   <li>PUT    /api/v1/admin/movies/{movieId}    — 영화 수정 (식별자 제외)</li>
 *   <li>DELETE /api/v1/admin/movies/{movieId}    — 영화 hard delete (운영상 신중)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 영화 관리", description = "Movie 마스터 등록/수정/삭제 (TMDB 동기화 파이프라인과 별개)")
@RestController
@RequestMapping("/api/v1/admin/movies")
@RequiredArgsConstructor
@Slf4j
public class AdminMovieController {

    /** 영화 마스터 관리 서비스 */
    private final AdminMovieService adminMovieService;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 영화 목록 조회 (페이징 + 키워드 검색).
     *
     * @param keyword 제목 검색 키워드 (생략 시 전체 조회)
     * @param page    페이지 번호
     * @param size    페이지 크기
     */
    @Operation(
            summary = "영화 목록 조회",
            description = "title/titleEn LIKE 검색. keyword 생략 시 전체 영화를 페이징 조회한다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<MovieResponse>>> getMovies(
            @Parameter(description = "제목 검색 키워드 (생략 시 전체)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MovieResponse> result = adminMovieService.getMovies(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 영화 단건 조회.
     *
     * @param movieId 영화 ID
     */
    @Operation(summary = "영화 단건 조회", description = "movie_id로 단건 조회")
    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieResponse>> getMovie(
            @PathVariable String movieId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminMovieService.getMovie(movieId)));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 영화 신규 등록 (source='admin').
     *
     * <p>movieId UNIQUE — 중복 시 409.
     * tmdbId가 있으면 별도 UNIQUE 검증.</p>
     */
    @Operation(
            summary = "영화 신규 등록",
            description = "관리자가 직접 등록하는 영화. source='admin' 고정. movieId/tmdbId UNIQUE."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<MovieResponse>> createMovie(
            @Valid @RequestBody CreateMovieRequest request
    ) {
        log.info("[관리자] 영화 등록 요청 — movieId={}, title={}", request.movieId(), request.title());
        MovieResponse created = adminMovieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /**
     * 영화 수정 (식별자 제외 핵심 필드만).
     *
     * <p>movieId/tmdbId/source 및 KOBIS/KMDb 외부 소스 컬럼은 수정 불가.</p>
     */
    @Operation(
            summary = "영화 수정",
            description = "title/overview/장르/감독/포스터/평점/관람등급 등 핵심 메타 필드 일괄 수정"
    )
    @PutMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieResponse>> updateMovie(
            @PathVariable String movieId,
            @Valid @RequestBody UpdateMovieRequest request
    ) {
        log.info("[관리자] 영화 수정 요청 — movieId={}", movieId);
        MovieResponse updated = adminMovieService.updateMovie(movieId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * 영화 hard delete.
     *
     * <p>주의: reviews/playlist_items 등이 movie_id를 String FK로 보관하므로
     * 삭제 시 해당 테이블에 orphan ID가 남을 수 있다. 운영상 신중하게 사용.</p>
     */
    @Operation(
            summary = "영화 삭제 (hard delete)",
            description = "Movie 엔티티에 is_deleted 컬럼이 없어 hard delete만 지원. orphan FK 주의."
    )
    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovie(@PathVariable String movieId) {
        log.warn("[관리자] 영화 삭제 요청 — movieId={}", movieId);
        adminMovieService.deleteMovie(movieId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
