package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.LogItem;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.Summary;
import com.monglepick.monglepickbackend.admin.service.AdminSupportChatLogService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 페이지 — 고객센터 챗봇 사용 로그 / 통계 컨트롤러.
 *
 * <p>Agent 가 매 턴 INSERT 한 {@code support_chat_log} 데이터를 관리자가
 * 검색·트레이스·집계할 수 있도록 read-only 엔드포인트를 제공한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code GET /api/v1/admin/support/chat-logs} — 페이징 검색 (의도/1:1유도/사용자/기간/키워드 필터)</li>
 *   <li>{@code GET /api/v1/admin/support/chat-logs/sessions/{sessionId}} — 단일 세션 트레이스</li>
 *   <li>{@code GET /api/v1/admin/support/chat-logs/summary} — 통계 요약 (분포/시계열/TOP 메시지)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>관리자 권한 (SUPER_ADMIN/ADMIN/SUPPORT_ADMIN) 필요. 실제 강제는
 * SecurityConfig 의 {@code /api/v1/admin/**} 매처에 위임.</p>
 */
@Tag(name = "관리자 — 고객센터 챗봇 로그", description = "사용자 문의 분석/통계/감사")
@RestController
@RequestMapping("/api/v1/admin/support/chat-logs")
@RequiredArgsConstructor
@Slf4j
public class AdminSupportChatLogController {

    private final AdminSupportChatLogService service;

    @Operation(
            summary = "고객센터 챗봇 로그 페이징 검색",
            description = "의도(intentKind), 1:1 유도 여부(needsHuman), 사용자(userId), 기간(from/to), 키워드(LIKE) 필터로 페이징 검색."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<LogItem>>> searchLogs(
            @Parameter(description = "의도 필터 (faq/personal_data/policy/redirect/smalltalk/complaint/unknown)")
            @RequestParam(required = false) String intentKind,
            @Parameter(description = "1:1 유도 여부 (true/false). NULL=무시")
            @RequestParam(required = false) Boolean needsHuman,
            @Parameter(description = "특정 사용자 ID 필터")
            @RequestParam(required = false) String userId,
            @Parameter(description = "user_message LIKE 키워드")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "기간 시작 (ISO LOCAL)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "기간 종료 (ISO LOCAL, 미포함)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<LogItem> result = service.searchLogs(
                intentKind, needsHuman, userId, keyword, from, to, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(
            summary = "단일 세션 트레이스",
            description = "session_id 단위로 모든 턴(user/assistant)을 시간순 조회한다. 한 사용자의 대화 흐름 분석용."
    )
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<List<LogItem>>> getSessionTrace(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.getSessionTrace(sessionId)));
    }

    @Operation(
            summary = "통계 요약",
            description = "기간 내 총 건수 / 1:1 유도 비율 / 의도별 분포 / 일자별 추이 / TOP 발화."
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Summary>> getSummary(
            @Parameter(description = "기간 시작 (ISO LOCAL). 미지정 시 전체 기간")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "기간 종료 (ISO LOCAL, 미포함). 미지정 시 전체 기간")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "TOP 발화 N (기본 20)")
            @RequestParam(defaultValue = "20") int topN
    ) {
        Summary summary = service.getSummary(from, to, topN);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
