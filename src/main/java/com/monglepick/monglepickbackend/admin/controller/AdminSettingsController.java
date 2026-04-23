package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminAccountCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminAccountResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminRoleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AgentAuditLogRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AuditLogResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.CsvExportLogRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsUpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminAuditService;
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

    /**
     * 관리자 감사 로그 기록 서비스 — CSV 내보내기 이벤트를 admin_audit_logs 에 남기기
     * 위해 직접 주입받는다. AdminSettingsService 를 경유하지 않는 이유는 이 호출이
     * 단순 로깅이라 별도의 비즈니스 검증/DTO 변환이 필요 없기 때문이다. (2026-04-09 P1-2 확장)
     */
    private final AdminAuditService adminAuditService;

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
            summary = "감사 로그 목록 조회 (고급 필터링)",
            description = "관리자 행위 감사 로그를 최신순으로 조회한다. " +
                    "actionType/targetType/targetId/시간 범위(from~to)를 조합하여 필터링할 수 있으며, " +
                    "모든 파라미터는 선택사항이다. 2026-04-09 P1-⑤ 확장 + P2-⑮ targetId 추가."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "날짜 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @Parameter(description = "행위 유형 부분 일치 키워드 (대소문자 무시, 생략 가능)")
            @RequestParam(required = false) String actionType,

            @Parameter(description = "대상 유형 정확 일치 (예: USER, PAYMENT, SUBSCRIPTION, EXPORT_SOURCE)")
            @RequestParam(required = false) String targetType,

            @Parameter(description = "대상 엔티티 식별자 정확 일치 (예: user_abc123). " +
                    "사용자 360도 뷰에서 특정 사용자 대상 관리 조치를 조회하는 용도.")
            @RequestParam(required = false) String targetId,

            @Parameter(description = "시작 시각 inclusive (ISO-8601, 예: 2026-04-01T00:00:00)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime fromDate,

            @Parameter(description = "종료 시각 exclusive (ISO-8601)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime toDate,

            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminSettings] 감사 로그 조회 — actionType={}, targetType={}, targetId={}, from={}, to={}, page={}",
                actionType, targetType, targetId, fromDate, toDate, pageable.getPageNumber());
        Page<AuditLogResponse> result =
                adminSettingsService.getAuditLogs(actionType, targetType, targetId, fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * CSV 내보내기 이벤트를 감사 로그에 기록한다 — 2026-04-09 신규 (P1-2 확장).
     *
     * <p>CSV 내보내기는 브라우저(클라이언트 측)에서 완료되므로, 서버는 기본적으로 이
     * 이벤트를 감지할 수 없다. 따라서 프론트엔드의 {@code CsvExportButton} 이 다운로드
     * 완료 직후 이 엔드포인트를 명시적으로 호출하여 {@code admin_audit_logs} 테이블에
     * "누가 언제 어떤 소스를 몇 건 내보냈는가"를 남긴다.</p>
     *
     * <h3>왜 이 기록이 필요한가</h3>
     * <ul>
     *   <li><b>개인정보 유출 추적</b>: 사용자/결제 데이터 CSV 가 외부로 유출되는 경로의
     *       상류(source) 지점을 식별하기 위함. GDPR/개인정보보호법 감사 대응.</li>
     *   <li><b>내부 통제</b>: 관리자가 비정상적으로 대량의 민감 데이터를 내보내는 행위를
     *       사후 탐지 가능.</li>
     *   <li><b>사용 패턴 분석</b>: 운영팀이 어떤 데이터 소스에 의존하는지 파악하여 대시보드
     *       개선 방향을 결정.</li>
     * </ul>
     *
     * <h3>보안 고려사항</h3>
     * <p>요청 측(프론트엔드)이 {@code rowCount} 를 조작할 수 있으나, 이 엔드포인트의
     * 목적은 "기록"이지 "검증"이 아니다. 실제 내보낸 양을 서버가 정확히 알려면 CSV 생성
     * 자체를 서버에서 해야 하는데 현재 아키텍처는 클라이언트 측 생성이므로, 일단 클라이언트
     * 보고를 신뢰하되 비정상적 수치는 통계 분석으로 탐지한다.</p>
     *
     * @param request 내보내기 메타데이터 (source / filename / rowCount / filterInfo)
     * @return 201 Created — 본문 없음
     */
    @Operation(
            summary = "CSV 내보내기 이벤트 로그 기록",
            description = "관리자가 브라우저에서 CSV 다운로드를 완료한 직후 호출하여, " +
                    "admin_audit_logs 테이블에 CSV_EXPORT 액션을 기록한다. 파일 내용은 저장하지 않고 " +
                    "소스/파일명/행 수/필터 정보만 남긴다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "감사 로그 기록 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 필드 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @PostMapping("/audit-logs/csv-export")
    public ResponseEntity<ApiResponse<Void>> recordCsvExport(
            @Valid @RequestBody CsvExportLogRequest request
    ) {
        log.info("[AdminSettings] CSV 내보내기 이벤트 기록 — source={}, rowCount={}, filename={}",
                request.source(), request.rowCount(), request.filename());

        // 사람이 읽을 수 있는 설명 — 감사 로그 조회 UI 에서 바로 이해 가능하도록 구성
        // 예: "CSV 내보내기 — source=recommendation_logs, rows=523, file=recommendation_logs_7d_2026-04-09.csv, filters=period=7d"
        String description = String.format(
                "CSV 내보내기 — source=%s, rows=%d%s%s",
                request.source(),
                request.rowCount(),
                request.filename() != null ? ", file=" + request.filename() : "",
                request.filterInfo() != null ? ", filters=" + request.filterInfo() : ""
        );

        // 상세 스냅샷(JSON) — 구조적 필터링이 가능하도록 afterData 에 저장
        String afterData = String.format(
                "{\"source\":\"%s\",\"rowCount\":%d,\"filename\":%s,\"filterInfo\":%s}",
                escapeJsonString(request.source()),
                request.rowCount(),
                request.filename() != null ? "\"" + escapeJsonString(request.filename()) + "\"" : "null",
                request.filterInfo() != null ? "\"" + escapeJsonString(request.filterInfo()) + "\"" : "null"
        );

        // AdminAuditService 는 REQUIRES_NEW 로 격리되어 있어 실패해도 컨트롤러에 예외가 전파되지 않는다.
        adminAuditService.log(
                AdminAuditService.ACTION_CSV_EXPORT,
                AdminAuditService.TARGET_EXPORT_SOURCE,
                request.source(),
                description,
                null,
                afterData
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    /**
     * 관리자 AI 에이전트 실행 감사 로그 등록 — Step 6a (2026-04-23 신규).
     *
     * <p>monglepick-agent 의 `tool_executor` 가 Tier 2/3 쓰기 tool 을 실행한 직후 이
     * 엔드포인트로 callback 하여 감사 기록을 남긴다. actor 식별은 Agent 가 forwarding 한
     * 관리자 JWT 의 SecurityContext 에서 `AdminAuditService.resolveCurrentActor()` 가 자동
     * 추출한다(설계서 §5.1 JWT forwarding / §7.1 actionType 네임스페이스).</p>
     *
     * <h3>기존 도메인 감사 로그와의 관계</h3>
     * <p>Tier 2/3 쓰기가 실제 수행되면 Backend 의 서비스 레이어(AdminUserService 등) 가
     * 이미 {@link AdminAuditService#ACTION_USER_SUSPEND} 같은 도메인 actionType 으로 로그
     * 한 건을 남긴다. 이 EP 가 남기는 레코드는 별도 한 건 — {@code AGENT_EXECUTED} — 으로,
     * "어느 에이전트 턴이 어떤 관리 작업을 유발했는지" 를 명시적으로 추적한다. 두 레코드는
     * target(userId, paymentId 등)이 동일하므로 사후 조회 시 조인 가능.</p>
     *
     * <h3>보안</h3>
     * <p>일반 관리자(ROLE_ADMIN) 가 JWT 로 호출하며 SecurityConfig 의 `/api/v1/admin/**`
     * 인증 게이트가 1차 방어. description 에 사용자 프롬프트 원문이 담기므로 Agent 측에서
     * 과도한 길이를 자르는 책임을 지고, 여기서는 DTO `@Size(max=2000)` 검증으로 2차 방어.</p>
     */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Agent 실행 감사 로그 등록",
            description = "관리자 AI 에이전트가 Tier 2/3 쓰기 tool 을 실행한 뒤 callback 하는 감사 로그 기록 EP. " +
                    "actionType 기본값은 AGENT_EXECUTED. 기존 도메인별 감사 로그와 별도로 한 건 추가 기록한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "감사 로그 기록 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "description 누락 등 필수 필드 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @PostMapping("/audit-logs/agent")
    public ResponseEntity<ApiResponse<Void>> recordAgentExecution(
            @Valid @RequestBody AgentAuditLogRequest request
    ) {
        // actionType 미지정 시 기본값 AGENT_EXECUTED — Agent 클라이언트는 보통 생략하고
        // 더 세부 분기가 필요할 때만 override 한다(예: AGENT_FAQ_CREATE).
        String actionType = (request.actionType() != null && !request.actionType().isBlank())
                ? request.actionType()
                : AdminAuditService.ACTION_AGENT_EXECUTED;

        log.info(
                "[AdminSettings] Agent 감사 로그 등록 — actionType={}, targetType={}, targetId={}",
                actionType, request.targetType(), request.targetId()
        );

        // AdminAuditService.log 는 REQUIRES_NEW 로 격리되어 있어 실패해도 컨트롤러에
        // 예외 전파하지 않는다. before/after 는 Agent 가 Tier 3 에서만 채우고, Tier 2 는
        // 보통 null 로 호출하므로 그대로 통과시킨다.
        adminAuditService.log(
                actionType,
                request.targetType(),
                request.targetId(),
                request.description(),
                request.beforeData(),
                request.afterData()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    /**
     * 감사 로그 afterData JSON 직렬화용 문자열 이스케이프 헬퍼.
     *
     * <p>Jackson 의존을 피하고 문자열 조립으로 JSON 을 만들기 때문에, 사용자 입력
     * 문자열에 포함된 큰따옴표/역슬래시/개행을 수동으로 이스케이프해야 한다.</p>
     */
    private String escapeJsonString(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
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
     * 신규 관리자 계정을 등록한다 — 2026-04-14 신규.
     *
     * <p>기존 일반 사용자를 관리자로 승격시키는 경로. 요청 Body 로 userId 또는 email
     * 중 하나를 전달하면 해당 사용자를 조회하여 users.user_role 을 ADMIN 으로 변경하고
     * admin 테이블에 신규 레코드를 생성한다.</p>
     *
     * <h3>보안</h3>
     * <p>현재 Spring Security 는 {@code /api/v1/admin/**} 에 {@code authenticated()}
     * 만 요구한다. 운영 정책상 이 엔드포인트는 SUPER_ADMIN 만 호출해야 하지만,
     * 엔드포인트 레벨 강제는 {@code @PreAuthorize} 적용 이슈와 함께 후속 처리된다.</p>
     *
     * @param request 신규 관리자 등록 요청 DTO (userId/email + adminRole)
     * @return 생성된 관리자 계정 응답 (HTTP 201 Created)
     */
    @Operation(
            summary = "관리자 계정 신규 등록",
            description = "기존 일반 사용자를 관리자로 승격시킨다. userId 또는 email 중 하나는 필수이며, " +
                    "adminRole 은 AdminRole enum 허용값(SUPER_ADMIN/ADMIN/MODERATOR/FINANCE_ADMIN/" +
                    "SUPPORT_ADMIN/DATA_ADMIN/AI_OPS_ADMIN/STATS_ADMIN) 중 하나여야 한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "유효성 검증 실패 / 허용되지 않은 역할 / 대상 사용자 없음 / 이미 관리자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> createAdmin(
            @RequestBody @Valid AdminAccountCreateRequest request
    ) {
        log.info("[AdminSettings] 관리자 계정 신규 등록 요청 — userId={}, email={}, role={}",
                request.userId(), request.email(), request.adminRole());
        AdminAccountResponse result = adminSettingsService.createAdminAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
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
