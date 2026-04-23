package com.monglepick.monglepickbackend.domain.chat.dto;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSuggestion;

import java.time.LocalDateTime;

/**
 * 채팅 추천 칩 관리자 응답 DTO.
 *
 * <p>GET /api/v1/admin/chat-suggestions 및 POST/PUT/PATCH 응답 페이로드.
 * 관리자 페이지 AI 운영 탭의 "채팅 추천 칩" 섹션에서 사용한다.</p>
 *
 * @param id           추천 칩 고유 ID
 * @param text         추천 질문 문구 (최대 200자)
 * @param category     분류 코드 (mood/genre/trending/family/seasonal 등, nullable)
 * @param isActive     활성 여부
 * @param startAt      노출 시작 시각 (nullable)
 * @param endAt        노출 종료 시각 (nullable)
 * @param displayOrder 정렬 우선순위
 * @param clickCount   클릭 수 누적
 * @param surface      AI 에이전트 채널 — 'user_chat' / 'admin_assistant' / 'faq_chatbot'
 *                     (2026-04-23 추가). 관리자 UI 가 이 값을 필터 셀렉트 + 테이블 컬럼에 노출.
 * @param adminId      등록 관리자 ID (nullable)
 * @param createdAt    생성 시각
 * @param updatedAt    최종 수정 시각
 */
public record AdminChatSuggestionResponse(
        Long id,
        String text,
        String category,
        Boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer displayOrder,
        Long clickCount,
        String surface,
        String adminId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 엔티티에서 관리자 응답 DTO를 생성한다.
     *
     * @param entity ChatSuggestion 엔티티
     * @return 관리자 응답 DTO
     */
    public static AdminChatSuggestionResponse from(ChatSuggestion entity) {
        return new AdminChatSuggestionResponse(
                entity.getSuggestionId(),
                entity.getText(),
                entity.getCategory(),
                entity.getIsActive(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getDisplayOrder(),
                entity.getClickCount(),
                entity.getSurface(),
                entity.getAdminId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
