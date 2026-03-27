package com.monglepick.monglepickbackend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 개발 전용 마스터키 인증 필터.
 *
 * <p>Swagger UI에서 API 테스트 시 JWT 토큰 없이 마스터키 하나로
 * 즉시 인증을 우회할 수 있는 필터이다.</p>
 *
 * <h3>사용법 (Swagger UI)</h3>
 * <ol>
 *   <li>Swagger UI 상단 🔓 Authorize 버튼 클릭</li>
 *   <li><b>DevMasterKeyAuth</b> 필드에 마스터키 입력 (기본: {@code monglepick-dev-master-2026})</li>
 *   <li>Authorize 클릭 → 이후 모든 API 호출에 자동 인증 적용</li>
 * </ol>
 *
 * <h3>인증 흐름</h3>
 * <ol>
 *   <li>{@code X-Dev-Master-Key} 헤더가 없으면 → 다음 필터로 위임 (일반 JWT/ServiceKey 인증)</li>
 *   <li>헤더가 있고 마스터키와 일치 → ROLE_ADMIN 권한으로 SecurityContext에 인증 설정</li>
 *   <li>헤더가 있지만 불일치 → 무시하고 다음 필터로 위임 (JWT 인증 시도)</li>
 * </ol>
 *
 * <p><b>⚠ 개발 전용:</b> {@code DEV_AUTH_ENABLED=true}일 때만 SecurityConfig에서
 * 이 필터가 등록된다. 운영 환경에서는 필터 자체가 체인에 포함되지 않는다.</p>
 *
 * @see SecurityConfig
 */
@Slf4j
public class DevMasterKeyFilter extends OncePerRequestFilter {

    /** 마스터키를 전달하는 HTTP 헤더 이름 */
    public static final String HEADER_NAME = "X-Dev-Master-Key";

    /** 마스터키 인증 시 사용하는 기본 사용자 ID */
    private static final String DEV_USER_ID = "dev-master-admin";

    /** 마스터키 인증 시 부여되는 권한 */
    private static final String DEV_ROLE = "ROLE_ADMIN";

    /** 환경변수 또는 application.yml에서 주입받은 마스터키 값 */
    private final String masterKey;

    /**
     * DevMasterKeyFilter 생성자.
     *
     * @param masterKey 유효한 마스터키 값
     *                  (application.yml의 {@code dev.master-key} 또는
     *                   환경변수 {@code DEV_MASTER_KEY}에서 가져옴)
     */
    public DevMasterKeyFilter(String masterKey) {
        this.masterKey = masterKey;
    }

    /**
     * 매 요청마다 실행되는 마스터키 인증 로직.
     *
     * <p>{@code X-Dev-Master-Key} 헤더를 확인하여 마스터키와 일치하면
     * ADMIN 권한으로 즉시 인증한다. 헤더가 없거나 불일치하면
     * 다음 필터(ServiceKey/JWT)로 위임한다.</p>
     *
     * @param request     HTTP 요청 객체
     * @param response    HTTP 응답 객체
     * @param filterChain 다음 필터로 요청을 전달하기 위한 체인
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // ── 1단계: X-Dev-Master-Key 헤더 읽기 ──
        String requestKey = request.getHeader(HEADER_NAME);

        // ── 2단계: 헤더가 없으면 일반 요청 → 다음 필터(JWT 등)로 위임 ──
        if (requestKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── 3단계: 마스터키 일치 → ADMIN 인증 즉시 설정 ──
        if (masterKey.equals(requestKey)) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            DEV_USER_ID,                                        // principal: 개발용 관리자 ID
                            null,                                               // credentials: 불필요
                            List.of(new SimpleGrantedAuthority(DEV_ROLE))       // 권한: ADMIN
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("[Dev Master Key] 인증 성공 — userId: {}, role: ADMIN, URI: {}",
                    DEV_USER_ID, request.getRequestURI());

            filterChain.doFilter(request, response);
            return;
        }

        // ── 4단계: 마스터키 불일치 → 무시하고 다음 필터로 위임 (JWT 인증 시도) ──
        log.debug("[Dev Master Key] 키 불일치 — JWT 인증으로 폴백, URI: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
