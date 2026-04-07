package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.ContentDto.PostResponse;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.PostUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ReportActionRequest;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ReportResponse;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ReviewResponse;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ToxicityActionRequest;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ToxicityResponse;
import com.monglepick.monglepickbackend.admin.service.AdminContentService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 콘텐츠 관리 API 컨트롤러.
 *
 * <p>이민수 담당 도메인(신고·혐오표현·게시글·리뷰) 관리자 API 9개를 제공한다.</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <pre>
 * GET    /api/v1/admin/reports               — 신고 목록 조회
 * PUT    /api/v1/admin/reports/{id}/action   — 신고 조치 (blind/delete/dismiss)
 * GET    /api/v1/admin/toxicity              — 혐오표현 로그 목록 조회
 * PUT    /api/v1/admin/toxicity/{id}/action  — 혐오표현 조치 (restore/delete/warn)
 * GET    /api/v1/admin/posts                 — 게시글 목록 조회 (키워드·카테고리·상태 필터)
 * PUT    /api/v1/admin/posts/{id}            — 게시글 수정
 * DELETE /api/v1/admin/posts/{id}            — 게시글 소프트 삭제
 * GET    /api/v1/admin/reviews               — 리뷰 목록 조회 (영화 ID·최소 평점 필터)
 * DELETE /api/v1/admin/reviews/{id}          — 리뷰 소프트 삭제
 * </pre>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 관리자 JWT 인증이 필요하다 (hasRole("ADMIN"), SecurityConfig에서 설정).</p>
 */
@Tag(name = "관리자 — 콘텐츠", description = "신고/혐오표현 관리, 게시글/리뷰 관리 (이민수 담당)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminContentService adminContentService;

    // ─────────────────────────────────────────────
    // 신고(Report) 관리 API
    // ─────────────────────────────────────────────

    /**
     * 신고 목록 조회.
     *
     * <p>{@code status} 파라미터로 처리 상태별 필터링이 가능하다.
     * 생략하면 전체 신고를 최신순으로 반환한다.</p>
     *
     * @param status 처리 상태 필터 (pending/reviewed/resolved/dismissed, 생략 시 전체)
     * @param page   페이지 번호 (0부터 시작, 기본값 0)
     * @param size   페이지 크기 (기본값 20)
     * @return 신고 목록 페이지
     */
    @Operation(
            summary = "신고 목록 조회",
            description = "처리 상태(pending/reviewed/resolved/dismissed)로 필터링 가능. 생략 시 전체 목록 반환"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> getReports(
            @Parameter(description = "처리 상태 필터 (pending/reviewed/resolved/dismissed)")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ReportResponse> result = adminContentService.getReports(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 신고 조치 처리.
     *
     * <p>신고 ID에 해당하는 건에 대해 blind·delete·dismiss 중 하나를 선택하여 처리한다.</p>
     *
     * @param id      신고 레코드 ID (post_declaration_id)
     * @param request 조치 요청 (action: blind/delete/dismiss)
     * @return 처리 완료 메시지
     */
    @Operation(
            summary = "신고 조치 처리",
            description = "blind: 게시글 블라인드 + 신고 reviewed, " +
                          "delete: 게시글 소프트 삭제 + 신고 reviewed, " +
                          "dismiss: 신고 기각(게시글 미처리)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조치 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 action"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신고 레코드 없음")
    })
    @PutMapping("/reports/{id}/action")
    public ResponseEntity<ApiResponse<String>> processReport(
            @Parameter(description = "신고 레코드 ID")
            @PathVariable Long id,
            @RequestBody ReportActionRequest request
    ) {
        adminContentService.processReport(id, request);
        return ResponseEntity.ok(ApiResponse.ok("신고 조치가 완료되었습니다."));
    }

    // ─────────────────────────────────────────────
    // 혐오표현(Toxicity) 관리 API
    // ─────────────────────────────────────────────

    /**
     * 혐오표현 로그 목록 조회.
     *
     * <p>{@code minScore} 파라미터로 독성 점수 임계값 이상의 로그만 필터링할 수 있다.
     * 생략하면 전체 혐오표현 로그를 최신순으로 반환한다.</p>
     *
     * @param minScore 최소 독성 점수 필터 (0.0~1.0, 생략 시 전체)
     * @param page     페이지 번호 (기본값 0)
     * @param size     페이지 크기 (기본값 20)
     * @return 혐오표현 로그 목록 페이지
     */
    @Operation(
            summary = "혐오표현 로그 목록 조회",
            description = "minScore로 독성 점수 임계값 이상 필터링 가능. " +
                          "예: minScore=0.6 → HIGH+CRITICAL 로그만 반환"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/toxicity")
    public ResponseEntity<ApiResponse<Page<ToxicityResponse>>> getToxicityLogs(
            @Parameter(description = "최소 독성 점수 (0.0~1.0, 생략 시 전체)")
            @RequestParam(required = false) Double minScore,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ToxicityResponse> result = adminContentService.getToxicityLogs(minScore, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 혐오표현 조치 처리.
     *
     * <p>혐오표현 로그 ID에 해당하는 건에 대해 restore·delete·warn 중 하나를 선택하여 처리한다.
     * {@link com.monglepick.monglepickbackend.domain.content.entity.ToxicityLog#processAction(String)}을
     * 통해 actionTaken과 processedAt이 동시에 기록된다.</p>
     *
     * @param id      혐오표현 로그 ID (toxicity_log_id)
     * @param request 조치 요청 (action: restore/delete/warn)
     * @return 처리 완료 메시지
     */
    @Operation(
            summary = "혐오표현 조치 처리",
            description = "restore: NONE으로 기록(복원), delete: DELETE로 기록, warn: WARN으로 기록(경고)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조치 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 action 또는 로그 없음")
    })
    @PutMapping("/toxicity/{id}/action")
    public ResponseEntity<ApiResponse<String>> processToxicity(
            @Parameter(description = "혐오표현 로그 ID")
            @PathVariable Long id,
            @RequestBody ToxicityActionRequest request
    ) {
        adminContentService.processToxicity(id, request);
        return ResponseEntity.ok(ApiResponse.ok("혐오표현 조치가 완료되었습니다."));
    }

    // ─────────────────────────────────────────────
    // 게시글(Post) 관리 API
    // ─────────────────────────────────────────────

    /**
     * 게시글 목록 조회.
     *
     * <p>키워드(제목·본문 LIKE)·카테고리·상태 조합 필터로 조회한다.
     * 파라미터를 생략하면 해당 조건 없이 전체를 조회한다.</p>
     *
     * @param keyword  검색어 (제목 또는 본문 LIKE, 생략 시 전체)
     * @param category 카테고리 (FREE/DISCUSSION/RECOMMENDATION/NEWS, 생략 시 전체)
     * @param status   게시 상태 (DRAFT/PUBLISHED, 생략 시 전체)
     * @param page     페이지 번호 (기본값 0)
     * @param size     페이지 크기 (기본값 20)
     * @return 게시글 목록 페이지
     */
    @Operation(
            summary = "게시글 목록 조회",
            description = "keyword(제목·본문 LIKE), category(FREE/DISCUSSION/RECOMMENDATION/NEWS), " +
                          "status(DRAFT/PUBLISHED) 조합 필터. 모두 생략 시 전체 조회"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getPosts(
            @Parameter(description = "검색어 (제목·본문 LIKE, 생략 시 전체)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "카테고리 필터 (FREE/DISCUSSION/RECOMMENDATION/NEWS)")
            @RequestParam(required = false) String category,
            @Parameter(description = "게시 상태 필터 (DRAFT/PUBLISHED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        // 게시글 목록은 최신순 정렬 고정
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostResponse> result = adminContentService.getPosts(keyword, category, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 게시글 수정 (관리자).
     *
     * <p>제목·본문·카테고리를 수정한다. null 필드는 기존 값을 유지한다.</p>
     *
     * @param id      수정 대상 게시글 ID
     * @param request 수정 요청 (title/content/category/editReason)
     * @return 수정된 게시글 응답 DTO
     */
    @Operation(
            summary = "게시글 수정 (관리자)",
            description = "제목·본문·카테고리 수정. null 필드는 기존 값 유지. editReason은 내부 로그용"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @PutMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @Parameter(description = "게시글 ID")
            @PathVariable Long id,
            @RequestBody PostUpdateRequest request
    ) {
        PostResponse result = adminContentService.updatePost(id, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 게시글 소프트 삭제 (관리자).
     *
     * <p>is_deleted=true, deleted_at=now()로 표시한다. 30일 후 스케줄러가 물리 삭제한다.</p>
     *
     * @param id 삭제 대상 게시글 ID
     * @return 삭제 완료 메시지
     */
    @Operation(
            summary = "게시글 소프트 삭제 (관리자)",
            description = "is_deleted=true 처리. 30일 후 스케줄러 물리 삭제. 복원 불가 주의"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<String>> deletePost(
            @Parameter(description = "게시글 ID")
            @PathVariable Long id
    ) {
        adminContentService.deletePost(id);
        return ResponseEntity.ok(ApiResponse.ok("게시글이 삭제되었습니다."));
    }

    // ─────────────────────────────────────────────
    // 리뷰(Review) 관리 API
    // ─────────────────────────────────────────────

    /**
     * 리뷰 목록 조회.
     *
     * <p>영화 ID·최소 평점 필터로 조회한다.
     * 파라미터를 생략하면 전체 리뷰를 반환한다.</p>
     *
     * <h3>도장깨기 인증 리뷰 모니터링</h3>
     * <p>{@code categoryCode="COURSE"}로 필터링하면 도장깨기(course) 단계 인증 리뷰만
     * 조회된다. 기타 카테고리: THEATER_RECEIPT/WORLDCUP/WISHLIST/AI_RECOMMEND/PLAYLIST.</p>
     *
     * @param movieId      영화 ID 필터 (생략 시 전체 영화)
     * @param minRating    최소 평점 필터 (1.0~5.0, 생략 시 무제한)
     * @param categoryCode 작성 카테고리 enum 이름 (COURSE/AI_RECOMMEND/... 생략 시 전체)
     * @param page         페이지 번호 (기본값 0)
     * @param size         페이지 크기 (기본값 20)
     * @return 리뷰 목록 페이지
     */
    @Operation(
            summary = "리뷰 목록 조회",
            description = "movieId / minRating / categoryCode 필터. categoryCode='COURSE'로 도장깨기 인증 리뷰 모니터링 가능"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getReviews(
            @Parameter(description = "영화 ID 필터 (생략 시 전체)")
            @RequestParam(required = false) String movieId,
            @Parameter(description = "최소 평점 필터 (1.0~5.0, 생략 시 무제한)")
            @RequestParam(required = false) Double minRating,
            @Parameter(description = "카테고리 enum 이름 필터 (THEATER_RECEIPT/COURSE/WORLDCUP/WISHLIST/AI_RECOMMEND/PLAYLIST, 생략 시 전체)")
            @RequestParam(required = false) String categoryCode,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReviewResponse> result = adminContentService.getReviews(
                movieId, minRating, categoryCode, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 리뷰 소프트 삭제 (관리자).
     *
     * <p>is_deleted=true로 표시한다.</p>
     *
     * @param id 삭제 대상 리뷰 ID
     * @return 삭제 완료 메시지
     */
    @Operation(
            summary = "리뷰 소프트 삭제 (관리자)",
            description = "is_deleted=true 처리. 부적절한 리뷰 제거 시 사용"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "리뷰 없음")
    })
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<String>> deleteReview(
            @Parameter(description = "리뷰 ID")
            @PathVariable Long id
    ) {
        adminContentService.deleteReview(id);
        return ResponseEntity.ok(ApiResponse.ok("리뷰가 삭제되었습니다."));
    }
}
