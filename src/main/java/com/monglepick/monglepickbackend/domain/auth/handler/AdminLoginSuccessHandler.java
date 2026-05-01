package com.monglepick.monglepickbackend.domain.auth.handler;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.UserInfo;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.auth.service.LoginPostProcessor;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.CookieUtil;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 관리자 로컬 로그인 성공 핸들러.
 *
 * <p>일반 로그인 성공 핸들러와 달리 {@code adminRefreshToken} 쿠키를 사용하여
 * 관리자 로그인 상태가 일반 사용자 로그인 상태를 덮어쓰지 않게 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final CookieUtil cookieUtil;
    private final LoginPostProcessor loginPostProcessor;

    private record AdminLoginResponse(String accessToken, UserInfo user) {
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String email = authentication.getName();
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getUserRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ONLY);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getUserRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        jwtService.addRefresh(user.getUserId(), refreshToken);
        loginPostProcessor.processLogin(user);
        cookieUtil.addAdminRefreshTokenCookie(response, refreshToken);

        UserInfo userInfo = new UserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getProvider().name(),
                user.getUserRole().name()
        );

        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), new AdminLoginResponse(accessToken, userInfo));

        log.info("관리자 로그인 성공 — userId: {}, email: {}", user.getUserId(), email);
    }
}
