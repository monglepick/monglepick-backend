package com.monglepick.monglepickbackend.domain.user.service;

import com.monglepick.monglepickbackend.domain.user.dto.UserResponseDTO;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MypageService {

    private final UserRepository userRepository;

    public UserResponseDTO getMyPage() {
        // JWT 필터에서 인증된 유저 이메일 추출
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByUserEmail(userEmail)
                .orElseThrow(()-> new RuntimeException("존재하지 않는 유저입니다." + userEmail));

        return UserResponseDTO.from(user);
    }
}
