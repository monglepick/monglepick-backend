package com.monglepick.monglepickbackend.domain.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청 DTO
 *
 * <p>변경할 필드만 포함하면 됩니다. null인 필드는 수정하지 않습니다 (Partial Update).
 * 비밀번호 변경 시 currentPassword와 newPassword를 함께 전달해야 합니다.
 * 소셜 로그인 사용자는 비밀번호 변경 불가 (403 반환).</p>
 *
 * @param nickname        변경할 닉네임 (2~20자, null이면 변경 안 함)
 * @param profileImageUrl 변경할 프로필 이미지 URL (null이면 변경 안 함)
 * @param currentPassword 현재 비밀번호 (비밀번호 변경 시 필수)
 * @param newPassword     새 비밀번호 (8~128자, null이면 변경 안 함)
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
        String nickname,

        @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다")
        String profileImageUrl,

        String currentPassword,

        @Size(min = 8, max = 128, message = "비밀번호는 8자 이상 128자 이하여야 합니다")
        String newPassword
) {
}
