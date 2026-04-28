package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.KeywordResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.UpdateExcludedRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.UpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.StatsDto.*;
import com.monglepick.monglepickbackend.admin.service.AdminPopularSearchService;
import com.monglepick.monglepickbackend.admin.service.AdminStatsService;
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
import org.springframework.data.domain.Sort;
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
    private final AdminPopularSearchService adminPopularSearchService;

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
     * 인기 검색어를 조회한다.
     */
    @Operation(summary = "인기 검색어", description = "trending_keywords 누적 검색 수 기반 TOP N. 기간 필터 영향 없음")
    @GetMapping("/search/popular")
    public ResponseEntity<ApiResponse<PopularKeywordsResponse>> getPopularKeywords(
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("[admin-stats-api] GET /search/popular — limit={}", limit);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getPopularKeywords(limit)));
    }

    /**
     * 기간별 검색 이력 키워드 통계를 조회한다.
     */
    @Operation(summary = "검색 이력 키워드 통계", description = "search_history 기반 기간별 TOP N 키워드 통계 (전환율은 30분 세션 기준)")
    @GetMapping("/search/history")
    public ResponseEntity<ApiResponse<SearchHistoryKeywordsResponse>> getSearchHistoryKeywords(
            @Parameter(description = "기간 (1d/7d/30d)", example = "7d")
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("[admin-stats-api] GET /search/history — period={}, limit={}", period, limit);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getSearchHistoryKeywords(period, limit)));
    }

    /**
     * 특정 키워드의 클릭 영화 통계를 조회한다.
     */
    @Operation(summary = "검색 키워드 클릭 상세", description = "search_history.clicked_movie_id 기반 기간별 클릭 영화 통계")
    @GetMapping("/search/history/clicks")
    public ResponseEntity<ApiResponse<SearchKeywordClicksResponse>> getSearchKeywordClicks(
            @RequestParam String keyword,
            @Parameter(description = "기간 (1d/7d/30d)", example = "7d")
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("[admin-stats-api] GET /search/history/clicks — keyword={}, period={}, limit={}",
                keyword, period, limit);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getSearchKeywordClicks(keyword, period, limit)));
    }

    /**
     * 검색 품질 지표를 조회한다.
     */
    @Operation(summary = "검색 품질", description = "기간별 검색 성공률/총 검색 수/0건 검색 수")
    @GetMapping("/search/quality")
    public ResponseEntity<ApiResponse<SearchQualityResponse>> getSearchQuality(
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /search/quality — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getSearchQuality(period)));
    }

    /**
     * 인기 검색어 운영 메타 목록을 조회한다.
     */
    @Operation(summary = "인기 검색어 운영 목록", description = "상단 TOP 20/하단 운영 관리 공용 메타 목록")
    @GetMapping("/popular-keywords")
    public ResponseEntity<ApiResponse<Page<KeywordResponse>>> getPopularKeywordMetaList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page, size,
                Sort.by(Sort.Order.desc("manualPriority"), Sort.Order.desc("createdAt"))
        );
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.getKeywords(pageable)));
    }

    /**
     * 인기 검색어 운영 메타 단건을 조회한다.
     */
    @Operation(summary = "인기 검색어 운영 단건", description = "TOP 20 편집 모달에서 사용하는 운영 메타 단건 조회")
    @GetMapping("/popular-keywords/{id}")
    public ResponseEntity<ApiResponse<KeywordResponse>> getPopularKeywordMeta(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.getKeyword(id)));
    }

    /**
     * 인기 검색어 운영 메타를 신규 등록한다.
     */
    @Operation(summary = "인기 검색어 운영 등록", description = "TOP 20 키워드에 운영 메타를 신규 연결한다")
    @PostMapping("/popular-keywords")
    public ResponseEntity<ApiResponse<KeywordResponse>> createPopularKeywordMeta(
            @Valid @RequestBody CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminPopularSearchService.createKeyword(request)));
    }

    /**
     * 인기 검색어 운영 메타를 수정한다.
     */
    @Operation(summary = "인기 검색어 운영 수정", description = "displayRank/manualPriority/isExcluded/adminNote 수정")
    @PutMapping("/popular-keywords/{id}")
    public ResponseEntity<ApiResponse<KeywordResponse>> updatePopularKeywordMeta(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.updateKeyword(id, request)));
    }

    /**
     * 인기 검색어 제외 상태를 토글한다.
     */
    @Operation(summary = "인기 검색어 제외 토글", description = "TOP 20 리스트에서 즉시 제외/복원")
    @PatchMapping("/popular-keywords/{id}/excluded")
    public ResponseEntity<ApiResponse<KeywordResponse>> updatePopularKeywordMetaExcluded(
            @PathVariable Long id,
            @RequestBody UpdateExcludedRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminPopularSearchService.updateExcluded(id, request)));
    }

    /**
     * 인기 검색어 운영 메타를 삭제한다.
     */
    @Operation(summary = "인기 검색어 운영 삭제", description = "popular_search_keyword hard delete")
    @DeleteMapping("/popular-keywords/{id}")
    public ResponseEntity<Void> deletePopularKeywordMeta(@PathVariable Long id) {
        adminPopularSearchService.deleteKeyword(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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

    // ──────────────────────────────────────────────
    // 6. 포인트 경제
    // ──────────────────────────────────────────────

    /**
     * 포인트 경제 개요 KPI를 조회한다.
     * 총 발행/소비, 전체 잔액, 활성 사용자, 오늘 발행/소비.
     */
    @Operation(summary = "포인트 경제 개요", description = "포인트 유통 KPI 6개 (총발행/소비/잔액/활성사용자/오늘)")
    @GetMapping("/point-economy/overview")
    public ResponseEntity<ApiResponse<PointEconomyOverviewResponse>> getPointEconomyOverview() {
        log.debug("[admin-stats-api] GET /point-economy/overview");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getPointEconomyOverview()));
    }

    /**
     * 포인트 유형별 분포를 조회한다.
     * earn/spend/bonus/expire/refund/revoke 유형별 건수 + 합계.
     */
    @Operation(summary = "포인트 유형별 분포", description = "포인트 유형(earn/spend/bonus 등)별 건수 + 합계")
    @GetMapping("/point-economy/distribution")
    public ResponseEntity<ApiResponse<PointTypeDistributionResponse>> getPointTypeDistribution() {
        log.debug("[admin-stats-api] GET /point-economy/distribution");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getPointTypeDistribution()));
    }

    /**
     * 등급별 사용자 분포를 조회한다.
     */
    @Operation(summary = "등급별 사용자 분포", description = "6등급(알갱이~몽아일체) 사용자 수 + 비율")
    @GetMapping("/point-economy/grades")
    public ResponseEntity<ApiResponse<GradeDistributionResponse>> getGradeDistribution() {
        log.debug("[admin-stats-api] GET /point-economy/grades");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getGradeDistribution()));
    }

    /**
     * 일별 포인트 발행/소비 추이를 조회한다.
     */
    @Operation(summary = "일별 포인트 추이", description = "일별 발행/소비/순유입 추이")
    @GetMapping("/point-economy/trends")
    public ResponseEntity<ApiResponse<PointTrendsResponse>> getPointTrends(
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /point-economy/trends — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getPointTrends(period)));
    }

    // ──────────────────────────────────────────────
    // 7. AI 서비스
    // ──────────────────────────────────────────────

    /**
     * AI 서비스 개요 KPI를 조회한다.
     * 전체/오늘 세션, 턴, 세션당 평균 턴, 추천 영화 수.
     */
    @Operation(summary = "AI 서비스 개요", description = "AI 채팅 KPI 6개 (세션/턴/평균/오늘)")
    @GetMapping("/ai-service/overview")
    public ResponseEntity<ApiResponse<AiServiceOverviewResponse>> getAiServiceOverview() {
        log.debug("[admin-stats-api] GET /ai-service/overview");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getAiServiceOverview()));
    }

    /**
     * AI 세션 일별 추이를 조회한다.
     */
    @Operation(summary = "AI 세션 일별 추이", description = "일별 세션 수 + 턴 수 추이")
    @GetMapping("/ai-service/trends")
    public ResponseEntity<ApiResponse<AiSessionTrendsResponse>> getAiSessionTrends(
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /ai-service/trends — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getAiSessionTrends(period)));
    }

    /**
     * AI 의도(Intent) 분포를 조회한다.
     */
    @Operation(summary = "AI 의도 분포", description = "추천/검색/일반대화/관계 등 의도별 건수 + 비율")
    @GetMapping("/ai-service/intents")
    public ResponseEntity<ApiResponse<AiIntentDistributionResponse>> getAiIntentDistribution() {
        log.debug("[admin-stats-api] GET /ai-service/intents");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getAiIntentDistribution()));
    }

    /**
     * AI 쿼터 소진 현황을 조회한다.
     */
    @Operation(summary = "AI 쿼터 소진 현황", description = "평균 사용량/구매 이용권/한도 소진 사용자 수")
    @GetMapping("/ai-service/quota")
    public ResponseEntity<ApiResponse<AiQuotaStatsResponse>> getAiQuotaStats() {
        log.debug("[admin-stats-api] GET /ai-service/quota");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getAiQuotaStats()));
    }

    // ──────────────────────────────────────────────
    // 8. 커뮤니티
    // ──────────────────────────────────────────────

    /**
     * 커뮤니티 개요 KPI를 조회한다.
     * 게시글, 댓글, 신고 수 + 오늘 수치.
     */
    @Operation(summary = "커뮤니티 개요", description = "커뮤니티 KPI 6개 (게시글/댓글/신고/오늘)")
    @GetMapping("/community/overview")
    public ResponseEntity<ApiResponse<CommunityOverviewResponse>> getCommunityOverview() {
        log.debug("[admin-stats-api] GET /community/overview");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getCommunityOverview()));
    }

    /**
     * 커뮤니티 일별 추이를 조회한다.
     */
    @Operation(summary = "커뮤니티 일별 추이", description = "일별 게시글/댓글/신고 추이")
    @GetMapping("/community/trends")
    public ResponseEntity<ApiResponse<CommunityTrendsResponse>> getCommunityTrends(
            @RequestParam(defaultValue = "7d") String period) {
        log.debug("[admin-stats-api] GET /community/trends — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getCommunityTrends(period)));
    }

    /**
     * 게시글 카테고리별 분포를 조회한다.
     */
    @Operation(summary = "카테고리별 게시글 분포", description = "자유/토론/추천/뉴스/플레이리스트 카테고리별 건수 + 비율")
    @GetMapping("/community/categories")
    public ResponseEntity<ApiResponse<PostCategoryDistributionResponse>> getPostCategoryDistribution() {
        log.debug("[admin-stats-api] GET /community/categories");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getPostCategoryDistribution()));
    }

    /**
     * 신고/독성 분석을 조회한다.
     */
    @Operation(summary = "신고/독성 분석", description = "신고 상태별 분포 + 독성 점수 구간 분포 + 처리 완료율")
    @GetMapping("/community/reports")
    public ResponseEntity<ApiResponse<ReportAnalysisResponse>> getReportAnalysis() {
        log.debug("[admin-stats-api] GET /community/reports");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getReportAnalysis()));
    }

    // ──────────────────────────────────────────────
    // 9. 사용자 참여도
    // ──────────────────────────────────────────────

    /**
     * 사용자 참여도 개요 KPI를 조회한다.
     *
     * <p>총 출석 체크 수, 오늘 출석 수, 활동 진행 사용자 수,
     * 총 위시리스트 수, 평균 연속 출석일 5가지 지표를 반환한다.</p>
     */
    @Operation(summary = "사용자 참여도 개요",
               description = "총 출석 수/오늘 출석/활동 진행 사용자/위시리스트/평균 연속 출석일 KPI")
    @GetMapping("/engagement/overview")
    public ResponseEntity<ApiResponse<EngagementOverviewResponse>> getEngagementOverview() {
        log.debug("[admin-stats-api] GET /engagement/overview");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getEngagementOverview()));
    }

    /**
     * 활동 유형별 참여 현황을 조회한다.
     *
     * <p>user_activity_progress 테이블에서 actionType별 참여 사용자 수와
     * 총 활동 횟수를 집계한다. totalActions 내림차순 정렬.</p>
     */
    @Operation(summary = "활동별 참여 현황",
               description = "활동 유형(리뷰 작성/출석/좋아요 등)별 참여 사용자 수 + 총 활동 횟수")
    @GetMapping("/engagement/activity-distribution")
    public ResponseEntity<ApiResponse<ActivityDistributionResponse>> getActivityDistribution() {
        log.debug("[admin-stats-api] GET /engagement/activity-distribution");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getActivityDistribution()));
    }

    /**
     * 연속 출석일 구간별 사용자 분포를 조회한다.
     *
     * <p>사용자별 현재 연속 출석일(streakCount)을 기준으로
     * 1일/2-3일/4-7일/8-14일/15-30일/31일+ 구간으로 분류한다.</p>
     */
    @Operation(summary = "연속 출석일 구간 분포",
               description = "사용자별 최신 연속 출석일을 6구간으로 분류한 분포")
    @GetMapping("/engagement/attendance-streak")
    public ResponseEntity<ApiResponse<AttendanceStreakDistributionResponse>> getAttendanceStreakDistribution() {
        log.debug("[admin-stats-api] GET /engagement/attendance-streak");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getAttendanceStreakDistribution()));
    }

    // ──────────────────────────────────────────────
    // 10. 콘텐츠 성과
    // ──────────────────────────────────────────────

    /**
     * 콘텐츠 성과 개요 KPI를 조회한다.
     *
     * <p>도장깨기 코스 진행/완주/완주율, 업적 달성 수,
     * 퀴즈 시도 수, 퀴즈 정답률 6가지 지표를 반환한다.</p>
     */
    @Operation(summary = "콘텐츠 성과 개요",
               description = "코스 진행/완주/완주율 + 업적 달성 + 퀴즈 시도/정답률 KPI")
    @GetMapping("/content-performance/overview")
    public ResponseEntity<ApiResponse<ContentPerformanceOverviewResponse>> getContentPerformanceOverview() {
        log.debug("[admin-stats-api] GET /content-performance/overview");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getContentPerformanceOverview()));
    }

    /**
     * 코스별 완주율을 조회한다.
     *
     * <p>courseId별 시작자 수, 완주자 수, 완주율, 평균 진행률을
     * 완주자 수 내림차순으로 반환한다.</p>
     */
    @Operation(summary = "코스별 완주율",
               description = "도장깨기 코스별 시작자/완주자 수 + 완주율 + 평균 진행률")
    @GetMapping("/content-performance/course-completion")
    public ResponseEntity<ApiResponse<CourseCompletionResponse>> getCourseCompletion() {
        log.debug("[admin-stats-api] GET /content-performance/course-completion");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getCourseCompletion()));
    }

    /**
     * 리뷰 품질 지표를 조회한다.
     *
     * <p>카테고리별 건수 분포, 평점(1~5)별 건수 분포, 전체 리뷰 수, 평균 평점을 반환한다.</p>
     */
    @Operation(summary = "리뷰 품질 지표",
               description = "리뷰 카테고리별 건수 + 평점 분포 + 전체 리뷰 수 + 평균 평점")
    @GetMapping("/content-performance/review-quality")
    public ResponseEntity<ApiResponse<ReviewQualityResponse>> getReviewQualityStats() {
        log.debug("[admin-stats-api] GET /content-performance/review-quality");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getReviewQualityStats()));
    }

    // ──────────────────────────────────────────────
    // 11. 전환 퍼널
    // ──────────────────────────────────────────────

    /**
     * 6단계 전환 퍼널을 조회한다.
     *
     * <p>신규 가입 → 첫 활동 → AI 채팅 → 리뷰 작성 → 구독 → 결제 순서의
     * 전환율을 분석한다. 각 단계별 전 단계 대비 전환율과 1단계 대비 전환율을 제공한다.</p>
     *
     * @param period 분석 기간 (예: "7d", "30d", "90d", 기본값 "30d")
     */
    @Operation(summary = "전환 퍼널",
               description = "가입→첫활동→AI채팅→리뷰→구독→결제 6단계 전환율 분석")
    @GetMapping("/funnel/conversion")
    public ResponseEntity<ApiResponse<FunnelConversionResponse>> getFunnelConversion(
            @Parameter(description = "분석 기간 (7d/30d/90d)", example = "30d")
            @RequestParam(defaultValue = "30d") String period) {
        log.debug("[admin-stats-api] GET /funnel/conversion — period={}", period);
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getFunnelConversion(period)));
    }

    // ──────────────────────────────────────────────
    // 12. 이탈 위험
    // ──────────────────────────────────────────────

    /**
     * 이탈 위험 개요 KPI를 조회한다.
     *
     * <p>최대 1000명 샘플을 로드하여 위험도 점수(0~100)를 계산하고
     * 없음(0-24) / 낮음(25-49) / 중간(50-74) / 높음(75+) 4구간으로 분류한다.</p>
     *
     * <p>점수 기준: 30일+ 미로그인(40점), 14일+ 미로그인(25점), 7일+ 미로그인(10점),
     * 포인트 잔액 0 + 가입 7일 이상(15점), AI 세션 없음 + 가입 14일 이상(20점),
     * 구독 미보유(10점).</p>
     */
    @Operation(summary = "이탈 위험 개요",
               description = "위험도 점수 기반 4구간(없음/낮음/중간/높음) 사용자 수 (최대 1000명 샘플)")
    @GetMapping("/churn-risk/overview")
    public ResponseEntity<ApiResponse<ChurnRiskOverviewResponse>> getChurnRiskOverview() {
        log.debug("[admin-stats-api] GET /churn-risk/overview");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getChurnRiskOverview()));
    }

    /**
     * 이탈 위험 신호 집계를 조회한다.
     *
     * <p>7일/14일/30일+ 미로그인 사용자 수, 포인트 잔액 0 사용자 수,
     * 구독 만료 후 미갱신 사용자 수를 반환한다.</p>
     */
    @Operation(summary = "이탈 위험 신호",
               description = "미로그인 기간별 사용자 수 + 포인트 잔액 0 + 구독 만료 미갱신 집계")
    @GetMapping("/churn-risk/signals")
    public ResponseEntity<ApiResponse<ChurnRiskSignalsResponse>> getChurnRiskSignals() {
        log.debug("[admin-stats-api] GET /churn-risk/signals");
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getChurnRiskSignals()));
    }
}
