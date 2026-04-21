package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.BulkOperationResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CandidateResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.DeactivateBelowRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.MovieSearchResult;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.UpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminWorldcupCandidateService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 월드컵 후보 영화(WorldcupCandidate) 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 월드컵 후보" 메뉴의 8개 엔드포인트를 제공한다.
 * 월드컵 후보 풀을 큐레이션하고, 인기 없는 영화를 일괄 제외한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/worldcup-candidates/movies/search  — 후보 등록용 영화 검색</li>
 *   <li>GET    /api/v1/admin/worldcup-candidates                — 후보 목록 (페이징 + 카테고리 코드 필터)</li>
 *   <li>GET    /api/v1/admin/worldcup-candidates/{id}           — 단건 조회</li>
 *   <li>POST   /api/v1/admin/worldcup-candidates                — 신규 등록 ((movieId, category_id) UNIQUE)</li>
 *   <li>PUT    /api/v1/admin/worldcup-candidates/{id}           — 메타 수정</li>
 *   <li>PATCH  /api/v1/admin/worldcup-candidates/{id}/active    — 활성화 토글</li>
 *   <li>POST   /api/v1/admin/worldcup-candidates/deactivate-below — 인기도 임계값 미만 일괄 비활성화</li>
 *   <li>DELETE /api/v1/admin/worldcup-candidates/{id}           — hard delete</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 월드컵 후보", description = "WorldcupCandidate 후보 풀 큐레이션")
@RestController
@RequestMapping("/api/v1/admin/worldcup-candidates")
@RequiredArgsConstructor
@Slf4j
public class AdminWorldcupCandidateController {

    private final AdminWorldcupCandidateService adminWorldcupCandidateService;

    /**
     * 월드컵 후보 신규 등록용 영화 검색.
     *
     * <p>recommend 검색 API를 우선 호출하여 ES 검색을 사용하고,
     * recommend 장애/미설정 시에는 backend MySQL 검색으로 폴백한다.</p>
     */
    @Operation(
            summary = "영화 검색 (월드컵 후보 등록용)",
            description = "제목 키워드와 popularity 범위로 영화를 검색합니다. 월드컵 후보 신규 등록 화면에서 여러 영화를 선택할 때 사용합니다."
    )
    @GetMapping("/movies/search")
    public ResponseEntity<ApiResponse<Page<MovieSearchResult>>> searchMovies(
            @Parameter(description = "검색 키워드 (한국어 또는 영어 제목)", example = "인터스텔라")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "최소 인기도 (TMDB popularity_score 기준)", example = "10")
            @RequestParam(required = false) Double popularityMin,
            @Parameter(description = "최대 인기도 (TMDB popularity_score 기준)", example = "20")
            @RequestParam(required = false) Double popularityMax,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 50)", example = "50")
            @RequestParam(defaultValue = "50") int size
    ) {
        log.debug("[관리자] 월드컵 후보 영화 검색 — keyword={}, popularityMin={}, popularityMax={}, page={}, size={}",
                keyword, popularityMin, popularityMax, page, size);
        return ResponseEntity.ok(ApiResponse.ok(
                adminWorldcupCandidateService.searchMovies(keyword, popularityMin, popularityMax, page, size)
        ));
    }

    /** 후보 목록 조회 */
    @Operation(
            summary = "월드컵 후보 목록 조회",
            description = "category 파라미터(category_code)로 필터링. 생략 시 전체. createdAt DESC 정렬."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CandidateResponse>>> getCandidates(
            @Parameter(description = "카테고리 코드 필터 (DEFAULT/ACTION/... 생략 시 전체)")
            @RequestParam(required = false) String category,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CandidateResponse> result = adminWorldcupCandidateService.getCandidates(category, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 후보 단건 조회 */
    @Operation(summary = "월드컵 후보 단건 조회", description = "PK로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CandidateResponse>> getCandidate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminWorldcupCandidateService.getCandidate(id)));
    }

    /** 신규 후보 등록 */
    @Operation(
            summary = "월드컵 후보 신규 등록",
            description = "(movieId, category_id) UNIQUE — 중복 시 409. category는 category_code를 의미하며 생략 시 DEFAULT를 사용합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CandidateResponse>> createCandidate(
            @Valid @RequestBody CreateRequest request
    ) {
        log.info("[관리자] 월드컵 후보 등록 요청 — movieId={}, categoryCode={}",
                request.movieId(), request.category());
        CandidateResponse created = adminWorldcupCandidateService.createCandidate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 후보 메타 수정 */
    @Operation(summary = "월드컵 후보 수정", description = "movies.popularity_score 재동기화 + isActive 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateCandidate(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        log.info("[관리자] 월드컵 후보 수정 요청 — id={}", id);
        return ResponseEntity.ok(ApiResponse.ok(adminWorldcupCandidateService.updateCandidate(id, request)));
    }

    /** 활성화 토글 */
    @Operation(
            summary = "월드컵 후보 활성/비활성 토글",
            description = "is_active 변경. 비활성화 시 월드컵 후보 풀에서 제외."
    )
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateActiveStatus(
            @PathVariable Long id,
            @RequestBody UpdateActiveRequest request
    ) {
        log.info("[관리자] 월드컵 후보 활성 토글 요청 — id={}, isActive={}", id, request.isActive());
        return ResponseEntity.ok(ApiResponse.ok(
                adminWorldcupCandidateService.updateActiveStatus(id, request)));
    }

    /**
     * 인기도 임계값 미만 일괄 비활성화.
     *
     * <p>예: threshold=5.0 → movies.popularity_score &lt; 5.0 인 모든 활성 후보를 isActive=false로 전환.</p>
     */
    @Operation(
            summary = "인기 없는 후보 일괄 비활성화",
            description = "movies.popularity_score < threshold 인 모든 활성 후보를 isActive=false로 일괄 전환"
    )
    @PostMapping("/deactivate-below")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> deactivateBelow(
            @Valid @RequestBody DeactivateBelowRequest request
    ) {
        log.info("[관리자] 월드컵 후보 일괄 비활성화 요청 — threshold={}", request.threshold());
        return ResponseEntity.ok(ApiResponse.ok(
                adminWorldcupCandidateService.deactivateBelowPopularity(request)));
    }

    /** 후보 hard delete */
    @Operation(summary = "월드컵 후보 삭제", description = "hard delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id) {
        log.warn("[관리자] 월드컵 후보 삭제 요청 — id={}", id);
        adminWorldcupCandidateService.deleteCandidate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
