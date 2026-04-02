package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.DashboardDto;
import com.monglepick.monglepickbackend.admin.service.AdminDashboardService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 대시보드 API 컨트롤러.
 *
 * <ul>
 *   <li>GET /api/v1/admin/dashboard/kpi      — KPI 카드 (핵심 지표 요약)</li>
 *   <li>GET /api/v1/admin/dashboard/trends   — 추이 차트 (최근 N일 일별 데이터)</li>
 *   <li>GET /api/v1/admin/dashboard/recent   — 최근 활동 피드</li>
 * </ul>
 *
 * <p>인증: hasRole("ADMIN") 필수 (SecurityConfig 에서 설정)</p>
 *
 * <p>주의: {@link ApiResponse}(글로벌 응답 래퍼)와
 * {@code io.swagger.v3.oas.annotations.responses.ApiResponse}(Swagger 애노테이션)의
 * 이름이 동일하므로, Swagger 애노테이션은 FQCN으로 사용하여 충돌을 방지한다.</p>
 */
@Tag(name = "관리자 — 대시보드", description = "KPI 카드, 추이 차트, 최근 활동 피드")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    // ────────────────────────────────────────────
    // KPI 카드
    // ────────────────────────────────────────────

    /**
     * KPI 카드 데이터를 조회한다.
     *
     * <p>전체 회원 수, 오늘/어제 신규 가입, 활성 구독, 오늘/어제 결제 금액,
     * 미처리 신고, 오늘 AI 채팅 요청 수를 반환한다.</p>
     *
     * <p>프론트엔드에서는 오늘/어제 값을 비교하여 전일 대비 증감률을 계산한다.</p>
     */
    @Operation(
            summary = "KPI 카드 조회",
            description = "전체 회원 수, 신규 가입, 활성 구독, 결제 금액, 미처리 신고 등 핵심 지표를 반환한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "KPI 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 필요")
    })
    @GetMapping("/kpi")
    public ResponseEntity<ApiResponse<DashboardDto.KpiResponse>> getKpi() {
        log.info("[관리자 대시보드] KPI 카드 조회 요청");
        DashboardDto.KpiResponse result = adminDashboardService.getKpi();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ────────────────────────────────────────────
    // 추이 차트
    // ────────────────────────────────────────────

    /**
     * 최근 N일 일별 추이 데이터를 조회한다.
     *
     * <p>신규 가입 수, 결제 금액, AI 채팅 요청 수를 날짜별로 집계하여 반환한다.
     * 프론트엔드 차트 컴포넌트(Line/Bar Chart)에 바로 바인딩 가능한 형태로 반환한다.</p>
     *
     * @param days 조회 일수 (기본 7일, 최소 1일, 최대 90일)
     */
    @Operation(
            summary = "추이 차트 조회",
            description = "최근 N일 일별 신규 가입 수, 결제 금액, AI 채팅 요청 수 추이를 반환한다. (기본 7일)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추이 데이터 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 필요")
    })
    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<DashboardDto.TrendsResponse>> getTrends(
            @Parameter(description = "조회 일수 (1~90, 기본 7)", example = "7")
            @RequestParam(defaultValue = "7") int days
    ) {
        log.info("[관리자 대시보드] 추이 차트 조회 요청 — days={}", days);
        DashboardDto.TrendsResponse result = adminDashboardService.getTrends(days);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ────────────────────────────────────────────
    // 최근 활동 피드
    // ────────────────────────────────────────────

    /**
     * 최근 활동 피드를 조회한다.
     *
     * <p>결제 주문, 신고 등 여러 도메인의 최근 이벤트를 통합하여 최신순으로 반환한다.
     * 대시보드 사이드바 또는 하단 피드 섹션에 표시한다.</p>
     *
     * @param limit 최대 반환 건수 (기본 20건, 최소 1건, 최대 100건)
     */
    @Operation(
            summary = "최근 활동 피드 조회",
            description = "결제, 신고 등 여러 도메인의 최근 활동을 통합하여 최신순으로 반환한다. (기본 20건)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "최근 활동 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 필요")
    })
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<DashboardDto.RecentActivitiesResponse>> getRecentActivities(
            @Parameter(description = "최대 반환 건수 (1~100, 기본 20)", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("[관리자 대시보드] 최근 활동 피드 조회 요청 — limit={}", limit);
        DashboardDto.RecentActivitiesResponse result = adminDashboardService.getRecentActivities(limit);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
