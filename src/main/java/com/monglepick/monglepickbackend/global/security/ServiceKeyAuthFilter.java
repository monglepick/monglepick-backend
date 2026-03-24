package com.monglepick.monglepickbackend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 서비스 간 내부 통신용 API 키 인증 필터.
 *
 * <p>AI Agent(monglepick-agent) 등 내부 서비스가 Spring Boot 백엔드의
 * 포인트 차감/조회 API를 호출할 때 사용하는 인증 메커니즘이다.
 * 클라이언트(브라우저)가 아닌 서버-서버 간 통신에서 JWT 대신 사용한다.</p>
 *
 * <h3>인증 흐름</h3>
 * <ol>
 *   <li>{@code X-Service-Key} 헤더가 <b>없으면</b> → 필터를 통과시킨다
 *       (일반 사용자 요청이므로 JWT 필터가 후속 처리)</li>
 *   <li>{@code X-Service-Key} 헤더가 <b>있고 일치</b> → {@code ROLE_SERVICE} 권한으로
 *       {@link SecurityContextHolder}에 인증 정보를 설정한다</li>
 *   <li>{@code X-Service-Key} 헤더가 <b>있지만 불일치</b> → 401 Unauthorized 응답을
 *       JSON 형태로 즉시 반환하고, 이후 필터 체인을 실행하지 않는다</li>
 * </ol>
 *
 * <h3>사용 예시 (AI Agent → Spring Boot)</h3>
 * <pre>{@code
 * POST /api/v1/point/deduct
 * X-Service-Key: dev-service-key-change-me
 * Content-Type: application/json
 *
 * {"user_id": "user123", "amount": 100, "reason": "AI 추천 1회"}
 * }</pre>
 *
 * <p><b>주의:</b> 이 필터는 {@code @Component}로 등록하지 않으며,
 * {@link SecurityConfig}에서 {@code new ServiceKeyAuthFilter(serviceKey)}로
 * 수동 인스턴스화하여 필터 체인에 추가한다.</p>
 *
 * @see SecurityConfig#filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity)
 * @see ErrorResponse
 */
public class ServiceKeyAuthFilter extends OncePerRequestFilter {

    /** 요청 헤더에서 서비스 키를 읽을 때 사용하는 헤더 이름 */
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    /** 401 응답 시 사용할 에러 코드 (ErrorCode.INVALID_SERVICE_KEY와 동일) */
    private static final String ERROR_CODE = "S001";

    /** 401 응답 시 사용할 에러 메시지 */
    private static final String ERROR_MESSAGE = "유효하지 않은 서비스 키입니다";

    /** JSON 직렬화를 위한 ObjectMapper (스레드 안전, 클래스 로딩 시 1회 초기화) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 환경변수 또는 application.yml에서 주입받은 서비스 키.
     * AI Agent 등 내부 서비스가 보내는 X-Service-Key 헤더 값과 비교한다.
     */
    private final String serviceKey;

    /**
     * 서비스 키 인증 필터 생성자.
     *
     * @param serviceKey 유효한 서비스 키 값
     *                   (application.yml의 {@code app.service.key} 또는
     *                    환경변수 {@code SERVICE_API_KEY}에서 가져옴)
     */
    public ServiceKeyAuthFilter(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    /**
     * 매 요청마다 실행되는 필터 로직.
     *
     * <p>{@code X-Service-Key} 헤더의 존재 여부와 값을 확인하여
     * 서비스 인증, 통과, 또는 거부를 결정한다.</p>
     *
     * @param request     HTTP 요청 객체
     * @param response    HTTP 응답 객체
     * @param filterChain 다음 필터로 요청을 전달하기 위한 체인
     * @throws ServletException 서블릿 처리 중 예외
     * @throws IOException      I/O 처리 중 예외
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // ── 1단계: X-Service-Key 헤더 읽기 ──
        String requestServiceKey = request.getHeader(SERVICE_KEY_HEADER);

        // ── 2단계: 헤더가 없으면 일반 사용자 요청 → 다음 필터(JWT 등)로 위임 ──
        if (requestServiceKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── 3단계: 헤더가 있고 서비스 키가 일치 → ROLE_SERVICE 인증 설정 ──
        if (serviceKey.equals(requestServiceKey)) {
            // "service"라는 principal로 인증 토큰 생성 (비밀번호 불필요)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "service",                                          // principal: 서비스 식별자
                            null,                                               // credentials: 불필요
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE")) // 서비스 전용 권한
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }

        // ── 4단계: 헤더가 있지만 서비스 키 불일치 → 401 Unauthorized 즉시 반환 ──
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        // ErrorResponse 형태의 JSON 응답 작성
        ErrorResponse errorResponse = new ErrorResponse(
                ERROR_CODE,
                ERROR_MESSAGE,
                Map.of()    // 추가 상세 정보 없음
        );
        OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
        // filterChain.doFilter()를 호출하지 않으므로 여기서 요청 처리 종료
    }
}
