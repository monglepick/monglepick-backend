package com.monglepick.monglepickbackend.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 신고 생성 요청 DTO.
 *
 * <p>사용자가 부적절한 게시글을 신고할 때 클라이언트에서 전송하는 페이로드.
 * 신고 사유(reason)만 필수이며, 신고자/피신고자/대상 게시글 정보는
 * 서버 측에서 JWT(userId)와 path variable(postId)로부터 추출한다.</p>
 *
 * <h3>중복 신고 방지</h3>
 * <ul>
 *   <li>동일 사용자의 동일 게시글 중복 신고는 서버 측에서 409 Conflict로 차단</li>
 *   <li>처리 상태(status)와 무관하게 멱등성 보장</li>
 * </ul>
 *
 * @param reason 신고 유형 코드 (SPAM, ABUSE, OBSCENE, DEFAMATION, ETC) — 필수
 * @param detail 추가 설명 (선택, 최대 500자)
 */
public record PostReportRequest(
        @NotBlank(message = "신고 사유는 필수입니다.")
        @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
        String reason,

        @Size(max = 500, message = "추가 설명은 500자 이하여야 합니다.")
        String detail
) {}
