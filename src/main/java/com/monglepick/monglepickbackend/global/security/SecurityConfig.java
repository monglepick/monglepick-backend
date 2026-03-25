package com.monglepick.monglepickbackend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.auth.filter.LoginFilter;
import com.monglepick.monglepickbackend.domain.auth.handler.LoginSuccessHandler;
import com.monglepick.monglepickbackend.domain.auth.handler.RefreshTokenLogoutHandler;
import com.monglepick.monglepickbackend.domain.auth.handler.SocialFailureHandler;
import com.monglepick.monglepickbackend.domain.auth.handler.SocialSuccessHandler;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 전역 보안 설정.
 *
 * <p>KMG 프로젝트의 SecurityConfig 패턴을 적용하여
 * Spring Security OAuth2 Client + JWT 필터 + 서비스키 필터를 통합한다.</p>
 *
 * <h3>인증 체계 (3단계)</h3>
 * <ol>
 *   <li><b>JWT 인증</b> ({@link JwtAuthenticationFilter}): Bearer 토큰 검증</li>
 *   <li><b>서비스 키 인증</b> ({@link ServiceKeyAuthFilter}): AI Agent 내부 통신</li>
 *   <li><b>OAuth2 로그인</b>: Spring Security OAuth2 Client (Google/Kakao/Naver)</li>
 * </ol>
 *
 * <h3>필터 체인 순서</h3>
 * <p>JwtAuthenticationFilter → (before LogoutFilter)
 *    → LoginFilter → (before UsernamePasswordAuthenticationFilter)
 *    → ServiceKeyAuthFilter → UsernamePasswordAuthenticationFilter</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final LoginSuccessHandler loginSuccessHandler;
    private final SocialSuccessHandler socialSuccessHandler;
    private final SocialFailureHandler socialFailureHandler;
    private final JwtService jwtService;

    @Value("${app.service.key:dev-service-key-change-me}")
    private String serviceKey;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * AuthenticationManager 빈 등록 (LoginFilter에서 사용).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Spring Security 필터 체인 설정.
     *
     * <p>KMG 패턴 적용: OAuth2 로그인 + LoginFilter + JWT 필터 + 로그아웃 핸들러</p>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            AuthenticationManager authenticationManager) throws Exception {
        http
                /* CSRF 비활성화 (REST API) */
                .csrf(csrf -> csrf.disable())

                /* 세션: IF_REQUIRED (OAuth2 흐름에서 세션이 필요) */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                /* CORS 설정 */
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                /* 폼 로그인 비활성화 (LoginFilter로 대체) */
                .formLogin(formLogin -> formLogin.disable())

                /* HTTP Basic 비활성화 */
                .httpBasic(httpBasic -> httpBasic.disable())

                /* OAuth2 소셜 로그인 설정 (KMG 패턴) */
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(socialSuccessHandler)
                        .failureHandler(socialFailureHandler)
                )

                /* 로그아웃 — Refresh Token DB 삭제 핸들러 등록 */
                .logout(logout -> logout
                        .addLogoutHandler(new RefreshTokenLogoutHandler(jwtService, jwtTokenProvider))
                )

                /* URL별 접근 권한 설정 */
                .authorizeHttpRequests(authorize -> authorize
                        /* 헬스체크 */
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()

                        /* Swagger/OpenAPI UI 및 API 문서 경로 */
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**").permitAll()

                        /* 인증 API (회원가입, 로그인 등) */
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        /* JWT 토큰 교환/갱신 (KMG 패턴) */
                        .requestMatchers("/jwt/exchange", "/jwt/refresh").permitAll()

                        /* OAuth2 흐름 경로 */
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers("/login", "/login/**").permitAll()

                        /* 결제 웹훅 */
                        .requestMatchers(HttpMethod.POST, "/api/v1/payment/webhook").permitAll()

                        /* 구독 플랜 조회 */
                        .requestMatchers(HttpMethod.GET, "/api/v1/subscription/plans").permitAll()

                        /* 게시글 조회는 비로그인 허용 */
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()

                        /* 나머지 모든 요청: 인증 필요 */
                        .anyRequest().authenticated()
                )

                /* 필터 체인 등록 (KMG 패턴 + 기존 ServiceKeyAuthFilter 유지) */

                /* 1. JwtAuthenticationFilter → LogoutFilter 앞에 배치 */
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        LogoutFilter.class
                )

                /* 2. LoginFilter → UsernamePasswordAuthenticationFilter 앞에 배치 */
                .addFilterBefore(
                        new LoginFilter(authenticationManager, loginSuccessHandler),
                        UsernamePasswordAuthenticationFilter.class
                )

                /* 3. ServiceKeyAuthFilter → UsernamePasswordAuthenticationFilter 앞에 배치 */
                .addFilterBefore(
                        new ServiceKeyAuthFilter(serviceKey),
                        UsernamePasswordAuthenticationFilter.class
                )

                /* 인증 실패 처리 (401 JSON) */
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");

                            ErrorResponse errorResponse = new ErrorResponse(
                                    "S002",
                                    "인증이 필요합니다. 로그인 후 다시 시도해주세요.",
                                    Map.of()
                            );
                            OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");

                            ErrorResponse errorResponse = new ErrorResponse(
                                    "S002",
                                    "접근 권한이 없습니다.",
                                    Map.of()
                            );
                            OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
                        })
                );

        return http.build();
    }

    /**
     * CORS 설정 소스.
     *
     * <p>Set-Cookie 헤더를 ExposedHeaders에 추가하여
     * OAuth2 쿠키 교환이 가능하도록 한다.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(List.of("*"));
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }

        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        /* Authorization + Set-Cookie 헤더를 클라이언트에 노출 (KMG 패턴) */
        configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
