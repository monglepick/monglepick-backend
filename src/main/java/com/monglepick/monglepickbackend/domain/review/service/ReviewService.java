package com.monglepick.monglepickbackend.domain.review.service;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.review.repository.ReviewRepository;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 리뷰 서비스
 *
 * <p>영화 리뷰의 CRUD 비즈니스 로직을 처리합니다.
 * 중복 리뷰 방지 검증을 수행합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final RewardService rewardService;

    /**
     * 영화 리뷰를 작성합니다.
     *
     * <p>같은 사용자가 같은 영화에 중복 리뷰를 작성할 수 없습니다.</p>
     *
     * @param movieId 영화 ID
     * @param request 리뷰 작성 요청 (평점, 내용)
     * @param userId 작성자 ID
     * @return 생성된 리뷰 응답 DTO
     * @throws BusinessException 중복 리뷰인 경우
     */
    @Transactional
    public ReviewResponse createReview(String movieId, ReviewCreateRequest request, String userId) {
        // 1. 중복 리뷰 검사
        if (reviewRepository.existsByUser_UserIdAndMovieId(userId, movieId)) {
            log.warn("리뷰 작성 실패 - 중복 리뷰: userId={}, movieId={}", userId, movieId);
            throw new BusinessException(ErrorCode.DUPLICATE_REVIEW);
        }

        // 2. 작성자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. 리뷰 엔티티 생성 및 저장
        Review review = Review.builder()
                .user(user)
                .movieId(movieId)
                .rating(request.rating())
                .content(request.content())
                .build();

        Review savedReview = reviewRepository.save(review);
        log.info("리뷰 작성 완료 - reviewId: {}, userId: {}, movieId: {}",
                savedReview.getReviewId(), userId, movieId);  /* PK 필드명 변경: getId() → getReviewId() */

        // 리워드 지급 — 같은 영화 리뷰 1회만 지급 (reference_id = "movie_{movieId}")
        // RewardService 내부에서 중복 검사/정책 조회/포인트 지급을 모두 처리하므로 try-catch 불필요
        rewardService.grantReward(userId, "REVIEW_CREATE", "movie_" + movieId, request.content().length());

        return ReviewResponse.from(savedReview);
    }

    /**
     * 특정 영화의 리뷰 목록을 페이징으로 조회합니다.
     *
     * @param movieId  영화 ID
     * @param pageable 페이징 정보
     * @return 페이지 단위의 리뷰 목록
     */
    public Page<ReviewResponse> getReviewsByMovie(String movieId, Pageable pageable) {
        return reviewRepository.findByMovieId(movieId, pageable)
                .map(ReviewResponse::from);
    }

    /**
     * 리뷰를 삭제합니다. 작성자 본인만 삭제할 수 있습니다.
     *
     * @param reviewId 리뷰 ID
     * @param userId   요청자 ID (JWT에서 추출)
     * @throws BusinessException 리뷰 미존재(POST_NOT_FOUND) 또는 권한 없음(POST_ACCESS_DENIED)
     */
    @Transactional
    public void deleteReview(Long reviewId, String userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> {
                    log.warn("리뷰 삭제 실패 - 리뷰 없음: reviewId={}", reviewId);
                    return new BusinessException(ErrorCode.POST_NOT_FOUND);
                });

        /* 작성자 본인 확인 */
        if (!review.getUser().getUserId().equals(userId)) {
            log.warn("리뷰 삭제 실패 - 권한 없음: reviewId={}, 작성자={}, 요청자={}",
                    reviewId, review.getUser().getUserId(), userId);
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        reviewRepository.delete(review);
        log.info("리뷰 삭제 완료 - reviewId: {}, userId: {}", reviewId, userId);

        // 리워드 회수 — 리뷰 삭제 시 지급했던 포인트를 회수 (reference_id = "movie_{movieId}")
        // RewardService 내부에서 지급 이력 조회/회수 처리를 모두 수행하므로 try-catch 불필요
        rewardService.revokeReward(userId, "REVIEW_CREATE", "movie_" + review.getMovieId());
    }
}
