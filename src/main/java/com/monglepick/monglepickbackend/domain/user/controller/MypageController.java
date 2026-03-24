package com.monglepick.monglepickbackend.domain.user.controller;

import com.monglepick.monglepickbackend.domain.user.dto.UserResponseDTO;
import com.monglepick.monglepickbackend.domain.user.service.MypageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MypageController {

    private final MypageService mypageService;

    @GetMapping("/mypage/profile")
    public ResponseEntity<UserResponseDTO> getMyPage() {
        return ResponseEntity.ok(mypageService.getMyPage());
    }

}
