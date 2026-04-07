package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ChatSessionSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateQuizRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateQuizResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateReviewRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.GenerateReviewResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.QuizSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReviewSummary;
import com.monglepick.monglepickbackend.admin.repository.AdminChatSessionRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminQuizRepository;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 관리자 AI 운영 서비스.
 *
 * <p>관리자 페이지 "AI 운영" 탭의 6개 기능에 대한 비즈니스 로직을 담당한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.2 AI 운영(6 API) 범위.</p>
 *
 * <h3>담당 기능</h3>
 * <ul>
 *   <li>퀴즈: 이력 조회 / 생성 트리거 (2)</li>
 *   <li>리뷰: 이력 조회 / 생성 트리거 (2)</li>
 *   <li>챗봇: 세션 목록 / 세션 메시지 (2)</li>
 * </ul>
 *
 * <h3>Agent 연동 상태</h3>
 * <p>현재 Agent FastAPI 에는 AI 퀴즈/리뷰 "전용 admin 생성 엔드포인트"가 없으므로,
 * 퀴즈 생성은 관리자가 직접 입력한 내용을 PENDING 상태로 INSERT 하는 폴백 경로를 사용하고,
 * 리뷰 생성은 501(NOT_IMPLEMENTED) 안내 응답을 반환한다.
 * 향후 Agent 쪽 엔드포인트가 추가되면 HTTP 호출로 전환한다 (TODO 주석 참조).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAiOpsService {

    /** 관리자 전용 퀴즈 리포지토리 — 페이징 + 상태 필터 */
    private final AdminQuizRepository adminQuizRepository;

    /** 관리자 전용 리뷰 리포지토리 — 동적 필터 (재사용) */
    /** 리뷰 통합 Mapper — AdminReviewRepository 폐기 (§15) */
    private final ReviewMapper reviewMapper;

    /** 관리자 전용 채팅 세션 리포지토리 */
    private final AdminChatSessionRepository adminChatSessionRepository;

    /** Phase 7 (2026-04-08): Agent 베이스 URL — AI 리뷰 생성 트리거 시 사용 */
    @Value("${admin.health.agent-url:http://localhost:8000}")
    private String agentUrl;

    /** Phase 7: Agent 호출용 RestClient (싱글턴) — 타임아웃 60초 (LLM 응답 대기) */
    private final RestClient agentRestClient = RestClient.builder()
            .requestFactory(
                    new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                        setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
                        setReadTimeout((int) Duration.ofSeconds(60).toMillis());
                    }}
            )
            .build();

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
    public Page<QuizSummary> getQuizHistory(String status, Pageable pageable) {
        log.debug("[AdminAiOps] 퀴즈 이력 조회 — status={}, page={}", status, pageable.getPageNumber());

        if (status != null && !status.isBlank()) {
            Quiz.QuizStatus statusEnum;
            try {
                statusEnum = Quiz.QuizStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[AdminAiOps] 잘못된 퀴즈 상태 필터: {}", status);
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "허용되지 않은 퀴즈 상태: " + status);
            }
            return adminQuizRepository.findByStatusOrderByCreatedAtDesc(statusEnum, pageable)
                    .map(this::toQuizSummary);
        }

        return adminQuizRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toQuizSummary);
    }

    /**
     * AI 퀴즈 생성을 트리거한다.
     *
     * <p>현재 구현에서는 Agent 라우터가 미구현이므로, 요청 파라미터를 그대로 받아
     * PENDING 상태의 Quiz 레코드를 직접 INSERT 한다. 관리자는 검수 후 approve() 로 전환한다.</p>
     *
     * <p>TODO: Agent 쪽 {@code POST /admin/ai/quiz/generate} 엔드포인트가 추가되면
     * 이 메서드를 HTTP 호출로 전환한다.</p>
     *
     * @param request 생성 요청 DTO (문제/정답/선택지 필수)
     * @return 생성 결과 응답 DTO
     */
    @Transactional
    public GenerateQuizResponse generateQuiz(GenerateQuizRequest request) {
        log.info("[AdminAiOps] 퀴즈 생성 요청 — movieId={}, question preview={}",
                request.movieId(),
                request.question().length() > 30 ? request.question().substring(0, 30) + "…" : request.question());

        Quiz quiz = Quiz.builder()
                .movieId(request.movieId())
                .question(request.question())
                .correctAnswer(request.correctAnswer())
                .options(request.options())
                .explanation(request.explanation())
                .rewardPoint(request.rewardPoint() != null ? request.rewardPoint() : 10)
                .status(Quiz.QuizStatus.PENDING)
                .build();

        Quiz saved = adminQuizRepository.save(quiz);
        log.info("[AdminAiOps] 퀴즈 생성 완료 — quizId={}", saved.getQuizId());

        return new GenerateQuizResponse(
                true,
                saved.getQuizId(),
                saved.getStatus().name(),
                "퀴즈가 PENDING 상태로 등록되었습니다. 검수 후 APPROVED 로 전환하세요."
        );
    }

    // ======================== 리뷰 ========================

    /**
     * 리뷰 이력을 최신순으로 페이징 조회한다.
     *
     * <p>현재 AI 생성 리뷰를 식별하는 플래그가 없으므로, 전체 리뷰 최신순으로 반환한다.
     * 향후 {@code reviews.is_ai_generated} 컬럼이 추가되면 해당 필터를 적용한다.</p>
     *
     * @param pageable 페이지 정보
     * @return 리뷰 요약 페이지
     */
    public Page<ReviewSummary> getReviewHistory(Pageable pageable) {
        log.debug("[AdminAiOps] 리뷰 이력 조회 — page={}", pageable.getPageNumber());

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        // 전체 리뷰 최신순 — MyBatis (§15)
        java.util.List<Review> reviews = reviewMapper.findAllAdminReviews(offset, limit);
        long total = reviewMapper.count();

        java.util.List<ReviewSummary> content = reviews.stream()
                .map(this::toReviewSummary)
                .toList();

        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * AI 리뷰 생성 트리거 (Phase 7 — 2026-04-08).
     *
     * <p>Agent FastAPI 의 {@code POST /api/v1/admin/ai/review/generate} 를 RestClient 로 호출하여
     * Explanation LLM 기반 리뷰 초안을 생성한다. 생성된 텍스트는 응답 메시지에 포함되며,
     * 실제 reviews 테이블 INSERT 는 관리자가 별도로 수행한다 (검수 후 게시 워크플로우).</p>
     *
     * <h4>요청 페이로드 (Agent)</h4>
     * <pre>{
     *   "movie_id": "...",
     *   "style": "neutral|critical|enthusiastic",
     *   "length": "short|medium|long"
     * }</pre>
     *
     * <h4>응답 페이로드</h4>
     * <pre>{ success, movie_id, review_text, word_count, style, length, message }</pre>
     *
     * @param request 생성 요청 DTO
     * @return 생성 결과 응답 DTO (success=true 시 message 에 리뷰 텍스트 포함)
     */
    public GenerateReviewResponse generateReview(GenerateReviewRequest request) {
        log.info("[AdminAiOps] 리뷰 생성 요청 — movieId={}, style={}, length={}",
                request.movieId(), request.style(), request.length());

        // Agent 호출 페이로드 (snake_case)
        Map<String, Object> body = Map.of(
                "movie_id", request.movieId(),
                "style", request.style() != null ? request.style() : "neutral",
                "length", request.length() != null ? request.length() : "medium"
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = agentRestClient.post()
                    .uri(agentUrl + "/api/v1/admin/ai/review/generate")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return new GenerateReviewResponse(false, "Agent 응답이 비어 있습니다.");
            }

            String reviewText = (String) response.getOrDefault("review_text", "");
            String agentMessage = (String) response.getOrDefault("message", "");
            Boolean success = (Boolean) response.getOrDefault("success", false);

            if (Boolean.TRUE.equals(success) && !reviewText.isBlank()) {
                log.info("[AdminAiOps] 리뷰 생성 완료 — movieId={}, length={}",
                        request.movieId(), reviewText.length());
                return new GenerateReviewResponse(true, reviewText);
            }

            return new GenerateReviewResponse(false,
                    agentMessage.isBlank() ? "Agent 가 빈 리뷰를 반환했습니다." : agentMessage);
        } catch (Exception e) {
            log.error("[AdminAiOps] 리뷰 생성 실패 — movieId={}, error={}",
                    request.movieId(), e.getMessage());
            return new GenerateReviewResponse(false,
                    "Agent 호출 실패: " + e.getMessage());
        }
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
     * {@link Review} → {@link ReviewSummary} 응답 DTO.
     *
     * <p>Review는 {@code String userId} 직접 보관 구조 (§15.4).</p>
     */
    private ReviewSummary toReviewSummary(Review review) {
        return new ReviewSummary(
                review.getReviewId(),
                review.getUserId(),
                review.getMovieId(),
                review.getRating(),
                review.getContent(),
                review.isDeleted(),
                review.isBlinded(),
                review.getCreatedAt()
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
