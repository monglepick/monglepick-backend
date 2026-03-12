package com.monglepick.monglepickbackend.domain.review.service;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.review.repository.ReviewRepository;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ReviewResponse createReview(Long movieId, ReviewCreateRequest request, Long userId) {
        // 1. 중복 리뷰 검사
        if (reviewRepository.existsByUserIdAndMovieId(userId, movieId)) {
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
                savedReview.getId(), userId, movieId);

        return ReviewResponse.from(savedReview);
    }

    /**
     * 특정 영화의 리뷰 목록을 조회합니다.
     *
     * @param movieId 영화 ID
     * @return 리뷰 목록
     */
    public List<ReviewResponse> getReviewsByMovie(Long movieId) {
        return reviewRepository.findByMovieId(movieId).stream()
                .map(ReviewResponse::from)
                .toList();
    }
}
