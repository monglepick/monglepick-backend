package com.monglepick.monglepickbackend.domain.auth.service;

import com.monglepick.monglepickbackend.domain.auth.dto.JwtResponseDto;
import com.monglepick.monglepickbackend.domain.auth.entity.RefreshEntity;
import com.monglepick.monglepickbackend.domain.auth.repository.RefreshRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT 토큰 라이프사이클 관리 서비스.
 *
 * <p>KMG 프로젝트의 JWTService 패턴을 적용하여
 * Refresh Token Rotation, 쿠키→헤더 교환, 화이트리스트 관리를 처리한다.</p>
 *
 * <p>C-4 수정: RuntimeException → BusinessException으로 교체하여
 * GlobalExceptionHandler가 적절한 HTTP 상태 코드와 에러 응답을 반환하도록 함.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JwtService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;

    /**
     * Refresh Token을 갱신한다 (Rotation 패턴).
     *
     * <p>기존 Refresh Token을 DB 화이트리스트에서 삭제하고,
     * 새로운 Access Token + Refresh Token 쌍을 발급한다.</p>
     *
     * <p>C-4 수정: RuntimeException → BusinessException (REFRESH_TOKEN_NOT_FOUND, USER_NOT_FOUND)</p>
     *
     * @param refreshToken 기존 Refresh Token
     * @return 새로운 토큰 쌍 + 사용자 닉네임
     * @throws BusinessException 유효하지 않은 토큰 또는 화이트리스트에 없는 토큰
     */
    @Transactional
    public JwtResponseDto refreshRotate(String refreshToken) {
        /* 1. 토큰 서명 및 만료 검증 */
        JwtTokenProvider.ParsedToken parsed = jwtTokenProvider.parse(refreshToken);
        if (parsed == null || !parsed.isRefresh()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        /* 2. DB 화이트리스트 확인 (탈취된 토큰 재사용 방지) */
        if (!existsRefresh(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        /* 3. userId 추출 및 사용자 조회 */
        String userId = parsed.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        /* 4. 새 토큰 쌍 생성 */
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, user.getUserRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        /* 5. 기존 토큰 삭제 + 새 토큰 저장 (토큰 로테이션) */
        removeRefresh(refreshToken);
        addRefresh(userId, newRefreshToken);

        log.info("Refresh Token 갱신 완료 — userId: {}", userId);

        return new JwtResponseDto(newAccessToken, newRefreshToken, user.getNickname());
    }

    /**
     * OAuth2 소셜 로그인 후 쿠키의 Refresh Token을 헤더 기반 JWT로 교환한다.
     *
     * <p>C-4 수정: RuntimeException → BusinessException (COOKIE_NOT_FOUND, REFRESH_TOKEN_NOT_FOUND, USER_NOT_FOUND)</p>
     *
     * @param request  HTTP 요청 (쿠키 포함)
     * @param response HTTP 응답 (쿠키 삭제용)
     * @return 새로운 토큰 쌍 + 사용자 닉네임
     * @throws BusinessException 쿠키가 없거나 유효하지 않은 토큰
     */
    @Transactional
    public JwtResponseDto cookie2Header(HttpServletRequest request, HttpServletResponse response) {
        /* 1. 쿠키에서 Refresh Token 추출 */
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_FOUND);
        }

        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
                break;
            }
        }

        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_FOUND, "refreshToken 쿠키가 없습니다.");
        }

        /* 2. Refresh Token 검증 */
        JwtTokenProvider.ParsedToken parsed = jwtTokenProvider.parse(refreshToken);
        if (parsed == null || !parsed.isRefresh()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        /* 3. userId 추출 및 사용자 조회 */
        String userId = parsed.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        /* 4. 새 토큰 쌍 생성 */
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, user.getUserRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        /* 5. 기존 토큰 삭제 + 새 토큰 저장 (토큰 로테이션) */
        removeRefresh(refreshToken);
        refreshRepository.flush();
        addRefresh(userId, newRefreshToken);

        /* 6. 기존 쿠키 삭제 */
        Cookie refreshCookie = new Cookie("refreshToken", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);

        log.info("OAuth2 쿠키→헤더 교환 완료 — userId: {}", userId);

        return new JwtResponseDto(newAccessToken, newRefreshToken, user.getNickname());
    }

    /**
     * Refresh Token을 DB 화이트리스트에 추가한다.
     */
    @Transactional
    public void addRefresh(String userId, String refreshToken) {
        RefreshEntity entity = RefreshEntity.builder()
                .userId(userId)
                .refreshToken(refreshToken)
                .build();
        refreshRepository.save(entity);
    }

    /**
     * 해당 Refresh Token이 화이트리스트에 존재하는지 확인한다.
     */
    public boolean existsRefresh(String refreshToken) {
        return refreshRepository.existsByRefreshToken(refreshToken);
    }

    /**
     * Refresh Token을 화이트리스트에서 삭제한다 (무효화).
     */
    @Transactional
    public void removeRefresh(String refreshToken) {
        refreshRepository.deleteByRefreshToken(refreshToken);
    }

    /**
     * 특정 사용자의 모든 Refresh Token을 삭제한다 (계정 삭제/전체 로그아웃).
     */
    @Transactional
    public void removeAllRefreshByUser(String userId) {
        refreshRepository.deleteByUserId(userId);
    }
}
