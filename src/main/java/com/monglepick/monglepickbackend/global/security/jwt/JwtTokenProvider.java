package com.monglepick.monglepickbackend.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 생성 및 검증 유틸리티
 *
 * <p>io.jsonwebtoken(jjwt) 라이브러리를 사용하여 JWT 토큰의
 * 생성, 파싱, 검증, 사용자 ID 추출 기능을 제공합니다.</p>
 *
 * <p>토큰 구조:</p>
 * <ul>
 *   <li>subject: 사용자 ID (Long → String 변환)</li>
 *   <li>type: 토큰 유형 ("access" 또는 "refresh")</li>
 *   <li>iat: 발급 시간</li>
 *   <li>exp: 만료 시간</li>
 * </ul>
 *
 * <p>서명 알고리즘: HS256 (HMAC-SHA256)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    /** JWT 설정 프로퍼티 (비밀키, 만료시간) */
    private final JwtProperties jwtProperties;

    /**
     * 비밀키로부터 HMAC-SHA 서명 키를 생성합니다.
     *
     * <p>application.yml의 jwt.secret 문자열을 바이트 배열로 변환하여
     * HS256 서명에 사용할 SecretKey 객체를 생성합니다.</p>
     *
     * @return HMAC-SHA 서명 키
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 액세스 토큰을 생성합니다.
     *
     * <p>사용자 ID를 subject로 설정하고, type 클레임을 "access"로 지정합니다.
     * 만료 시간은 jwt.expiration 설정값을 따릅니다.</p>
     *
     * @param userId 사용자 ID
     * @return 생성된 JWT 액세스 토큰 문자열
     */
    public String generateAccessToken(Long userId) {
        Date now = new Date();
        // 현재 시간 + 설정된 만료 시간 (기본 1시간)
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                // subject: 사용자 식별자
                .subject(userId.toString())
                // 커스텀 클레임: 토큰 유형 구분
                .claim("type", "access")
                // 발급 시간
                .issuedAt(now)
                // 만료 시간
                .expiration(expiry)
                // HS256 서명
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 리프레시 토큰을 생성합니다.
     *
     * <p>액세스 토큰과 동일한 구조이지만, type이 "refresh"이고
     * 만료 시간이 더 깁니다 (기본 7일).</p>
     *
     * @param userId 사용자 ID
     * @return 생성된 JWT 리프레시 토큰 문자열
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        // 현재 시간 + 리프레시 토큰 만료 시간 (기본 7일)
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpiration());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     *
     * <p>토큰을 파싱하여 subject(사용자 ID)를 Long 타입으로 변환하여 반환합니다.</p>
     *
     * @param token JWT 토큰 문자열
     * @return 사용자 ID
     */
    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * JWT 토큰의 유효성을 검증합니다.
     *
     * <p>다음 항목을 검증합니다:</p>
     * <ul>
     *   <li>서명 무결성 (비밀키 일치 여부)</li>
     *   <li>토큰 만료 여부</li>
     *   <li>토큰 형식 유효성</li>
     * </ul>
     *
     * @param token 검증할 JWT 토큰 문자열
     * @return 유효하면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            // 토큰 파싱 및 서명 검증 수행
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException e) {
            // 서명이 유효하지 않은 경우
            log.warn("잘못된 JWT 서명입니다: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            // JWT 형식이 올바르지 않은 경우
            log.warn("잘못된 JWT 토큰 형식입니다: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            // 토큰이 만료된 경우
            log.warn("만료된 JWT 토큰입니다: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            // 지원하지 않는 JWT인 경우
            log.warn("지원하지 않는 JWT 토큰입니다: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // 토큰이 비어있는 경우
            log.warn("JWT 클레임이 비어있습니다: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 토큰이 리프레시 토큰인지 확인합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 리프레시 토큰이면 true
     */
    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);
        return "refresh".equals(claims.get("type", String.class));
    }

    /**
     * JWT 토큰의 클레임(payload)을 파싱합니다.
     *
     * <p>내부 헬퍼 메서드로, 서명 검증 후 페이로드를 반환합니다.</p>
     *
     * @param token JWT 토큰 문자열
     * @return 파싱된 Claims 객체
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
