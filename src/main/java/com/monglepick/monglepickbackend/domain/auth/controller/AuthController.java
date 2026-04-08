package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.AuthResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.AuthResponseBody;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.PasswordCheckRequest;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.PasswordResetRequest;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.SignupRequest;
import com.monglepick.monglepickbackend.domain.auth.service.AuthService;
import com.monglepick.monglepickbackend.global.security.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 인증 컨트롤러 — 회원가입 REST API 엔드포인트.
 *
 * <p>KMG 패턴 적용 후 변경 사항:</p>
 * <ul>
 *   <li><b>로컬 로그인</b>: LoginFilter가 POST /api/v1/auth/login을 인터셉트하여 처리
 *       → AuthController에서 제거됨</li>
 *   <li><b>소셜 로그인</b>: Spring Security OAuth2 Client가 자동 처리
 *       (/oauth2/authorization/{provider}) → AuthController에서 제거됨</li>
 *   <li><b>토큰 갱신</b>: JwtController (POST /jwt/refresh)에서 DB 화이트리스트 기반 처리
 *       → AuthController에서 제거됨</li>
 *   <li><b>로그아웃</b>: Spring Security logout + RefreshTokenLogoutHandler에서 처리
 *       → AuthController에서 제거됨</li>
 * </ul>
 *
 * <h3>쿠키 보안 정책</h3>
 * <p>회원가입 성공 시 Refresh Token은 HttpOnly 쿠키로만 전달하고,
 * HTTP 응답 body에는 Access Token과 사용자 정보만 포함한다 (XSS 방어).</p>
 *
 * <h3>남은 엔드포인트</h3>
 * <ul>
 *   <li>POST /api/v1/auth/signup — 로컬 회원가입</li>
 * </ul>
 */
@Tag(name = "인증", description = "회원가입 API")
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Refresh Token 쿠키 설정/삭제/추출을 담당하는 단일 유틸리티.
     * 모든 인증 흐름에서 쿠키 처리를 일관되게 관리한다.
     */
    private final CookieUtil cookieUtil;

    /**
     * 로컬 회원가입.
     *
     * <p>이메일+비밀번호+닉네임으로 회원가입 후 JWT를 즉시 발급한다.
     * Refresh Token은 HttpOnly 쿠키로 전달하고,
     * 응답 body에는 Access Token과 사용자 정보만 포함한다.</p>
     *
     * @param request  회원가입 요청 (email, password, nickname)
     * @param response HTTP 응답 객체 (Refresh Token 쿠키 설정에 사용)
     * @return 201 Created + AuthResponseBody (accessToken + user, refreshToken은 쿠키)
     */
    @Operation(
            summary = "로컬 회원가입",
            description = "이메일/비밀번호/닉네임으로 계정 생성. accessToken은 body, refreshToken은 HttpOnly 쿠키로 반환"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일 또는 닉네임")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponseBody> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /api/v1/auth/signup — email: {}", request.email());

        /* 서비스에서 토큰 쌍 + 사용자 정보 생성 (refreshToken 포함) */
        AuthResponse authResponse = authService.signup(request);

        /*
         * Refresh Token을 HttpOnly 쿠키로 설정.
         * body에는 포함하지 않아 XSS 공격으로부터 Refresh Token을 보호한다.
         * Set-Cookie 헤더는 ResponseEntity.body() 반환 이전에 추가해야 정상 동작한다.
         */
        cookieUtil.addRefreshTokenCookie(response, authResponse.refreshToken());

        /* HTTP 응답 body: accessToken + user 정보만 반환 (refreshToken 제외) */
        AuthResponseBody responseBody = new AuthResponseBody(
                authResponse.accessToken(),
                authResponse.user()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
    }

    /**
     * 비밀번호 찾기 — 이메일 존재 여부 확인.
     *
     * <p>LOCAL 계정으로 가입된 이메일인지 확인한다.
     * 존재하면 200 OK, 없으면 404 반환.</p>
     *
     * @param request 이메일 주소
     */
    @Operation(summary = "비밀번호 찾기 이메일 확인", description = "LOCAL 계정으로 가입된 이메일인지 확인한다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이메일 존재 확인"),
            @ApiResponse(responseCode = "404", description = "해당 이메일로 가입된 계정 없음")
    })
    @SecurityRequirement(name = "")
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/password/check")
    public ResponseEntity<Void> checkEmail(@Valid @RequestBody PasswordCheckRequest request) {
        log.info("POST /api/v1/auth/password/check — email: {}", request.email());
        authService.checkEmailExists(request);
        return ResponseEntity.ok().build();
    }

    /**
     * 비밀번호 재설정.
     *
     * <p>LOCAL 계정의 비밀번호를 새 비밀번호로 변경한다.</p>
     *
     * @param request 이메일 + 새 비밀번호
     */
    @Operation(summary = "비밀번호 재설정", description = "LOCAL 계정의 비밀번호를 새 비밀번호로 변경한다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 완료"),
            @ApiResponse(responseCode = "404", description = "해당 이메일로 가입된 계정 없음")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        log.info("POST /api/v1/auth/password/reset — email: {}", request.email());
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
