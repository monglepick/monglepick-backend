package com.monglepick.monglepickbackend.domain.user.controller;


import com.monglepick.monglepickbackend.domain.auth.dto.UserRequestDTO;
import com.monglepick.monglepickbackend.domain.user.dto.UserResponseDTO;
import com.monglepick.monglepickbackend.domain.user.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 회원가입
    @PostMapping(value = "/auth/signup")
    public ResponseEntity<Map<String, String>> joinApi(
            @Validated(UserRequestDTO.addGroup.class) @RequestBody UserRequestDTO dto
    ){
        //유저 이메일 중복 체크 로직
        if (userService.existUser(dto)){
            return ResponseEntity.status(409).body(Collections.singletonMap("error", "이미 있는 이메일입니다."));
        }

        String id = userService.addUser(dto);
        //singletonMap 딱 하나의 키-값  쌍만 가지는 불변 Map을 만들때 사용 여러 개의 쌍이 필요하면 HashMap 사용
        Map<String, String> responseBody = Collections.singletonMap("userEntityId", id);
        return ResponseEntity.status(201).body(responseBody);
    }


    // 유저 정보
    @GetMapping(value = "/users/me")
    public UserResponseDTO userMeApi() {
        return userService.readUser();
    }

    // 유저 수정 (자체 로그인 유저만)
    @PutMapping(value = "/users/me")
    public ResponseEntity<String> updateUserApi(
            @Validated(UserRequestDTO.updateGroup.class) @RequestBody UserRequestDTO dto
    ) throws AccessDeniedException {
        return ResponseEntity.status(200).body(userService.updateUser(dto));
    }

    // 유저 제거 (자체/소셜)
    @DeleteMapping(value = "/users/me")
    public ResponseEntity<Boolean> deleteUserApi(
            @Validated(UserRequestDTO.deleteGroup.class) @RequestBody UserRequestDTO dto
    ) throws AccessDeniedException {

        userService.deleteUser(dto);
        return ResponseEntity.status(200).body(true);
    }

}