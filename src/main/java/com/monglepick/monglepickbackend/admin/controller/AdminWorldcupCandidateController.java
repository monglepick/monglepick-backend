package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.BulkOperationResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CandidateResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.DeactivateBelowRequest;
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
 * <p>관리자 페이지 "운영 도구 → 월드컵 후보" 메뉴의 7개 엔드포인트를 제공한다.
 * 월드컵 후보 풀을 큐레이션하고, 인기 없는 영화를 일괄 제외한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/worldcup-candidates                — 후보 목록 (페이징 + 카테고리 필터)</li>
 *   <li>GET    /api/v1/admin/worldcup-candidates/{id}           — 단건 조회</li>
 *   <li>POST   /api/v1/admin/worldcup-candidates                — 신규 등록 ((movieId, category) UNIQUE)</li>
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

    /** 후보 목록 조회 */
    @Operation(
            summary = "월드컵 후보 목록 조회",
            description = "category 파라미터로 필터링. 생략 시 전체. createdAt DESC 정렬."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CandidateResponse>>> getCandidates(
            @Parameter(description = "카테고리 필터 (DEFAULT/ACTION/... 생략 시 전체)")
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
            description = "(movieId, category) UNIQUE — 중복 시 409. category 생략 시 'DEFAULT'."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CandidateResponse>> createCandidate(
            @Valid @RequestBody CreateRequest request
    ) {
        log.info("[관리자] 월드컵 후보 등록 요청 — movieId={}, category={}",
                request.movieId(), request.category());
        CandidateResponse created = adminWorldcupCandidateService.createCandidate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 후보 메타 수정 */
    @Operation(summary = "월드컵 후보 수정", description = "popularity/isActive/adminNote 일괄 수정")
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
     * <p>예: threshold=5.0 → popularity &lt; 5.0 인 모든 활성 후보를 isActive=false로 전환.</p>
     */
    @Operation(
            summary = "인기 없는 후보 일괄 비활성화",
            description = "popularity < threshold 인 모든 활성 후보를 isActive=false로 일괄 전환"
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
