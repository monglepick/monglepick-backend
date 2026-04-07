package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminGenreDto.CreateGenreRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminGenreDto.GenreResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminGenreDto.UpdateGenreRequest;
import com.monglepick.monglepickbackend.admin.service.AdminGenreService;
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

import java.util.List;

/**
 * 관리자 장르 마스터(GenreMaster) 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 장르 마스터" 메뉴의 5개 엔드포인트를 제공한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/genres            — 장르 목록 조회 (페이징)</li>
 *   <li>GET    /api/v1/admin/genres/all        — 전체 장르 목록 (드롭다운/필터용, 페이징 없음)</li>
 *   <li>GET    /api/v1/admin/genres/{id}       — 장르 단건 조회</li>
 *   <li>POST   /api/v1/admin/genres            — 장르 신규 등록 (genre_code UNIQUE)</li>
 *   <li>PUT    /api/v1/admin/genres/{id}       — 장르 한국어명 수정 (genre_code 제외)</li>
 *   <li>DELETE /api/v1/admin/genres/{id}       — 장르 hard delete</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 장르 마스터", description = "GenreMaster 마스터 데이터 등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/admin/genres")
@RequiredArgsConstructor
@Slf4j
public class AdminGenreController {

    private final AdminGenreService adminGenreService;

    /**
     * 장르 목록 페이징 조회.
     */
    @Operation(summary = "장르 목록 조회 (페이징)", description = "createdAt DESC 정렬")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<GenreResponse>>> getGenres(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GenreResponse> result = adminGenreService.getGenres(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 전체 장르 목록 (드롭다운/필터용, 페이징 없음).
     */
    @Operation(
            summary = "전체 장르 목록",
            description = "페이징 없이 모든 장르 반환. 영화 등록 모달의 장르 선택 드롭다운 등에서 사용."
    )
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<GenreResponse>>> getAllGenres() {
        return ResponseEntity.ok(ApiResponse.ok(adminGenreService.getAllGenres()));
    }

    /**
     * 장르 단건 조회.
     */
    @Operation(summary = "장르 단건 조회", description = "genre_id로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GenreResponse>> getGenre(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminGenreService.getGenre(id)));
    }

    /**
     * 장르 신규 등록.
     *
     * <p>{@code genre_code} UNIQUE — 중복 시 409.</p>
     */
    @Operation(
            summary = "장르 신규 등록",
            description = "신규 장르 마스터 등록. genre_code는 UNIQUE이므로 중복 시 409."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<GenreResponse>> createGenre(
            @Valid @RequestBody CreateGenreRequest request
    ) {
        log.info("[관리자] 장르 등록 요청 — code={}, name={}", request.genreCode(), request.genreName());
        GenreResponse created = adminGenreService.createGenre(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /**
     * 장르 한국어명 수정 (genre_code 제외).
     */
    @Operation(
            summary = "장르 수정",
            description = "장르 한국어명만 수정 가능. genre_code는 시스템 식별자이므로 변경 불가."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GenreResponse>> updateGenre(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGenreRequest request
    ) {
        log.info("[관리자] 장르 수정 요청 — id={}, name={}", id, request.genreName());
        GenreResponse updated = adminGenreService.updateGenre(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * 장르 hard delete.
     */
    @Operation(
            summary = "장르 삭제 (hard delete)",
            description = "contents_count > 0인 경우 경고 로그만 남기고 삭제 진행. 운영상 신중하게 사용."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGenre(@PathVariable Long id) {
        log.warn("[관리자] 장르 삭제 요청 — id={}", id);
        adminGenreService.deleteGenre(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
