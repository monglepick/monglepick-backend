package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.domain.community.dto.PostCommentCreateRequest;
import com.monglepick.monglepickbackend.domain.community.dto.PostCommentResponse;
import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
import com.monglepick.monglepickbackend.domain.community.repository.PostCommentRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 댓글 서비스.
 *
 * <p>커뮤니티 게시글에 달린 댓글의 작성·삭제·조회 비즈니스 로직을 처리한다.</p>
 *
 * <h3>소프트 삭제 정책</h3>
 * <p>댓글 삭제 시 실제 레코드를 제거하지 않고 {@code is_deleted=true}로 마스킹한다.
 * 대댓글 스레드 구조를 보존하고, 응답 시 content를 "삭제된 댓글입니다"로 치환한다.</p>
 *
 * <h3>리워드 연동</h3>
 * <p>댓글 작성 성공 시 {@code COMMENT_CREATE} 리워드를 지급한다.
 * 댓글 삭제 시 리워드는 회수하지 않는다 (설계서 §6.3.2: 소액 미회수 정책).</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <p>클래스 레벨 {@code readOnly=true}, 쓰기 메서드는 {@code @Transactional} 오버라이드.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    /** 게시글 댓글 리포지토리 — post_comment 테이블 접근 */
    private final PostCommentRepository postCommentRepository;

    /** 활동 리워드 서비스 — COMMENT_CREATE 리워드 지급 위임 */
    private final RewardService rewardService;

    // ─────────────────────────────────────────────
    // 댓글 작성
    // ─────────────────────────────────────────────

    /**
     * 게시글에 댓글을 작성한다.
     *
     * <p>저장 완료 후 {@code COMMENT_CREATE} 리워드를 지급한다.
     * RewardService는 {@code REQUIRES_NEW} 트랜잭션 + 내부 try-catch로 동작하므로
     * 리워드 실패가 댓글 저장 트랜잭션을 롤백시키지 않는다.</p>
     *
     * @param userId          작성자 사용자 ID (JWT에서 추출)
     * @param postId          댓글을 달 게시글 ID
     * @param request         댓글 작성 요청 (content, parentCommentId)
     * @return 저장된 댓글의 응답 DTO
     */
    @Transactional
    public PostCommentResponse createComment(String userId, Long postId,
                                             PostCommentCreateRequest request) {
        /* 댓글 엔티티 빌드 — postId/userId/content/parentCommentId 설정 */
        PostComment comment = PostComment.builder()
                .postId(postId)
                .userId(userId)
                .content(request.content())
                .parentCommentId(request.parentCommentId())   // null이면 최상위 댓글
                .build();

        PostComment saved = postCommentRepository.save(comment);
        log.info("[Comment] 댓글 작성 완료: commentId={}, postId={}, userId={}",
                saved.getPostCommentId(), postId, userId);

        /*
         * COMMENT_CREATE 활동 리워드 지급.
         * referenceId: "comment_{commentId}" — 댓글 단위 중복 지급 방지 키로 사용.
         * contentLength: content 길이를 전달하여 min_content_length 정책 검사에 활용.
         */
        rewardService.grantReward(
                userId,
                "COMMENT_CREATE",
                "comment_" + saved.getPostCommentId(),
                request.content().length()
        );

        return PostCommentResponse.from(saved);
    }

    // ─────────────────────────────────────────────
    // 댓글 삭제
    // ─────────────────────────────────────────────

    /**
     * 댓글을 소프트 삭제한다.
     *
     * <p>작성자 본인만 삭제할 수 있다. 실제 레코드는 DB에 유지되며
     * {@code is_deleted=true}로 마스킹된다.</p>
     *
     * <p>설계서 §6.3.2: 댓글 삭제 시 리워드는 회수하지 않는다 (소액 미회수 정책).</p>
     *
     * @param userId    삭제 요청 사용자 ID (JWT에서 추출)
     * @param commentId 삭제 대상 댓글 ID
     * @throws BusinessException 댓글이 없거나 본인 댓글이 아닌 경우
     */
    @Transactional
    public void deleteComment(String userId, Long commentId) {
        /* 댓글 존재 확인 */
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_ACCESS_DENIED, "댓글을 찾을 수 없습니다: " + commentId));

        /* 본인 확인 — 작성자가 아니면 403 */
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED,
                    "댓글 삭제 권한이 없습니다: commentId=" + commentId);
        }

        /* 소프트 삭제 — is_deleted=true 플래그 설정 */
        comment.softDelete();
        log.info("[Comment] 댓글 소프트 삭제: commentId={}, userId={}", commentId, userId);
    }

    // ─────────────────────────────────────────────
    // 댓글 목록 조회
    // ─────────────────────────────────────────────

    /**
     * 게시글의 유효 댓글 목록을 페이징으로 조회한다.
     *
     * <p>소프트 삭제(is_deleted=true)된 댓글은 결과에서 제외된다.
     * 단, 소프트 삭제 댓글의 content는 응답 DTO 변환 시 마스킹된다.</p>
     *
     * @param postId   게시글 ID
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 댓글 응답 페이지
     */
    public Page<PostCommentResponse> getComments(Long postId, Pageable pageable) {
        log.debug("[Comment] 댓글 목록 조회: postId={}, page={}", postId, pageable.getPageNumber());
        return postCommentRepository
                .findByPostIdAndIsDeletedFalse(postId, pageable)
                .map(PostCommentResponse::from);
    }
}
