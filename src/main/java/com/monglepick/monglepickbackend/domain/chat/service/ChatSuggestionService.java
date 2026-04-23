package com.monglepick.monglepickbackend.domain.chat.service;

import com.monglepick.monglepickbackend.domain.chat.dto.AdminChatSuggestionResponse;
import com.monglepick.monglepickbackend.domain.chat.dto.ChatSuggestionRequest;
import com.monglepick.monglepickbackend.domain.chat.dto.ChatSuggestionResponse;
import com.monglepick.monglepickbackend.domain.chat.entity.ChatSuggestion;
import com.monglepick.monglepickbackend.domain.chat.repository.ChatSuggestionRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 채팅 추천 칩 서비스.
 *
 * <p>클라이언트 채팅 환영 화면에 표시되는 추천 칩의
 * Public 조회, 클릭 트래킹, 관리자 CRUD 를 담당한다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly=true)} — 읽기 전용 기본</li>
 *   <li>쓰기 메서드: 메서드 레벨 {@code @Transactional} 으로 readOnly=false 오버라이드</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSuggestionService {

    /** limit 파라미터의 최솟값 — 1 미만 요청은 1로 클램프 */
    private static final int LIMIT_MIN = 1;

    /** limit 파라미터의 최댓값 — 10 초과 요청은 10으로 클램프 */
    private static final int LIMIT_MAX = 10;

    private final ChatSuggestionRepository chatSuggestionRepository;

    // ─────────────────────────────────────────────
    // Public API — 채팅 환영 화면 칩 노출
    // ─────────────────────────────────────────────

    /**
     * 현재 활성 추천 칩 풀에서 랜덤으로 {@code limit} 개를 반환한다.
     *
     * <p>활성 조건: is_active=true AND 기간(start_at/end_at) 충족.
     * 풀 전체를 셔플한 뒤 앞에서 {@code limit} 개를 잘라 반환하므로
     * 매 요청마다 다른 조합이 노출된다.</p>
     *
     * <p>limit 범위를 [1, 10] 으로 클램프하여 DB 부하를 방지한다.</p>
     *
     * @param limit 반환할 칩 수 (요청값이 범위를 벗어나면 클램프)
     * @return 활성 추천 칩 DTO 목록 (최대 limit 개, 풀이 부족하면 그보다 적을 수 있음)
     */
    public List<ChatSuggestionResponse> getActivePool(int limit) {
        // limit 클램프: [1, 10]
        int clampedLimit = Math.max(LIMIT_MIN, Math.min(LIMIT_MAX, limit));

        List<ChatSuggestion> pool = chatSuggestionRepository.findActiveAt(LocalDateTime.now());

        if (pool.isEmpty()) {
            log.debug("[ChatSuggestionService] 활성 추천 칩 풀이 비어 있음");
            return List.of();
        }

        // 매 요청마다 다른 조합을 제공하기 위해 풀을 셔플한다
        Collections.shuffle(pool);

        return pool.stream()
                .limit(clampedLimit)
                .map(ChatSuggestionResponse::from)
                .toList();
    }

    /**
     * 추천 칩 클릭을 트래킹한다 (fire-and-forget).
     *
     * <p>해당 칩이 존재하지 않으면 silent return (404 예외 없음).
     * DB 원자적 UPDATE 로 click_count = click_count + 1 을 실행한다.</p>
     *
     * @param id 클릭된 추천 칩 ID
     */
    @Transactional
    public void trackClick(Long id) {
        if (id == null) {
            log.debug("[ChatSuggestionService] trackClick — null id, 스킵");
            return;
        }
        if (!chatSuggestionRepository.existsById(id)) {
            log.debug("[ChatSuggestionService] trackClick — id={} 미존재, silent return", id);
            return;
        }
        chatSuggestionRepository.incrementClickCount(id);
        log.debug("[ChatSuggestionService] trackClick — id={} 클릭 수 +1", id);
    }

    // ─────────────────────────────────────────────
    // Admin API — 관리자 CRUD
    // ─────────────────────────────────────────────

    /**
     * 관리자용 추천 칩 목록을 페이지네이션으로 조회한다.
     *
     * @param isActive  활성 여부 필터 (null 이면 전체)
     * @param fromDate  생성일 시작 (inclusive, null 이면 하한 없음)
     * @param toDate    생성일 종료 (exclusive, null 이면 상한 없음)
     * @param pageable  페이지 정보
     * @return 필터링된 추천 칩 관리자 응답 페이지
     */
    public Page<AdminChatSuggestionResponse> getList(
            Boolean isActive,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable
    ) {
        return chatSuggestionRepository
                .findAdminFiltered(isActive, fromDate, toDate, pageable)
                .map(AdminChatSuggestionResponse::from);
    }

    /**
     * 추천 칩을 새로 생성한다.
     *
     * <p>현재 인증된 관리자의 userId 를 adminId 에 기록한다.
     * isActive / displayOrder 가 null 이면 기본값(false / 0)을 사용한다.</p>
     *
     * @param request 생성 요청 DTO
     * @return 생성된 추천 칩 관리자 응답 DTO
     */
    @Transactional
    public AdminChatSuggestionResponse create(ChatSuggestionRequest request) {
        String adminId = resolveCurrentAdminId();

        ChatSuggestion entity = ChatSuggestion.builder()
                .text(request.text())
                .category(request.category())
                .isActive(request.isActive() != null ? request.isActive() : false)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .adminId(adminId)
                .build();

        ChatSuggestion saved = chatSuggestionRepository.save(entity);
        log.info("[ChatSuggestionService] 추천 칩 생성 — id={}, text={}", saved.getSuggestionId(), saved.getText());
        return AdminChatSuggestionResponse.from(saved);
    }

    /**
     * 추천 칩 내용을 수정한다.
     *
     * @param id      수정 대상 추천 칩 ID
     * @param request 수정 요청 DTO
     * @return 수정된 추천 칩 관리자 응답 DTO
     * @throws BusinessException CHAT_SUGGESTION_NOT_FOUND — 해당 ID가 존재하지 않는 경우
     */
    @Transactional
    public AdminChatSuggestionResponse update(Long id, ChatSuggestionRequest request) {
        ChatSuggestion entity = findByIdOrThrow(id);
        entity.update(
                request.text(),
                request.category(),
                request.startAt(),
                request.endAt(),
                request.displayOrder()
        );
        // isActive 는 update() 도메인 메서드 범위 밖이므로 별도 처리
        if (request.isActive() != null) {
            if (Boolean.TRUE.equals(request.isActive())) {
                entity.activate();
            } else {
                entity.deactivate();
            }
        }
        log.info("[ChatSuggestionService] 추천 칩 수정 — id={}", id);
        return AdminChatSuggestionResponse.from(entity);
    }

    /**
     * 추천 칩을 삭제한다.
     *
     * @param id 삭제 대상 추천 칩 ID
     * @throws BusinessException CHAT_SUGGESTION_NOT_FOUND — 해당 ID가 존재하지 않는 경우
     */
    @Transactional
    public void delete(Long id) {
        ChatSuggestion entity = findByIdOrThrow(id);
        chatSuggestionRepository.delete(entity);
        log.info("[ChatSuggestionService] 추천 칩 삭제 — id={}", id);
    }

    /**
     * 추천 칩의 활성 여부를 토글한다.
     *
     * @param id       대상 추천 칩 ID
     * @param isActive 변경할 활성 여부
     * @return 변경된 추천 칩 관리자 응답 DTO
     * @throws BusinessException CHAT_SUGGESTION_NOT_FOUND — 해당 ID가 존재하지 않는 경우
     */
    @Transactional
    public AdminChatSuggestionResponse toggleActive(Long id, boolean isActive) {
        ChatSuggestion entity = findByIdOrThrow(id);
        if (isActive) {
            entity.activate();
        } else {
            entity.deactivate();
        }
        log.info("[ChatSuggestionService] 추천 칩 활성 토글 — id={}, isActive={}", id, isActive);
        return AdminChatSuggestionResponse.from(entity);
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    /**
     * ID로 추천 칩을 조회하거나, 없으면 BusinessException 을 던진다 (Admin 전용).
     *
     * @param id 조회할 추천 칩 ID
     * @return 조회된 ChatSuggestion 엔티티
     * @throws BusinessException CHAT_SUGGESTION_NOT_FOUND
     */
    private ChatSuggestion findByIdOrThrow(Long id) {
        return chatSuggestionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHAT_SUGGESTION_NOT_FOUND,
                        "추천 칩 id=" + id + " 를 찾을 수 없습니다"
                ));
    }

    /**
     * 현재 SecurityContext에서 관리자 userId 를 추출한다.
     * 인증 컨텍스트가 없으면 "system" 을 반환한다.
     *
     * @return 현재 관리자 userId
     */
    private String resolveCurrentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "system";
    }
}
