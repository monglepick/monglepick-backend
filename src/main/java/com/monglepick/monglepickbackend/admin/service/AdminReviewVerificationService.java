package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReviewDecisionRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReviewVerificationDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReviewVerificationOverviewResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReviewVerificationSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminAiOpsDto.ReverifyResponse;
import com.monglepick.monglepickbackend.admin.repository.AdminCourseVerificationRepository;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseReview;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseVerification;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import com.monglepick.monglepickbackend.domain.roadmap.repository.CourseReviewRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.CourseVerificationRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.roadmap.service.ReviewVerificationAgentClient;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 "AI 운영 → 리뷰 인증" 탭 전용 서비스 — 2026-04-14 신규.
 *
 * <p>도장깨기 코스에서 사용자가 작성한 리뷰로 영화 시청을 인증하는 흐름을 관리자가
 * 모니터링/오버라이드하기 위한 비즈니스 로직을 담당한다. AI 리뷰 검증 에이전트(추후 개발)가
 * 계산한 유사도/신뢰도를 읽어 목록/상세 제공, 관리자 수동 승인/반려/재검증 요청까지 처리한다.</p>
 *
 * <h3>에이전트 미구현 현황</h3>
 * <p>현재 AI 리뷰 검증 에이전트는 계약만 정의되어 있고 실제 구현은 대기 중이다. 따라서 본 서비스는
 * 다음 전략을 적용한다:</p>
 * <ul>
 *   <li>에이전트가 {@code CourseVerification} 에 남기는 유사도/신뢰도/매칭 키워드는 null 일 수 있으며,
 *       UI 는 해당 컬럼을 "-" 또는 "판정 전" 배지로 표시한다.</li>
 *   <li>재검증 버튼({@code reverify}) 은 상태를 PENDING 으로 되돌릴 뿐이며, 에이전트 호출은 수행하지 않는다.
 *       {@link ReverifyResponse#agentAvailable()} 를 false 로 응답해 UI 가 "에이전트 준비 중" 배너를 띄운다.</li>
 *   <li>관리자 수동 승인/반려는 에이전트 구현 여부와 독립적으로 운영 가능해야 하므로 본 서비스가 단독으로 처리한다.</li>
 * </ul>
 *
 * <h3>감사 로그 연동</h3>
 * <p>승인/반려 액션은 {@link AdminAuditService} 를 통해 {@code admin_audit_logs} 에 기록한다.
 * 재검증 요청은 상태 복귀만 수행하므로 감사 로그 대상이 아니다 (운영 데이터 변화 없음).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReviewVerificationService {

    /** 리뷰 인증 전용 리포지토리 */
    private final AdminCourseVerificationRepository reviewVerificationRepository;

    /** 영화 제목/줄거리 조회용 */
    private final MovieRepository movieRepository;

    /** 관리자 감사 로그 서비스 (승인/반려 이벤트 기록) */
    private final AdminAuditService adminAuditService;

    /** 기존 CourseReview 백필용 — 도메인 리포지토리 */
    private final CourseReviewRepository courseReviewRepository;
    private final CourseVerificationRepository courseVerificationRepository;

    /** 관리자 판정 결과를 사용자 코스 진행률에 동기화 */
    private final UserCourseProgressRepository userCourseProgressRepository;

    /** AI 리뷰 검증 에이전트(FastAPI) 호출 클라이언트 */
    private final ReviewVerificationAgentClient agentClient;

    /**
     * AI 자동 승인 임계값 — application.yml {@code app.ai.review-verification.threshold}.
     *
     * <p>에이전트가 계산한 {@code aiConfidence} 가 이 값 이상이면 AUTO_VERIFIED 로 자동 처리될 예정이다.
     * 본 서비스는 직접 판정을 하지 않지만 관리자 화면의 임계값 안내 카드에 노출하기 위해 주입받는다.</p>
     */
    @Value("${app.ai.review-verification.threshold:0.7}")
    private float autoVerificationThreshold;

    // ─────────────────────────────────────────────
    // 감사 로그 액션/타겟 상수 — AdminAuditService 패턴을 따른다.
    // ─────────────────────────────────────────────

    /** 리뷰 인증 관리자 수동 승인 */
    private static final String ACTION_REVIEW_VERIFY_APPROVE = "REVIEW_VERIFY_APPROVE";
    /** 리뷰 인증 관리자 수동 반려 */
    private static final String ACTION_REVIEW_VERIFY_REJECT  = "REVIEW_VERIFY_REJECT";
    /** course_verification 엔티티 타겟 식별자 */
    private static final String TARGET_COURSE_VERIFICATION   = "COURSE_VERIFICATION";
    /** 미리보기 최대 길이 (목록 응답의 reviewPreview) */
    private static final int REVIEW_PREVIEW_MAX_LENGTH = 100;
    /** 관리자 판정 사유 최대 길이 (엔티티 컬럼 500 자 - 여유) */
    private static final int DECISION_REASON_MAX_LENGTH = 500;

    // ─────────────────────────────────────────────
    // 조회 (목록 / 상세 / KPI)
    // ─────────────────────────────────────────────

    /**
     * 리뷰 인증 목록을 복합 필터로 페이징 조회한다.
     *
     * <p>빈 문자열 파라미터는 null 로 정규화하여 JPQL {@code :param IS NULL} 조건이 활성화되게 한다.
     * toDate 는 {@code yyyy-MM-dd} 문자열로 받고 내부적으로 +1일의 자정으로 변환해 exclusive 처리한다
     * (자정 경계에서 누락되는 기록을 방지).</p>
     *
     * @param reviewStatus  리뷰 인증 상태 필터 (nullable/blank)
     * @param minConfidence 최소 aiConfidence (nullable)
     * @param userId        사용자 ID 부분 일치 (nullable/blank)
     * @param courseId      코스 ID 부분 일치 (nullable/blank)
     * @param fromDate      createdAt 시작 (ISO 날짜, nullable)
     * @param toDate        createdAt 종료 (ISO 날짜, inclusive — 내부에서 exclusive 로 변환)
     * @param pageable      페이지 정보
     * @return 필터링된 리뷰 인증 페이지
     */
    public Page<ReviewVerificationSummary> search(
            String reviewStatus,
            Float minConfidence,
            String userKeyword,
            String courseTitleKeyword,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        // 빈 문자열 → null 정규화 (JPQL IS NULL 조건 활성화)
        String status = (reviewStatus != null && !reviewStatus.isBlank()) ? reviewStatus.trim() : null;
        String uKw    = (userKeyword != null && !userKeyword.isBlank())             ? userKeyword.trim()       : null;
        String cKw    = (courseTitleKeyword != null && !courseTitleKeyword.isBlank()) ? courseTitleKeyword.trim() : null;

        // toDate 는 inclusive 로 받아 자정 경계 누락 방지 차원에서 +1일의 자정(exclusive) 으로 변환
        LocalDateTime from = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime to   = (toDate != null)   ? toDate.plusDays(1).atStartOfDay() : null;

        log.debug("[AdminReviewVerify] 목록 조회 — status={}, minConf={}, userKeyword={}, courseTitleKeyword={}, from={}, to={}, page={}",
                status, minConfidence, uKw, cKw, from, to, pageable.getPageNumber());

        return reviewVerificationRepository
                .searchReviewVerifications(status, minConfidence, uKw, cKw, from, to, pageable)
                .map(this::toSummary);
    }

    /**
     * 단일 리뷰 인증 상세를 조회한다. 영화 제목/줄거리 + 리뷰 본문을 함께 내려준다.
     *
     * @param verificationId course_verification PK
     * @return 상세 응답 DTO
     * @throws BusinessException COURSE_VERIFICATION_NOT_FOUND / NOT_REVIEW_VERIFICATION
     */
    public ReviewVerificationDetail getDetail(Long verificationId) {
        CourseVerification v = loadReviewVerification(verificationId);

        // 영화 메타 (제목/줄거리) — 존재 여부 불확실하므로 orElse(null) 패턴
        String movieTitle = null;
        String moviePlot  = null;
        Movie movie = movieRepository.findById(v.getMovieId()).orElse(null);
        if (movie != null) {
            movieTitle = movie.getTitle();
            moviePlot  = movie.getOverview();
        }

        // 리뷰 본문 — (userId, courseId, movieId) 복합키 단일 조회
        String reviewText = reviewVerificationRepository
                .findCourseReviewText(v.getUserId(), v.getCourseId(), v.getMovieId());

        return new ReviewVerificationDetail(
                v.getVerificationId(),
                v.getUserId(),
                v.getCourseId(),
                v.getMovieId(),
                movieTitle,
                moviePlot,
                reviewText,
                v.getSimilarityScore(),
                v.getAiConfidence(),
                v.getMatchedKeywords(),
                v.getReviewStatus(),
                v.getIsVerified(),
                v.getDecisionReason(),
                v.getReviewedBy(),
                v.getReviewedAt(),
                v.getVerifiedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }

    /**
     * 상단 KPI 카드용 집계를 반환한다.
     *
     * <p>상태별 COUNT 를 6번 호출한다. 단일 GROUP BY 쿼리로 바꿀 수도 있지만 상태 문자열이
     * 적고 관리자 화면 호출 빈도가 낮아 가독성을 우선한다.</p>
     *
     * @return KPI 집계 + 현재 임계값
     */
    public ReviewVerificationOverviewResponse getOverview() {
        long pending        = reviewVerificationRepository.countReviewByStatus("PENDING");
        long autoVerified   = reviewVerificationRepository.countReviewByStatus("AUTO_VERIFIED");
        long needsReview    = reviewVerificationRepository.countReviewByStatus("NEEDS_REVIEW");
        long autoRejected   = reviewVerificationRepository.countReviewByStatus("AUTO_REJECTED");
        long adminApproved  = reviewVerificationRepository.countReviewByStatus("ADMIN_APPROVED");
        long adminRejected  = reviewVerificationRepository.countReviewByStatus("ADMIN_REJECTED");

        return new ReviewVerificationOverviewResponse(
                pending,
                autoVerified,
                needsReview,
                autoRejected,
                adminApproved,
                adminRejected,
                autoVerificationThreshold
        );
    }

    // ─────────────────────────────────────────────
    // 쓰기 (수동 승인 / 반려 / 재검증)
    // ─────────────────────────────────────────────

    /**
     * 관리자가 리뷰 인증을 수동 승인한다.
     *
     * <p>상태를 ADMIN_APPROVED 로 전환하고 {@link CourseVerification#approveByAdmin} 도메인 메서드가
     * {@code is_verified=true} + {@code verified_at=now} 를 함께 세팅한다. 감사 로그는 REQUIRES_NEW 로
     * 별도 트랜잭션에서 기록된다.</p>
     *
     * @param verificationId 대상 인증 PK
     * @param request        승인 사유 (nullable)
     * @return 반영된 상세 응답
     */
    @Transactional
    public ReviewVerificationDetail approve(Long verificationId, ReviewDecisionRequest request) {
        CourseVerification v = loadReviewVerification(verificationId);
        String actor  = resolveCurrentActor();
        String reason = normalizeReason(request);
        String prevStatus = v.getReviewStatus();

        log.info("[AdminReviewVerify] 수동 승인 — id={}, actor={}, reason={}", verificationId, actor, reason);
        v.approveByAdmin(actor, reason);

        // ADMIN_REJECTED였던 경우 count가 이미 감소되어 있으므로 복원
        if ("ADMIN_REJECTED".equals(prevStatus)) {
            userCourseProgressRepository.findByUserIdAndCourseId(v.getUserId(), v.getCourseId())
                    .ifPresent(progress -> {
                        progress.verify();
                        userCourseProgressRepository.save(progress);
                        log.info("[AdminReviewVerify] 승인 → 진행률 복원: userId={}, courseId={}, verifiedMovies={}",
                                v.getUserId(), v.getCourseId(), progress.getVerifiedMovies());
                    });
        }

        // 감사 로그 — before/after 스냅샷은 상태/유저ID 수준만 가볍게
        adminAuditService.log(
                ACTION_REVIEW_VERIFY_APPROVE,
                TARGET_COURSE_VERIFICATION,
                String.valueOf(verificationId),
                String.format("리뷰 인증 승인 (userId=%s, courseId=%s, movieId=%s, reason=%s)",
                        v.getUserId(), v.getCourseId(), v.getMovieId(), reason)
        );

        return getDetail(verificationId);
    }

    /**
     * 관리자가 리뷰 인증을 수동 반려한다.
     *
     * <p>상태를 ADMIN_REJECTED 로 전환하고 {@code is_verified=false} 로 되돌린다. {@code verified_at}
     * 은 보존하여 "과거 승인되었다가 반려된 이력"을 추적 가능하게 한다.</p>
     *
     * @param verificationId 대상 인증 PK
     * @param request        반려 사유 (nullable)
     * @return 반영된 상세 응답
     */
    @Transactional
    public ReviewVerificationDetail reject(Long verificationId, ReviewDecisionRequest request) {
        CourseVerification v = loadReviewVerification(verificationId);
        String actor  = resolveCurrentActor();
        String reason = normalizeReason(request);
        String prevStatus = v.getReviewStatus();

        log.info("[AdminReviewVerify] 수동 반려 — id={}, actor={}, reason={}", verificationId, actor, reason);
        v.rejectByAdmin(actor, reason);

        // 이미 반려 상태가 아니었다면 카운트 감소
        if (!"ADMIN_REJECTED".equals(prevStatus)) {
            userCourseProgressRepository.findByUserIdAndCourseId(v.getUserId(), v.getCourseId())
                    .ifPresent(progress -> {
                        progress.unverify();
                        userCourseProgressRepository.save(progress);
                        log.info("[AdminReviewVerify] 반려 → 진행률 감소: userId={}, courseId={}, verifiedMovies={}",
                                v.getUserId(), v.getCourseId(), progress.getVerifiedMovies());
                    });
        }

        adminAuditService.log(
                ACTION_REVIEW_VERIFY_REJECT,
                TARGET_COURSE_VERIFICATION,
                String.valueOf(verificationId),
                String.format("리뷰 인증 반려 (userId=%s, courseId=%s, movieId=%s, reason=%s)",
                        v.getUserId(), v.getCourseId(), v.getMovieId(), reason)
        );

        return getDetail(verificationId);
    }

    /**
     * AI 에이전트에 재검증을 요청하고 판정 결과를 즉시 반영한다.
     *
     * <p>리뷰 본문 + 영화 줄거리를 수집한 뒤 FastAPI 에이전트
     * ({@code POST /api/v1/admin/ai/review-verification/verify})를 호출한다.
     * 에이전트 호출 성공 시 {@link CourseVerification#applyAiDecision()} 으로 결과를 반영하고
     * {@code agentAvailable=true} 로 응답한다. 에이전트 연결 실패 시 PENDING 으로만 복귀하고
     * {@code agentAvailable=false} 로 응답한다.</p>
     *
     * @param verificationId 대상 인증 PK
     * @return 재검증 응답 DTO
     */
    @Transactional
    public ReverifyResponse reverify(Long verificationId) {
        CourseVerification v = loadReviewVerification(verificationId);
        boolean wasVerified = Boolean.TRUE.equals(v.getIsVerified());

        // 리뷰 본문 수집
        String reviewText = reviewVerificationRepository
                .findCourseReviewText(v.getUserId(), v.getCourseId(), v.getMovieId());

        // 영화 줄거리 수집
        String moviePlot = null;
        Movie movie = movieRepository.findById(v.getMovieId()).orElse(null);
        if (movie != null) {
            moviePlot = movie.getOverview();
        }

        // course_review PK (에이전트 로깅용, 없으면 null)
        Long reviewId = courseReviewRepository
                .findByCourseIdAndMovieIdAndUserId(v.getCourseId(), v.getMovieId(), v.getUserId())
                .map(CourseReview::getCourseReviewId)
                .orElse(null);

        // 에이전트 호출
        try {
            log.info("[AdminReviewVerify] AI 재검증 에이전트 호출 — id={}, movieId={}",
                    verificationId, v.getMovieId());

            ReviewVerificationAgentClient.VerifyResponse agentResult = agentClient.verify(
                    verificationId,
                    v.getUserId(),
                    v.getCourseId(),
                    v.getMovieId(),
                    reviewId,
                    reviewText != null ? reviewText : "",
                    moviePlot != null ? moviePlot : ""
            );

            // 판정 결과 반영
            v.applyAiDecision(
                    agentResult.similarity_score(),
                    toJsonArray(agentResult.matched_keywords()),
                    agentResult.confidence(),
                    agentResult.review_status(),
                    agentResult.rationale()
            );

            // UserCourseProgress 동기화 — isVerified 변화에 따라 verifiedMovies 증감
            boolean isNowVerified = Boolean.TRUE.equals(v.getIsVerified());
            syncCourseProgress(v.getUserId(), v.getCourseId(), wasVerified, isNowVerified);

            log.info("[AdminReviewVerify] AI 재검증 완료 — id={}, status={}, confidence={}",
                    verificationId, agentResult.review_status(), agentResult.confidence());

            return new ReverifyResponse(
                    verificationId,
                    agentResult.review_status(),
                    true,
                    String.format("AI 재검증 완료: %s (신뢰도 %.2f)", agentResult.review_status(), agentResult.confidence())
            );

        } catch (ReviewVerificationAgentClient.AgentUnavailableException e) {
            // 에이전트 호출 실패 → PENDING 으로만 복귀
            log.warn("[AdminReviewVerify] 에이전트 호출 실패, PENDING 복귀 — id={}, error={}",
                    verificationId, e.getMessage());
            v.requestReverify();
            return new ReverifyResponse(
                    verificationId,
                    v.getReviewStatus(),
                    false,
                    "AI 에이전트 호출에 실패했습니다. 상태만 PENDING 으로 복귀했습니다."
            );
        }
    }

    /**
     * AI 판정 결과 변화에 따라 UserCourseProgress.verifiedMovies 를 동기화한다.
     *
     * <p>미인증 → 인증: verifiedMovies++. 인증 → 미인증: verifiedMovies--.</p>
     */
    private void syncCourseProgress(String userId, String courseId,
                                     boolean wasVerified, boolean isNowVerified) {
        if (wasVerified == isNowVerified) return;
        userCourseProgressRepository.findByUserIdAndCourseId(userId, courseId)
                .ifPresent(progress -> {
                    if (!wasVerified) {
                        progress.verify();
                        log.info("[AdminReviewVerify] 진행률 증가 — userId={}, courseId={}", userId, courseId);
                    } else {
                        progress.unverify();
                        log.info("[AdminReviewVerify] 진행률 감소 — userId={}, courseId={}", userId, courseId);
                    }
                    userCourseProgressRepository.save(progress);
                });
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    /**
     * ID 로 REVIEW 타입의 CourseVerification 을 로드한다. 미존재/타입 불일치 시 BusinessException 을 던진다.
     */
    private CourseVerification loadReviewVerification(Long verificationId) {
        CourseVerification v = reviewVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COURSE_VERIFICATION_NOT_FOUND,
                        "verificationId=" + verificationId));

        if (!"REVIEW".equals(v.getVerificationType())) {
            // QUIZ/IMAGE 인증 기록은 이 탭에서 조작 대상이 아니다.
            throw new BusinessException(ErrorCode.NOT_REVIEW_VERIFICATION,
                    "verificationType=" + v.getVerificationType());
        }
        return v;
    }

    /**
     * 엔티티 → 목록 응답 DTO 변환.
     *
     * <p>리뷰 본문 미리보기는 목록 엔드포인트가 (course_id, movie_id, user_id) 별 course_review 를
     * 추가 조회해야 하므로 N+1 비용이 발생한다. 관리자 페이지 단일 페이지당 최대 50건 수준이므로
     * MVP 에서는 단순 조회로 충분하다. 추후 부담이 커지면 한 번의 JOIN 쿼리로 DTO 프로젝션하도록
     * 리팩토링한다.</p>
     */
    private ReviewVerificationSummary toSummary(CourseVerification v) {
        String reviewText = reviewVerificationRepository
                .findCourseReviewText(v.getUserId(), v.getCourseId(), v.getMovieId());
        String preview     = truncate(reviewText, REVIEW_PREVIEW_MAX_LENGTH);
        String userNickname  = reviewVerificationRepository.findUserNicknameByUserId(v.getUserId());
        String courseTitle   = reviewVerificationRepository.findCourseTitleByCourseId(v.getCourseId());

        return new ReviewVerificationSummary(
                v.getVerificationId(),
                v.getUserId(),
                userNickname,
                v.getCourseId(),
                courseTitle,
                v.getMovieId(),
                preview,
                v.getSimilarityScore(),
                v.getAiConfidence(),
                v.getReviewStatus(),
                v.getIsVerified(),
                v.getReviewedBy(),
                v.getReviewedAt(),
                v.getCreatedAt()
        );
    }

    /** 요청 사유를 정규화한다 (null/공백 → "사유 미기재", 최대 길이 절단). */
    private String normalizeReason(ReviewDecisionRequest request) {
        String raw = (request != null && request.reason() != null) ? request.reason().trim() : "";
        if (raw.isEmpty()) {
            return "사유 미기재";
        }
        if (raw.length() > DECISION_REASON_MAX_LENGTH) {
            return raw.substring(0, DECISION_REASON_MAX_LENGTH - 3) + "...";
        }
        return raw;
    }

    /** List<String> → JSON 배열 문자열 변환. null/빈 리스트 → "[]". */
    private String toJsonArray(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"');
            sb.append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\""));
            sb.append('"');
        }
        sb.append("]");
        return sb.toString();
    }

    /** 문자열을 maxLen 기준으로 자르고 "..." 접미 추가. null 은 그대로 null 반환. */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /**
     * 기존 CourseReview 레코드 중 CourseVerification(REVIEW) 이 없는 것들을 PENDING 상태로 백필.
     *
     * <p>2026-04-20 이전에 저장된 리뷰는 course_verification 레코드가 없어 관리자 탭이 비어 보임.
     * 애플리케이션 시작 시 1회 실행되며 이미 레코드가 있는 행은 스킵(멱등성 보장).</p>
     */
    @EventListener(ApplicationStartedEvent.class)
    @Transactional
    public void backfillReviewVerifications() {
        List<CourseReview> allReviews = courseReviewRepository.findAll();
        if (allReviews.isEmpty()) return;

        List<CourseVerification> toSave = new ArrayList<>();
        for (CourseReview review : allReviews) {
            boolean exists = courseVerificationRepository
                    .findByUserIdAndCourseIdAndMovieId(
                            review.getUserId(), review.getCourseId(), review.getMovieId())
                    .isPresent();
            if (!exists) {
                toSave.add(CourseVerification.builder()
                        .userId(review.getUserId())
                        .courseId(review.getCourseId())
                        .movieId(review.getMovieId())
                        .verificationType("REVIEW")
                        .build());
            }
        }

        if (!toSave.isEmpty()) {
            reviewVerificationRepository.saveAll(toSave);
            log.info("[ReviewVerify] 기존 CourseReview 백필 완료 — {}건 생성", toSave.size());
        }
    }

    /**
     * 현재 인증된 관리자 user_id 를 반환한다.
     * {@link AdminAuditService#resolveCurrentActor()} 와 동일한 정책 (익명/미인증 → "SYSTEM").
     */
    private String resolveCurrentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return "SYSTEM";
            }
            return auth.getName();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
