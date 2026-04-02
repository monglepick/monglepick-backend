package com.monglepick.monglepickbackend.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 댓글 작성 요청 DTO.
 *
 * <p>POST /api/v1/posts/{postId}/comments 의 요청 바디로 사용한다.</p>
 *
 * <h3>필드</h3>
 * <ul>
 *   <li>{@code content} — 댓글 내용 (필수, 1~2000자)</li>
 *   <li>{@code parentCommentId} — 대댓글 대상 부모 댓글 ID (null이면 최상위 댓글)</li>
 * </ul>
 *
 * <h3>대댓글 정책</h3>
 * <p>1단계 대댓글만 지원한다. {@code parentCommentId}가 이미 대댓글인 경우에도
 * 해당 댓글의 부모 댓글에 대한 답글로 처리한다 (서비스 레이어에서 처리).</p>
 */
public record PostCommentCreateRequest(

        /**
         * 댓글 내용 (필수, 최대 2000자).
         * 공백만 있는 댓글은 허용하지 않는다.
         */
        @NotBlank(message = "댓글 내용은 필수입니다")
        @Size(max = 2000, message = "댓글은 2000자를 초과할 수 없습니다")
        String content,

        /**
         * 부모 댓글 ID (nullable) — 대댓글 작성 시에만 전달.
         * null이면 최상위 댓글로 저장된다.
         */
        Long parentCommentId

) {}
