package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.HistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.PolicyResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.UpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminRewardPolicyService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 리워드 정책(RewardPolicy) 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 리워드 정책" 메뉴의 6개 엔드포인트를 제공한다.
 * 모든 변경 작업은 RewardPolicyHistory에 INSERT-ONLY 원장으로 자동 기록된다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/reward-policies              — 정책 목록 (페이징)</li>
 *   <li>GET    /api/v1/admin/reward-policies/{id}         — 정책 단건</li>
 *   <li>GET    /api/v1/admin/reward-policies/{id}/history — 변경 이력 (최신순)</li>
 *   <li>POST   /api/v1/admin/reward-policies              — 신규 정책 등록</li>
 *   <li>PUT    /api/v1/admin/reward-policies/{id}         — 정책 메타 수정</li>
 *   <li>PATCH  /api/v1/admin/reward-policies/{id}/active  — 활성 토글</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 *
 * <h3>물리 삭제 미지원</h3>
 * <p>RewardPolicy는 hard delete 불가. user_activity_progress 등 사용자 활동 기록이
 * action_type을 String FK로 보관하므로 삭제 시 정합성이 깨진다. 폐지된 정책은
 * PATCH /active로 isActive=false 토글만 가능하다.</p>
 */
@Tag(name = "관리자 — 리워드 정책", description = "RewardPolicy 등록/수정/활성화 + 변경 이력 조회")
@RestController
@RequestMapping("/api/v1/admin/reward-policies")
@RequiredArgsConstructor
@Slf4j
public class AdminRewardPolicyController {

    private final AdminRewardPolicyService adminRewardPolicyService;

    /** 정책 목록 페이징 조회 */
    @Operation(
            summary = "리워드 정책 목록 조회",
            description = "활성/비활성 모든 정책을 페이징 조회. createdAt DESC 정렬."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PolicyResponse>>> getPolicies(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PolicyResponse> result = adminRewardPolicyService.getPolicies(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 정책 단건 조회 */
    @Operation(summary = "리워드 정책 단건 조회", description = "policy_id로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyResponse>> getPolicy(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminRewardPolicyService.getPolicy(id)));
    }

    /** 변경 이력 조회 */
    @Operation(
            summary = "리워드 정책 변경 이력 조회",
            description = "특정 정책의 INSERT-ONLY 변경 이력 (최신순)"
    )
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<HistoryResponse>>> getPolicyHistory(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminRewardPolicyService.getPolicyHistory(id)));
    }

    /**
     * 전체 정책 변경 이력 대시보드 — 2026-04-09 P2-⑰ 신규.
     *
     * <p>Spring MVC 는 리터럴 경로(`/history`)를 path variable(`/{id}/history`) 보다
     * 우선 매치하므로, `/reward-policies/history` 와 `/reward-policies/{id}/history` 는
     * 충돌 없이 공존한다. 전자는 전체 이력 통합 대시보드용, 후자는 개별 정책 이력 조회용.</p>
     *
     * <h3>필터 파라미터</h3>
     * <ul>
     *   <li>{@code policyId}: 특정 정책만 (생략 시 전체)</li>
     *   <li>{@code changedBy}: 특정 관리자 userId 만 (생략 시 전체)</li>
     *   <li>{@code fromDate} / {@code toDate}: 시간 범위 ISO-8601</li>
     *   <li>{@code page} / {@code size}: 페이징 (기본 size=20)</li>
     * </ul>
     */
    @Operation(
            summary = "리워드 정책 변경 이력 대시보드 (전체)",
            description = "모든 정책의 변경 이력을 복합 필터(policyId/changedBy/시간 범위)로 페이징 조회한다. " +
                    "운영 감사 관점에서 '어떤 정책이 언제 누구에 의해 변경되었는가' 를 한 화면에서 파악한다. " +
                    "2026-04-09 P2-⑰ 신규."
    )
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<HistoryResponse>>> getAllHistory(
            @Parameter(description = "특정 정책 ID 만 조회 (생략 시 전체)")
            @RequestParam(required = false) Long policyId,

            @Parameter(description = "변경한 관리자 userId (생략 시 전체)")
            @RequestParam(required = false) String changedBy,

            @Parameter(description = "시작 시각 inclusive (ISO-8601)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime fromDate,

            @Parameter(description = "종료 시각 exclusive (ISO-8601)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime toDate,

            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<HistoryResponse> result = adminRewardPolicyService.getAllHistory(
                policyId, changedBy, fromDate, toDate, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 신규 정책 등록 */
    @Operation(
            summary = "리워드 정책 신규 등록",
            description = "action_type UNIQUE — 중복 시 409. RewardPolicyHistory에 신규 등록 이력 자동 INSERT."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<PolicyResponse>> createPolicy(
            @Valid @RequestBody CreateRequest request
    ) {
        log.info("[관리자] 리워드 정책 등록 요청 — actionType={}, points={}",
                request.actionType(), request.pointsAmount());
        PolicyResponse created = adminRewardPolicyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 정책 메타 수정 */
    @Operation(
            summary = "리워드 정책 수정",
            description = "actionType 제외 핵심 필드 수정. 변경 전/후 스냅샷이 RewardPolicyHistory에 자동 기록."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyResponse>> updatePolicy(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        log.info("[관리자] 리워드 정책 수정 요청 — id={}", id);
        return ResponseEntity.ok(ApiResponse.ok(adminRewardPolicyService.updatePolicy(id, request)));
    }

    /** 활성 토글 */
    @Operation(
            summary = "리워드 정책 활성/비활성 토글",
            description = "is_active 변경. 변경 이력이 RewardPolicyHistory에 자동 기록."
    )
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<PolicyResponse>> updateActive(
            @PathVariable Long id,
            @RequestBody UpdateActiveRequest request
    ) {
        log.info("[관리자] 리워드 정책 활성 토글 요청 — id={}, isActive={}", id, request.isActive());
        return ResponseEntity.ok(ApiResponse.ok(adminRewardPolicyService.updateActive(id, request)));
    }
}
