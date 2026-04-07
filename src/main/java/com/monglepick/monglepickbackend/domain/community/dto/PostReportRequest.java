package com.monglepick.monglepickbackend.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 신고 생성 요청 DTO.
 *
 * <p>사용자가 부적절한 게시글을 신고할 때 클라이언트에서 전송하는 페이로드.
 * 신고 사유(declarationContent)만 필수이며, 신고자/피신고자/대상 게시글 정보는
 * 서버 측에서 JWT(userId)와 path variable(postId)로부터 추출한다.</p>
 *
 * <h3>중복 신고 방지</h3>
 * <ul>
 *   <li>동일 사용자의 동일 게시글 중복 신고는 서버 측에서 409 Conflict로 차단</li>
 *   <li>처리 상태(status)와 무관하게 멱등성 보장</li>
 * </ul>
 *
 * @param reason 신고 사유 (필수, 5~500자) — TEXT 컬럼이지만 UX 보호 차원에서 상한 적용
 */
public record PostReportRequest(
        @NotBlank(message = "신고 사유는 필수입니다.")
        @Size(min = 5, max = 500, message = "신고 사유는 5자 이상 500자 이하여야 합니다.")
        String reason
) {}
