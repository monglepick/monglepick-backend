package com.monglepick.monglepickbackend.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 전역 보안 설정.
 *
 * <p>몽글픽 백엔드의 보안 정책을 정의한다.
 * REST API 서버이므로 세션을 사용하지 않고(STATELESS),
 * CSRF를 비활성화하며, 모든 인증은 토큰 기반으로 처리한다.</p>
 *
 * <h3>인증 체계 (2단계)</h3>
 * <ol>
 *   <li><b>서비스 키 인증</b> ({@link ServiceKeyAuthFilter}):
 *       AI Agent 등 내부 서비스가 {@code X-Service-Key} 헤더로 인증</li>
 *   <li><b>JWT 인증</b> (미구현, 김민규 담당):
 *       클라이언트(브라우저)가 {@code Authorization: Bearer {token}} 헤더로 인증.
 *       JWT 필터 추가 시 ServiceKeyAuthFilter 앞에 배치할 것</li>
 * </ol>
 *
 * <h3>CORS 정책</h3>
 * <p>프론트엔드(monglepick-client)의 localhost:3000/5173 개발 서버와
 * 운영 도메인에서의 API 접근을 허용한다.
 * 운영 환경에서는 {@code CORS_ALLOWED_ORIGINS} 환경변수로 오버라이드한다.</p>
 *
 * <h3>현재 개발 상태</h3>
 * <p>개발 초기 단계이므로 대부분의 엔드포인트가 {@code permitAll()}로 설정되어 있다.
 * JWT 인증 구현 완료 후 {@code authenticated()}로 변경 예정이다.</p>
 *
 * @see ServiceKeyAuthFilter
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 서비스 간 내부 통신용 API 키.
     * application.yml의 {@code app.service.key} 또는
     * 환경변수 {@code SERVICE_API_KEY}에서 주입받는다.
     */
    @Value("${app.service.key:dev-service-key-change-me}")
    private String serviceKey;

    // ── CORS 관련 설정값 (application.yml에서 주입) ──

    /** 허용할 Origin 목록 (쉼표 구분 문자열) */
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    /** 허용할 HTTP 메서드 목록 (쉼표 구분 문자열) */
    @Value("${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String allowedMethods;

    /** 허용할 HTTP 헤더 목록 (쉼표 구분 문자열, "*"이면 모든 헤더 허용) */
    @Value("${cors.allowed-headers:*}")
    private String allowedHeaders;

    /** 자격증명(쿠키, Authorization 헤더) 포함 허용 여부 */
    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    /** Preflight 요청 캐시 시간 (초) */
    @Value("${cors.max-age:3600}")
    private long maxAge;

    /** 401 응답 JSON 템플릿 (ObjectMapper 미사용, jackson 의존성 불필요) */
    private static final String UNAUTHORIZED_JSON =
            "{\"code\":\"S002\",\"message\":\"인증이 필요합니다. 로그인 후 다시 시도해주세요.\",\"details\":{}}";

    /**
     * Spring Security 필터 체인 설정.
     *
     * <p>HTTP 보안 정책의 핵심 구성을 정의한다:</p>
     * <ul>
     *   <li>CSRF 비활성화 (REST API, 세션 미사용)</li>
     *   <li>세션 정책: STATELESS (JWT/서비스키 기반 인증)</li>
     *   <li>CORS: {@link #corsConfigurationSource()}에서 정의한 정책 적용</li>
     *   <li>폼 로그인, HTTP Basic 비활성화</li>
     *   <li>ServiceKeyAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 배치</li>
     *   <li>인증 실패 시 401 JSON 응답 반환</li>
     * </ul>
     *
     * @param http Spring Security의 HttpSecurity 빌더
     * @return 구성 완료된 SecurityFilterChain
     * @throws Exception 보안 설정 중 발생하는 예외
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ── CSRF 비활성화 (REST API, 세션 미사용이므로 CSRF 불필요) ──
                .csrf(csrf -> csrf.disable())

                // ── 세션 관리: STATELESS (서버에 세션을 저장하지 않음) ──
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ── CORS 설정 적용 ──
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── 폼 로그인 비활성화 (REST API이므로 로그인 페이지 불필요) ──
                .formLogin(formLogin -> formLogin.disable())

                // ── HTTP Basic 인증 비활성화 (토큰 기반 인증 사용) ──
                .httpBasic(httpBasic -> httpBasic.disable())

                // ── URL별 접근 권한 설정 ──
                .authorizeHttpRequests(authorize -> authorize
                        // 헬스체크: 인증 없이 접근 가능
                        .requestMatchers("GET", "/health").permitAll()

                        // 결제 웹훅: PG사에서 직접 호출하므로 인증 불필요
                        .requestMatchers("POST", "/api/v1/payment/webhook").permitAll()

                        // 구독 플랜 조회: 비로그인 사용자도 플랜 확인 가능
                        .requestMatchers("GET", "/api/v1/subscription/plans").permitAll()

                        // 포인트 API: ServiceKeyAuthFilter가 X-Service-Key 인증 처리
                        // 클라이언트 호출은 JWT 인증 필요 (김민규 JWT 필터 추가 후 활성화)
                        // TODO: JWT 필터 추가 후 hasAnyRole("SERVICE", "USER")로 변경
                        .requestMatchers("/api/v1/point/**").permitAll()

                        // 결제 API: 개발 중 임시 permitAll
                        // TODO: JWT 인증 추가 후 authenticated()로 변경
                        .requestMatchers("/api/v1/payment/**").permitAll()

                        // 구독 API: 개발 중 임시 permitAll
                        // TODO: JWT 인증 추가 후 authenticated()로 변경
                        .requestMatchers("/api/v1/subscription/**").permitAll()

                        // 나머지 모든 요청: 개발 중 임시 permitAll
                        // TODO: JWT 인증 추가 후 authenticated()로 변경
                        //       .anyRequest().authenticated()
                        .anyRequest().permitAll()
                )

                // ── 서비스 키 인증 필터 등록 ──
                // UsernamePasswordAuthenticationFilter 앞에 배치하여
                // X-Service-Key 헤더가 있는 요청을 우선 처리한다.
                // JWT 필터 추가 시: ServiceKeyAuthFilter → JwtAuthFilter → UsernamePasswordAuthenticationFilter 순서
                .addFilterBefore(
                        new ServiceKeyAuthFilter(serviceKey),
                        UsernamePasswordAuthenticationFilter.class
                )

                // ── 인증 실패 처리 (401 Unauthorized JSON 응답) ──
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");

                            // ErrorCode.UNAUTHORIZED(S002) — ObjectMapper 미사용으로 직접 JSON 작성
                            response.getWriter().write(UNAUTHORIZED_JSON);
                        })
                );

        return http.build();
    }

    /**
     * CORS(Cross-Origin Resource Sharing) 설정 소스.
     *
     * <p>프론트엔드(monglepick-client)에서 백엔드 API로의 교차 출처 요청을 허용한다.
     * application.yml의 {@code cors.*} 속성에서 설정값을 읽어온다.</p>
     *
     * <h3>기본 허용 Origin (개발 환경)</h3>
     * <ul>
     *   <li>{@code http://localhost:3000} — React 개발 서버 (CRA)</li>
     *   <li>{@code http://localhost:5173} — Vite 개발 서버</li>
     * </ul>
     *
     * <h3>운영 환경 오버라이드</h3>
     * <pre>{@code
     * CORS_ALLOWED_ORIGINS=https://monglepick.com,https://www.monglepick.com
     * }</pre>
     *
     * @return CORS 설정이 등록된 UrlBasedCorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용 Origin 설정 (쉼표 구분 문자열 → 리스트 변환)
        configuration.setAllowedOrigins(
                Arrays.asList(allowedOrigins.split(","))
        );

        // 허용 HTTP 메서드 설정
        configuration.setAllowedMethods(
                Arrays.asList(allowedMethods.split(","))
        );

        // 허용 헤더 설정 ("*"이면 모든 헤더 허용)
        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(List.of("*"));
        } else {
            configuration.setAllowedHeaders(
                    Arrays.asList(allowedHeaders.split(","))
            );
        }

        // 자격증명(쿠키, Authorization 헤더) 포함 허용
        configuration.setAllowCredentials(allowCredentials);

        // Preflight 요청 캐시 시간 (초)
        configuration.setMaxAge(maxAge);

        // 응답에서 클라이언트가 접근 가능한 헤더 목록
        // (X-Service-Key는 서버간 내부 통신 전용이므로 클라이언트에 노출하지 않음)
        configuration.setExposedHeaders(
                List.of("Authorization")
        );

        // 모든 경로(/**)에 위 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
