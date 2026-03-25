package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.domain.community.dto.PostCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostResponse;
import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.community.repository.PostRepository;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 서비스
 *
 * <p>게시글의 CRUD + 임시저장(DRAFT) 비즈니스 로직을 처리합니다.
 * Downloads POST 파일의 임시저장/게시 기능을 통합하였습니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────
    // 게시글 CRUD (기존 기능)
    // ──────────────────────────────────────────────

    /**
     * 게시글을 작성합니다 (바로 게시).
     *
     * <p>PostCreateRequest.category()는 이미 Post.Category enum 타입이므로
     * valueOf() 변환 및 toUpperCase() 호출이 불필요하다.
     * Jackson의 @JsonCreator(fromValue)가 역직렬화 시 자동 변환한다.</p>
     */
    @Transactional
    public PostResponse createPost(PostCreateRequest request, String userId) {
        User user = findUserById(userId);
        // category 필드가 Post.Category enum 타입이므로 직접 사용
        Post.Category category = request.category();

        Post post = Post.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .category(category)
                .status(PostStatus.PUBLISHED)
                .build();

        Post savedPost = postRepository.save(post);
        log.info("게시글 작성 완료 — postId: {}, userId: {}, category: {}",
                savedPost.getPostId(), userId, category);

        return PostResponse.from(savedPost);
    }

    /**
     * 게시글 상세를 조회합니다. 조회 시 조회수가 1 증가합니다.
     */
    @Transactional
    public PostResponse getPost(Long postId) {
        postRepository.incrementViewCount(postId);
        Post post = findPostById(postId);
        return PostResponse.from(post);
    }

    /**
     * 카테고리별 게시글 목록을 조회합니다 (게시 완료된 글만).
     *
     * <p>@RequestParam은 Jackson을 거치지 않으므로
     * {@link Post.Category#fromValue(String)}을 직접 호출하여 대소문자 무관 변환한다.
     * "general" → FREE 별칭 처리도 fromValue() 내부에서 수행된다.</p>
     *
     * @param category 카테고리 문자열 (null이면 전체 조회, 소문자 허용)
     * @param pageable 페이징 정보
     * @return 게시글 응답 페이지
     */
    public Page<PostResponse> getPosts(String category, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            // @RequestParam String은 Jackson을 거치지 않으므로 fromValue()로 직접 변환
            Post.Category cat = Post.Category.fromValue(category);
            return postRepository.findByCategoryAndStatusWithUser(cat, PostStatus.PUBLISHED, pageable)
                    .map(PostResponse::from);
        }
        return postRepository.findByStatusWithUser(PostStatus.PUBLISHED, pageable)
                .map(PostResponse::from);
    }

    /**
     * 게시글을 수정합니다. 작성자 본인만 수정할 수 있습니다.
     *
     * <p>category 필드가 Post.Category enum 타입으로 변경되어
     * valueOf()/toUpperCase() 호출 없이 직접 사용한다.</p>
     */
    @Transactional
    public PostResponse updatePost(Long postId, PostCreateRequest request, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        // category 필드가 Post.Category enum 타입이므로 직접 사용
        Post.Category category = request.category();
        post.update(request.title(), request.content(), category);

        log.info("게시글 수정 완료 — postId: {}, userId: {}", postId, userId);
        return PostResponse.from(post);
    }

    /**
     * 게시글을 삭제합니다. 작성자 본인만 삭제할 수 있습니다.
     */
    @Transactional
    public void deletePost(Long postId, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        postRepository.delete(post);
        log.info("게시글 삭제 완료 — postId: {}, userId: {}", postId, userId);
    }

    // ──────────────────────────────────────────────
    // 임시저장 기능 (Downloads POST 파일 적용)
    // ──────────────────────────────────────────────

    /**
     * 게시글을 임시저장합니다.
     *
     * <p>category 필드가 Post.Category enum 타입으로 변경되어
     * valueOf()/toUpperCase() 호출 없이 직접 사용한다.</p>
     */
    @Transactional
    public PostResponse createDraft(PostCreateRequest request, String userId) {
        User user = findUserById(userId);
        // category 필드가 Post.Category enum 타입이므로 직접 사용
        Post.Category category = request.category();

        Post draft = Post.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .category(category)
                .status(PostStatus.DRAFT)
                .build();

        Post savedDraft = postRepository.save(draft);
        log.info("임시저장 완료 — postId: {}, userId: {}", savedDraft.getPostId(), userId);

        return PostResponse.from(savedDraft);
    }

    /**
     * 사용자의 임시저장 목록을 조회합니다.
     */
    public Page<PostResponse> getDrafts(String userId, Pageable pageable) {
        User user = findUserById(userId);
        return postRepository.findByUserAndStatus(user, PostStatus.DRAFT, pageable)
                .map(PostResponse::from);
    }

    /**
     * 임시저장 게시글을 수정합니다.
     *
     * <p>category 필드가 Post.Category enum 타입으로 변경되어
     * valueOf()/toUpperCase() 호출 없이 직접 사용한다.</p>
     */
    @Transactional
    public PostResponse updateDraft(Long postId, PostCreateRequest request, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        // category 필드가 Post.Category enum 타입이므로 직접 사용
        Post.Category category = request.category();
        post.update(request.title(), request.content(), category);

        return PostResponse.from(post);
    }

    /**
     * 임시저장 게시글을 삭제합니다.
     */
    @Transactional
    public void deleteDraft(Long postId, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        postRepository.delete(post);
    }

    /**
     * 임시저장 게시글을 게시합니다 (DRAFT → PUBLISHED).
     */
    @Transactional
    public PostResponse publishDraft(Long postId, String userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        if (post.getStatus() != PostStatus.DRAFT) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        post.publish();
        log.info("임시저장 게시 완료 — postId: {}", postId);

        return PostResponse.from(post);
    }

    // ──────────────────────────────────────────────
    // Private 헬퍼 메서드
    // ──────────────────────────────────────────────

    private User findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private void validatePostOwner(Post post, String userId) {
        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
    }
}
