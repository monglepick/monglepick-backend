package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.CreateOcrEventRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.OcrEventResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.ReviewRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.UpdateOcrEventRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.UpdateStatusRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.VerificationResponse;
import com.monglepick.monglepickbackend.admin.service.AdminOcrEventService;
import com.monglepick.monglepickbackend.admin.service.AdminVerificationService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 OCR 인증 이벤트(OcrEvent) 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → OCR 이벤트" 메뉴의 6개 엔드포인트를 제공한다.
 * 관리자가 특정 영화에 대해 실관람 영수증 OCR 인증 이벤트를 운영한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/ocr-events                — 이벤트 목록 (페이징 + 상태 필터)</li>
 *   <li>GET    /api/v1/admin/ocr-events/{eventId}      — 이벤트 단건 조회</li>
 *   <li>POST   /api/v1/admin/ocr-events                — 이벤트 신규 등록 (READY)</li>
 *   <li>PUT    /api/v1/admin/ocr-events/{eventId}      — 이벤트 메타 수정 (movieId/start/end)</li>
 *   <li>PATCH  /api/v1/admin/ocr-events/{eventId}/status — 상태 전이 (READY/ACTIVE/CLOSED)</li>
 *   <li>DELETE /api/v1/admin/ocr-events/{eventId}      — 이벤트 hard delete</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — OCR 이벤트", description = "OcrEvent 라이프사이클 관리 (READY/ACTIVE/CLOSED)")
@RestController
@RequestMapping("/api/v1/admin/ocr-events")
@RequiredArgsConstructor
@Slf4j
public class AdminOcrEventController {

    private final AdminOcrEventService adminOcrEventService;
    private final AdminVerificationService adminVerificationService;

    /** 이벤트 목록 조회 */
    @Operation(
            summary = "OCR 이벤트 목록 조회",
            description = "status 파라미터로 READY/ACTIVE/CLOSED 필터링. 생략 시 전체."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OcrEventResponse>>> getEvents(
            @Parameter(description = "상태 필터 (READY/ACTIVE/CLOSED, 생략 시 전체)")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<OcrEventResponse> result = adminOcrEventService.getEvents(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 이벤트 단건 조회 */
    @Operation(summary = "OCR 이벤트 단건 조회", description = "event_id로 단건 조회")
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<OcrEventResponse>> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.ok(adminOcrEventService.getEvent(eventId)));
    }

    /** 신규 이벤트 등록 */
    @Operation(
            summary = "OCR 이벤트 신규 등록",
            description = "신규 이벤트는 READY 상태로 시작. admin_id는 SecurityContext에서 자동 추출."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<OcrEventResponse>> createEvent(
            @Valid @RequestBody CreateOcrEventRequest request
    ) {
        log.info("[관리자] OCR 이벤트 등록 요청 — movieId={}, period={}~{}",
                request.movieId(), request.startDate(), request.endDate());
        OcrEventResponse created = adminOcrEventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 이벤트 메타 수정 */
    @Operation(summary = "OCR 이벤트 수정", description = "movieId/startDate/endDate 수정")
    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<OcrEventResponse>> updateEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateOcrEventRequest request
    ) {
        log.info("[관리자] OCR 이벤트 수정 요청 — eventId={}", eventId);
        return ResponseEntity.ok(ApiResponse.ok(adminOcrEventService.updateEvent(eventId, request)));
    }

    /** 상태 전이 */
    @Operation(
            summary = "OCR 이벤트 상태 전이",
            description = "targetStatus = READY/ACTIVE/CLOSED. 관리자가 임의로 전이 가능."
    )
    @PatchMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<OcrEventResponse>> updateStatus(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        log.info("[관리자] OCR 이벤트 상태 변경 요청 — eventId={}, target={}",
                eventId, request.targetStatus());
        return ResponseEntity.ok(ApiResponse.ok(adminOcrEventService.updateStatus(eventId, request)));
    }

    /** 이벤트 삭제 */
    @Operation(summary = "OCR 이벤트 삭제", description = "hard delete. 사용자 인증 기록은 보존되지 않을 수 있음.")
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        log.warn("[관리자] OCR 이벤트 삭제 요청 — eventId={}", eventId);
        adminOcrEventService.deleteEvent(eventId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ──────────────────────────────────────────────
    // 유저 인증(UserVerification) 검토
    // ──────────────────────────────────────────────

    /** 이벤트별 인증 목록 조회 */
    @Operation(
            summary = "인증 목록 조회",
            description = "이벤트에 제출된 유저 인증 목록. status=PENDING/APPROVED/REJECTED 필터 가능."
    )
    @GetMapping("/{eventId}/verifications")
    public ResponseEntity<ApiResponse<Page<VerificationResponse>>> getVerifications(
            @PathVariable Long eventId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<VerificationResponse> result = adminVerificationService.getVerifications(eventId, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 인증 단건 조회 */
    @Operation(summary = "인증 단건 조회", description = "verificationId로 인증 상세 + OCR 분석 결과 조회")
    @GetMapping("/verifications/{verificationId}")
    public ResponseEntity<ApiResponse<VerificationResponse>> getVerification(
            @PathVariable Long verificationId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminVerificationService.getVerification(verificationId)));
    }

    /** 인증 승인/반려 */
    @Operation(
            summary = "인증 승인/반려",
            description = "action = APPROVE(승인) 또는 REJECT(반려). OCR 결과가 맞으면 APPROVE."
    )
    @PatchMapping("/verifications/{verificationId}/review")
    public ResponseEntity<ApiResponse<VerificationResponse>> review(
            @PathVariable Long verificationId,
            @Valid @RequestBody ReviewRequest request
    ) {
        VerificationResponse result = switch (request.action().toUpperCase()) {
            case "APPROVE" -> adminVerificationService.approve(verificationId);
            case "REJECT"  -> adminVerificationService.reject(verificationId);
            default -> throw new com.monglepick.monglepickbackend.global.exception.BusinessException(
                    com.monglepick.monglepickbackend.global.exception.ErrorCode.INVALID_INPUT,
                    "action은 APPROVE 또는 REJECT 여야 합니다."
            );
        };
        log.info("[관리자] 인증 검토 완료 — verificationId={}, action={}", verificationId, request.action());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
