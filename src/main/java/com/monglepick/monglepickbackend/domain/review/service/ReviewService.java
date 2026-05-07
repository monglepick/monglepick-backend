package com.monglepick.monglepickbackend.domain.review.service;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.domain.review.dto.MyReviewSummary;
import com.monglepick.monglepickbackend.domain.review.dto.MyReviewSummaryRow;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.RewardResult;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewUpdateRequest;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewCategoryCode;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewLike;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.roadmap.service.AchievementService;
import com.monglepick.monglepickbackend.domain.userwatchhistory.service.UserWatchHistoryService;
import com.monglepick.monglepickbackend.global.dto.AchievementAwareResponse;
import com.monglepick.monglepickbackend.global.dto.LikeToggleResponse;
import com.monglepick.monglepickbackend.global.dto.UnlockedAchievementResponse;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 리뷰 서비스
 *
 * <p>영화 리뷰의 CRUD + 좋아요 토글 비즈니스 로직을 처리한다.
 * JPA/MyBatis 하이브리드 §15에 따라 모든 데이터 접근은 {@link ReviewMapper}를 통해 이루어진다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    /** 리뷰/좋아요/투표 통합 Mapper */
    private final ReviewMapper reviewMapper;

    /** 리워드 서비스 */
    private final RewardService rewardService;

    /** 추천 임팩트 리포지토리 — 리뷰 작성 시 rated 플래그 업데이트 (윤형주 recommendation 도메인 유지) */
    private final RecommendationImpactRepository recommendationImpactRepository;

    /**
     * 추천 로그 리포지토리 — 추천 카드에서 리뷰 작성 시 movie_id/소유권 검증용
     * (recommendation_feedback 폐기 후 통합 경로, 2026-04-27).
     */
    private final RecommendationLogRepository recommendationLogRepository;

    /**
     * 시청 이력 서비스 — 리뷰 작성 시 user_watch_history 자동 동기화 (P0-2, 2026-04-24).
     *
     * <p>"봤다=리뷰" 부분 재정의 원칙(2026-04-08) 하에서 reviews(강한 신호) 와
     * user_watch_history(약한 신호) 가 별개 테이블로 운영되지만, 유저가 마이페이지를 거치지 않고
     * 바로 리뷰만 작성한 경우 user_watch_history 에 기록이 없어 마이페이지 시청 이력 UI 와
     * 정합성이 깨지는 문제가 있었다. 본 서비스의 {@code ensureWatchHistoryExists} 를
     * REQUIRES_NEW 트랜잭션으로 호출하여 정합성을 보장한다.</p>
     */
    private final UserWatchHistoryService userWatchHistoryService;

    /**
     * 포인트 이력 리포지토리 — 고객센터 봇 진단용 리뷰 목록에서 적립 여부 조회 (2026-04-28 추가).
     *
     * <p>리뷰 목록 조회 후 "movie_{movieId}" referenceId 기준 배치 IN 조회로
     * N+1 없이 적립 이력을 한 번에 매핑한다.</p>
     */
    private final PointsHistoryRepository pointsHistoryRepository;

    /** 업적 서비스 — review_count_10, genre_explorer 업적 달성 체크 */
    private final AchievementService achievementService;

    /**
     * 영화 리뷰를 작성한다. 같은 사용자가 같은 영화에 중복 리뷰를 작성할 수 없다.
     */
    @Transactional
    public AchievementAwareResponse<ReviewResponse> createReview(String movieId, ReviewCreateRequest request, String userId) {
        // 1. 중복 리뷰 검사
        if (reviewMapper.existsByUserIdAndMovieId(userId, movieId)) {
            log.warn("리뷰 작성 실패 - 중복 리뷰: userId={}, movieId={}", userId, movieId);
            throw new BusinessException(ErrorCode.DUPLICATE_REVIEW);
        }

        // 2. 사용자 존재 검증은 JWT 인증 단계에서 처리됨 (§15.4)

        // 3. 리뷰 엔티티 생성 및 저장
        Review review = Review.builder()
                .userId(userId)
                .movieId(movieId)
                .rating(request.rating())
                .content(request.content())
                .reviewSource(request.reviewSource())
                .reviewCategoryCode(request.reviewCategoryCode())
                .build();

        // MyBatis insert — useGeneratedKeys로 reviewId 자동 세팅
        reviewMapper.insert(review);
        log.info("리뷰 작성 완료 - reviewId: {}, userId: {}, movieId: {}, reviewSource: {}, reviewCategoryCode: {}",
                review.getReviewId(), userId, movieId,
                request.reviewSource(), request.reviewCategoryCode());

        // user_watch_history 자동 동기화 (P0-2, 2026-04-24)
        // — "봤다 = 리뷰" 정합성 확보. 마이페이지를 거치지 않고 리뷰만 작성한 경우에도
        //   시청 이력 UI 에 즉시 반영되도록 보장.
        // — REQUIRES_NEW 별도 트랜잭션이므로 실패해도 본 트랜잭션은 영향 없음.
        //   추가 안전장치로 try/catch 까지 감싸 어떠한 watch_history 측 오류도
        //   리뷰 작성 흐름을 막지 않도록 한다.
        try {
            userWatchHistoryService.ensureWatchHistoryExists(userId, movieId, "review_context");
        } catch (Exception watchSyncErr) {
            log.warn("리뷰 작성 후 watch_history 동기화 실패 - reviewId:{}, userId:{}, movieId:{}, err:{}",
                    review.getReviewId(), userId, movieId, watchSyncErr.getMessage());
        }

        // 리워드 지급 — 결과를 캡처하여 응답에 포함
        int contentLength = request.content() != null ? request.content().length() : 0;
        RewardResult rewardResult = rewardService.grantReward(userId, "REVIEW_CREATE", "movie_" + movieId, contentLength);

        // 첫 리뷰 작성 보너스 — INSERT 후 카운트가 1이면 첫 리뷰
        long reviewCount = reviewMapper.countByUserId(userId);
        if (reviewCount == 1) {
            RewardResult firstResult = rewardService.grantReward(userId, "FIRST_REVIEW", "first_review_" + userId, 0);
            // 첫 리뷰 보너스가 지급되면 합산하여 응답에 포함
            if (firstResult.earned()) {
                rewardResult = RewardResult.of(
                        rewardResult.points() + firstResult.points(),
                        rewardResult.policyName()
                );
            }
        }

        // review_count_10 업적 — 리뷰 10개 이상 달성 시 1회 지급
        List<UnlockedAchievementResponse> unlockedAchievements = new ArrayList<>();
        if (reviewCount >= 10) {
            try {
                achievementService.checkAndGrant(userId, "review_count_10", "default")
                        .ifPresent(unlockedAchievements::add);
            } catch (Exception e) {
                log.warn("review_count_10 업적 체크 실패 (리뷰 작성은 정상 처리): userId={}, error={}", userId, e.getMessage());
            }
        }

        // genre_explorer 업적 — 서로 다른 5개 장르 탐험 시 1회 지급
        try {
            long distinctGenreCount = reviewMapper.countDistinctExploredGenres(userId);
            if (distinctGenreCount >= 5) {
                achievementService.checkAndGrant(userId, "genre_explorer", "default")
                        .ifPresent(unlockedAchievements::add);
            }
        } catch (Exception e) {
            log.warn("genre_explorer 업적 체크 실패 (리뷰 작성은 정상 처리): userId={}, error={}", userId, e.getMessage());
        }

        // recommendation_impact.rated 업데이트 (퍼널 완성)
        // 윤형주 recommendation 도메인은 JPA 유지 — dirty checking 정상 동작
        List<RecommendationImpact> impacts =
                recommendationImpactRepository.findByUserIdAndMovieId(userId, movieId);
        if (!impacts.isEmpty()) {
            impacts.forEach(RecommendationImpact::markRated);
            log.debug("recommendation_impact.rated 업데이트 — userId:{}, movieId:{}, 건수:{}",
                    userId, movieId, impacts.size());
        }

        // 리워드 지급 포인트를 응답에 포함 (earned=true일 때만 포인트 표시)
        Integer rewardPoints = rewardResult.earned() ? rewardResult.points() : null;
        return AchievementAwareResponse.of(ReviewResponse.from(review, rewardPoints), unlockedAchievements);
    }

    /**
     * 추천 카드에서 별점/코멘트를 제출하면 reviews 테이블에 UPSERT 한다 (2026-04-27 신설).
     *
     * <p>"봤다 = 리뷰" 단일 진실 원본 원칙(CLAUDE.md)에 따라, 추천 내역 페이지의 별점 제출은
     * 더 이상 {@code recommendation_feedback} 으로 가지 않고 본 메서드를 통해 reviews 테이블에
     * 저장된다. 추천 카드는 같은 추천에 대해 별점을 여러 번 갱신할 수 있으므로
     * (user_id, movie_id) 활성 리뷰가 있으면 update, 없으면 create 로 동작한다.</p>
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>recommendation_log 소유권 검증 (recommendationLogId × userId) — 없으면 REC001 (404)</li>
     *   <li>movie_id 추출 (JOIN FETCH 된 movie)</li>
     *   <li>활성 리뷰(is_deleted=false) 조회</li>
     *   <li>있으면 update — rating/contents 갱신, reward 미지급 (이미 부여 받은 리뷰)</li>
     *   <li>없으면 create — {@link #createReview(String, ReviewCreateRequest, String)} 위임 (reward + watch_history + impact.rated 자동 처리)</li>
     * </ol>
     *
     * <p>reviewSource = {@code "rec_log_{logId}"}, reviewCategoryCode = {@link ReviewCategoryCode#AI_RECOMMEND}
     * 으로 강제 세팅하여 리뷰의 출처가 "AI 추천 카드" 임을 명시한다.</p>
     *
     * @param userId              JWT 에서 추출한 사용자 ID
     * @param recommendationLogId 평가 대상 추천 로그 ID
     * @param rating              별점 (0.5 ~ 5.0)
     * @param content             리뷰 본문 (선택, null/빈 문자열 허용)
     * @return 저장된 리뷰 응답 DTO (신규 작성 시 reward 포인트 포함, update 시 null)
     * @throws BusinessException RECOMMENDATION_LOG_NOT_FOUND — 추천 로그가 없거나 본인 로그가 아닐 때
     */
    @Transactional
    public ReviewResponse createOrUpdateFromRecommendation(
            String userId,
            Long recommendationLogId,
            Double rating,
            String content) {

        // 1) 추천 로그 소유권 검증 + movie_id 획득 (movie JOIN FETCH 포함)
        RecommendationLog recLog = recommendationLogRepository
                .findByRecommendationLogIdAndUserId(recommendationLogId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECOMMENDATION_LOG_NOT_FOUND,
                        "추천 이력을 찾을 수 없습니다: recommendationLogId=" + recommendationLogId));
        String movieId = recLog.getMovie().getMovieId();

        // 2) 활성 리뷰 단건 조회 — UPSERT 분기
        Review existing = reviewMapper.findByUserIdAndMovieId(userId, movieId);

        if (existing != null) {
            // 2-a) 기존 리뷰 update — content 는 null 허용 (별점만 갱신 케이스 지원)
            existing.update(rating, content);
            reviewMapper.update(existing);
            log.info("추천 카드 리뷰 update — reviewId:{}, userId:{}, movieId:{}, recLogId:{}",
                    existing.getReviewId(), userId, movieId, recommendationLogId);

            // recommendation_impact.rated 마킹 — 기존 리뷰가 있어도 funnel 지표 정합성 유지
            recommendationImpactRepository.findByUserIdAndMovieId(userId, movieId)
                    .forEach(RecommendationImpact::markRated);

            return ReviewResponse.from(existing);
        }

        // 2-b) 신규 리뷰 — createReview 에 위임 (reward + watch_history + impact.rated 일괄 처리)
        ReviewCreateRequest createRequest = new ReviewCreateRequest(
                movieId,
                rating,
                content,
                "rec_log_" + recommendationLogId,
                ReviewCategoryCode.AI_RECOMMEND
        );
        log.info("추천 카드 리뷰 신규 작성 위임 — userId:{}, movieId:{}, recLogId:{}",
                userId, movieId, recommendationLogId);
        return createReview(movieId, createRequest, userId).data();
    }

    /**
     * 특정 영화의 리뷰 목록을 페이징으로 조회한다 (닉네임 포함).
     */
    public Page<ReviewResponse> getReviewsByMovie(String movieId, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<Review> reviews = reviewMapper.findByMovieIdWithNickname(movieId, offset, limit);
        long total = reviewMapper.countByMovieId(movieId);

        List<ReviewResponse> content = reviews.stream().map(ReviewResponse::from).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 리뷰 좋아요 토글 (인스타그램 스타일).
     */
    @Transactional
    public LikeToggleResponse toggleReviewLike(String userId, Long reviewId) {
        ReviewLike existing = reviewMapper.findReviewLikeByReviewIdAndUserId(reviewId, userId);
        boolean liked;

        if (existing != null) {
            /* 좋아요 취소 — hard-delete */
            reviewMapper.deleteReviewLikeByReviewIdAndUserId(reviewId, userId);
            liked = false;
        } else {
            /* 좋아요 등록 — INSERT, race condition 처리 */
            try {
                reviewMapper.insertReviewLike(
                        ReviewLike.builder()
                                .reviewId(reviewId)
                                .userId(userId)
                                .build()
                );
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("리뷰 좋아요 중복 INSERT 감지 (race condition) — userId:{}, reviewId:{}", userId, reviewId);
                reviewMapper.deleteReviewLikeByReviewIdAndUserId(reviewId, userId);
                long count = reviewMapper.countReviewLikeByReviewId(reviewId);
                return LikeToggleResponse.of(false, count);
            }
            liked = true;
        }

        long count = reviewMapper.countReviewLikeByReviewId(reviewId);
        log.debug("리뷰 좋아요 토글 — userId:{}, reviewId:{}, liked:{}, count:{}", userId, reviewId, liked, count);

        return LikeToggleResponse.of(liked, count);
    }

    /**
     * 리뷰 좋아요 수 조회 (비로그인 허용).
     */
    public long getReviewLikeCount(Long reviewId) {
        return reviewMapper.countReviewLikeByReviewId(reviewId);
    }

    /**
     * 리뷰 내용 및 평점을 수정한다. 작성자 본인만 수정 가능.
     */
    @Transactional
    public ReviewResponse updateReview(String movieId,
                                       Long reviewId,
                                       ReviewUpdateRequest request,
                                       String userId) {
        Review review = reviewMapper.findById(reviewId);
        if (review == null) {
            log.warn("리뷰 수정 실패 - 리뷰 없음: reviewId={}", reviewId);
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 경로 변수 movieId와 실제 리뷰의 movieId 일치 검증 (존재 정보 유출 방지를 위해 404)
        if (!review.getMovieId().equals(movieId)) {
            log.warn("리뷰 수정 실패 - 경로 movieId 불일치: path={}, review={}",
                    movieId, review.getMovieId());
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 작성자 본인 확인 (String FK 직접 비교)
        if (!review.getUserId().equals(userId)) {
            log.warn("리뷰 수정 실패 - 권한 없음: reviewId={}, 작성자={}, 요청자={}",
                    reviewId, review.getUserId(), userId);
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        // 도메인 메서드 + 명시 UPDATE (dirty checking 미지원)
        review.update(request.rating(), request.content());
        reviewMapper.update(review);

        log.info("리뷰 수정 완료 - reviewId: {}, userId: {}, movieId: {}", reviewId, userId, movieId);

        return ReviewResponse.from(review);
    }

    /**
     * 리뷰를 삭제한다. 작성자 본인만 삭제 가능.
     */
    @Transactional
    public void deleteReview(Long reviewId, String userId) {
        Review review = reviewMapper.findById(reviewId);
        if (review == null) {
            log.warn("리뷰 삭제 실패 - 리뷰 없음: reviewId={}", reviewId);
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!review.getUserId().equals(userId)) {
            log.warn("리뷰 삭제 실패 - 권한 없음: reviewId={}, 작성자={}, 요청자={}",
                    reviewId, review.getUserId(), userId);
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        reviewMapper.deleteById(reviewId);
        log.info("리뷰 삭제 완료 - reviewId: {}, userId: {}", reviewId, userId);

        // 리워드 회수
        rewardService.revokeReward(userId, "REVIEW_CREATE", "movie_" + review.getMovieId());
    }

    // ──────────────────────────────────────────────
    // 내 리뷰 목록 조회 — 고객센터 AI 봇 진단용 (v4 신규, 2026-04-28)
    // ──────────────────────────────────────────────

    /**
     * 사용자 본인의 리뷰 목록을 날짜 필터 + 페이징으로 조회한다.
     *
     * <p>고객센터 지원 봇(support_assistant v4)이 "리뷰 작성했는데 포인트 안 들어와요" 류의
     * 발화를 진단할 때 {@code GET /api/v1/users/me/reviews} 를 통해 호출한다.</p>
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>ReviewMapper.findMyReviewsWithTitle — reviews LEFT JOIN movies 페이징 조회</li>
     *   <li>ReviewMapper.countMyReviewsSince — 총 건수 조회 (Page 생성용)</li>
     *   <li>PointsHistoryRepository.findByUserIdAndActionTypeAndReferenceIdIn —
     *       "REVIEW_CREATE" 이력을 referenceId IN 절 배치 조회 (N+1 방지)</li>
     *   <li>Map&lt;referenceId, createdAt&gt; 으로 변환 후 MyReviewSummary.of() 로 DTO 조립</li>
     * </ol>
     *
     * <h3>pointAwarded 판정 기준</h3>
     * <p>points_history 테이블에서 action_type='REVIEW_CREATE', reference_id='movie_{movieId}'
     * 인 이력이 존재하면 {@code pointAwarded=true}, 없으면 {@code null}.
     * 포인트 정책 변경 전 작성된 리뷰는 이력이 없어 {@code null} 이 될 수 있다.</p>
     *
     * @param userId  사용자 ID (JWT 강제 주입, BOLA 방지)
     * @param days    최근 N일 필터 (기본 30, 최대 365)
     * @param pageable 페이징 정보 (기본 size=20, created_at DESC)
     * @return 내 리뷰 요약 페이지 (MyReviewSummary)
     */
    public Page<MyReviewSummary> getMyReviews(String userId, int days, Pageable pageable) {
        // 1. days 범위 방어 (최소 1, 최대 365)
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        log.debug("내 리뷰 목록 조회: userId={}, days={}, offset={}, limit={}", userId, safeDays, offset, limit);

        // 2. reviews LEFT JOIN movies — 영화 제목 포함 페이징 조회
        List<MyReviewSummaryRow> rows = reviewMapper.findMyReviewsWithTitle(userId, since, offset, limit);

        // 3. 총 건수 (Page 메타 계산용)
        long total = reviewMapper.countMyReviewsSince(userId, since);

        if (rows.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        // 4. 포인트 적립 이력 배치 조회 — referenceId = "movie_{movieId}" 형식
        //    N+1 방지: 단일 IN 쿼리로 현재 페이지 전체를 한 번에 조회
        List<String> referenceIds = rows.stream()
                .map(r -> "movie_" + r.getMovieId())
                .distinct()
                .collect(Collectors.toList());

        List<PointsHistory> histories = pointsHistoryRepository
                .findByUserIdAndActionTypeAndReferenceIdIn(userId, "REVIEW_CREATE", referenceIds);

        // 5. referenceId → createdAt Map 변환
        //    같은 referenceId 가 여러 건이면 가장 최신 이력 1건을 남긴다 (정합성 복구 상황 대비)
        Map<String, LocalDateTime> awardMap = histories.stream()
                .collect(Collectors.toMap(
                        PointsHistory::getReferenceId,
                        PointsHistory::getCreatedAt,
                        (a, b) -> a.isAfter(b) ? a : b   // 중복 시 더 최신 항목 유지
                ));

        // 6. DTO 조립
        List<MyReviewSummary> content = rows.stream()
                .map(row -> {
                    String refId = "movie_" + row.getMovieId();
                    LocalDateTime awardedAt = awardMap.get(refId);
                    // MyReviewSummaryRow → Review-like 구조를 MyReviewSummary.of()로 조립
                    // 포인트 이력은 awardedAt(null 가능)으로 전달
                    return new MyReviewSummary(
                            row.getReviewId(),
                            row.getMovieId(),
                            row.getMovieTitle(),
                            row.getRating(),
                            // 본문 100자 미리보기
                            row.getContent() != null && row.getContent().length() > 100
                                    ? row.getContent().substring(0, 100)
                                    : row.getContent(),
                            // createdAt → KST OffsetDateTime
                            row.getCreatedAt() != null
                                    ? row.getCreatedAt()
                                        .atZone(java.time.ZoneId.of("Asia/Seoul"))
                                        .toOffsetDateTime()
                                    : null,
                            // pointAwarded: 이력 존재 시 true, 없으면 null (미확인)
                            awardedAt != null ? Boolean.TRUE : null,
                            // pointAwardedAt → KST OffsetDateTime
                            awardedAt != null
                                    ? awardedAt.atZone(java.time.ZoneId.of("Asia/Seoul"))
                                        .toOffsetDateTime()
                                    : null
                    );
                })
                .collect(Collectors.toList());

        log.debug("내 리뷰 목록 조회 완료: userId={}, 조회건수={}, 총건수={}", userId, content.size(), total);
        return new PageImpl<>(content, pageable, total);
    }
}
