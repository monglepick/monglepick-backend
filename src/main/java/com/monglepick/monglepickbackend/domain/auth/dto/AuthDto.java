package com.monglepick.monglepickbackend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증 시스템 DTO 모음.
 *
 * <p>KMG 패턴 적용 후 변경 사항:</p>
 * <ul>
 *   <li>LoginRequest 제거 — LoginFilter가 JSON Body를 직접 파싱</li>
 *   <li>OAuthRequest 제거 — Spring Security OAuth2 Client가 자동 처리</li>
 *   <li>RefreshRequest 제거 — JwtController에서 자체 record 사용</li>
 *   <li>TokenResponse 제거 — JwtResponseDto로 대체</li>
 * </ul>
 *
 * <h3>남은 DTO</h3>
 * <ul>
 *   <li>{@link SignupRequest} — 로컬 회원가입 요청 (이메일, 비밀번호, 닉네임)</li>
 *   <li>{@link AuthResponse} — 회원가입 성공 응답 (토큰 쌍 + 사용자 정보)</li>
 *   <li>{@link UserInfo} — 사용자 요약 정보</li>
 * </ul>
 */
public final class AuthDto {

    private AuthDto() {
    }

    /**
     * 로컬 회원가입 요청.
     *
     * @param email    이메일 주소 (필수, 이메일 형식)
     * @param password 비밀번호 (필수, 8~128자, 영문+숫자 필수)
     * @param nickname 닉네임 (필수, 2~20자)
     */
    @Schema(description = "로컬 회원가입 요청")
    public record SignupRequest(
            @Schema(description = "이메일 주소", example = "user@example.com")
            @NotBlank(message = "이메일은 필수입니다")
            @Email(message = "올바른 이메일 형식이 아닙니다")
            String email,

            @Schema(description = "비밀번호 (8~128자, 영문+숫자 필수)", example = "password123")
            @NotBlank(message = "비밀번호는 필수입니다")
            @Size(min = 8, max = 128, message = "비밀번호는 8자 이상이어야 합니다")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "비밀번호는 영문자와 숫자를 모두 포함해야 합니다"
            )
            String password,

            @Schema(description = "닉네임 (2~20자)", example = "몽글유저")
            @NotBlank(message = "닉네임은 필수입니다")
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
            String nickname
    ) {
    }

    /**
     * 인증 성공 응답 (회원가입 시 사용).
     *
     * @param accessToken  JWT Access Token
     * @param refreshToken JWT Refresh Token
     * @param user         사용자 요약 정보
     */
    @Schema(description = "인증 성공 응답 (JWT 토큰 쌍 + 사용자 정보)")
    public record AuthResponse(
            @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,

            @Schema(description = "JWT Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String refreshToken,

            @Schema(description = "사용자 요약 정보")
            UserInfo user
    ) {
    }

    /**
     * 사용자 요약 정보.
     *
     * @param id           사용자 ID (PK)
     * @param email        이메일 주소
     * @param nickname     닉네임
     * @param profileImage 프로필 이미지 URL (nullable)
     * @param provider     로그인 제공자 (LOCAL, GOOGLE, KAKAO, NAVER)
     * @param role         사용자 역할 (USER, ADMIN)
     */
    @Schema(description = "사용자 요약 정보")
    public record UserInfo(
            @Schema(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
            String id,

            @Schema(description = "이메일", example = "user@example.com")
            String email,

            @Schema(description = "닉네임", example = "몽글유저")
            String nickname,

            @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg", nullable = true)
            String profileImage,

            @Schema(description = "로그인 제공자", example = "LOCAL")
            String provider,

            @Schema(description = "사용자 역할", example = "USER")
            String role
    ) {
    }
}
