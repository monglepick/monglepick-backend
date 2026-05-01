package com.monglepick.monglepickbackend.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * HttpOnly 쿠키 처리 유틸리티 컴포넌트.
 *
 * <p>모든 인증 흐름(로컬 로그인, 회원가입, OAuth2 소셜 로그인, 토큰 갱신, 로그아웃)에서
 * Refresh Token 쿠키 설정/삭제/추출을 단일 진입점으로 처리한다.</p>
 *
 * <p>Jakarta Servlet API의 {@link Cookie}는 SameSite 속성을 지원하지 않으므로
 * Spring의 {@link ResponseCookie}를 사용하여 Set-Cookie 헤더를 직접 추가한다.
 * ResponseCookie는 SameSite 속성을 포함한 RFC 6265 규격의 쿠키 생성을 지원한다.</p>
 *
 * <h3>쿠키 보안 속성</h3>
 * <ul>
 *   <li><b>HttpOnly</b>: true — 클라이언트 JavaScript에서 접근 불가 (XSS 방어)</li>
 *   <li><b>Secure</b>: 환경변수 APP_COOKIE_SECURE으로 제어 (운영 환경: true)</li>
 *   <li><b>SameSite</b>: Lax — CSRF 방어 (OAuth2 리다이렉트 허용)</li>
 *   <li><b>Path</b>: "/" — 모든 경로에서 쿠키 전송</li>
 *   <li><b>MaxAge</b>: refreshTokenExpiry(ms) / 1000 → 초 단위 변환</li>
 * </ul>
 *
 * @see JwtTokenProvider
 * @see SecurityConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CookieUtil {

    /** Refresh Token을 담는 쿠키 이름 — 모든 인증 흐름에서 이 이름을 사용 */
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    /** 관리자 Refresh Token을 담는 쿠키 이름 — 일반 사용자 세션과 분리 */
    private static final String ADMIN_REFRESH_TOKEN_COOKIE_NAME = "adminRefreshToken";

    /**
     * Secure 속성 제어 플래그.
     * <ul>
     *   <li>개발 환경 (HTTP): false (기본값)</li>
     *   <li>운영 환경 (HTTPS): true (APP_COOKIE_SECURE=true 환경변수)</li>
     * </ul>
     */
    @Value("${app.cookie.secure:false}")
    private boolean secure;

    /**
     * JWT 토큰 프로바이더 — Refresh Token 만료 시간(ms)을 가져오기 위해 주입.
     * 쿠키의 MaxAge는 refreshTokenExpiry(ms)를 초(s) 단위로 변환하여 설정한다.
     */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 응답에 Refresh Token HttpOnly 쿠키를 추가한다.
     *
     * <p>ResponseCookie를 사용하여 SameSite=Lax 속성을 포함한
     * Set-Cookie 헤더를 직접 추가한다. MaxAge는 JwtTokenProvider의
     * refreshTokenExpiry 값(ms → s 변환)을 사용하여 쿠키 만료와 토큰 만료를 동기화한다.</p>
     *
     * @param response     HTTP 응답 객체
     * @param refreshToken 저장할 Refresh Token 문자열
     */
    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addRefreshTokenCookie(response, REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    }

    /**
     * 응답에 관리자 Refresh Token HttpOnly 쿠키를 추가한다.
     *
     * <p>일반 사용자용 {@code refreshToken} 쿠키와 분리된 {@code adminRefreshToken}
     * 쿠키를 사용하여 관리자 로그인 상태가 사용자 로그인 상태를 덮어쓰지 않게 한다.</p>
     *
     * @param response     HTTP 응답 객체
     * @param refreshToken 저장할 관리자 Refresh Token 문자열
     */
    public void addAdminRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addRefreshTokenCookie(response, ADMIN_REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String cookieName, String refreshToken) {
        /* ms → s 변환: 쿠키 MaxAge는 초 단위 */
        long maxAgeSeconds = jwtTokenProvider.getRefreshTokenExpiry() / 1000;

        ResponseCookie cookie = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)           // JavaScript에서 document.cookie로 접근 불가 (XSS 방어)
                .secure(secure)           // HTTPS 전용 여부 (운영: true, 개발: false)
                .sameSite("Lax")          // CSRF 방어 + OAuth2 리다이렉트 허용
                .path("/")                // 모든 경로에서 쿠키 전송
                .maxAge(maxAgeSeconds)    // Refresh Token 만료 시간과 동기화
                .build();

        /* ResponseCookie.toString()은 "name=value; attributes" 형식의 Set-Cookie 헤더 값을 반환 */
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        log.debug("Refresh Token 쿠키 설정 완료 — name: {}, maxAge: {}초, secure: {}",
                cookieName, maxAgeSeconds, secure);
    }

    /**
     * 응답에 Refresh Token 쿠키 삭제용 빈 쿠키를 추가한다.
     *
     * <p>MaxAge=0으로 설정하면 클라이언트의 브라우저가 해당 쿠키를 즉시 삭제한다.
     * 로그아웃 처리 시 호출한다.</p>
     *
     * @param response HTTP 응답 객체
     */
    public void deleteRefreshTokenCookie(HttpServletResponse response) {
        deleteRefreshTokenCookie(response, REFRESH_TOKEN_COOKIE_NAME);
    }

    /**
     * 응답에 관리자 Refresh Token 쿠키 삭제용 빈 쿠키를 추가한다.
     *
     * @param response HTTP 응답 객체
     */
    public void deleteAdminRefreshTokenCookie(HttpServletResponse response) {
        deleteRefreshTokenCookie(response, ADMIN_REFRESH_TOKEN_COOKIE_NAME);
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response, String cookieName) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)   // MaxAge=0 → 브라우저가 즉시 쿠키 삭제
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        log.debug("Refresh Token 쿠키 삭제 — name: {}, Set-Cookie MaxAge=0 전송", cookieName);
    }

    /**
     * 요청에서 Refresh Token 쿠키 값을 추출한다.
     *
     * <p>쿠키 배열에서 이름이 "refreshToken"인 쿠키를 찾아 값을 반환한다.
     * 쿠키가 없거나 해당 이름의 쿠키가 없으면 null을 반환한다.</p>
     *
     * @param request HTTP 요청 객체
     * @return Refresh Token 문자열, 없으면 null
     */
    public String extractRefreshToken(HttpServletRequest request) {
        return extractRefreshToken(request, REFRESH_TOKEN_COOKIE_NAME);
    }

    /**
     * 요청에서 관리자 Refresh Token 쿠키 값을 추출한다.
     *
     * @param request HTTP 요청 객체
     * @return 관리자 Refresh Token 문자열, 없으면 null
     */
    public String extractAdminRefreshToken(HttpServletRequest request) {
        return extractRefreshToken(request, ADMIN_REFRESH_TOKEN_COOKIE_NAME);
    }

    private String extractRefreshToken(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        /* 쿠키가 아예 없는 경우 — null 반환 */
        if (cookies == null) {
            return null;
        }

        /* 스트림으로 "refreshToken" 이름의 쿠키 검색 */
        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
