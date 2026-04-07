package com.monglepick.monglepickbackend.domain.chat.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.chat.dto.ChatDto;
import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import com.monglepick.monglepickbackend.domain.chat.repository.ChatSessionArchiveRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 채팅 세션 서비스.
 *
 * <p>Agent 내부 API(세션 저장/로드)와 Client 이력 API(목록/상세/삭제)의
 * 비즈니스 로직을 담당한다.</p>
 *
 * <h3>Agent 내부 호출 (ServiceKey 인증)</h3>
 * <ul>
 *   <li>{@link #saveSession} — 세션 upsert (매 턴마다 호출)</li>
 *   <li>{@link #loadSession} — 세션 로드 (이어하기 시 상태 복원)</li>
 * </ul>
 *
 * <h3>Client 호출 (JWT 인증)</h3>
 * <ul>
 *   <li>{@link #getSessionList} — 이전 채팅 목록 조회 (페이징)</li>
 *   <li>{@link #getSessionDetail} — 세션 상세 조회 (메시지 포함)</li>
 *   <li>{@link #deleteSession} — 세션 소프트 삭제</li>
 *   <li>{@link #updateSessionTitle} — 세션 제목 변경</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ChatService {

    private final ChatSessionArchiveRepository sessionRepository;
    private final ObjectMapper objectMapper;

    /** 세션 제목 자동 생성 시 최대 길이 */
    private static final int TITLE_MAX_LENGTH = 50;

    // ══════════════════════════════════════════════
    // Agent 내부 호출
    // ══════════════════════════════════════════════

    /**
     * 세션 upsert — 존재하면 업데이트, 없으면 신규 생성.
     *
     * <p>Agent가 매 턴마다 그래프 실행 완료 후 호출한다.
     * title이 null이고 첫 턴이면 messages에서 첫 user 메시지를 파싱하여 자동 생성한다.</p>
     */
    @Transactional
    public ChatDto.SaveSessionResponse saveSession(ChatDto.SaveSessionRequest request) {
        LocalDateTime now = LocalDateTime.now();

        return sessionRepository.findBySessionId(request.sessionId())
                .map(existing -> {
                    // 기존 세션 업데이트
                    existing.updateMessages(request.messages(), request.turnCount(), now);
                    if (request.sessionState() != null) {
                        existing.updateSessionState(request.sessionState());
                    }
                    if (request.intentSummary() != null) {
                        existing.updateIntentSummary(request.intentSummary());
                    }
                    // 제목이 아직 없고 요청에 제목이 있으면 설정
                    if (existing.getTitle() == null && request.title() != null) {
                        existing.updateTitle(request.title());
                    }
                    log.debug("chat_session_updated: sessionId={}, turnCount={}",
                            request.sessionId(), request.turnCount());
                    return new ChatDto.SaveSessionResponse(
                            existing.getChatSessionArchiveId(), existing.getSessionId(), false);
                })
                .orElseGet(() -> {
                    // 신규 세션 생성
                    // users 테이블 쓰기 소유는 김민규(MyBatis) — JPA에서 fetch 하지 않고
                    // String userId만 보관한다 (설계서 §15.4).
                    // 사용자 존재 검증은 ServiceKey 인증 단계에서 이미 수행됨.

                    // 제목 자동 생성: 요청에 title이 없으면 첫 user 메시지에서 추출
                    String title = request.title();
                    if (title == null || title.isBlank()) {
                        title = extractTitleFromMessages(request.messages());
                    }

                    ChatSessionArchive newSession = ChatSessionArchive.builder()
                            .userId(request.userId())
                            .sessionId(request.sessionId())
                            .messages(request.messages())
                            .turnCount(request.turnCount())
                            .sessionState(request.sessionState())
                            .intentSummary(request.intentSummary())
                            .title(title)
                            .startedAt(now)
                            .lastMessageAt(now)
                            .isActive(true)
                            .isDeleted(false)
                            .build();

                    ChatSessionArchive saved = sessionRepository.save(newSession);
                    log.info("chat_session_created: sessionId={}, userId={}",
                            request.sessionId(), request.userId());
                    return new ChatDto.SaveSessionResponse(
                            saved.getChatSessionArchiveId(), saved.getSessionId(), true);
                });
    }

    /**
     * 세션 로드 — Agent가 이어하기 시 세션 상태를 복원한다.
     *
     * @return 세션 데이터 (messages, sessionState 등), 없으면 null
     */
    public ChatDto.LoadSessionResponse loadSession(String userId, String sessionId) {
        return sessionRepository
                .findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionId)
                .map(ChatDto.LoadSessionResponse::from)
                .orElse(null);
    }

    // ══════════════════════════════════════════════
    // Client 호출
    // ══════════════════════════════════════════════

    /**
     * 사용자의 이전 채팅 목록 조회 (페이징, 최신 메시지 순).
     * 소프트 삭제된 세션은 제외한다.
     */
    public Page<ChatDto.SessionListItem> getSessionList(String userId, Pageable pageable) {
        return sessionRepository
                .findByUserIdAndIsDeletedFalseOrderByLastMessageAtDesc(userId, pageable)
                .map(ChatDto.SessionListItem::from);
    }

    /**
     * 세션 상세 조회 (메시지 포함).
     * 본인 세션만 조회 가능하며, 소프트 삭제된 세션은 접근 불가.
     */
    public ChatDto.SessionDetailResponse getSessionDetail(String userId, String sessionId) {
        ChatSessionArchive session = sessionRepository
                .findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        return ChatDto.SessionDetailResponse.from(session);
    }

    /**
     * 세션 소프트 삭제.
     * 본인 세션만 삭제 가능하며, 30일 후 물리삭제 스케줄러가 처리한다.
     */
    @Transactional
    public void deleteSession(String userId, String sessionId) {
        ChatSessionArchive session = sessionRepository
                .findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        session.softDelete();
        log.info("chat_session_deleted: sessionId={}, userId={}", sessionId, userId);
    }

    /**
     * 세션 제목 변경.
     */
    @Transactional
    public void updateSessionTitle(String userId, String sessionId, String newTitle) {
        ChatSessionArchive session = sessionRepository
                .findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        session.updateTitle(newTitle);
    }

    // ══════════════════════════════════════════════
    // 내부 유틸
    // ══════════════════════════════════════════════

    /**
     * messages JSON에서 첫 번째 user 메시지의 content를 추출하여 세션 제목으로 사용한다.
     * 50자를 초과하면 "..." 접미사를 붙여 자른다.
     *
     * @param messagesJson messages JSON 문자열
     * @return 추출된 제목 또는 "새 대화" (파싱 실패/빈 메시지 시)
     */
    private String extractTitleFromMessages(String messagesJson) {
        try {
            List<Map<String, Object>> messages = objectMapper.readValue(
                    messagesJson, new TypeReference<>() {
                    });
            for (Map<String, Object> msg : messages) {
                if ("user".equals(msg.get("role")) && msg.get("content") != null) {
                    String content = msg.get("content").toString().trim();
                    if (!content.isEmpty()) {
                        return content.length() > TITLE_MAX_LENGTH
                                ? content.substring(0, TITLE_MAX_LENGTH) + "..."
                                : content;
                    }
                }
            }
        } catch (JacksonException e) {
            log.warn("chat_title_extract_failed: {}", e.getMessage());
        }
        return "새 대화";
    }
}
