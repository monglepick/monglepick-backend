package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.SystemDto.ServiceStatusResponse;
import com.monglepick.monglepickbackend.admin.dto.SystemDto.SystemConfigResponse;
import com.monglepick.monglepickbackend.admin.service.AdminSystemService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 시스템 API 컨트롤러.
 * - GET /api/v1/admin/system/services — 4개 서비스 헬스체크 집계
 * - GET /api/v1/admin/system/config — 현재 설정값 조회 (읽기 전용)
 *
 * 인증: hasRole("ADMIN") 필수 (SecurityConfig에서 설정)
 */
@Tag(name = "관리자 — 시스템", description = "서비스 헬스체크 및 시스템 설정 조회")
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    /**
     * 4개 서비스 헬스체크 집계.
     * Spring Boot, AI Agent, Recommend, Nginx의 /health 엔드포인트를 호출하고
     * 연결 상태, 응답 시간을 집계한다.
     */
    @Operation(summary = "서비스 상태 조회", description = "4개 서비스(Boot/Agent/Recommend/Nginx) 헬스체크 집계")
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<ServiceStatusResponse>> getServiceStatus() {
        ServiceStatusResponse result = adminSystemService.checkServiceStatus();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 현재 시스템 설정값 조회 (읽기 전용).
     * JWT TTL, 파일 업로드 제한, Redis TTL 등 운영 설정을 반환한다.
     */
    @Operation(summary = "시스템 설정 조회", description = "현재 적용된 시스템 설정값 (읽기 전용)")
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> getSystemConfig() {
        SystemConfigResponse result = adminSystemService.getSystemConfig();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
