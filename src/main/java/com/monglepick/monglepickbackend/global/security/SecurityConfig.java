package com.monglepick.monglepickbackend.global.security;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.auth.filter.AdminLoginFilter;
import com.monglepick.monglepickbackend.domain.auth.filter.LoginFilter;
import com.monglepick.monglepickbackend.domain.auth.handler.LoginSuccessHandler;
import com.monglepick.monglepickbackend.domain.auth.handler.RefreshTokenLogoutHandler;
import com.monglepick.monglepickbackend.domain.auth.handler.SocialFailureHandler;
import com.monglepick.monglepickbackend.domain.auth.handler.SocialSuccessHandler;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.global.constants.AppConstants;
import com.monglepick.monglepickbackend.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
 * <p>OAuth2 흐름과 JWT REST API의 세션 정책이 달라 두 개의 {@link SecurityFilterChain}으로 분리한다.</p>
 *
 * <h3>필터 체인 구성</h3>
 * <ol>
 *   <li>{@link #oauth2FilterChain} ({@code @Order(1)}) — OAuth2 전용
 *       <ul>
 *         <li>매칭 경로: {@code /oauth2/**}, {@code /login/oauth2/**}, {@code /cookie}</li>
 *         <li>세션 정책: {@link SessionCreationPolicy#IF_REQUIRED}
 *             (OAuth2 인가 코드 흐름에서 state 값을 세션에 저장해야 하므로 세션 허용)</li>
 *         <li>OAuth2 로그인 성공/실패 핸들러 등록</li>
 *       </ul>
 *   </li>
 *   <li>{@link #apiFilterChain} ({@code @Order(2)}) — REST API 전용
 *       <ul>
 *         <li>매칭 경로: 나머지 모든 경로 (/** 캐치올)</li>
 *         <li>세션 정책: {@link SessionCreationPolicy#STATELESS}
 *             (JWT 기반, 세션 불필요)</li>
 *         <li>JWT 필터, ServiceKey 필터, LoginFilter, 로그아웃 핸들러 등록</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>인증 체계 (3단계)</h3>
 * <ol>
 *   <li><b>JWT 인증</b> ({@link JwtAuthenticationFilter}): Bearer 토큰 검증</li>
 *   <li><b>서비스 키 인증</b> ({@link ServiceKeyAuthFilter}): AI Agent 내부 통신</li>
 *   <li><b>OAuth2 로그인</b>: Spring Security OAuth2 Client (Google/Kakao/Naver)</li>
 * </ol>
 *
 * <h3>CORS 공유</h3>
 * <p>두 체인 모두 {@link #corsConfigurationSource()} 빈을 참조하여 동일한 CORS 정책을 적용한다.</p>
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

    /**
     * Refresh Token 쿠키 처리 단일 유틸리티.
     * RefreshTokenLogoutHandler 생성 시 전달하여 로그아웃 시 쿠키 삭제에 사용한다.
     */
    private final CookieUtil cookieUtil;

    @Value("${app.service.key:dev-service-key-change-me}")
    private String serviceKey;

    /** Dev Master Key 활성화 여부 (DEV_AUTH_ENABLED 환경변수) */
    @Value("${dev.auth.enabled:false}")
    private boolean devAuthEnabled;

    /** Dev Master Key 값 (DEV_MASTER_KEY 환경변수) */
    @Value("${dev.master-key:monglepick-dev-master-2026}")
    private String devMasterKey;

    /** Swagger UI 접근 허용 여부 (SWAGGER_ENABLED 환경변수, 운영=false) */
    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

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

    /** JSON 직렬화를 위한 ObjectMapper (스레드 안전, 클래스 로딩 시 1회 초기화) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * AuthenticationManager 빈 등록 (LoginFilter에서 사용).
     *
     * @param configuration Spring Security AuthenticationConfiguration
     * @return AuthenticationManager 인스턴스
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 비밀번호 인코더 빈 등록.
     *
     * @return BCryptPasswordEncoder 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ════════════════════════════════════════════════════════════════
    // FilterChain 1: OAuth2 전용 (Order=1, 먼저 평가)
    // ════════════════════════════════════════════════════════════════

    /**
     * OAuth2 전용 SecurityFilterChain.
     *
     * <p>소셜 로그인 인가 코드 흐름({@code /oauth2/**})과
     * 콜백 경로({@code /login/oauth2/**}), 쿠키 교환 페이지({@code /cookie})만 처리한다.</p>
     *
     * <p><b>세션 정책 — IF_REQUIRED</b>: Spring Security OAuth2 Client는
     * CSRF state 값과 redirect_uri를 HttpSession에 저장한 뒤 인가 코드 콜백 시 검증한다.
     * 세션이 없으면 {@code state mismatch} 오류가 발생하므로 OAuth2 체인에서만 세션을 허용한다.</p>
     *
     * <p>이 체인에는 JWT 필터나 LoginFilter를 등록하지 않는다.
     * 소셜 로그인 성공 후 Refresh Token을 HttpOnly 쿠키에 저장하고
     * 클라이언트를 {@code /cookie} 페이지로 리다이렉트한다.
     * 이후 클라이언트는 {@code /jwt/exchange}를 호출하여 Access Token을 발급받는다.</p>
     *
     * @param http HttpSecurity 빌더
     * @return OAuth2 전용 SecurityFilterChain
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {

        http
            /* ── 이 체인이 처리할 경로를 명시적으로 제한 ── */
            /* /oauth2/**, /login/oauth2/**, /cookie 경로만 이 체인에서 처리 */
            .securityMatcher("/oauth2/**", "/login/oauth2/**", "/cookie")

            /* CSRF 비활성화 (OAuth2 state 파라미터로 CSRF 방어 대체) */
            .csrf(csrf -> csrf.disable())

            /*
             * 세션 정책: IF_REQUIRED
             * OAuth2 인가 코드 흐름에서 세션이 반드시 필요하다.
             * Spring Security OAuth2 Client는 state/nonce 값을 세션에 저장하여
             * 콜백 요청에서 검증하기 때문에 STATELESS로 설정하면 state mismatch 오류가 발생한다.
             */
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            /* CORS 설정 (API 체인과 동일한 corsConfigurationSource 빈 공유) */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /* 폼 로그인 비활성화 */
            .formLogin(formLogin -> formLogin.disable())

            /* HTTP Basic 비활성화 */
            .httpBasic(httpBasic -> httpBasic.disable())

            /*
             * OAuth2 소셜 로그인 설정.
             * 성공 핸들러(SocialSuccessHandler): Refresh Token 생성 → HttpOnly 쿠키 저장 → /cookie 리다이렉트
             * 실패 핸들러(SocialFailureHandler): 에러 메시지 URL 인코딩 → /login?error=... 리다이렉트
             */
            .oauth2Login(oauth2 -> oauth2
                .successHandler(socialSuccessHandler)
                .failureHandler(socialFailureHandler)
            )

            /* 이 체인이 다루는 경로는 모두 인증 없이 접근 가능 (OAuth2 흐름 자체가 인증 과정) */
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            );

        return http.build();
    }

    // ════════════════════════════════════════════════════════════════
    // FilterChain 2: REST API 전용 (Order=2, 캐치올)
    // ════════════════════════════════════════════════════════════════

    /**
     * REST API 전용 SecurityFilterChain.
     *
     * <p>OAuth2 체인({@code @Order(1)})이 처리하지 않는 모든 경로를 담당한다.
     * JWT 기반 Stateless 인증으로 운영된다.</p>
     *
     * <p><b>세션 정책 — STATELESS</b>: JWT 토큰으로만 인증하며 서버에 세션을 생성하지 않는다.
     * 요청마다 Authorization 헤더의 Bearer 토큰을 검증한다.</p>
     *
     * <h3>필터 등록 순서</h3>
     * <ol>
     *   <li>{@link JwtAuthenticationFilter} — {@link LogoutFilter} 앞
     *       (로그아웃 요청에도 JWT 인증 컨텍스트가 먼저 설정되어야 한다)</li>
     *   <li>{@link LoginFilter} — {@link UsernamePasswordAuthenticationFilter} 앞
     *       (JSON Body에서 이메일/비밀번호를 읽어 UsernamePasswordAuthenticationToken 생성)</li>
     *   <li>{@link ServiceKeyAuthFilter} — {@link UsernamePasswordAuthenticationFilter} 앞
     *       (AI Agent의 X-Service-Key 헤더 인증)</li>
     * </ol>
     *
     * @param http                  HttpSecurity 빌더
     * @param authenticationManager AuthenticationManager (LoginFilter에서 사용)
     * @return REST API 전용 SecurityFilterChain
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                               AuthenticationManager authenticationManager) throws Exception {
        http
            /* CSRF 비활성화 (REST API + JWT로 CSRF 방어 불필요) */
            .csrf(csrf -> csrf.disable())

            /*
             * 세션 정책: STATELESS
             * JWT 기반 인증. 요청마다 토큰을 검증하며 서버 세션을 일절 사용하지 않는다.
             * HttpSession을 생성하거나 조회하지 않으므로 메모리 효율이 높고
             * 수평 확장(스케일아웃)에 유리하다.
             */
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            /* CORS 설정 (OAuth2 체인과 동일한 corsConfigurationSource 빈 공유) */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /* 폼 로그인 비활성화 (LoginFilter로 대체) */
            .formLogin(formLogin -> formLogin.disable())

            /* HTTP Basic 비활성화 */
            .httpBasic(httpBasic -> httpBasic.disable())

            /*
             * 로그아웃 핸들러 등록 (API 체인에만 등록).
             * OAuth2 체인에는 로그아웃이 불필요하므로 여기서만 설정한다.
             * RefreshTokenLogoutHandler: 쿠키에서 Refresh Token 추출 → DB 화이트리스트 삭제 → 쿠키 만료
             */
            .logout(logout -> logout
                /* Spring Security 기본값(/logout)이 아닌 프로젝트 API 경로로 명시 */
                .logoutUrl("/api/v1/auth/logout")
                /* CookieUtil을 함께 전달 — 쿠키에서 RT 추출 + 로그아웃 후 쿠키 삭제 */
                .addLogoutHandler(new RefreshTokenLogoutHandler(jwtService, jwtTokenProvider, cookieUtil))
                /* 로그아웃 성공 응답: 200 OK + JSON */
                .logoutSuccessHandler((req, res, auth) -> {
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setContentType(AppConstants.CONTENT_TYPE_JSON);
                    res.getWriter().write("{\"message\":\"로그아웃 성공\"}");
                })
            )

            /* ── URL별 접근 권한 설정 ── */
            .authorizeHttpRequests(authorize -> authorize
                /* 헬스체크 */
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()

                /*
                 * Swagger/OpenAPI UI 및 API 문서 경로.
                 * SWAGGER_ENABLED=false(운영)이면 denyAll로 차단 (springdoc 비활성화와 이중 방어).
                 * SWAGGER_ENABLED=true(로컬/스테이징)이면 permitAll로 공개.
                 */
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**")
                    .access((authentication, context) ->
                        new org.springframework.security.authorization.AuthorizationDecision(swaggerEnabled))

                /* Dev Auth — 개발 전용 토큰 발급 (DEV_AUTH_ENABLED=true 일 때만 컨트롤러 빈 등록) */
                .requestMatchers("/api/v1/dev/**").permitAll()

                /* 인증 API (회원가입, 로그인, 소셜 로그인 등) */
                .requestMatchers("/api/v1/auth/**").permitAll()

                /* 관리자 전용 로그인 — AdminLoginFilter가 처리, permitAll 필요 */
                .requestMatchers("/api/v1/admin/auth/login").permitAll()

                /* JWT 토큰 교환/갱신 (KMG 패턴: OAuth2 성공 후 /jwt/exchange 호출) */
                .requestMatchers("/jwt/exchange", "/jwt/refresh").permitAll()
                     /* ✅ 추가 — 업로드 이미지 서빙 (비로그인 허용) */
                    .requestMatchers(HttpMethod.GET, "/images/**").permitAll()

                /* 로그인 관련 내부 경로 — OAuth2 체인에서 처리하지 못한 경우 대비 */
                .requestMatchers("/login", "/login/**").permitAll()

                /* 결제 웹훅 — Toss Payments 서버에서 호출, 인증 불가 */
                .requestMatchers(HttpMethod.POST, "/api/v1/payment/webhook").permitAll()

                /* 구독 플랜 조회 — 비로그인 사용자도 요금제 확인 가능 */
                .requestMatchers(HttpMethod.GET, "/api/v1/subscription/plans").permitAll()

                /* 게시글 조회 — 비로그인 허용 */
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()

                /* 고객센터 FAQ/도움말 조회 — 비로그인 허용 */
                .requestMatchers(HttpMethod.GET, "/api/v1/support/faq").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/support/help").permitAll()
                /* 고객센터 AI 챗봇 — 비로그인 허용 (플로팅 위젯/SupportPage 공용) */
                .requestMatchers(HttpMethod.POST, "/api/v1/support/chatbot").permitAll()

                /* 영화 조회 — 비로그인 허용 (상세, TMDB, 인기) */
                .requestMatchers(HttpMethod.GET, "/api/v1/movies/**").permitAll()

                /* 앱 메인 공지 조회 — 비로그인 허용 (사용자 홈 배너/팝업/모달) */
                .requestMatchers(HttpMethod.GET, "/api/v1/notices/**").permitAll()

                /* 나머지 모든 요청: 인증 필요 */
                .anyRequest().authenticated()
            )

            /* ── 필터 체인 등록 ── */

            /*
             * 1. JwtAuthenticationFilter → LogoutFilter 앞에 배치.
             * 로그아웃 요청(/api/v1/auth/logout)에서도 JWT 인증 컨텍스트가 먼저 설정되어야
             * RefreshTokenLogoutHandler에서 userId를 올바르게 참조할 수 있다.
             */
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                LogoutFilter.class
            )

            /*
             * 2. LoginFilter → UsernamePasswordAuthenticationFilter 앞에 배치.
             * POST /api/v1/auth/login 요청을 가로채 JSON Body에서 email/password를 읽는다.
             */
            .addFilterBefore(
                new LoginFilter(authenticationManager, loginSuccessHandler),
                UsernamePasswordAuthenticationFilter.class
            )

            /*
             * 2-1. AdminLoginFilter → UsernamePasswordAuthenticationFilter 앞에 배치.
             * POST /api/v1/admin/auth/login 전용. 인증 성공 후 ROLE_ADMIN 검증을 추가로 수행하여
             * 일반 사용자(ROLE_USER)에게는 403을 반환하고 관리자 JWT 발급을 차단한다.
             */
            .addFilterBefore(
                new AdminLoginFilter(authenticationManager, loginSuccessHandler),
                UsernamePasswordAuthenticationFilter.class
            )

            /*
             * 3. ServiceKeyAuthFilter → UsernamePasswordAuthenticationFilter 앞에 배치.
             * X-Service-Key 헤더가 있으면 AI Agent 내부 요청으로 인식하여 ROLE_SERVICE로 인증한다.
             * 헤더가 없으면 다음 필터(JWT)로 위임한다.
             */
            .addFilterBefore(
                new ServiceKeyAuthFilter(serviceKey),
                UsernamePasswordAuthenticationFilter.class
            );

            /*
             * 4. DevMasterKeyFilter → JwtAuthenticationFilter 앞에 배치 (개발 전용).
             * DEV_AUTH_ENABLED=true 일 때만 등록된다.
             * X-Dev-Master-Key 헤더로 간편하게 ADMIN 인증을 우회할 수 있다.
             * Swagger UI에서 Authorize → DevMasterKeyAuth에 키를 입력하면 모든 API 인증 통과.
             */
            if (devAuthEnabled) {
                http.addFilterBefore(
                    new DevMasterKeyFilter(devMasterKey),
                    JwtAuthenticationFilter.class
                );
            }

            http

            /*
             * 인증/인가 예외 처리 핸들러.
             * authenticationEntryPoint: 인증되지 않은 요청 → 401 JSON
             * accessDeniedHandler: 권한이 부족한 요청 → 403 JSON
             */
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    /* 인증 실패 (401 Unauthorized) — S002 에러 코드 반환 */
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(AppConstants.CONTENT_TYPE_JSON);

                    ErrorResponse errorResponse = new ErrorResponse(
                        "S002",
                        "인증이 필요합니다. 로그인 후 다시 시도해주세요.",
                        Map.of()
                    );
                    OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    /* 접근 거부 (403 Forbidden) — S002 에러 코드 반환 */
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(AppConstants.CONTENT_TYPE_JSON);

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

    // ════════════════════════════════════════════════════════════════
    // 공유 빈: CORS 설정
    // ════════════════════════════════════════════════════════════════

    /**
     * CORS 설정 소스 (두 FilterChain이 공유).
     *
     * <p>OAuth2 체인과 API 체인 모두 이 빈을 참조하므로
     * 하나의 설정만 변경해도 전체에 일관 적용된다.</p>
     *
     * <p>{@code Set-Cookie} 헤더를 {@code ExposedHeaders}에 추가하여
     * OAuth2 쿠키 교환 시 클라이언트 JavaScript에서 쿠키를 확인할 수 있도록 한다.</p>
     *
     * <p>허용 오리진, 메서드, 헤더, 자격증명 포함 여부, 캐시 유효시간은
     * {@code application.yml} 또는 환경변수로 오버라이드 가능하다.</p>
     *
     * @return CorsConfigurationSource 인스턴스 (/** 전 경로에 동일 정책 적용)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        /* 허용 오리진: 콤마 구분 문자열을 리스트로 분리 (기본값: localhost:3000, localhost:5173) */
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

        /* 허용 HTTP 메서드 */
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        /* 허용 헤더: "*" 이면 모든 헤더 허용, 아니면 콤마 구분 목록으로 제한 */
        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(List.of("*"));
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }

        /* 자격증명(쿠키, Authorization 헤더) 포함 여부 */
        configuration.setAllowCredentials(allowCredentials);

        /* Preflight 응답 캐시 유효시간 (초) */
        configuration.setMaxAge(maxAge);

        /*
         * Authorization + Set-Cookie 헤더를 클라이언트에 노출.
         * Authorization: JWT 토큰 수신 시 클라이언트에서 읽기 가능.
         * Set-Cookie: OAuth2 성공 후 Refresh Token 쿠키를 클라이언트가 확인 가능.
         */
        configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));

        /* 모든 경로(/**)에 동일한 CORS 정책 적용 */
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
