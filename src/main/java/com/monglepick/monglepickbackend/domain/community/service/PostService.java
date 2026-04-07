package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.domain.community.dto.PostCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostReportRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostResponse;
import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostDeclaration;
import com.monglepick.monglepickbackend.domain.community.entity.PostLike;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
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
    /** 리워드 서비스 — POST_REWARD 정책 지급/회수 */
    private final RewardService rewardService;

    // ──────────────────────────────────────────────
    // 게시글 CRUD
    // ──────────────────────────────────────────────

    /**
     * 게시글을 작성합니다 (바로 게시).
     */
    @Transactional
    public PostResponse createPost(PostCreateRequest request, String userId) {
        // 사용자 존재 검증은 JWT 인증 단계에서 이미 처리됨 (§15.4)
        Post.Category category = request.category();

        Post post = Post.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .category(category)
                .status(PostStatus.PUBLISHED)
                .build();

        // MyBatis insert — useGeneratedKeys로 postId 자동 세팅
        postMapper.insert(post);
        log.info("게시글 작성 완료 — postId: {}, userId: {}, category: {}",
                post.getPostId(), userId, category);

        // 리워드 지급 — 게시글 ID 기준 1회 (reference_id = "post_{postId}")
        rewardService.grantReward(userId, "POST_REWARD", "post_" + post.getPostId(), request.content().length());

        return PostResponse.from(post);
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
     */
    public Page<PostResponse> getPosts(String category, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();
        String statusStr = PostStatus.PUBLISHED.name();

        List<Post> posts;
        long total;

        if (category != null && !category.isBlank()) {
            // @RequestParam String을 enum으로 변환 후 SQL에는 enum.name() 문자열 전달
            Post.Category cat = Post.Category.fromValue(category);
            posts = postMapper.findByCategoryAndStatusWithNickname(cat.name(), statusStr, offset, limit);
            total = postMapper.countByCategoryAndStatus(cat.name(), statusStr);
        } else {
            posts = postMapper.findByStatusWithNickname(statusStr, offset, limit);
            total = postMapper.countByStatus(statusStr);
        }

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

        // 리워드 지급 (createPost와 동일, RewardService 내부 중복 검사)
        rewardService.grantReward(userId, "POST_REWARD", "post_" + postId, post.getContent().length());

        return PostResponse.from(post);
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
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("게시글 좋아요 중복 INSERT 감지 (race condition) — userId:{}, postId:{}", userId, postId);
                postMapper.deletePostLikeByPostIdAndUserId(postId, userId);
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
}
