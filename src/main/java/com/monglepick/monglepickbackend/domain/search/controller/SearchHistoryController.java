package com.monglepick.monglepickbackend.domain.search.controller;

import com.monglepick.monglepickbackend.domain.search.service.SearchHistoryService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 검색 이력 컨트롤러 — 최근 검색어 조회/삭제 REST API 엔드포인트.
 *
 * <p>사용자의 검색 키워드 이력을 조회하고 삭제하는 API를 제공한다.
 * 모든 엔드포인트는 JWT 인증이 필요하며, 본인의 검색 이력만 접근 가능하다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET  /api/v1/search/history — 최근 검색어 목록 조회 (최대 20개, 최신순)</li>
 *   <li>DELETE /api/v1/search/history/{id} — 특정 검색어 삭제</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <ul>
 *   <li>JWT Bearer 토큰 필수</li>
 * </ul>
 */
@Tag(name = "검색 이력", description = "최근 검색어 목록 조회 및 삭제")
@RestController
@RequestMapping("/api/v1/search")
@Slf4j
@RequiredArgsConstructor
public class SearchHistoryController extends BaseController {

    /** 검색 이력 서비스 (조회/UPSERT/삭제 비즈니스 로직) */
    private final SearchHistoryService searchHistoryService;

    /**
     * 사용자의 최근 검색어 목록을 반환한다.
     *
     * <p>searchedAt 내림차순으로 최대 20개의 키워드 문자열 목록을 반환한다.
     * 클라이언트 검색창의 "최근 검색어" 드롭다운에 표시하기 위해 사용된다.</p>
     *
     * @param principal 인증된 사용자 정보 (JWT에서 자동 추출)
     * @return 최근 검색 키워드 문자열 목록 (최대 20개, 최신순)
     */
    @Operation(
            summary = "최근 검색어 목록 조회",
            description = "로그인한 사용자의 최근 검색어를 최대 20개, 최신순으로 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최근 검색어 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (JWT 토큰 없음 또는 만료)")
    })
    @GetMapping("/history")
    public ResponseEntity<List<String>> getRecentSearches(Principal principal) {
        // JWT Principal에서 userId 추출 (BaseController 공통 메서드)
        String userId = resolveUserId(principal);

        log.debug("최근 검색어 조회 요청: userId={}", userId);

        List<String> keywords = searchHistoryService.getRecentSearches(userId);
        return ResponseEntity.ok(keywords);
    }

    /**
     * 특정 검색 이력을 삭제한다.
     *
     * <p>본인 소유 검색 이력만 삭제할 수 있다.
     * 이미 삭제됐거나 타인 소유인 경우에도 204 No Content를 반환한다
     * (클라이언트 UX 상 멱등하게 처리).</p>
     *
     * @param id        삭제할 검색 이력 ID (URL 경로 파라미터)
     * @param principal 인증된 사용자 정보 (JWT에서 자동 추출)
     * @return 204 No Content
     */
    @Operation(
            summary = "검색어 삭제",
            description = "특정 검색 이력을 삭제합니다. 본인 소유 이력만 삭제 가능하며, " +
                    "존재하지 않는 ID도 204로 응답합니다 (멱등 처리).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "검색어 삭제 성공 (또는 이미 존재하지 않음)"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (JWT 토큰 없음 또는 만료)")
    })
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteSearchHistory(
            @Parameter(description = "삭제할 검색 이력 ID", required = true, example = "10")
            @PathVariable Long id,

            Principal principal
    ) {
        // JWT Principal에서 userId 추출 (BaseController 공통 메서드)
        String userId = resolveUserId(principal);

        log.info("검색 이력 삭제 요청: userId={}, searchHistoryId={}", userId, id);

        searchHistoryService.deleteSearchHistory(userId, id);
        return ResponseEntity.noContent().build();
    }
}
