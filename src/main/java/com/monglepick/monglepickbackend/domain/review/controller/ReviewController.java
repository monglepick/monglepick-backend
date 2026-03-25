package com.monglepick.monglepickbackend.domain.review.controller;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 컨트롤러
 *
 * <p>영화 리뷰의 조회 및 작성 API를 제공합니다.
 * 리뷰 조회는 비로그인 사용자도 접근 가능하며,
 * 리뷰 작성은 인증이 필요합니다.</p>
 */
@Tag(name = "리뷰", description = "영화 리뷰 작성, 조회, 삭제")
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
    @Operation(summary = "영화별 리뷰 목록 조회", description = "특정 영화의 리뷰를 최신순으로 페이징 조회 (비로그인 허용)")
    @ApiResponse(responseCode = "200", description = "리뷰 목록 조회 성공")
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviewsByMovie(
            @PathVariable String movieId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByMovie(movieId, pageable);
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
    @Operation(summary = "리뷰 작성", description = "영화 리뷰 작성. 같은 영화에 중복 리뷰 불가")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "리뷰 작성 성공"),
            @ApiResponse(responseCode = "409", description = "이미 리뷰 작성됨")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable String movieId,
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 작성 요청 - userId: {}, movieId: {}", userId, movieId);
        ReviewResponse review = reviewService.createReview(movieId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }
}
