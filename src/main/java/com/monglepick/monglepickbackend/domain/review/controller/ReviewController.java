package com.monglepick.monglepickbackend.domain.review.controller;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 리뷰 컨트롤러
 *
 * <p>영화 리뷰의 조회 및 작성 API를 제공합니다.
 * 리뷰 조회는 비로그인 사용자도 접근 가능하며,
 * 리뷰 작성은 인증이 필요합니다.</p>
 *
 * <p>API 목록:</p>
 * <ul>
 *   <li>GET /api/v1/movies/{movieId}/reviews - 영화별 리뷰 목록 조회</li>
 *   <li>POST /api/v1/movies/{movieId}/reviews - 리뷰 작성 (인증 필요)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/movies/{movieId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 영화별 리뷰 목록 조회 API
     *
     * @param movieId 영화 ID
     * @return 200 OK + 리뷰 목록
     */
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> getReviewsByMovie(@PathVariable Long movieId) {
        List<ReviewResponse> reviews = reviewService.getReviewsByMovie(movieId);
        return ResponseEntity.ok(reviews);
    }

    /**
     * 리뷰 작성 API (인증 필요)
     *
     * <p>같은 사용자가 같은 영화에 중복 리뷰를 작성할 수 없습니다.</p>
     *
     * @param movieId 영화 ID
     * @param request 리뷰 작성 요청 (평점, 내용)
     * @param userId JWT에서 추출한 사용자 ID
     * @return 201 Created + 생성된 리뷰 정보
     */
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long movieId,
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal Long userId) {

        log.info("리뷰 작성 요청 - userId: {}, movieId: {}", userId, movieId);
        ReviewResponse review = reviewService.createReview(movieId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }
}
