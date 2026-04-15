package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.StatsDto.*;
import com.monglepick.monglepickbackend.admin.repository.AdminChatSessionRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminPointsHistoryRepository;
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
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.entity.UserBehaviorProfile;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.UserBehaviorProfileRepository;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAttendanceRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserActivityProgressRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserAchievementRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizAttemptRepository;
import com.monglepick.monglepickbackend.domain.search.repository.SearchHistoryRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.domain.wishlist.repository.UserWishlistRepository;
// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
 * <p>원 담당(정한나)은 monglepick-recommend(FastAPI) 전담으로 이관되었으며,
 * 본 Backend 11 EP 는 윤형주가 유지보수한다 (CLAUDE.md "관리자 페이지 담당 배정").</p>
 *
 * <h3>구현 범위</h3>
 * <ul>
 *   <li>서비스 KPI (DAU/MAU/신규가입/리뷰/평점/게시글)</li>
 *   <li>일별 추이 차트</li>
 *   <li>추천 성능/분포/로그 — {@code recommendation_impact} 테이블 기반</li>
 *   <li>검색 분석 — {@code search_history} 테이블 기반</li>
 *   <li>사용자 행동(장르 선호) — {@code user_behavior_profile.genre_affinity}</li>
 *   <li>코호트 리텐션 — {@code users.last_login_at} 기반</li>
 *   <li>매출/구독 — {@code payment_orders}, {@code user_subscriptions}</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>Phase 4: Mock 제거 — 모든 지표가 실제 테이블 집계 기반으로 전환됨</li>
 *   <li>2026-04-08: outdated 주석("추천 로그·검색 로그 테이블 미구현") 정리 —
 *       {@code recommendation_impact} / {@code search_history} 엔티티가 이미 존재하고
 *       본 서비스는 해당 리포지토리만 호출한다. 별도의 {@code recommendation_logs} /
 *       {@code search_histories} 테이블은 필요하지 않다 (단일 진실 원본 원칙).</li>
 * </ul>
 *
 * <p>빈 DB 환경에서도 모든 메서드가 0/빈 리스트를 반환하도록 구성되어 있어
 * 별도의 null/mock 분기가 없다.</p>
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

    /**
     * 추천 로그 리포지토리 (2026-04-15 추가).
     * 관리자 "AI 추천 분석" 탭이 기존에 RecommendationImpact 를 조회하여 "클릭/찜/봤어요"
     * 파생 피드백만 보여주던 것을 {@link RecommendationLog} 로 교체하기 위한 의존성.
     */
    private final RecommendationLogRepository recommendationLogRepository;
    /* 검색 이력 리포지토리 — 인기 검색어/검색 품질 집계 */
    private final SearchHistoryRepository searchHistoryRepository;
    /* Phase 4: Mock 제거 — Movie genres 파싱 (장르 분포 집계용) */
    private final MovieRepository movieRepository;
    /* Phase 4: Mock 제거 — UserBehaviorProfile 의 genreAffinity JSON 집계 */
    private final UserBehaviorProfileRepository userBehaviorProfileRepository;
    /* 포인트 경제 통계 — PointsHistory 집계 */
    private final AdminPointsHistoryRepository adminPointsHistoryRepository;
    /* 포인트 경제 통계 — UserPoint 잔액/등급 집계 */
    private final UserPointRepository userPointRepository;
    /* AI 서비스 통계 — ChatSessionArchive 세션/턴/의도 집계 */
    private final AdminChatSessionRepository adminChatSessionRepository;
    /* AI 서비스 통계 — UserAiQuota 쿼터 소진율 집계 */
    private final UserAiQuotaRepository userAiQuotaRepository;

    /* ── 섹션 12: 사용자 참여도 ── */
    /* 출석 체크 — user_attendance 테이블 집계 */
    private final UserAttendanceRepository userAttendanceRepository;
    /* 활동 진행 — user_activity_progress 테이블 집계 */
    private final UserActivityProgressRepository userActivityProgressRepository;
    /* 위시리스트 — user_wishlists 테이블 집계 */
    private final UserWishlistRepository userWishlistRepository;

    /* ── 섹션 13: 콘텐츠 성과 ── */
    /* 코스 진행 현황 — user_course_progress 테이블 집계 */
    private final UserCourseProgressRepository userCourseProgressRepository;
    /* 업적 달성 — user_achievements 테이블 집계 */
    private final UserAchievementRepository userAchievementRepository;
    /* 퀴즈 시도 — quiz_attempts 테이블 집계 */
    private final QuizAttemptRepository quizAttemptRepository;

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
        long totalPosts = postMapper.countByStatus(PostStatus.PUBLISHED.name(),null);

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
    // 3. 추천 성능 지표 — recommendation_impact 기반 (실측치)
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
     * <p>Movie 카탈로그의 상위 1000건을 ID 오름차순으로 로드하여
     * {@code movies.genres} JSON 배열을 Java 에서 파싱·집계한 뒤 상위 8개 장르를 반환한다.
     * 관리자 화면에서 이 지표는 "추천될 가능성이 있는 영화의 장르 분포" 로 해석된다.</p>
     *
     * <p>2026-04-08: 과거의 "recommendation_logs 테이블 구현 후 교체" 계획 주석을 제거.
     * 실제 추천 이력은 {@code recommendation_impact} 에 누적되며,
     * 관리자 화면은 별도의 {@code getRecommendationLogs()} 에서 해당 테이블을 직접 조회한다.
     * 본 메서드는 "카탈로그 기반 장르 분포" 라는 독립 지표이므로 교체 대상이 아니다.</p>
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
     * <p>2026-04-15 전환: 기존 {@code recommendation_impact} 기반 매핑을
     * {@code recommendation_log} (Agent 저장 원천 테이블) 직접 조회로 교체한다.
     * Agent 가 추천 완료 시점에 {@code POST /api/v1/recommendations/internal/batch} 로
     * 저장한 실제 점수/이유/랭크 정보를 그대로 관리자에게 보여준다.</p>
     *
     * <h4>점수 · 피드백 필드 매핑</h4>
     * <ul>
     *   <li>{@code score}: {@link RecommendationLog#getScore()} 원본값 (0.0~1.0 정규화 가정)</li>
     *   <li>{@code feedback}: {@code clicked=true} 면 "CLICKED", 그 외 null.
     *       찜/봤어요/좋아요/관심없음 등 다른 피드백은 현재 {@link RecommendationImpact}
     *       / {@code recommendation_feedback} 에 분리 저장되어 있어, 본 탭의 단일 컬럼으로는
     *       클릭 여부만 표시한다 (상세 분석은 별도 탭에서 제공 예정).</li>
     * </ul>
     *
     * <h4>N+1 주의</h4>
     * <p>{@link RecommendationLogRepository#findAllWithMovie(Pageable)} 는 Movie 를
     * JOIN FETCH 하여 N+1 을 방지한다.</p>
     */
    public List<RecommendationLogResponse> getRecommendationLogs(int page, int size) {
        log.debug("[admin-stats] 추천 로그 조회 — page={}, size={}", page, size);

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        // RecommendationLog 페이징 조회 (Movie JOIN FETCH, createdAt DESC 정렬은 Pageable Sort 로 지정)
        Page<RecommendationLog> logPage = recommendationLogRepository.findAllWithMovie(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return logPage.getContent().stream()
                .map(logEntity -> {
                    // score 원본값 (Float → double). null 방어는 0.0 fallback
                    double score = (logEntity.getScore() != null)
                            ? logEntity.getScore().doubleValue()
                            : 0.0;

                    Double hybridScore = (logEntity.getHybridScore() != null)
                            ? logEntity.getHybridScore().doubleValue()
                            : null;

                    // 클릭 여부만 단일 피드백 필드로 표시 (그 외 피드백은 별도 탭 대상)
                    String feedback = Boolean.TRUE.equals(logEntity.getClicked()) ? "CLICKED" : null;

                    // Movie 는 JOIN FETCH 로 이미 로드 — title 까지 한 번에 꺼냄
                    String movieId = logEntity.getMovie() != null ? logEntity.getMovie().getMovieId() : null;
                    String movieTitle = logEntity.getMovie() != null ? logEntity.getMovie().getTitle() : null;

                    return new RecommendationLogResponse(
                            logEntity.getRecommendationLogId(),
                            logEntity.getUserId(),
                            movieId,
                            movieTitle,
                            score,
                            hybridScore,
                            logEntity.getRankPosition(),
                            logEntity.getReason(),
                            logEntity.getUserIntent(),
                            logEntity.getSessionId(),
                            feedback,
                            logEntity.getCreatedAt()
                    );
                })
                .toList();
    }

    // ──────────────────────────────────────────────
    // 4. 검색 분석 — search_history 기반 (실측치)
    // ──────────────────────────────────────────────

    /**
     * 인기 검색어를 반환한다.
     *
     * <p>search_history 테이블의 실제 데이터를 집계한다.
     * COUNT는 해당 키워드에 대해 저장된 검색 이력 레코드 수를 의미한다.</p>
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
                    // Jackson 3.x: JsonNode.fields() Iterator → properties() Set<Map.Entry> 로 API 변경
                    for (var entry : root.properties()) {
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

    // ══════════════════════════════════════════════
    // 9. 포인트 경제 분석
    // ══════════════════════════════════════════════

    /**
     * 포인트 경제 개요 KPI를 반환한다.
     *
     * <p>총 발행/소비, 전체 잔액, 활성 사용자 수, 오늘 발행/소비 6개 지표.</p>
     */
    public PointEconomyOverviewResponse getPointEconomyOverview() {
        long totalIssued = adminPointsHistoryRepository.sumTotalIssued();
        long totalSpent = adminPointsHistoryRepository.sumTotalSpent();
        long totalBalance = userPointRepository.sumTotalBalance();
        long activeUsers = userPointRepository.countActiveUsers();

        /* 오늘 발행/소비 */
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        long todayIssued = adminPointsHistoryRepository.sumIssuedBetween(todayStart, todayEnd);
        long todaySpent = adminPointsHistoryRepository.sumSpentBetween(todayStart, todayEnd);

        log.debug("[admin-stats] 포인트 경제 개요 — 총발행={}, 총소비={}, 잔액={}, 활성={}",
                totalIssued, totalSpent, totalBalance, activeUsers);
        return new PointEconomyOverviewResponse(totalIssued, totalSpent, totalBalance,
                activeUsers, todayIssued, todaySpent);
    }

    /**
     * 포인트 유형별 분포를 반환한다.
     *
     * <p>earn/spend/bonus/expire/refund/revoke 유형별 건수와 포인트 합계.</p>
     */
    public PointTypeDistributionResponse getPointTypeDistribution() {
        List<Object[]> rows = adminPointsHistoryRepository.countAndSumGroupByPointType();
        long totalCount = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<PointTypeItem> distribution = rows.stream()
                .map(row -> {
                    String type = (String) row[0];
                    long count = ((Number) row[1]).longValue();
                    long amount = ((Number) row[2]).longValue();
                    double pct = totalCount > 0
                            ? Math.round((double) count / totalCount * 1000.0) / 10.0
                            : 0.0;
                    String label = switch (type) {
                        case "earn"   -> "활동 리워드";
                        case "spend"  -> "포인트 사용";
                        case "bonus"  -> "보너스";
                        case "expire" -> "만료";
                        case "refund" -> "환불";
                        case "revoke" -> "회수";
                        default       -> type;
                    };
                    return new PointTypeItem(type, label, count, amount, pct);
                })
                .toList();

        log.debug("[admin-stats] 포인트 유형 분포 — {} 유형", distribution.size());
        return new PointTypeDistributionResponse(distribution);
    }

    /**
     * 등급별 사용자 분포를 반환한다.
     */
    public GradeDistributionResponse getGradeDistribution() {
        List<Object[]> rows = userPointRepository.countGroupByGrade();
        long totalUsers = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        /* 등급 코드 → 한국어명 매핑 */
        Map<String, String> gradeNames = Map.of(
                "NORMAL", "알갱이",
                "BRONZE", "강냉이",
                "SILVER", "팝콘",
                "GOLD", "카라멜팝콘",
                "PLATINUM", "몽글팝콘",
                "DIAMOND", "몽아일체"
        );

        List<GradeItem> grades = rows.stream()
                .map(row -> {
                    String code = (String) row[0];
                    long count = ((Number) row[1]).longValue();
                    double pct = totalUsers > 0
                            ? Math.round((double) count / totalUsers * 1000.0) / 10.0
                            : 0.0;
                    String name = gradeNames.getOrDefault(code, code);
                    return new GradeItem(code, name, count, pct);
                })
                .toList();

        log.debug("[admin-stats] 등급 분포 — {} 등급, 총 {} 명", grades.size(), totalUsers);
        return new GradeDistributionResponse(grades);
    }

    /**
     * 일별 포인트 발행/소비 추이를 반환한다.
     *
     * @param period 기간 문자열 ("7d", "30d" 등)
     */
    public PointTrendsResponse getPointTrends(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now();
        List<DailyPointTrend> trends = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            long issued = adminPointsHistoryRepository.sumIssuedBetween(dayStart, dayEnd);
            long spent = adminPointsHistoryRepository.sumSpentBetween(dayStart, dayEnd);
            long netFlow = issued - spent;

            trends.add(new DailyPointTrend(date.format(DATE_FMT), issued, spent, netFlow));
        }

        log.debug("[admin-stats] 포인트 추이 — {}일", days);
        return new PointTrendsResponse(trends);
    }

    // ══════════════════════════════════════════════
    // 10. AI 서비스 분석
    // ══════════════════════════════════════════════

    /**
     * AI 서비스 개요 KPI를 반환한다.
     *
     * <p>전체/오늘 세션 수, 턴 수, 세션당 평균 턴, 추천 영화 수.</p>
     */
    public AiServiceOverviewResponse getAiServiceOverview() {
        long totalSessions = adminChatSessionRepository.countByIsDeletedFalse();
        long totalTurns = adminChatSessionRepository.sumAllTurnCount();
        double avgTurns = totalSessions > 0
                ? Math.round((double) totalTurns / totalSessions * 10.0) / 10.0
                : 0.0;
        long totalMovies = adminChatSessionRepository.sumAllRecommendedMovieCount();

        /* 오늘 세션/턴 */
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        long todaySessions = adminChatSessionRepository.countByCreatedAtBetween(todayStart, todayEnd);
        long todayTurns = adminChatSessionRepository.sumTurnCountByCreatedAtBetween(todayStart, todayEnd);

        log.debug("[admin-stats] AI 서비스 개요 — 세션={}, 턴={}, 평균={}, 영화={}",
                totalSessions, totalTurns, avgTurns, totalMovies);
        return new AiServiceOverviewResponse(totalSessions, totalTurns, avgTurns,
                todaySessions, todayTurns, totalMovies);
    }

    /**
     * AI 세션 일별 추이를 반환한다.
     *
     * @param period 기간 문자열 ("7d", "30d" 등)
     */
    public AiSessionTrendsResponse getAiSessionTrends(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now();
        List<DailyAiTrend> trends = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            long sessions = adminChatSessionRepository.countByCreatedAtBetween(dayStart, dayEnd);
            long turns = adminChatSessionRepository.sumTurnCountByCreatedAtBetween(dayStart, dayEnd);

            trends.add(new DailyAiTrend(date.format(DATE_FMT), sessions, turns));
        }

        log.debug("[admin-stats] AI 세션 추이 — {}일", days);
        return new AiSessionTrendsResponse(trends);
    }

    /**
     * AI 의도(Intent) 분포를 반환한다.
     *
     * <p>ChatSessionArchive.intentSummary JSON을 파싱하여 의도별 건수를 집계한다.
     * intentSummary 형식: {"recommend": 3, "search": 1, ...}</p>
     */
    public AiIntentDistributionResponse getAiIntentDistribution() {
        List<String> summaries = adminChatSessionRepository.findAllIntentSummaries();

        /* 의도별 카운트 합산 */
        Map<String, Long> intentCounts = new HashMap<>();
        for (String json : summaries) {
            if (json == null || json.isBlank()) continue;
            try {
                JsonNode root = STATS_OBJECT_MAPPER.readTree(json);
                if (root.isObject()) {
                    for (var entry : root.properties()) {
                        String intent = entry.getKey().trim();
                        long count = entry.getValue().asLong(0);
                        if (!intent.isEmpty() && count > 0) {
                            intentCounts.merge(intent, count, Long::sum);
                        }
                    }
                }
            } catch (Exception e) {
                log.trace("[admin-stats] intentSummary 파싱 실패: {}", json);
            }
        }

        long totalCount = intentCounts.values().stream().mapToLong(Long::longValue).sum();

        /* 의도 코드 → 한국어 라벨 매핑 */
        Map<String, String> intentLabels = Map.of(
                "recommend", "영화 추천",
                "search", "영화 검색",
                "general", "일반 대화",
                "relation", "관계 탐색",
                "info", "영화 정보",
                "theater", "상영관 조회",
                "booking", "예매 안내"
        );

        List<IntentItem> intents = intentCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    String intent = entry.getKey();
                    long count = entry.getValue();
                    double pct = totalCount > 0
                            ? Math.round((double) count / totalCount * 1000.0) / 10.0
                            : 0.0;
                    String label = intentLabels.getOrDefault(intent, intent);
                    return new IntentItem(intent, label, count, pct);
                })
                .toList();

        log.debug("[admin-stats] AI 의도 분포 — {} 의도, 총 {} 건", intents.size(), totalCount);
        return new AiIntentDistributionResponse(intents);
    }

    /**
     * AI 쿼터 소진 현황을 반환한다.
     */
    public AiQuotaStatsResponse getAiQuotaStats() {
        long totalQuotaUsers = userAiQuotaRepository.count();
        double avgDaily = userAiQuotaRepository.avgDailyAiUsed();
        double avgMonthly = userAiQuotaRepository.avgMonthlyCouponUsed();
        long totalTokens = userAiQuotaRepository.sumPurchasedAiTokens();
        long exhausted = userAiQuotaRepository.countExhaustedUsers();

        /* 소수점 1자리 반올림 */
        avgDaily = Math.round(avgDaily * 10.0) / 10.0;
        avgMonthly = Math.round(avgMonthly * 10.0) / 10.0;

        log.debug("[admin-stats] AI 쿼터 — 사용자={}, 평균일일={}, 평균월간={}, 이용권={}, 소진={}",
                totalQuotaUsers, avgDaily, avgMonthly, totalTokens, exhausted);
        return new AiQuotaStatsResponse(totalQuotaUsers, avgDaily, avgMonthly, totalTokens, exhausted);
    }

    // ══════════════════════════════════════════════
    // 11. 커뮤니티 분석
    // ══════════════════════════════════════════════

    /**
     * 커뮤니티 개요 KPI를 반환한다.
     *
     * <p>전체/오늘 게시글, 댓글 수, 전체/미처리 신고 수.</p>
     */
    public CommunityOverviewResponse getCommunityOverview() {
        long totalPosts = postMapper.countByStatus(PostStatus.PUBLISHED.name(),null);
        long totalComments = postMapper.countAllComments();
        long totalReports = postMapper.countAllDeclarations();
        long pendingReports = postMapper.countDeclarationsByStatus("pending");

        /* 오늘 게시글/댓글 */
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        long todayPosts = postMapper.countByStatusAndCreatedAtBetween(
                PostStatus.PUBLISHED.name(), todayStart, todayEnd);
        long todayComments = postMapper.countCommentsByCreatedAtBetween(todayStart, todayEnd);

        log.debug("[admin-stats] 커뮤니티 개요 — 게시글={}, 댓글={}, 신고={}, 미처리={}",
                totalPosts, totalComments, totalReports, pendingReports);
        return new CommunityOverviewResponse(totalPosts, totalComments, totalReports,
                pendingReports, todayPosts, todayComments);
    }

    /**
     * 커뮤니티 일별 추이를 반환한다.
     *
     * @param period 기간 문자열 ("7d", "30d" 등)
     */
    public CommunityTrendsResponse getCommunityTrends(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now();
        List<DailyCommunityTrend> trends = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            long posts = postMapper.countByStatusAndCreatedAtBetween(
                    PostStatus.PUBLISHED.name(), dayStart, dayEnd);
            long comments = postMapper.countCommentsByCreatedAtBetween(dayStart, dayEnd);
            long reports = postMapper.countDeclarationsByCreatedAtBetween(dayStart, dayEnd);

            trends.add(new DailyCommunityTrend(date.format(DATE_FMT), posts, comments, reports));
        }

        log.debug("[admin-stats] 커뮤니티 추이 — {}일", days);
        return new CommunityTrendsResponse(trends);
    }

    /**
     * 게시글 카테고리별 분포를 반환한다.
     */
    public PostCategoryDistributionResponse getPostCategoryDistribution() {
        List<Map<String, Object>> rows = postMapper.countPublishedGroupByCategory();
        long totalPosts = rows.stream()
                .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                .sum();

        /* 카테고리 코드 → 한국어 라벨 매핑 */
        Map<String, String> categoryLabels = Map.of(
                "FREE", "자유게시판",
                "DISCUSSION", "토론",
                "RECOMMENDATION", "추천",
                "NEWS", "뉴스",
                "PLAYLIST_SHARE", "플레이리스트 공유"
        );

        List<CategoryItem> categories = rows.stream()
                .map(row -> {
                    String category = (String) row.get("category");
                    long count = ((Number) row.get("cnt")).longValue();
                    double pct = totalPosts > 0
                            ? Math.round((double) count / totalPosts * 1000.0) / 10.0
                            : 0.0;
                    String label = categoryLabels.getOrDefault(category, category);
                    return new CategoryItem(category, label, count, pct);
                })
                .toList();

        log.debug("[admin-stats] 카테고리 분포 — {} 카테고리, 총 {} 게시글", categories.size(), totalPosts);
        return new PostCategoryDistributionResponse(categories);
    }

    /**
     * 신고/독성 분석을 반환한다.
     *
     * <p>전체 신고 수, 처리 완료율, 평균 독성 점수, 상태별 분포, 독성 점수 5구간 분포.</p>
     */
    public ReportAnalysisResponse getReportAnalysis() {
        long totalReports = postMapper.countAllDeclarations();

        /* 상태별 분포 */
        String[] statuses = {"pending", "reviewed", "resolved", "dismissed"};
        Map<String, String> statusLabels = Map.of(
                "pending", "대기 중",
                "reviewed", "검토 완료",
                "resolved", "처리 완료",
                "dismissed", "기각"
        );
        List<ReportStatusItem> statusDist = new ArrayList<>();
        long resolvedCount = 0;
        for (String status : statuses) {
            long count = postMapper.countDeclarationsByStatus(status);
            double pct = totalReports > 0
                    ? Math.round((double) count / totalReports * 1000.0) / 10.0
                    : 0.0;
            statusDist.add(new ReportStatusItem(status, statusLabels.getOrDefault(status, status), count, pct));
            if ("resolved".equals(status) || "dismissed".equals(status)) {
                resolvedCount += count;
            }
        }
        double resolvedRate = totalReports > 0
                ? Math.round((double) resolvedCount / totalReports * 1000.0) / 10.0
                : 0.0;

        /* 평균 독성 점수 */
        Double avgToxRaw = postMapper.avgDeclarationToxicityScore();
        double avgToxicity = avgToxRaw != null ? Math.round(avgToxRaw * 1000.0) / 1000.0 : 0.0;

        /* 독성 점수 5구간 분포 */
        List<Map<String, Object>> bucketRows = postMapper.countDeclarationsByToxicityBucket();
        long totalBucketCount = bucketRows.stream()
                .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                .sum();
        List<ToxicityBucket> buckets = bucketRows.stream()
                .map(row -> {
                    String range = (String) row.get("range_label");
                    long count = ((Number) row.get("cnt")).longValue();
                    double pct = totalBucketCount > 0
                            ? Math.round((double) count / totalBucketCount * 1000.0) / 10.0
                            : 0.0;
                    return new ToxicityBucket(range, count, pct);
                })
                .toList();

        log.debug("[admin-stats] 신고 분석 — 총={}, 처리율={}%, 평균독성={}",
                totalReports, resolvedRate, avgToxicity);
        return new ReportAnalysisResponse(totalReports, resolvedRate, avgToxicity, statusDist, buckets);
    }

    // ══════════════════════════════════════════════
    // 12. 사용자 참여도 분석
    // ══════════════════════════════════════════════

    /**
     * 사용자 참여도 개요 KPI를 반환한다.
     *
     * <p>총 출석 체크 수, 오늘 출석 수, 활동 진행 사용자 수,
     * 총 위시리스트 수, 평균 연속 출석일 5가지 지표를 집계한다.</p>
     *
     * <ul>
     *   <li>totalAttendance: user_attendance 전체 레코드 수 (날짜별 1건이므로 실질적 총 출석일 수)</li>
     *   <li>todayAttendance: 오늘(LocalDate.now()) 출석 체크 수</li>
     *   <li>totalActivityUsers: user_activity_progress 레코드 수 (활동 참여한 적 있는 사용자의 활동별 행 수)</li>
     *   <li>totalWishlists: user_wishlists 전체 레코드 수</li>
     *   <li>avgStreakDays: 사용자별 최신 streak 평균, 소수점 1자리 반올림</li>
     * </ul>
     */
    public EngagementOverviewResponse getEngagementOverview() {
        /* 총 출석 체크 수 */
        long totalAttendance = userAttendanceRepository.count();

        /* 오늘 출석 수 */
        long todayAttendance = userAttendanceRepository.countByCheckDate(LocalDate.now());

        /* 활동 진행 레코드 수 (사용자 × 활동유형 행 수) */
        long totalActivityUsers = userActivityProgressRepository.count();

        /* 총 위시리스트 수 */
        long totalWishlists = userWishlistRepository.count();

        /* 평균 연속 출석일 (소수점 1자리 반올림) */
        double avgStreak = userAttendanceRepository.avgLatestStreakCount();
        avgStreak = Math.round(avgStreak * 10.0) / 10.0;

        log.debug("[admin-stats] 참여도 개요 — 총출석={}, 오늘출석={}, 활동사용자={}, 위시리스트={}, 평균연속={}",
                totalAttendance, todayAttendance, totalActivityUsers, totalWishlists, avgStreak);
        return new EngagementOverviewResponse(
                totalAttendance, todayAttendance, totalActivityUsers, totalWishlists, avgStreak);
    }

    /**
     * 활동별 참여 현황을 반환한다.
     *
     * <p>user_activity_progress 테이블에서 actionType별로 참여 사용자 수(행 수)와
     * 총 활동 횟수(totalCount 합계)를 집계한다. totalActions 내림차순 정렬.</p>
     *
     * <p>actionType 코드를 한국어 라벨로 매핑한다.
     * 매핑되지 않은 코드는 코드 자체를 라벨로 사용한다.</p>
     */
    public ActivityDistributionResponse getActivityDistribution() {
        /* actionType별 [actionType, userCount, totalActions] 집계 */
        List<Object[]> rows = userActivityProgressRepository.countGroupByActionType();

        /* actionType → 한국어 라벨 매핑 (주요 55개 정책 코드 기반) */
        Map<String, String> actionLabels = new HashMap<>();
        actionLabels.put("review_write",       "리뷰 작성");
        actionLabels.put("attendance",          "출석 체크");
        actionLabels.put("movie_like",          "영화 좋아요");
        actionLabels.put("wishlist_add",        "위시리스트 추가");
        actionLabels.put("post_write",          "게시글 작성");
        actionLabels.put("comment_write",       "댓글 작성");
        actionLabels.put("worldcup_play",       "이상형 월드컵");
        actionLabels.put("roadmap_complete",    "도장깨기 완주");
        actionLabels.put("quiz_correct",        "퀴즈 정답");
        actionLabels.put("playlist_create",     "플레이리스트 생성");
        actionLabels.put("profile_complete",    "프로필 완성");
        actionLabels.put("first_review",        "첫 리뷰");
        actionLabels.put("first_login",         "첫 로그인");

        List<ActivityItem> activities = rows.stream()
                .map(row -> {
                    String actionType  = (String) row[0];
                    long userCount     = ((Number) row[1]).longValue();
                    long totalActions  = ((Number) row[2]).longValue();
                    String label = actionLabels.getOrDefault(actionType, actionType);
                    return new ActivityItem(actionType, label, userCount, totalActions);
                })
                .toList();

        log.debug("[admin-stats] 활동 분포 — {} 유형", activities.size());
        return new ActivityDistributionResponse(activities);
    }

    /**
     * 연속 출석일 구간별 사용자 분포를 반환한다.
     *
     * <p>사용자별 최신 streakCount를 기준으로
     * 1일 / 2-3일 / 4-7일 / 8-14일 / 15-30일 / 31일+ 구간으로 분류한다.
     * 빈 DB에서는 빈 리스트를 반환한다.</p>
     *
     * <p>구간 순서를 보장하기 위해 미리 정의된 순서 맵으로 정렬한다.</p>
     */
    public AttendanceStreakDistributionResponse getAttendanceStreakDistribution() {
        /* DB에서 구간별 사용자 수 집계 */
        List<Object[]> rows = userAttendanceRepository.countGroupByStreakRange();

        /* 구간 표시 순서 정의 (오름차순) */
        Map<String, Integer> rangeOrder = new LinkedHashMap<>();
        rangeOrder.put("1일",      0);
        rangeOrder.put("2-3일",    1);
        rangeOrder.put("4-7일",    2);
        rangeOrder.put("8-14일",   3);
        rangeOrder.put("15-30일",  4);
        rangeOrder.put("31일+",    5);

        /* rows → Map<range, userCount> 변환 */
        Map<String, Long> countMap = new HashMap<>();
        for (Object[] row : rows) {
            String range = (String) row[0];
            long count   = ((Number) row[1]).longValue();
            countMap.put(range, count);
        }

        /* 전체 사용자 수 (비율 계산 분모) */
        long total = countMap.values().stream().mapToLong(Long::longValue).sum();

        /* 정의된 순서로 StreakBucket 리스트 생성 (데이터 없는 구간은 0으로 채움) */
        List<StreakBucket> buckets = rangeOrder.keySet().stream()
                .map(range -> {
                    long count = countMap.getOrDefault(range, 0L);
                    double pct = total > 0
                            ? Math.round((double) count / total * 1000.0) / 10.0
                            : 0.0;
                    return new StreakBucket(range, count, pct);
                })
                .toList();

        log.debug("[admin-stats] 연속 출석 분포 — 총 {} 사용자 분석", total);
        return new AttendanceStreakDistributionResponse(buckets);
    }

    // ══════════════════════════════════════════════
    // 13. 콘텐츠 성과 분석
    // ══════════════════════════════════════════════

    /**
     * 콘텐츠 성과 개요 KPI를 반환한다.
     *
     * <p>도장깨기 코스 진행/완주/완주율, 업적 달성 수, 퀴즈 시도 수, 정답률을 집계한다.</p>
     *
     * <ul>
     *   <li>completionRate = completedCourses / totalCourseProgress × 100 (소수점 1자리)</li>
     *   <li>quizCorrectRate = correctAttempts / totalAttempts × 100 (소수점 1자리)</li>
     *   <li>빈 DB 환경에서는 모두 0 반환</li>
     * </ul>
     */
    public ContentPerformanceOverviewResponse getContentPerformanceOverview() {
        /* 전체 코스 진행 레코드 수 */
        long totalCourseProgress = userCourseProgressRepository.count();

        /* 완주 코스 수 (status = COMPLETED) */
        long completedCourses = userCourseProgressRepository.countByStatus(CourseProgressStatus.COMPLETED);

        /* 완주율 (소수점 1자리 반올림) */
        double completionRate = totalCourseProgress > 0
                ? Math.round((double) completedCourses / totalCourseProgress * 1000.0) / 10.0
                : 0.0;

        /* 전체 업적 달성 수 */
        long totalAchievements = userAchievementRepository.count();

        /* 전체 퀴즈 시도 수 */
        long totalQuizAttempts = quizAttemptRepository.count();

        /* 퀴즈 정답률 (소수점 1자리 반올림) */
        long correctAttempts = quizAttemptRepository.countCorrect();
        double quizCorrectRate = totalQuizAttempts > 0
                ? Math.round((double) correctAttempts / totalQuizAttempts * 1000.0) / 10.0
                : 0.0;

        log.debug("[admin-stats] 콘텐츠 성과 — 코스진행={}, 완주={}, 업적={}, 퀴즈={}, 정답률={}%",
                totalCourseProgress, completedCourses, totalAchievements, totalQuizAttempts, quizCorrectRate);
        return new ContentPerformanceOverviewResponse(
                totalCourseProgress, completedCourses, completionRate,
                totalAchievements, totalQuizAttempts, quizCorrectRate);
    }

    /**
     * 코스별 완주율을 반환한다.
     *
     * <p>user_course_progress 테이블에서 courseId별 시작자 수, 완주자 수,
     * 완주율, 평균 진행률을 집계한다. 완주자 수 내림차순 정렬.</p>
     *
     * <p>BigDecimal avgProgress를 double로 변환 시 소수점 1자리로 반올림한다.</p>
     */
    public CourseCompletionResponse getCourseCompletion() {
        /* courseId별 [courseId, totalStarters, completedCount, avgProgressPercent] 집계 */
        List<Object[]> rows = userCourseProgressRepository.countGroupByCourseId();

        List<CourseCompletionItem> courses = rows.stream()
                .map(row -> {
                    String courseId      = (String) row[0];
                    long totalStarters   = ((Number) row[1]).longValue();
                    long completedCount  = ((Number) row[2]).longValue();
                    /* avgProgressPercent: BigDecimal or Double — Number로 안전하게 처리 */
                    double avgProgress   = row[3] != null
                            ? Math.round(((Number) row[3]).doubleValue() * 10.0) / 10.0
                            : 0.0;
                    double completionRate = totalStarters > 0
                            ? Math.round((double) completedCount / totalStarters * 1000.0) / 10.0
                            : 0.0;
                    return new CourseCompletionItem(courseId, totalStarters, completedCount,
                            completionRate, avgProgress);
                })
                .toList();

        log.debug("[admin-stats] 코스 완주율 — {} 코스", courses.size());
        return new CourseCompletionResponse(courses);
    }

    /**
     * 리뷰 품질 지표를 반환한다.
     *
     * <p>카테고리별 건수 분포, 평점별 건수 분포, 전체 리뷰 수, 평균 평점을 제공한다.
     * ReviewMapper(MyBatis)를 통해 실제 DB 집계를 수행한다.</p>
     *
     * <p>카테고리 코드 → 한국어 라벨 매핑:
     * THEATER_RECEIPT(영수증 인증), COURSE(도장깨기), WORLDCUP(월드컵),
     * WISHLIST(위시리스트), AI_RECOMMEND(AI 추천), PLAYLIST(플레이리스트), NONE(일반)</p>
     */
    public ReviewQualityResponse getReviewQualityStats() {
        /* 전체 리뷰 수 (소프트 삭제 제외) */
        long totalReviews = reviewMapper.count();

        /* 평균 평점 (소수점 2자리 반올림) */
        Double avgRatingRaw = reviewMapper.findAverageRating();
        double avgRating = avgRatingRaw != null
                ? Math.round(avgRatingRaw * 100.0) / 100.0
                : 0.0;

        /* 카테고리별 건수 분포 */
        List<Map<String, Object>> categoryRows = reviewMapper.countGroupByCategory();
        long totalForCatPct = categoryRows.stream()
                .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                .sum();

        /* 카테고리 코드 → 한국어 라벨 */
        Map<String, String> categoryLabels = new HashMap<>();
        categoryLabels.put("THEATER_RECEIPT", "영수증 인증");
        categoryLabels.put("COURSE",          "도장깨기");
        categoryLabels.put("WORLDCUP",        "월드컵");
        categoryLabels.put("WISHLIST",        "위시리스트");
        categoryLabels.put("AI_RECOMMEND",    "AI 추천");
        categoryLabels.put("PLAYLIST",        "플레이리스트");
        categoryLabels.put("NONE",            "일반");

        List<ReviewCategoryItem> categoryDist = categoryRows.stream()
                .map(row -> {
                    String code  = (String) row.get("categoryCode");
                    long count   = ((Number) row.get("cnt")).longValue();
                    double pct   = totalForCatPct > 0
                            ? Math.round((double) count / totalForCatPct * 1000.0) / 10.0
                            : 0.0;
                    String label = categoryLabels.getOrDefault(code, code);
                    return new ReviewCategoryItem(code, label, count, pct);
                })
                .toList();

        /* 평점별 건수 분포 */
        List<Map<String, Object>> ratingRows = reviewMapper.countGroupByRating();
        long totalForRatingPct = ratingRows.stream()
                .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                .sum();

        List<ReviewRatingItem> ratingDist = ratingRows.stream()
                .map(row -> {
                    int rating  = ((Number) row.get("rating")).intValue();
                    long count  = ((Number) row.get("cnt")).longValue();
                    double pct  = totalForRatingPct > 0
                            ? Math.round((double) count / totalForRatingPct * 1000.0) / 10.0
                            : 0.0;
                    return new ReviewRatingItem(rating, count, pct);
                })
                .toList();

        log.debug("[admin-stats] 리뷰 품질 — 총={}, 평점={}, 카테고리={}, 평점구간={}",
                totalReviews, avgRating, categoryDist.size(), ratingDist.size());
        return new ReviewQualityResponse(categoryDist, ratingDist, totalReviews, avgRating);
    }

    // ══════════════════════════════════════════════
    // 14. 전환 퍼널 분석
    // ══════════════════════════════════════════════

    /**
     * 6단계 전환 퍼널을 반환한다.
     *
     * <p>기간(period) 내 신규 가입자가 AI 채팅 사용 → 리뷰 작성 → 구독 → 결제로
     * 이어지는 전환 과정을 측정한다. 각 단계는 독립적으로 집계하며,
     * 이전 단계를 거치지 않아도 이후 단계에 포함될 수 있다.</p>
     *
     * <h4>6단계 정의</h4>
     * <ol>
     *   <li>신규 가입 — users.created_at 기간 내 신규 가입자 수</li>
     *   <li>첫 활동 — AI 세션 OR 리뷰 OR 위시리스트 추가한 사용자 (합산 후 중복 제거 불가 → 합산 최소값 사용)</li>
     *   <li>AI 채팅 — ChatSessionArchive 기간 내 distinct userId</li>
     *   <li>리뷰 작성 — reviews 기간 내 distinct userId</li>
     *   <li>구독 전환 — user_subscriptions 기간 내 distinct userId</li>
     *   <li>결제 전환 — payment_orders COMPLETED 기간 내 distinct userId</li>
     * </ol>
     *
     * <p>conversionFromPrev: 전 단계 대비 전환율 (단계 1 = 100.0).
     * conversionFromTop: 1단계(가입) 대비 전환율.</p>
     *
     * @param period 기간 문자열 ("7d", "30d", "90d")
     */
    public FunnelConversionResponse getFunnelConversion(String period) {
        int days = parsePeriodDays(period);
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        LocalDateTime end   = LocalDateTime.now();

        /* 단계 1: 신규 가입자 수 */
        long step1 = userMapper.countByCreatedAtBetween(start, end);

        /* 단계 3: AI 채팅 사용 고유 사용자 수 */
        long step3 = adminChatSessionRepository.countDistinctUserByCreatedAtBetween(start, end);

        /* 단계 4: 리뷰 작성 고유 사용자 수 */
        long step4 = reviewMapper.countDistinctUserByCreatedAtBetween(start, end);

        /* 단계 5: 구독 전환 고유 사용자 수 */
        long step5 = userSubscriptionRepository.countDistinctUserByCreatedAtBetween(start, end);

        /* 단계 6: 결제 전환 고유 사용자 수 (COMPLETED 주문) */
        long step6 = paymentOrderRepository.countByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, start, end);

        /*
         * 단계 2: 첫 활동 사용자 수.
         * 이상적으로는 (AI OR 리뷰 OR 위시리스트) DISTINCT userId 이지만
         * 3개 테이블 cross-join 없이는 정확한 합집합이 어렵다.
         * 실용적 근사: 3가지 중 최대값을 첫 활동 사용자 수로 사용한다
         * (단계 1과 단계 3 사이의 합리적 상한선).
         * step1 을 초과하지 않도록 min(step1, max(step3, step4, step5)) 적용.
         */
        long step2 = step1 > 0 ? Math.min(step1, Math.max(step3, Math.max(step4, step5))) : 0L;

        /* 단계별 배열로 처리하여 전환율 일괄 계산 */
        long[] counts = {step1, step2, step3, step4, step5, step6};
        String[] labels = {
                "신규 가입",
                "첫 활동",
                "AI 채팅 사용",
                "리뷰 작성",
                "구독 전환",
                "결제 전환"
        };

        List<FunnelStep> steps = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            long count = counts[i];
            /* 전 단계 대비 전환율: 단계 1은 100.0, 이후는 전 단계 / 현 단계 */
            double fromPrev;
            if (i == 0) {
                fromPrev = 100.0;
            } else {
                long prev = counts[i - 1];
                fromPrev = prev > 0
                        ? Math.round((double) count / prev * 1000.0) / 10.0
                        : 0.0;
            }
            /* 1단계(가입) 대비 전환율 */
            double fromTop = step1 > 0
                    ? Math.round((double) count / step1 * 1000.0) / 10.0
                    : 0.0;

            steps.add(new FunnelStep(i + 1, labels[i], count, fromPrev, fromTop));
        }

        log.debug("[admin-stats] 전환 퍼널 — 기간={}일, 가입={}, AI={}, 리뷰={}, 구독={}, 결제={}",
                days, step1, step3, step4, step5, step6);
        return new FunnelConversionResponse(period, steps);
    }

    // ══════════════════════════════════════════════
    // 15. 이탈 위험 분석
    // ══════════════════════════════════════════════

    /**
     * 이탈 위험 개요 KPI를 반환한다.
     *
     * <p>전체 사용자(최대 1000명 샘플)의 위험도 점수를 Java에서 계산하여
     * 4구간(없음/낮음/중간/높음)으로 분류한다.</p>
     *
     * <h4>점수 계산 기준 (최대 95점)</h4>
     * <ul>
     *   <li>로그인 공백 7일+: 10점 / 14일+: 25점 / 30일+: 40점 (누적 아님, 가장 높은 구간 적용)</li>
     *   <li>포인트 잔액 0 + 가입 7일 이상: 15점</li>
     *   <li>AI 세션 없음 (가입 14일 이상): 20점</li>
     *   <li>구독 미보유: 10점</li>
     * </ul>
     *
     * <p>AI 세션 없음 판정: ChatSessionArchive.userId 전체 목록을 Set으로 캐싱하여 O(1) 조회.</p>
     * <p>구독 보유 판정: UserSubscription ACTIVE 상태 userId Set으로 캐싱.</p>
     * <p>포인트 잔액: UserPoint.balance를 userId → balance Map으로 캐싱.</p>
     *
     * @return 위험도 4구간별 사용자 수 + 분석된 전체 사용자 수
     */
    public ChurnRiskOverviewResponse getChurnRiskOverview() {
        LocalDateTime now = LocalDateTime.now();

        /* 1. 전체 사용자 샘플 로드 (최대 1000명 — OOM 방지) */
        List<User> users = userMapper.findAllLimited(1000);
        if (users.isEmpty()) {
            log.debug("[admin-stats] 이탈 위험 분석 — 사용자 없음");
            return new ChurnRiskOverviewResponse(0, 0, 0, 0, 0);
        }

        /*
         * 2. 보조 데이터 Set/Map 캐싱 (N+1 방지).
         * 전체 ACTIVE 구독 userId Set
         */
        java.util.Set<String> activeSubUserIds = userSubscriptionRepository
                .findAll()
                .stream()
                .filter(s -> UserSubscription.Status.ACTIVE.equals(s.getStatus()))
                .map(UserSubscription::getUserId)
                .collect(Collectors.toSet());

        /* AI 세션 보유 userId Set (isDeleted 무관 — 사용 이력 기준) */
        java.util.Set<String> aiSessionUserIds = adminChatSessionRepository
                .findAll(PageRequest.of(0, 5000))
                .getContent()
                .stream()
                .map(session -> session.getUserId())
                .collect(Collectors.toSet());

        /* 포인트 잔액 userId → balance Map */
        Map<String, Long> balanceMap = userWishlistRepository.count() >= 0   // 항상 실행 (조건 더미)
                ? userPointRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                p -> p.getUserId(),
                                p -> (long) p.getBalance(),
                                (a, b) -> a
                        ))
                : Collections.emptyMap();

        /* 3. 사용자별 위험도 점수 계산 */
        long noRisk = 0, lowRisk = 0, mediumRisk = 0, highRisk = 0;

        for (User user : users) {
            int score = 0;

            /* 로그인 공백 점수 (가장 높은 구간 적용) */
            LocalDateTime lastLogin = user.getLastLoginAt();
            if (lastLogin == null || lastLogin.isBefore(now.minusDays(30))) {
                score += 40;
            } else if (lastLogin.isBefore(now.minusDays(14))) {
                score += 25;
            } else if (lastLogin.isBefore(now.minusDays(7))) {
                score += 10;
            }

            /* 포인트 잔액 0 + 가입 7일 이상: +15점 */
            LocalDateTime createdAt = user.getCreatedAt();
            long balance = balanceMap.getOrDefault(user.getUserId(), 0L);
            if (balance == 0 && createdAt != null && createdAt.isBefore(now.minusDays(7))) {
                score += 15;
            }

            /* AI 세션 없음 + 가입 14일 이상: +20점 */
            if (!aiSessionUserIds.contains(user.getUserId())
                    && createdAt != null && createdAt.isBefore(now.minusDays(14))) {
                score += 20;
            }

            /* 구독 미보유: +10점 */
            if (!activeSubUserIds.contains(user.getUserId())) {
                score += 10;
            }

            /* 구간 분류 */
            if (score < 25) {
                noRisk++;
            } else if (score < 50) {
                lowRisk++;
            } else if (score < 75) {
                mediumRisk++;
            } else {
                highRisk++;
            }
        }

        log.debug("[admin-stats] 이탈 위험 — 분석={}명, 없음={}, 낮음={}, 중간={}, 높음={}",
                users.size(), noRisk, lowRisk, mediumRisk, highRisk);
        return new ChurnRiskOverviewResponse(noRisk, lowRisk, mediumRisk, highRisk, users.size());
    }

    /**
     * 이탈 위험 신호 집계를 반환한다.
     *
     * <p>운영팀이 리텐션 캠페인 타깃을 설정할 수 있도록
     * 구체적인 이탈 위험 신호별 사용자 수를 집계한다.</p>
     *
     * <h4>집계 항목</h4>
     * <ul>
     *   <li>7일/14일/30일+ 미로그인: UserMapper.countByLastLoginAtBefore()</li>
     *   <li>포인트 잔액 0: UserPointRepository.countZeroBalanceUsers()</li>
     *   <li>구독 만료 후 미갱신: UserSubscriptionRepository.countExpiredWithoutRenewal() — 30일 이전 만료 기준</li>
     * </ul>
     */
    public ChurnRiskSignalsResponse getChurnRiskSignals() {
        LocalDateTime now = LocalDateTime.now();

        /* 7일+ 미로그인 */
        long inactive7  = userMapper.countByLastLoginAtBefore(now.minusDays(7));
        /* 14일+ 미로그인 */
        long inactive14 = userMapper.countByLastLoginAtBefore(now.minusDays(14));
        /* 30일+ 미로그인 */
        long inactive30 = userMapper.countByLastLoginAtBefore(now.minusDays(30));

        /* 포인트 잔액 0 사용자 수 */
        long zeroPoint = userPointRepository.countZeroBalanceUsers();

        /* 구독 만료 후 미갱신 사용자 수 (30일 이전 만료 기준) */
        long expiredNoRenewal = userSubscriptionRepository
                .countExpiredWithoutRenewal(now.minusDays(30));

        log.debug("[admin-stats] 이탈 신호 — 7일미로그인={}, 14일={}, 30일={}, 잔액0={}, 만료미갱신={}",
                inactive7, inactive14, inactive30, zeroPoint, expiredNoRenewal);
        return new ChurnRiskSignalsResponse(inactive7, inactive14, inactive30, zeroPoint, expiredNoRenewal);
    }
}
