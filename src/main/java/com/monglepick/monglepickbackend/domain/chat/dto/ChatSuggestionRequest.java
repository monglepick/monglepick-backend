package com.monglepick.monglepickbackend.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 채팅 추천 칩 생성/수정 요청 DTO.
 *
 * <p>POST /api/v1/admin/chat-suggestions (생성)
 * 및 PUT /api/v1/admin/chat-suggestions/{id} (수정) 의 요청 바디.
 * PATCH /{id}/active 는 별도의 단순 boolean 파라미터로 처리한다.</p>
 *
 * @param text         추천 질문 문구 (필수, 1~200자)
 * @param category     분류 코드 (옵셔널, 최대 50자). mood/genre/trending/family/seasonal/similar/personal 등
 * @param isActive     활성 여부 (옵셔널, 미전달 시 기본값 false)
 * @param startAt      노출 시작 시각 (옵셔널, null 이면 즉시)
 * @param endAt        노출 종료 시각 (옵셔널, null 이면 무기한)
 * @param displayOrder 정렬 우선순위 (옵셔널, 미전달 시 기본값 0)
 * @param surface      AI 에이전트 채널 (2026-04-23 추가). 'user_chat' / 'admin_assistant' /
 *                     'faq_chatbot' 중 하나. null 또는 빈 문자열이면 기본값 'user_chat'.
 *                     서비스 레이어 `ChatSuggestionService.ALLOWED_SURFACES` 로 화이트리스트 검증.
 */
public record ChatSuggestionRequest(
        @NotBlank(message = "추천 칩 문구는 필수입니다")
        @Size(max = 200, message = "추천 칩 문구는 최대 200자입니다")
        String text,

        @Size(max = 50, message = "카테고리는 최대 50자입니다")
        String category,

        Boolean isActive,

        LocalDateTime startAt,

        LocalDateTime endAt,

        Integer displayOrder,

        @Size(max = 30, message = "surface 는 최대 30자입니다")
        String surface
) {
}
