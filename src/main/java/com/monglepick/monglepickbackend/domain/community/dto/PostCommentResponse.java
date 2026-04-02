package com.monglepick.monglepickbackend.domain.community.dto;

import com.monglepick.monglepickbackend.domain.community.entity.PostComment;

import java.time.LocalDateTime;

/**
 * 게시글 댓글 응답 DTO.
 *
 * <p>댓글 작성(POST), 댓글 목록 조회(GET) API의 응답 바디로 사용한다.</p>
 *
 * <h3>소프트 삭제 처리</h3>
 * <p>{@code isDeleted=true}인 댓글은 {@code content}를 "삭제된 댓글입니다"로 마스킹하여
 * 클라이언트에 전달한다. 대댓글 구조(parentCommentId)는 유지하여 스레드 형태를 보존한다.</p>
 *
 * @param commentId       댓글 고유 ID
 * @param postId          게시글 ID
 * @param userId          작성자 사용자 ID
 * @param content         댓글 내용 (소프트 삭제 시 "삭제된 댓글입니다"로 마스킹)
 * @param parentCommentId 부모 댓글 ID (null이면 최상위 댓글)
 * @param likeCount       좋아요 수 (비정규화 캐시)
 * @param isDeleted       소프트 삭제 여부
 * @param createdAt       작성 일시
 */
public record PostCommentResponse(
        Long commentId,
        Long postId,
        String userId,
        String content,
        Long parentCommentId,
        int likeCount,
        boolean isDeleted,
        LocalDateTime createdAt
) {

    /** 소프트 삭제된 댓글의 마스킹 문자열 */
    private static final String DELETED_CONTENT = "삭제된 댓글입니다";

    /**
     * {@link PostComment} 엔티티로부터 응답 DTO를 생성한다.
     *
     * <p>소프트 삭제(isDeleted=true)된 댓글은 content를 {@value #DELETED_CONTENT}로
     * 마스킹하여 반환한다.</p>
     *
     * @param entity 댓글 엔티티
     * @return 댓글 응답 DTO
     */
    public static PostCommentResponse from(PostComment entity) {
        /* 소프트 삭제 댓글은 내용을 마스킹하여 대댓글 스레드 구조 보존 */
        String displayContent = Boolean.TRUE.equals(entity.getIsDeleted())
                ? DELETED_CONTENT
                : entity.getContent();

        return new PostCommentResponse(
                entity.getPostCommentId(),
                entity.getPostId(),
                entity.getUserId(),
                displayContent,
                entity.getParentCommentId(),
                entity.getLikeCount(),
                Boolean.TRUE.equals(entity.getIsDeleted()),
                entity.getCreatedAt()
        );
    }
}
