package com.monglepick.monglepickbackend.domain.recommendation.controller;

import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationHistoryDto;
import com.monglepick.monglepickbackend.domain.recommendation.service.RecommendationHistoryService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 추천 이력 REST API 컨트롤러.
 *
 * <p>클라이언트 추천 이력 탭에서 호출하는 3개 엔드포인트를 제공한다.
 * 기존 {@link RecommendationFeedbackController}와 동일한 {@code /api/v1/recommendations} 베이스 경로를
 * 공유하되, 목록 조회 및 찜/봤어요 토글 엔드포인트를 신규로 추가한다.</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <ul>
 *   <li>GET  /api/v1/recommendations              — 추천 이력 목록 조회 (페이징)</li>
 *   <li>POST /api/v1/recommendations/{id}/wishlist — 찜 토글</li>
 *   <li>POST /api/v1/recommendations/{id}/watched  — 봤어요 토글</li>
 * </ul>
 *
 * <h3>기존 엔드포인트와의 관계</h3>
 * <p>{@code POST /api/v1/recommendations/{id}/feedback}는
 * {@link RecommendationFeedbackController}가 담당하며 이 컨트롤러와 경로가 겹치지 않는다.</p>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 JWT Bearer 토큰 인증 필수.</p>
 */
@Tag(name = "추천 이력", description = "사용자 AI 추천 이력 조회 및 찜/봤어요 토글 API")
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationHistoryController extends BaseController {

    /** 추천 이력 비즈니스 로직 서비스 */
    private final RecommendationHistoryService recommendationHistoryService;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 사용자의 AI 추천 이력 목록을 페이징 조회한다.
     *
     * <p>로그인한 사용자가 AI 챗봇으로부터 받은 영화 추천 이력을 최신 순으로 반환한다.
     * 각 항목에는 영화 정보(제목, 포스터, 장르), 추천 이유, 찜/봤어요 상태가 포함된다.</p>
     *
     * @param page      페이지 번호 (0-based, 기본값 0)
     * @param size      페이지 크기 (기본값 20, 최대 100)
     * @param principal JWT 인증 정보
     * @return 추천 이력 응답 DTO 페이지
     */
    @Operation(
            summary = "추천 이력 목록 조회",
            description = "로그인한 사용자의 AI 추천 이력을 최신 순으로 페이징 조회합니다. " +
                    "각 항목에는 영화 정보, 추천 이유, 찜/봤어요 상태가 포함됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ResponseEntity<Page<RecommendationHistoryDto.RecommendationHistoryResponse>> getRecommendationHistory(
            @Parameter(description = "페이지 번호 (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,

            // QA 후속 (2026-04-23): 찜한영화/본영화 필터 미적용 버그 수정.
            // Client 는 `?status=WISHLIST|WATCHED` 쿼리를 보내왔으나 기존엔 이 파라미터를 아예 받지 않아
            // Service 가 항상 전체 목록을 반환 → 세 탭 결과가 동일.
            // null/"ALL" 은 필터 없음(기존 동작 호환), WISHLIST/WATCHED 는 Impact 테이블 JOIN 으로 필터링.
            @Parameter(description = "필터 상태 (ALL/WISHLIST/WATCHED)", example = "WISHLIST")
            @RequestParam(required = false) String status,

            Principal principal
    ) {
        String userId = resolveUserId(principal);

        // 페이지 크기 상한 제한 (DoS 방지)
        int limitedSize = limitPageSize(size);

        // 최신 추천 순(createdAt DESC) 페이징
        Pageable pageable = PageRequest.of(page, limitedSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        log.debug("추천 이력 목록 조회 요청: userId={}, page={}, size={}, status={}",
                userId, page, limitedSize, status);

        // 2026-04-16 반환 타입 bare 화 (ApiResponse 래핑 제거).
        // 프로젝트 컨벤션: 페이징 리스트 엔드포인트는 ApiResponse 래핑 없이 Page 직접 반환.
        //   - PointController.getHistory / PostController.getSharedPlaylistPosts 참조
        // 이전(ApiResponse<Page<..>>) 은 Client 의 axios 인터셉터가 response.data 1회만 언래핑하므로
        // 최종 data={success, data:{content}, error} 형태가 되어 RecommendationPage `data?.content`
        // 접근이 undefined 로 귀결 → 실제 48건 저장되어 있어도 빈 배열로 렌더링되던 문제.
        Page<RecommendationHistoryDto.RecommendationHistoryResponse> result =
                recommendationHistoryService.getRecommendationHistory(userId, status, pageable);

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────
    // 찜 / 봤어요 토글
    // ─────────────────────────────────────────────

    /**
     * 추천 이력 항목의 찜 상태를 토글한다.
     *
     * <p>현재 찜 상태의 반대로 전환한다 (false→true: 찜 추가, true→false: 찜 취소).
     * RecommendationImpact 레코드를 업서트하여 {@code wishlisted} 필드를 갱신한다.</p>
     *
     * @param recommendationLogId 찜 토글 대상 추천 로그 ID
     * @param principal           JWT 인증 정보
     * @return 토글 후 찜 상태 ({@code wishlisted: boolean})
     */
    @Operation(
            summary = "추천 이력 찜 토글",
            description = "추천 이력 항목의 찜 상태를 토글합니다. " +
                    "현재 false이면 true(찜 추가), true이면 false(찜 취소)로 전환됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토글 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "추천 이력 없음 또는 본인 이력 아님")
    })
    @PostMapping("/{recommendationLogId}/wishlist")
    public ResponseEntity<RecommendationHistoryDto.WishlistToggleResponse> toggleWishlist(
            @Parameter(description = "찜 토글 대상 추천 로그 ID", required = true, example = "42")
            @PathVariable Long recommendationLogId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("추천 이력 찜 토글 요청: recommendationLogId={}, userId={}", recommendationLogId, userId);

        // 2026-04-16 반환 타입 bare 화 (목록 EP 와 동일 사유 — Client `result?.wishlisted` 접근 정상화)
        RecommendationHistoryDto.WishlistToggleResponse result =
                recommendationHistoryService.toggleWishlist(recommendationLogId, userId);

        return ResponseEntity.ok(result);
    }

    /**
     * 추천 이력 항목의 봤어요 상태를 토글한다.
     *
     * <p>현재 봤어요 상태의 반대로 전환한다 (false→true: 봤어요 추가, true→false: 봤어요 취소).
     * RecommendationImpact 레코드를 업서트하여 {@code watched} 필드를 갱신한다.</p>
     *
     * @param recommendationLogId 봤어요 토글 대상 추천 로그 ID
     * @param principal           JWT 인증 정보
     * @return 토글 후 봤어요 상태 ({@code watched: boolean})
     */
    @Operation(
            summary = "추천 이력 봤어요 토글",
            description = "추천 이력 항목의 봤어요 상태를 토글합니다. " +
                    "현재 false이면 true(봤어요 추가), true이면 false(봤어요 취소)로 전환됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토글 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "추천 이력 없음 또는 본인 이력 아님")
    })
    @PostMapping("/{recommendationLogId}/watched")
    public ResponseEntity<RecommendationHistoryDto.WatchedToggleResponse> toggleWatched(
            @Parameter(description = "봤어요 토글 대상 추천 로그 ID", required = true, example = "42")
            @PathVariable Long recommendationLogId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("추천 이력 봤어요 토글 요청: recommendationLogId={}, userId={}", recommendationLogId, userId);

        // 2026-04-16 반환 타입 bare 화 (목록 EP 와 동일 사유 — Client `result?.watched` 접근 정상화)
        RecommendationHistoryDto.WatchedToggleResponse result =
                recommendationHistoryService.toggleWatched(recommendationLogId, userId);

        return ResponseEntity.ok(result);
    }
}
