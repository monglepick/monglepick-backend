package com.monglepick.monglepickbackend.global.security.filter;

import com.monglepick.monglepickbackend.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 인증 필터
 *
 * <p>매 HTTP 요청마다 한 번 실행되는 OncePerRequestFilter를 확장합니다.
 * Authorization 헤더에서 Bearer 토큰을 추출하고, 유효성을 검증한 후
 * SecurityContext에 인증 정보를 설정합니다.</p>
 *
 * <p>처리 흐름:</p>
 * <ol>
 *   <li>Authorization 헤더에서 "Bearer " 접두사를 제거하여 토큰 추출</li>
 *   <li>JwtTokenProvider로 토큰 유효성 검증</li>
 *   <li>토큰에서 사용자 ID 추출</li>
 *   <li>UsernamePasswordAuthenticationToken 생성하여 SecurityContext에 설정</li>
 * </ol>
 *
 * <p>토큰이 없거나 유효하지 않은 경우, SecurityContext를 설정하지 않고
 * 다음 필터로 진행합니다. 이후 SecurityConfig의 권한 설정에 따라
 * 접근이 허용되거나 403/401 응답이 반환됩니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 토큰 검증 및 파싱 유틸리티 */
    private final JwtTokenProvider jwtTokenProvider;

    /** Authorization 헤더에서 사용하는 Bearer 접두사 */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 실제 필터 로직 수행
     *
     * <p>모든 HTTP 요청에 대해 JWT 토큰을 검증하고,
     * 유효한 경우 SecurityContext에 인증 정보를 설정합니다.</p>
     *
     * @param request  HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param filterChain 다음 필터로 전달하기 위한 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException I/O 예외
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. 요청 헤더에서 JWT 토큰 추출
            String token = extractToken(request);

            // 2. 토큰이 존재하고 유효한 경우에만 인증 처리
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                // 3. 토큰에서 사용자 ID 추출
                Long userId = jwtTokenProvider.extractUserId(token);

                // 4. Spring Security 인증 객체 생성
                // - principal: 사용자 ID (Long)
                // - credentials: null (JWT 인증에서는 별도 자격증명 불필요)
                // - authorities: ROLE_USER (기본 역할)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                // 5. SecurityContext에 인증 정보 설정
                // 이후 @AuthenticationPrincipal로 사용자 ID를 주입받을 수 있음
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("사용자 인증 성공 - userId: {}, URI: {}",
                        userId, request.getRequestURI());
            }
        } catch (Exception e) {
            // JWT 처리 중 예외 발생 시 로그만 남기고 필터 체인 계속 진행
            // SecurityContext가 설정되지 않으므로 인증 필요 경로에서는 401 반환
            log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
        }

        // 다음 필터로 요청 전달 (인증 성공/실패 모두)
        filterChain.doFilter(request, response);
    }

    /**
     * HTTP 요청의 Authorization 헤더에서 Bearer 토큰을 추출합니다.
     *
     * <p>"Bearer " 접두사를 제거하고 순수 토큰 문자열만 반환합니다.
     * 헤더가 없거나 Bearer 형식이 아닌 경우 null을 반환합니다.</p>
     *
     * @param request HTTP 요청 객체
     * @return JWT 토큰 문자열 또는 null
     */
    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        // Authorization 헤더가 존재하고 "Bearer "로 시작하는 경우
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            // "Bearer " 접두사(7자) 제거 후 토큰 반환
            return authorization.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
