package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.StatsDto.*;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.repository.PostRepository;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.review.repository.ReviewRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 통계/분석 서비스.
 *
 * <p>정한나 담당 영역: 서비스 KPI, 추천/검색 분석, 사용자 행동, 매출 통계.
 * 추천 로그·검색 로그 테이블이 아직 구현되지 않은 항목은 mock 데이터를 반환하며,
 * 실제 테이블 구현 후 교체한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ReviewRepository reviewRepository;
    private final PostRepository postRepository;

    /* ── 날짜 포맷터 ── */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ──────────────────────────────────────────────
    // 1. 서비스 개요 KPI
    // ──────────────────────────────────────────────

    /**
     * 서비스 전체 KPI 개요를 반환한다.
     *
     * @param period 기간 문자열 (예: "7d", "30d", "90d") — 신규 가입 집계 범위
     * @return KPI 카드 6항목 (DAU, MAU, 신규가입, 리뷰수, 평균평점, 게시글수)
     */
    public OverviewResponse getOverview(String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        /* DAU: 오늘 로그인한 사용자 수 */
        long dau = userRepository.countByLastLoginAtAfter(today.atStartOfDay());

        /* MAU: 최근 30일 내 로그인 */
        long mau = userRepository.countByLastLoginAtAfter(now.minusDays(30));

        /* 신규 가입: period에 해당하는 기간 내 */
        int days = parsePeriodDays(period);
        long newUsersWeek = userRepository.countByCreatedAtAfter(now.minusDays(days));

        /* 전체 리뷰 수 */
        long totalReviews = reviewRepository.count();

        /* 평균 평점 */
        Double avgRatingRaw = reviewRepository.findAverageRating();
        double avgRating = avgRatingRaw != null ? Math.round(avgRatingRaw * 100.0) / 100.0 : 0.0;

        /* 전체 게시글 수 (PUBLISHED) */
        long totalPosts = postRepository.countByStatus(PostStatus.PUBLISHED);

        log.debug("[admin-stats] 서비스 개요 조회 — DAU={}, MAU={}, 신규={}({}일)", dau, mau, newUsersWeek, days);
        return new OverviewResponse(dau, mau, newUsersWeek, totalReviews, avgRating, totalPosts);
    }

    // ──────────────────────────────────────────────
    // 2. 일별 추이 차트
    // ──────────────────────────────────────────────

    /**
     * 일별 추이 데이터를 반환한다.
     *
     * @param period 기간 문자열 ("7d", "30d" 등)
     * @return 날짜별 추이 리스트
     */
    public TrendsResponse getTrends(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now();
        List<DailyTrend> trends = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            long dau = userRepository.countByLastLoginAtBetween(dayStart, dayEnd);
            long newUsers = userRepository.countByCreatedAtBetween(dayStart, dayEnd);
            long reviews = reviewRepository.countByCreatedAtBetween(dayStart, dayEnd);
            long posts = postRepository.countByStatusAndCreatedAtBetween(PostStatus.PUBLISHED, dayStart, dayEnd);

            trends.add(new DailyTrend(date.format(DATE_FMT), dau, newUsers, reviews, posts));
        }

        log.debug("[admin-stats] 추이 차트 조회 — {}일", days);
        return new TrendsResponse(trends);
    }

    // ──────────────────────────────────────────────
    // 3. 추천 성능 지표 (mock)
    // ──────────────────────────────────────────────

    /**
     * 추천 시스템 성능 지표를 반환한다.
     * <p>TODO: recommendation_logs 테이블 구현 후 실제 데이터로 교체</p>
     */
    public RecommendationResponse getRecommendationPerformance(String period) {
        log.info("[admin-stats] 추천 성능 — recommendation_logs 테이블 미존재, mock 데이터 반환");
        return new RecommendationResponse(0.342, 0.785, 15420L);
    }

    /**
     * 추천 장르 분포를 반환한다.
     * <p>TODO: recommendation_logs 테이블 구현 후 실제 데이터로 교체</p>
     */
    public DistributionResponse getRecommendationDistribution() {
        log.info("[admin-stats] 추천 분포 — mock 데이터 반환");
        List<GenreDistribution> genres = List.of(
                new GenreDistribution("액션", 3245, 25.3),
                new GenreDistribution("로맨스", 2180, 17.0),
                new GenreDistribution("코미디", 1890, 14.7),
                new GenreDistribution("드라마", 1720, 13.4),
                new GenreDistribution("SF", 1350, 10.5),
                new GenreDistribution("스릴러", 1200, 9.4),
                new GenreDistribution("공포", 680, 5.3),
                new GenreDistribution("애니메이션", 560, 4.4)
        );
        return new DistributionResponse(genres);
    }

    /**
     * 추천 로그 목록을 반환한다.
     * <p>TODO: recommendation_logs 테이블 구현 후 실제 데이터로 교체</p>
     */
    public List<RecommendationLogResponse> getRecommendationLogs(int page, int size) {
        log.info("[admin-stats] 추천 로그 — recommendation_logs 테이블 미존재, 빈 목록 반환");
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // 4. 검색 분석 (mock)
    // ──────────────────────────────────────────────

    /**
     * 인기 검색어를 반환한다.
     * <p>TODO: search_histories 테이블 구현 후 실제 데이터로 교체</p>
     */
    public PopularKeywordsResponse getPopularKeywords(String period, int limit) {
        log.info("[admin-stats] 인기 검색어 — search_histories 테이블 미존재, mock 데이터 반환");
        List<KeywordItem> keywords = List.of(
                new KeywordItem("기생충", 1520, 0.72),
                new KeywordItem("범죄도시", 1340, 0.68),
                new KeywordItem("듄", 1180, 0.65),
                new KeywordItem("올드보이", 980, 0.71),
                new KeywordItem("인터스텔라", 920, 0.82),
                new KeywordItem("부산행", 850, 0.74),
                new KeywordItem("어벤져스", 780, 0.61),
                new KeywordItem("라라랜드", 720, 0.69),
                new KeywordItem("타짜", 680, 0.58),
                new KeywordItem("헤어질 결심", 620, 0.77)
        );
        return new PopularKeywordsResponse(keywords.stream().limit(limit).toList());
    }

    /**
     * 검색 품질 지표를 반환한다.
     * <p>TODO: search_histories 테이블 구현 후 실제 데이터로 교체</p>
     */
    public SearchQualityResponse getSearchQuality(String period) {
        log.info("[admin-stats] 검색 품질 — search_histories 테이블 미존재, mock 데이터 반환");
        return new SearchQualityResponse(0.876, 24560L, 3050L);
    }

    // ──────────────────────────────────────────────
    // 5. 사용자 행동 분석 (mock — watch_history에 장르 없음)
    // ──────────────────────────────────────────────

    /**
     * 사용자 행동 패턴을 반환한다.
     * <p>TODO: watch_history에 장르 컬럼 추가 또는 movie 테이블 JOIN 후 실데이터 교체</p>
     */
    public BehaviorResponse getUserBehavior(String period) {
        log.info("[admin-stats] 사용자 행동 — mock 데이터 반환 (watch_history 장르 매핑 필요)");

        List<GenrePreference> preferences = List.of(
                new GenrePreference("드라마", 4520, 28.5),
                new GenrePreference("액션", 3210, 20.2),
                new GenrePreference("코미디", 2450, 15.4),
                new GenrePreference("로맨스", 1980, 12.5),
                new GenrePreference("스릴러", 1650, 10.4),
                new GenrePreference("SF", 1120, 7.1),
                new GenrePreference("공포", 580, 3.7),
                new GenrePreference("다큐멘터리", 370, 2.3)
        );

        /* 시간대별 활동량 (24시간) — mock */
        List<HourlyActivity> hourly = new ArrayList<>();
        int[] mockCounts = {120, 80, 45, 30, 25, 35, 90, 210, 380, 520, 610, 580,
                490, 550, 620, 700, 810, 950, 1020, 980, 870, 650, 420, 250};
        for (int h = 0; h < 24; h++) {
            hourly.add(new HourlyActivity(h, mockCounts[h]));
        }

        return new BehaviorResponse(preferences, hourly);
    }

    // ──────────────────────────────────────────────
    // 6. 코호트 리텐션
    // ──────────────────────────────────────────────

    /**
     * 주간 코호트 리텐션 데이터를 반환한다.
     *
     * <p>User.createdAt으로 주간 코호트를 생성하고, lastLoginAt으로 재방문을 판단한다.</p>
     *
     * @param weeks 분석할 주간 수 (기본 4)
     * @return 코호트별 리텐션율 히트맵 데이터
     */
    public RetentionResponse getRetention(int weeks) {
        LocalDate today = LocalDate.now();
        List<CohortRow> cohorts = new ArrayList<>();

        for (int w = weeks - 1; w >= 0; w--) {
            /* 코호트 기준 주간의 시작/끝 */
            LocalDate cohortStart = today.minusWeeks(w + 1);
            LocalDate cohortEnd = today.minusWeeks(w);
            LocalDateTime cohortStartDt = cohortStart.atStartOfDay();
            LocalDateTime cohortEndDt = cohortEnd.atStartOfDay();

            /* 해당 주간 가입자 목록 */
            List<User> cohortUsers = userRepository.findByCreatedAtBetween(cohortStartDt, cohortEndDt);
            long cohortSize = cohortUsers.size();

            String weekLabel = cohortStart.getYear() + "-W"
                    + String.format("%02d", cohortStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));

            if (cohortSize == 0) {
                cohorts.add(new CohortRow(weekLabel, 0, Collections.emptyList()));
                continue;
            }

            /* 코호트 사용자 ID 목록 */
            List<String> userIds = cohortUsers.stream()
                    .map(User::getUserId)
                    .collect(Collectors.toList());

            /* 각 후속 주차별 리텐션율 계산 */
            List<Double> retentionRates = new ArrayList<>();
            for (int rw = 1; rw <= w + 1 && rw <= 4; rw++) {
                LocalDateTime rwStart = cohortEndDt.plusWeeks(rw - 1);
                LocalDateTime rwEnd = cohortEndDt.plusWeeks(rw);
                long retained = userRepository.countCohortRetention(userIds, rwStart, rwEnd);
                double rate = Math.round((double) retained / cohortSize * 1000.0) / 10.0;
                retentionRates.add(rate);
            }

            cohorts.add(new CohortRow(weekLabel, cohortSize, retentionRates));
        }

        log.debug("[admin-stats] 리텐션 조회 — {}주 코호트", weeks);
        return new RetentionResponse(cohorts);
    }

    // ──────────────────────────────────────────────
    // 7. 매출 통계
    // ──────────────────────────────────────────────

    /**
     * 매출 현황을 반환한다.
     *
     * @param period 기간 문자열 ("7d", "30d" 등)
     * @return 월 매출, MRR, 일별 추이
     */
    public RevenueResponse getRevenue(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        /* 이번 달 누적 매출 */
        Long monthlyRaw = paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, monthStart, now);
        long monthlyRevenue = monthlyRaw != null ? monthlyRaw : 0L;

        /* MRR: 이번 달 1일 이후 COMPLETED 합계 (구독 기반 매출 추정) */
        Long mrrRaw = paymentOrderRepository.sumAmountByStatusAndCreatedAtAfter(
                PaymentOrder.OrderStatus.COMPLETED, monthStart);
        long mrr = mrrRaw != null ? mrrRaw : 0L;

        /* 일별 매출 추이 */
        List<DailyRevenue> dailyRevenue = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            Long amountRaw = paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                    PaymentOrder.OrderStatus.COMPLETED, dayStart, dayEnd);
            long amount = amountRaw != null ? amountRaw : 0L;
            dailyRevenue.add(new DailyRevenue(date.format(DATE_FMT), amount));
        }

        log.debug("[admin-stats] 매출 조회 — 월매출={}, MRR={}, 기간={}일", monthlyRevenue, mrr, days);
        return new RevenueResponse(monthlyRevenue, mrr, dailyRevenue);
    }

    // ──────────────────────────────────────────────
    // 8. 구독 통계
    // ──────────────────────────────────────────────

    /**
     * 구독 현황 통계를 반환한다.
     *
     * @return 활성 구독 수, 이탈률, 플랜별 분포
     */
    public SubscriptionStatsResponse getSubscriptionStats() {
        /* 활성 구독 수 */
        long totalActive = userSubscriptionRepository.countByStatus(UserSubscription.Status.ACTIVE);

        /* 이탈률: 최근 30일 취소/만료 / 이전 활성 — 단순 추정 */
        long cancelled = userSubscriptionRepository.countByStatus(UserSubscription.Status.CANCELLED);
        long expired = userSubscriptionRepository.countByStatus(UserSubscription.Status.EXPIRED);
        long totalEver = totalActive + cancelled + expired;
        double churnRate = totalEver > 0
                ? Math.round((double) (cancelled + expired) / totalEver * 1000.0) / 10.0
                : 0.0;

        /* 플랜별 분포 — 현재 구독 플랜 테이블에서 조회 */
        /* TODO: UserSubscription과 SubscriptionPlan JOIN으로 그룹별 카운트 필요 */
        /* 간소화: 전체 활성을 기본 플랜으로 반환 */
        List<PlanDistribution> plans = new ArrayList<>();
        if (totalActive > 0) {
            plans.add(new PlanDistribution("basic_monthly", "베이직 월간", totalActive, 100.0));
        }

        log.debug("[admin-stats] 구독 통계 — 활성={}, 이탈률={}%", totalActive, churnRate);
        return new SubscriptionStatsResponse(totalActive, churnRate, plans);
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────

    /**
     * 기간 문자열을 일 수로 파싱한다.
     *
     * @param period "7d", "30d", "90d" 형식
     * @return 일 수 (파싱 실패 시 기본 7)
     */
    private int parsePeriodDays(String period) {
        if (period == null || period.isBlank()) return 7;
        try {
            return Integer.parseInt(period.replace("d", ""));
        } catch (NumberFormatException e) {
            log.warn("[admin-stats] period 파싱 실패: '{}', 기본값 7일 사용", period);
            return 7;
        }
    }
}
