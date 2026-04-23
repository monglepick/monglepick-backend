package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.domain.chat.dto.AdminChatSuggestionResponse;
import com.monglepick.monglepickbackend.domain.chat.dto.ChatSuggestionRequest;
import com.monglepick.monglepickbackend.domain.chat.service.ChatSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 채팅 추천 칩 관리자 API 컨트롤러.
 *
 * <p>관리자 페이지 "AI 운영 → 채팅 추천 칩" 탭의 엔드포인트를 제공한다.
 * 보안은 SecurityConfig URL 기반 {@code /api/v1/admin/**} 보호 정책으로 처리한다
 * (@PreAuthorize 미사용 — AdminAiOpsController 패턴 동일).</p>
 *
 * <h3>엔드포인트 (5개)</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/chat-suggestions               — 목록 조회 (페이징 + 필터)</li>
 *   <li>POST   /api/v1/admin/chat-suggestions               — 신규 생성</li>
 *   <li>PUT    /api/v1/admin/chat-suggestions/{id}          — 전체 수정</li>
 *   <li>DELETE /api/v1/admin/chat-suggestions/{id}          — 삭제</li>
 *   <li>PATCH  /api/v1/admin/chat-suggestions/{id}/active   — 활성 여부 토글</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/chat-suggestions")
@RequiredArgsConstructor
@Tag(name = "관리자 — 채팅 추천 칩", description = "채팅 환영 화면 추천 칩 관리자 CRUD API")
public class AdminChatSuggestionController {

    private final ChatSuggestionService chatSuggestionService;

    /**
     * 추천 칩 목록을 페이지네이션으로 조회한다.
     *
     * <p>isActive / fromDate / toDate 필터는 모두 옵셔널이다.
     * 기본 정렬: 생성일 내림차순.</p>
     *
     * @param isActive  활성 여부 필터 (null 이면 전체)
     * @param fromDate  생성일 시작 (inclusive, ISO datetime)
     * @param toDate    생성일 종료 (exclusive, ISO datetime)
     * @param pageable  페이지 정보 (기본: page=0, size=20)
     * @return 200 OK — bare Page&lt;AdminChatSuggestionResponse&gt;
     */
    @Operation(
            summary = "추천 칩 목록 조회",
            description = "isActive/fromDate/toDate 필터와 페이지네이션으로 추천 칩 목록을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 조회 성공")
    })
    @GetMapping
    public Page<AdminChatSuggestionResponse> getList(
            @Parameter(
                    description = "AI 에이전트 채널 필터 (user_chat / admin_assistant / faq_chatbot). "
                            + "미전달 또는 빈 문자열이면 전체. 2026-04-23 추가.",
                    example = "user_chat"
            )
            @RequestParam(required = false) String surface,

            @Parameter(description = "활성 여부 필터 (미전달 시 전체)")
            @RequestParam(required = false) Boolean isActive,

            @Parameter(description = "생성일 시작 (inclusive, ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,

            @Parameter(description = "생성일 종료 (exclusive, ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,

            @PageableDefault(size = 20) Pageable pageable
    ) {
        return chatSuggestionService.getList(surface, isActive, fromDate, toDate, pageable);
    }

    /**
     * 추천 칩을 새로 생성한다.
     *
     * @param request 생성 요청 DTO (text 필수, 나머지 옵셔널)
     * @return 201 Created — 생성된 추천 칩 관리자 응답 DTO
     */
    @Operation(
            summary = "추천 칩 생성",
            description = "새로운 채팅 추천 칩을 생성한다. text 는 필수(최대 200자), 나머지는 옵셔널."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검증 실패")
    })
    @PostMapping
    public ResponseEntity<AdminChatSuggestionResponse> create(
            @RequestBody @Valid ChatSuggestionRequest request
    ) {
        AdminChatSuggestionResponse response = chatSuggestionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 추천 칩 내용을 전체 수정한다.
     *
     * @param id      수정 대상 추천 칩 ID
     * @param request 수정 요청 DTO
     * @return 200 OK — 수정된 추천 칩 관리자 응답 DTO
     */
    @Operation(
            summary = "추천 칩 수정",
            description = "text/category/isActive/startAt/endAt/displayOrder 를 수정한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
            @ApiResponse(responseCode = "404", description = "해당 추천 칩 없음")
    })
    @PutMapping("/{id}")
    public AdminChatSuggestionResponse update(
            @Parameter(description = "수정할 추천 칩 ID") @PathVariable Long id,
            @RequestBody @Valid ChatSuggestionRequest request
    ) {
        return chatSuggestionService.update(id, request);
    }

    /**
     * 추천 칩을 삭제한다.
     *
     * @param id 삭제 대상 추천 칩 ID
     * @return 204 No Content
     */
    @Operation(
            summary = "추천 칩 삭제",
            description = "해당 ID의 추천 칩을 영구 삭제한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "해당 추천 칩 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "삭제할 추천 칩 ID") @PathVariable Long id
    ) {
        chatSuggestionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 추천 칩의 활성 여부를 토글한다.
     *
     * <p>요청 바디: {@code {"isActive": true}} 형식의 단순 JSON.
     * PATCH 의 단일 필드 변경 의미론에 맞춰 isActive 만 변경한다.</p>
     *
     * @param id       대상 추천 칩 ID
     * @param body     {@code {"isActive": boolean}} 맵
     * @return 200 OK — 변경된 추천 칩 관리자 응답 DTO
     */
    @Operation(
            summary = "추천 칩 활성 여부 토글",
            description = "isActive 필드만 변경한다. 요청 바디: {\"isActive\": true/false}"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토글 성공"),
            @ApiResponse(responseCode = "404", description = "해당 추천 칩 없음")
    })
    @PatchMapping("/{id}/active")
    public AdminChatSuggestionResponse toggleActive(
            @Parameter(description = "토글할 추천 칩 ID") @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean isActive = Boolean.TRUE.equals(body.get("isActive"));
        return chatSuggestionService.toggleActive(id, isActive);
    }
}
