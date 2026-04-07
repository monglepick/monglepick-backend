package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.KeywordResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.UpdateExcludedRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.UpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminPopularSearchService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 인기 검색어(PopularSearchKeyword) 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 인기 검색어" 메뉴의 6개 엔드포인트를 제공한다.
 * 인기 검색어는 SearchHistory로부터 자동 집계되지만, 본 EP는 수동 강제 노출/제외용
 * 마스터 데이터를 관리한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/popular-keywords          — 키워드 목록 (페이징)</li>
 *   <li>GET    /api/v1/admin/popular-keywords/{id}     — 단건 조회</li>
 *   <li>POST   /api/v1/admin/popular-keywords          — 신규 등록 (keyword UNIQUE)</li>
 *   <li>PUT    /api/v1/admin/popular-keywords/{id}     — 메타 수정 (keyword 제외)</li>
 *   <li>PATCH  /api/v1/admin/popular-keywords/{id}/excluded — 블랙리스트 토글</li>
 *   <li>DELETE /api/v1/admin/popular-keywords/{id}     — 키워드 hard delete</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 인기 검색어", description = "PopularSearchKeyword 마스터 등록/수정/제외 토글")
@RestController
@RequestMapping("/api/v1/admin/popular-keywords")
@RequiredArgsConstructor
@Slf4j
public class AdminPopularSearchController {

    private final AdminPopularSearchService adminPopularSearchService;

    /** 키워드 목록 조회 (페이징) */
    @Operation(
            summary = "인기 검색어 목록 조회",
            description = "수동 우선순위 DESC, 생성일 DESC 정렬로 페이징 조회"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<KeywordResponse>>> getKeywords(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page, size,
                Sort.by(Sort.Order.desc("manualPriority"), Sort.Order.desc("createdAt"))
        );
        Page<KeywordResponse> result = adminPopularSearchService.getKeywords(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 단건 조회 */
    @Operation(summary = "인기 검색어 단건 조회", description = "PK로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KeywordResponse>> getKeyword(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.getKeyword(id)));
    }

    /** 신규 등록 */
    @Operation(
            summary = "인기 검색어 신규 등록",
            description = "키워드는 UNIQUE이므로 중복 시 409. isExcluded=true 등록 시 즉시 블랙리스트로 분류."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<KeywordResponse>> createKeyword(
            @Valid @RequestBody CreateRequest request
    ) {
        log.info("[관리자] 인기 검색어 등록 요청 — keyword={}, isExcluded={}",
                request.keyword(), request.isExcluded());
        KeywordResponse created = adminPopularSearchService.createKeyword(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 메타 수정 (keyword 제외) */
    @Operation(
            summary = "인기 검색어 수정",
            description = "displayRank/manualPriority/isExcluded/adminNote 일괄 수정"
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KeywordResponse>> updateKeyword(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        log.info("[관리자] 인기 검색어 수정 요청 — id={}", id);
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.updateKeyword(id, request)));
    }

    /** 블랙리스트 토글 */
    @Operation(
            summary = "인기 검색어 제외(블랙리스트) 토글",
            description = "is_excluded=true → 자동 집계 결과에서 제외. 부적절 키워드 차단용."
    )
    @PatchMapping("/{id}/excluded")
    public ResponseEntity<ApiResponse<KeywordResponse>> updateExcluded(
            @PathVariable Long id,
            @RequestBody UpdateExcludedRequest request
    ) {
        log.info("[관리자] 인기 검색어 제외 토글 — id={}, isExcluded={}", id, request.isExcluded());
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.updateExcluded(id, request)));
    }

    /** 삭제 */
    @Operation(summary = "인기 검색어 삭제 (hard delete)", description = "마스터 데이터 hard delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyword(@PathVariable Long id) {
        log.warn("[관리자] 인기 검색어 삭제 요청 — id={}", id);
        adminPopularSearchService.deleteKeyword(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
