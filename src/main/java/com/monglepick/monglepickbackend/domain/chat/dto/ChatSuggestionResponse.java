package com.monglepick.monglepickbackend.domain.chat.dto;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSuggestion;

/**
 * 채팅 추천 칩 Public 응답 DTO.
 *
 * <p>GET /api/v1/chat/suggestions 의 응답 페이로드.
 * 클라이언트 채팅 환영 화면에서 칩 버튼으로 표시된다.
 * text 를 클릭하면 채팅창에 해당 문구가 자동 삽입된다.</p>
 *
 * @param id   추천 칩 고유 ID (클릭 트래킹 POST 시 경로 변수로 사용)
 * @param text 채팅창에 삽입될 추천 질문 문구
 */
public record ChatSuggestionResponse(
        Long id,
        String text
) {
    /**
     * 엔티티에서 Public 응답 DTO를 생성한다.
     *
     * @param entity ChatSuggestion 엔티티
     * @return Public 응답 DTO
     */
    public static ChatSuggestionResponse from(ChatSuggestion entity) {
        return new ChatSuggestionResponse(
                entity.getSuggestionId(),
                entity.getText()
        );
    }
}
