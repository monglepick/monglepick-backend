package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.domain.community.dto.PostCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostReportRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.RewardResult;
import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostDeclaration;
import com.monglepick.monglepickbackend.domain.community.entity.PostLike;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.playlist.entity.Playlist;
import com.monglepick.monglepickbackend.domain.playlist.mapper.PlaylistMapper;
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
 * 게시글 서비스
 *
 * <p>게시글의 CRUD + 임시저장(DRAFT) + 좋아요 토글 비즈니스 로직을 처리한다.
 * JPA/MyBatis 하이브리드 §15에 따라 모든 데이터 접근은 {@link PostMapper}를 통해 이루어진다.
 * Post/PostLike {@code @Entity}는 DDL 정의 전용이며 dirty checking은 사용하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    /** 게시글/댓글/좋아요 통합 Mapper — posts/post_comment/post_likes/comment_likes 담당 */
    private final PostMapper postMapper;
    /** 플레이리스트 Mapper — PLAYLIST_SHARE 카테고리 게시글 작성 시 플레이리스트 검증에 사용 */
    private final PlaylistMapper playlistMapper;
    /** 리워드 서비스 — POST_REWARD 정책 지급/회수 */
    private final RewardService rewardService;

    // ──────────────────────────────────────────────
    // 게시글 CRUD
    // ──────────────────────────────────────────────

    /**
     * 게시글을 작성합니다 (바로 게시).
     *
     * <p>PLAYLIST_SHARE 카테고리의 경우 추가 검증:</p>
     * <ul>
     *   <li>playlistId 필수</li>
     *   <li>해당 플레이리스트가 존재해야 함</li>
     *   <li>작성자 본인 소유의 플레이리스트여야 함</li>
     *   <li>공개(isPublic=true) 플레이리스트여야 함</li>
     * </ul>
     */
    @Transactional
    public PostResponse createPost(PostCreateRequest request, String userId) {
        // 사용자 존재 검증은 JWT 인증 단계에서 이미 처리됨 (§15.4)
        Post.Category category = request.category();

        // PLAYLIST_SHARE 전용 검증
        Long playlistId = null;
        if (category == Post.Category.PLAYLIST_SHARE) {
            playlistId = validateAndGetPlaylistId(request.playlistId(), userId);

            // 멱등성 보장 — 이미 공유된 게시글이 있으면 기존 게시글 반환 (중복 생성 방지)
            Post existing = postMapper.findByPlaylistId(playlistId);
            if (existing != null) {
                log.info("PLAYLIST_SHARE 중복 공유 방지 — 기존 postId={} 반환", existing.getPostId());
                return PostResponse.from(existing, null);
            }
        }

        Post post = Post.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .category(category)
                .status(PostStatus.PUBLISHED)
                .playlistId(playlistId)
                .build();
        // ✅ 이미지 URL 저장
        // 프론트에서 이미지 업로드 후 받은 URL 목록을 콤마 구분 문자열로 변환하여 저장
        // 예: "http://localhost:8080/images/userId/a.jpg,http://localhost:8080/images/userId/b.jpg"
        // 추후 S3 전환 시 URL 형식만 바뀌고 이 코드는 그대로 유지
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            post.setImageUrls(String.join(",", request.imageUrls()));
        }

        // MyBatis insert — useGeneratedKeys로 postId 자동 세팅
        postMapper.insert(post);
        log.info("게시글 작성 완료 — postId: {}, userId: {}, category: {}",
                post.getPostId(), userId, category);

        // 리워드 지급 — 게시글 ID 기준 1회 (reference_id = "post_{postId}"), 결과를 응답에 포함
        RewardResult rewardResult = rewardService.grantReward(userId, "POST_REWARD", "post_" + post.getPostId(), request.content().length());

        // 첫 게시글 작성 보너스 — INSERT 후 카운트가 1이면 첫 게시글
        long postCount = postMapper.countByUserId(userId);
        if (postCount == 1) {
            RewardResult firstResult = rewardService.grantReward(userId, "FIRST_POST", "first_post_" + userId, 0);
            if (firstResult.earned()) {
                rewardResult = RewardResult.of(
                        rewardResult.points() + firstResult.points(),
                        rewardResult.policyName()
                );
            }
        }

        Integer rewardPoints = rewardResult.earned() ? rewardResult.points() : null;
        return PostResponse.from(post, rewardPoints);
    }

    /**
     * 게시글 상세를 조회합니다. 조회 시 조회수가 1 증가한다 (원자적 UPDATE).
     *
     * <p>닉네임 표시를 위해 JOIN users 쿼리({@code findByIdWithNickname})를 사용한다.</p>
     */
    @Transactional
    public PostResponse getPost(Long postId) {
        // 조회수 원자적 증가 → 이후 상세 조회 (증가 결과 반영된 값으로 반환)
        postMapper.incrementViewCount(postId);

        Post post = postMapper.findByIdWithNickname(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return PostResponse.from(post);
    }

    /**
     * 카테고리별 게시글 목록을 조회합니다 (게시 완료된 글만, 닉네임 포함).
     *
     * <p>PLAYLIST_SHARE 카테고리는 playlist JOIN 전용 쿼리를 사용하여
     * 플레이리스트 상세 정보(이름/설명/커버/좋아요/영화수)를 함께 반환한다.</p>
     */
    public Page<PostResponse> getPosts(String category, String keyword, String sort, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();
        String statusStr = PostStatus.PUBLISHED.name();

        List<Post> posts;
        long total;

        if (category != null && !category.isBlank()) {
            Post.Category cat = Post.Category.fromValue(category);

            if (cat == Post.Category.PLAYLIST_SHARE) {
                posts = postMapper.findPlaylistSharePostsWithDetail(offset, limit);
                total = postMapper.countPlaylistSharePosts();
            } else {
                posts = postMapper.findByCategoryAndStatusWithNickname(cat.name(), statusStr, keyword, sort, offset, limit);
                total = postMapper.countByCategoryAndStatus(cat.name(), statusStr, keyword);
            }
        } else {
            posts = postMapper.findByStatusWithNickname(statusStr, keyword, sort, offset, limit);
            total = postMapper.countByStatus(statusStr, keyword);
        }

        List<PostResponse> content = posts.stream().map(PostResponse::from).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 플레이리스트 공유 피드를 조회합니다 (PLAYLIST_SHARE 전용 페이지).
     *
     * <p>커뮤니티 상단 "플레이리스트 공유" 탭에서 사용한다.
     * playlist JOIN 쿼리로 플레이리스트 상세 정보를 포함하여 반환한다.</p>
     */
    public Page<PostResponse> getSharedPlaylistPosts(Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<Post> posts = postMapper.findPlaylistSharePostsWithDetail(offset, limit);
        long total = postMapper.countPlaylistSharePosts();

        List<PostResponse> content = posts.stream().map(PostResponse::from).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 게시글을 수정합니다. 작성자 본인만 수정할 수 있다.
     */
    @Transactional
    public PostResponse updatePost(Long postId, PostCreateRequest request, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        Post.Category category = request.category();
        post.update(request.title(), request.content(), category);

        // MyBatis는 dirty checking 미지원 — 명시적 UPDATE 호출
        postMapper.update(post);

        log.info("게시글 수정 완료 — postId: {}, userId: {}", postId, userId);
        return PostResponse.from(post);
    }

    /**
     * 게시글을 삭제합니다. 작성자 본인만 삭제할 수 있다.
     *
     * <p>현재는 hard delete. 소프트 삭제 정책으로 전환 시 {@code softDelete} + 별도 UPDATE로 전환.</p>
     */
    @Transactional
    public void deletePost(Long postId, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        postMapper.deleteById(postId);
        log.info("게시글 삭제 완료 — postId: {}, userId: {}", postId, userId);

        // 리워드 회수
        rewardService.revokeReward(userId, "POST_REWARD", "post_" + postId);
    }

    /**
     * 플레이리스트 ID로 공유 게시글을 찾아 삭제합니다 (비공개 전환 전용).
     *
     * <p>sharedPostId를 프론트엔드 세션에서 관리하면 새로고침 시 소실되므로,
     * 비공개 전환 시에는 postId 대신 playlistId로 게시글을 조회하여 삭제합니다.
     * 공유 게시글이 없으면 조용히 무시합니다.</p>
     *
     * @param playlistId 비공개로 전환할 플레이리스트 ID
     * @param userId     요청 사용자 ID (소유자 검증)
     */
    @Transactional
    public void deletePostByPlaylistId(Long playlistId, String userId) {
        Post post = postMapper.findByPlaylistId(playlistId);
        if (post == null) {
            log.debug("비공개 전환 — playlistId={}에 연결된 공유 게시글 없음 (이미 삭제됐거나 미공유)", playlistId);
            return;
        }
        validatePostOwner(post, userId);
        postMapper.deleteById(post.getPostId());
        rewardService.revokeReward(userId, "POST_REWARD", "post_" + post.getPostId());
        log.info("플레이리스트 비공개 전환 — postId={} 삭제 완료 (playlistId={})", post.getPostId(), playlistId);
    }

    // ──────────────────────────────────────────────
    // 임시저장 기능
    // ──────────────────────────────────────────────

    @Transactional
    public PostResponse createDraft(PostCreateRequest request, String userId) {
        Post.Category category = request.category();

        Post draft = Post.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .category(category)
                .status(PostStatus.DRAFT)
                .build();

        postMapper.insert(draft);
        log.info("임시저장 완료 — postId: {}, userId: {}", draft.getPostId(), userId);

        return PostResponse.from(draft);
    }

    /**
     * 내가 쓴 게시글 목록 조회 (PUBLISHED 상태만, 마이페이지용).
     */
    public Page<PostResponse> getMyPosts(String userId, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();
        String statusStr = PostStatus.PUBLISHED.name();

        List<Post> posts = postMapper.findByUserIdAndStatus(userId, statusStr, offset, limit);
        long total = postMapper.countByUserIdAndStatus(userId, statusStr);

        List<PostResponse> content = posts.stream().map(PostResponse::from).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 사용자의 임시저장 목록을 조회한다.
     */
    public Page<PostResponse> getDrafts(String userId, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();
        String statusStr = PostStatus.DRAFT.name();

        List<Post> posts = postMapper.findByUserIdAndStatus(userId, statusStr, offset, limit);
        long total = postMapper.countByUserIdAndStatus(userId, statusStr);

        List<PostResponse> content = posts.stream().map(PostResponse::from).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 임시저장 게시글을 수정한다.
     */
    @Transactional
    public PostResponse updateDraft(Long postId, PostCreateRequest request, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        Post.Category category = request.category();
        post.update(request.title(), request.content(), category);

        postMapper.update(post);
        return PostResponse.from(post);
    }

    /**
     * 임시저장 게시글을 삭제한다.
     */
    @Transactional
    public void deleteDraft(Long postId, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        postMapper.deleteById(postId);
    }

    /**
     * 임시저장 게시글을 게시한다 (DRAFT → PUBLISHED).
     */
    @Transactional
    public PostResponse publishDraft(Long postId, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        post.publish();
        postMapper.update(post);

        log.info("임시저장 게시 완료 — postId: {}", postId);

        // 리워드 지급 (createPost와 동일, RewardService 내부 중복 검사) — 결과를 응답에 포함
        RewardResult rewardResult = rewardService.grantReward(userId, "POST_REWARD", "post_" + postId, post.getContent().length());
        Integer rewardPoints = rewardResult.earned() ? rewardResult.points() : null;

        return PostResponse.from(post, rewardPoints);
    }

    // ──────────────────────────────────────────────
    // 게시글 좋아요 토글
    // ──────────────────────────────────────────────

    /**
     * 게시글 좋아요 토글 (인스타그램 스타일).
     *
     * <p>해당 사용자의 좋아요 레코드가 없으면 INSERT, 있으면 hard DELETE한다.
     * 토글 완료 후 현재 좋아요 상태와 전체 좋아요 수를 반환한다.</p>
     */
    @Transactional
    public LikeToggleResponse togglePostLike(String userId, Long postId) {
        PostLike existing = postMapper.findPostLikeByPostIdAndUserId(postId, userId);
        boolean liked;

        if (existing != null) {
            /* 좋아요 취소 — hard-delete */
            postMapper.deletePostLikeByPostIdAndUserId(postId, userId);
            postMapper.updateLikeCount(postId, -1);
            liked = false;
        } else {
            /* 좋아요 등록 — INSERT.
             * 동시 요청 race condition으로 UNIQUE 제약 위반 시 DataIntegrityViolationException. */
            try {
                postMapper.insertPostLike(
                        PostLike.builder()
                                .postId(postId)
                                .userId(userId)
                                .build()
                );
                postMapper.updateLikeCount(postId, +1);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("게시글 좋아요 중복 INSERT 감지 (race condition) — userId:{}, postId:{}", userId, postId);
                postMapper.deletePostLikeByPostIdAndUserId(postId, userId);
                postMapper.updateLikeCount(postId, -1);
                long count = postMapper.countPostLikeByPostId(postId);
                return LikeToggleResponse.of(false, count);
            }
            liked = true;
        }

        long count = postMapper.countPostLikeByPostId(postId);
        log.debug("게시글 좋아요 토글 — userId:{}, postId:{}, liked:{}, count:{}", userId, postId, liked, count);

        return LikeToggleResponse.of(liked, count);
    }

    // ──────────────────────────────────────────────
    // 게시글 신고 (사용자 측)
    // ──────────────────────────────────────────────

    /**
     * 게시글 신고를 접수한다.
     *
     * <p>사용자가 부적절한 게시글을 신고하면 {@code post_declaration} 테이블에
     * 새 신고 레코드를 INSERT 한다. 처리 상태는 "pending"으로 시작하며,
     * 관리자는 {@code AdminContentService.processReport()}로 검토/조치한다.</p>
     *
     * <h3>비즈니스 규칙</h3>
     * <ul>
     *   <li>대상 게시글이 없으면 404 (POST_NOT_FOUND)</li>
     *   <li>본인이 작성한 게시글은 신고 불가 (400 SELF_REPORT_NOT_ALLOWED)</li>
     *   <li>동일 사용자가 동일 게시글을 중복 신고하면 409 (DUPLICATE_REPORT) — 멱등 보장</li>
     *   <li>이미 소프트 삭제된 게시글도 신고 가능(악의적 작성자 추적용)</li>
     * </ul>
     *
     * <p>{@code categoryId}는 PostDeclaration 컬럼이지만 현재 Post.Category는
     * enum 기반이라 별도의 카테고리 마스터 ID가 없으므로 null로 저장한다.
     * (Phase 5-2 카테고리 관리 작업 시 Category 마스터 PK로 매핑 예정)</p>
     *
     * <p>AI 독성 분석은 비동기로 별도 워커가 처리하므로 INSERT 시점에는
     * {@code toxicity_score = NULL}로 저장된다.</p>
     *
     * @param postId  신고 대상 게시글 ID
     * @param request 신고 사유 DTO
     * @param userId  신고자 사용자 ID (JWT에서 추출)
     * @return 생성된 신고 레코드의 ID (post_declaration_id)
     * @throws BusinessException 게시글 없음 / 본인 신고 / 중복 신고
     */
    @Transactional
    public Long reportPost(Long postId, PostReportRequest request, String userId) {
        // 1) 대상 게시글 존재 검증 (소프트 삭제 포함 — 악의적 작성자 추적용)
        Post target = postMapper.findById(postId);
        if (target == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 2) 본인이 작성한 게시글 신고 차단
        if (target.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        // 3) 중복 신고 차단 (멱등 보장 — 처리 상태 무관)
        if (postMapper.existsDeclarationByPostIdAndUserId(postId, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        // 4) 신고 INSERT — status="pending", target_type="post", toxicity_score=null
        //    categoryId는 Category 마스터 미연동 상태이므로 null 저장 (Phase 5-2 이후 매핑 예정)
        PostDeclaration declaration = PostDeclaration.builder()
                .postId(postId)
                .categoryId(null)
                .userId(userId)
                .reportedUserId(target.getUserId())
                .targetType("post")
                .declarationContent(request.reason())
                .toxicityScore(null)
                .status("pending")
                .build();

        postMapper.insertDeclaration(declaration);

        log.info("게시글 신고 접수 — postId:{}, reporterId:{}, reportedUserId:{}, declarationId:{}",
                postId, userId, target.getUserId(), declaration.getPostDeclarationId());

        return declaration.getPostDeclarationId();
    }

    // ──────────────────────────────────────────────
    // Private 헬퍼 메서드
    // ──────────────────────────────────────────────

    private Post findPostById(Long postId) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void validatePostOwner(Post post, String userId) {
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
    }



    /**
     * PLAYLIST_SHARE 게시글 작성 시 플레이리스트 유효성을 검증하고 playlistId를 반환한다.
     *
     * <ul>
     *   <li>playlistId 누락 → INVALID_INPUT (400)</li>
     *   <li>플레이리스트 없음 → PLAYLIST_NOT_FOUND (404)</li>
     *   <li>본인 소유 아님 → PLAYLIST_SHARE_INVALID (400)</li>
     *   <li>비공개 → PLAYLIST_SHARE_INVALID (400)</li>
     * </ul>
     */
    private Long validateAndGetPlaylistId(Long playlistId, String userId) {
        if (playlistId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "PLAYLIST_SHARE 카테고리는 playlistId가 필수입니다");
        }
        Playlist playlist = playlistMapper.findById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND);
        }
        if (!playlist.getUserId().equals(userId) || !Boolean.TRUE.equals(playlist.getIsPublic())) {
            throw new BusinessException(ErrorCode.PLAYLIST_SHARE_INVALID);
        }
        return playlistId;
    }
}
