package com.monglepick.monglepickbackend.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통계/분석 Admin API DTO 모음.
 *
 * <p>정한나 담당 영역: 서비스 KPI, 추천/검색 분석, 사용자 행동, 매출 통계.</p>
 *
 * <h3>포함된 DTO 목록</h3>
 * <ul>
 *   <li>{@link OverviewResponse} — 서비스 전체 KPI 카드 (DAU/MAU/신규/리뷰/평점/게시글)</li>
 *   <li>{@link TrendsResponse} — 일별 추이 데이터</li>
 *   <li>{@link DailyTrend} — 추이 차트 단일 데이터포인트</li>
 *   <li>{@link RecommendationResponse} — 추천 성능 지표 (CTR, 만족도)</li>
 *   <li>{@link DistributionResponse} — 장르 분포 (추천 분포 차트용)</li>
 *   <li>{@link GenreDistribution} — 장르별 추천 건수 + 비율</li>
 *   <li>{@link RecommendationLogResponse} — 추천 로그 단건 (테이블 뷰)</li>
 *   <li>{@link PopularKeywordsResponse} — 인기 검색 키워드</li>
 *   <li>{@link KeywordItem} — 키워드 단건 (검색 수 + 전환율)</li>
 *   <li>{@link SearchQualityResponse} — 검색 품질 지표 (성공률/제로결과)</li>
 *   <li>{@link BehaviorResponse} — 사용자 행동 패턴 (장르 선호 + 시간대별 활동)</li>
 *   <li>{@link GenrePreference} — 시청 이력 기반 장르 선호도</li>
 *   <li>{@link HourlyActivity} — 시간대별 활동량</li>
 *   <li>{@link RetentionResponse} — 코호트 리텐션 히트맵 데이터</li>
 *   <li>{@link CohortRow} — 주간 코호트 단건</li>
 *   <li>{@link RevenueResponse} — 매출 현황 (월 매출/MRR/일별 추이)</li>
 *   <li>{@link DailyRevenue} — 일별 매출 단건</li>
 *   <li>{@link SubscriptionStatsResponse} — 구독 통계 (활성 수/이탈률/플랜 분포)</li>
 *   <li>{@link PlanDistribution} — 플랜별 구독 분포</li>
 * </ul>
 */
public class StatsDto {

    // ──────────────────────────────────────────────
    // 1. 서비스 개요 KPI
    // ──────────────────────────────────────────────

    /**
     * 서비스 전체 KPI 카드 응답.
     *
     * <p>관리자 통계 탭 최상단의 숫자 카드 6개에 대응한다.</p>
     *
     * @param dau            일간 활성 사용자 수 (오늘 로그인한 사용자)
     * @param mau            월간 활성 사용자 수 (최근 30일 내 로그인)
     * @param newUsersWeek   이번 주 신규 가입자 수 (최근 7일)
     * @param totalReviews   전체 리뷰 수
     * @param avgRating      전체 평균 평점 (1.0~5.0, reviews 테이블 기반)
     * @param totalPosts     전체 게시글 수 (PUBLISHED 상태만)
     */
    public record OverviewResponse(
            long dau,
            long mau,
            long newUsersWeek,
            long totalReviews,
            double avgRating,
            long totalPosts
    ) {}

    // ──────────────────────────────────────────────
    // 2. 일별 추이 차트
    // ──────────────────────────────────────────────

    /**
     * 일별 추이 전체 응답.
     *
     * @param trends 날짜별 추이 데이터 리스트 (period에 따라 7~90개)
     */
    public record TrendsResponse(
            List<DailyTrend> trends
    ) {}

    /**
     * 일별 추이 단일 데이터포인트.
     *
     * @param date     날짜 문자열 (yyyy-MM-dd 형식)
     * @param dau      해당 날짜 활성 사용자 수 (lastLoginAt 기준)
     * @param newUsers 해당 날짜 신규 가입자 수 (createdAt 기준)
     * @param reviews  해당 날짜 작성된 리뷰 수
     * @param posts    해당 날짜 게시된 글 수 (PUBLISHED)
     */
    public record DailyTrend(
            String date,
            long dau,
            long newUsers,
            long reviews,
            long posts
    ) {}

    // ──────────────────────────────────────────────
    // 3. 추천 성능 지표
    // ──────────────────────────────────────────────

    /**
     * 추천 시스템 성능 지표 응답.
     *
     * <p>recommendation_logs 테이블이 구현되면 실제 데이터로 교체한다.
     * 현재는 mock 데이터를 반환한다.</p>
     *
     * @param ctr                  클릭률 (Click-Through Rate, 0.0~1.0)
     * @param satisfactionRate     만족도 (사용자 피드백 기반, 0.0~1.0)
     * @param totalRecommendations 총 추천 호출 횟수
     */
    public record RecommendationResponse(
            double ctr,
            double satisfactionRate,
            long totalRecommendations
    ) {}

    /**
     * 추천 장르 분포 응답.
     *
     * @param genres 장르별 분포 리스트
     */
    public record DistributionResponse(
            List<GenreDistribution> genres
    ) {}

    /**
     * 장르별 추천/시청 분포 단건.
     *
     * @param genre      장르명 (예: "액션", "로맨스")
     * @param count      해당 장르 건수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record GenreDistribution(
            String genre,
            long count,
            double percentage
    ) {}

    /**
     * 추천 로그 단건 응답 (테이블 뷰).
     *
     * <p>recommendation_logs 테이블 구현 시 실데이터로 교체 예정.</p>
     *
     * @param userId    사용자 ID
     * @param movieId   추천된 영화 ID
     * @param score     추천 점수 (0.0~1.0)
     * @param feedback  사용자 피드백 ("LIKE", "DISLIKE", "SKIP", null)
     * @param createdAt 추천 발생 시각
     */
    public record RecommendationLogResponse(
            String userId,
            String movieId,
            double score,
            String feedback,
            LocalDateTime createdAt
    ) {}

    // ──────────────────────────────────────────────
    // 4. 검색 분석
    // ──────────────────────────────────────────────

    /**
     * 인기 검색 키워드 응답.
     *
     * <p>search_histories 테이블 또는 ES 집계 기반.
     * 현재는 mock 데이터 반환.</p>
     *
     * @param keywords 인기 키워드 리스트 (searchCount 내림차순)
     */
    public record PopularKeywordsResponse(
            List<KeywordItem> keywords
    ) {}

    /**
     * 인기 키워드 단건.
     *
     * @param keyword        검색어
     * @param searchCount    검색 횟수
     * @param conversionRate 추천/상세 페이지 전환율 (0.0~1.0, mock)
     */
    public record KeywordItem(
            String keyword,
            long searchCount,
            double conversionRate
    ) {}

    /**
     * 검색 품질 지표 응답.
     *
     * <p>검색 성공률 및 제로결과 검색 건수를 제공한다.
     * search_histories 테이블 구현 시 실데이터로 교체 예정.</p>
     *
     * @param successRate          검색 성공률 (결과가 1건 이상인 비율, 0.0~1.0)
     * @param totalSearches        총 검색 횟수
     * @param zeroResultSearches   결과 없는 검색 횟수
     */
    public record SearchQualityResponse(
            double successRate,
            long totalSearches,
            long zeroResultSearches
    ) {}

    // ──────────────────────────────────────────────
    // 5. 사용자 행동 분석
    // ──────────────────────────────────────────────

    /**
     * 사용자 행동 패턴 분석 응답.
     *
     * @param genrePreferences 장르 선호도 분포 (reviews + movies JOIN 기반, watch_history 폐기)
     * @param hourlyActivity   시간대별 활동량 (0~23시)
     */
    public record BehaviorResponse(
            List<GenrePreference> genrePreferences,
            List<HourlyActivity> hourlyActivity
    ) {}

    /**
     * 장르별 사용자 선호도 단건.
     *
     * <p>"리뷰 작성 = 시청 완료" 단일 진실 원본 원칙에 따라 reviews 테이블의
     * movie_id를 movies 테이블과 JOIN하여 장르를 집계해야 한다 (현재는 mock).
     * watch_history 도메인은 폐기되었다 (2026-04-08).</p>
     *
     * @param genre      장르명
     * @param count      해당 장르 시청 건수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record GenrePreference(
            String genre,
            long count,
            double percentage
    ) {}

    /**
     * 시간대별 활동량 단건.
     *
     * @param hour  시간 (0~23)
     * @param count 해당 시간대 활동 수 (로그인, 검색, 추천 등 합산)
     */
    public record HourlyActivity(
            int hour,
            long count
    ) {}

    // ──────────────────────────────────────────────
    // 6. 코호트 리텐션
    // ──────────────────────────────────────────────

    /**
     * 코호트 리텐션 히트맵 전체 응답.
     *
     * <p>주간 코호트별 리텐션 데이터를 2D 히트맵으로 표시하기 위한 구조.
     * 각 행이 하나의 가입 주간 코호트에 대응한다.</p>
     *
     * @param cohorts 코호트 행 리스트 (가입 주간 순서)
     */
    public record RetentionResponse(
            List<CohortRow> cohorts
    ) {}

    /**
     * 주간 코호트 단건.
     *
     * @param cohortWeek     코호트 기준 주간 (예: "2026-W01")
     * @param cohortSize     해당 주간 신규 가입자 수
     * @param retentionRates 주차별 리텐션율 리스트 (인덱스 0=1주차, 1=2주차, ...)
     *                       값 범위: 0.0~100.0 (퍼센트)
     */
    public record CohortRow(
            String cohortWeek,
            long cohortSize,
            List<Double> retentionRates
    ) {}

    // ──────────────────────────────────────────────
    // 7. 매출 통계
    // ──────────────────────────────────────────────

    /**
     * 매출 현황 응답.
     *
     * @param monthlyRevenue 이번 달 누적 매출 (원 단위, COMPLETED 결제 합산)
     * @param mrr            월 반복 매출 — Monthly Recurring Revenue (구독 수 × 평균 구독 금액)
     * @param dailyRevenue   일별 매출 추이 리스트
     */
    public record RevenueResponse(
            long monthlyRevenue,
            long mrr,
            List<DailyRevenue> dailyRevenue
    ) {}

    /**
     * 일별 매출 단건.
     *
     * @param date   날짜 문자열 (yyyy-MM-dd)
     * @param amount 해당 날짜 매출 합계 (원 단위)
     */
    public record DailyRevenue(
            String date,
            long amount
    ) {}

    // ──────────────────────────────────────────────
    // 8. 구독 통계
    // ──────────────────────────────────────────────

    /**
     * 구독 통계 응답.
     *
     * @param totalActive 현재 활성 구독 수 (status=ACTIVE)
     * @param churnRate   구독 이탈률 (최근 30일 내 취소/만료 건수 / 이전 달 총 구독 수, mock)
     * @param plans       구독 플랜별 분포 리스트
     */
    public record SubscriptionStatsResponse(
            long totalActive,
            double churnRate,
            List<PlanDistribution> plans
    ) {}

    /**
     * 구독 플랜별 분포 단건.
     *
     * @param planCode   플랜 코드 (예: "monthly_basic", "yearly_premium")
     * @param planName   플랜 표시명 (예: "베이직 월간", "프리미엄 연간")
     * @param count      해당 플랜 활성 구독 수
     * @param percentage 전체 활성 구독 대비 비율 (0.0~100.0)
     */
    public record PlanDistribution(
            String planCode,
            String planName,
            long count,
            double percentage
    ) {}
}
