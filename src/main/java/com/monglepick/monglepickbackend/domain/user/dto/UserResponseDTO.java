package com.monglepick.monglepickbackend.domain.user.dto;

import com.monglepick.monglepickbackend.domain.user.entity.User;

public record UserResponseDTO(String userEmail, Boolean isSocial, String userNickname, String profileImg) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getUserEmail(),
                user.getIsSocial(),
                user.getUserNickname(),
                user.getProfileImg()
        );
    }
}