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
 * <h3>닉네임 필드 (2026-04-08 추가)</h3>
 * <p>{@code nickname} 은 {@link PostComment#getNickname()} 에 보관된 값을 그대로 전달한다.
 * MyBatis JOIN 결과로 채워지며, JOIN 없이 로드된 엔티티에서는 {@code "알 수 없음"} 으로
 * 폴백한다 (Post 도메인과 동일 패턴, JPA/MyBatis 하이브리드 §15).</p>
 *
 * @param commentId       댓글 고유 ID
 * @param postId          게시글 ID
 * @param userId          작성자 사용자 ID (식별자, 표시용 아님)
 * @param nickname        작성자 닉네임 (표시용; JOIN 결과)
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
        String nickname,
        String content,
        Long parentCommentId,
        int likeCount,
        boolean isDeleted,
        LocalDateTime createdAt
) {

    /** 소프트 삭제된 댓글의 마스킹 문자열 */
    private static final String DELETED_CONTENT = "삭제된 댓글입니다";

    /** 닉네임 폴백 문자열 — JOIN 없이 로드되거나 사용자가 탈퇴/익명일 때 */
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    /**
     * {@link PostComment} 엔티티로부터 응답 DTO를 생성한다.
     *
     * <p>소프트 삭제(isDeleted=true)된 댓글은 content를 {@value #DELETED_CONTENT}로
     * 마스킹하여 반환한다.</p>
     *
     * <p>JOIN 없이 로드된 엔티티(nickname == null)는 {@value #UNKNOWN_NICKNAME} 으로
     * 폴백하여 클라이언트가 항상 표시 가능한 문자열을 받도록 보장한다.</p>
     *
     * @param entity 댓글 엔티티
     * @return 댓글 응답 DTO
     */
    public static PostCommentResponse from(PostComment entity) {
        /* 소프트 삭제 댓글은 내용을 마스킹하여 대댓글 스레드 구조 보존 */
        String displayContent = Boolean.TRUE.equals(entity.getIsDeleted())
                ? DELETED_CONTENT
                : entity.getContent();

        /* nickname 폴백 — Post.from() 과 동일 패턴 */
        String displayNickname = entity.getNickname() != null
                ? entity.getNickname()
                : UNKNOWN_NICKNAME;

        return new PostCommentResponse(
                entity.getPostCommentId(),
                entity.getPostId(),
                entity.getUserId(),
                displayNickname,
                displayContent,
                entity.getParentCommentId(),
                entity.getLikeCount(),
                Boolean.TRUE.equals(entity.getIsDeleted()),
                entity.getCreatedAt()
        );
    }
}
