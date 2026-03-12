package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.LoginRequest;
import com.monglepick.monglepickbackend.domain.auth.dto.SignUpRequest;
import com.monglepick.monglepickbackend.domain.auth.dto.TokenResponse;
import com.monglepick.monglepickbackend.domain.user.dto.UserResponse;
import com.monglepick.monglepickbackend.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 컨트롤러
 *
 * <p>회원가입, 로그인, 토큰 갱신 등 인증 관련 API를 제공합니다.
 * /api/v1/auth/** 경로는 SecurityConfig에서 인증 없이 접근 가능하도록 설정됩니다.</p>
 *
 * <p>API 목록:</p>
 * <ul>
 *   <li>POST /api/v1/auth/signup - 회원가입</li>
 *   <li>POST /api/v1/auth/login - 로그인</li>
 *   <li>POST /api/v1/auth/refresh - 토큰 갱신</li>
 *   <li>GET /api/v1/auth/me - 현재 사용자 정보 조회 (인증 필요)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입 API
     *
     * <p>이메일, 비밀번호, 닉네임을 받아 새 사용자를 생성합니다.
     * 가입 즉시 로그인 처리되어 JWT 토큰이 발급됩니다.</p>
     *
     * @param request 회원가입 요청 (이메일, 비밀번호, 닉네임)
     * @return 201 Created + JWT 토큰 쌍
     */
    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        log.info("회원가입 요청 - email: {}", request.email());
        TokenResponse tokenResponse = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenResponse);
    }

    /**
     * 로그인 API
     *
     * <p>이메일과 비밀번호로 인증 후 JWT 토큰을 발급합니다.</p>
     *
     * @param request 로그인 요청 (이메일, 비밀번호)
     * @return 200 OK + JWT 토큰 쌍
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("로그인 요청 - email: {}", request.email());
        TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * 토큰 갱신 API
     *
     * <p>유효한 리프레시 토큰을 Authorization 헤더에 담아 보내면
     * 새로운 액세스 토큰과 리프레시 토큰을 발급합니다.</p>
     *
     * @param authorization "Bearer {refreshToken}" 형식의 Authorization 헤더
     * @return 200 OK + 새 JWT 토큰 쌍
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader("Authorization") String authorization) {

        // "Bearer " 접두사 제거하여 순수 토큰 추출
        String refreshToken = authorization.replace("Bearer ", "");
        log.info("토큰 갱신 요청");

        TokenResponse tokenResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * 현재 로그인한 사용자 정보 조회 API
     *
     * <p>JWT 토큰에서 추출한 사용자 ID로 프로필 정보를 반환합니다.
     * 이 엔드포인트는 인증이 필요합니다.</p>
     *
     * @param userId JWT에서 추출한 사용자 ID (@AuthenticationPrincipal)
     * @return 200 OK + 사용자 정보
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal Long userId) {

        UserResponse userResponse = authService.getCurrentUser(userId);
        return ResponseEntity.ok(userResponse);
    }
}
