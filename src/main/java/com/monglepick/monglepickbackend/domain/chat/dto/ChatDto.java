package com.monglepick.monglepickbackend.domain.chat.dto;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 채팅 도메인 DTO 모음.
 *
 * <p>Agent 내부 호출(SaveSession/LoadSession)과
 * Client 이력 API(SessionList/SessionDetail) DTO를 함께 관리한다.</p>
 */
public final class ChatDto {

    private ChatDto() {
    }

    // ══════════════════════════════════════════════
    // Agent 내부 호출 (ServiceKey 인증)
    // ══════════════════════════════════════════════

    /**
     * 세션 저장 요청 (Agent → Backend, 매 턴마다 호출).
     * 세션이 없으면 신규 생성, 있으면 업데이트 (upsert).
     */
    public record SaveSessionRequest(
            @NotBlank(message = "userId는 필수입니다")
            String userId,

            @NotBlank(message = "sessionId는 필수입니다")
            String sessionId,

            @NotBlank(message = "messages는 필수입니다")
            String messages,

            int turnCount,

            /** 세션 제목 (nullable — 첫 턴에서 자동 생성) */
            String title,

            /** Agent 세션 상태 JSON (preferences, emotion 등) */
            String sessionState,

            /** 의도 요약 JSON */
            String intentSummary
    ) {
    }

    /** 세션 저장 응답 */
    public record SaveSessionResponse(
            Long chatSessionArchiveId,
            String sessionId,
            /** true면 신규 생성, false면 업데이트 */
            boolean created
    ) {
    }

    /** 세션 로드 요청 (Agent → Backend) */
    public record LoadSessionRequest(
            @NotBlank(message = "userId는 필수입니다")
            String userId,

            @NotBlank(message = "sessionId는 필수입니다")
            String sessionId
    ) {
    }

    /** 세션 로드 응답 — Agent가 그래프 초기 State 복원에 사용 */
    public record LoadSessionResponse(
            String sessionId,
            String messages,
            int turnCount,
            String sessionState,
            String intentSummary
    ) {
        public static LoadSessionResponse from(ChatSessionArchive archive) {
            return new LoadSessionResponse(
                    archive.getSessionId(),
                    archive.getMessages(),
                    archive.getTurnCount(),
                    archive.getSessionState(),
                    archive.getIntentSummary()
            );
        }
    }

    // ══════════════════════════════════════════════
    // Client 호출 (JWT 인증)
    // ══════════════════════════════════════════════

    /** 세션 목록 항목 (메시지 본문 제외, 메타데이터만) */
    public record SessionListItem(
            Long id,
            String sessionId,
            String title,
            int turnCount,
            LocalDateTime lastMessageAt,
            LocalDateTime startedAt
    ) {
        public static SessionListItem from(ChatSessionArchive archive) {
            return new SessionListItem(
                    archive.getChatSessionArchiveId(),
                    archive.getSessionId(),
                    archive.getTitle(),
                    archive.getTurnCount(),
                    archive.getLastMessageAt(),
                    archive.getStartedAt()
            );
        }
    }

    /** 세션 상세 응답 (메시지 포함 — 이어하기 시 이전 대화 표시용) */
    public record SessionDetailResponse(
            String sessionId,
            String title,
            String messages,
            int turnCount,
            LocalDateTime startedAt,
            LocalDateTime lastMessageAt
    ) {
        public static SessionDetailResponse from(ChatSessionArchive archive) {
            return new SessionDetailResponse(
                    archive.getSessionId(),
                    archive.getTitle(),
                    archive.getMessages(),
                    archive.getTurnCount(),
                    archive.getStartedAt(),
                    archive.getLastMessageAt()
            );
        }
    }

    /** 세션 제목 변경 요청 */
    public record UpdateTitleRequest(
            @NotBlank(message = "제목은 필수입니다")
            String title
    ) {
    }
}
