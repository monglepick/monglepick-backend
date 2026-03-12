package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.domain.community.dto.PostCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostResponse;
import com.monglepick.monglepickbackend.domain.community.entity.Post;
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
 * <p>게시글의 CRUD 비즈니스 로직을 처리합니다.
 * 게시글 작성/수정/삭제 시 작성자 검증을 수행합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 게시글을 작성합니다.
     *
     * @param request 게시글 작성 요청 (제목, 내용, 카테고리)
     * @param userId 작성자 ID (JWT에서 추출)
     * @return 생성된 게시글 응답 DTO
     */
    @Transactional
    public PostResponse createPost(PostCreateRequest request, Long userId) {
        User user = findUserById(userId);
        Post.Category category = Post.Category.valueOf(request.category().toUpperCase());

        Post post = Post.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .category(category)
                .build();

        Post savedPost = postRepository.save(post);
        log.info("게시글 작성 완료 - postId: {}, userId: {}, category: {}",
                savedPost.getId(), userId, category);

        return PostResponse.from(savedPost);
    }

    /**
     * 게시글 상세를 조회합니다. 조회 시 조회수가 1 증가합니다.
     */
    @Transactional
    public PostResponse getPost(Long postId) {
        Post post = findPostById(postId);
        post.incrementViewCount();
        return PostResponse.from(post);
    }

    /**
     * 카테고리별 게시글 목록을 조회합니다.
     */
    public Page<PostResponse> getPosts(String category, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            Post.Category cat = Post.Category.valueOf(category.toUpperCase());
            return postRepository.findByCategory(cat, pageable).map(PostResponse::from);
        }
        return postRepository.findAll(pageable).map(PostResponse::from);
    }

    /**
     * 게시글을 수정합니다. 작성자 본인만 수정할 수 있습니다.
     */
    @Transactional
    public PostResponse updatePost(Long postId, PostCreateRequest request, Long userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        Post.Category category = Post.Category.valueOf(request.category().toUpperCase());
        post.update(request.title(), request.content(), category);

        log.info("게시글 수정 완료 - postId: {}, userId: {}", postId, userId);
        return PostResponse.from(post);
    }

    /**
     * 게시글을 삭제합니다. 작성자 본인만 삭제할 수 있습니다.
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = findPostById(postId);
        validatePostOwner(post, userId);

        postRepository.delete(post);
        log.info("게시글 삭제 완료 - postId: {}, userId: {}", postId, userId);
    }

    /** 사용자 ID로 사용자를 조회하는 헬퍼 */
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 게시글 ID로 게시글을 조회하는 헬퍼 */
    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** 게시글 작성자와 요청자가 일치하는지 검증하는 헬퍼 */
    private void validatePostOwner(Post post, Long userId) {
        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
    }
}
