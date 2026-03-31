package com.monglepick.monglepickbackend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 *   <li>{@link AuthResponse} — 회원가입 내부 처리용 (refreshToken 포함, 서비스 레이어 전용)</li>
 *   <li>{@link AuthResponseBody} — 회원가입 HTTP 응답 body용 (refreshToken 제외, XSS 방어)</li>
 *   <li>{@link UserInfo} — 사용자 요약 정보</li>
 * </ul>
 */
public final class AuthDto {

    private AuthDto() {
    }

    /**
     * 로컬 회원가입 요청.
     *
     * <h3>필수 항목</h3>
     * email, password, nickname, requiredTerm(true여야 함)
     *
     * <h3>선택 항목</h3>
     * name, userBirth, profileImage, optionTerm, marketingAgreed
     */
    @Schema(description = "로컬 회원가입 요청")
    public record SignupRequest(

            // ── 필수 항목 ──────────────────────────────────
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
            String nickname,

            @Schema(description = "필수 약관 동의 (이용약관 + 개인정보처리방침, 반드시 true)", example = "true")
            @NotNull(message = "필수 약관 동의는 필수입니다")
            @AssertTrue(message = "필수 약관에 동의해야 합니다")
            Boolean requiredTerm,

            // ── 선택 항목 ──────────────────────────────────
            @Schema(description = "이름 (선택)", example = "홍길동", nullable = true)
            @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다")
            String name,

            @Schema(description = "생년월일 YYYYMMDD (선택)", example = "19990101", nullable = true)
            @Pattern(
                    regexp = "^\\d{8}$|^$",
                    message = "생년월일은 YYYYMMDD 형식이어야 합니다"
            )
            String userBirth,

            @Schema(description = "프로필 이미지 URL (선택)", example = "https://example.com/img.jpg", nullable = true)
            @Size(max = 500, message = "프로필 이미지 URL은 500자를 초과할 수 없습니다")
            String profileImage,

            @Schema(description = "선택 약관 동의 (선택, 기본값 false)", example = "false", nullable = true)
            Boolean optionTerm,

            @Schema(description = "마케팅 수신 동의 (선택, 기본값 false)", example = "false", nullable = true)
            Boolean marketingAgreed
    ) {
    }

    /**
     * 인증 성공 응답 — 서비스 레이어 내부 전달용.
     *
     * <p>AuthService.signup()에서 생성되며, refreshToken 필드를 포함한다.
     * 이 DTO는 HTTP 응답 body로 직접 반환하지 않고,
     * AuthController에서 쿠키 설정 후 {@link AuthResponseBody}로 변환하여 반환한다.</p>
     *
     * @param accessToken  JWT Access Token
     * @param refreshToken JWT Refresh Token (서비스 레이어 내부 전달용 — body 노출 금지)
     * @param user         사용자 요약 정보
     */
    @Schema(description = "인증 성공 응답 (서비스 레이어 내부 전달용 — refreshToken 포함)")
    public record AuthResponse(
            @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,

            @Schema(description = "JWT Refresh Token (HTTP 응답 body에 노출 금지, 쿠키로만 전달)",
                    example = "eyJhbGciOiJIUzI1NiJ9...")
            String refreshToken,

            @Schema(description = "사용자 요약 정보")
            UserInfo user
    ) {
    }

    /**
     * 회원가입 HTTP 응답 body용 DTO.
     *
     * <p>Refresh Token을 HttpOnly 쿠키로 전달한 후 HTTP 응답 body에는
     * Access Token과 사용자 정보만 포함한다 (XSS 방어).</p>
     *
     * @param accessToken JWT Access Token
     * @param user        사용자 요약 정보
     */
    @Schema(description = "회원가입 HTTP 응답 body (accessToken + user, refreshToken은 쿠키로 전달)")
    public record AuthResponseBody(
            @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,

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
