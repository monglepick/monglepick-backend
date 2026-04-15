package com.monglepick.monglepickbackend.domain.recommendation.controller;

import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationLogBatchDto.SaveBatchRequest;
import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationLogBatchDto.SaveBatchResponse;
import com.monglepick.monglepickbackend.domain.recommendation.service.RecommendationLogService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 추천 로그 내부 API 컨트롤러 — Agent 전용 (ServiceKey 인증, 2026-04-15 신규).
 *
 * <p>AI Agent 가 {@code recommendation_ranker} 완료 후 {@code movie_card} SSE 를
 * 발행하는 시점에 추천된 영화 N 개를 한 번에 {@code recommendation_log} 테이블에
 * INSERT 한다. 본 엔드포인트가 생기기 전에는 저장 경로가 전혀 없어서 마이픽 추천
 * 내역 + 관리자 AI 추천 분석 탭이 모두 빈 화면이었다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code POST /api/v1/recommendations/internal/batch} — 배치 INSERT</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.domain.chat.controller.ChatInternalController
 *   동일한 ServiceKey 인증 패턴
 */
@Tag(name = "추천 내부 API", description = "Agent 전용 추천 로그 저장 (ServiceKey 인증)")
@RestController
@RequestMapping("/api/v1/recommendations/internal")
@RequiredArgsConstructor
public class RecommendationInternalController extends BaseController {

    private final RecommendationLogService recommendationLogService;

    /**
     * 추천 로그 배치 저장 — Agent 가 추천 완료 시점에 1 회 호출.
     *
     * <p>요청 items 순서를 보존한 PK 리스트를 반환한다. movieId 가 {@code movies} 테이블에
     * 없는 경우 해당 자리는 null 로 채워진다 (graceful).</p>
     */
    @Operation(
            summary = "추천 로그 배치 저장",
            description = "Agent 가 recommendation_ranker 완료 후 movie_card 발행 직전에 호출. " +
                    "영화 N 개를 한 번의 요청으로 recommendation_log 테이블에 기록한다."
    )
    @SecurityRequirement(name = "ServiceKey")
    @PostMapping("/batch")
    public ResponseEntity<SaveBatchResponse> saveBatch(
            Principal principal,
            @Valid @RequestBody SaveBatchRequest request) {
        // ServiceKey 인증 → body 의 userId 신뢰. 비 ServiceKey 인증 시 principal 의 userId 사용
        resolveUserIdWithServiceKey(principal, request.userId());
        SaveBatchResponse response = recommendationLogService.saveBatch(request);
        return ResponseEntity.ok(response);
    }
}
