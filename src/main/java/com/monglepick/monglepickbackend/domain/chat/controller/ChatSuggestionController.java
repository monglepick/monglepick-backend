package com.monglepick.monglepickbackend.domain.chat.controller;

import com.monglepick.monglepickbackend.domain.chat.dto.ChatSuggestionResponse;
import com.monglepick.monglepickbackend.domain.chat.service.ChatSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 추천 칩 Public API 컨트롤러.
 *
 * <p>클라이언트 채팅 환영 화면에서 추천 질문 칩을 동적으로 노출하기 위한
 * 공개 엔드포인트를 제공한다. 인증 불필요 (SecurityConfig permitAll 등록됨).</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET  /api/v1/chat/suggestions?limit=4 — 활성 칩 랜덤 N개 조회</li>
 *   <li>POST /api/v1/chat/suggestions/{id}/click — 클릭 트래킹 (204, fire-and-forget)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/suggestions")
@RequiredArgsConstructor
@Tag(name = "채팅 추천 칩", description = "채팅 환영 화면 추천 질문 칩 Public API")
public class ChatSuggestionController {

    private final ChatSuggestionService chatSuggestionService;

    /**
     * 현재 활성 추천 칩을 랜덤으로 조회한다.
     *
     * <p>활성 풀(is_active=true, 기간 조건 충족) 전체를 셔플한 뒤
     * 앞에서 limit 개만 잘라 반환한다.
     * limit 범위는 [1, 10] 으로 클램프된다.</p>
     *
     * @param limit 반환할 칩 수 (기본값 4, 범위 초과 시 클램프)
     * @return 200 OK — 추천 칩 DTO 목록 (bare List, 최대 limit 개)
     */
    @Operation(
            summary = "활성 추천 칩 조회",
            description = "AI 에이전트 채널(surface) 별 활성 추천 질문 칩을 랜덤으로 반환한다. "
                    + "surface 미지정 시 기본값 'user_chat' (유저 채팅 환영 화면)."
                    + "limit 범위는 [1, 10]으로 클램프된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 칩 목록 반환 성공")
    })
    @GetMapping
    public List<ChatSuggestionResponse> getActive(
            @Parameter(
                    description = "AI 에이전트 채널 (user_chat / admin_assistant / faq_chatbot). "
                            + "허용 외 값이면 user_chat 으로 보정됨.",
                    example = "user_chat"
            )
            @RequestParam(defaultValue = "user_chat") String surface,
            @Parameter(description = "반환할 칩 수 (기본값 4, 범위 [1, 10])")
            @RequestParam(defaultValue = "4") int limit
    ) {
        // 2026-04-23: surface 파라미터 추가. 기존 호출부(limit 만 전달)는 surface 기본값
        // 'user_chat' 으로 동작하므로 하위 호환 완전 유지.
        return chatSuggestionService.getActivePool(surface, limit);
    }

    /**
     * 추천 칩 클릭을 트래킹한다 (fire-and-forget).
     *
     * <p>해당 칩이 존재하지 않으면 silent return 으로 204를 반환한다.
     * 클라이언트에서 칩 클릭 시 비동기로 호출하며 응답을 기다리지 않아도 된다.</p>
     *
     * @param id 클릭된 추천 칩 ID
     * @return 204 No Content
     */
    @Operation(
            summary = "추천 칩 클릭 트래킹",
            description = "사용자가 추천 칩을 클릭했을 때 click_count를 1 증가시킨다. "
                    + "해당 ID가 존재하지 않아도 204를 반환한다 (fire-and-forget)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "클릭 트래킹 성공 (또는 silent skip)")
    })
    @PostMapping("/{id}/click")
    public ResponseEntity<Void> trackClick(
            @Parameter(description = "클릭된 추천 칩 ID")
            @PathVariable Long id
    ) {
        chatSuggestionService.trackClick(id);
        return ResponseEntity.noContent().build();
    }
}
