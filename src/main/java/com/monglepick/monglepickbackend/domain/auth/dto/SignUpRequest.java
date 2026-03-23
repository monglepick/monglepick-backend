package com.monglepick.monglepickbackend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 DTO
 *
 * <p>회원가입 시 필요한 정보를 담는 불변 레코드입니다.
 * Bean Validation으로 입력값을 검증합니다.</p>
 *
 * @param email 이메일 주소 (이메일 형식 검증)
 * @param password 비밀번호 (8~50자)
 * @param nickname 닉네임 (2~20자)
 */
public record SignUpRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 50, message = "비밀번호는 8자 이상 50자 이하여야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        String nickname
) {}
