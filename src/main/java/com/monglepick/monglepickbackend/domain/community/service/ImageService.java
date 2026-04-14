package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 이미지 업로드 서비스
 *
 * 【로컬 개발】
 *   - UPLOAD_DIR=./uploads (프로젝트 루트 기준 상대경로)
 *   - Spring Boot WebMvcConfig가 /images/** 를 ./uploads/ 로 서빙
 *
 * 【서버 배포】
 *   - UPLOAD_DIR=/home/ubuntu/data (.env로 오버라이드)
 *   - NGINX가 /images/** 를 /home/ubuntu/data/ 로 서빙
 *   - Spring Boot WebMvcConfig는 로컬에서만 동작 (서버에서는 NGINX가 담당)
 *
 * 【추후 S3/Object Storage 전환 시】
 *   - 이 서비스만 S3 업로드 로직으로 교체하면 됨
 *   - Controller, 프론트 코드는 변경 불필요
 */
@Slf4j
@Service
public class ImageService {

    /**
     * 파일 저장 경로
     * 로컬: ./uploads (기본값)
     * 서버: /home/ubuntu/data (.env UPLOAD_DIR으로 오버라이드)
     */
    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 이미지 접근 URL 접두사
     * 로컬: http://localhost:8080/images (Spring Boot 직접 서빙)
     * 서버: http://210.109.15.187/images (.env UPLOAD_URL_PREFIX으로 오버라이드, NGINX 서빙)
     */
    @Value("${app.upload.url-prefix:http://localhost:8080/images}")
    private String urlPrefix;

    // 허용 이미지 타입
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_FILE_COUNT = 5; // 최대 5장

    /**
     * 이미지 업로드
     * 파일을 저장하고 접근 가능한 URL 목록을 반환한다.
     *
     * @param files  업로드할 이미지 파일 목록
     * @param userId 업로드하는 사용자 ID (디렉토리 분리용)
     * @return 업로드된 이미지 URL 목록
     */
    public List<String> uploadImages(List<MultipartFile> files, String userId) {
        if (files.size() > MAX_FILE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미지는 최대 " + MAX_FILE_COUNT + "장까지 업로드 가능합니다.");
        }

        List<String> urls = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            // 파일 타입 검증
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "JPG, PNG, GIF, WEBP 형식만 업로드 가능합니다.");
            }

            // 파일 크기 검증
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "파일 크기는 10MB 이하여야 합니다.");
            }

            // UUID로 고유 파일명 생성 (중복 방지)
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;

            // 절대경로로 변환 (상대경로 문제 방지)
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path dirPath = uploadPath.resolve(userId); // userId별 디렉토리 분리
            Path filePath = dirPath.resolve(filename);

            try {
                // 디렉토리가 없으면 자동 생성
                Files.createDirectories(dirPath);
                // 파일 저장 (transferTo 대신 Files.copy 사용 — Tomcat 임시경로 문제 방지)
                try (var inputStream = file.getInputStream()) {
                    Files.copy(inputStream, filePath,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.error("이미지 저장 실패 — userId: {}, filename: {}", userId, filename, e);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 저장에 실패했습니다.");
            }

            // URL 생성: urlPrefix + /userId/파일명
            // 로컬: http://localhost:8080/images/userId/uuid.jpg
            // 서버: http://210.109.15.187/images/userId/uuid.jpg
            urls.add(urlPrefix + "/" + userId + "/" + filename);
            log.info("이미지 업로드 완료 — userId: {}, path: {}", userId, filePath);
        }

        return urls;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}