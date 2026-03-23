package com.monglepick.monglepickbackend.domain.user.dto;

import com.monglepick.monglepickbackend.domain.user.entity.User;

/**
 * 사용자 정보 응답 DTO
 *
 * <p>프로필 조회 시 반환되는 사용자 정보입니다.
 * 비밀번호 등 민감한 정보는 포함하지 않습니다.</p>
 *
 * @param id 사용자 ID
 * @param email 이메일 주소
 * @param nickname 닉네임
 * @param profileImage 프로필 이미지 URL (null 가능)
 */
public record UserResponse(
        Long id,
        String email,
        String nickname,
        String profileImage
) {
    /**
     * User 엔티티로부터 UserResponse를 생성하는 팩토리 메서드
     *
     * @param user User 엔티티
     * @return UserResponse 인스턴스
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage()
        );
    }
}
