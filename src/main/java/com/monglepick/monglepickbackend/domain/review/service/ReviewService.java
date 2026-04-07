package com.monglepick.monglepickbackend.domain.review.service;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewUpdateRequest;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewLike;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.dto.LikeToggleResponse;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
     * 영화 리뷰를 작성한다. 같은 사용자가 같은 영화에 중복 리뷰를 작성할 수 없다.
     */
    @Transactional
    public ReviewResponse createReview(String movieId, ReviewCreateRequest request, String userId) {
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

        // 리워드 지급
        int contentLength = request.content() != null ? request.content().length() : 0;
        rewardService.grantReward(userId, "REVIEW_CREATE", "movie_" + movieId, contentLength);

        // recommendation_impact.rated 업데이트 (퍼널 완성)
        // 윤형주 recommendation 도메인은 JPA 유지 — dirty checking 정상 동작
        List<RecommendationImpact> impacts =
                recommendationImpactRepository.findByUserIdAndMovieId(userId, movieId);
        if (!impacts.isEmpty()) {
            impacts.forEach(RecommendationImpact::markRated);
            log.debug("recommendation_impact.rated 업데이트 — userId:{}, movieId:{}, 건수:{}",
                    userId, movieId, impacts.size());
        }

        return ReviewResponse.from(review);
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
}
