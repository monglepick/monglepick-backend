package com.monglepick.monglepickbackend.domain.auth.dto;

public record JWTResponseDTO(String accessToken, String refreshToken, String userNickname) {
}