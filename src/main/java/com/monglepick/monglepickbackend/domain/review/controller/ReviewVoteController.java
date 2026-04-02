package com.monglepick.monglepickbackend.domain.review.controller;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewVoteResponse;
import com.monglepick.monglepickbackend.domain.review.service.ReviewVoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 투표 컨트롤러 — "도움이 됐어요" 투표 처리 및 현황 조회 API.
 *
 * <p>사용자가 특정 리뷰에 대해 "도움이 됐어요(true) / 도움이 안 됐어요(false)"를
 * 투표하거나 현재 투표 현황을 조회할 수 있는 REST API를 제공한다.
 * 모든 엔드포인트는 JWT 인증이 필요하다.</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <ul>
 *   <li>{@code POST /api/v1/reviews/{reviewId}/vote} — 투표 등록 또는 변경</li>
 *   <li>{@code GET  /api/v1/reviews/{reviewId}/vote} — 투표 현황 + 내 투표 상태 조회</li>
 * </ul>
 *
 * <h3>요청 본문 (POST)</h3>
 * <pre>{@code
 * { "helpful": true }   // 도움이 됐어요
 * { "helpful": false }  // 도움이 안 됐어요
 * }</pre>
 */
@Tag(name = "리뷰 투표", description = "리뷰 도움됨/도움안됨 투표 및 현황 조회")
@Slf4j
@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/vote")
@RequiredArgsConstructor
public class ReviewVoteController {

    private final ReviewVoteService reviewVoteService;

    /**
     * 리뷰 투표 등록/변경 API.
     *
     * <p>해당 리뷰에 처음 투표하면 신규 레코드가 생성되고,
     * 이미 투표한 경우 기존 투표 값이 갱신된다.
     * 처리 후 최신 집계 결과와 현재 사용자의 투표 상태를 반환한다.</p>
     *
     * @param reviewId 투표 대상 리뷰 ID
     * @param request  투표 요청 본문 ({@code {"helpful": true/false}})
     * @param userId   JWT에서 추출한 사용자 ID
     * @return 200 OK + 최신 투표 집계 및 내 투표 상태
     */
    @Operation(
            summary = "리뷰 투표",
            description = "리뷰에 '도움이 됐어요(true)' 또는 '도움이 안 됐어요(false)'를 투표합니다. "
                    + "기존 투표가 있으면 값이 갱신됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "투표 처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public ResponseEntity<ReviewVoteResponse> vote(
            @PathVariable Long reviewId,
            @RequestBody VoteRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 투표 요청 - userId: {}, reviewId: {}, helpful: {}",
                userId, reviewId, request.helpful());
        ReviewVoteResponse response = reviewVoteService.vote(userId, reviewId, request.helpful());
        return ResponseEntity.ok(response);
    }

    /**
     * 리뷰 투표 현황 조회 API.
     *
     * <p>"도움이 됐어요" / "도움이 안 됐어요" 수와 현재 사용자의 투표 상태를 반환한다.
     * 현재 사용자가 투표하지 않은 경우 {@code myVote}는 {@code null}이다.</p>
     *
     * @param reviewId 조회 대상 리뷰 ID
     * @param userId   JWT에서 추출한 사용자 ID
     * @return 200 OK + 투표 집계 및 내 투표 상태
     */
    @Operation(
            summary = "리뷰 투표 현황 조회",
            description = "리뷰의 도움됨/도움안됨 투표 수와 현재 사용자의 투표 상태를 반환합니다. "
                    + "미투표 시 myVote는 null입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "투표 현황 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public ResponseEntity<ReviewVoteResponse> getVoteCounts(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String userId) {

        ReviewVoteResponse response = reviewVoteService.getVoteCounts(reviewId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 투표 요청 본문 DTO.
     *
     * <p>{@code POST /api/v1/reviews/{reviewId}/vote} 요청의 body를 바인딩한다.</p>
     *
     * @param helpful true=도움이 됐어요, false=도움이 안 됐어요
     */
    public record VoteRequest(boolean helpful) {}
}
