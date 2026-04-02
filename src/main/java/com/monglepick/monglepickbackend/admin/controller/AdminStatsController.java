package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.StatsDto.*;
import com.monglepick.monglepickbackend.admin.service.AdminStatsService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 통계/분석 API 컨트롤러.
 *
 * <p>정한나 담당 영역 — 서비스 KPI, 추천/검색 분석, 사용자 행동, 매출 통계.
 * 모든 엔드포인트는 읽기 전용(GET)이다.</p>
 *
 * <h3>엔드포인트 목록 (11개)</h3>
 * <ul>
 *   <li>GET /overview — 서비스 전체 KPI</li>
 *   <li>GET /trends — 일별 추이 차트</li>
 *   <li>GET /recommendation — 추천 성능 지표</li>
 *   <li>GET /recommendation/distribution — 추천 장르 분포</li>
 *   <li>GET /recommendation/logs — 추천 로그</li>
 *   <li>GET /search/popular — 인기 검색어</li>
 *   <li>GET /search/quality — 검색 품질</li>
 *   <li>GET /behavior — 사용자 행동</li>
 *   <li>GET /retention — 코호트 리텐션</li>
 *   <li>GET /revenue — 매출 현황</li>
 *   <li>GET /subscription — 구독 통계</li>
 * </ul>
 *
 * <p>인증: hasRole("ADMIN") 필수 (SecurityConfig에서 설정)</p>
 */
@Tag(name = "관리자 — 통계/분석", description = "서비스 KPI, 추천/검색 분석, 사용자 행동, 매출 통계")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    // ──────────────────────────────────────────────
    // 1. 서비스 통계
    // ──────────────────────────────────────────────

    /**
     * 서비스 전체 KPI 개요를 조회한다.
     * DAU, MAU, 신규 가입, 리뷰 수, 평균 평점, 게시글 수.
     */
    @Operation(summary = "서비스 KPI 개요", description = "DAU/MAU/신규가입/리뷰/평점/게시글 집계")
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<OverviewResponse>> getOverview(
            @Parameter(description = "기간 (7d/30d/90d)", example = "7d")
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /overview — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getOverview(period)));
    }

    /**
     * 일별 추이 데이터를 조회한다.
     * DAU, 신규 가입, 리뷰 수, 게시글 수의 일별 추이.
     */
    @Operation(summary = "일별 추이 차트", description = "기간 내 일별 DAU/신규가입/리뷰/게시글 추이")
    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<TrendsResponse>> getTrends(
            @Parameter(description = "기간 (7d/30d)", example = "7d")
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /trends — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getTrends(period)));
    }

    // ──────────────────────────────────────────────
    // 2. 추천 분석
    // ──────────────────────────────────────────────

    /**
     * 추천 시스템 성능 지표를 조회한다.
     * CTR, 만족도, 총 추천 수. (현재 mock)
     */
    @Operation(summary = "추천 성능 지표", description = "CTR/만족도/총 추천 수 (recommendation_logs 미구현 시 mock)")
    @GetMapping("/recommendation")
    public ResponseEntity<ApiResponse<RecommendationResponse>> getRecommendation(
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /recommendation — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getRecommendationPerformance(period)));
    }

    /**
     * 추천 장르 분포를 조회한다. (현재 mock)
     */
    @Operation(summary = "추천 장르 분포", description = "장르별 추천/시청 비율 분포")
    @GetMapping("/recommendation/distribution")
    public ResponseEntity<ApiResponse<DistributionResponse>> getRecommendationDistribution() {
        log.debug("[admin-stats-api] GET /recommendation/distribution");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getRecommendationDistribution()));
    }

    /**
     * 추천 로그 목록을 조회한다. (현재 빈 목록)
     */
    @Operation(summary = "추천 로그", description = "추천 로그 페이징 조회 (recommendation_logs 미구현 시 빈 목록)")
    @GetMapping("/recommendation/logs")
    public ResponseEntity<ApiResponse<List<RecommendationLogResponse>>> getRecommendationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("[admin-stats-api] GET /recommendation/logs — page={}, size={}", page, size);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getRecommendationLogs(page, size)));
    }

    // ──────────────────────────────────────────────
    // 3. 검색 분석
    // ──────────────────────────────────────────────

    /**
     * 인기 검색어를 조회한다. (현재 mock)
     */
    @Operation(summary = "인기 검색어", description = "검색 수 기준 인기 키워드 목록 (search_histories 미구현 시 mock)")
    @GetMapping("/search/popular")
    public ResponseEntity<ApiResponse<PopularKeywordsResponse>> getPopularKeywords(
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("[admin-stats-api] GET /search/popular — period={}, limit={}", period, limit);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getPopularKeywords(period, limit)));
    }

    /**
     * 검색 품질 지표를 조회한다. (현재 mock)
     */
    @Operation(summary = "검색 품질", description = "검색 성공률/총 검색 수/0건 검색 수 (mock)")
    @GetMapping("/search/quality")
    public ResponseEntity<ApiResponse<SearchQualityResponse>> getSearchQuality(
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /search/quality — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getSearchQuality(period)));
    }

    // ──────────────────────────────────────────────
    // 4. 사용자 행동
    // ──────────────────────────────────────────────

    /**
     * 사용자 행동 패턴을 조회한다.
     * 장르 선호도, 시간대별 활동량. (현재 mock)
     */
    @Operation(summary = "사용자 행동", description = "장르 선호도 + 시간대별 활동량 (mock)")
    @GetMapping("/behavior")
    public ResponseEntity<ApiResponse<BehaviorResponse>> getBehavior(
            @RequestParam(defaultValue = "30d") String period) {
        log.debug("[admin-stats-api] GET /behavior — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getUserBehavior(period)));
    }

    /**
     * 주간 코호트 리텐션을 조회한다.
     * User.createdAt 기반 주간 코호트, lastLoginAt으로 재방문 판단.
     */
    @Operation(summary = "코호트 리텐션", description = "주간 코호트별 리텐션율 히트맵")
    @GetMapping("/retention")
    public ResponseEntity<ApiResponse<RetentionResponse>> getRetention(
            @Parameter(description = "분석할 주간 수", example = "4")
            @RequestParam(defaultValue = "4") int weeks) {
        log.debug("[admin-stats-api] GET /retention — weeks={}", weeks);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getRetention(weeks)));
    }

    // ──────────────────────────────────────────────
    // 5. 매출
    // ──────────────────────────────────────────────

    /**
     * 매출 현황을 조회한다.
     * 월 매출, MRR, 일별 추이.
     */
    @Operation(summary = "매출 현황", description = "월 매출/MRR/일별 추이 (PaymentOrder 기반)")
    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueResponse>> getRevenue(
            @RequestParam(defaultValue = "30d") String period) {
        log.debug("[admin-stats-api] GET /revenue — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getRevenue(period)));
    }

    /**
     * 구독 통계를 조회한다.
     * 활성 구독 수, 이탈률, 플랜별 분포.
     */
    @Operation(summary = "구독 통계", description = "활성 구독 수/이탈률/플랜별 분포")
    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<SubscriptionStatsResponse>> getSubscription() {
        log.debug("[admin-stats-api] GET /subscription");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getSubscriptionStats()));
    }
}
