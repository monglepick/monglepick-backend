package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.UserInfo;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService.JwtRefreshResult;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.CookieUtil;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 인증 보조 API.
 *
 * <p>관리자 로그인 자체는 {@link com.monglepick.monglepickbackend.domain.auth.filter.AdminLoginFilter}
 * 가 {@code POST /api/v1/admin/auth/login}에서 처리한다. 이 컨트롤러는 관리자 전용
 * refresh/logout 흐름만 담당한다.</p>
 */
@Tag(name = "관리자 인증", description = "관리자 Refresh Token 갱신 및 로그아웃")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtService jwtService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    public record AdminRefreshResponseBody(String accessToken, UserInfo user) {
    }

    public record AdminLogoutResponseBody(String message) {
    }

    /**
     * 관리자 Access Token 갱신.
     *
     * <p>{@code adminRefreshToken} 쿠키만 사용한다. 일반 사용자용 {@code refreshToken}
     * 쿠키와 분리하여 관리자 세션과 사용자 세션이 서로 덮어쓰지 않게 한다.</p>
     */
    @Operation(
            summary = "관리자 Access Token 갱신",
            description = "adminRefreshToken 쿠키로 관리자 Access/Refresh Token을 회전 발급"
    )
    @SecurityRequirement(name = "")
    @PostMapping("/refresh")
    public ResponseEntity<AdminRefreshResponseBody> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /api/v1/admin/auth/refresh — 관리자 Refresh Token 갱신");

        String refreshToken = cookieUtil.extractAdminRefreshToken(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_FOUND, "adminRefreshToken 쿠키가 없습니다.");
        }

        JwtRefreshResult result = jwtService.refreshRotateForRole(refreshToken, UserRole.ADMIN);
        cookieUtil.addAdminRefreshTokenCookie(response, result.newRefreshToken());

        User user = result.user();
        UserInfo userInfo = new UserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getProvider().name(),
                user.getUserRole().name()
        );

        return ResponseEntity.ok(new AdminRefreshResponseBody(result.newAccessToken(), userInfo));
    }

    /**
     * 관리자 로그아웃.
     *
     * <p>{@code adminRefreshToken}만 삭제한다. 일반 사용자용 {@code refreshToken} 쿠키는
     * 건드리지 않으므로 같은 브라우저의 일반 사용자 로그인 상태와 독립적으로 동작한다.</p>
     */
    @Operation(summary = "관리자 로그아웃", description = "adminRefreshToken 무효화 및 쿠키 삭제")
    @SecurityRequirement(name = "")
    @PostMapping("/logout")
    public ResponseEntity<AdminLogoutResponseBody> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /api/v1/admin/auth/logout — 관리자 로그아웃");

        String refreshToken = cookieUtil.extractAdminRefreshToken(request);
        if (StringUtils.hasText(refreshToken) && jwtTokenProvider.validateToken(refreshToken)) {
            jwtService.removeRefresh(refreshToken);
            log.info("관리자 로그아웃 — Refresh Token DB에서 삭제 완료");
        }

        cookieUtil.deleteAdminRefreshTokenCookie(response);
        return ResponseEntity.ok(new AdminLogoutResponseBody("관리자 로그아웃 성공"));
    }
}
