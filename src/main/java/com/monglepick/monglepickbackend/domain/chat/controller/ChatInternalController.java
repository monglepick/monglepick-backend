package com.monglepick.monglepickbackend.domain.chat.controller;

import com.monglepick.monglepickbackend.domain.chat.dto.ChatDto;
import com.monglepick.monglepickbackend.domain.chat.service.ChatService;
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
 * 채팅 내부 API 컨트롤러 — Agent 전용 (ServiceKey 인증).
 *
 * <p>AI Agent가 매 턴마다 세션 상태를 MySQL에 저장/로드할 때 사용한다.
 * X-Service-Key 헤더로 인증하며, 요청 body의 userId를 신뢰한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code POST /api/v1/chat/internal/session/save} — 세션 upsert</li>
 *   <li>{@code POST /api/v1/chat/internal/session/load} — 세션 로드</li>
 * </ul>
 */
@Tag(name = "채팅 내부 API", description = "Agent 전용 세션 관리 (ServiceKey 인증)")
@RestController
@RequestMapping("/api/v1/chat/internal")
@RequiredArgsConstructor
public class ChatInternalController extends BaseController {

    private final ChatService chatService;

    /**
     * 세션 저장 (upsert) — Agent가 매 턴마다 그래프 실행 완료 후 호출.
     * 세션이 없으면 신규 생성, 있으면 messages/sessionState 업데이트.
     */
    @Operation(summary = "세션 저장 (upsert)", description = "Agent가 매 턴마다 호출하여 세션 상태를 MySQL에 저장한다.")
    @SecurityRequirement(name = "ServiceKey")
    @PostMapping("/session/save")
    public ResponseEntity<ChatDto.SaveSessionResponse> saveSession(
            Principal principal,
            @Valid @RequestBody ChatDto.SaveSessionRequest request) {
        resolveUserIdWithServiceKey(principal, request.userId());
        ChatDto.SaveSessionResponse response = chatService.saveSession(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 세션 로드 — Agent가 이어하기 시 세션 상태를 복원.
     * 세션이 없으면 body가 null인 200 응답을 반환한다 (신규 세션으로 진행).
     */
    @Operation(summary = "세션 로드", description = "Agent가 이어하기 시 호출하여 세션 상태를 MySQL에서 복원한다.")
    @SecurityRequirement(name = "ServiceKey")
    @PostMapping("/session/load")
    public ResponseEntity<ChatDto.LoadSessionResponse> loadSession(
            Principal principal,
            @Valid @RequestBody ChatDto.LoadSessionRequest request) {
        resolveUserIdWithServiceKey(principal, request.userId());
        ChatDto.LoadSessionResponse response = chatService.loadSession(
                request.userId(), request.sessionId());
        return ResponseEntity.ok(response);
    }
}
