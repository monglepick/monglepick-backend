package com.monglepick.monglepickbackend.domain.community.controller;

import com.monglepick.monglepickbackend.domain.community.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 이미지 업로드 컨트롤러
 *
 * POST /api/v1/images/upload
 *   - multipart/form-data 로 이미지 파일 수신
 *   - 저장 후 접근 가능한 URL 목록 반환
 *   - JWT 인증 필수
 */
@Tag(name = "이미지", description = "게시글 이미지 업로드")
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "이미지 업로드", description = "게시글 첨부 이미지 업로드 (최대 5장, JWT 필수)")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/upload")
    public ResponseEntity<Map<String, List<String>>> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal String userId) {

        List<String> urls = imageService.uploadImages(files, userId);
        return ResponseEntity.ok(Map.of("urls", urls));
    }
}