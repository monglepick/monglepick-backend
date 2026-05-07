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
import com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyRepository;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserAchievementRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizAttemptRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizParticipationRepository;
import com.monglepick.monglepickbackend.domain.support.repository.SupportChatLogRepository;
import com.monglepick.monglepickbackend.domain.search.entity.PopularSearchKeyword;
import com.monglepick.monglepickbackend.domain.search.entity.SearchHistory;
import com.monglepick.monglepickbackend.domain.search.repository.PopularSearchKeywordRepository;
import com.monglepick.monglepickbackend.domain.search.repository.SearchHistoryRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.domain.wishlist.repository.UserWishlistRepository;
// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** trending_keywords 전체 누적치를 얻기 위한 사실상 무제한 lookback. */
    private static final String TRENDING_FULL_LOOKBACK_PERIOD = "36500d";
    /** 검색 세션 만료 기준. 동일 user_id + keyword 조합에서 30분 inactivity면 새 세션으로 본다. */
    private static final long SEARCH_SESSION_TIMEOUT_MINUTES = 30L;

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
    /* 인기 검색어 운영 메타 — 상단 TOP 20에 제외/수정 상태 병합 */
    private final PopularSearchKeywordRepository popularSearchKeywordRepository;
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
    /* 리워드 정책 — 활동 코드의 한국어 표시명 조회 */
    private final RewardPolicyRepository rewardPolicyRepository;
    /* 위시리스트 — user_wishlists 테이블 집계 */
    private final UserWishlistRepository userWishlistRepository;

    /* ── 섹션 13: 콘텐츠 성과 ── */
    /* 코스 진행 현황 — user_course_progress 테이블 집계 */
    private final UserCourseProgressRepository userCourseProgressRepository;
    /* 업적 달성 — user_achievements 테이블 집계 */
    private final UserAchievementRepository userAchievementRepository;
    /* 퀴즈 시도 — quiz_attempts 테이블 집계 */
    private final QuizAttemptRepository quizAttemptRepository;

    /* ── AI 서비스 통계 V2 (2026-04-29) ── */
    /* 퀴즈 참여 — quiz_participations 테이블 집계 (정답률·일별 응시 추이) */
    private final QuizParticipationRepository quizParticipationRepository;
    /* 고객센터 챗봇 로그 — support_chat_log 테이블 집계 (자동화율·의도 분포·hop) */
    private final SupportChatLogRepository supportChatLogRepository;

    /* Phase 4: Mock 제거 — Movie.genres / UserBehaviorProfile.genreAffinity JSON 파싱 */
    private static final ObjectMapper STATS_OBJECT_MAPPER = new ObjectMapper();

    @Value("${admin.health.recommend-url:http://localhost:8001}")
    private String recommendUrl;

    /** recommend 관리자 집계 API 호출용 클라이언트. */
    private final RestClient restClient = RestClient.create();

    /** 서비스 통계 KST 시간대 — 기간 필터를 한국 날짜 기준으로 고정한다. */
    private static final ZoneId SERVICE_STATS_ZONE = ZoneId.of("Asia/Seoul");
    /* ── 날짜 포맷터 ── */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ──────────────────────────────────────────────
    // 1. 서비스 개요 KPI
    // ──────────────────────────────────────────────

    /**
     * 서비스 전체 KPI 개요를 반환한다.
     *
     * <p>서비스 통계 탭의 기간 선택(7/30/90일)에 맞춰
     * DAU/MAU/신규 가입 카드를 같은 윈도우로 계산한다.</p>
     *
     * <ul>
     *   <li>DAU: 선택 기간 내 최대 일간 활성 사용자 수</li>
     *   <li>MAU: 선택 기간 내 활성 사용자 수</li>
     *   <li>신규 가입: 선택 기간 내 신규 가입자 수</li>
     * </ul>
     *
     * @param period 기간 문자열 (예: "7d", "30d", "90d")
     * @return KPI 카드 6항목 (DAU, MAU, 신규가입, 리뷰수, 평균평점, 게시글수)
     */
    public OverviewResponse getOverview(String period) {
        ServiceStatsWindow window = resolveServiceStatsWindow(period);
        List<DailyTrend> trends = buildServiceTrends(window);

        /* DAU: 선택 기간 내 최대 일간 활성 사용자 수 */
        long dau = trends.stream()
                .mapToLong(DailyTrend::dau)
                .max()
                .orElse(0L);

        /*
         * MAU: users.last_login_at 기반으로 추적 가능한 "기간 내 활성 사용자 수".
         * 각 사용자는 마지막 로그인 시각 1건만 유지하므로 일별 DAU 합계와 동일 기준으로 맞춘다.
         */
        long mau = trends.stream()
                .mapToLong(DailyTrend::dau)
                .sum();

        /* 신규 가입: 차트와 동일하게 일별 합계를 사용해 카드/차트 기준을 일치시킨다. */
        long newUsersWeek = trends.stream()
                .mapToLong(DailyTrend::newUsers)
                .sum();

        /* 전체 리뷰 수 */
        long totalReviews = reviewMapper.count();

        /* 평균 평점 */
        Double avgRatingRaw = reviewMapper.findAverageRating();
        double avgRating = avgRatingRaw != null ? Math.round(avgRatingRaw * 100.0) / 100.0 : 0.0;

        /* 전체 게시글 수 (PUBLISHED) */
        long totalPosts = postMapper.countByStatus(PostStatus.PUBLISHED.name(),null);

        log.debug("[admin-stats] 서비스 개요 조회 — period={}, start={}, end={}, DAU={}, MAU={}, 신규={}",
                period, window.startDate(), window.endDate(), dau, mau, newUsersWeek);
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
        ServiceStatsWindow window = resolveServiceStatsWindow(period);
        List<DailyTrend> trends = buildServiceTrends(window);

        log.debug("[admin-stats] 추이 차트 조회 — period={}, start={}, end={}, days={}",
                period, window.startDate(), window.endDate(), window.days());
        return new TrendsResponse(trends);
    }

    /**
     * 서비스 통계 일별 추이 목록을 생성한다.
     *
     * <p>모든 일자 집계는 KST 자정 경계의 반개구간[start, end)으로 처리하여
     * 자정에 찍힌 레코드가 이틀에 중복 포함되지 않도록 한다.</p>
     */
    private List<DailyTrend> buildServiceTrends(ServiceStatsWindow window) {
        List<DailyTrend> trends = new ArrayList<>(window.days());

        for (int i = 0; i < window.days(); i++) {
            LocalDate date = window.startDate().plusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            long dau = userMapper.countByLastLoginAtBetween(dayStart, dayEnd);
            long newUsers = userMapper.countByCreatedAtBetween(dayStart, dayEnd);
            long reviews = reviewMapper.countByCreatedAtBetween(dayStart, dayEnd);
            long posts = postMapper.countByStatusAndCreatedAtBetween(PostStatus.PUBLISHED.name(), dayStart, dayEnd);

            trends.add(new DailyTrend(date.format(DATE_FMT), dau, newUsers, reviews, posts));
        }

        return trends;
    }

    /** 서비스 통계용 기간 윈도우를 KST 기준으로 계산한다. */
    private ServiceStatsWindow resolveServiceStatsWindow(String period) {
        int days = parsePeriodDays(period);
        LocalDate endDate = LocalDate.now(SERVICE_STATS_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1L);

        return new ServiceStatsWindow(days, startDate, endDate);
    }

    /** 서비스 통계 집계에 사용하는 날짜 윈도우. */
    private record ServiceStatsWindow(
            int days,
            LocalDate startDate,
            LocalDate endDate
    ) {}

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
     *       찜/봤어요/관심없음 등 funnel 피드백은 {@link RecommendationImpact} 단일 출처에서,
     *       별점/리뷰는 reviews 테이블(2026-04-27 통합 — recommendation_feedback 폐기 후
     *       단일 진실 원본)에 저장되어 있어, 본 탭의 단일 컬럼으로는 클릭 여부만 표시한다
     *       (상세 분석은 별도 탭에서 제공 예정).</li>
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
    // 4. 검색 분석
    // ──────────────────────────────────────────────

    /**
     * 인기 검색어를 반환한다.
     *
     * <p>recommend의 trending_keywords 누적치를 기준으로 TOP N을 반환한다.
     * 기간 필터는 무시하며, 관리자 운영 메타(popular_search_keyword)만 병합한다.</p>
     */
    public PopularKeywordsResponse getPopularKeywords(int limit) {
        PopularKeywordsResponse recommendResponse = getPopularKeywordsViaRecommend(limit);
        if (recommendResponse != null) {
            return recommendResponse;
        }

        log.warn("[admin-stats] recommend 인기 검색어 집계 실패, 빈 목록 반환");
        return new PopularKeywordsResponse(List.of());
    }

    private PopularKeywordsResponse getPopularKeywordsViaRecommend(int limit) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(recommendUrl)
                    .path("/api/v2/search/admin/popular")
                    .queryParam("period", TRENDING_FULL_LOOKBACK_PERIOD)
                    .queryParam("limit", limit)
                    .encode()
                    .build()
                    .toUri();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(Map.class);

            List<KeywordItem> items = enrichPopularKeywords(extractRecommendPopularKeywords(payload));
            log.debug("[admin-stats] recommend 인기 검색어 집계 성공 — lookback={}, limit={}, count={}",
                    TRENDING_FULL_LOOKBACK_PERIOD, limit, items.size());
            return new PopularKeywordsResponse(items);
        } catch (Exception e) {
            log.warn("[admin-stats] recommend 인기 검색어 집계 호출 실패 — lookback={}, limit={}, error={}",
                    TRENDING_FULL_LOOKBACK_PERIOD, limit, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<KeywordItem> extractRecommendPopularKeywords(Map<String, Object> payload) {
        if (payload == null) {
            return List.of();
        }

        Object keywordsObject = payload.get("keywords");
        if (!(keywordsObject instanceof List<?> keywordList) || keywordList.isEmpty()) {
            return List.of();
        }

        List<KeywordItem> items = new ArrayList<>();
        for (Object entry : keywordList) {
            if (!(entry instanceof Map<?, ?> item)) {
                continue;
            }

            String keyword = asString(item.get("keyword"));
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            long searchCount = asLong(item.get("search_count"));
            items.add(new KeywordItem(keyword, searchCount, 0.0, null, null, null, null, null));
        }
        return items;
    }

    private List<KeywordItem> enrichPopularKeywords(List<KeywordItem> items) {
        if (items.isEmpty()) {
            return items;
        }

        List<String> keywords = items.stream()
                .map(KeywordItem::keyword)
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .distinct()
                .toList();
        if (keywords.isEmpty()) {
            return items;
        }

        Map<String, PopularSearchKeyword> metaByKeyword = new HashMap<>();
        for (PopularSearchKeyword entity : popularSearchKeywordRepository.findByKeywordIn(keywords)) {
            metaByKeyword.put(entity.getKeyword(), entity);
        }

        List<KeywordItem> enriched = new ArrayList<>();
        for (KeywordItem item : items) {
            PopularSearchKeyword meta = metaByKeyword.get(item.keyword());
            enriched.add(new KeywordItem(
                    item.keyword(),
                    item.searchCount(),
                    item.conversionRate(),
                    meta != null ? meta.getId() : null,
                    meta != null ? meta.getDisplayRank() : null,
                    meta != null ? meta.getManualPriority() : null,
                    meta != null ? meta.getIsExcluded() : null,
                    meta != null ? meta.getAdminNote() : null
            ));
        }
        return enriched;
    }

    /**
     * 기간별 검색 이력 키워드 통계를 반환한다.
     */
    public SearchHistoryKeywordsResponse getSearchHistoryKeywords(String period, int limit) {
        int days = parsePeriodDays(period);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Object[]> rows = searchHistoryRepository.findKeywordStatsSince(
                since,
                PageRequest.of(0, limit)
        );
        List<String> targetKeywords = rows.stream()
                .map(row -> row[0] != null ? String.valueOf(row[0]) : null)
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .distinct()
                .toList();
        Map<String, KeywordSessionStats> sessionStatsByKeyword = buildKeywordSessionStats(since, targetKeywords);

        List<SearchHistoryKeywordItem> keywords = rows.stream()
                .map(row -> {
                    String keyword = (String) row[0];
                    long searchCount = asLong(row[1]);
                    long resultCount = asLong(row[2]);
                    KeywordSessionStats sessionStats = sessionStatsByKeyword.get(keyword);
                    long sessionCount = sessionStats != null ? sessionStats.totalSessions() : 0L;
                    double conversionRate = 0.0;
                    if (sessionCount > 0) {
                        conversionRate = Math.round(
                                (double) sessionStats.clickedSessions() / sessionCount * 1000.0
                        ) / 1000.0;
                    }
                    return new SearchHistoryKeywordItem(
                            keyword,
                            searchCount,
                            sessionCount,
                            resultCount,
                            conversionRate
                    );
                })
                .toList();

        return new SearchHistoryKeywordsResponse(keywords);
    }

    private Map<String, KeywordSessionStats> buildKeywordSessionStats(
            LocalDateTime since,
            List<String> keywords
    ) {
        if (keywords.isEmpty()) {
            return Map.of();
        }

        List<SearchHistory> events = searchHistoryRepository.findSessionEventsSince(since, keywords);
        if (events.isEmpty()) {
            return Map.of();
        }

        Map<String, MutableKeywordSessionStats> statsByKeyword = new HashMap<>();
        Map<String, OpenKeywordSession> openSessions = new HashMap<>();

        for (SearchHistory event : events) {
            if (event.getUserId() == null || event.getUserId().isBlank()
                    || event.getKeyword() == null || event.getKeyword().isBlank()
                    || event.getSearchedAt() == null) {
                continue;
            }

            String sessionKey = buildSessionKey(event.getUserId(), event.getKeyword());
            OpenKeywordSession openSession = openSessions.get(sessionKey);

            if (openSession != null && isExpired(openSession.lastEventAt(), event.getSearchedAt())) {
                finalizeSession(statsByKeyword, openSession);
                openSessions.remove(sessionKey);
                openSession = null;
            }

            if (event.getClickedMovieId() == null || event.getClickedMovieId().isBlank()) {
                if (openSession == null) {
                    openSessions.put(sessionKey, new OpenKeywordSession(event.getKeyword(), event.getSearchedAt()));
                } else {
                    openSession.touch(event.getSearchedAt());
                }
                continue;
            }

            if (openSession != null) {
                openSession.markClicked(event.getSearchedAt());
            }
        }

        for (OpenKeywordSession openSession : openSessions.values()) {
            finalizeSession(statsByKeyword, openSession);
        }

        Map<String, KeywordSessionStats> immutableStatsByKeyword = new HashMap<>();
        for (Map.Entry<String, MutableKeywordSessionStats> entry : statsByKeyword.entrySet()) {
            MutableKeywordSessionStats stats = entry.getValue();
            immutableStatsByKeyword.put(
                    entry.getKey(),
                    new KeywordSessionStats(stats.totalSessions, stats.clickedSessions)
            );
        }
        return immutableStatsByKeyword;
    }

    private String buildSessionKey(String userId, String keyword) {
        return userId + '\u0000' + keyword;
    }

    private boolean isExpired(LocalDateTime lastEventAt, LocalDateTime currentEventAt) {
        return !currentEventAt.isBefore(lastEventAt.plusMinutes(SEARCH_SESSION_TIMEOUT_MINUTES));
    }

    private void finalizeSession(
            Map<String, MutableKeywordSessionStats> statsByKeyword,
            OpenKeywordSession openSession
    ) {
        MutableKeywordSessionStats stats = statsByKeyword.computeIfAbsent(
                openSession.keyword,
                ignored -> new MutableKeywordSessionStats()
        );
        stats.totalSessions++;
        if (openSession.clicked) {
            stats.clickedSessions++;
        }
    }

    private record KeywordSessionStats(long totalSessions, long clickedSessions) {}

    private static final class MutableKeywordSessionStats {
        private long totalSessions;
        private long clickedSessions;
    }

    private static final class OpenKeywordSession {
        private final String keyword;
        private LocalDateTime lastEventAt;
        private boolean clicked;

        private OpenKeywordSession(String keyword, LocalDateTime lastEventAt) {
            this.keyword = keyword;
            this.lastEventAt = lastEventAt;
            this.clicked = false;
        }

        private LocalDateTime lastEventAt() {
            return lastEventAt;
        }

        private void touch(LocalDateTime eventAt) {
            this.lastEventAt = eventAt;
        }

        private void markClicked(LocalDateTime eventAt) {
            this.clicked = true;
            this.lastEventAt = eventAt;
        }
    }

    /**
     * 기간별 특정 키워드의 클릭 영화 통계를 반환한다.
     */
    public SearchKeywordClicksResponse getSearchKeywordClicks(String keyword, String period, int limit) {
        String keywordCleaned = keyword != null ? keyword.trim() : "";
        if (keywordCleaned.isBlank()) {
            return new SearchKeywordClicksResponse(keywordCleaned, 0L, List.of());
        }

        int days = parsePeriodDays(period);
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = searchHistoryRepository.findClickedMoviesByKeywordSince(
                keywordCleaned,
                since,
                PageRequest.of(0, limit)
        );
        if (rows.isEmpty()) {
            return new SearchKeywordClicksResponse(keywordCleaned, 0L, List.of());
        }

        long totalClicks = rows.stream()
                .mapToLong(row -> asLong(row[1]))
                .sum();
        List<String> movieIds = rows.stream()
                .map(row -> row[0] != null ? String.valueOf(row[0]) : null)
                .filter(movieId -> movieId != null && !movieId.isBlank())
                .distinct()
                .toList();
        Map<String, String> movieTitleById = new HashMap<>();
        if (!movieIds.isEmpty()) {
            for (Movie movie : movieRepository.findAllByMovieIdIn(movieIds)) {
                movieTitleById.put(movie.getMovieId(), movie.getTitle());
            }
        }

        List<SearchKeywordClickItem> movies = rows.stream()
                .map(row -> {
                    String movieId = row[0] != null ? String.valueOf(row[0]) : null;
                    long clickCount = asLong(row[1]);
                    double clickRate = totalClicks > 0
                            ? Math.round((double) clickCount / totalClicks * 1000.0) / 1000.0
                            : 0.0;
                    return new SearchKeywordClickItem(
                            movieId,
                            movieId != null ? movieTitleById.get(movieId) : null,
                            clickCount,
                            clickRate
                    );
                })
                .toList();

        return new SearchKeywordClicksResponse(keywordCleaned, totalClicks, movies);
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
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
     * @param period 기간 문자열 ("1d", "7d", "30d" 등)
     * @return 검색 성공률, 전체 검색 수, 무결과 검색 수
     */
    public SearchQualityResponse getSearchQuality(String period) {
        int days = parsePeriodDays(period);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // 클릭 로그(clicked_movie_id IS NOT NULL)는 제외하고 실제 검색 이벤트만 집계한다.
        long totalSearches = searchHistoryRepository
                .countBySearchedAtGreaterThanEqualAndClickedMovieIdIsNull(since);

        // result_count = 0 인 무결과 검색 이력 수 (동일하게 클릭 로그 제외)
        long zeroResultCount = searchHistoryRepository
                .countByResultCountAndSearchedAtGreaterThanEqualAndClickedMovieIdIsNull(0, since);

        // 검색 성공률: 결과가 1건 이상인 검색 / 전체 검색 (소수점 3자리 반올림)
        double successRate = totalSearches > 0
                ? Math.round((double) (totalSearches - zeroResultCount) / totalSearches * 1000.0) / 1000.0
                : 0.0;

        log.debug("[admin-stats] 검색 품질 — period={}, total={}, zeroResult={}, successRate={}",
                period, totalSearches, zeroResultCount, successRate);
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
     * 매출 현황을 반환한다 (확장 — 2026-04-28).
     *
     * <p>Frontend RevenueTab 가 요구하는 모든 KPI/차트 데이터를 한 번에 응답한다.
     * 기간 파라미터는 일별 추이/시간대/요일/Top payer/결제수단/플랜 분포의 윈도우 크기를 결정한다.
     * 월 매출/MRR/오늘/어제/이번주/12개월 추이는 기간과 무관하게 항상 계산한다.</p>
     *
     * <h4>집계 정의</h4>
     * <ul>
     *   <li>monthlyRevenue: 이번 달 1일 ~ 현재 COMPLETED 합산</li>
     *   <li>mrr: 활성 구독의 월 환산 가격 합계 (subscriptionMrr 와 동일)</li>
     *   <li>arpu: 선택 기간 매출 / 결제 고유 사용자 수 (없으면 0)</li>
     *   <li>avgOrderValue: 선택 기간 매출 / 결제 건수 (없으면 0)</li>
     *   <li>refundRate: 이번 달 환불액 / 이번 달 매출 (0~1)</li>
     *   <li>netRevenue: monthlyRevenue - 이번 달 환불액 (음수 가능 시 0 클램프)</li>
     * </ul>
     *
     * @param period 기간 문자열 ("7d", "30d", "90d")
     * @return 매출 종합 응답
     */
    public RevenueResponse getRevenue(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime periodStart = today.minusDays(days - 1L).atStartOfDay();

        /* ── 1. 핵심 KPI ── */

        // 이번 달 누적 매출 (COMPLETED)
        long monthlyRevenue = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, monthStart, now));

        // 누적 총 매출
        long totalRevenue = nz(paymentOrderRepository.sumAmountByStatus(PaymentOrder.OrderStatus.COMPLETED));

        // 오늘 / 어제 / 이번주(월요일~) 매출
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays((today.getDayOfWeek().getValue() - 1L)).atStartOfDay();

        long todayRevenue = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, todayStart, tomorrowStart));
        long yesterdayRevenue = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, yesterdayStart, todayStart));
        long weekRevenue = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, weekStart, now));

        // 선택 기간 매출 / 건수 / 결제 고유 사용자 수 → ARPU·객단가 분모
        long periodRevenue = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, periodStart, now));
        long periodOrders = paymentOrderRepository.countByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, periodStart, now);
        long payingUsers = paymentOrderRepository.findDistinctPayerCountAfter(
                PaymentOrder.OrderStatus.COMPLETED, periodStart);

        long arpu = payingUsers > 0 ? periodRevenue / payingUsers : 0L;
        long avgOrderValue = periodOrders > 0 ? periodRevenue / periodOrders : 0L;

        // 오늘 결제 건수
        long todayOrders = paymentOrderRepository.countByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, todayStart, tomorrowStart);

        /* ── 2. 환불 통계 (이번 달 기준) ── */
        long refundAmount = nz(paymentOrderRepository.sumRefundAmountAfter(monthStart));
        long refundCount = paymentOrderRepository.countRefundedAfter(monthStart);
        double refundRate = monthlyRevenue > 0
                ? Math.round((double) refundAmount / monthlyRevenue * 1000.0) / 1000.0
                : 0.0;
        long netRevenue = Math.max(0L, monthlyRevenue - refundAmount);

        /* ── 3. MRR (활성 구독 월 환산) ── */
        long mrr = computeSubscriptionMrr();

        /* ── 4. 일별 추이 (선택 기간) ── */
        List<DailyRevenue> dailyRevenue = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            long amount = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                    PaymentOrder.OrderStatus.COMPLETED, dayStart, dayEnd));
            long count = paymentOrderRepository.countByStatusAndCreatedAtBetween(
                    PaymentOrder.OrderStatus.COMPLETED, dayStart, dayEnd);
            dailyRevenue.add(new DailyRevenue(date.format(DATE_FMT), amount, count));
        }

        /* ── 5. 월별 추이 (최근 12개월, 이번 달 포함) ── */
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<MonthlyRevenue> monthlyTrend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            LocalDate firstOfMonth = today.withDayOfMonth(1).minusMonths(i);
            LocalDateTime ms = firstOfMonth.atStartOfDay();
            LocalDateTime me = firstOfMonth.plusMonths(1).atStartOfDay();
            long amount = nz(paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                    PaymentOrder.OrderStatus.COMPLETED, ms, me));
            long count = paymentOrderRepository.countByStatusAndCreatedAtBetween(
                    PaymentOrder.OrderStatus.COMPLETED, ms, me);
            monthlyTrend.add(new MonthlyRevenue(firstOfMonth.format(monthFmt), amount, count));
        }

        /* ── 6. 결제 수단 분포 (선택 기간) ── */
        List<PaymentMethodItem> methodDist = buildPaymentMethodDistribution(periodStart, periodRevenue);

        /* ── 7. 플랜별 매출 (선택 기간) ── */
        List<PlanRevenueItem> planRevenue = buildPlanRevenueDistribution(periodStart);

        /* ── 8. 주문 유형 분포 (POINT_PACK vs SUBSCRIPTION) ── */
        List<OrderTypeItem> orderTypeDist = buildOrderTypeDistribution(periodStart, periodRevenue);

        /* ── 9. 시간대별 분포 (24시간) ── */
        List<HourlyRevenueItem> hourlyDist = buildHourlyDistribution(periodStart);

        /* ── 10. 요일별 분포 (월~일) ── */
        List<WeekdayRevenueItem> weekdayDist = buildWeekdayDistribution(periodStart);

        /* ── 11. Top 10 결제 사용자 ── */
        List<TopPayerItem> topPayers = buildTopPayers(periodStart, 10);

        log.debug("[admin-stats] 매출 조회 — 월={}, MRR={}, ARPU={}, 환불={}, 기간={}일",
                monthlyRevenue, mrr, arpu, refundAmount, days);

        return new RevenueResponse(
                monthlyRevenue, mrr, arpu, avgOrderValue, totalRevenue,
                todayRevenue, yesterdayRevenue, weekRevenue,
                periodOrders, todayOrders, payingUsers,
                refundAmount, refundCount, refundRate, netRevenue,
                dailyRevenue, monthlyTrend,
                methodDist, planRevenue, orderTypeDist,
                hourlyDist, weekdayDist, topPayers
        );
    }

    /** null-safe Long 언래핑 — Repository SUM 이 0건일 때 null 반환 처리. */
    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    /**
     * 활성 구독 기준 MRR 을 계산한다.
     *
     * <p>월간 플랜은 price 그대로, 연간 플랜은 price/12 로 환산하여 모두 합산.</p>
     */
    private long computeSubscriptionMrr() {
        List<Object[]> rows = userSubscriptionRepository.sumMrrByPlan();
        long mrr = 0L;
        for (Object[] row : rows) {
            // [planCode, planName, periodType, price, count]
            Object periodTypeObj = row[2];
            int price = ((Number) row[3]).intValue();
            long count = ((Number) row[4]).longValue();
            // PeriodType enum 의 toString — YEARLY 만 12 분할
            boolean yearly = periodTypeObj != null && "YEARLY".equals(periodTypeObj.toString());
            long monthly = yearly ? Math.round(price / 12.0) : price;
            mrr += monthly * count;
        }
        return mrr;
    }

    /** 결제 수단 코드 → 한국어 표시명 매핑 */
    private static String mapProviderLabel(String code) {
        if (code == null || code.isBlank()) return "미지정";
        return switch (code.toLowerCase()) {
            case "toss", "tosspayments" -> "토스페이먼츠";
            case "kakao", "kakaopay"    -> "카카오페이";
            case "naver", "naverpay"    -> "네이버페이";
            case "card"                 -> "신용카드";
            case "transfer"             -> "계좌이체";
            case "vbank"                -> "가상계좌";
            default                     -> code;
        };
    }

    /** 결제 수단 분포 목록을 빌드한다. */
    private List<PaymentMethodItem> buildPaymentMethodDistribution(LocalDateTime periodStart, long periodRevenue) {
        List<Object[]> rows = paymentOrderRepository.sumAndCountGroupByProviderAfter(
                PaymentOrder.OrderStatus.COMPLETED, periodStart);
        List<PaymentMethodItem> out = new ArrayList<>();
        for (Object[] row : rows) {
            String provider = row[0] != null ? String.valueOf(row[0]) : "unknown";
            long amount = nz(((Number) row[1]).longValue());
            long count = ((Number) row[2]).longValue();
            double ratio = periodRevenue > 0
                    ? Math.round((double) amount / periodRevenue * 1000.0) / 1000.0
                    : 0.0;
            out.add(new PaymentMethodItem(provider, mapProviderLabel(provider), amount, count, ratio));
        }
        return out;
    }

    /** 플랜별 매출 목록을 빌드한다 (구독 매출 한정). */
    private List<PlanRevenueItem> buildPlanRevenueDistribution(LocalDateTime periodStart) {
        List<Object[]> rows = paymentOrderRepository.sumAndCountGroupByPlanAfter(periodStart);
        long total = rows.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();
        List<PlanRevenueItem> out = new ArrayList<>();
        for (Object[] row : rows) {
            String code = String.valueOf(row[0]);
            String name = row[1] != null ? String.valueOf(row[1]) : code;
            long amount = ((Number) row[2]).longValue();
            long count = ((Number) row[3]).longValue();
            double ratio = total > 0
                    ? Math.round((double) amount / total * 1000.0) / 1000.0
                    : 0.0;
            out.add(new PlanRevenueItem(code, name, amount, count, ratio));
        }
        return out;
    }

    /** 주문 유형 분포(POINT_PACK vs SUBSCRIPTION). */
    private List<OrderTypeItem> buildOrderTypeDistribution(LocalDateTime periodStart, long periodRevenue) {
        List<Object[]> rows = paymentOrderRepository.sumAndCountGroupByOrderTypeAfter(
                PaymentOrder.OrderStatus.COMPLETED, periodStart);
        List<OrderTypeItem> out = new ArrayList<>();
        for (Object[] row : rows) {
            String type = row[0] != null ? row[0].toString() : "UNKNOWN";
            long amount = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            double ratio = periodRevenue > 0
                    ? Math.round((double) amount / periodRevenue * 1000.0) / 1000.0
                    : 0.0;
            String label = switch (type) {
                case "POINT_PACK"   -> "포인트팩";
                case "SUBSCRIPTION" -> "구독";
                default             -> type;
            };
            out.add(new OrderTypeItem(type, label, amount, count, ratio));
        }
        return out;
    }

    /** 시간대별(24시간) 결제 분포. 빈 시간은 0/0 으로 채움. */
    private List<HourlyRevenueItem> buildHourlyDistribution(LocalDateTime periodStart) {
        List<Object[]> rows = paymentOrderRepository.sumAndCountGroupByHourAfter(
                PaymentOrder.OrderStatus.COMPLETED, periodStart);
        Map<Integer, long[]> bucket = new HashMap<>();
        for (Object[] row : rows) {
            int hour = ((Number) row[0]).intValue();
            long amount = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            bucket.put(hour, new long[]{amount, count});
        }
        List<HourlyRevenueItem> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            long[] v = bucket.getOrDefault(h, new long[]{0L, 0L});
            out.add(new HourlyRevenueItem(h, v[0], v[1]));
        }
        return out;
    }

    /** 요일별 결제 분포. MySQL DAYOFWEEK(1=일~7=토) → 표시는 월~일 순. */
    private List<WeekdayRevenueItem> buildWeekdayDistribution(LocalDateTime periodStart) {
        List<Object[]> rows = paymentOrderRepository.sumAndCountGroupByWeekdayAfter(
                PaymentOrder.OrderStatus.COMPLETED, periodStart);
        // mysqlDow(1=일,2=월,...,7=토) → out 인덱스(1=월,...,7=일) 변환
        Map<Integer, long[]> bucket = new HashMap<>();
        for (Object[] row : rows) {
            int mysqlDow = ((Number) row[0]).intValue();
            int monIdx = (mysqlDow == 1) ? 7 : mysqlDow - 1; // 일=7, 나머지는 -1
            long amount = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            bucket.put(monIdx, new long[]{amount, count});
        }
        String[] names = {"월", "화", "수", "목", "금", "토", "일"};
        List<WeekdayRevenueItem> out = new ArrayList<>(7);
        for (int i = 1; i <= 7; i++) {
            long[] v = bucket.getOrDefault(i, new long[]{0L, 0L});
            out.add(new WeekdayRevenueItem(i, names[i - 1], v[0], v[1]));
        }
        return out;
    }

    /** Top N 결제 사용자. 닉네임은 UserMapper.findNicknameById 로 조회. */
    private List<TopPayerItem> buildTopPayers(LocalDateTime periodStart, int limit) {
        List<Object[]> rows = paymentOrderRepository.findTopPayersAfter(
                periodStart, PageRequest.of(0, limit));
        List<TopPayerItem> out = new ArrayList<>();
        for (Object[] row : rows) {
            String userId = String.valueOf(row[0]);
            long amount = ((Number) row[1]).longValue();
            long cnt = ((Number) row[2]).longValue();
            // null-safe 닉네임 — 미설정 시 userId 앞 8자
            String nickname = userMapper.findNicknameById(userId);
            if (nickname == null || nickname.isBlank()) {
                nickname = userId.length() > 8 ? userId.substring(0, 8) : userId;
            }
            out.add(new TopPayerItem(userId, nickname, amount, cnt));
        }
        return out;
    }

    // ──────────────────────────────────────────────
    // 8. 구독 통계
    // ──────────────────────────────────────────────

    /**
     * 구독 현황 통계를 반환한다 (확장 — 2026-04-28).
     *
     * <p>Frontend 기대 키와 정렬: activeSubscriptions / planDistribution / churnRate(0~1).
     * 신규/취소/만료 이번 달 카운트, 활성 구독 MRR, 1인당 평균 매출, 플랜별 MRR 추가.</p>
     *
     * @return 구독 종합 응답
     */
    public SubscriptionStatsResponse getSubscriptionStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        /* 활성/취소/만료 카운트 */
        long activeSubscriptions = userSubscriptionRepository.countByStatus(UserSubscription.Status.ACTIVE);
        long cancelled = userSubscriptionRepository.countByStatus(UserSubscription.Status.CANCELLED);
        long expired = userSubscriptionRepository.countByStatus(UserSubscription.Status.EXPIRED);
        long totalSubscriptions = activeSubscriptions + cancelled + expired;

        /* 이탈률: 누적 취소+만료 / 누적 전체 (0.0~1.0 비율, Frontend 가 *100 포맷) */
        double churnRate = totalSubscriptions > 0
                ? Math.round((double) (cancelled + expired) / totalSubscriptions * 1000.0) / 1000.0
                : 0.0;

        /* 이번 달 신규/취소 — 별도 컬럼 기반 정확한 카운트 */
        long newThisMonth = userSubscriptionRepository.countByCreatedAtBetween(monthStart, nextMonthStart);
        long cancelledThisMonth = userSubscriptionRepository.countCancelledBetween(monthStart, nextMonthStart);
        // 만료(미갱신) 추정: cutoff 를 이번 달 시작 시점으로 사용
        long expiredThisMonth = userSubscriptionRepository.countExpiredWithoutRenewal(monthStart);

        /* MRR 계산 (활성 구독 월 환산) + 플랜별 MRR */
        long subscriptionMrr = 0L;
        List<Object[]> mrrRows = userSubscriptionRepository.sumMrrByPlan();
        // 1차 패스: 총 MRR 산출
        long[] perPlanMrr = new long[mrrRows.size()];
        int idx = 0;
        for (Object[] row : mrrRows) {
            int price = ((Number) row[3]).intValue();
            long count = ((Number) row[4]).longValue();
            boolean yearly = row[2] != null && "YEARLY".equals(row[2].toString());
            long monthly = yearly ? Math.round(price / 12.0) : price;
            long planMrrAmount = monthly * count;
            perPlanMrr[idx++] = planMrrAmount;
            subscriptionMrr += planMrrAmount;
        }
        // 2차 패스: PlanMrrItem 변환 + ratio
        List<PlanMrrItem> planMrr = new ArrayList<>();
        idx = 0;
        for (Object[] row : mrrRows) {
            String code = row[0] != null ? String.valueOf(row[0]) : "unknown";
            String name = row[1] != null ? String.valueOf(row[1]) : code;
            long count = ((Number) row[4]).longValue();
            long mrrAmount = perPlanMrr[idx++];
            double ratio = subscriptionMrr > 0
                    ? Math.round((double) mrrAmount / subscriptionMrr * 1000.0) / 1000.0
                    : 0.0;
            planMrr.add(new PlanMrrItem(code, mapPlanLabel(code, name), mrrAmount, count, ratio));
        }
        long avgRevenuePerSubscriber = activeSubscriptions > 0 ? subscriptionMrr / activeSubscriptions : 0L;

        /* 플랜별 가입자 분포 (Frontend 호환 — plan/ratio 키) */
        List<Object[]> planRows = userSubscriptionRepository.countActiveByPlanType();
        List<PlanDistribution> planDistribution = new ArrayList<>();
        for (Object[] row : planRows) {
            String planCode = row[0] != null ? String.valueOf(row[0]) : "unknown";
            long count = ((Number) row[1]).longValue();
            double ratio = activeSubscriptions > 0
                    ? Math.round((double) count / activeSubscriptions * 1000.0) / 1000.0
                    : 0.0;
            planDistribution.add(new PlanDistribution(planCode, mapPlanLabel(planCode, null), count, ratio));
        }

        log.debug("[admin-stats] 구독 통계 — 활성={}, 이탈률={}, MRR={}",
                activeSubscriptions, churnRate, subscriptionMrr);

        return new SubscriptionStatsResponse(
                activeSubscriptions, totalSubscriptions,
                newThisMonth, cancelledThisMonth, expiredThisMonth,
                churnRate, subscriptionMrr, avgRevenuePerSubscriber,
                planDistribution, planMrr
        );
    }

    /** 플랜 코드 → 한국어 표시명. fallback 으로 DB의 name 또는 code 사용. */
    private static String mapPlanLabel(String code, String fallback) {
        return switch (code) {
            case "monthly_basic"   -> "베이직 월간";
            case "monthly_premium" -> "프리미엄 월간";
            case "yearly_basic"    -> "베이직 연간";
            case "yearly_premium"  -> "프리미엄 연간";
            default -> (fallback != null && !fallback.isBlank()) ? fallback : code;
        };
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
     * <p>earn/spend/bonus/expire/refund/revoke + admin_grant/admin_revoke 유형별
     * 건수와 포인트 합계.</p>
     *
     * <p><b>2026-04-28 변경</b> — 관리자 수동 지급/회수가 정상 보너스/회수와 섞여
     * 통계 분포가 왜곡되던 문제를 해결하기 위해 admin_grant/admin_revoke 라벨을
     * "운영 지급"/"운영 회수" 로 별도 표시한다. 클라이언트(PointEconomyTab) 차트는
     * 두 카테고리를 동일 색상 그룹으로 묶어 "운영 조정" 으로 강조할 수 있다.</p>
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
                        case "earn"         -> "활동 리워드";
                        case "spend"        -> "포인트 사용";
                        case "bonus"        -> "보너스";
                        case "expire"       -> "만료";
                        case "refund"       -> "환불";
                        case "revoke"       -> "회수";
                        case "admin_grant"  -> "운영 지급";   // 2026-04-28 — 운영 조정 분리
                        case "admin_revoke" -> "운영 회수";   // 2026-04-28 — 운영 조정 분리
                        default             -> type;
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

    // ──────────────────────────────────────────────
    // 10-V2. AI 서비스 분석 — 전면 재설계 (2026-04-29)
    //
    // 운영자가 (a) 한눈에 오늘 AI 호출 건강도를 파악하고,
    // (b) 4개 에이전트(챗·추천·고객센터·퀴즈) 를 분리해서 보고,
    // (c) 응답 성능·CTR·자동화율 같은 비즈니스 지표를 즉시 얻기 위한 9개 신규 메서드.
    //
    // - 모든 시각 계산은 KST(Asia/Seoul) 기준 LocalDate 사용 → 서버 TZ 의존성 제거.
    // - 발생량 통계(소프트 삭제 무시)는 신규 countAllSessions / sumAllTurns 메서드 사용.
    // - 등급별 차등 쿼터는 native 쿼리 aggregateQuotaByGrade / countExhaustedUsersByGrade 사용.
    // ──────────────────────────────────────────────

    /** AI 서비스 통계 KST 시간대 — LocalDate.now(KST) 일관성 보장 (서버 TZ 무관). */
    private static final ZoneId AI_STATS_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * AI 서비스 요약 KPI — "오늘 한눈에" 4개 핵심 지표.
     *
     * <p>화면 상단 카드 4개에 직결되는 KPI. 운영자가 오늘의 AI 호출 건강도를 즉시 파악한다.
     * 4 에이전트 합산 호출 + 전일 대비 + 평균 응답시간(7d) + CTR(30d) + 자동화율(30d).</p>
     *
     * @return AI 요약 응답 (todayCalls, dayOverDayPct, avgLatencyMs, recommendCtr, supportAutomationRate, activeUsers)
     */
    public AiSummaryResponse getAiSummary() {
        LocalDate today = LocalDate.now(AI_STATS_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();

        /* 오늘 4 에이전트 합산 호출량 */
        long chatTurnsToday = adminChatSessionRepository.sumTurnsByCreatedAtBetween(todayStart, tomorrowStart);
        long recommendToday = recommendationLogRepository.countByCreatedAtBetween(todayStart, tomorrowStart);
        long supportToday = supportChatLogRepository.countByCreatedAtBetween(todayStart, tomorrowStart);
        long quizToday = quizParticipationRepository.countBySubmittedAtBetween(todayStart, tomorrowStart);
        long todayCalls = chatTurnsToday + recommendToday + supportToday + quizToday;

        /* 어제 4 에이전트 합산 — 전일 대비 비교 */
        long chatTurnsYday = adminChatSessionRepository.sumTurnsByCreatedAtBetween(yesterdayStart, todayStart);
        long recommendYday = recommendationLogRepository.countByCreatedAtBetween(yesterdayStart, todayStart);
        long supportYday = supportChatLogRepository.countByCreatedAtBetween(yesterdayStart, todayStart);
        long quizYday = quizParticipationRepository.countBySubmittedAtBetween(yesterdayStart, todayStart);
        long yesterdayCalls = chatTurnsYday + recommendYday + supportYday + quizYday;

        /* 전일 대비 % — 어제 0 이면 0% (직접 비교 불가) */
        double dayOverDayPct = yesterdayCalls > 0
                ? Math.round((double) (todayCalls - yesterdayCalls) / yesterdayCalls * 1000.0) / 10.0
                : 0.0;

        /* 추천 엔진 평균 응답시간 — 최근 7일 (NULL 제외 평균) */
        LocalDateTime sevenDaysAgo = todayStart.minusDays(7);
        Double avgLatency = recommendationLogRepository.findAverageLatency(sevenDaysAgo, tomorrowStart);
        double avgLatencyMs = avgLatency != null ? Math.round(avgLatency * 10.0) / 10.0 : 0.0;

        /* 추천 CTR — 최근 30일 (clicked / total * 100) */
        LocalDateTime thirtyDaysAgo = todayStart.minusDays(30);
        long recommend30d = recommendationLogRepository.countByCreatedAtBetween(thirtyDaysAgo, tomorrowStart);
        long clicked30d = recommendationLogRepository.countClickedByCreatedAtBetween(thirtyDaysAgo, tomorrowStart);
        double recommendCtr = recommend30d > 0
                ? Math.round((double) clicked30d / recommend30d * 1000.0) / 10.0
                : 0.0;

        /* 고객센터 자동화율 — 최근 30일 (1 - needs_human 비율) */
        List<Object[]> needsHumanList = supportChatLogRepository.needsHumanRatio(thirtyDaysAgo, tomorrowStart);
        Object[] needsHumanRow = needsHumanList != null && !needsHumanList.isEmpty() ? needsHumanList.get(0) : null;
        long supportNeedsHuman = needsHumanRow != null && needsHumanRow.length >= 1 && needsHumanRow[0] != null
                ? ((Number) needsHumanRow[0]).longValue() : 0L;
        long supportTotal = needsHumanRow != null && needsHumanRow.length >= 2 && needsHumanRow[1] != null
                ? ((Number) needsHumanRow[1]).longValue() : 0L;
        double supportAutomationRate = supportTotal > 0
                ? Math.round((1.0 - (double) supportNeedsHuman / supportTotal) * 1000.0) / 10.0
                : 0.0;

        /* 오늘 활성 사용자 — 채팅 세션 시작자 distinct (대표 지표) */
        long activeUsers = adminChatSessionRepository.countDistinctUserByCreatedAtBetween(todayStart, tomorrowStart);

        log.debug("[admin-stats] AI 요약 — 오늘={}, 전일대비={}%, 응답={}ms, CTR={}%, 자동화={}%, 활성={}",
                todayCalls, dayOverDayPct, avgLatencyMs, recommendCtr, supportAutomationRate, activeUsers);
        return new AiSummaryResponse(todayCalls, dayOverDayPct, avgLatencyMs,
                recommendCtr, supportAutomationRate, activeUsers);
    }

    /**
     * 에이전트별 호출량 일별 추이 — 멀티 라인 차트.
     *
     * @param period 기간 ("7d", "30d", "90d")
     * @return DailyAgentTrend 시계열
     */
    public AgentTrendsResponse getAgentTrends(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now(AI_STATS_ZONE);
        List<DailyAgentTrend> trends = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            long chatSessions = adminChatSessionRepository.countSessionsByCreatedAtBetween(dayStart, dayEnd);
            long chatTurns = adminChatSessionRepository.sumTurnsByCreatedAtBetween(dayStart, dayEnd);
            long recommendCount = recommendationLogRepository.countByCreatedAtBetween(dayStart, dayEnd);
            long supportSessions = supportChatLogRepository.countByCreatedAtBetween(dayStart, dayEnd);
            long quizAttempts = quizParticipationRepository.countBySubmittedAtBetween(dayStart, dayEnd);

            trends.add(new DailyAgentTrend(
                    date.format(DATE_FMT),
                    chatSessions, chatTurns, recommendCount, supportSessions, quizAttempts
            ));
        }

        log.debug("[admin-stats] 에이전트 추이 — {}일", days);
        return new AgentTrendsResponse(trends);
    }

    /**
     * 에이전트별 KPI 요약 — 4개 카드 (챗·추천·고객센터·퀴즈).
     *
     * @return AgentSummaryResponse (chat, recommend, support, quiz)
     */
    public AgentSummaryResponse getAgentSummary() {
        LocalDate today = LocalDate.now(AI_STATS_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        /* ── 챗 에이전트 KPI ── */
        long chatTotalSessions = adminChatSessionRepository.countAllSessions();
        long chatTotalTurns = adminChatSessionRepository.sumAllTurns();
        double chatAvgTurns = chatTotalSessions > 0
                ? Math.round((double) chatTotalTurns / chatTotalSessions * 10.0) / 10.0
                : 0.0;
        long chatTodaySessions = adminChatSessionRepository.countSessionsByCreatedAtBetween(todayStart, tomorrowStart);
        String chatTopIntent = computeChatTopIntent();
        ChatStat chatStat = new ChatStat(chatTotalSessions, chatAvgTurns, chatTodaySessions, chatTopIntent);

        /* ── 추천 엔진 KPI ── */
        long recommendTotal = recommendationLogRepository.count();
        long recommendClicked = recommendationLogRepository.countByClickedTrue();
        long recommendExternal = recommendationLogRepository.countBySourceType("EXTERNAL_DDGS");
        double recommendCtr = recommendTotal > 0
                ? Math.round((double) recommendClicked / recommendTotal * 1000.0) / 10.0
                : 0.0;
        double externalRatio = recommendTotal > 0
                ? Math.round((double) recommendExternal / recommendTotal * 1000.0) / 10.0
                : 0.0;
        Double avgScoreRaw = recommendationLogRepository.findAverageScore();
        double avgScore = avgScoreRaw != null ? Math.round(avgScoreRaw * 1000.0) / 1000.0 : 0.0;
        RecommendStat recommendStat = new RecommendStat(recommendTotal, recommendCtr, externalRatio, avgScore);

        /* ── 고객센터 챗봇 KPI ── */
        long supportTotal = supportChatLogRepository.count();
        List<Object[]> supportRatioList = supportChatLogRepository.needsHumanRatio(null, null);
        Object[] supportRatioRow = supportRatioList != null && !supportRatioList.isEmpty() ? supportRatioList.get(0) : null;
        long supportNeedsHuman = supportRatioRow != null && supportRatioRow.length >= 1 && supportRatioRow[0] != null
                ? ((Number) supportRatioRow[0]).longValue() : 0L;
        double escalationRate = supportTotal > 0
                ? Math.round((double) supportNeedsHuman / supportTotal * 1000.0) / 10.0
                : 0.0;
        String supportTopIntent = computeSupportTopIntent();
        Double avgHopRaw = supportChatLogRepository.findAverageHopCount();
        double avgHop = avgHopRaw != null ? Math.round(avgHopRaw * 100.0) / 100.0 : 0.0;
        SupportStat supportStat = new SupportStat(supportTotal, escalationRate, supportTopIntent, avgHop);

        /* ── 퀴즈 KPI ── */
        long quizTotal = quizParticipationRepository.count();
        long quizCorrect = quizParticipationRepository.countCorrect();
        double correctRate = quizTotal > 0
                ? Math.round((double) quizCorrect / quizTotal * 1000.0) / 10.0
                : 0.0;
        long quizToday = quizParticipationRepository.countBySubmittedAtBetween(todayStart, tomorrowStart);
        QuizStat quizStat = new QuizStat(quizTotal, correctRate, quizToday);

        log.debug("[admin-stats] 에이전트 요약 — 챗={}/추천={}/고객센터={}/퀴즈={}",
                chatTotalSessions, recommendTotal, supportTotal, quizTotal);
        return new AgentSummaryResponse(chatStat, recommendStat, supportStat, quizStat);
    }

    /**
     * 챗 의도 분포 1위 라벨을 계산한다 (페이지네이션으로 의도 합산).
     *
     * @return 1위 의도 한글 라벨 (없으면 "-")
     */
    private String computeChatTopIntent() {
        Page<String> page = adminChatSessionRepository.findIntentSummariesPaged(
                PageRequest.of(0, 10000));
        Map<String, Long> counts = parseIntentSummaries(page.getContent());
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> chatIntentLabel(e.getKey()))
                .orElse("-");
    }

    /**
     * 고객센터 의도 분포 1위 라벨을 계산한다.
     *
     * @return 1위 의도 한글 라벨 (없으면 "-")
     */
    private String computeSupportTopIntent() {
        List<Object[]> rows = supportChatLogRepository.countByIntent(null, null);
        if (rows.isEmpty()) return "-";
        Object[] top = rows.get(0);
        String intentKind = top[0] != null ? top[0].toString() : null;
        return supportIntentLabel(intentKind);
    }

    /**
     * 챗 intent 코드 → 한글 라벨.
     */
    private String chatIntentLabel(String intent) {
        return switch (intent == null ? "" : intent) {
            case "recommend" -> "영화 추천";
            case "search"    -> "영화 검색";
            case "general"   -> "일반 대화";
            case "relation"  -> "관계 탐색";
            case "info"      -> "영화 정보";
            case "theater"   -> "상영관 조회";
            case "booking"   -> "예매 안내";
            case ""          -> "-";
            default          -> intent;
        };
    }

    /**
     * 고객센터 intent 코드 → 한글 라벨 (v4 6종 + unknown).
     */
    private String supportIntentLabel(String intent) {
        return switch (intent == null ? "" : intent) {
            case "faq"           -> "자주 묻는 질문";
            case "personal_data" -> "개인 정보 조회";
            case "policy"        -> "정책 안내";
            case "redirect"      -> "1:1 유도";
            case "smalltalk"     -> "일상 대화";
            case "complaint"     -> "불만/항의";
            case "unknown"       -> "분류 실패";
            case ""              -> "-";
            default              -> intent;
        };
    }

    /**
     * intent_summary JSON 리스트 → 의도별 카운트 합산 맵.
     */
    private Map<String, Long> parseIntentSummaries(List<String> summaries) {
        Map<String, Long> counts = new HashMap<>();
        for (String json : summaries) {
            if (json == null || json.isBlank()) continue;
            try {
                JsonNode root = STATS_OBJECT_MAPPER.readTree(json);
                if (root.isObject()) {
                    for (var entry : root.properties()) {
                        String intent = entry.getKey().trim();
                        long count = entry.getValue().asLong(0);
                        if (!intent.isEmpty() && count > 0) {
                            counts.merge(intent, count, Long::sum);
                        }
                    }
                }
            } catch (Exception e) {
                log.trace("[admin-stats] intentSummary 파싱 실패: {}", json);
            }
        }
        return counts;
    }

    /**
     * 응답 시간 분포 — p50/p95/p99 + 일별 시계열.
     *
     * @param period 기간 ("7d" 권장 — 1만건 제한)
     * @return LatencyResponse (p50, p95, p99, daily)
     */
    public LatencyResponse getLatencyDistribution(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now(AI_STATS_ZONE);
        LocalDateTime windowStart = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        /* 전체 기간 raw 응답시간 → 정렬해 percentile 인덱싱 (운영 규모 시 1만건 LIMIT 권장) */
        List<Integer> latencies = recommendationLogRepository.findLatenciesByCreatedAtBetween(windowStart, windowEnd);
        List<Integer> sorted = latencies.stream().filter(java.util.Objects::nonNull).sorted().toList();
        int p50 = percentile(sorted, 50);
        int p95 = percentile(sorted, 95);
        int p99 = percentile(sorted, 99);

        /* 일별 p50/p95 */
        List<DailyLatency> daily = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            List<Integer> dayList = recommendationLogRepository
                    .findLatenciesByCreatedAtBetween(dayStart, dayEnd)
                    .stream().filter(java.util.Objects::nonNull).sorted().toList();
            daily.add(new DailyLatency(date.format(DATE_FMT), percentile(dayList, 50), percentile(dayList, 95)));
        }

        log.debug("[admin-stats] 응답시간 — p50={}, p95={}, p99={}, 표본={}",
                p50, p95, p99, sorted.size());
        return new LatencyResponse(p50, p95, p99, daily);
    }

    /**
     * 정렬된 리스트에서 percentile 추출.
     *
     * @param sorted 오름차순 정렬된 리스트
     * @param pct    백분위 (0~100)
     * @return percentile 값 (빈 리스트면 0)
     */
    private int percentile(List<Integer> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(sorted.size() * pct / 100.0) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return sorted.get(idx);
    }

    /**
     * 모델 버전별 비교 — recommendation_log GROUP BY model_version.
     *
     * @return ModelComparisonResponse (호출수 내림차순)
     */
    public ModelComparisonResponse getModelComparison() {
        List<Object[]> rows = recommendationLogRepository.aggregateByModelVersion();
        List<ModelStat> models = new ArrayList<>();
        for (Object[] row : rows) {
            String modelVersion = row[0] != null ? row[0].toString() : "unknown";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            double avgScore = row[2] != null ? Math.round(((Number) row[2]).doubleValue() * 1000.0) / 1000.0 : 0.0;
            double avgLatency = row[3] != null ? Math.round(((Number) row[3]).doubleValue() * 10.0) / 10.0 : 0.0;
            long clicked = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            double ctr = count > 0 ? Math.round((double) clicked / count * 1000.0) / 10.0 : 0.0;
            models.add(new ModelStat(modelVersion, count, avgScore, avgLatency, ctr));
        }
        log.debug("[admin-stats] 모델 비교 — {} 모델", models.size());
        return new ModelComparisonResponse(models);
    }

    /**
     * 추천 펀넬 — 5단계 (recommendation_impact 기반).
     *
     * @param period 기간 ("30d" 권장)
     * @return RecommendationFunnelResponse
     */
    public RecommendationFunnelResponse getRecommendationFunnel(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now(AI_STATS_ZONE);
        LocalDateTime start = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        List<Object[]> funnelList = recommendationImpactRepository.aggregateFunnel(start, end);
        Object[] row = funnelList != null && !funnelList.isEmpty() ? funnelList.get(0) : null;
        long recommended = row != null && row.length >= 1 && row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long clicked = row != null && row.length >= 2 && row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long viewedDetail = row != null && row.length >= 3 && row[2] != null ? ((Number) row[2]).longValue() : 0L;
        long wishlisted = row != null && row.length >= 4 && row[3] != null ? ((Number) row[3]).longValue() : 0L;
        long watched = row != null && row.length >= 5 && row[4] != null ? ((Number) row[4]).longValue() : 0L;
        long rated = row != null && row.length >= 6 && row[5] != null ? ((Number) row[5]).longValue() : 0L;

        double ctr = recommended > 0 ? Math.round((double) clicked / recommended * 1000.0) / 10.0 : 0.0;
        double watchRate = recommended > 0 ? Math.round((double) watched / recommended * 1000.0) / 10.0 : 0.0;

        log.debug("[admin-stats] 추천 펀넬({}일) — 추천={}/클릭={}/상세={}/찜={}/시청={}/평점={}",
                days, recommended, clicked, viewedDetail, wishlisted, watched, rated);
        return new RecommendationFunnelResponse(
                recommended, clicked, viewedDetail, wishlisted, watched, rated, ctr, watchRate);
    }

    /**
     * 고객센터 자동화율 — 추이 + hop 분포.
     *
     * @param period 기간 ("30d" 권장)
     * @return SupportAutomationResponse
     */
    public SupportAutomationResponse getSupportAutomation(String period) {
        int days = parsePeriodDays(period);
        LocalDate today = LocalDate.now(AI_STATS_ZONE);
        LocalDateTime start = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        /* 기간 합계 */
        List<Object[]> ratioList = supportChatLogRepository.needsHumanRatio(start, end);
        Object[] ratio = ratioList != null && !ratioList.isEmpty() ? ratioList.get(0) : null;
        long escalated = ratio != null && ratio.length >= 1 && ratio[0] != null
                ? ((Number) ratio[0]).longValue() : 0L;
        long totalLogs = ratio != null && ratio.length >= 2 && ratio[1] != null
                ? ((Number) ratio[1]).longValue() : 0L;
        long autoResolved = totalLogs - escalated;
        double automationRate = totalLogs > 0
                ? Math.round((double) autoResolved / totalLogs * 1000.0) / 10.0
                : 0.0;

        /* 일별 추이 — native dailyCounts 활용 */
        List<Object[]> dailyRows = supportChatLogRepository.dailyCounts(start, end);
        Map<String, Object[]> dailyMap = new HashMap<>();
        for (Object[] r : dailyRows) {
            String date = r[0] != null ? r[0].toString() : null;
            if (date != null) dailyMap.put(date, r);
        }

        /* 빠진 날짜 0 채우기 */
        List<DailyAutomation> daily = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.format(DATE_FMT);
            Object[] r = dailyMap.get(key);
            long dayTotal = r != null && r[1] != null ? ((Number) r[1]).longValue() : 0L;
            long dayEsc = r != null && r[2] != null ? ((Number) r[2]).longValue() : 0L;
            double dayRate = dayTotal > 0
                    ? Math.round((double) (dayTotal - dayEsc) / dayTotal * 1000.0) / 10.0
                    : 0.0;
            daily.add(new DailyAutomation(key, dayTotal, dayEsc, dayRate));
        }

        /* hop 분포 — 0~5 */
        List<Object[]> hopRows = supportChatLogRepository.hopDistribution();
        List<HopBucket> hopDistribution = new ArrayList<>();
        for (Object[] r : hopRows) {
            int hops = r[0] != null ? ((Number) r[0]).intValue() : 0;
            long count = r[1] != null ? ((Number) r[1]).longValue() : 0L;
            hopDistribution.add(new HopBucket(hops, count));
        }

        log.debug("[admin-stats] 고객센터 자동화({}일) — 총={}/자동={}/유도={}/{}%",
                days, totalLogs, autoResolved, escalated, automationRate);
        return new SupportAutomationResponse(
                totalLogs, autoResolved, escalated, automationRate, daily, hopDistribution);
    }

    /**
     * AI 의도 분포 V2 — chat / support 두 채널 분리.
     *
     * @return AiIntentDistributionResponseV2 (chat, support)
     */
    public AiIntentDistributionResponseV2 getAiIntentDistributionV2() {
        /* ── chat 채널 — intent_summary JSON 페이지네이션 ── */
        Page<String> page = adminChatSessionRepository.findIntentSummariesPaged(
                PageRequest.of(0, 10000));
        Map<String, Long> chatCounts = parseIntentSummaries(page.getContent());
        long chatTotal = chatCounts.values().stream().mapToLong(Long::longValue).sum();
        List<IntentItem> chatIntents = chatCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    long count = e.getValue();
                    double pct = chatTotal > 0
                            ? Math.round((double) count / chatTotal * 1000.0) / 10.0
                            : 0.0;
                    return new IntentItem(e.getKey(), chatIntentLabel(e.getKey()), count, pct);
                })
                .toList();

        /* ── support 채널 — DB GROUP BY ── */
        List<Object[]> supportRows = supportChatLogRepository.countByIntent(null, null);
        long supportTotal = supportRows.stream()
                .mapToLong(r -> r[1] != null ? ((Number) r[1]).longValue() : 0L)
                .sum();
        List<IntentItem> supportIntents = supportRows.stream()
                .map(r -> {
                    String intent = r[0] != null ? r[0].toString() : "unknown";
                    long count = r[1] != null ? ((Number) r[1]).longValue() : 0L;
                    double pct = supportTotal > 0
                            ? Math.round((double) count / supportTotal * 1000.0) / 10.0
                            : 0.0;
                    return new IntentItem(intent, supportIntentLabel(intent), count, pct);
                })
                .toList();

        log.debug("[admin-stats] 의도 분포 V2 — chat {} 종, support {} 종",
                chatIntents.size(), supportIntents.size());
        return new AiIntentDistributionResponseV2(chatIntents, supportIntents);
    }

    /**
     * AI 쿼터 현황 V2 — 6 등급 차등 기준 적용.
     *
     * @return AiQuotaStatsResponseV2 (등급별 분포 포함)
     */
    public AiQuotaStatsResponseV2 getAiQuotaStatsV2() {
        long totalQuotaUsers = userAiQuotaRepository.count();
        double avgDaily = Math.round(userAiQuotaRepository.avgDailyAiUsed() * 10.0) / 10.0;
        double avgMonthly = Math.round(userAiQuotaRepository.avgMonthlyCouponUsed() * 10.0) / 10.0;
        long totalTokens = userAiQuotaRepository.sumPurchasedAiTokens();
        long exhausted = userAiQuotaRepository.countExhaustedUsersByGrade();

        List<Object[]> rows = userAiQuotaRepository.aggregateQuotaByGrade();
        List<GradeQuotaBucket> byGrade = new ArrayList<>();
        for (Object[] row : rows) {
            String gradeCode = row[0] != null ? row[0].toString() : "NORMAL";
            String gradeName = row[1] != null ? row[1].toString() : "알갱이";
            long users = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long ex = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            double avgUsed = row[4] != null ? Math.round(((Number) row[4]).doubleValue() * 10.0) / 10.0 : 0.0;
            byGrade.add(new GradeQuotaBucket(gradeCode, gradeName, users, ex, avgUsed));
        }

        log.debug("[admin-stats] AI 쿼터 V2 — 사용자={}, 등급버킷={}, 소진={}",
                totalQuotaUsers, byGrade.size(), exhausted);
        return new AiQuotaStatsResponseV2(
                totalQuotaUsers, avgDaily, avgMonthly, totalTokens, exhausted, byGrade);
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
     *   <li>totalActivityUsers: user_activity_progress 레코드를 가진 고유 사용자 수</li>
     *   <li>totalWishlists: user_wishlists 전체 레코드 수</li>
     *   <li>avgStreakDays: 사용자별 최신 streak 평균, 소수점 1자리 반올림</li>
     * </ul>
     */
    public EngagementOverviewResponse getEngagementOverview() {
        /* 총 출석 체크 수 */
        long totalAttendance = userAttendanceRepository.count();

        /* 오늘 출석 수 */
        long todayAttendance = userAttendanceRepository.countByCheckDate(LocalDate.now());

        /* 활동 진행 사용자 수 (사용자 × 활동유형 행 수가 아니라 고유 사용자 수) */
        long totalActivityUsers = userActivityProgressRepository.countDistinctUsers();

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

        /* actionType → 한국어 라벨 매핑. reward_policy.activity_name을 단일 표시명 원본으로 사용한다. */
        Map<String, String> actionLabels = rewardPolicyRepository.findAll().stream()
                .collect(Collectors.toMap(
                        policy -> policy.getActionType(),
                        policy -> policy.getActivityName(),
                        (existing, replacement) -> existing
                ));

        List<ActivityItem> activities = rows.stream()
                .map(row -> {
                    String actionType  = (String) row[0];
                    long userCount     = ((Number) row[1]).longValue();
                    long totalActions  = ((Number) row[2]).longValue();
                    String label = actionLabels.getOrDefault(actionType, toKoreanActivityFallback(actionType));
                    return new ActivityItem(actionType, label, userCount, totalActions);
                })
                .toList();

        log.debug("[admin-stats] 활동 분포 — {} 유형", activities.size());
        return new ActivityDistributionResponse(activities);
    }

    private String toKoreanActivityFallback(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return "기타 활동";
        }
        return switch (actionType) {
            case "review_write", "REVIEW_WRITE", "REVIEW_CREATE" -> "리뷰 작성";
            case "attendance", "ATTENDANCE", "ATTENDANCE_BASE" -> "출석 체크";
            case "movie_like", "MOVIE_LIKE" -> "영화 좋아요";
            case "wishlist_add", "WISHLIST_ADD" -> "위시리스트 추가";
            case "post_write", "POST_WRITE", "POST_REWARD" -> "게시글 작성";
            case "comment_write", "COMMENT_WRITE", "COMMENT_CREATE" -> "댓글 작성";
            case "worldcup_play", "WORLDCUP_PLAY", "WORLDCUP_COMPLETE" -> "월드컵 완주";
            case "roadmap_complete", "ROADMAP_COMPLETE", "COURSE_COMPLETE" -> "도장깨기 완주";
            case "quiz_correct", "QUIZ_CORRECT" -> "퀴즈 정답";
            case "playlist_create", "PLAYLIST_CREATE", "PLAYLIST_SHARE" -> "플레이리스트 공유";
            case "profile_complete", "PROFILE_COMPLETE" -> "프로필 완성";
            case "first_review", "FIRST_REVIEW" -> "첫 리뷰 작성";
            case "first_login", "FIRST_LOGIN" -> "첫 로그인";
            default -> "기타 활동";
        };
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

        /*
         * 단계 5: 결제 전환 고유 사용자 수 (COMPLETED 주문).
         * v3.6 (2026-04-28): 기존 6단계에서 "구독 전환" 단계를 제거하고 5단계로 단순화.
         * payment_orders COMPLETED 안에 구독 결제(monthly/yearly)와 이용권 결제가 모두 포함되므로
         * "결제" 단계가 유료 전환의 단일 지표로 충분하다 (단계 중복 제거).
         */
        long step5 = paymentOrderRepository.countByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, start, end);

        /*
         * 단계 2: 첫 활동 사용자 수.
         * 이상적으로는 (AI OR 리뷰 OR 위시리스트) DISTINCT userId 이지만
         * 3개 테이블 cross-join 없이는 정확한 합집합이 어렵다.
         * 실용적 근사: AI 채팅 / 리뷰 작성 중 최대값을 첫 활동 사용자 수로 사용한다.
         * step1 을 초과하지 않도록 min(step1, max(step3, step4)) 적용.
         */
        long step2 = step1 > 0 ? Math.min(step1, Math.max(step3, step4)) : 0L;

        /* 단계별 배열로 처리하여 전환율 일괄 계산 (5단계) */
        long[] counts = {step1, step2, step3, step4, step5};
        String[] labels = {
                "신규 가입",
                "첫 활동",
                "AI 채팅 사용",
                "리뷰 작성",
                "결제"
        };

        List<FunnelStep> steps = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            long count = counts[i];
            /* 전 단계 대비 전환율: 단계 1은 100.0, 이후는 현 단계 / 전 단계 */
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

        /* 전체 전환율(가입 → 결제) — 마지막 단계 conversionFromTop */
        double totalConversionRate = step1 > 0
                ? Math.round((double) step5 / step1 * 1000.0) / 10.0
                : 0.0;

        log.debug("[admin-stats] 전환 퍼널 — 기간={}일, 가입={}, 첫활동={}, AI={}, 리뷰={}, 결제={}, 전체={}%",
                days, step1, step2, step3, step4, step5, totalConversionRate);
        return new FunnelConversionResponse(period, steps, totalConversionRate);
    }

    // ══════════════════════════════════════════════
    // 15. 이탈 위험 분석
    // ══════════════════════════════════════════════

    /**
     * 이탈 위험 개요 KPI를 반환한다.
     *
     * <p>전체 사용자(최대 1000명 샘플)의 위험도 점수를 Java에서 계산하여
     * 4구간(안전/낮음/중간/높음)으로 분류한다.</p>
     *
     * <h4>점수 계산 기준 (최대 75점) — v3.6 (2026-04-28) 재조정</h4>
     * <ul>
     *   <li>로그인 공백 7일+: 10점 / 14일+: 25점 / 30일+: 40점 (누적 아님, 가장 높은 구간 적용)</li>
     *   <li>포인트 잔액 0 + 가입 7일 이상: 15점</li>
     *   <li>AI 세션 없음 (가입 14일 이상): 20점</li>
     * </ul>
     *
     * <h4>구간 재조정</h4>
     * <ul>
     *   <li>안전: 0~14점 / 낮음: 15~29점 / 중간: 30~49점 / 높음: 50~75점</li>
     *   <li>이전 (95점 만점)에서는 "구독 미보유 +10점"이 포함되어 있었으나 v3.6 에서 제거</li>
     *   <li>이유: 무료 사용자가 압도적 다수라 "구독 미보유" 신호는 변별력이 없음</li>
     * </ul>
     *
     * <p>AI 세션 없음 판정: ChatSessionArchive.userId 전체 목록을 Set으로 캐싱하여 O(1) 조회.</p>
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
         * AI 세션 보유 userId Set (isDeleted 무관 — 사용 이력 기준)
         */
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

        /* 3. 사용자별 위험도 점수 계산 (구독 미보유 점수 제거 — 최대 75점) */
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

            /* 구간 분류 (재조정: 0~14 / 15~29 / 30~49 / 50+) */
            if (score < 15) {
                noRisk++;
            } else if (score < 30) {
                lowRisk++;
            } else if (score < 50) {
                mediumRisk++;
            } else {
                highRisk++;
            }
        }

        log.debug("[admin-stats] 이탈 위험 — 분석={}명, 안전={}, 낮음={}, 중간={}, 높음={}",
                users.size(), noRisk, lowRisk, mediumRisk, highRisk);
        return new ChurnRiskOverviewResponse(noRisk, lowRisk, mediumRisk, highRisk, users.size());
    }

    /**
     * 이탈 위험 신호 집계를 반환한다.
     *
     * <p>운영팀이 리텐션 캠페인 타깃을 설정할 수 있도록
     * 구체적인 이탈 위험 신호별 사용자 수를 집계한다.</p>
     *
     * <h4>집계 항목 — v3.6 (2026-04-28) 재구성</h4>
     * <ul>
     *   <li>7일/14일/30일+ 미로그인: UserMapper.countByLastLoginAtBefore()</li>
     *   <li>포인트 잔액 0: UserPointRepository.countZeroBalanceUsers()</li>
     *   <li>AI 채팅 미사용 (가입 14일 이상): User.createdAt 14일 이전인 사용자 중 ChatSessionArchive 에 userId 없는 수</li>
     * </ul>
     *
     * <p>변경: 기존 "구독 만료 후 미갱신" 신호 제거 — 무료 사용자가 압도적이라 변별력이 없었음.
     * 대신 점수 산정 기준에 이미 들어 있던 "AI 채팅 미사용" 신호를 노출하여 산정 기준과 화면 정합 회복.</p>
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

        /*
         * AI 채팅 미사용 (가입 14일 이상) 사용자 수 집계.
         * AI 세션 사용 이력이 있는 userId Set 을 한 번 캐싱한 뒤,
         * 가입 14일 이상 사용자 전수 검사하여 Set 에 없는 사용자 수를 카운트한다.
         * (UserMapper.findAllLimited(1000) 샘플 기반 — getChurnRiskOverview 와 동일 정책)
         */
        java.util.Set<String> aiSessionUserIds = adminChatSessionRepository
                .findAll(PageRequest.of(0, 5000))
                .getContent()
                .stream()
                .map(session -> session.getUserId())
                .collect(Collectors.toSet());

        LocalDateTime joinedBefore = now.minusDays(14);
        long noAiUsageOver14days = userMapper.findAllLimited(1000)
                .stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isBefore(joinedBefore))
                .filter(u -> !aiSessionUserIds.contains(u.getUserId()))
                .count();

        log.debug("[admin-stats] 이탈 신호 — 7일미로그인={}, 14일={}, 30일={}, 잔액0={}, AI미사용14일+={}",
                inactive7, inactive14, inactive30, zeroPoint, noAiUsageOver14days);
        return new ChurnRiskSignalsResponse(inactive7, inactive14, inactive30, zeroPoint, noAiUsageOver14days);
    }
}
