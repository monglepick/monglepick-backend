package com.monglepick.monglepickbackend.domain.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로컬 로그인 성공 핸들러.
 *
 * <p>KMG 프로젝트의 LoginSuccessHandler 패턴을 적용.
 * 로그인 성공 시 Access Token + Refresh Token을 생성하고,
 * Refresh Token을 DB 화이트리스트에 저장한 후 JSON 응답으로 반환한다.</p>
 *
 * <p>C-1 수정: String.format() JSON 수동 조합 → ObjectMapper 직렬화로 교체
 * (JSON Injection 취약점 방지)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /** JSON 직렬화용 ObjectMapper (Spring Bean 주입) */
    private final ObjectMapper objectMapper;

    /**
     * 로그인 성공 응답 DTO.
     *
     * <p>C-1: String.format() 대신 ObjectMapper로 직렬화하여
     * 닉네임/프로필 이미지 등 사용자 입력값에 의한 JSON Injection을 방지한다.</p>
     */
    private record LoginResponse(
            String accessToken,
            String refreshToken,
            UserInfo user
    ) {
    }

    /** 로그인 응답에 포함될 사용자 정보 */
    private record UserInfo(
            String id,
            String email,
            String nickname,
            String profileImage,
            String provider,
            String role
    ) {
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        /* principal은 AuthService.loadUserByUsername()에서 반환한 username(= email) */
        String email = authentication.getName();

        /* 이메일로 User 엔티티 조회 → userId로 JWT 생성 */
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("로그인 성공 후 사용자 조회 실패: " + email));

        /* Access Token + Refresh Token 생성 */
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getUserRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        /* Refresh Token을 DB 화이트리스트에 저장 */
        jwtService.addRefresh(user.getUserId(), refreshToken);

        /* C-1: ObjectMapper로 안전한 JSON 직렬화 (JSON Injection 방지) */
        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                refreshToken,
                new UserInfo(
                        user.getUserId(),
                        user.getEmail(),
                        user.getNickname(),
                        user.getProfileImage(),
                        user.getProvider().name(),
                        user.getUserRole()
                )
        );

        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), loginResponse);

        log.info("로컬 로그인 성공 — userId: {}, email: {}", user.getUserId(), email);
    }
}
