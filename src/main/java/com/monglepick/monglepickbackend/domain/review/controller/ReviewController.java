package com.monglepick.monglepickbackend.domain.review.controller;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewCreateRequest;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewResponse;
import com.monglepick.monglepickbackend.domain.review.dto.ReviewUpdateRequest;
import com.monglepick.monglepickbackend.domain.review.service.ReviewService;
import com.monglepick.monglepickbackend.global.constants.AppConstants;
import com.monglepick.monglepickbackend.global.dto.AchievementAwareResponse;
import com.monglepick.monglepickbackend.global.dto.LikeToggleResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
        /* 페이지 크기 상한 제한 (대량 조회 DoS 방지) */
        int safeSize = Math.min(pageable.getPageSize(), AppConstants.MAX_PAGE_SIZE);
        Pageable safePage = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), safeSize, pageable.getSort());
        Page<ReviewResponse> reviews = reviewService.getReviewsByMovie(movieId, safePage);
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
    public ResponseEntity<AchievementAwareResponse<ReviewResponse>> createReview(
            @PathVariable String movieId,
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 작성 요청 - userId: {}, movieId: {}", userId, movieId);
        AchievementAwareResponse<ReviewResponse> response = reviewService.createReview(movieId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 리뷰 좋아요 토글 API (인스타그램 스타일, JWT 필수).
     *
     * <p>한 번 호출로 좋아요 등록/취소를 전환한다.
     * 좋아요가 없으면 INSERT, 있으면 hard DELETE 처리된다.
     * movieId 는 경로 일관성을 위해 포함되며, 실제 좋아요 처리는 reviewId 기준으로 수행된다.</p>
     *
     * @param movieId  영화 ID (경로 일관성용)
     * @param reviewId 좋아요 대상 리뷰 ID
     * @param userId   JWT에서 추출한 사용자 ID
     * @return 200 OK + { liked, likeCount }
     */
    @Operation(summary = "리뷰 좋아요 토글",
            description = "리뷰 좋아요를 토글합니다 (인스타그램 스타일 — 한 번 클릭으로 등록/취소). JWT 필수.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 토글 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{reviewId}/like")
    public ResponseEntity<LikeToggleResponse> toggleReviewLike(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 좋아요 토글 — userId:{}, movieId:{}, reviewId:{}", userId, movieId, reviewId);
        LikeToggleResponse response = reviewService.toggleReviewLike(userId, reviewId);
        return ResponseEntity.ok(response);
    }

    /**
     * 리뷰 수정 API (작성자만).
     *
     * <p>클라이언트 {@code reviewApi.updateReview(movieId, reviewId, { content, rating })}와
     * 1:1 대응된다. 요청 바디에는 수정할 평점과 내용만 포함되며,
     * 리뷰 식별자는 경로 변수로 전달된다.</p>
     *
     * <h3>응답 코드</h3>
     * <ul>
     *   <li>200 OK — 수정 성공 (변경된 리뷰 반환)</li>
     *   <li>403 — 작성자 본인이 아님</li>
     *   <li>404 — 리뷰 없음 또는 {@code movieId} 불일치</li>
     * </ul>
     *
     * @param movieId  영화 ID (경로 일관성용 — 실제 검증에도 사용)
     * @param reviewId 수정 대상 리뷰 ID
     * @param request  수정 요청 DTO (rating 필수, content nullable)
     * @param userId   JWT에서 추출한 사용자 ID
     * @return 200 OK + 수정된 리뷰 정보
     */
    @Operation(summary = "리뷰 수정", description = "리뷰 내용/평점 수정 (작성자 본인만 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리뷰 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (평점 범위 초과 등)"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음 또는 영화 불일치")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 수정 요청 - userId: {}, movieId: {}, reviewId: {}", userId, movieId, reviewId);
        ReviewResponse updated = reviewService.updateReview(movieId, reviewId, request, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 리뷰 삭제 API (작성자만)
     *
     * @param movieId  영화 ID
     * @param reviewId 리뷰 ID
     * @param userId   JWT에서 추출한 사용자 ID
     * @return 204 No Content
     */
    @Operation(summary = "리뷰 삭제", description = "리뷰 삭제 (작성자 본인만 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "리뷰 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 삭제 요청 - userId: {}, movieId: {}, reviewId: {}", userId, movieId, reviewId);
        reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.noContent().build();
    }
}
