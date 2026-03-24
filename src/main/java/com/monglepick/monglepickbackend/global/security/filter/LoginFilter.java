package com.monglepick.monglepickbackend.global.security.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;


/**
 * JSON 형식의 로그인 요청을 처리하는 커스텀 인증 필터
 * AbstractAuthenticationProcessingFilter를 상속받아 /login POST 요청을 가로챔
 */

public class LoginFilter extends AbstractAuthenticationProcessingFilter {

    // JSON 요청 body에서 사용할 key 이름 상수 정의
    public static final String SPRING_SECURITY_FORM_USEREMAIL_KEY = "useremail";
    public static final String SPRING_SECURITY_FORM_USERPASSWORD_KEY = "userpassword";

    // /login POST 요청만 이 필터가 처리하도록 매처 설정
    private static final RequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/v1/auth/login");

    // 실제로 loginMap에서 값을 꺼낼 때 사용할 파라미터 key
    private String useremailParameter = SPRING_SECURITY_FORM_USEREMAIL_KEY;
    private String userpasswordParameter = SPRING_SECURITY_FORM_USERPASSWORD_KEY;

    // 로그인 핸들러 추가
    private final AuthenticationSuccessHandler authenticationSuccessHandler;

    // AuthenticationManager를 주입받아 부모 클래스에 전달
    public LoginFilter(AuthenticationManager authenticationManager, AuthenticationSuccessHandler loginSuccessHandler) {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER, authenticationManager);
        this.authenticationSuccessHandler = loginSuccessHandler;
    }
    /**
     * 실제 인증 시도 메소드
     * HTTP 요청의 body에서 JSON을 파싱해 이메일/비밀번호를 추출 후 인증 토큰 생성
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        if (!request.getMethod().equals("POST")) {// POST 요청이 아니면 예외 발생
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }

        Map<String, String> loginMap;

        try { // request body를 String으로 읽은 뒤 Map으로 역직렬화
            ObjectMapper objectMapper = new ObjectMapper();
            ServletInputStream inputStream = request.getInputStream();
            String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            loginMap = objectMapper.readValue(messageBody, new TypeReference<>() {
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Map에서 useremail 추출, null이면 빈 문자열로 처리
        String useremail = loginMap.get(useremailParameter);
        useremail = (useremail != null) ? useremail.trim() : "";
        // Map에서 userpassword 추출, null이면 빈 문자열로 처리
        String userpassword = loginMap.get(userpasswordParameter);
        userpassword = (userpassword != null) ? userpassword : "";

        // 미인증 상태의 인증 토큰 생성 (인증 전 단계)
        UsernamePasswordAuthenticationToken authRequest = UsernamePasswordAuthenticationToken.unauthenticated(useremail,
                userpassword);
        setDetails(request, authRequest);
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    protected void setDetails(HttpServletRequest request, UsernamePasswordAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }

    //로그인 핸들로 추가
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                            Authentication authResult) throws IOException, ServletException {
        authenticationSuccessHandler.onAuthenticationSuccess(request, response, authResult);
    }

}