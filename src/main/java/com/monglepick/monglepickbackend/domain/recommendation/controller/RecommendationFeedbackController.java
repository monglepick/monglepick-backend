package com.monglepick.monglepickbackend.domain.recommendation.controller;

import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationFeedbackRequest;
import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationFeedbackResponse;
import com.monglepick.monglepickbackend.domain.recommendation.service.RecommendationFeedbackService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 추천 피드백 컨트롤러 — 추천 결과 피드백 REST API 엔드포인트.
 *
 * <p>사용자가 AI 추천 결과(좋아요/싫어요/시청/관심없음)에 대한 피드백을 제출하는 API이다.
 * 동일 추천에 대해 재제출하면 기존 피드백이 업데이트된다 (UPSERT 방식).</p>
 *
 * <h3>인증</h3>
 * <ul>
 *   <li>JWT Bearer 토큰 필수 (로그인한 사용자만 피드백 제출 가능)</li>
 * </ul>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>POST /api/v1/recommendations/{recommendationLogId}/feedback — 피드백 제출 (UPSERT)</li>
 * </ul>
 */
@Tag(name = "추천 피드백", description = "AI 추천 결과에 대한 사용자 피드백 제출 (좋아요/싫어요/시청/관심없음)")
@RestController
@RequestMapping("/api/v1/recommendations")
@Slf4j
@RequiredArgsConstructor
public class RecommendationFeedbackController extends BaseController {

    /** 추천 피드백 서비스 (UPSERT 비즈니스 로직) */
    private final RecommendationFeedbackService recommendationFeedbackService;

    /**
     * 추천 피드백을 제출한다 (UPSERT).
     *
     * <p>처음 제출하면 새 피드백을 생성(201 Created)하고,
     * 동일 추천에 이미 피드백이 있으면 기존 피드백을 업데이트(200 OK)한다.
     * 두 경우 모두 저장된 피드백 정보를 응답 바디에 포함한다.</p>
     *
     * <h3>피드백 유형</h3>
     * <ul>
     *   <li>{@code like} — 추천이 마음에 들었음</li>
     *   <li>{@code dislike} — 추천이 마음에 들지 않았음</li>
     *   <li>{@code watched} — 추천 영화를 실제로 시청함</li>
     *   <li>{@code not_interested} — 해당 영화에 관심 없음</li>
     * </ul>
     *
     * @param recommendationLogId 피드백 대상 추천 로그 ID (URL 경로 파라미터)
     * @param request             피드백 유형 및 코멘트 요청 바디
     * @param principal           인증된 사용자 정보 (JWT에서 자동 추출)
     * @return 저장된 피드백 응답 DTO (201 Created)
     */
    @Operation(
            summary = "추천 피드백 제출",
            description = "AI 추천 결과에 대한 피드백을 제출합니다. " +
                    "동일 추천에 이미 피드백이 존재하면 기존 피드백을 업데이트합니다 (UPSERT). " +
                    "feedbackType: like(좋아요), dislike(싫어요), watched(시청함), not_interested(관심없음)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "피드백 제출 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 (feedbackType 누락 또는 잘못된 값)"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (JWT 토큰 없음 또는 만료)"),
            @ApiResponse(responseCode = "500", description = "추천 로그를 찾을 수 없음")
    })
    @PostMapping("/{recommendationLogId}/feedback")
    public ResponseEntity<RecommendationFeedbackResponse> submitFeedback(
            @Parameter(description = "피드백 대상 추천 로그 ID", required = true, example = "1")
            @PathVariable Long recommendationLogId,

            @RequestBody @Valid RecommendationFeedbackRequest request,

            Principal principal
    ) {
        // JWT Principal에서 userId 추출 (BaseController 공통 메서드)
        String userId = resolveUserId(principal);

        log.info("추천 피드백 제출 요청: userId={}, recommendationLogId={}, feedbackType={}",
                userId, recommendationLogId, request.feedbackType());

        RecommendationFeedbackResponse response =
                recommendationFeedbackService.submitFeedback(userId, recommendationLogId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
