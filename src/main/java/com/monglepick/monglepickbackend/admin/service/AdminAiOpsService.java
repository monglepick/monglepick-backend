package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatStatsResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.QuizSummary;
import com.monglepick.monglepickbackend.admin.repository.AdminChatSessionRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminQuizRepository;
import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
// 본 프로젝트의 AdminStatsService 와 동일한 패키지 경로를 사용한다.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 AI 운영 서비스.
 *
 * <p>관리자 페이지 "AI 운영" 탭의 비즈니스 로직을 담당한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.2 AI 운영 범위.</p>
 *
 * <h3>담당 기능 (4개)</h3>
 * <ul>
 *   <li>퀴즈: 이력 조회 (1) — 생성(POST /quiz/generate)은 Agent 전담</li>
 *   <li>챗봇: 세션 목록 / 세션 메시지 / 통계 (3)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-08: AI 리뷰 생성/이력 기능 제거 (ai_generated 플래그 부재로 의미 없음)</li>
 *   <li>2026-04-08: {@code generateQuiz} 제거 — LLM 호출 없이 입력값만 INSERT 하던 dead code 였으며,
 *       Admin UI 는 agentApi 로 직접 호출하므로 Backend 경로는 단일 진실 원본 원칙에 따라 삭제</li>
 *   <li>2026-04-08: {@code getChatStats} 추가 — 챗봇 사용량 집계</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAiOpsService {

    /** 관리자 전용 퀴즈 리포지토리 — 페이징 + 상태 필터 */
    private final AdminQuizRepository adminQuizRepository;

    /** 관리자 전용 채팅 세션 리포지토리 */
    private final AdminChatSessionRepository adminChatSessionRepository;

    // ======================== 퀴즈 ========================

    /**
     * 퀴즈 이력을 최신순으로 페이징 조회한다.
     *
     * <p>status 파라미터가 null/공백이면 전체, 그 외에는 해당 상태만 필터링한다.</p>
     *
     * @param status   퀴즈 상태 문자열 (PENDING/APPROVED/REJECTED/PUBLISHED)
     * @param pageable 페이지 정보
     * @return 퀴즈 요약 페이지
     */
    /**
     * 퀴즈 이력을 복합 필터로 페이징 조회한다 — 2026-04-09 P1-⑫ 확장.
     *
     * <p>기존에는 {@code status} 하나만 필터링 가능하여 Frontend 에서 "현재 페이지 10건 내"
     * 클라이언트 사이드 필터로 movieId/keyword/날짜 범위를 적용하는 MVP 가 운영 중이었다.
     * 본 메서드는 Repository 의 {@code searchByFilters()} JPQL 을 호출하여 DB 전역에서
     * 복합 조건 검색을 수행한다. Frontend 는 동일 state 를 서버 파라미터로 전달만 하면 된다.</p>
     *
     * <h3>필터 파라미터</h3>
     * <ul>
     *   <li>{@code status}: 퀴즈 상태 enum (PENDING/APPROVED/REJECTED/PUBLISHED). 빈 값이면 전체</li>
     *   <li>{@code movieId}: 영화 ID 부분 일치 (대소문자 무시). 빈 값이면 전체</li>
     *   <li>{@code keyword}: 문제/해설/정답 본문 중 어느 하나에 부분 일치 (OR 조건, 대소문자 무시)</li>
     *   <li>{@code fromDate}: quizDate 시작 inclusive</li>
     *   <li>{@code toDate}: quizDate 종료 inclusive</li>
     * </ul>
     *
     * <p>빈 문자열 파라미터는 null 로 정규화하여 JPQL {@code :param IS NULL} 조건이
     * 반응하도록 한다 (Frontend 폼 리셋 시 빈 문자열 전달 패턴 수용).</p>
     *
     * @param status   상태 필터 문자열 (nullable/blank)
     * @param movieId  영화 ID 부분 일치 (nullable/blank)
     * @param keyword  본문 키워드 (nullable/blank)
     * @param fromDate 시작 quizDate (nullable)
     * @param toDate   종료 quizDate (nullable)
     * @param pageable 페이지 정보 (정렬은 Repository 에서 createdAt DESC 고정)
     * @return 필터링된 퀴즈 요약 페이지
     */
    public Page<QuizSummary> getQuizHistory(
            String status,
            String movieId,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        log.debug("[AdminAiOps] 퀴즈 이력 조회 — status={}, movieId={}, keyword={}, from={}, to={}, page={}",
                status, movieId, keyword, fromDate, toDate, pageable.getPageNumber());

        // status 문자열 → enum 변환 (빈 값은 null 로 정규화)
        Quiz.QuizStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = Quiz.QuizStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[AdminAiOps] 잘못된 퀴즈 상태 필터: {}", status);
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "허용되지 않은 퀴즈 상태: " + status);
            }
        }

        // 빈 문자열 필터는 null 로 정규화 — JPQL :param IS NULL 조건 활성화
        String normalizedMovieId = (movieId != null && !movieId.isBlank()) ? movieId.trim() : null;
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        return adminQuizRepository
                .searchByFilters(statusEnum, normalizedMovieId, normalizedKeyword, fromDate, toDate, pageable)
                .map(this::toQuizSummary);
    }

    // ======================== 챗봇 세션 ========================

    /**
     * 전체 채팅 세션 목록을 최신순으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 세션 요약 페이지 (소프트 삭제 제외)
     */
    public Page<ChatSessionSummary> getChatSessions(Pageable pageable) {
        log.debug("[AdminAiOps] 챗봇 세션 목록 조회 — page={}", pageable.getPageNumber());

        return adminChatSessionRepository
                .findByIsDeletedFalseOrderByLastMessageAtDesc(pageable)
                .map(this::toChatSessionSummary);
    }

    /**
     * 단일 채팅 세션의 메시지 상세를 조회한다.
     *
     * @param sessionId 세션 UUID
     * @return 세션 상세 응답 DTO
     * @throws BusinessException 세션 미발견 시
     */
    public ChatSessionDetail getChatSessionDetail(String sessionId) {
        log.debug("[AdminAiOps] 챗봇 세션 상세 조회 — sessionId={}", sessionId);

        ChatSessionArchive archive = adminChatSessionRepository
                .findBySessionIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> {
                    log.warn("[AdminAiOps] 챗봇 세션 상세 실패 — 미발견: sessionId={}", sessionId);
                    return new BusinessException(ErrorCode.INVALID_INPUT,
                            "채팅 세션을 찾을 수 없습니다: " + sessionId);
                });

        // ChatSessionArchive는 String FK 직접 보관 (JPA/MyBatis 하이브리드 §15.4)
        String userId = archive.getUserId();

        return new ChatSessionDetail(
                archive.getSessionId(),
                userId,
                archive.getTitle(),
                archive.getMessages(),
                archive.getSessionState(),
                archive.getIntentSummary(),
                archive.getTurnCount(),
                archive.getStartedAt(),
                archive.getLastMessageAt(),
                archive.getIsActive()
        );
    }

    // ======================== Task 4: 챗봇 통계 ========================

    /**
     * 챗봇 사용량 통계를 집계한다.
     *
     * <p>파라미터 from/to 는 ISO {@code yyyy-MM-dd} 날짜 문자열이며 생략 시 최근 7일 범위를 사용한다.
     * from 일자 00:00:00 부터 (to + 1일) 00:00:00 미만을 inclusive/exclusive 범위로 삼아
     * 자정 경계에서의 누락을 방지한다.</p>
     *
     * <h3>집계 항목</h3>
     * <ul>
     *   <li>totalSessions   — 해당 기간 생성된 전체 세션 수 (소프트 삭제 제외)</li>
     *   <li>activeSessions  — 해당 기간 생성된 세션 중 현재 is_active=true 수</li>
     *   <li>totalTurns      — 해당 기간 세션의 turn_count 합계</li>
     *   <li>topIntents      — 세션별 intent_summary JSON 을 파싱/합산한 뒤 상위 5개 의도명</li>
     * </ul>
     *
     * <p>빈 DB 환경에서도 0/빈 리스트를 반환하여 UI 가 에러 없이 "데이터 없음" 상태를 표시할 수 있게 한다.</p>
     *
     * @param from ISO 날짜 (yyyy-MM-dd) 또는 null (→ 오늘 -6일)
     * @param to   ISO 날짜 (yyyy-MM-dd) 또는 null (→ 오늘)
     * @return 챗봇 통계 응답 DTO
     */
    public ChatStatsResponse getChatStats(String from, String to) {
        // 1) from/to 파싱 및 기본값 보정 (생략 시 최근 7일)
        LocalDate toDate;
        LocalDate fromDate;
        try {
            toDate = (to == null || to.isBlank()) ? LocalDate.now() : LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            log.warn("[AdminAiOps] 잘못된 to 파라미터: {} — 오늘로 대체", to);
            toDate = LocalDate.now();
        }
        try {
            fromDate = (from == null || from.isBlank())
                    ? toDate.minusDays(6)
                    : LocalDate.parse(from);
        } catch (DateTimeParseException e) {
            log.warn("[AdminAiOps] 잘못된 from 파라미터: {} — 최근 7일로 대체", from);
            fromDate = toDate.minusDays(6);
        }

        // from > to 인 경우 두 값을 swap (방어적)
        if (fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }

        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime end = toDate.plusDays(1).atStartOfDay(); // exclusive

        log.debug("[AdminAiOps] 챗봇 통계 집계 — {} ~ {}", start, end);

        // 2) 단순 카운트 기반 집계
        long totalSessions = adminChatSessionRepository.countByCreatedAtBetween(start, end);
        long activeSessions = adminChatSessionRepository
                .countByIsActiveTrueAndIsDeletedFalseAndCreatedAtBetween(start, end);
        long totalTurns = adminChatSessionRepository.sumTurnCountByCreatedAtBetween(start, end);

        // 3) 상위 의도 집계 — intent_summary JSON 파싱 후 key 별로 합산
        List<String> topIntents = totalSessions == 0
                ? Collections.emptyList()
                : aggregateTopIntents(
                        adminChatSessionRepository.findIntentSummariesByCreatedAtBetween(start, end),
                        5
                );

        return new ChatStatsResponse(
                totalSessions,
                activeSessions,
                totalTurns,
                topIntents
        );
    }

    /** intent_summary 파싱 전용 ObjectMapper (스레드 안전, 싱글톤) */
    private static final ObjectMapper INTENT_OBJECT_MAPPER = new ObjectMapper();

    /**
     * intent_summary JSON 문자열 리스트에서 key 별 빈도를 합산하여 상위 N 개 key 를 반환한다.
     *
     * <p>intent_summary 는 {@code {"recommend": 3, "search": 1, "general": 2}} 형태로 저장된다.
     * 파싱 실패는 조용히 무시한다 (운영 안정성 > 완전성).</p>
     *
     * <p>Jackson 3.x: {@code JsonNode.fields()} (Iterator) 가 {@code properties()} (Set&lt;Map.Entry&gt;)
     * 로 변경되었으므로 AdminStatsService 와 동일한 패턴으로 순회한다.</p>
     *
     * @param summaries intent_summary JSON 문자열 리스트
     * @param limit     반환할 상위 개수
     * @return 상위 의도 key 리스트 (빈도 내림차순)
     */
    private List<String> aggregateTopIntents(List<String> summaries, int limit) {
        Map<String, Long> counter = new HashMap<>();

        for (String json : summaries) {
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode root = INTENT_OBJECT_MAPPER.readTree(json);
                if (!root.isObject()) {
                    continue;
                }
                // Jackson 3.x: properties() 사용
                for (var entry : root.properties()) {
                    JsonNode value = entry.getValue();
                    long add = value.isNumber() ? value.asLong() : 0L;
                    if (add > 0) {
                        counter.merge(entry.getKey(), add, Long::sum);
                    }
                }
            } catch (Exception ignore) {
                // 파싱 실패는 무시 — 통계는 근사치로 충분하다.
            }
        }

        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ======================== DTO 변환 ========================

    /**
     * {@link Quiz} → {@link QuizSummary} 응답 DTO.
     */
    private QuizSummary toQuizSummary(Quiz quiz) {
        return new QuizSummary(
                quiz.getQuizId(),
                quiz.getMovieId(),
                quiz.getQuestion(),
                quiz.getCorrectAnswer(),
                quiz.getOptions(),
                quiz.getRewardPoint(),
                quiz.getStatus().name(),
                quiz.getQuizDate() != null ? quiz.getQuizDate().toString() : null,
                quiz.getCreatedAt(),
                quiz.getUpdatedAt()
        );
    }

    /**
     * {@link ChatSessionArchive} → {@link ChatSessionSummary} 응답 DTO.
     *
     * <p>ChatSessionArchive 는 String FK 직접 보관 방식이므로 LAZY 프록시 없이
     * 곧바로 userId 를 읽는다 (JPA/MyBatis 하이브리드 §15.4).</p>
     */
    private ChatSessionSummary toChatSessionSummary(ChatSessionArchive archive) {
        String userId = archive.getUserId();
        return new ChatSessionSummary(
                archive.getChatSessionArchiveId(),
                archive.getSessionId(),
                userId,
                archive.getTitle(),
                archive.getTurnCount(),
                archive.getRecommendedMovieCount(),
                archive.getStartedAt(),
                archive.getLastMessageAt(),
                archive.getIsActive()
        );
    }
}
