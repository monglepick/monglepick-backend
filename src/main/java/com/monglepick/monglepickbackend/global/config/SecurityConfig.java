package com.monglepick.monglepickbackend.global.config;

import com.monglepick.monglepickbackend.global.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정 클래스
 *
 * <p>JWT 기반 무상태(Stateless) 인증을 구성합니다.
 * 세션을 사용하지 않으며, 모든 요청에서 JWT 토큰을 검증합니다.</p>
 *
 * <p>인증이 필요 없는 경로:</p>
 * <ul>
 *   <li>/api/v1/auth/** - 회원가입, 로그인, 토큰 갱신</li>
 *   <li>GET /api/v1/movies/** - 영화 정보 조회 (비로그인 허용)</li>
 *   <li>GET /api/v1/posts/** - 커뮤니티 게시글 조회 (비로그인 허용)</li>
 *   <!-- 검색 API(/api/v1/search/**)는 monglepick-recommend로 이관되어 삭제됨 -->
 * </ul>
 *
 * <p>그 외 모든 경로는 JWT 인증이 필요합니다.</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT 인증 필터 - 매 요청마다 Bearer 토큰을 검증 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Security 필터 체인 설정
     *
     * <p>CSRF 비활성화 (JWT 사용으로 불필요),
     * 세션 무상태 모드, 경로별 권한 설정,
     * JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 배치합니다.</p>
     *
     * @param http HttpSecurity 빌더
     * @return 구성된 SecurityFilterChain
     * @throws Exception Security 설정 실패 시
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (JWT 기반 인증에서는 불필요)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 관리: 무상태 모드 (서버에 세션 저장 안 함)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 경로별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 인증 API: 누구나 접근 가능 (회원가입, 로그인, 토큰 갱신)
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // 영화 조회 API: 비로그인 사용자도 조회 가능
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies/**").permitAll()

                        // 커뮤니티 게시글 조회: 비로그인 사용자도 읽기 가능
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()

                        // 리뷰 조회: 비로그인 사용자도 읽기 가능
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/**").permitAll()

                        // ※ 검색 API(/api/v1/search/**)는 monglepick-recommend(FastAPI)로 이관되어 삭제됨

                        // 헬스체크: 누구나 접근 가능
                        .requestMatchers("/health", "/actuator/**").permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 인증 필터를 Spring Security 기본 필터 앞에 배치
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 비밀번호 암호화기 빈 등록
     *
     * <p>BCrypt 해싱 알고리즘을 사용하여 비밀번호를 안전하게 저장합니다.
     * 기본 강도(strength=10)를 사용합니다.</p>
     *
     * @return BCryptPasswordEncoder 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager 빈 등록
     *
     * <p>Spring Security의 인증 매니저를 빈으로 노출하여
     * AuthService에서 프로그래밍 방식으로 인증을 수행할 수 있도록 합니다.</p>
     *
     * @param authConfig 인증 설정
     * @return AuthenticationManager 인스턴스
     * @throws Exception 인증 매니저 생성 실패 시
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
