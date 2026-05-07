package com.monglepick.monglepickbackend.domain.community.controller;

import com.monglepick.monglepickbackend.domain.community.dto.PostCommentCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostCommentResponse;
import com.monglepick.monglepickbackend.domain.community.service.PostCommentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 댓글 컨트롤러.
 *
 * <p>커뮤니티 게시글 댓글의 작성·삭제·목록 조회 API를 제공한다.
 * 기본 경로: {@code /api/v1/posts/{postId}/comments}</p>
 *
 * <h3>인증 구분</h3>
 * <ul>
 *   <li>비인증: 댓글 목록 조회 (GET /)</li>
 *   <li>JWT 필요: 댓글 작성 (POST /), 댓글 삭제 (DELETE /{commentId})</li>
 * </ul>
 *
 * <h3>소프트 삭제</h3>
 * <p>삭제된 댓글은 is_deleted=true 플래그로 마스킹되며,
 * 응답에서 content가 "삭제된 댓글입니다"로 치환된다.</p>
 */
@Tag(name = "게시글 댓글", description = "커뮤니티 게시글 댓글 작성·삭제·목록 조회")
@Slf4j
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
public class PostCommentController {

    /** 게시글 댓글 서비스 */
    private final PostCommentService postCommentService;

    // ─────────────────────────────────────────────
    // 댓글 목록 조회 (비인증 허용)
    // ─────────────────────────────────────────────

    /**
     * 게시글 댓글 목록 조회 API (비로그인 허용).
     *
     * <p>소프트 삭제(is_deleted=true)된 댓글은 제외하고 반환한다.
     * 기본 정렬: 작성일시 오름차순 (오래된 댓글이 위에).</p>
     *
     * @param postId   게시글 ID (경로 변수)
     * @param pageable 페이징 정보 (기본: size=20, sort=createdAt ASC)
     * @return 댓글 페이지
     */
    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 페이징으로 조회합니다 (비로그인 허용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공"),
            @ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<Page<PostCommentResponse>> getComments(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        log.debug("[Comment API] 댓글 목록 조회: postId={}", postId);
        Page<PostCommentResponse> comments = postCommentService.getComments(postId, pageable);
        return ResponseEntity.ok(comments);
    }

    // ─────────────────────────────────────────────
    // 댓글 작성 (JWT 인증 필요)
    // ─────────────────────────────────────────────

    /**
     * 댓글 작성 API (JWT 인증 필요).
     *
     * <p>{@code parentCommentId}를 전달하면 대댓글로 저장된다 (1단계만 지원).
     * 작성 성공 시 COMMENT_CREATE 리워드가 자동 지급된다.</p>
     *
     * @param postId  게시글 ID (경로 변수)
     * @param request 댓글 작성 요청 DTO (content, parentCommentId)
     * @param userId  JWT에서 추출한 작성자 ID
     * @return 201 Created + 저장된 댓글 응답 DTO
     */
    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다. parentCommentId 전달 시 대댓글로 저장됩니다 (JWT 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "댓글 작성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (내용 없음 등)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public ResponseEntity<AchievementAwareResponse<PostCommentResponse>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody PostCommentCreateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("[Comment API] 댓글 작성 요청: postId={}, userId={}", postId, userId);
        AchievementAwareResponse<PostCommentResponse> response = postCommentService.createComment(userId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────
    // 댓글 좋아요 토글 (JWT 인증 필요)
    // ─────────────────────────────────────────────

    /**
     * 댓글 좋아요 토글 API (인스타그램 스타일, JWT 필수).
     *
     * <p>한 번 호출로 좋아요 등록/취소를 전환한다.
     * 좋아요가 없으면 INSERT, 있으면 hard DELETE 처리된다.
     * postId는 경로 일관성을 위해 포함되며, 실제 좋아요 처리는 commentId 기준으로 수행된다.</p>
     *
     * @param postId    게시글 ID (경로 일관성용)
     * @param commentId 좋아요 대상 댓글 ID
     * @param userId    JWT에서 추출한 사용자 ID
     * @return 200 OK + { liked, likeCount }
     */
    @Operation(summary = "댓글 좋아요 토글",
            description = "댓글 좋아요를 토글합니다 (인스타그램 스타일 — 한 번 클릭으로 등록/취소). JWT 필수.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 토글 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "댓글 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{commentId}/like")
    public ResponseEntity<LikeToggleResponse> toggleCommentLike(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal String userId) {

        log.info("[Comment Like] 댓글 좋아요 토글 — userId:{}, postId:{}, commentId:{}",
                userId, postId, commentId);
        LikeToggleResponse response = postCommentService.toggleCommentLike(userId, commentId);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // 댓글 삭제 (JWT 인증 필요)
    // ─────────────────────────────────────────────

    /**
     * 댓글 소프트 삭제 API (JWT 인증 필요, 작성자 본인만 가능).
     *
     * <p>실제 레코드는 삭제되지 않으며 is_deleted=true로 마스킹된다.
     * 이후 목록 조회 시 해당 댓글은 "삭제된 댓글입니다"로 표시된다.</p>
     *
     * @param postId    게시글 ID (경로 변수, 라우팅용 — 서비스에서는 commentId로 처리)
     * @param commentId 삭제할 댓글 ID
     * @param userId    JWT에서 추출한 요청자 ID
     * @return 204 No Content
     */
    @Operation(summary = "댓글 삭제", description = "댓글을 소프트 삭제합니다. 작성자 본인만 가능합니다 (JWT 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "댓글 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
            @ApiResponse(responseCode = "404", description = "댓글 없음"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal String userId) {

        log.info("[Comment API] 댓글 삭제 요청: postId={}, commentId={}, userId={}",
                postId, commentId, userId);
        postCommentService.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
