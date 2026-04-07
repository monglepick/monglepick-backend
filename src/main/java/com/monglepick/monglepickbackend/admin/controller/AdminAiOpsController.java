package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateQuizRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateQuizResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateReviewRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateReviewResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.QuizSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReviewSummary;
import com.monglepick.monglepickbackend.admin.service.AdminAiOpsService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 AI 운영 API 컨트롤러.
 *
 * <p>관리자 페이지 "AI 운영" 탭의 6개 엔드포인트를 제공한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.2 AI 운영(6 API) 범위.</p>
 *
 * <h3>담당 엔드포인트 (6개)</h3>
 * <ul>
 *   <li>퀴즈 이력: GET /admin/ai/quiz/history</li>
 *   <li>퀴즈 생성 트리거: POST /admin/ai/quiz/generate</li>
 *   <li>리뷰 이력: GET /admin/ai/review/history</li>
 *   <li>리뷰 생성 트리거: POST /admin/ai/review/generate</li>
 *   <li>챗봇 세션 목록: GET /admin/ai/chatbot/sessions</li>
 *   <li>챗봇 세션 메시지: GET /admin/ai/chatbot/sessions/{sessionId}/messages</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — AI 운영", description = "AI 퀴즈/리뷰 이력·생성 트리거, 챗봇 대화 로그 조회")
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@Slf4j
public class AdminAiOpsController {

    /** AI 운영 비즈니스 로직 서비스 */
    private final AdminAiOpsService adminAiOpsService;

    // ======================== 퀴즈 ========================

    /**
     * AI 퀴즈 이력을 페이징 조회한다.
     *
     * @param status   퀴즈 상태 필터 (PENDING/APPROVED/REJECTED/PUBLISHED, 생략 시 전체)
     * @param pageable 페이지 정보 (기본 size=20)
     * @return 퀴즈 요약 페이지
     */
    @Operation(
            summary = "AI 퀴즈 이력 조회",
            description = "AI가 생성한 퀴즈 목록을 최신순으로 페이징 조회한다. status 파라미터로 상태 필터링 가능."
    )
    @GetMapping("/quiz/history")
    public ResponseEntity<ApiResponse<Page<QuizSummary>>> getQuizHistory(
            @Parameter(description = "퀴즈 상태 필터 (생략 시 전체)")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminAiOps] 퀴즈 이력 조회 요청 — status={}, page={}",
                status, pageable.getPageNumber());
        Page<QuizSummary> result = adminAiOpsService.getQuizHistory(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * AI 퀴즈 생성을 트리거한다.
     *
     * <p>현재는 Agent 라우터가 미구현이므로 관리자가 입력한 내용을 PENDING 상태로 저장한다.
     * 향후 Agent 쪽 엔드포인트가 추가되면 HTTP 호출로 전환된다.</p>
     *
     * @param request 생성 요청 DTO
     * @return 생성 결과 응답 DTO (HTTP 201)
     */
    @Operation(
            summary = "AI 퀴즈 생성 트리거",
            description = "AI 퀴즈 생성을 요청한다. 현재는 관리자가 입력한 내용을 PENDING 상태로 저장하는 폴백 경로를 사용한다."
    )
    @PostMapping("/quiz/generate")
    public ResponseEntity<ApiResponse<GenerateQuizResponse>> generateQuiz(
            @RequestBody @Valid GenerateQuizRequest request
    ) {
        log.info("[AdminAiOps] 퀴즈 생성 요청 — movieId={}", request.movieId());
        GenerateQuizResponse result = adminAiOpsService.generateQuiz(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    // ======================== 리뷰 ========================

    /**
     * AI 리뷰 이력을 페이징 조회한다.
     *
     * <p>현재는 AI 생성 플래그가 없어 전체 리뷰 최신순으로 반환한다.</p>
     *
     * @param pageable 페이지 정보
     * @return 리뷰 요약 페이지
     */
    @Operation(
            summary = "AI 리뷰 이력 조회",
            description = "AI가 생성한 리뷰 이력을 페이징 조회한다. 현재는 ai_generated 플래그가 없어 전체 리뷰 최신순으로 반환한다."
    )
    @GetMapping("/review/history")
    public ResponseEntity<ApiResponse<Page<ReviewSummary>>> getReviewHistory(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminAiOps] 리뷰 이력 조회 요청 — page={}", pageable.getPageNumber());
        Page<ReviewSummary> result = adminAiOpsService.getReviewHistory(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * AI 리뷰 생성을 트리거한다 (현재 미구현).
     *
     * @param request 생성 요청 DTO
     * @return 생성 결과 응답 DTO (success=false)
     */
    @Operation(
            summary = "AI 리뷰 생성 트리거",
            description = "AI 리뷰 생성을 요청한다. 현재 Agent 쪽 엔드포인트가 미구현이므로 안내 메시지만 반환한다."
    )
    @PostMapping("/review/generate")
    public ResponseEntity<ApiResponse<GenerateReviewResponse>> generateReview(
            @RequestBody @Valid GenerateReviewRequest request
    ) {
        log.info("[AdminAiOps] 리뷰 생성 요청 — movieId={}", request.movieId());
        GenerateReviewResponse result = adminAiOpsService.generateReview(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ======================== 챗봇 세션 ========================

    /**
     * 챗봇 세션 목록을 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 세션 요약 페이지
     */
    @Operation(
            summary = "챗봇 세션 목록 조회",
            description = "모든 사용자의 채팅 세션을 마지막 메시지 시각 기준 최신순으로 페이징 조회한다."
    )
    @GetMapping("/chatbot/sessions")
    public ResponseEntity<ApiResponse<Page<ChatSessionSummary>>> getChatSessions(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminAiOps] 챗봇 세션 목록 조회 요청 — page={}", pageable.getPageNumber());
        Page<ChatSessionSummary> result = adminAiOpsService.getChatSessions(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 단일 채팅 세션의 메시지 상세를 조회한다.
     *
     * @param sessionId 세션 UUID
     * @return 세션 상세 응답 DTO
     */
    @Operation(
            summary = "챗봇 세션 메시지 조회",
            description = "특정 세션 UUID 에 해당하는 전체 메시지(JSON)와 메타데이터를 조회한다."
    )
    @GetMapping("/chatbot/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<ChatSessionDetail>> getChatSessionMessages(
            @Parameter(description = "세션 UUID") @PathVariable String sessionId
    ) {
        log.debug("[AdminAiOps] 챗봇 세션 메시지 조회 요청 — sessionId={}", sessionId);
        ChatSessionDetail result = adminAiOpsService.getChatSessionDetail(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
