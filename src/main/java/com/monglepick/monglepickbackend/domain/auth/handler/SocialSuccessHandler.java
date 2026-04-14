package com.monglepick.monglepickbackend.domain.auth.handler;

import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.auth.service.LoginPostProcessor;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.security.CookieUtil;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * OAuth2 소셜 로그인 성공 핸들러.
 *
 * <p>KMG 프로젝트의 SocialSuccessHandler 패턴을 적용.
 * 소셜 로그인 성공 시 Refresh Token만 생성하여 HttpOnly 쿠키에 저장하고,
 * 클라이언트의 /cookie 페이지로 리다이렉트한다.
 * 클라이언트는 이후 /jwt/exchange를 호출하여 쿠키를 헤더 기반 JWT로 교환한다.</p>
 *
 * <p>쿠키 보안 수정: 기존의 수동 Cookie 생성 코드(MaxAge 10초, Secure false 하드코딩)를
 * CookieUtil 단일 유틸리티로 교체하였다. CookieUtil은 SameSite=Lax, ResponseCookie 기반으로
 * 쿠키를 생성하며 MaxAge를 refreshTokenExpiry와 동기화한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtService jwtService;

    /**
     * Refresh Token 쿠키 설정/삭제/추출을 담당하는 단일 유틸리티.
     * SameSite=Lax + HttpOnly + Secure(환경변수) + MaxAge(refreshTokenExpiry 동기화) 적용.
     */
    private final CookieUtil cookieUtil;

    /**
     * 회원가입 보너스 토스트 트리거를 위한 points_history 조회용 리포지토리.
     * {@code AuthService.loadUser()}가 신규 OAuth 사용자에게 이미 SIGNUP_BONUS를 지급한 상태이므로,
     * 본 핸들러는 "지급 이력이 방금 생성되었는지"만 판단해 프론트에 쿼리파람으로 전달한다.
     */
    private final PointsHistoryRepository pointsHistoryRepository;

    /**
     * 로그인 성공 후처리(2026-04-14) — users/admin 최종 로그인 시각 갱신 및
     * 관리자 접속 감사 로그 기록. OAuth 로그인도 동일 후처리를 수행하여 DAU/MAU 집계에
     * 누락이 생기지 않도록 한다.
     */
    private final LoginPostProcessor loginPostProcessor;

    /** 소셜 로그인 후 User 엔티티를 조회하여 LoginPostProcessor 에 전달하기 위함. */
    private final UserMapper userMapper;

    /** 프론트엔드 리다이렉트 URL (환경변수로 오버라이드 가능) */
    @Value("${app.oauth.redirect-url:http://localhost:5173/cookie}")
    private String redirectUrl;

    /**
     * "방금 가입" 으로 간주하는 시간 윈도우 (초).
     * loadUser() → onAuthenticationSuccess 까지 수 초 이내이므로 60초면 충분히 여유있다.
     * 윈도우를 초과한 SIGNUP_BONUS 이력은 기존 사용자의 재로그인이므로 토스트를 띄우지 않는다.
     */
    private static final long FRESH_SIGNUP_WINDOW_SECONDS = 60L;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        /* principal은 CustomOAuth2User.getName() = userId */
        String userId = authentication.getName();

        /* Refresh Token만 생성 (Access Token은 /jwt/exchange에서 발급) */
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        /* Refresh Token을 DB 화이트리스트에 저장 */
        jwtService.addRefresh(userId, refreshToken);

        /*
         * 로그인 성공 후처리 (2026-04-14 추가):
         *   - users.last_login_at 갱신 (DAU/MAU 집계용)
         *   - 해당 사용자가 ROLE_ADMIN 이면 admin.last_login_at 갱신 +
         *     admin_audit_logs ADMIN_LOGIN 기록.
         *
         * 소셜 로그인은 일반 사용자가 대부분이지만 관리자가 OAuth 로 로그인하는 경우도
         * 누락 없이 감사 로그를 남기기 위해 공통 후처리에 포함시킨다. User 조회 실패
         * 시에는 조용히 스킵한다.
         */
        try {
            User user = userMapper.findById(userId);
            if (user != null) {
                loginPostProcessor.processLogin(user);
            } else {
                log.warn("소셜 로그인 후처리: User 조회 실패 — userId={} (last_login_at 갱신 생략)", userId);
            }
        } catch (Exception e) {
            // 후처리 실패는 로그인 자체에 영향 주지 않도록 삼킨다.
            log.warn("소셜 로그인 후처리 중 예외 — userId={}, err={}", userId, e.getMessage());
        }

        /*
         * CookieUtil로 HttpOnly 쿠키 설정.
         * 기존 수동 코드(MaxAge 10초, Secure false 하드코딩)를 제거하고
         * CookieUtil 단일 진입점으로 통일한다.
         * MaxAge: refreshTokenExpiry(ms → s 변환), SameSite: Lax,
         * Secure: APP_COOKIE_SECURE 환경변수 적용
         */
        cookieUtil.addRefreshTokenCookie(response, refreshToken);

        /*
         * 신규 OAuth 가입자 판별 — points_history 에서 SIGNUP_BONUS 이력을 조회하여
         * created_at이 최근 FRESH_SIGNUP_WINDOW_SECONDS 이내이면 "방금 가입"으로 간주.
         *
         * 신규 가입이면 리다이렉트 URL에 ?signupBonus=<point_change> 쿼리 파라미터를 추가하여
         * 프론트엔드의 /cookie 페이지가 토스트를 띄울 수 있게 한다.
         *
         * 조회 실패(정책 미스·DB 장애 등)는 토스트만 못 띄우는 것이지 로그인 자체는 영향 없어야 하므로
         * try/catch 로 감싸서 조용히 무시한다.
         */
        int signupBonus = 0;
        try {
            Optional<PointsHistory> historyOpt = pointsHistoryRepository
                    .findByUserIdAndActionTypeAndReferenceId(userId, "SIGNUP_BONUS", "signup");
            if (historyOpt.isPresent()) {
                PointsHistory history = historyOpt.get();
                LocalDateTime threshold = LocalDateTime.now().minusSeconds(FRESH_SIGNUP_WINDOW_SECONDS);
                if (history.getCreatedAt() != null && history.getCreatedAt().isAfter(threshold)) {
                    signupBonus = history.getPointChange();
                    log.info("OAuth 신규 가입 감지 — signupBonus={}P 를 리다이렉트에 포함: userId={}",
                            signupBonus, userId);
                }
            }
        } catch (Exception e) {
            /* 토스트 트리거 실패는 로그인 성공에 영향 주지 않는다 */
            log.warn("OAuth 신규 가입 판별 중 예외 — 토스트 생략하고 정상 리다이렉트: userId={}, error={}",
                    userId, e.getMessage());
        }

        String target = (signupBonus > 0)
                ? redirectUrl + (redirectUrl.contains("?") ? "&" : "?") + "signupBonus=" + signupBonus
                : redirectUrl;

        /* 클라이언트의 /cookie 페이지로 리다이렉트 */
        response.sendRedirect(target);

        log.info("소셜 로그인 성공 — userId: {}, 리다이렉트: {}", userId, target);
    }
}
