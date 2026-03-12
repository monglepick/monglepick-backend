package com.monglepick.monglepickbackend.domain.auth.dto;

/**
 * JWT 토큰 응답 DTO
 *
 * @param accessToken 액세스 토큰 (1시간)
 * @param refreshToken 리프레시 토큰 (7일)
 */
public record TokenResponse(
        String accessToken,
        String refreshToken
) {}
