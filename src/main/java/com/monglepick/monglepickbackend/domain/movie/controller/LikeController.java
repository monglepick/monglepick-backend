package com.monglepick.monglepickbackend.domain.movie.controller;

import com.monglepick.monglepickbackend.domain.movie.dto.LikeResponse;
import com.monglepick.monglepickbackend.domain.movie.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영화 좋아요 컨트롤러.
 *
 * <p>영화 좋아요 토글, 상태 조회, 수 조회 API를 제공한다.</p>
 *
 * <h3>인증 정책</h3>
 * <ul>
 *   <li>POST /api/v1/movies/{movieId}/like — JWT 필수 (좋아요 토글)</li>
 *   <li>GET  /api/v1/movies/{movieId}/like — JWT 필수 (내 좋아요 상태 확인)</li>
 *   <li>GET  /api/v1/movies/{movieId}/like/count — 공개 (비로그인도 접근 가능)</li>
 * </ul>
 *
 * <h3>엔드포인트</h3>
 * <pre>
 * POST /api/v1/movies/{movieId}/like        → toggleLike
 * GET  /api/v1/movies/{movieId}/like        → isLiked
 * GET  /api/v1/movies/{movieId}/like/count  → getLikeCount
 * </pre>
 */
@Tag(name = "영화 좋아요", description = "영화 좋아요 토글, 상태 조회, 수 조회 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/movies/{movieId}/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /**
     * 영화 좋아요 토글 API.
     *
     * <p>현재 좋아요 상태에 따라 자동으로 등록/취소/복구를 처리한다.</p>
     *
     * <ul>
     *   <li>좋아요 없음 → 등록 (liked=true)</li>
     *   <li>좋아요 활성 → 취소 (liked=false)</li>
     *   <li>좋아요 취소됨 → 복구 (liked=true)</li>
     * </ul>
     *
     * @param movieId     경로 변수: 대상 영화 ID
     * @param userDetails JWT 인증 정보 (Principal)
     * @return 200 OK + 변경 후 좋아요 상태 및 전체 좋아요 수
     */
    @Operation(
            summary = "영화 좋아요 토글",
            description = "현재 좋아요 상태에 따라 자동으로 등록/취소/복구를 수행한다. JWT 인증 필수.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 토글 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (JWT 없음 또는 만료)")
    })
    @PostMapping
    public ResponseEntity<LikeResponse> toggleLike(
            @Parameter(description = "영화 ID", required = true)
            @PathVariable String movieId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userId = userDetails.getUsername();
        log.debug("좋아요 토글 요청 - userId: {}, movieId: {}", userId, movieId);
        LikeResponse response = likeService.toggleLike(userId, movieId);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 사용자의 영화 좋아요 상태 조회 API.
     *
     * <p>로그인한 사용자가 해당 영화에 활성 좋아요를 눌렀는지 확인한다.
     * 클라이언트 영화 상세 페이지 초기 렌더링 시 하트 버튼 활성화 여부 결정에 사용된다.</p>
     *
     * @param movieId     경로 변수: 대상 영화 ID
     * @param userDetails JWT 인증 정보 (Principal)
     * @return 200 OK + 좋아요 상태 및 전체 좋아요 수
     */
    @Operation(
            summary = "내 영화 좋아요 상태 조회",
            description = "로그인한 사용자의 해당 영화 활성 좋아요 여부를 반환한다. JWT 인증 필수.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 상태 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (JWT 없음 또는 만료)")
    })
    @GetMapping
    public ResponseEntity<LikeResponse> isLiked(
            @Parameter(description = "영화 ID", required = true)
            @PathVariable String movieId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userId = userDetails.getUsername();
        log.debug("좋아요 상태 조회 - userId: {}, movieId: {}", userId, movieId);
        boolean liked = likeService.isLiked(userId, movieId);
        long likeCount = likeService.getLikeCount(movieId);
        return ResponseEntity.ok(LikeResponse.of(liked, likeCount));
    }

    /**
     * 영화 좋아요 수 조회 API (공개).
     *
     * <p>비로그인 사용자도 접근 가능한 공개 엔드포인트다.
     * 영화 목록 카드, 상세 페이지 등에서 전체 좋아요 수를 표시할 때 사용한다.</p>
     *
     * <p>SecurityConfig에서 {@code GET /api/v1/movies/**} 패턴을 permitAll로 설정해야 한다.</p>
     *
     * @param movieId 경로 변수: 대상 영화 ID
     * @return 200 OK + liked=false(고정) + 전체 활성 좋아요 수
     */
    @Operation(
            summary = "영화 좋아요 수 조회",
            description = "해당 영화의 전체 활성 좋아요 수를 반환한다. 비로그인 사용자도 접근 가능."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 수 조회 성공")
    })
    @SecurityRequirement(name = "")  // 공개 엔드포인트 — Swagger UI 자물쇠 숨김
    @GetMapping("/count")
    public ResponseEntity<LikeResponse> getLikeCount(
            @Parameter(description = "영화 ID", required = true)
            @PathVariable String movieId
    ) {
        log.debug("좋아요 수 조회 - movieId: {}", movieId);
        long likeCount = likeService.getLikeCount(movieId);
        // 비로그인 공개 엔드포인트이므로 liked는 항상 false 반환
        return ResponseEntity.ok(LikeResponse.of(false, likeCount));
    }
}
