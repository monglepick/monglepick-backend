package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ActivityResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.GrantAiTokenRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.GrantAiTokenResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ManualPointAdjustRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ManualPointResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.PaymentHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.PointHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.RoleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.SuspendRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.SuspensionHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.UserDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.UserListResponse;
import com.monglepick.monglepickbackend.admin.service.AdminUserService;
import com.monglepick.monglepickbackend.domain.reward.dto.RewardCatalogDto.UserRewardStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.service.RewardQueryService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 관리자 사용자 관리 API 컨트롤러.
 *
 * <p>회원 목록 조회·검색, 사용자 상세 조회, 역할 변경, 계정 정지/복구,
 * 활동 이력·포인트 내역·결제 내역 조회 8개 엔드포인트를 제공한다.</p>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 {@code hasRole("ADMIN")} 권한이 필요하다.
 * SecurityConfig에서 {@code /api/v1/admin/**} 경로에 ADMIN 역할을 요구하도록 설정되어야 한다.</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <ul>
 *   <li>GET  /api/v1/admin/users — 사용자 목록 조회 (검색·필터·페이징)</li>
 *   <li>GET  /api/v1/admin/users/{userId} — 사용자 상세 조회</li>
 *   <li>PUT  /api/v1/admin/users/{userId}/role — 역할 변경</li>
 *   <li>PUT  /api/v1/admin/users/{userId}/suspend — 계정 정지</li>
 *   <li>PUT  /api/v1/admin/users/{userId}/activate — 계정 복구</li>
 *   <li>GET  /api/v1/admin/users/{userId}/activity — 최근 활동 이력</li>
 *   <li>GET  /api/v1/admin/users/{userId}/points — 포인트 변동 내역</li>
 *   <li>GET  /api/v1/admin/users/{userId}/payments — 결제 내역</li>
 * </ul>
 */
@Tag(name = "관리자 — 사용자", description = "회원 관리, 역할 변경, 계정 정지/복구, 활동/포인트/결제 이력 조회")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 리워드 진행 현황 조회 서비스 (2026-04-14 추가).
     *
     * <p>특정 사용자의 활동별 카운터 + 마일스톤 진행률을 관리자 시점에서 조회한다.
     * 사용자 본인이 {@code GET /api/v1/point/progress} 로 보는 것과 동일한 응답 구조를
     * 관리자가 임의의 userId 로 조회할 수 있게 한다.</p>
     */
    private final RewardQueryService rewardQueryService;

    // ──────────────────────────────────────────────
    // 목록 / 상세 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자 목록을 조회한다.
     *
     * <p>keyword, status, role, deletedFilter 파라미터를 조합하여 필터링한다.
     * deletedFilter를 생략하면 탈퇴하지 않은 사용자만 반환한다.</p>
     *
     * <h4>정렬 기본값</h4>
     * <p>가입 일시(createdAt) 내림차순 — 최근 가입 회원이 먼저 표시된다.</p>
     *
     * @param keyword 닉네임 또는 이메일 검색 키워드 (생략 가능)
     * @param status  계정 상태 필터: ACTIVE, SUSPENDED, LOCKED (생략 가능)
     * @param role    역할 필터: USER, ADMIN (생략 가능)
     * @param deletedFilter 삭제 필터: NOT_DELETED, DELETED, ALL (기본값 NOT_DELETED)
     * @param page    페이지 번호 (0부터 시작, 기본값 0)
     * @param size    페이지 크기 (기본값 20, 최대 100)
     * @return 필터 조건에 맞는 사용자 목록 페이지
     */
    @Operation(
            summary = "사용자 목록 조회",
            description = "닉네임/이메일 키워드, 계정 상태(ACTIVE/SUSPENDED/LOCKED), " +
                    "역할(USER/ADMIN), 삭제 필터(NOT_DELETED/DELETED/ALL)를 조합하여 사용자 목록을 페이징 조회한다. " +
                    "deletedFilter 생략 시 탈퇴 회원(is_deleted=true)은 제외된다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 status 또는 role 값")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserListResponse>>> getUsers(
            @Parameter(description = "닉네임 또는 이메일 검색 키워드 (생략 시 전체)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "계정 상태 필터 (ACTIVE, SUSPENDED, LOCKED)")
            @RequestParam(required = false) String status,

            @Parameter(description = "역할 필터 (USER, ADMIN)")
            @RequestParam(required = false) String role,

            @Parameter(description = "삭제 필터 (NOT_DELETED, DELETED, ALL)", example = "NOT_DELETED")
            @RequestParam(required = false) String deletedFilter,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        // 페이지 크기 상한 제한 — 너무 큰 요청 방지
        int safeSize = Math.min(size, 100);

        // 기본 정렬: 가입 일시 내림차순 (최근 가입 회원이 먼저)
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by("createdAt").descending());

        log.debug("[AdminUserController] 사용자 목록 조회 — keyword={}, status={}, role={}, deletedFilter={}, page={}, size={}",
                keyword, status, role, deletedFilter, page, safeSize);

        Page<UserListResponse> result = adminUserService.getUsers(keyword, status, role, deletedFilter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 특정 사용자의 상세 정보를 조회한다.
     *
     * <p>기본 프로필 + 포인트/등급 + 활동 통계(게시글·리뷰·댓글 건수)를 반환한다.
     * 탈퇴한 사용자(is_deleted=true)도 관리자 식별/복구를 위해 조회 가능하다.</p>
     *
     * @param userId 조회할 사용자 ID (VARCHAR(50))
     * @return 사용자 상세 응답 DTO
     */
    @Operation(
            summary = "사용자 상세 조회",
            description = "userId로 특정 사용자의 프로필, 포인트 잔액·등급, " +
                    "게시글·리뷰·댓글 작성 건수를 조회한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(
            @Parameter(description = "조회할 사용자 ID", example = "user_abc123")
            @PathVariable String userId
    ) {
        log.debug("[AdminUserController] 사용자 상세 조회 — userId={}", userId);

        UserDetailResponse result = adminUserService.getUserDetail(userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ──────────────────────────────────────────────
    // 역할 변경 / 계정 정지·복구
    // ──────────────────────────────────────────────

    /**
     * 사용자의 역할을 변경한다 (USER ↔ ADMIN).
     *
     * <p>변경 후 최신 사용자 상세 정보를 반환한다.
     * 역할 변경은 즉시 DB에 반영되며, 사용자의 다음 로그인(JWT 재발급) 시 적용된다.</p>
     *
     * @param userId  역할을 변경할 사용자 ID
     * @param request 변경할 역할 ({@code role}: "USER" 또는 "ADMIN")
     * @return 변경 후 사용자 상세 응답 DTO
     */
    @Operation(
            summary = "사용자 역할 변경",
            description = "특정 사용자의 역할을 USER 또는 ADMIN으로 변경한다. " +
                    "변경된 역할은 사용자의 다음 JWT 재발급 시 반영된다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "역할 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 역할 값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    /* 2026-04-23 Step 2B: 역할 변경은 관리자 전용 method-level 가드. Step 2C 에서 SUPER_ADMIN 전용으로 강화 예정. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUserRole(
            @Parameter(description = "역할을 변경할 사용자 ID", example = "user_abc123")
            @PathVariable String userId,

            @RequestBody RoleUpdateRequest request
    ) {
        log.info("[AdminUserController] 역할 변경 요청 — userId={}, newRole={}", userId, request.role());

        UserDetailResponse result = adminUserService.updateUserRole(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 사용자 계정을 정지한다.
     *
     * <p>계정 상태를 SUSPENDED로 변경하고 정지 사유·일시를 기록한다.
     * {@code durationDays}가 양수이면 임시 정지(해당 일수 후 자동 복구 대상),
     * null이면 영구 정지. 모든 정지/복구는 {@code user_status} INSERT-ONLY 원장에 자동 기록.</p>
     *
     * @param userId  정지할 사용자 ID
     * @param request 정지 사유 + 임시 정지 기간
     * @return 성공 메시지 응답
     */
    @Operation(
            summary = "사용자 계정 정지",
            description = "특정 사용자의 계정을 정지(SUSPENDED)한다. " +
                    "reason 생략 시 기본 메시지. durationDays가 양수이면 임시 정지(N일 후 자동 복구 대상), null이면 영구 정지."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계정 정지 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    /* 2026-04-23 Step 2B: 계정 정지 — Tier 3 위험 Write. Step 2C 에서 MODERATOR 이상으로 완화 가능. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<String>> suspendUser(
            @Parameter(description = "정지할 사용자 ID", example = "user_abc123")
            @PathVariable String userId,

            @Parameter(description = "정지 사유 (선택 입력)")
            @RequestBody(required = false) SuspendRequest request
    ) {
        log.info("[AdminUserController] 계정 정지 요청 — userId={}, reason={}",
                userId, request != null ? request.reason() : "(없음)");

        adminUserService.suspendUser(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("계정이 정지되었습니다."));
    }

    /**
     * 정지된 사용자 계정을 복구(활성화)한다.
     *
     * <p>계정 상태를 ACTIVE로 변경하고 정지 사유·정지 일시를 초기화한다.
     * LOCKED 상태 계정도 ACTIVE로 전환할 수 있다.</p>
     *
     * @param userId 복구할 사용자 ID
     * @return 성공 메시지 응답
     */
    @Operation(
            summary = "사용자 계정 복구",
            description = "정지(SUSPENDED) 또는 잠금(LOCKED)된 사용자 계정을 활성화(ACTIVE)한다. " +
                    "정지 사유와 정지 일시가 초기화된다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계정 복구 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    /* 2026-04-23 Step 2B: 계정 복구 — Tier 3 위험 Write. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/activate")
    public ResponseEntity<ApiResponse<String>> activateUser(
            @Parameter(description = "복구할 사용자 ID", example = "user_abc123")
            @PathVariable String userId
    ) {
        log.info("[AdminUserController] 계정 복구 요청 — userId={}", userId);

        adminUserService.activateUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("계정이 복구되었습니다."));
    }

    /**
     * 탈퇴 처리된 사용자 계정을 복구한다.
     *
     * <p>is_deleted/deleted_at을 초기화하고 status를 ACTIVE로 되돌린다.
     * 정지 복구(/activate)와 구분되는 탈퇴 복구 전용 엔드포인트이다.</p>
     *
     * @param userId 복구할 탈퇴 사용자 ID
     * @return 성공 메시지 응답
     */
    @Operation(
            summary = "탈퇴 계정 복구",
            description = "탈퇴 처리된 사용자(is_deleted=true)를 관리자 권한으로 복구한다. " +
                    "is_deleted=false, deleted_at=null, status=ACTIVE로 변경하고 재가입 제한 이력을 정리한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 계정 복구 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "탈퇴한 사용자가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{userId}/restore-withdrawal")
    public ResponseEntity<ApiResponse<String>> restoreWithdrawnUser(
            @Parameter(description = "복구할 탈퇴 사용자 ID", example = "user_abc123")
            @PathVariable String userId
    ) {
        log.info("[AdminUserController] 탈퇴 계정 복구 요청 — userId={}", userId);

        adminUserService.restoreWithdrawnUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("탈퇴 계정이 복구되었습니다."));
    }

    // ──────────────────────────────────────────────
    // 이력 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자의 계정 제재 이력을 조회한다.
     *
     * <p>user_status 테이블의 INSERT-ONLY 원장에서 최신순으로 정지/복구 이력을 반환한다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @return 제재 이력 목록 (최신순)
     */
    @Operation(
            summary = "사용자 제재 이력 조회",
            description = "user_status 테이블에서 특정 사용자의 정지/복구 이력을 최신순으로 반환."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{userId}/suspension-history")
    public ResponseEntity<ApiResponse<List<SuspensionHistoryResponse>>> getSuspensionHistory(
            @Parameter(description = "조회할 사용자 ID", example = "user_abc123")
            @PathVariable String userId
    ) {
        log.debug("[AdminUserController] 제재 이력 조회 — userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getSuspensionHistory(userId)));
    }

    /**
     * 관리자 수동 포인트 지급/회수.
     *
     * <p>CS 보상, 운영 사고 복구, 프로모션 수동 지급 등에 사용.
     * 양수=지급(bonus), 음수=회수(revoke). 0이면 400 BAD_REQUEST.
     * PointsHistory에 INSERT-ONLY 원장으로 자동 기록된다.</p>
     *
     * @param userId  대상 사용자 ID
     * @param request 변동량 + 사유
     * @return 처리 결과 (변동 전/후 잔액, history ID)
     */
    @Operation(
            summary = "관리자 수동 포인트 조정",
            description = "amount 양수=지급, 음수=회수, 0=400. " +
                    "point_type: 양수→bonus, 음수→revoke. " +
                    "PointsHistory INSERT-ONLY 원장에 자동 기록."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "amount=0 또는 잔액 부족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 user_points 없음")
    })
    /* 2026-04-23 Step 2B: 수동 포인트 조정 — Tier 3 위험 Write (금전). Step 2C 에서 FINANCE_ADMIN 으로 세분화. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/points/adjust")
    public ResponseEntity<ApiResponse<ManualPointResponse>> adjustUserPoints(
            @Parameter(description = "대상 사용자 ID", example = "user_abc123")
            @PathVariable String userId,
            @Valid @RequestBody ManualPointAdjustRequest request
    ) {
        log.info("[AdminUserController] 수동 포인트 조정 요청 — userId={}, amount={}, reason={}",
                userId, request.amount(), request.reason());
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.adjustUserPoints(userId, request)));
    }

    /**
     * 관리자 수동 AI 이용권(쿠폰) 발급.
     *
     * <p>특정 사용자의 {@code user_ai_quota.purchased_ai_tokens}를 지정 수량만큼 증가.
     * 사과 보상, 마케팅 캠페인, 운영 사고 복구 등에 사용.</p>
     */
    @Operation(
            summary = "관리자 수동 이용권 발급",
            description = "특정 사용자의 purchased_ai_tokens를 count만큼 증가시킨다. " +
                    "PointItem 구매 흐름과 동일한 효과. 비관적 락으로 동시성 보호."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "count가 1 미만"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 AI 쿼터 레코드 없음")
    })
    /* 2026-04-23 Step 2B: AI 이용권 수동 발급 — Tier 3 위험 Write (금전성). Step 2C 에서 FINANCE_ADMIN 으로. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/tokens/grant")
    public ResponseEntity<ApiResponse<GrantAiTokenResponse>> grantAiTokens(
            @Parameter(description = "대상 사용자 ID", example = "user_abc123")
            @PathVariable String userId,
            @Valid @RequestBody GrantAiTokenRequest request
    ) {
        log.info("[AdminUserController] 수동 이용권 발급 요청 — userId={}, count={}, reason={}",
                userId, request.count(), request.reason());
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.grantAiTokens(userId, request)));
    }

    /**
     * 사용자의 최근 활동 이력을 조회한다.
     *
     * <p>게시글 작성, 리뷰 작성, 댓글 작성 이력을 통합하여 최신순으로 반환한다.
     * 각 유형별로 최대 {@code size}건씩 조회한 후 통합·정렬하므로
     * 실제 반환 건수는 최대 {@code size * 3}건이 될 수 있다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @param page   페이지 번호 (기본값 0)
     * @param size   각 활동 유형별 조회 건수 (기본값 10)
     * @return 통합 활동 이력 목록 (createdAt 내림차순)
     */
    @Operation(
            summary = "사용자 활동 이력 조회",
            description = "게시글·리뷰·댓글 작성 이력을 통합하여 최신순으로 반환한다. " +
                    "각 유형별로 최대 size건씩 조회 후 통합·정렬하므로 실제 반환 건수는 최대 size×3건이다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{userId}/activity")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> getUserActivity(
            @Parameter(description = "조회할 사용자 ID", example = "user_abc123")
            @PathVariable String userId,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "각 활동 유형별 조회 건수", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("[AdminUserController] 활동 이력 조회 — userId={}, page={}, size={}", userId, page, size);

        // 활동 유형별 최신순 정렬 — createdAt DESC
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        List<ActivityResponse> result = adminUserService.getUserActivity(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 사용자의 포인트 변동 내역을 최신순으로 조회한다.
     *
     * <p>획득(earn), 사용(spend), 보너스(bonus), 만료(expire), 환불(refund), 회수(revoke)
     * 모든 유형의 포인트 변동 이력을 반환한다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @param page   페이지 번호 (기본값 0)
     * @param size   페이지 크기 (기본값 20)
     * @return 포인트 변동 내역 페이지
     */
    @Operation(
            summary = "사용자 포인트 내역 조회",
            description = "earn/spend/bonus/expire/refund/revoke 모든 유형의 포인트 변동 이력을 최신순으로 반환한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{userId}/points")
    public ResponseEntity<ApiResponse<Page<PointHistoryResponse>>> getUserPointHistory(
            @Parameter(description = "조회할 사용자 ID", example = "user_abc123")
            @PathVariable String userId,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("[AdminUserController] 포인트 내역 조회 — userId={}, page={}, size={}", userId, page, size);

        // PointsHistoryRepository.findByUserIdOrderByCreatedAtDesc는 이미 최신순 정렬이므로
        // Pageable에는 정렬 없이 페이지 정보만 전달
        Pageable pageable = PageRequest.of(page, size);

        Page<PointHistoryResponse> result = adminUserService.getUserPointHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 사용자의 결제 내역을 최신순으로 조회한다.
     *
     * <p>PENDING, COMPLETED, FAILED, REFUNDED, COMPENSATION_FAILED
     * 모든 상태의 결제 주문 이력을 반환한다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @param page   페이지 번호 (기본값 0)
     * @param size   페이지 크기 (기본값 20)
     * @return 결제 내역 페이지
     */
    @Operation(
            summary = "사용자 결제 내역 조회",
            description = "포인트팩 구매·구독 결제의 모든 상태(PENDING/COMPLETED/FAILED/REFUNDED/COMPENSATION_FAILED)" +
                    " 이력을 최신순으로 반환한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{userId}/payments")
    public ResponseEntity<ApiResponse<Page<PaymentHistoryResponse>>> getUserPaymentHistory(
            @Parameter(description = "조회할 사용자 ID", example = "user_abc123")
            @PathVariable String userId,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("[AdminUserController] 결제 내역 조회 — userId={}, page={}, size={}", userId, page, size);

        // PaymentOrderRepository.findByUserIdOrderByCreatedAtDesc는 이미 최신순 정렬이므로
        // Pageable에는 정렬 없이 페이지 정보만 전달
        Pageable pageable = PageRequest.of(page, size);

        Page<PaymentHistoryResponse> result = adminUserService.getUserPaymentHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ──────────────────────────────────────────────
    // 리워드 진행 현황 (2026-04-14 신설)
    // ──────────────────────────────────────────────

    /**
     * 특정 사용자의 리워드 진행 현황을 조회한다 (관리자 시점).
     *
     * <p>사용자 본인이 {@code GET /api/v1/point/progress} 로 보는 것과 동일한 구조를
     * 관리자가 임의의 userId 로 조회할 수 있다.</p>
     *
     * <p>응답은 {@link UserRewardStatusResponse} 구조로:</p>
     * <ul>
     *   <li>{@code activities} — 일반 활동별 카운터 (리뷰/출석/댓글 등 + 일일 한도 게이지)</li>
     *   <li>{@code milestones} — threshold 기반 마일스톤 진행률 (현재값/임계치/달성여부)</li>
     *   <li>{@code totalEarned / earnedByActivity / currentBalance / gradeCode} — 요약</li>
     * </ul>
     *
     * <p>관리자가 특정 유저의 리워드 어뷰징 의심(예: 마일스톤 근처 머무름), 서포트
     * 문의 대응(예: "리뷰 5회 보너스 못 받았어요") 등에 활용된다.</p>
     *
     * @param userId 조회 대상 사용자 ID
     * @return 리워드 진행 현황 (ApiResponse 래퍼)
     */
    @Operation(
            summary = "사용자 리워드 진행 현황 조회",
            description = "활동별 카운터 + 마일스톤 진행률 + 포인트 요약을 반환한다. " +
                    "사용자 본인 조회(/api/v1/point/progress)와 동일한 구조의 관리자 버전."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{userId}/rewards")
    public ResponseEntity<ApiResponse<UserRewardStatusResponse>> getUserRewards(
            @Parameter(description = "조회할 사용자 ID", example = "user_abc123")
            @PathVariable String userId
    ) {
        log.debug("[AdminUserController] 리워드 진행 현황 조회 — userId={}", userId);
        UserRewardStatusResponse result = rewardQueryService.getUserRewardStatus(userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
