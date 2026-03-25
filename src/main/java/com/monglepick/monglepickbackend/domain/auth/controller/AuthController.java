package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.AuthResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.SignupRequest;
import com.monglepick.monglepickbackend.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 로컬 회원가입.
     *
     * <p>이메일+비밀번호+닉네임으로 회원가입 후 JWT를 즉시 발급한다.</p>
     *
     * @param request 회원가입 요청
     * @return 201 Created + AuthResponse (토큰 쌍 + 사용자 정보)
     */
    @Operation(
            summary = "로컬 회원가입",
            description = "이메일/비밀번호/닉네임으로 계정 생성, JWT 토큰 반환"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        log.info("POST /api/v1/auth/signup — email: {}", request.email());

        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
