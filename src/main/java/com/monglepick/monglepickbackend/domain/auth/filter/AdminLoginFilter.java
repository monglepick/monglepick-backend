package com.monglepick.monglepickbackend.domain.auth.filter;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.global.constants.AppConstants;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 관리자 전용 JSON 로그인 필터.
 *
 * <p>일반 로그인 필터({@link LoginFilter}, POST /api/v1/auth/login)와 별도로
 * 관리자 페이지 전용 엔드포인트(POST /api/v1/admin/auth/login)를 처리한다.</p>
 *
 * <p>인증 성공 후 {@link #successfulAuthentication}에서 ROLE_ADMIN 여부를 검증한다.
 * 일반 사용자(ROLE_USER)가 이 엔드포인트로 로그인을 시도하면 인증 자체는 성공하더라도
 * 403 Forbidden(A010)을 반환하여 관리자 토큰 발급을 차단한다.</p>
 *
 * <p>인터셉트 경로: POST /api/v1/admin/auth/login</p>
 */
public class AdminLoginFilter extends AbstractAuthenticationProcessingFilter {

    /** JSON 직렬화를 위한 ObjectMapper (스레드 안전, 클래스 로딩 시 1회 초기화) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AuthenticationSuccessHandler successHandler;

    /**
     * AdminLoginFilter 생성자.
     *
     * @param authenticationManager Spring Security 인증 매니저
     * @param successHandler        관리자 인증 성공 핸들러 (관리자 JWT + adminRefreshToken 발급)
     */
    public AdminLoginFilter(AuthenticationManager authenticationManager,
                             AuthenticationSuccessHandler successHandler) {
        super("/api/v1/admin/auth/login");
        setAuthenticationManager(authenticationManager);
        this.successHandler = successHandler;
    }

    /**
     * JSON 요청 Body에서 email/password를 추출하여 인증을 시도한다.
     *
     * <p>요청 JSON 형식:</p>
     * <pre>{"email": "admin@example.com", "password": "adminpassword"}</pre>
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException(
                    "Authentication method not supported: " + request.getMethod());
        }

        Map<String, String> loginMap;
        try {
            ServletInputStream inputStream = request.getInputStream();
            String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            loginMap = OBJECT_MAPPER.readValue(messageBody, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("관리자 로그인 요청 파싱 실패", e);
        }

        String email = loginMap.get("email");
        email = (email != null) ? email.trim() : "";
        String password = loginMap.get("password");
        password = (password != null) ? password : "";

        UsernamePasswordAuthenticationToken authRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(email, password);
        setDetails(request, authRequest);

        return this.getAuthenticationManager().authenticate(authRequest);
    }

    /**
     * 인증 성공 후 ROLE_ADMIN 여부를 검증한다.
     *
     * <p>일반 사용자(ROLE_USER)인 경우 403 Forbidden(A010)을 반환하고 종료한다.
     * ROLE_ADMIN이면 관리자 성공 핸들러를 호출하여 JWT를 발급한다.</p>
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain, Authentication authResult)
            throws IOException, ServletException {

        /* 인증된 사용자의 권한에서 ROLE_ADMIN 포함 여부 확인 */
        boolean isAdmin = authResult.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            /* 일반 사용자 → 403 Forbidden 반환 (관리자 토큰 발급 차단) */
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(AppConstants.CONTENT_TYPE_JSON);
            ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.ADMIN_ONLY);
            OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
            return;
        }

        /* ADMIN 확인 → LoginSuccessHandler로 JWT 발급 */
        successHandler.onAuthenticationSuccess(request, response, authResult);
    }

    /**
     * 인증 실패 시 401 JSON 응답을 반환한다.
     * (이메일/비밀번호 불일치)
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                               AuthenticationException failed) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(AppConstants.CONTENT_TYPE_JSON);
        ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.INVALID_CREDENTIALS);
        OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
    }

    /**
     * request 상세 정보를 인증 토큰에 설정한다.
     */
    protected void setDetails(HttpServletRequest request, UsernamePasswordAuthenticationToken authRequest) {
        authRequest.setDetails(authenticationDetailsSource.buildDetails(request));
    }
}
