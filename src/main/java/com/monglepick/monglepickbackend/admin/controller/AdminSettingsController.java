package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminAccountResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminRoleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AuditLogResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsUpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminSettingsService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 설정 API 컨트롤러.
 *
 * <p>관리자 페이지 "설정" 탭의 4가지 하위 기능에 대한 엔드포인트를 제공한다.</p>
 *
 * <h3>담당 엔드포인트 (11개)</h3>
 * <ul>
 *   <li>약관/정책: GET /terms, POST /terms, PUT /terms/{id}, DELETE /terms/{id}</li>
 *   <li>배너:      GET /banners, POST /banners, PUT /banners/{id}, DELETE /banners/{id}</li>
 *   <li>감사 로그: GET /audit-logs</li>
 *   <li>관리자 계정: GET /admins, PUT /admins/{id}</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다 (SecurityConfig hasRole("ADMIN") 설정).</p>
 *
 * <h3>공통 응답 형식</h3>
 * <p>모든 응답은 {@link ApiResponse} 래퍼로 감싼다.
 * 리소스 생성(POST)은 HTTP 201 Created, 나머지는 HTTP 200 OK를 반환한다.
 * 삭제(DELETE) 성공 시에는 data=null로 200 OK를 반환한다.</p>
 */
@Tag(name = "관리자 — 설정", description = "약관/정책, 배너, 감사 로그, 관리자 계정 관리")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminSettingsController {

    /** 설정 관련 비즈니스 로직 서비스 */
    private final AdminSettingsService adminSettingsService;

    // ======================== 약관/정책 ========================

    /**
     * 약관 목록을 최신 등록순으로 페이지네이션하여 조회한다.
     *
     * <p>기본 페이지 크기는 20이다. Pageable을 통해 page/size/sort를 쿼리 파라미터로
     * 전달할 수 있다 (예: ?page=0&size=10&sort=createdAt,desc).</p>
     *
     * @param pageable 페이지 정보 (기본 size=20)
     * @return 약관 목록 페이지 (Page&lt;TermsResponse&gt;)
     */
    @Operation(
            summary = "약관 목록 조회",
            description = "등록된 약관/정책 목록을 최신 등록순으로 페이지네이션하여 조회한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<Page<TermsResponse>>> getTerms(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminSettings] 약관 목록 조회 요청 — page={}", pageable.getPageNumber());
        Page<TermsResponse> result = adminSettingsService.getTerms(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 신규 약관을 등록한다.
     *
     * <p>title, content, type, isRequired 는 필수값이다.
     * version을 생략하면 서비스 레이어에서 null로 저장된다.</p>
     *
     * @param request 약관 등록 요청 DTO (유효성 검증 적용)
     * @return 등록된 약관 단건 응답 (HTTP 201 Created)
     */
    @Operation(
            summary = "약관 등록",
            description = "신규 약관/정책을 등록한다. title, content, type, isRequired 는 필수값이다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @PostMapping("/terms")
    public ResponseEntity<ApiResponse<TermsResponse>> createTerm(
            @RequestBody @Valid TermsCreateRequest request
    ) {
        log.info("[AdminSettings] 약관 등록 요청 — type={}, version={}", request.type(), request.version());
        TermsResponse result = adminSettingsService.createTerm(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    /**
     * 기존 약관을 수정한다.
     *
     * <p>제목, 전문, 유형, 버전, 필수 동의 여부를 일괄 업데이트한다.
     * 존재하지 않는 ID를 전달하면 BusinessException(G002)이 발생한다.</p>
     *
     * @param id      수정할 약관 ID (경로 변수)
     * @param request 약관 수정 요청 DTO (유효성 검증 적용)
     * @return 수정된 약관 단건 응답
     */
    @Operation(
            summary = "약관 수정",
            description = "기존 약관/정책의 제목·전문·유형·버전·필수 여부를 수정한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "약관 미발견")
    })
    @PutMapping("/terms/{id}")
    public ResponseEntity<ApiResponse<TermsResponse>> updateTerm(
            @Parameter(description = "수정할 약관 ID") @PathVariable Long id,
            @RequestBody @Valid TermsUpdateRequest request
    ) {
        log.info("[AdminSettings] 약관 수정 요청 — termsId={}", id);
        TermsResponse result = adminSettingsService.updateTerm(id, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 약관을 삭제한다.
     *
     * <p>물리적 삭제를 수행한다. 존재하지 않는 ID를 전달하면 BusinessException(G002)이 발생한다.</p>
     *
     * @param id 삭제할 약관 ID (경로 변수)
     * @return data=null인 성공 응답 (HTTP 200 OK)
     */
    @Operation(
            summary = "약관 삭제",
            description = "지정한 ID의 약관/정책을 물리적으로 삭제한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "약관 미발견")
    })
    @DeleteMapping("/terms/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTerm(
            @Parameter(description = "삭제할 약관 ID") @PathVariable Long id
    ) {
        log.info("[AdminSettings] 약관 삭제 요청 — termsId={}", id);
        adminSettingsService.deleteTerm(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ======================== 배너 ========================

    /**
     * 배너 목록을 정렬 순서(sortOrder 오름차순)로 페이지네이션하여 조회한다.
     *
     * <p>sortOrder 값이 낮을수록 우선 노출된다.
     * 기본 페이지 크기는 20이다.</p>
     *
     * @param pageable 페이지 정보 (기본 size=20)
     * @return 배너 목록 페이지 (Page&lt;BannerResponse&gt;)
     */
    @Operation(
            summary = "배너 목록 조회",
            description = "등록된 배너 목록을 정렬 순서(sortOrder 오름차순)로 페이지네이션하여 조회한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/banners")
    public ResponseEntity<ApiResponse<Page<BannerResponse>>> getBanners(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminSettings] 배너 목록 조회 요청 — page={}", pageable.getPageNumber());
        Page<BannerResponse> result = adminSettingsService.getBanners(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 신규 배너를 등록한다.
     *
     * <p>title, imageUrl 은 필수값이다.
     * position 생략 시 서비스 레이어에서 기본값 "MAIN"으로 설정되고,
     * sortOrder 생략 시 0으로 설정된다.</p>
     *
     * @param request 배너 등록 요청 DTO (유효성 검증 적용)
     * @return 등록된 배너 단건 응답 (HTTP 201 Created)
     */
    @Operation(
            summary = "배너 등록",
            description = "신규 배너를 등록한다. title, imageUrl 은 필수값이다. " +
                    "position 생략 시 'MAIN', sortOrder 생략 시 0이 기본값으로 설정된다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @PostMapping("/banners")
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(
            @RequestBody @Valid BannerCreateRequest request
    ) {
        log.info("[AdminSettings] 배너 등록 요청 — title={}, position={}", request.title(), request.position());
        BannerResponse result = adminSettingsService.createBanner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    /**
     * 기존 배너를 수정한다.
     *
     * <p>기본 정보(제목/이미지/링크/위치/순서), 활성화 여부, 게시 기간을 일괄 업데이트한다.
     * 존재하지 않는 ID를 전달하면 BusinessException(G002)이 발생한다.</p>
     *
     * @param id      수정할 배너 ID (경로 변수)
     * @param request 배너 수정 요청 DTO (유효성 검증 적용)
     * @return 수정된 배너 단건 응답
     */
    @Operation(
            summary = "배너 수정",
            description = "기존 배너의 제목·이미지·링크·위치·순서·활성화 여부·게시 기간을 수정한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배너 미발견")
    })
    @PutMapping("/banners/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> updateBanner(
            @Parameter(description = "수정할 배너 ID") @PathVariable Long id,
            @RequestBody @Valid BannerUpdateRequest request
    ) {
        log.info("[AdminSettings] 배너 수정 요청 — bannerId={}", id);
        BannerResponse result = adminSettingsService.updateBanner(id, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 배너를 삭제한다.
     *
     * <p>물리적 삭제를 수행한다. 존재하지 않는 ID를 전달하면 BusinessException(G002)이 발생한다.</p>
     *
     * @param id 삭제할 배너 ID (경로 변수)
     * @return data=null인 성공 응답 (HTTP 200 OK)
     */
    @Operation(
            summary = "배너 삭제",
            description = "지정한 ID의 배너를 물리적으로 삭제한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배너 미발견")
    })
    @DeleteMapping("/banners/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(
            @Parameter(description = "삭제할 배너 ID") @PathVariable Long id
    ) {
        log.info("[AdminSettings] 배너 삭제 요청 — bannerId={}", id);
        adminSettingsService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ======================== 감사 로그 ========================

    /**
     * 감사 로그 목록을 최신순으로 페이지네이션하여 조회한다.
     *
     * <p>actionType 파라미터가 있으면 해당 문자열을 포함하는 로그만 필터링한다 (부분 일치, 대소문자 무시).
     * 파라미터를 생략하거나 빈 문자열로 전달하면 전체 로그를 조회한다.</p>
     *
     * <p>예: ?actionType=SUSPEND 전달 시 "USER_SUSPEND", "ADMIN_SUSPEND" 등 모두 포함.</p>
     *
     * @param actionType 행위 유형 필터 키워드 (선택, 부분 일치)
     * @param pageable   페이지 정보 (기본 size=20)
     * @return 감사 로그 목록 페이지 (Page&lt;AuditLogResponse&gt;)
     */
    @Operation(
            summary = "감사 로그 목록 조회",
            description = "관리자 행위 감사 로그를 최신순으로 조회한다. " +
                    "actionType 쿼리 파라미터로 행위 유형 필터링이 가능하다 (부분 일치, 대소문자 무시)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @Parameter(description = "행위 유형 필터 키워드 (부분 일치, 생략 시 전체 조회)")
            @RequestParam(required = false) String actionType,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminSettings] 감사 로그 조회 요청 — actionType={}, page={}", actionType, pageable.getPageNumber());
        Page<AuditLogResponse> result = adminSettingsService.getAuditLogs(actionType, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ======================== 관리자 계정 ========================

    /**
     * 관리자 계정 목록을 최신 등록순으로 페이지네이션하여 조회한다.
     *
     * <p>기본 페이지 크기는 20이다.</p>
     *
     * @param pageable 페이지 정보 (기본 size=20)
     * @return 관리자 계정 목록 페이지 (Page&lt;AdminAccountResponse&gt;)
     */
    @Operation(
            summary = "관리자 계정 목록 조회",
            description = "등록된 관리자 계정 목록을 최신 등록순으로 페이지네이션하여 조회한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/admins")
    public ResponseEntity<ApiResponse<Page<AdminAccountResponse>>> getAdmins(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminSettings] 관리자 계정 목록 조회 요청 — page={}", pageable.getPageNumber());
        Page<AdminAccountResponse> result = adminSettingsService.getAdmins(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 관리자 역할을 수정한다.
     *
     * <p>ADMIN ↔ SUPER_ADMIN 간 역할 변경 등에 사용한다.
     * 존재하지 않는 ID를 전달하면 BusinessException(G002)이 발생한다.</p>
     *
     * @param id      수정할 관리자 레코드 ID (경로 변수)
     * @param request 역할 수정 요청 DTO (adminRole 필수)
     * @return 수정된 관리자 계정 단건 응답
     */
    @Operation(
            summary = "관리자 역할 수정",
            description = "지정한 관리자 계정의 역할(adminRole)을 수정한다. " +
                    "예: ADMIN → SUPER_ADMIN 승격, SUPER_ADMIN → ADMIN 강등."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자 계정 미발견")
    })
    @PutMapping("/admins/{id}")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> updateAdminRole(
            @Parameter(description = "수정할 관리자 레코드 ID") @PathVariable Long id,
            @RequestBody @Valid AdminRoleUpdateRequest request
    ) {
        log.info("[AdminSettings] 관리자 역할 수정 요청 — adminId={}, newRole={}", id, request.adminRole());
        AdminAccountResponse result = adminSettingsService.updateAdminRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
