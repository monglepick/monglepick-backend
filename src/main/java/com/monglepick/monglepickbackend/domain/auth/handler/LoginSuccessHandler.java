package com.monglepick.monglepickbackend.domain.auth.handler;


import com.monglepick.monglepickbackend.domain.auth.service.JWTService;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.global.security.util.JWTUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Qualifier("LoginSuccessHandler")
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JWTService jwtService;
    private final UserRepository userRepository;

    public LoginSuccessHandler(JWTService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    // 인증 성공 시 호출되는 메서드
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 인증 객체에서 유저 이메일 추출
        String useremail = authentication.getName();

        // JWT(Access/Refresh) 토큰 발급
        String accessToken = JWTUtil.createJWT(useremail, true);
        String refreshToken = JWTUtil.createJWT(useremail, false);

        // 발급한 Refresh DB 테이블 저장 (Refresh whitelist 방식)
        jwtService.addRefresh(useremail, refreshToken);

        // 닉네임 조회
        String userNickname = userRepository.findByUserEmail(useremail)
                .map(user -> user.getUserNickname())
                .orElse("");

        // 응답 타입 설정
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Access 토큰, Refresh 토큰, 닉네임을 JSON 형태로 응답
        String json = String.format(
                "{\"accessToken\":\"%s\", \"refreshToken\":\"%s\", \"userNickname\":\"%s\"}",
                accessToken, refreshToken, userNickname
        );
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
