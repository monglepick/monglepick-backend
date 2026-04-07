package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.SystemDto.ServiceStatusResponse;
import com.monglepick.monglepickbackend.admin.dto.SystemDto.SystemConfigResponse;
import com.monglepick.monglepickbackend.admin.service.AdminLogStreamService;
import com.monglepick.monglepickbackend.admin.service.AdminSystemService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    /** Phase 7 (2026-04-08): SSE 로그 스트리밍 서비스 — Logback ring buffer 어펜더 기반 */
    private final AdminLogStreamService adminLogStreamService;

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

    /**
     * 시스템 로그 SSE 스트리밍 (Phase 7).
     *
     * <p>Logback root 로거에 부착된 ring buffer 어펜더에서 최근 500건의 로그를 즉시 전송하고,
     * 이후 새로 발생하는 로그 이벤트를 실시간으로 클라이언트에 push 한다. 클라이언트가 연결을
     * 종료하거나 60분 타임아웃이 지나면 자동으로 구독이 해제된다.</p>
     *
     * <p>이 엔드포인트는 {@link MediaType#TEXT_EVENT_STREAM_VALUE} 응답 타입을 사용하며,
     * 클라이언트는 EventSource 또는 SSE 클라이언트 라이브러리로 접속한다.</p>
     *
     * @return SseEmitter — Spring 이 응답으로 직렬화하여 SSE 연결을 유지한다.
     */
    @Operation(
            summary = "시스템 로그 SSE 스트리밍",
            description = "최근 500건의 누적 로그 + 실시간 신규 로그를 SSE 로 전송한다. 60분 타임아웃."
    )
    @GetMapping(value = "/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        return adminLogStreamService.subscribe();
    }
}
