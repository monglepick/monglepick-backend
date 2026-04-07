package com.monglepick.monglepickbackend.domain.auth.handler;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.security.CookieUtil;
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
 * Refresh Token을 DB 화이트리스트에 저장한다.</p>
 *
 * <p>C-1 수정: String.format() JSON 수동 조합 → ObjectMapper 직렬화로 교체
 * (JSON Injection 취약점 방지)</p>
 *
 * <p>쿠키 보안 수정: Refresh Token은 HttpOnly 쿠키에만 저장하고
 * JSON 응답 body에서는 제거한다. Access Token만 body로 반환하여
 * XSS 공격으로부터 Refresh Token을 보호한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtService jwtService;
    /** 사용자 조회 — MyBatis Mapper (설계서 §15) */
    private final UserMapper userMapper;

    /** JSON 직렬화용 ObjectMapper (Spring Bean 주입) */
    private final ObjectMapper objectMapper;

    /**
     * Refresh Token을 HttpOnly 쿠키로 전환하기 위한 CookieUtil 주입.
     * 모든 인증 흐름에서 단일 진입점으로 쿠키를 관리한다.
     */
    private final CookieUtil cookieUtil;

    /**
     * 로그인 성공 응답 DTO.
     *
     * <p>보안 정책: Refresh Token은 JSON body에 포함하지 않고
     * HttpOnly 쿠키로만 전달한다 (XSS 방어).</p>
     *
     * <p>C-1: String.format() 대신 ObjectMapper로 직렬화하여
     * 닉네임/프로필 이미지 등 사용자 입력값에 의한 JSON Injection을 방지한다.</p>
     */
    private record LoginResponse(
            String accessToken,
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

        /* 이메일로 User 엔티티 조회 → userId로 JWT 생성 (MyBatis, null 반환 시 예외) */
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("로그인 성공 후 사용자 조회 실패: " + email);
        }

        /* Access Token + Refresh Token 생성 */
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getUserRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        /* Refresh Token을 DB 화이트리스트에 저장 */
        jwtService.addRefresh(user.getUserId(), refreshToken);

        /*
         * [중요] Set-Cookie 헤더는 반드시 objectMapper.writeValue() 이전에 추가해야 한다.
         * response.getWriter()가 호출되면 헤더가 커밋(flush)되므로,
         * 그 이후에 addHeader()를 호출해도 적용되지 않는다.
         * Refresh Token을 HttpOnly 쿠키에만 저장하여 XSS로부터 보호한다.
         */
        cookieUtil.addRefreshTokenCookie(response, refreshToken);

        /* C-1: ObjectMapper로 안전한 JSON 직렬화 (JSON Injection 방지) */
        /* body: accessToken + user 정보만 반환 (refreshToken 제외) */
        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                new UserInfo(
                        user.getUserId(),
                        user.getEmail(),
                        user.getNickname(),
                        user.getProfileImage(),
                        user.getProvider().name(),
                        user.getUserRole().name()
                )
        );

        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), loginResponse);

        log.info("로컬 로그인 성공 — userId: {}, email: {}", user.getUserId(), email);
    }
}
