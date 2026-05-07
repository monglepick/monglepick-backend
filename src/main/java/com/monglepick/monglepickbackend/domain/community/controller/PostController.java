package com.monglepick.monglepickbackend.domain.community.controller;

import com.monglepick.monglepickbackend.domain.community.dto.PostCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostReportRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostResponse;
import com.monglepick.monglepickbackend.domain.community.service.PostService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 컨트롤러
 *
 * <p>커뮤니티 게시글의 CRUD + 임시저장(Draft) API를 제공합니다.</p>
 */
@Tag(name = "커뮤니티", description = "게시글 CRUD, 임시저장, 좋아요")
@Slf4j
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ──────────────────────────────────────────────
    // 게시글 CRUD (기존 기능)
    // ──────────────────────────────────────────────

    /**
     * 게시글 목록 조회 API (비로그인 허용)
     */
    @Operation(summary = "게시글 목록 조회", description = "PUBLISHED 상태 게시글 목록. 카테고리 필터링 가능 (비로그인 허용)")
    @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal String userId) { // 비로그인 시 null — PLAYLIST_SHARE 좋아요 여부 판별용

        /* 페이지 크기 상한 제한 (대량 조회 DoS 방지) */
        int safeSize = Math.min(pageable.getPageSize(), AppConstants.MAX_PAGE_SIZE);
        Pageable safePage = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), safeSize, pageable.getSort());

        Page<PostResponse> posts = postService.getPosts(category, keyword, sort, safePage, userId);
        return ResponseEntity.ok(posts);
    }

    /**
     * 게시글 상세 조회 API (조회수 1 증가)
     */
    @Operation(summary = "게시글 상세 조회", description = "게시글 상세 정보 + 조회수 1 증가 (비로그인 허용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시글 조회 성공"),
            @ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @SecurityRequirement(name = "")
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        PostResponse post = postService.getPost(id);
        return ResponseEntity.ok(post);
    }

    /**
     * 게시글 작성 API (인증 필요)
     */
    @Operation(summary = "게시글 작성", description = "새 게시글 작성 (PUBLISHED 상태로 즉시 게시)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "게시글 작성 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public ResponseEntity<AchievementAwareResponse<PostResponse>> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("게시글 작성 요청 — userId: {}, category: {}", userId, request.category());
        AchievementAwareResponse<PostResponse> response = postService.createPost(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 게시글 수정 API (작성자만)
     */
    @Operation(summary = "게시글 수정", description = "게시글 수정 (작성자 본인만 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시글 수정 성공"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음"),
            @ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("게시글 수정 요청 — postId: {}, userId: {}", id, userId);
        PostResponse post = postService.updatePost(id, request, userId);
        return ResponseEntity.ok(post);
    }

    /**
     * 게시글 삭제 API (작성자만)
     */
    @Operation(summary = "게시글 삭제", description = "게시글 삭제 (작성자 본인만 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "게시글 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
            @ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal String userId) {

        log.info("게시글 삭제 요청 — postId: {}, userId: {}", id, userId);
        postService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 플레이리스트 공유 게시글 삭제 API (비공개 전환 전용).
     *
     * <p>postId 대신 playlistId로 게시글을 찾아 삭제합니다.
     * 프론트엔드 세션에서 postId가 소실되어도 안전하게 삭제할 수 있습니다.</p>
     */
    @Operation(summary = "플레이리스트 공유 게시글 삭제", description = "playlistId로 공유 게시글 삭제 (비공개 전환 전용)")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/playlist/{playlistId}")
    public ResponseEntity<Void> deletePostByPlaylistId(
            @PathVariable Long playlistId,
            @AuthenticationPrincipal String userId) {

        log.info("플레이리스트 공유 게시글 삭제 요청 — playlistId: {}, userId: {}", playlistId, userId);
        postService.deletePostByPlaylistId(playlistId, userId);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────
    // 임시저장 기능 (Downloads POST 파일 적용)
    // ──────────────────────────────────────────────

    /**
     * 내가 쓴 게시글 목록 조회 API (JWT 필수, 마이페이지용)
     */
    @Operation(summary = "내가 쓴 게시글 목록 조회", description = "JWT 기준 본인이 작성한 PUBLISHED 게시글 목록을 페이징 조회합니다.")
    @ApiResponse(responseCode = "200", description = "내 게시글 목록 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/me")
    public ResponseEntity<Page<PostResponse>> getMyPosts(
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal String userId) {

        int safeSize = Math.min(pageable.getPageSize(), AppConstants.MAX_PAGE_SIZE);
        Pageable safePage = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), safeSize, pageable.getSort());

        Page<PostResponse> posts = postService.getMyPosts(userId, safePage);
        return ResponseEntity.ok(posts);
    }

    /**
     * 임시저장 작성 API (인증 필요)
     */
    @Operation(summary = "임시저장 작성", description = "게시글을 DRAFT 상태로 임시 저장")
    @ApiResponse(responseCode = "201", description = "임시저장 성공")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/drafts")
    public ResponseEntity<PostResponse> createDraft(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("임시저장 작성 요청 — userId: {}", userId);
        PostResponse post = postService.createDraft(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    /**
     * 임시저장 목록 조회 API (인증 필요)
     */
    @Operation(summary = "임시저장 목록 조회", description = "내 임시저장 게시글 목록 조회")
    @ApiResponse(responseCode = "200", description = "임시저장 목록 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/drafts")
    public ResponseEntity<Page<PostResponse>> getDrafts(
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal String userId) {

        Page<PostResponse> drafts = postService.getDrafts(userId, pageable);
        return ResponseEntity.ok(drafts);
    }

    /**
     * 임시저장 수정 API (작성자만)
     */
    @Operation(summary = "임시저장 수정", description = "임시저장 게시글 내용 수정 (작성자만)")
    @ApiResponse(responseCode = "200", description = "임시저장 수정 성공")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/drafts/{id}")
    public ResponseEntity<PostResponse> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal String userId) {

        PostResponse post = postService.updateDraft(id, request, userId);
        return ResponseEntity.ok(post);
    }

    /**
     * 임시저장 삭제 API (작성자만)
     */
    @Operation(summary = "임시저장 삭제", description = "임시저장 게시글 삭제 (작성자만)")
    @ApiResponse(responseCode = "204", description = "임시저장 삭제 성공")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal String userId) {

        postService.deleteDraft(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────
    // 게시글 좋아요 토글
    // ──────────────────────────────────────────────

    /**
     * 게시글 좋아요 토글 API (인스타그램 스타일, JWT 필수).
     *
     * <p>한 번 호출로 좋아요 등록/취소를 전환한다.
     * 좋아요가 없으면 INSERT, 있으면 hard DELETE 처리된다.</p>
     *
     * @param id     게시글 ID
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + { liked, likeCount }
     */
    @Operation(summary = "게시글 좋아요 토글",
            description = "게시글 좋아요를 토글합니다 (인스타그램 스타일 — 한 번 클릭으로 등록/취소). JWT 필수.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 토글 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "게시글 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/like")
    public ResponseEntity<LikeToggleResponse> togglePostLike(
            @PathVariable Long id,
            @AuthenticationPrincipal String userId) {

        log.info("게시글 좋아요 토글 — userId:{}, postId:{}", userId, id);
        LikeToggleResponse response = postService.togglePostLike(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * 임시저장 → 게시 API (작성자만)
     */
    @Operation(summary = "임시저장 게시", description = "DRAFT 상태 게시글을 PUBLISHED로 변경하여 게시")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "게시 성공"),
            @ApiResponse(responseCode = "404", description = "임시저장 게시글 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/drafts/{id}/publish")
    public ResponseEntity<PostResponse> publishDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal String userId) {

        PostResponse post = postService.publishDraft(id, userId);
        return ResponseEntity.ok(post);
    }

    // ──────────────────────────────────────────────
    // 게시글 신고 (사용자 → 관리자)
    // ──────────────────────────────────────────────

    // ──────────────────────────────────────────────
    // 플레이리스트 공유 피드
    // ──────────────────────────────────────────────

    /**
     * 플레이리스트 공유 피드 조회 API (비로그인 허용).
     *
     * <p>PLAYLIST_SHARE 카테고리 게시글만 반환하며, 각 게시글에는
     * 연결된 플레이리스트의 이름·설명·커버 이미지·좋아요 수·영화 수가 포함된다.</p>
     */
    @Operation(summary = "플레이리스트 공유 피드 조회",
            description = "커뮤니티에 공유된 플레이리스트 목록을 최신 순으로 페이징 조회합니다 (비로그인 허용).")
    @ApiResponse(responseCode = "200", description = "플레이리스트 공유 피드 조회 성공")
    @SecurityRequirement(name = "")
    @GetMapping("/shared-playlists")
    public ResponseEntity<Page<PostResponse>> getSharedPlaylistPosts(
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal String userId) { // 비로그인 시 null — 좋아요 여부 판별용

        int safeSize = Math.min(pageable.getPageSize(), AppConstants.MAX_PAGE_SIZE);
        Pageable safePage = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), safeSize, pageable.getSort());

        Page<PostResponse> posts = postService.getSharedPlaylistPosts(safePage, userId);
        return ResponseEntity.ok(posts);
    }

    /**
     * 게시글 신고 API (인증 필요).
     *
     * <p>사용자가 부적절한 게시글을 신고하면 {@code post_declaration} 테이블에
     * 새 신고 레코드가 생성된다(status="pending"). 관리자는 별도 화면에서 검토/조치한다.</p>
     *
     * <h3>유효성/멱등성</h3>
     * <ul>
     *   <li>본인이 작성한 게시글은 신고 불가 (400 SELF_REPORT_NOT_ALLOWED)</li>
     *   <li>동일 사용자의 중복 신고는 차단 (409 DUPLICATE_REPORT)</li>
     * </ul>
     *
     * @param id      신고 대상 게시글 ID
     * @param request 신고 사유 DTO
     * @param userId  JWT에서 추출한 신고자 사용자 ID
     * @return 201 Created + 생성된 신고 레코드 ID (post_declaration_id)
     */
    @Operation(
            summary = "게시글 신고",
            description = "게시글을 신고합니다 (JWT 필수). 본인 게시글 신고/중복 신고는 차단됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공"),
            @ApiResponse(responseCode = "400", description = "본인 게시글 신고 불가"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "게시글 없음"),
            @ApiResponse(responseCode = "409", description = "중복 신고")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/report")
    public ResponseEntity<PostReportResponse> reportPost(
            @PathVariable Long id,
            @Valid @RequestBody PostReportRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("게시글 신고 요청 — postId:{}, userId:{}", id, userId);
        Long declarationId = postService.reportPost(id, request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new PostReportResponse(declarationId, "pending"));
    }

    /**
     * 게시글 신고 응답 DTO (인라인).
     *
     * @param reportId 생성된 신고 레코드 ID (post_declaration_id)
     * @param status   초기 처리 상태 (항상 "pending")
     */
    public record PostReportResponse(Long reportId, String status) {}
}
