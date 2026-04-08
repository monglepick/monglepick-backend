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
     *
     * <h3>2026-04-08 변경 — 응답 데이터 정합성 보강</h3>
     * <p>기존엔 {@code insertComment} 직후 build 시점의 {@link PostComment} 객체를 그대로
     * 응답으로 변환했으나, 두 가지 문제가 있었다:</p>
     * <ol>
     *   <li>{@code createdAt} 이 null — INSERT SQL 이 {@code NOW()} 로 컬럼만 채울 뿐
     *       엔티티 객체에는 set 되지 않아 응답의 createdAt 이 null 로 직렬화됨
     *       → 프론트에서 시간이 표시되지 않는 이슈.</li>
     *   <li>{@code nickname} 이 null — 빌더 단계에서 채울 수 없음 → 프론트가 userId(예: "user_001") 를
     *       그대로 표시하는 이슈.</li>
     * </ol>
     * <p>해결: INSERT 직후 {@link com.monglepick.monglepickbackend.domain.community.mapper.PostMapper#findCommentByIdWithNickname}
     * 로 fully-loaded 댓글(JOIN users 포함)을 재조회하여 응답을 만든다. DB 라운드트립 1회 추가
     * 비용을 들이는 대신 응답 데이터의 완전성을 보장한다.</p>
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

        /*
         * 응답 정합성 보강 — INSERT 후 nickname JOIN 으로 재조회.
         * 재조회 실패(이론상 발생 어려움) 시에도 build 객체로 폴백하여 리워드 지급은 유지.
         */
        PostComment fullyLoaded = postMapper.findCommentByIdWithNickname(comment.getPostCommentId());
        return PostCommentResponse.from(fullyLoaded != null ? fullyLoaded : comment);
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
     *
     * <p>2026-04-08 변경 — 닉네임 JOIN 쿼리({@code findCommentsByPostIdAndIsDeletedFalseWithNickname})
     * 를 사용하여 응답에 작성자 닉네임을 포함하도록 변경했다. 기존 nickname 미포함 메서드는
     * 권한 체크 등 내부 용도로만 남는다.</p>
     */
    public Page<PostCommentResponse> getComments(Long postId, Pageable pageable) {
        log.debug("[Comment] 댓글 목록 조회: postId={}, page={}", postId, pageable.getPageNumber());

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        /* nickname JOIN 포함 SQL 사용 — 응답에 작성자 닉네임을 채우기 위함 */
        List<PostComment> comments = postMapper.findCommentsByPostIdAndIsDeletedFalseWithNickname(
                postId, offset, limit);
        long total = postMapper.countCommentsByPostIdAndIsDeletedFalse(postId);

        List<PostCommentResponse> content = comments.stream()
                .map(PostCommentResponse::from)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }
}
