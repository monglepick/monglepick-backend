package com.monglepick.monglepickbackend.domain.chat.controller;

import com.monglepick.monglepickbackend.domain.chat.dto.ChatDto;
import com.monglepick.monglepickbackend.domain.chat.service.ChatService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 채팅 이력 API 컨트롤러 — Client 대면 (JWT 인증).
 *
 * <p>사용자가 이전 채팅 목록을 조회하고, 세션을 선택하여 이어서 대화하거나,
 * 세션을 삭제/제목 변경할 때 사용한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code GET    /api/v1/chat/history}                  — 세션 목록 (페이징)</li>
 *   <li>{@code GET    /api/v1/chat/history/{sessionId}}      — 세션 상세 (메시지 포함)</li>
 *   <li>{@code DELETE /api/v1/chat/history/{sessionId}}      — 세션 소프트 삭제</li>
 *   <li>{@code PATCH  /api/v1/chat/history/{sessionId}/title} — 제목 변경</li>
 * </ul>
 */
@Tag(name = "채팅 이력", description = "이전 채팅 목록 조회, 이어하기, 삭제")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/v1/chat/history")
@RequiredArgsConstructor
public class ChatHistoryController extends BaseController {

    private final ChatService chatService;

    /**
     * 사용자의 이전 채팅 목록 조회 (페이징, 최신 대화 순).
     * 소프트 삭제된 세션은 제외된다.
     */
    @Operation(summary = "채팅 목록 조회", description = "사용자의 이전 채팅 세션 목록을 페이징으로 조회한다.")
    @GetMapping
    public ResponseEntity<Page<ChatDto.SessionListItem>> getSessionList(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = resolveUserId(principal);
        int safeSize = limitPageSize(size);
        Page<ChatDto.SessionListItem> sessions = chatService.getSessionList(
                userId, PageRequest.of(page, safeSize));
        return ResponseEntity.ok(sessions);
    }

    /**
     * 세션 상세 조회 (메시지 포함).
     * 이어하기 시 이전 대화 내용을 표시하기 위해 사용한다.
     */
    @Operation(summary = "채팅 상세 조회", description = "세션의 전체 메시지를 포함한 상세 정보를 조회한다.")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatDto.SessionDetailResponse> getSessionDetail(
            Principal principal,
            @PathVariable String sessionId) {
        String userId = resolveUserId(principal);
        ChatDto.SessionDetailResponse detail = chatService.getSessionDetail(userId, sessionId);
        return ResponseEntity.ok(detail);
    }

    /**
     * 세션 소프트 삭제.
     * 삭제된 세션은 목록에서 제외되며, 30일 후 물리삭제 스케줄러가 처리한다.
     */
    @Operation(summary = "채팅 삭제", description = "채팅 세션을 소프트 삭제한다.")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            Principal principal,
            @PathVariable String sessionId) {
        String userId = resolveUserId(principal);
        chatService.deleteSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 세션 제목 변경.
     */
    @Operation(summary = "채팅 제목 변경", description = "채팅 세션의 제목을 변경한다.")
    @PatchMapping("/{sessionId}/title")
    public ResponseEntity<Void> updateTitle(
            Principal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody ChatDto.UpdateTitleRequest request) {
        String userId = resolveUserId(principal);
        chatService.updateSessionTitle(userId, sessionId, request.title());
        return ResponseEntity.noContent().build();
    }
}
