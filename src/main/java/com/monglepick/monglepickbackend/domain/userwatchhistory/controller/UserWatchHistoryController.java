package com.monglepick.monglepickbackend.domain.userwatchhistory.controller;

import com.monglepick.monglepickbackend.domain.userwatchhistory.dto.UserWatchHistoryRequest;
import com.monglepick.monglepickbackend.domain.userwatchhistory.dto.UserWatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.userwatchhistory.service.UserWatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 실 유저 시청 이력 컨트롤러.
 *
 * <p>{@code /api/v1/watch-history} 독립 경로에서 시청 기록의 추가·조회·삭제·재관람 카운트를 전담한다.
 * 마이페이지 통합 경로({@code /api/v1/users/me/watch-history})는 {@code UserController} 가 별도로 제공한다.</p>
 *
 * <h3>경로 분리 이유</h3>
 * <ul>
 *   <li>{@code /users/me/watch-history} (마이페이지) — 프로필과 묶어 한 번에 렌더링하는 통합 진입점</li>
 *   <li>{@code /watch-history} (독립 경로) — 기록 추가/삭제/재관람 카운트 등 운영 액션 전용</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 JWT Bearer 인증 필수이다.
 * SecurityConfig 의 기본 정책(authenticated)에 의해 자동 보호된다.</p>
 */
@Tag(name = "시청 이력", description = "실 유저 시청 기록 추가·조회·삭제·재관람 카운트 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/watch-history")
@RequiredArgsConstructor
public class UserWatchHistoryController {

    private final UserWatchHistoryService userWatchHistoryService;

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/watch-history — 시청 기록 추가
    // ════════════════════════════════════════════════════════════════

    /**
     * 시청 기록 추가 API.
     *
     * <p>사용자가 영화를 시청한 기록을 저장한다.
     * 동일한 영화를 여러 번 시청한 경우 중복 기록이 허용되며, 재관람 횟수 추적에 활용된다.
     * {@code watchedAt} 을 생략하면 서버 수신 시각이 자동으로 설정된다.</p>
     *
     * <h3>요청 예시</h3>
     * <pre>{@code
     * POST /api/v1/watch-history
     * Authorization: Bearer {accessToken}
     * {
     *   "movieId": "tt1375666",
     *   "watchedAt": "2026-04-08T21:00:00",   // 생략 가능
     *   "rating": 4.5,                          // 생략 가능
     *   "watchSource": "recommendation",        // 생략 가능
     *   "watchDurationSeconds": 8400,           // 생략 가능
     *   "completionStatus": "COMPLETED"         // 생략 가능
     * }
     * }</pre>
     *
     * @param userId  JWT 에서 추출한 사용자 ID
     * @param request 시청 기록 요청 DTO
     * @return 201 Created + 저장된 시청 이력 응답 DTO
     */
    @Operation(
            summary = "시청 기록 추가",
            description = "사용자가 영화를 시청한 기록을 저장합니다. 중복 허용(재관람 카운트). " +
                    "watchedAt 생략 시 현재 시각, rating 생략 시 미부여로 처리됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "시청 기록 추가 성공"),
            @ApiResponse(responseCode = "400", description = "요청 유효성 검증 실패 (movieId 누락, rating 범위 오류 등)"),
            @ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public ResponseEntity<UserWatchHistoryResponse> addWatchHistory(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UserWatchHistoryRequest request) {

        log.info("시청 기록 추가 요청 - userId: {}, movieId: {}", userId, request.movieId());
        UserWatchHistoryResponse response = userWatchHistoryService.addWatchHistory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/watch-history — 내 시청 이력 목록 (페이징)
    // ════════════════════════════════════════════════════════════════

    /**
     * 내 시청 이력 목록 조회 API.
     *
     * <p>로그인한 사용자의 시청 이력을 최신순으로 페이징 조회한다.</p>
     *
     * <h3>페이징 파라미터 (쿼리스트링)</h3>
     * <ul>
     *   <li>{@code page} — 페이지 번호 (0 부터 시작, 기본값: 0)</li>
     *   <li>{@code size} — 페이지 크기 (기본값: 20)</li>
     *   <li>{@code sort} — 정렬 기준 (기본값: watchedAt,desc)</li>
     * </ul>
     *
     * <h3>요청 예시</h3>
     * <pre>{@code
     * GET /api/v1/watch-history?page=0&size=20&sort=watchedAt,desc
     * Authorization: Bearer {accessToken}
     * }</pre>
     *
     * @param userId   JWT 에서 추출한 사용자 ID
     * @param pageable 페이징/정렬 정보 (기본: 20 건, watchedAt 역순)
     * @return 200 OK + 페이지 단위의 시청 이력 응답
     */
    @Operation(
            summary = "내 시청 이력 목록 조회",
            description = "로그인한 사용자의 시청 이력을 최신순으로 페이징 조회합니다. " +
                    "page/size/sort 쿼리 파라미터로 제어합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "시청 이력 조회 성공"),
            @ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public ResponseEntity<Page<UserWatchHistoryResponse>> getWatchHistory(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20, sort = "watchedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("시청 이력 목록 조회 - userId: {}, page: {}", userId, pageable.getPageNumber());
        Page<UserWatchHistoryResponse> history = userWatchHistoryService.getWatchHistory(userId, pageable);
        return ResponseEntity.ok(history);
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/watch-history/movies/{movieId}/count — 재관람 카운트
    // ════════════════════════════════════════════════════════════════

    /**
     * 특정 영화의 재관람 횟수 조회 API.
     *
     * <p>로그인한 사용자가 특정 영화를 몇 번 시청했는지 카운트를 반환한다.
     * 시청 기록이 없으면 0 을 반환한다. 영화 상세 화면의 "이 영화를 N 번 봤어요" 표시 등에 사용한다.</p>
     *
     * <h3>요청 예시</h3>
     * <pre>{@code
     * GET /api/v1/watch-history/movies/tt1375666/count
     * Authorization: Bearer {accessToken}
     *
     * Response 200:
     * { "movieId": "tt1375666", "count": 3 }
     * }</pre>
     *
     * @param userId  JWT 에서 추출한 사용자 ID
     * @param movieId 카운트 대상 영화 ID
     * @return 200 OK + {@code {"movieId": "...", "count": N}}
     */
    @Operation(
            summary = "재관람 횟수 조회",
            description = "로그인한 사용자가 특정 영화를 몇 번 시청했는지 카운트를 반환합니다. " +
                    "시청 기록이 없으면 0 을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재관람 카운트 조회 성공"),
            @ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/movies/{movieId}/count")
    public ResponseEntity<Map<String, Object>> getRewatchCount(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "조회 대상 영화 ID", example = "tt1375666")
            @PathVariable String movieId) {

        long count = userWatchHistoryService.getRewatchCount(userId, movieId);
        return ResponseEntity.ok(Map.of(
                "movieId", movieId,
                "count", count
        ));
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/watch-history/{id} — 시청 기록 삭제
    // ════════════════════════════════════════════════════════════════

    /**
     * 시청 기록 삭제 API.
     *
     * <p>특정 시청 기록을 삭제한다. 본인의 기록만 삭제할 수 있으며,
     * 본인 소유가 아니거나 존재하지 않는 ID 는 동일하게 400 으로 응답하여
     * enumeration attack 을 방지한다.</p>
     *
     * <h3>요청 예시</h3>
     * <pre>{@code
     * DELETE /api/v1/watch-history/42
     * Authorization: Bearer {accessToken}
     * }</pre>
     *
     * @param userId             JWT 에서 추출한 사용자 ID
     * @param userWatchHistoryId 삭제할 시청 이력 PK
     * @return 204 No Content
     */
    @Operation(
            summary = "시청 기록 삭제",
            description = "특정 시청 기록을 삭제합니다. 본인의 기록만 삭제 가능하며, " +
                    "타인의 기록 또는 존재하지 않는 ID 는 동일하게 400 으로 거부됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "시청 기록 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "해당 기록 없음 또는 본인 소유 아님"),
            @ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWatchHistory(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "삭제할 시청 이력 ID", example = "42")
            @PathVariable("id") Long userWatchHistoryId) {

        log.info("시청 기록 삭제 요청 - userId: {}, id: {}", userId, userWatchHistoryId);
        userWatchHistoryService.deleteWatchHistory(userId, userWatchHistoryId);
        return ResponseEntity.noContent().build();
    }
}
