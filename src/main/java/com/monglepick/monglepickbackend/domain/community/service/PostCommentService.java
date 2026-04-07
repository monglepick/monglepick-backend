package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.domain.community.dto.PostCommentCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostCommentResponse;
import com.monglepick.monglepickbackend.domain.community.entity.CommentLike;
import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
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
 * 게시글 댓글 서비스.
 *
 * <p>커뮤니티 게시글에 달린 댓글의 작성·삭제·좋아요·조회 비즈니스 로직을 처리한다.
 * JPA/MyBatis 하이브리드 §15에 따라 모든 데이터 접근은 {@link PostMapper}를 통해 이루어진다.</p>
 *
 * <h3>소프트 삭제 정책</h3>
 * <p>댓글 삭제 시 실제 레코드를 제거하지 않고 {@code is_deleted=true}로 마스킹한다.
 * 대댓글 스레드 구조를 보존하고, 응답 시 content를 "삭제된 댓글입니다"로 치환한다.</p>
 *
 * <h3>리워드 연동</h3>
 * <p>댓글 작성 성공 시 {@code COMMENT_CREATE} 리워드를 지급한다.
 * 댓글 삭제 시 리워드는 회수하지 않는다 (설계서 §6.3.2: 소액 미회수 정책).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    /** 게시글/댓글/좋아요 통합 Mapper — post_comment/comment_likes 담당 */
    private final PostMapper postMapper;

    /** 활동 리워드 서비스 — COMMENT_CREATE 리워드 지급 위임 */
    private final RewardService rewardService;

    // ─────────────────────────────────────────────
    // 댓글 작성
    // ─────────────────────────────────────────────

    /**
     * 게시글에 댓글을 작성한다.
     *
     * <p>저장 완료 후 {@code COMMENT_CREATE} 리워드를 지급한다.</p>
     */
    @Transactional
    public PostCommentResponse createComment(String userId, Long postId,
                                             PostCommentCreateRequest request) {
        PostComment comment = PostComment.builder()
                .postId(postId)
                .userId(userId)
                .content(request.content())
                .parentCommentId(request.parentCommentId())   // null이면 최상위 댓글
                .build();

        postMapper.insertComment(comment);
        log.info("[Comment] 댓글 작성 완료: commentId={}, postId={}, userId={}",
                comment.getPostCommentId(), postId, userId);

        // 비정규화 comment_count 증가 (원자적 UPDATE)
        postMapper.updateCommentCount(postId, 1);

        // COMMENT_CREATE 활동 리워드 지급
        rewardService.grantReward(
                userId,
                "COMMENT_CREATE",
                "comment_" + comment.getPostCommentId(),
                request.content().length()
        );

        return PostCommentResponse.from(comment);
    }

    // ─────────────────────────────────────────────
    // 댓글 삭제
    // ─────────────────────────────────────────────

    /**
     * 댓글을 소프트 삭제한다.
     */
    @Transactional
    public void deleteComment(String userId, Long commentId) {
        PostComment comment = postMapper.findCommentById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND, "댓글을 찾을 수 없습니다: " + commentId);
        }

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED,
                    "댓글 삭제 권한이 없습니다: commentId=" + commentId);
        }

        // 소프트 삭제 — is_deleted=true
        postMapper.softDeleteComment(commentId);

        // 비정규화 comment_count 감소 (원자적 UPDATE)
        postMapper.updateCommentCount(comment.getPostId(), -1);

        log.info("[Comment] 댓글 소프트 삭제: commentId={}, userId={}", commentId, userId);
    }

    // ─────────────────────────────────────────────
    // 댓글 좋아요 토글
    // ─────────────────────────────────────────────

    /**
     * 댓글 좋아요 토글 (인스타그램 스타일).
     */
    @Transactional
    public LikeToggleResponse toggleCommentLike(String userId, Long commentId) {
        CommentLike existing = postMapper.findCommentLikeByCommentIdAndUserId(commentId, userId);
        boolean liked;

        if (existing != null) {
            /* 좋아요 취소 — hard-delete */
            postMapper.deleteCommentLikeByCommentIdAndUserId(commentId, userId);
            liked = false;
        } else {
            /* 좋아요 등록 — INSERT, race condition 처리 */
            try {
                postMapper.insertCommentLike(
                        CommentLike.builder()
                                .commentId(commentId)
                                .userId(userId)
                                .build()
                );
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("[Comment Like] 중복 INSERT 감지 (race condition) — userId:{}, commentId:{}", userId, commentId);
                postMapper.deleteCommentLikeByCommentIdAndUserId(commentId, userId);
                long count = postMapper.countCommentLikeByCommentId(commentId);
                return LikeToggleResponse.of(false, count);
            }
            liked = true;
        }

        long count = postMapper.countCommentLikeByCommentId(commentId);
        log.debug("[Comment Like] 댓글 좋아요 토글 — userId:{}, commentId:{}, liked:{}, count:{}",
                userId, commentId, liked, count);

        return LikeToggleResponse.of(liked, count);
    }

    // ─────────────────────────────────────────────
    // 댓글 목록 조회
    // ─────────────────────────────────────────────

    /**
     * 게시글의 유효 댓글 목록을 페이징으로 조회한다.
     */
    public Page<PostCommentResponse> getComments(Long postId, Pageable pageable) {
        log.debug("[Comment] 댓글 목록 조회: postId={}, page={}", postId, pageable.getPageNumber());

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<PostComment> comments = postMapper.findCommentsByPostIdAndIsDeletedFalse(postId, offset, limit);
        long total = postMapper.countCommentsByPostIdAndIsDeletedFalse(postId);

        List<PostCommentResponse> content = comments.stream()
                .map(PostCommentResponse::from)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }
}
