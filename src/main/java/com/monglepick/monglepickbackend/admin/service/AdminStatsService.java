package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.StatsDto.*;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact;
import com.monglepick.monglepickbackend.domain.recommendation.entity.UserBehaviorProfile;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.UserBehaviorProfileRepository;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.search.repository.SearchHistoryRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 사용자 통계 — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    /** 리뷰 통계 — MyBatis Mapper (§15) */
    private final ReviewMapper reviewMapper;
    /** 게시글 통계 — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final PostMapper postMapper;
    /* 추천 임팩트 리포지토리 — CTR/위시리스트율 집계 */
    private final RecommendationImpactRepository recommendationImpactRepository;
    /* 검색 이력 리포지토리 — 인기 검색어/검색 품질 집계 */
    private final SearchHistoryRepository searchHistoryRepository;
    /* Phase 4: Mock 제거 — Movie genres 파싱 (장르 분포 집계용) */
    private final MovieRepository movieRepository;
    /* Phase 4: Mock 제거 — UserBehaviorProfile 의 genreAffinity JSON 집계 */
    private final UserBehaviorProfileRepository userBehaviorProfileRepository;
    /* Phase 4: Mock 제거 — Movie.genres / UserBehaviorProfile.genreAffinity JSON 파싱 */
    private static final ObjectMapper STATS_OBJECT_MAPPER = new ObjectMapper();

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
        long dau = userMapper.countByLastLoginAtAfter(today.atStartOfDay());

        /* MAU: 최근 30일 내 로그인 */
        long mau = userMapper.countByLastLoginAtAfter(now.minusDays(30));

        /* 신규 가입: period에 해당하는 기간 내 */
        int days = parsePeriodDays(period);
        long newUsersWeek = userMapper.countByCreatedAtAfter(now.minusDays(days));

        /* 전체 리뷰 수 */
        long totalReviews = reviewMapper.count();

        /* 평균 평점 */
        Double avgRatingRaw = reviewMapper.findAverageRating();
        double avgRating = avgRatingRaw != null ? Math.round(avgRatingRaw * 100.0) / 100.0 : 0.0;

        /* 전체 게시글 수 (PUBLISHED) */
        long totalPosts = postMapper.countByStatus(PostStatus.PUBLISHED.name());

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

            long dau = userMapper.countByLastLoginAtBetween(dayStart, dayEnd);
            long newUsers = userMapper.countByCreatedAtBetween(dayStart, dayEnd);
            long reviews = reviewMapper.countByCreatedAtBetween(dayStart, dayEnd);
            long posts = postMapper.countByStatusAndCreatedAtBetween(PostStatus.PUBLISHED.name(), dayStart, dayEnd);

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
     *
     * <p>recommendation_impact 테이블의 실제 데이터를 집계하여
     * CTR(클릭률)과 위시리스트 전환율을 계산한다.</p>
     *
     * <ul>
     *   <li>CTR = clicked=true 수 / 전체 임팩트 수</li>
     *   <li>위시리스트율 = wishlisted=true 수 / 전체 임팩트 수</li>
     *   <li>totalCount = 전체 추천 임팩트 레코드 수</li>
     * </ul>
     *
     * @param period 기간 문자열 (현재 미사용 — 전체 기간 집계)
     * @return CTR, 위시리스트율, 전체 임팩트 수
     */
    public RecommendationResponse getRecommendationPerformance(String period) {
        // 전체 추천 임팩트 레코드 수
        long totalImpacts = recommendationImpactRepository.count();

        // 클릭된 추천 수
        long clicked = recommendationImpactRepository.countByClickedTrue();

        // CTR: 추천 후 클릭률 (소수점 3자리 반올림)
        double ctr = totalImpacts > 0
                ? Math.round((double) clicked / totalImpacts * 1000.0) / 1000.0
                : 0.0;

        // 위시리스트 추가 수
        long wishlisted = recommendationImpactRepository.countByWishlistedTrue();

        // 위시리스트 전환율 (소수점 3자리 반올림)
        double wishlistRate = totalImpacts > 0
                ? Math.round((double) wishlisted / totalImpacts * 1000.0) / 1000.0
                : 0.0;

        log.debug("[admin-stats] 추천 성능 — total={}, clicked={}, ctr={}, wishlisted={}, wishlistRate={}",
                totalImpacts, clicked, ctr, wishlisted, wishlistRate);
        return new RecommendationResponse(ctr, wishlistRate, totalImpacts);
    }

    /**
     * 추천 장르 분포를 반환한다.
     *
     * <p>Phase 4 (Mock 제거): Movie 카탈로그의 상위 1000건을 ID 오름차순으로 로드하여
     * {@code movies.genres} JSON 배열을 Java 에서 파싱·집계한 뒤 상위 8개 장르를 반환한다.
     * 추천 임팩트 테이블이 충분히 누적되기 전이라도 영화 카탈로그 자체의 장르 분포를 반영하므로
     * 관리자 화면의 "추천될 가능성이 있는 영화의 장르 분포"로 해석할 수 있다.</p>
     *
     * <p>향후 {@code recommendation_logs} 테이블이 구현되면 실제 추천 빈도 기반으로 교체한다.</p>
     */
    public DistributionResponse getRecommendationDistribution() {
        log.debug("[admin-stats] 추천 분포 집계 시작 — Movie 카탈로그 기반");

        // 1. Movie 카탈로그에서 상위 1000건 로드 (성능 보호 — 전체 카탈로그가 50만+ 건일 수 있음)
        Page<Movie> moviePage = movieRepository.findAll(PageRequest.of(0, 1000));
        List<Movie> movies = moviePage.getContent();

        // 2. 장르별 카운트 집계 (LinkedHashMap 사용 — 첫 등장 순서 유지)
        Map<String, Long> genreCounts = new LinkedHashMap<>();
        long totalGenreOccurrences = 0L;

        for (Movie movie : movies) {
            String genresJson = movie.getGenres();
            if (genresJson == null || genresJson.isBlank()) {
                continue;
            }
            // JSON 배열 파싱 — 형식: ["액션", "SF"] 또는 ["Action"] 등
            try {
                JsonNode root = STATS_OBJECT_MAPPER.readTree(genresJson);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        String genre = node.asText().trim();
                        if (genre.isEmpty()) {
                            continue;
                        }
                        genreCounts.merge(genre, 1L, Long::sum);
                        totalGenreOccurrences++;
                    }
                }
            } catch (Exception e) {
                // JSON 파싱 실패 — 해당 영화는 건너뛴다 (로그 노이즈 방지를 위해 trace 레벨)
                log.trace("[admin-stats] 장르 JSON 파싱 실패: movieId={}, genres={}",
                        movie.getMovieId(), genresJson);
            }
        }

        // 3. 카운트 내림차순 정렬 + 상위 8개 추출
        final long total = totalGenreOccurrences;
        List<GenreDistribution> top8 = genreCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> {
                    long count = entry.getValue();
                    double percentage = total > 0
                            ? Math.round((double) count / total * 1000.0) / 10.0  // 소수점 1자리
                            : 0.0;
                    return new GenreDistribution(entry.getKey(), count, percentage);
                })
                .toList();

        log.debug("[admin-stats] 추천 분포 집계 완료 — 총 {} 영화, {} 장르 어카운트, top8 size={}",
                movies.size(), totalGenreOccurrences, top8.size());
        return new DistributionResponse(top8);
    }

    /**
     * 추천 로그 목록을 반환한다.
     *
     * <p>Phase 4 (Mock 제거): {@code recommendation_impact} 테이블에서 최근 임팩트를
     * 페이징 조회하여 RecommendationLogResponse 로 매핑한다. 점수(score)는 별도 저장 컬럼이 없으므로
     * 추천 위치(recommendationPosition)를 0~1 정규화한 값으로 대체한다 (1순위=1.0, 10순위=0.1 등).
     * 피드백은 wishlisted/watched/clicked 플래그로 파생한다.</p>
     */
    public List<RecommendationLogResponse> getRecommendationLogs(int page, int size) {
        log.debug("[admin-stats] 추천 로그 조회 — page={}, size={}", page, size);

        // 1. RecommendationImpact 페이징 조회 (최신순)
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<RecommendationImpact> impactPage = recommendationImpactRepository.findAll(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        // 2. RecommendationLogResponse 매핑
        return impactPage.getContent().stream()
                .map(impact -> {
                    // 점수: 추천 위치(position) 기반 역수 정규화 (1순위=1.0, 10순위=0.1)
                    // position 이 null 이면 0.5 fallback
                    double score;
                    Integer position = impact.getRecommendationPosition();
                    if (position == null || position <= 0) {
                        score = 0.5;
                    } else {
                        score = Math.max(0.05, 1.0 / position);
                        score = Math.round(score * 100.0) / 100.0;  // 소수점 2자리 반올림
                    }

                    // 피드백 파생: 우선순위 watched > wishlisted > clicked > null
                    String feedback;
                    if (Boolean.TRUE.equals(impact.getWatched())) {
                        feedback = "WATCHED";
                    } else if (Boolean.TRUE.equals(impact.getWishlisted())) {
                        feedback = "LIKE";
                    } else if (Boolean.TRUE.equals(impact.getClicked())) {
                        feedback = "CLICKED";
                    } else {
                        feedback = null;
                    }

                    return new RecommendationLogResponse(
                            impact.getUserId(),
                            impact.getMovieId(),
                            score,
                            feedback,
                            impact.getCreatedAt()
                    );
                })
                .toList();
    }

    // ──────────────────────────────────────────────
    // 4. 검색 분석 (mock)
    // ──────────────────────────────────────────────

    /**
     * 인기 검색어를 반환한다.
     *
     * <p>search_history 테이블의 실제 데이터를 집계한다.
     * (user_id, keyword) UNIQUE 제약으로 인해 COUNT는 해당 키워드를 검색한
     * 고유 사용자 수를 의미한다.</p>
     *
     * <p>ctr 필드는 키워드 검색 후 결과가 1건 이상인 검색 비율을 나타낸다.
     * totalResultCount &gt; 0이면 결과 있음으로 간주하고,
     * 값이 클수록 검색 결과 제공률이 높음을 의미한다.</p>
     *
     * @param period 기간 문자열 (현재 미사용 — 전체 기간 집계)
     * @param limit  반환할 상위 키워드 수
     * @return 인기 검색어 목록 (사용자 수 내림차순)
     */
    public PopularKeywordsResponse getPopularKeywords(String period, int limit) {
        // keyword별 검색 사용자 수 + 총 결과 수를 내림차순으로 limit개 조회
        List<Object[]> rows = searchHistoryRepository.findTopKeywordsByUserCount(
                PageRequest.of(0, limit)
        );

        List<KeywordItem> keywords = rows.stream()
                .map(row -> {
                    // 반환 배열: [keyword(String), userCount(Long), totalResultCount(Long)]
                    String keyword = (String) row[0];
                    long userCount = ((Number) row[1]).longValue();
                    long totalResultCount = ((Number) row[2]).longValue();
                    // ctr: 해당 키워드의 총 결과 수를 사용자 수로 나눈 값 (결과 제공률, 최대 1.0 보정)
                    double ctr = userCount > 0
                            ? Math.min(Math.round((double) totalResultCount / userCount * 100.0) / 100.0, 1.0)
                            : 0.0;
                    return new KeywordItem(keyword, userCount, ctr);
                })
                .toList();

        log.debug("[admin-stats] 인기 검색어 — {}개 조회 완료", keywords.size());
        return new PopularKeywordsResponse(keywords);
    }

    /**
     * 검색 품질 지표를 반환한다.
     *
     * <p>search_history 테이블의 실제 데이터를 기반으로
     * 검색 성공률(result_count &gt; 0인 검색 비율)과
     * 무결과 검색 수를 계산한다.</p>
     *
     * <ul>
     *   <li>successRate = (전체 검색 수 - 무결과 검색 수) / 전체 검색 수</li>
     *   <li>totalSearches = search_history 전체 레코드 수</li>
     *   <li>zeroResultCount = result_count = 0 인 레코드 수</li>
     * </ul>
     *
     * @param period 기간 문자열 (현재 미사용 — 전체 기간 집계)
     * @return 검색 성공률, 전체 검색 수, 무결과 검색 수
     */
    public SearchQualityResponse getSearchQuality(String period) {
        // 전체 검색 이력 수
        long totalSearches = searchHistoryRepository.count();

        // result_count = 0 인 무결과 검색 이력 수
        long zeroResultCount = searchHistoryRepository.countByResultCount(0);

        // 검색 성공률: 결과가 1건 이상인 검색 / 전체 검색 (소수점 3자리 반올림)
        double successRate = totalSearches > 0
                ? Math.round((double) (totalSearches - zeroResultCount) / totalSearches * 1000.0) / 1000.0
                : 0.0;

        log.debug("[admin-stats] 검색 품질 — total={}, zeroResult={}, successRate={}",
                totalSearches, zeroResultCount, successRate);
        return new SearchQualityResponse(successRate, totalSearches, zeroResultCount);
    }

    // ──────────────────────────────────────────────
    // 5. 사용자 행동 분석
    // ──────────────────────────────────────────────

    /**
     * 사용자 행동 패턴을 반환한다.
     *
     * <p>Phase 4 (Mock 제거): {@code user_behavior_profile.genre_affinity} JSON 필드를
     * Java 에서 파싱·집계한 실데이터를 반환한다. BehaviorProfileScheduler 가 매일 03:00 에
     * reviews + event_logs 기반으로 갱신하므로 ("리뷰 작성 = 시청 완료" 단일 진실 원본,
     * watch_history 도메인 폐기 2026-04-08), 이 메서드는 최신 프로필 스냅샷을
     * 사용자별로 합산한 결과를 노출한다.</p>
     *
     * <p>시간대별 활동량은 User.lastLoginAt 의 시간을 추출하여 24시간 분포로 집계한다.
     * (이벤트 로그 기반의 정확한 활동량은 향후 EventLog 스케줄러 통합 시 보강.)</p>
     *
     * @param period 기간 문자열 (현재 미사용 — 전체 프로필 누적)
     */
    public BehaviorResponse getUserBehavior(String period) {
        log.debug("[admin-stats] 사용자 행동 집계 시작 — UserBehaviorProfile + lastLoginAt 기반");

        // ──────────── 1. 장르 선호도: UserBehaviorProfile.genreAffinity 합산 ────────────
        // 최대 1000건 제한으로 메모리 보호 (전체 사용자가 100만+인 경우 대비)
        Page<UserBehaviorProfile> profilePage = userBehaviorProfileRepository.findAll(
                PageRequest.of(0, 1000)
        );
        List<UserBehaviorProfile> profiles = profilePage.getContent();

        // 장르별 누적 가중치 (Double 합산)
        Map<String, Double> genreScoreSum = new HashMap<>();
        double totalScore = 0.0;

        for (UserBehaviorProfile profile : profiles) {
            String affinityJson = profile.getGenreAffinity();
            if (affinityJson == null || affinityJson.isBlank()) {
                continue;
            }
            try {
                JsonNode root = STATS_OBJECT_MAPPER.readTree(affinityJson);
                if (root.isObject()) {
                    var iter = root.fields();
                    while (iter.hasNext()) {
                        var entry = iter.next();
                        String genre = entry.getKey().trim();
                        double score = entry.getValue().asDouble(0.0);
                        if (genre.isEmpty() || score <= 0.0) {
                            continue;
                        }
                        genreScoreSum.merge(genre, score, Double::sum);
                        totalScore += score;
                    }
                }
            } catch (Exception e) {
                // 파싱 실패는 trace 레벨로 로그하여 정상 흐름을 방해하지 않음
                log.trace("[admin-stats] genreAffinity 파싱 실패: userId={}",
                        profile.getUserId());
            }
        }

        // 누적 가중치 내림차순 + 상위 8개 추출
        final double total = totalScore;
        List<GenrePreference> preferences = genreScoreSum.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(8)
                .map(entry -> {
                    long count = Math.round(entry.getValue() * 100);  // 가중치 *100 → "건수" 표현
                    double percentage = total > 0
                            ? Math.round(entry.getValue() / total * 1000.0) / 10.0
                            : 0.0;
                    return new GenrePreference(entry.getKey(), count, percentage);
                })
                .toList();

        // ──────────── 2. 시간대별 활동량: User.lastLoginAt 의 시간 분포 ────────────
        // 24시간 배열 초기화 (0~23시 모두 0)
        long[] hourlyCounts = new long[24];

        // User 전체 페이지에서 lastLoginAt 추출 — 너무 많은 사용자를 한 번에 로드하지 않도록 1000건 제한
        // (MyBatis: Spring Page 대신 List + LIMIT 직접 전달, 설계서 §15)
        List<User> recentUsers = userMapper.findAllLimited(1000);
        for (User user : recentUsers) {
            LocalDateTime lastLogin = user.getLastLoginAt();
            if (lastLogin != null) {
                int hour = lastLogin.getHour();
                if (hour >= 0 && hour < 24) {
                    hourlyCounts[hour]++;
                }
            }
        }

        List<HourlyActivity> hourly = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            hourly.add(new HourlyActivity(h, hourlyCounts[h]));
        }

        log.debug("[admin-stats] 사용자 행동 집계 완료 — 프로필 {} 건, 장르 {} 종, 사용자 {} 명",
                profiles.size(), preferences.size(), recentUsers.size());
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
            List<User> cohortUsers = userMapper.findByCreatedAtBetween(cohortStartDt, cohortEndDt);
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
                long retained = userMapper.countCohortRetention(userIds, rwStart, rwEnd);
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

        /* 플랜별 실제 가입자 수 집계 — UserSubscription JOIN SubscriptionPlan.planCode 그룹화 */
        List<Object[]> planRows = userSubscriptionRepository.countActiveByPlanType();
        List<PlanDistribution> plans = new ArrayList<>();
        for (Object[] row : planRows) {
            // 반환 배열: [planCode(String), count(Long)]
            String planCode = row[0] != null ? String.valueOf(row[0]) : "unknown";
            long count = ((Number) row[1]).longValue();
            // 비율: 소수점 1자리 퍼센트 (예: 65.4%)
            double pct = totalActive > 0
                    ? Math.round((double) count / totalActive * 1000.0) / 10.0
                    : 0.0;
            // planCode → 한국어 표시명 매핑
            String planName = switch (planCode) {
                case "monthly_basic"   -> "베이직 월간";
                case "monthly_premium" -> "프리미엄 월간";
                case "yearly_basic"    -> "베이직 연간";
                case "yearly_premium"  -> "프리미엄 연간";
                default                -> planCode;  // 알 수 없는 플랜은 코드 그대로 노출
            };
            plans.add(new PlanDistribution(planCode, planName, count, pct));
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
