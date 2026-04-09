package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatStatsResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.QuizSummary;
import com.monglepick.monglepickbackend.admin.service.AdminAiOpsService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 AI 운영 API 컨트롤러.
 *
 * <p>관리자 페이지 "AI 운영" 탭의 엔드포인트를 제공한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.2 AI 운영 참조.</p>
 *
 * <h3>담당 엔드포인트 (4개 — Backend)</h3>
 * <ul>
 *   <li>퀴즈 이력: GET /admin/ai/quiz/history</li>
 *   <li>챗봇 세션 목록: GET /admin/ai/chat/sessions</li>
 *   <li>챗봇 세션 메시지: GET /admin/ai/chat/sessions/{sessionId}/messages</li>
 *   <li>챗봇 통계: GET /admin/ai/chat/stats</li>
 * </ul>
 *
 * <h3>Agent 전담 엔드포인트</h3>
 * <ul>
 *   <li>POST /admin/ai/quiz/generate — LLM 기반 퀴즈 자동 생성 (monglepick-agent :8000)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-08: AI 리뷰 생성/이력 기능 제거 (ai_generated 플래그 부재로 의미 없음)</li>
 *   <li>2026-04-08: 퀴즈 생성(POST /quiz/generate) 제거 — Agent 로 완전 이관.
 *       기존 Backend 스텁은 LLM 호출 없이 입력값을 INSERT 만 하던 dead code 였으므로 삭제</li>
 *   <li>2026-04-08: 챗봇 엔드포인트 경로를 UI 상수({@code CHAT_SESSIONS})에 맞추어
 *       {@code /chatbot/sessions} → {@code /chat/sessions} 로 통일</li>
 *   <li>2026-04-08: {@code GET /chat/stats} 추가 — 챗봇 사용량 집계</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — AI 운영", description = "AI 퀴즈 이력 조회, 챗봇 대화 로그 및 사용 통계")
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@Slf4j
public class AdminAiOpsController {

    /** AI 운영 비즈니스 로직 서비스 */
    private final AdminAiOpsService adminAiOpsService;

    // ======================== 퀴즈 ========================

    /**
     * AI 퀴즈 이력을 복합 필터로 페이징 조회한다 — 2026-04-09 P1-⑫ 확장.
     *
     * <p>2026-04-08: 퀴즈 생성(POST /quiz/generate) 은 Agent(monglepick-agent :8000)로
     * 완전히 이관되었으므로 Backend 에는 이력 조회만 남는다.</p>
     *
     * <p>2026-04-09 P1-⑫ 확장: status 외에 {@code movieId} / {@code keyword} / 시간 범위
     * (fromDate/toDate) 복합 필터 파라미터를 추가했다. 기존 Frontend 클라이언트 MVP 필터
     * (현재 페이지 10건 내) 를 서버 전역 검색으로 승급하여 "다른 페이지에 있는 특정 영화의
     * 퀴즈" 같은 실제 운영 검색이 가능해졌다. 모든 파라미터는 optional 이며 빈 값은
     * null 로 정규화되어 해당 조건이 비활성화된다.</p>
     *
     * @param status   퀴즈 상태 필터 (PENDING/APPROVED/REJECTED/PUBLISHED, 생략 시 전체)
     * @param movieId  영화 ID 부분 일치 필터 (대소문자 무시, 생략 시 전체)
     * @param keyword  문제/해설/정답 본문 키워드 부분 일치 (OR 조건, 대소문자 무시, 생략 시 전체)
     * @param fromDate quizDate 시작 inclusive (ISO-8601 DATE, 예: 2026-04-01)
     * @param toDate   quizDate 종료 inclusive (ISO-8601 DATE)
     * @param pageable 페이지 정보 (기본 size=20)
     * @return 퀴즈 요약 페이지
     */
    @Operation(
            summary = "AI 퀴즈 이력 조회 (복합 필터)",
            description = "AI 퀴즈 목록을 최신순으로 페이징 조회한다. status/movieId/keyword/quizDate 범위를 " +
                    "조합하여 필터링할 수 있으며, 모든 파라미터는 선택사항이다. 2026-04-09 P1-⑫ 확장."
    )
    @GetMapping("/quiz/history")
    public ResponseEntity<ApiResponse<Page<QuizSummary>>> getQuizHistory(
            @Parameter(description = "퀴즈 상태 필터 (PENDING/APPROVED/REJECTED/PUBLISHED, 생략 시 전체)")
            @RequestParam(required = false) String status,

            @Parameter(description = "영화 ID 부분 일치 키워드 (대소문자 무시)")
            @RequestParam(required = false) String movieId,

            @Parameter(description = "문제/해설/정답 본문 키워드 (OR 조건, 대소문자 무시)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "quizDate 시작 inclusive (ISO-8601 DATE, 예: 2026-04-01)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fromDate,

            @Parameter(description = "quizDate 종료 inclusive (ISO-8601 DATE)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate toDate,

            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminAiOps] 퀴즈 이력 조회 — status={}, movieId={}, keyword={}, from={}, to={}, page={}",
                status, movieId, keyword, fromDate, toDate, pageable.getPageNumber());
        Page<QuizSummary> result = adminAiOpsService.getQuizHistory(
                status, movieId, keyword, fromDate, toDate, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ======================== 챗봇 세션 ========================
    //
    // 2026-04-08: UI 상수(monglepick-admin/src/shared/constants/api.js 의
    // AI_ADMIN_ENDPOINTS.CHAT_SESSIONS)와의 경로 불일치를 해소하기 위해
    // "/chatbot/sessions" → "/chat/sessions" 로 통일한다.

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
    @GetMapping("/chat/sessions")
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
    @GetMapping("/chat/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<ChatSessionDetail>> getChatSessionMessages(
            @Parameter(description = "세션 UUID") @PathVariable String sessionId
    ) {
        log.debug("[AdminAiOps] 챗봇 세션 메시지 조회 요청 — sessionId={}", sessionId);
        ChatSessionDetail result = adminAiOpsService.getChatSessionDetail(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 챗봇 사용량 통계를 조회한다 (Task 4).
     *
     * <p>일자 범위(from/to)를 받아 해당 기간의 세션 수/메시지 수/활성 사용자 수/의도별 빈도를 집계한다.
     * 파라미터 생략 시 최근 7일 범위로 동작한다.</p>
     *
     * @param from 시작 일자 (ISO yyyy-MM-dd, 생략 시 오늘 -7일)
     * @param to   종료 일자 (ISO yyyy-MM-dd, 생략 시 오늘)
     * @return 챗봇 통계 응답 DTO
     */
    @Operation(
            summary = "챗봇 사용 통계 조회",
            description = "지정 기간의 챗봇 세션 수/총 턴 수/활성 세션 수/상위 의도를 집계한다. 파라미터 생략 시 최근 7일."
    )
    @GetMapping("/chat/stats")
    public ResponseEntity<ApiResponse<ChatStatsResponse>> getChatStats(
            @Parameter(description = "시작 일자 (ISO yyyy-MM-dd, 생략 시 오늘 -7일)")
            @RequestParam(required = false) String from,
            @Parameter(description = "종료 일자 (ISO yyyy-MM-dd, 생략 시 오늘)")
            @RequestParam(required = false) String to
    ) {
        log.debug("[AdminAiOps] 챗봇 통계 조회 요청 — from={}, to={}", from, to);
        ChatStatsResponse result = adminAiOpsService.getChatStats(from, to);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
