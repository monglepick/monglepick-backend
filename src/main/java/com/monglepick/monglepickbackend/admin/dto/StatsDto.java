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
     * <p>2026-04-15 확장: Agent 가 `recommendation_log` 에 직접 저장하는 필드들이
     * 관리자 화면에 실제로 노출되도록 DTO 확장. 기존 필드명/타입은 하위 호환 위해 유지하고
     * 영화 제목/이유/랭크/하이브리드 점수/intent/세션 ID 를 추가했다.</p>
     *
     * @param recommendationLogId 추천 로그 PK (Row key)
     * @param userId              사용자 ID
     * @param movieId             추천된 영화 ID
     * @param movieTitle          추천된 영화 제목 (Movie JOIN FETCH 결과)
     * @param score               추천 점수 (recommendation_log.score 원본값)
     * @param hybridScore         하이브리드 합산 점수 (CF + CBF, nullable)
     * @param rankPosition        추천 목록 내 순위 (1부터, nullable)
     * @param reason              AI 가 생성한 추천 이유
     * @param userIntent          Intent-First 요약 (nullable)
     * @param sessionId           chat_session_archive.session_id 와 매칭
     * @param feedback            사용자 피드백 ("CLICKED", null). 상세 피드백은 별도 탭 예정
     * @param createdAt           추천 발생 시각
     */
    public record RecommendationLogResponse(
            Long recommendationLogId,
            String userId,
            String movieId,
            String movieTitle,
            double score,
            Double hybridScore,
            Integer rankPosition,
            String reason,
            String userIntent,
            String sessionId,
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
     * @param conversionRate 추천/상세 페이지 전환율 (0.0~1.0)
     * @param id             popular_search_keyword PK (운영 메타 없으면 null)
     * @param displayRank    고정 노출 순위 (nullable)
     * @param manualPriority 수동 우선순위 (nullable)
     * @param isExcluded     제외 여부 (운영 메타 없으면 null)
     * @param adminNote      관리자 메모 (nullable)
     */
    public record KeywordItem(
            String keyword,
            long searchCount,
            double conversionRate,
            Long id,
            Integer displayRank,
            Integer manualPriority,
            Boolean isExcluded,
            String adminNote
    ) {}

    /**
     * 기간별 검색 이력 키워드 통계 응답.
     *
     * @param keywords 키워드 통계 리스트 (검색 수 내림차순)
     */
    public record SearchHistoryKeywordsResponse(
            List<SearchHistoryKeywordItem> keywords
    ) {}

    /**
     * 기간별 검색 이력 키워드 단건.
     *
     * @param keyword        검색어
     * @param searchCount    검색 횟수 (clicked_movie_id IS NULL 기준)
     * @param resultCount    기간 내 누적 검색 결과 수
     * @param conversionRate 검색 세션 대비 클릭 발생 세션 비율 (0.0~1.0)
     */
    public record SearchHistoryKeywordItem(
            String keyword,
            long searchCount,
            long resultCount,
            double conversionRate
    ) {}

    /**
     * 특정 키워드의 클릭 영화 통계 응답.
     *
     * @param keyword    기준 키워드
     * @param totalClicks 기간 내 총 클릭 수
     * @param movies     클릭 영화 통계 리스트
     */
    public record SearchKeywordClicksResponse(
            String keyword,
            long totalClicks,
            List<SearchKeywordClickItem> movies
    ) {}

    /**
     * 특정 키워드의 클릭 영화 단건.
     *
     * @param movieId     클릭된 영화 ID
     * @param movieTitle  클릭된 영화 제목 (미등록 시 null)
     * @param clickCount  클릭 수
     * @param clickRate   해당 키워드 클릭 내 비중 (0.0~1.0)
     */
    public record SearchKeywordClickItem(
            String movieId,
            String movieTitle,
            long clickCount,
            double clickRate
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
     * 매출 현황 응답 (확장 — 2026-04-28).
     *
     * <p>Frontend RevenueTab 의 모든 KPI 카드/차트가 이 DTO 하나로 렌더링된다.
     * 기존 monthlyRevenue/mrr/dailyRevenue 외에 ARPU·환불·플랜·결제수단·시간대
     * ·요일·신규vs갱신·Top payer 까지 한 번의 API 호출로 제공한다.</p>
     *
     * <h4>금액 단위</h4>
     * <p>모든 금액 필드는 {@code long} (원 단위, KRW). 환불은 양수로 표시하며 별도로 차감 표시.
     * netRevenue = monthlyRevenue - refundAmount.</p>
     *
     * @param monthlyRevenue           이번 달 누적 매출 (COMPLETED 합산)
     * @param mrr                      월 반복 매출 (구독 활성 사용자 × 월환산 가격)
     * @param arpu                     사용자 1인당 평균 매출 (선택 기간 매출 / 결제 고유 사용자 수)
     * @param avgOrderValue            객단가 (선택 기간 매출 / 결제 건수)
     * @param totalRevenue             서비스 시작 이후 총 누적 매출
     * @param todayRevenue             오늘 매출
     * @param yesterdayRevenue         어제 매출
     * @param weekRevenue              이번 주(월요일~) 매출
     * @param totalOrders              선택 기간 총 결제 건수 (COMPLETED)
     * @param todayOrders              오늘 결제 건수
     * @param payingUsers              선택 기간 결제 고유 사용자 수
     * @param refundAmount             선택 기간 환불 금액
     * @param refundCount              선택 기간 환불 건수
     * @param refundRate               환불률 (0.0~1.0, 환불 금액 / 매출)
     * @param netRevenue               순 매출 (이번 달 매출 - 이번 달 환불)
     * @param dailyRevenue             일별 매출 추이 (선택 기간)
     * @param monthlyRevenueTrend      월별 매출 추이 (최근 12개월)
     * @param paymentMethodDistribution 결제 수단별 분포 (선택 기간)
     * @param planRevenueDistribution  구독 플랜별 매출 (선택 기간)
     * @param orderTypeDistribution    주문 유형별 분포 (point_pack vs subscription)
     * @param hourlyDistribution       시간대별(0~23시) 결제 분포 (선택 기간)
     * @param weekdayDistribution      요일별(월~일) 결제 분포 (선택 기간)
     * @param topPayers                Top 10 결제 사용자 (선택 기간)
     */
    public record RevenueResponse(
            long monthlyRevenue,
            long mrr,
            long arpu,
            long avgOrderValue,
            long totalRevenue,
            long todayRevenue,
            long yesterdayRevenue,
            long weekRevenue,
            long totalOrders,
            long todayOrders,
            long payingUsers,
            long refundAmount,
            long refundCount,
            double refundRate,
            long netRevenue,
            List<DailyRevenue> dailyRevenue,
            List<MonthlyRevenue> monthlyRevenueTrend,
            List<PaymentMethodItem> paymentMethodDistribution,
            List<PlanRevenueItem> planRevenueDistribution,
            List<OrderTypeItem> orderTypeDistribution,
            List<HourlyRevenueItem> hourlyDistribution,
            List<WeekdayRevenueItem> weekdayDistribution,
            List<TopPayerItem> topPayers
    ) {}

    /**
     * 일별 매출 단건.
     *
     * @param date   날짜 문자열 (yyyy-MM-dd)
     * @param amount 해당 날짜 매출 합계 (원 단위)
     * @param count  해당 날짜 결제 건수
     */
    public record DailyRevenue(
            String date,
            long amount,
            long count
    ) {}

    /**
     * 월별 매출 단건 (12개월 추이용).
     *
     * @param month  월 문자열 (yyyy-MM)
     * @param amount 해당 월 매출 합계
     * @param count  해당 월 결제 건수
     */
    public record MonthlyRevenue(
            String month,
            long amount,
            long count
    ) {}

    /**
     * 결제 수단(pgProvider)별 분포.
     *
     * @param provider 결제 수단 코드 (toss, kakao, naver 등 — null 이면 "unknown")
     * @param label    표시명 (예: "토스페이먼츠")
     * @param amount   금액 합계
     * @param count    건수
     * @param ratio    전체 대비 비율 (0.0~1.0)
     */
    public record PaymentMethodItem(
            String provider,
            String label,
            long amount,
            long count,
            double ratio
    ) {}

    /**
     * 구독 플랜별 매출 분포.
     *
     * @param planCode 플랜 코드 (monthly_basic 등)
     * @param planName 플랜 표시명
     * @param amount   해당 플랜 매출 합계
     * @param count    해당 플랜 결제 건수
     * @param ratio    구독 매출 내 비율 (0.0~1.0)
     */
    public record PlanRevenueItem(
            String planCode,
            String planName,
            long amount,
            long count,
            double ratio
    ) {}

    /**
     * 주문 유형별 분포 (point_pack vs subscription).
     *
     * @param type   "POINT_PACK" 또는 "SUBSCRIPTION"
     * @param label  표시명 ("포인트팩"/"구독")
     * @param amount 매출 합계
     * @param count  건수
     * @param ratio  전체 대비 비율 (0.0~1.0)
     */
    public record OrderTypeItem(
            String type,
            String label,
            long amount,
            long count,
            double ratio
    ) {}

    /**
     * 시간대별 결제 분포.
     *
     * @param hour   시간 (0~23)
     * @param amount 해당 시간 매출
     * @param count  해당 시간 건수
     */
    public record HourlyRevenueItem(
            int hour,
            long amount,
            long count
    ) {}

    /**
     * 요일별 결제 분포.
     *
     * @param weekday    요일 인덱스 (1=월요일 ~ 7=일요일)
     * @param weekdayName 표시명 ("월", "화", ...)
     * @param amount     해당 요일 매출
     * @param count      해당 요일 건수
     */
    public record WeekdayRevenueItem(
            int weekday,
            String weekdayName,
            long amount,
            long count
    ) {}

    /**
     * Top 결제 사용자.
     *
     * @param userId      사용자 ID
     * @param nickname    닉네임 (없으면 userId 앞 8자리)
     * @param totalAmount 누적 결제액
     * @param orderCount  결제 건수
     */
    public record TopPayerItem(
            String userId,
            String nickname,
            long totalAmount,
            long orderCount
    ) {}

    // ──────────────────────────────────────────────
    // 8. 구독 통계
    // ──────────────────────────────────────────────

    /**
     * 구독 통계 응답 (확장 — 2026-04-28).
     *
     * <p>Frontend SubscriptionTab/RevenueTab 에서 사용. 필드명을 Frontend 와 정렬:
     * activeSubscriptions / planDistribution / churnRate(0.0~1.0).</p>
     *
     * @param activeSubscriptions    현재 활성 구독 수 (status=ACTIVE)
     * @param totalSubscriptions     역대 총 구독 수 (모든 상태 합계)
     * @param newThisMonth           이번 달 신규 구독 건수
     * @param cancelledThisMonth     이번 달 취소 구독 건수
     * @param expiredThisMonth       이번 달 만료(미갱신) 추정 건수
     * @param churnRate              구독 이탈률 (0.0~1.0, 누적 취소+만료 / 누적 전체)
     * @param subscriptionMrr        활성 구독 기준 MRR (월환산)
     * @param avgRevenuePerSubscriber 활성 구독자 1인당 월 평균 매출
     * @param planDistribution       구독 플랜별 가입자 분포 (Frontend 호환 키)
     * @param planMrr                플랜별 월 환산 MRR 기여도
     */
    public record SubscriptionStatsResponse(
            long activeSubscriptions,
            long totalSubscriptions,
            long newThisMonth,
            long cancelledThisMonth,
            long expiredThisMonth,
            double churnRate,
            long subscriptionMrr,
            long avgRevenuePerSubscriber,
            List<PlanDistribution> planDistribution,
            List<PlanMrrItem> planMrr
    ) {}

    /**
     * 구독 플랜별 분포 단건 (Frontend 호환 — plan/ratio 키).
     *
     * @param planCode 플랜 코드 (monthly_basic, yearly_premium 등)
     * @param plan     플랜 표시명 (Frontend `nameKey="plan"` 호환)
     * @param count    해당 플랜 활성 구독 수
     * @param ratio    전체 활성 구독 대비 비율 (0.0~1.0, Frontend `props.payload.ratio` 호환)
     */
    public record PlanDistribution(
            String planCode,
            String plan,
            long count,
            double ratio
    ) {}

    /**
     * 플랜별 MRR 기여도.
     *
     * @param planCode 플랜 코드
     * @param plan     플랜 표시명
     * @param mrr      월 환산 매출 (연간 플랜은 price/12)
     * @param count    해당 플랜 활성 구독 수
     * @param ratio    MRR 내 비율 (0.0~1.0)
     */
    public record PlanMrrItem(
            String planCode,
            String plan,
            long mrr,
            long count,
            double ratio
    ) {}

    // ══════════════════════════════════════════════
    // 9. 포인트 경제 분석
    // ══════════════════════════════════════════════

    /**
     * 포인트 경제 개요 응답.
     *
     * <p>포인트 유통 현황의 핵심 지표 6개를 제공한다.</p>
     *
     * @param totalIssued      총 발행 포인트 (earn+bonus 합계)
     * @param totalSpent       총 소비 포인트 (spend 합계, 절대값)
     * @param totalBalance     전체 사용자 잔액 합계
     * @param activeUsers      포인트 보유 사용자 수 (balance > 0)
     * @param todayIssued      오늘 발행 포인트
     * @param todaySpent       오늘 소비 포인트 (절대값)
     */
    public record PointEconomyOverviewResponse(
            long totalIssued,
            long totalSpent,
            long totalBalance,
            long activeUsers,
            long todayIssued,
            long todaySpent
    ) {}

    /**
     * 포인트 유형별 분포 응답.
     *
     * @param distribution 유형(earn/spend/bonus/expire/refund/revoke/admin_grant/admin_revoke)별
     *                     건수 + 포인트 합계
     */
    public record PointTypeDistributionResponse(
            List<PointTypeItem> distribution
    ) {}

    /**
     * 포인트 유형별 분포 단건.
     *
     * @param pointType  유형 코드 (earn/spend/bonus/expire/refund/revoke/admin_grant/admin_revoke).
     *                   admin_grant/admin_revoke 는 2026-04-28 도입된 운영 조정 전용 코드로,
     *                   KPI(총발행/총소비)에서는 자동 제외되며 분포 차트만 별도 카테고리로 표시된다.
     * @param label      한국어 라벨 (예: "활동 리워드", "운영 지급")
     * @param count      해당 유형 거래 건수
     * @param totalAmount 해당 유형 포인트 합계 (절대값)
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record PointTypeItem(
            String pointType,
            String label,
            long count,
            long totalAmount,
            double percentage
    ) {}

    /**
     * 등급별 사용자 분포 응답.
     *
     * @param grades 등급별 사용자 수 + 비율
     */
    public record GradeDistributionResponse(
            List<GradeItem> grades
    ) {}

    /**
     * 등급별 사용자 분포 단건.
     *
     * @param gradeCode  등급 코드 (NORMAL/BRONZE/SILVER/GOLD/PLATINUM/DIAMOND)
     * @param gradeName  한국어명 (알갱이/강냉이/팝콘/카라멜팝콘/몽글팝콘/몽아일체)
     * @param count      해당 등급 사용자 수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record GradeItem(
            String gradeCode,
            String gradeName,
            long count,
            double percentage
    ) {}

    /**
     * 일별 포인트 발행/소비 추이 응답.
     *
     * @param trends 일별 발행/소비 추이 리스트
     */
    public record PointTrendsResponse(
            List<DailyPointTrend> trends
    ) {}

    /**
     * 일별 포인트 발행/소비 추이 단건.
     *
     * @param date    날짜 문자열 (yyyy-MM-dd)
     * @param issued  해당일 발행 포인트 합계
     * @param spent   해당일 소비 포인트 합계 (절대값)
     * @param netFlow 순유입량 (issued - spent)
     */
    public record DailyPointTrend(
            String date,
            long issued,
            long spent,
            long netFlow
    ) {}

    // ══════════════════════════════════════════════
    // 10. AI 서비스 분석
    // ══════════════════════════════════════════════

    /**
     * AI 서비스 개요 응답.
     *
     * <p>AI 채팅 서비스의 핵심 KPI 6개를 제공한다.</p>
     *
     * @param totalSessions      전체 세션 수
     * @param totalTurns         전체 대화 턴 수
     * @param avgTurnsPerSession 세션당 평균 턴 수
     * @param todaySessions      오늘 세션 수
     * @param todayTurns         오늘 턴 수
     * @param totalRecommendedMovies 추천된 영화 총 수
     */
    public record AiServiceOverviewResponse(
            long totalSessions,
            long totalTurns,
            double avgTurnsPerSession,
            long todaySessions,
            long todayTurns,
            long totalRecommendedMovies
    ) {}

    /**
     * AI 세션 일별 추이 응답.
     *
     * @param trends 일별 세션/턴 추이 리스트
     */
    public record AiSessionTrendsResponse(
            List<DailyAiTrend> trends
    ) {}

    /**
     * AI 세션 일별 추이 단건.
     *
     * @param date     날짜 문자열 (yyyy-MM-dd)
     * @param sessions 해당일 세션 수
     * @param turns    해당일 총 턴 수
     */
    public record DailyAiTrend(
            String date,
            long sessions,
            long turns
    ) {}

    /**
     * AI 의도(Intent) 분포 응답.
     *
     * @param intents 의도별 건수 + 비율
     */
    public record AiIntentDistributionResponse(
            List<IntentItem> intents
    ) {}

    /**
     * 의도 분포 단건.
     *
     * @param intent     의도 코드 (recommend/search/general/relation/info/theater/booking 등)
     * @param label      한국어 라벨
     * @param count      발생 건수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record IntentItem(
            String intent,
            String label,
            long count,
            double percentage
    ) {}

    /**
     * AI 쿼터 소진 현황 응답.
     *
     * @param totalQuotaUsers      쿼터 레코드 보유 사용자 수
     * @param avgDailyUsage        일일 평균 AI 사용 횟수
     * @param avgMonthlyUsage      월간 평균 쿠폰 사용 횟수
     * @param totalPurchasedTokens 전체 구매 이용권 보유량
     * @param exhaustedUsers       일일 무료 한도 소진 사용자 수
     */
    public record AiQuotaStatsResponse(
            long totalQuotaUsers,
            double avgDailyUsage,
            double avgMonthlyUsage,
            long totalPurchasedTokens,
            long exhaustedUsers
    ) {}

    // ══════════════════════════════════════════════
    // 11. 커뮤니티 분석
    // ══════════════════════════════════════════════

    /**
     * 커뮤니티 개요 응답.
     *
     * <p>커뮤니티 활동의 핵심 KPI 6개를 제공한다.</p>
     *
     * @param totalPosts       전체 게시글 수 (PUBLISHED)
     * @param totalComments    전체 댓글 수
     * @param totalReports     전체 신고 수
     * @param pendingReports   미처리 신고 수
     * @param todayPosts       오늘 게시글 수
     * @param todayComments    오늘 댓글 수
     */
    public record CommunityOverviewResponse(
            long totalPosts,
            long totalComments,
            long totalReports,
            long pendingReports,
            long todayPosts,
            long todayComments
    ) {}

    /**
     * 커뮤니티 일별 추이 응답.
     *
     * @param trends 일별 게시글/댓글 추이 리스트
     */
    public record CommunityTrendsResponse(
            List<DailyCommunityTrend> trends
    ) {}

    /**
     * 커뮤니티 일별 추이 단건.
     *
     * @param date     날짜 문자열 (yyyy-MM-dd)
     * @param posts    해당일 게시글 수
     * @param comments 해당일 댓글 수
     * @param reports  해당일 신고 수
     */
    public record DailyCommunityTrend(
            String date,
            long posts,
            long comments,
            long reports
    ) {}

    /**
     * 게시글 카테고리별 분포 응답.
     *
     * @param categories 카테고리별 게시글 수 + 비율
     */
    public record PostCategoryDistributionResponse(
            List<CategoryItem> categories
    ) {}

    /**
     * 카테고리별 게시글 분포 단건.
     *
     * @param category   카테고리 코드 (FREE/DISCUSSION/RECOMMENDATION/NEWS/PLAYLIST_SHARE)
     * @param label      한국어 라벨
     * @param count      해당 카테고리 게시글 수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record CategoryItem(
            String category,
            String label,
            long count,
            double percentage
    ) {}

    /**
     * 신고/독성 분석 응답.
     *
     * @param totalReports      전체 신고 건수
     * @param resolvedRate      처리 완료율 (0.0~100.0)
     * @param avgToxicityScore  평균 독성 점수 (0.0~1.0)
     * @param statusDistribution 신고 상태별 분포
     * @param toxicityBuckets   독성 점수 구간별 분포 (0~0.2/0.2~0.4/0.4~0.6/0.6~0.8/0.8~1.0)
     */
    public record ReportAnalysisResponse(
            long totalReports,
            double resolvedRate,
            double avgToxicityScore,
            List<ReportStatusItem> statusDistribution,
            List<ToxicityBucket> toxicityBuckets
    ) {}

    /**
     * 신고 상태별 분포 단건.
     *
     * @param status     상태 코드 (pending/reviewed/resolved/dismissed)
     * @param label      한국어 라벨
     * @param count      해당 상태 건수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record ReportStatusItem(
            String status,
            String label,
            long count,
            double percentage
    ) {}

    /**
     * 독성 점수 구간 분포 단건.
     *
     * @param range      구간 라벨 (예: "0.0~0.2")
     * @param count      해당 구간 건수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record ToxicityBucket(
            String range,
            long count,
            double percentage
    ) {}

    // ══════════════════════════════════════════════
    // 12. 사용자 참여도 분석
    // ══════════════════════════════════════════════

    /**
     * 사용자 참여도 개요 KPI 응답.
     *
     * <p>출석 체크 현황, 활동 진행 사용자 수, 위시리스트 수,
     * 평균 연속 출석일 5개 지표를 제공한다.</p>
     *
     * @param totalAttendance      전체 출석 체크 레코드 수 (중복 포함 — 날짜별 1건)
     * @param todayAttendance      오늘 출석 체크 수
     * @param totalActivityUsers   활동 진행 레코드 보유 사용자 수 (user_activity_progress 행 수)
     * @param totalWishlists       전체 위시리스트 추가 수
     * @param avgStreakDays        전체 사용자의 현재 연속 출석일 평균 (소수점 1자리)
     */
    public record EngagementOverviewResponse(
            long totalAttendance,
            long todayAttendance,
            long totalActivityUsers,
            long totalWishlists,
            double avgStreakDays
    ) {}

    /**
     * 활동별 참여 현황 응답.
     *
     * @param activities 활동 유형별 참여자 수 + 총 활동 횟수 리스트
     */
    public record ActivityDistributionResponse(
            List<ActivityItem> activities
    ) {}

    /**
     * 활동별 참여 현황 단건.
     *
     * @param actionType   활동 유형 코드 (review_write/movie_like/attendance 등)
     * @param label        한국어 라벨
     * @param userCount    해당 활동에 참여한 고유 사용자 수
     * @param totalActions 해당 활동의 전체 누적 횟수 합계
     */
    public record ActivityItem(
            String actionType,
            String label,
            long userCount,
            long totalActions
    ) {}

    /**
     * 연속 출석일 구간 분포 응답.
     *
     * @param buckets 구간별 사용자 수 리스트 (1일/2-3일/4-7일/8-14일/15-30일/31일+)
     */
    public record AttendanceStreakDistributionResponse(
            List<StreakBucket> buckets
    ) {}

    /**
     * 연속 출석일 구간 단건.
     *
     * @param range      구간 라벨 (예: "1일", "2-3일", "4-7일")
     * @param userCount  해당 구간에 속하는 사용자 수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record StreakBucket(
            String range,
            long userCount,
            double percentage
    ) {}

    // ══════════════════════════════════════════════
    // 13. 콘텐츠 성과 분석
    // ══════════════════════════════════════════════

    /**
     * 콘텐츠 성과 개요 KPI 응답.
     *
     * <p>도장깨기 코스 진행/완주, 업적 달성, 퀴즈 정답률 지표를 제공한다.</p>
     *
     * @param totalCourseProgress 전체 코스 진행 레코드 수 (시작한 사람 수)
     * @param completedCourses    전체 완주 코스 수
     * @param courseCompletionRate 전체 완주율 (0.0~100.0, completedCourses/totalCourseProgress)
     * @param totalAchievements   전체 업적 달성 수
     * @param totalQuizAttempts   전체 퀴즈 시도 수
     * @param quizCorrectRate     퀴즈 정답률 (0.0~100.0)
     */
    public record ContentPerformanceOverviewResponse(
            long totalCourseProgress,
            long completedCourses,
            double courseCompletionRate,
            long totalAchievements,
            long totalQuizAttempts,
            double quizCorrectRate
    ) {}

    /**
     * 코스별 완주율 응답.
     *
     * @param courses 코스별 완주율 리스트 (완주자 수 내림차순)
     */
    public record CourseCompletionResponse(
            List<CourseCompletionItem> courses
    ) {}

    /**
     * 코스별 완주율 단건.
     *
     * @param courseId          코스 ID (slug 형태, 예: "nolan-filmography")
     * @param totalStarters     코스를 시작한 사용자 수
     * @param completedCount    완주한 사용자 수
     * @param completionRate    완주율 (0.0~100.0)
     * @param avgProgressPercent 평균 진행률 (0.0~100.0, 소수점 1자리)
     */
    public record CourseCompletionItem(
            String courseId,
            long totalStarters,
            long completedCount,
            double completionRate,
            double avgProgressPercent
    ) {}

    /**
     * 리뷰 품질 지표 응답.
     *
     * @param categoryDistribution 리뷰 카테고리별 건수 분포
     * @param ratingDistribution   평점(1~5)별 리뷰 건수 분포
     * @param totalReviews         전체 유효 리뷰 수 (소프트 삭제 제외)
     * @param avgRating            전체 평균 평점 (1.0~5.0)
     */
    public record ReviewQualityResponse(
            List<ReviewCategoryItem> categoryDistribution,
            List<ReviewRatingItem> ratingDistribution,
            long totalReviews,
            double avgRating
    ) {}

    /**
     * 리뷰 카테고리별 분포 단건.
     *
     * @param categoryCode 카테고리 코드 (THEATER_RECEIPT/COURSE/WORLDCUP/WISHLIST/AI_RECOMMEND/PLAYLIST/NONE)
     * @param label        한국어 라벨
     * @param count        해당 카테고리 리뷰 수
     * @param percentage   전체 대비 비율 (0.0~100.0)
     */
    public record ReviewCategoryItem(
            String categoryCode,
            String label,
            long count,
            double percentage
    ) {}

    /**
     * 평점별 리뷰 분포 단건.
     *
     * @param rating     평점 (1~5 정수)
     * @param count      해당 평점 리뷰 수
     * @param percentage 전체 대비 비율 (0.0~100.0)
     */
    public record ReviewRatingItem(
            int rating,
            long count,
            double percentage
    ) {}

    // ══════════════════════════════════════════════
    // 14. 전환 퍼널 분석
    // ══════════════════════════════════════════════

    /**
     * 전환 퍼널 응답.
     *
     * <p>5단계 퍼널을 통해 신규 가입자가 결제까지 이어지는 전환율을 측정한다.
     * 각 단계는 이전 단계 대비 전환율과 1단계(가입) 대비 전환율을 모두 제공한다.</p>
     *
     * <p>v3.6 (2026-04-28): "구독 전환" 단계 제거 — 결제(payment_orders COMPLETED)
     * 안에 구독 결제가 포함되어 있어 단계가 사실상 중복이었다. 5단계로 단순화.</p>
     *
     * @param period                 분석 기간 문자열 (예: "30d")
     * @param steps                  5단계 퍼널 리스트
     * @param totalConversionRate    가입 → 결제 전체 전환율 (0.0~100.0, 마지막 단계 conversionFromTop 와 동일)
     */
    public record FunnelConversionResponse(
            String period,
            List<FunnelStep> steps,
            double totalConversionRate
    ) {}

    /**
     * 퍼널 단계 단건.
     *
     * @param step              단계 번호 (1~5)
     * @param label             단계 라벨 (예: "신규 가입", "AI 채팅 사용")
     * @param count             해당 단계 달성 사용자 수 (고유)
     * @param conversionFromPrev 전 단계 대비 전환율 (0.0~100.0, 단계 1은 100.0)
     * @param conversionFromTop  1단계(가입) 대비 전환율 (0.0~100.0)
     */
    public record FunnelStep(
            int step,
            String label,
            long count,
            double conversionFromPrev,
            double conversionFromTop
    ) {}

    // ══════════════════════════════════════════════
    // 15. 이탈 위험 분석
    // ══════════════════════════════════════════════

    /**
     * 이탈 위험 개요 KPI 응답.
     *
     * <p>전체 사용자(최대 1000명 샘플)를 위험도 점수(0~75)로 분류한 결과를 제공한다.</p>
     *
     * <p>v3.6 (2026-04-28) 변경:
     * <ul>
     *   <li>"구독 미보유" 점수 제거 — 무료 사용자가 대부분이라 변별력 없음</li>
     *   <li>최대 점수: 95 → 75 (40 + 15 + 20)</li>
     *   <li>구간 재조정: 안전 0~14 / 낮음 15~29 / 중간 30~49 / 높음 50~75</li>
     * </ul>
     * </p>
     *
     * <p>점수 계산 기준:
     * <ul>
     *   <li>로그인 공백 7일+: +10점, 14일+: +25점, 30일+: +40점 (가장 높은 구간만 적용)</li>
     *   <li>포인트 잔액 0 + 가입 7일 이상: +15점</li>
     *   <li>AI 세션 없음 (가입 14일 이상): +20점</li>
     * </ul>
     * </p>
     *
     * @param noRisk     안전 (0~14점) 사용자 수
     * @param lowRisk    낮은 위험 (15~29점) 사용자 수
     * @param mediumRisk 중간 위험 (30~49점) 사용자 수
     * @param highRisk   높은 위험 (50~75점) 사용자 수
     * @param totalAnalyzed 분석된 전체 사용자 수 (최대 1000명 샘플)
     */
    public record ChurnRiskOverviewResponse(
            long noRisk,
            long lowRisk,
            long mediumRisk,
            long highRisk,
            long totalAnalyzed
    ) {}

    /**
     * 이탈 위험 신호 집계 응답.
     *
     * <p>구체적인 이탈 위험 신호별 사용자 수를 제공하여
     * 운영팀이 타깃 리텐션 캠페인을 기획할 수 있도록 한다.</p>
     *
     * <p>v3.6 (2026-04-28) 변경:
     * <ul>
     *   <li>"구독 만료 후 미갱신" 신호 제거 — 무료 사용자가 대부분이라 변별력 없음</li>
     *   <li>"AI 채팅 미사용 (가입 14일 이상)" 신호 추가 — 점수 산정 기준과 정합</li>
     * </ul>
     * </p>
     *
     * @param inactive7days       7일+ 미로그인 사용자 수
     * @param inactive14days      14일+ 미로그인 사용자 수
     * @param inactive30days      30일+ 미로그인 사용자 수
     * @param zeroPointUsers      포인트 잔액 0 사용자 수
     * @param noAiUsageOver14days AI 채팅 사용 이력이 없는 가입 14일 이상 사용자 수
     */
    public record ChurnRiskSignalsResponse(
            long inactive7days,
            long inactive14days,
            long inactive30days,
            long zeroPointUsers,
            long noAiUsageOver14days
    ) {}
}
