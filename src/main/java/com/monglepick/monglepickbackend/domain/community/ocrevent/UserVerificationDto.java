package com.monglepick.monglepickbackend.domain.community.ocrevent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 유저 OCR 실관람 인증 요청/응답 DTO 모음 (2026-04-14 신규).
 *
 * <p>클라이언트는 먼저 {@code POST /api/v1/images/upload} 로 영수증 이미지를
 * 업로드해 URL 을 얻은 뒤, 본 DTO 로 이벤트에 인증을 제출한다.
 * (기존 커뮤니티 이미지 업로드 인프라 재사용 — 별도 multipart EP 신설 회피.)</p>
 */
public class UserVerificationDto {

    /**
     * 영수증 이미지 URL 기반 인증 제출 요청.
     *
     * <p>OCR 자동 추출이 아직 구현되지 않았으므로, 유저가 수기로 관람일을
     * 입력할 수 있도록 {@code watchDate} 필드를 선택적으로 받는다.
     * 관리자 검토(approve/reject) 단계는 후속 작업에서 추가될 예정.</p>
     */
    public record SubmitRequest(
            /** 이미지 업로드 EP 로부터 받은 영수증 이미지 URL */
            @NotBlank(message = "영수증 이미지 URL은 필수입니다.")
            @Size(max = 500, message = "이미지 URL 은 500자 이하여야 합니다.")
            String imageUrl,

            /** 관람일(자유 형식, 선택). 예: "2026-04-10", "4월 10일" */
            @Size(max = 50, message = "관람일은 50자 이하여야 합니다.")
            String watchDate,

            /** 관람 영수증에 표시된 영화명(자유 형식, 선택). */
            @Size(max = 200, message = "영화명은 200자 이하여야 합니다.")
            String movieName
    ) {}

    /**
     * 인증 제출 성공 응답.
     *
     * @param verificationId 저장된 인증 PK
     * @param eventId        대상 이벤트 PK
     * @param message        유저 노출용 친화 메시지
     */
    public record SubmitResponse(
            Long verificationId,
            Long eventId,
            String message
    ) {}
}
