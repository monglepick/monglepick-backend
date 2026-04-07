package com.monglepick.monglepickbackend.domain.recommendation.scheduler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.recommendation.entity.UserBehaviorProfile;
import com.monglepick.monglepickbackend.domain.recommendation.repository.EventLogRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.UserBehaviorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 사용자 행동 프로필 일일 배치 스케줄러.
 *
 * <p>매일 새벽 3시에 최근 90일 내 활동이 있는 유저를 대상으로
 * {@link UserBehaviorProfile}을 집계·갱신한다.</p>
 *
 * <h3>처리 단계</h3>
 * <ol>
 *   <li>최근 90일 내 활동 유저 ID 목록 조회 (reviews + event_logs UNION)</li>
 *   <li>유저별 장르·감독 친화도 계산 (최근 100건 리뷰 작성 기반 — "봤다" 단일 진실 원본)</li>
 *   <li>취향 일관성 지수 계산 (Shannon Entropy 역산)</li>
 *   <li>추천 수용률 집계 (RecommendationImpact clicked+watched / total)</li>
 *   <li>평균 탐색 깊이 집계 (세션별 클릭 카드 수 평균)</li>
 *   <li>활동 수준 판정 (최근 30일 이벤트 수 기준 4단계)</li>
 *   <li>UPSERT (기존 프로필 갱신 또는 신규 생성)</li>
 * </ol>
 *
 * <h3>watch_history 폐기 (2026-04-08)</h3>
 * <p>장르·감독 친화도 계산의 입력을 {@code watch_history} 테이블에서 {@code reviews} 테이블로
 * 전환했다. WatchHistory 도메인은 폐기되었으며, "리뷰 작성 = 시청 완료 확인"이라는 단일 진실
 * 원본 원칙에 따라 reviews 테이블이 모든 시청 행동의 기준이 된다.</p>
 *
 * <h3>에러 전파 정책</h3>
 * <p>개별 유저 처리 실패 시 해당 유저만 skip하고 전체 배치는 중단하지 않는다.</p>
 *
 * <h3>활동 수준 기준 (최근 30일 이벤트 수)</h3>
 * <ul>
 *   <li>0~5건   → dormant (비활성)</li>
 *   <li>6~20건  → casual  (일반)</li>
 *   <li>21~100건 → active  (활성)</li>
 *   <li>101건+  → power   (파워 유저)</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BehaviorProfileScheduler {

    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final EventLogRepository eventLogRepository;
    private final RecommendationImpactRepository recommendationImpactRepository;
    private final MovieRepository movieRepository;
    private final ObjectMapper objectMapper;

    /** 장르·감독 친화도 계산에 사용할 최근 리뷰 작성 이력 최대 건수 */
    private static final int RECENT_REVIEW_LIMIT = 100;

    /** 활동 유저 기준: 최근 90일 이내 활동 */
    private static final int ACTIVE_PERIOD_DAYS = 90;

    /** 활동 수준 판정 기준: 최근 30일 이벤트 수 */
    private static final int ACTIVITY_LEVEL_PERIOD_DAYS = 30;

    /** 감독 친화도 Top N 개수 */
    private static final int DIRECTOR_TOP_N = 10;

    /**
     * 매일 새벽 3시에 전체 활성 유저의 행동 프로필을 재계산한다.
     *
     * <p>{@code @Transactional}을 메서드 레벨에 선언하여 유저별 처리는
     * {@link #computeProfileForUser(String)}에서 별도 트랜잭션으로 위임된다.
     * 개별 유저 실패가 전체 배치를 롤백하지 않도록 try-catch로 격리한다.</p>
     */
    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시 실행
    public void computeBehaviorProfiles() {
        log.info("[BehaviorProfileScheduler] 행동 프로필 배치 시작");
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVE_PERIOD_DAYS);

        // 최근 90일 내 활동이 있는 유저 ID 목록 (reviews + event_logs UNION)
        List<String> activeUserIds = behaviorProfileRepository.findActiveUserIdsSince(since);
        log.info("[BehaviorProfileScheduler] 처리 대상 유저 수: {}명", activeUserIds.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (String userId : activeUserIds) {
            try {
                // 유저별 처리는 별도 트랜잭션으로 위임 — 실패 시 해당 유저만 롤백
                computeProfileForUser(userId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 에러 전파 금지: 개별 유저 실패 시 skip하고 배치 계속 진행
                failCount.incrementAndGet();
                log.warn("[BehaviorProfileScheduler] 유저 프로필 계산 실패 (userId={}) — skip: {}",
                        userId, e.getMessage());
            }
        }

        log.info("[BehaviorProfileScheduler] 행동 프로필 배치 완료 — 성공: {}명, 실패(skip): {}명",
                successCount.get(), failCount.get());
    }

    /**
     * 단일 유저의 행동 프로필을 계산하고 UPSERT한다.
     *
     * <p>트랜잭션 경계를 유저 단위로 분리하기 위해 {@code @Transactional}을 메서드에 선언한다.
     * 같은 빈 내부 호출이므로 self-invocation 문제를 피하려면 Spring AOP 프록시가
     * 정상 동작하는지 확인이 필요하다. (현재 구조에서는 외부에서 호출되므로 정상 동작)</p>
     *
     * @param userId 처리 대상 사용자 ID
     */
    @Transactional
    public void computeProfileForUser(String userId) {

        // ── 1. 최근 100건 리뷰 작성 영화 ID 목록 조회 ────────────────────────
        // "봤다" 단일 진실 원본 = reviews 테이블 (watch_history 폐기, 2026-04-08)
        List<String> recentMovieIds = behaviorProfileRepository.findRecentReviewedMovieIdsByUserId(
                userId, RECENT_REVIEW_LIMIT);

        // ── 2. 영화 메타데이터 일괄 조회 (IN 절, N+1 방지) ────────────────────
        Map<String, Movie> movieMap = new HashMap<>();
        if (!recentMovieIds.isEmpty()) {
            movieRepository.findAllByMovieIdIn(recentMovieIds)
                    .forEach(m -> movieMap.put(m.getMovieId(), m));
        }

        // ── 3. 장르·감독 친화도 계산 ──────────────────────────────────────────
        String genreAffinityJson  = computeGenreAffinity(recentMovieIds, movieMap);
        String directorAffinityJson = computeDirectorAffinity(recentMovieIds, movieMap);

        // ── 4. 취향 일관성 지수 계산 (Shannon Entropy 역산) ───────────────────
        double tasteConsistency = computeTasteConsistency(recentMovieIds, movieMap);

        // ── 5. 추천 수용률 집계 ────────────────────────────────────────────────
        double acceptanceRate = computeAcceptanceRate(userId);

        // ── 6. 평균 탐색 깊이 집계 ────────────────────────────────────────────
        double avgExplorationDepth = computeAvgExplorationDepth(userId);

        // ── 7. 활동 수준 판정 (최근 30일 이벤트 수 기준) ─────────────────────
        String activityLevel = computeActivityLevel(userId);

        // ── 8. UPSERT — 기존 프로필 갱신, 없으면 신규 생성 ───────────────────
        UserBehaviorProfile profile = behaviorProfileRepository.findByUserId(userId)
                .orElse(UserBehaviorProfile.builder()
                        .userId(userId)
                        .build());

        profile.updateProfile(
                genreAffinityJson,
                null,               // moodAffinity: 현재 집계 미구현 (Phase 2에서 추가 예정)
                directorAffinityJson,
                tasteConsistency,
                acceptanceRate,
                avgExplorationDepth,
                activityLevel
        );

        behaviorProfileRepository.save(profile);
    }

    // ========== 내부 계산 메서드 ==========

    /**
     * 장르 친화도 JSON을 계산한다.
     *
     * <p>최근 시청 영화의 genres(JSON 배열) 필드를 파싱하여 장르별 빈도를 집계하고,
     * 총합으로 나눠 0.0~1.0으로 정규화한다.</p>
     *
     * <p>예시 결과: {"액션": 0.42, "SF": 0.28, "드라마": 0.18, "스릴러": 0.12}</p>
     *
     * @param movieIds 최근 시청 영화 ID 목록
     * @param movieMap 영화 ID → Movie 엔티티 맵
     * @return 정규화된 장르 친화도 JSON 문자열 (집계 불가 시 "{}")
     */
    private String computeGenreAffinity(List<String> movieIds, Map<String, Movie> movieMap) {
        if (movieIds.isEmpty()) {
            return "{}";
        }

        // 장르별 빈도 집계
        Map<String, Integer> genreCount = new HashMap<>();
        int totalGenreCount = 0;

        for (String movieId : movieIds) {
            Movie movie = movieMap.get(movieId);
            if (movie == null || movie.getGenres() == null) {
                continue;
            }
            // genres 필드는 JSON 배열 문자열 (예: ["액션", "SF"])
            List<String> genres = parseJsonStringArray(movie.getGenres());
            for (String genre : genres) {
                genreCount.merge(genre.trim(), 1, Integer::sum);
                totalGenreCount++;
            }
        }

        if (totalGenreCount == 0) {
            return "{}";
        }

        // 정규화: 각 장르 빈도 / 전체 장르 등장 수
        Map<String, Double> genreAffinity = new HashMap<>();
        final int total = totalGenreCount;
        genreCount.forEach((genre, count) ->
                genreAffinity.put(genre, Math.round((double) count / total * 100.0) / 100.0));

        return toJson(genreAffinity);
    }

    /**
     * 감독 친화도 JSON을 계산한다.
     *
     * <p>최근 시청 영화의 director 필드를 집계하여 빈도 기반으로 정규화한다.
     * 상위 {@value #DIRECTOR_TOP_N}명만 결과에 포함한다.</p>
     *
     * @param movieIds 최근 시청 영화 ID 목록
     * @param movieMap 영화 ID → Movie 엔티티 맵
     * @return 정규화된 감독 친화도 JSON 문자열 (집계 불가 시 "{}")
     */
    private String computeDirectorAffinity(List<String> movieIds, Map<String, Movie> movieMap) {
        if (movieIds.isEmpty()) {
            return "{}";
        }

        // 감독별 빈도 집계
        Map<String, Integer> directorCount = new HashMap<>();
        int totalCount = 0;

        for (String movieId : movieIds) {
            Movie movie = movieMap.get(movieId);
            if (movie == null || movie.getDirector() == null || movie.getDirector().isBlank()) {
                continue;
            }
            directorCount.merge(movie.getDirector().trim(), 1, Integer::sum);
            totalCount++;
        }

        if (totalCount == 0) {
            return "{}";
        }

        // 빈도 내림차순 정렬 후 Top N 추출 → 정규화
        Map<String, Double> directorAffinity = new HashMap<>();
        final int total = totalCount;
        directorCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(DIRECTOR_TOP_N)
                .forEach(entry ->
                        directorAffinity.put(entry.getKey(),
                                Math.round((double) entry.getValue() / total * 100.0) / 100.0));

        return toJson(directorAffinity);
    }

    /**
     * 취향 일관성 지수를 계산한다.
     *
     * <p>장르 빈도 분포의 Shannon Entropy를 역산한다.
     * 엔트로피가 낮을수록(특정 장르에 집중) 일관성이 높아지고,
     * 엔트로피가 높을수록(고르게 분산) 일관성이 낮아진다.</p>
     *
     * <p>계산식:</p>
     * <pre>
     *   entropy         = -Σ p(g) * log2(p(g))
     *   max_entropy     = log2(장르 수)
     *   tasteConsistency = 1.0 - (entropy / max_entropy)
     * </pre>
     *
     * @param movieIds 최근 시청 영화 ID 목록
     * @param movieMap 영화 ID → Movie 엔티티 맵
     * @return 취향 일관성 지수 (0.0~1.0, 데이터 없으면 0.0)
     */
    private double computeTasteConsistency(List<String> movieIds, Map<String, Movie> movieMap) {
        if (movieIds.isEmpty()) {
            return 0.0;
        }

        // 장르 빈도 집계
        Map<String, Integer> genreCount = new HashMap<>();
        int totalGenreCount = 0;

        for (String movieId : movieIds) {
            Movie movie = movieMap.get(movieId);
            if (movie == null || movie.getGenres() == null) {
                continue;
            }
            List<String> genres = parseJsonStringArray(movie.getGenres());
            for (String genre : genres) {
                genreCount.merge(genre.trim(), 1, Integer::sum);
                totalGenreCount++;
            }
        }

        if (totalGenreCount == 0 || genreCount.size() <= 1) {
            // 장르가 0~1종류이면 최대 일관성
            return 1.0;
        }

        // Shannon Entropy 계산: -Σ p(g) * log2(p(g))
        final double total = totalGenreCount;
        double entropy = genreCount.values().stream()
                .mapToDouble(count -> {
                    double p = count / total;
                    return -p * (Math.log(p) / Math.log(2));  // log2(p) = ln(p) / ln(2)
                })
                .sum();

        // 최대 엔트로피: log2(장르 수) — 모든 장르가 균등 분포일 때
        double maxEntropy = Math.log(genreCount.size()) / Math.log(2);

        if (maxEntropy == 0.0) {
            return 1.0;
        }

        // 일관성 = 1.0 - 정규화 엔트로피 (0.0: 완전 분산, 1.0: 완전 편향)
        double consistency = 1.0 - (entropy / maxEntropy);

        // 부동소수점 오차로 범위 초과 방지
        return Math.max(0.0, Math.min(1.0, consistency));
    }

    /**
     * 추천 수용률을 집계한다.
     *
     * <p>전체 추천 레코드 중 클릭 또는 시청으로 이어진 비율을 계산한다.
     * 계산식: (clicked_count + watched_count) / total_recommended_count</p>
     *
     * @param userId 집계 대상 사용자 ID
     * @return 추천 수용률 (0.0~1.0, 추천 이력 없으면 0.0)
     */
    private double computeAcceptanceRate(String userId) {
        Object[] stats = recommendationImpactRepository.countImpactStatsByUserId(userId);

        if (stats == null || stats.length == 0 || stats[0] == null) {
            return 0.0;
        }

        // countImpactStatsByUserId 반환: [총 추천 수(Long), 클릭 수(Long), 시청 수(Long)]
        long total   = ((Number) stats[0]).longValue();
        long clicked = stats[1] != null ? ((Number) stats[1]).longValue() : 0L;
        long watched = stats[2] != null ? ((Number) stats[2]).longValue() : 0L;

        if (total == 0) {
            return 0.0;
        }

        double rate = (double) (clicked + watched) / total;
        // 클릭+시청 합산이 total을 초과하는 경우(동일 레코드 중복 집계) 1.0으로 클램핑
        return Math.min(1.0, rate);
    }

    /**
     * 평균 세션 탐색 깊이를 집계한다.
     *
     * <p>한 추천 세션에서 평균적으로 클릭한 카드 수를 반환한다.
     * 데이터 없거나 집계 실패 시 0.0을 반환한다.</p>
     *
     * @param userId 집계 대상 사용자 ID
     * @return 평균 탐색 깊이 (0.0 이상)
     */
    private double computeAvgExplorationDepth(String userId) {
        Double depth = recommendationImpactRepository.avgExplorationDepthByUserId(userId);
        return depth != null ? depth : 0.0;
    }

    /**
     * 활동 수준을 판정한다.
     *
     * <p>최근 {@value #ACTIVITY_LEVEL_PERIOD_DAYS}일 이벤트 수를 기준으로 분류한다.</p>
     * <ul>
     *   <li>0~5건   → dormant</li>
     *   <li>6~20건  → casual</li>
     *   <li>21~100건 → active</li>
     *   <li>101건+  → power</li>
     * </ul>
     *
     * @param userId 판정 대상 사용자 ID
     * @return 활동 수준 문자열 (dormant / casual / active / power)
     */
    private String computeActivityLevel(String userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVITY_LEVEL_PERIOD_DAYS);
        long eventCount = eventLogRepository.countByUserIdAndCreatedAtAfter(userId, since);

        if (eventCount <= 5) {
            return "dormant";
        } else if (eventCount <= 20) {
            return "casual";
        } else if (eventCount <= 100) {
            return "active";
        } else {
            return "power";
        }
    }

    // ========== 유틸리티 메서드 ==========

    /**
     * JSON 배열 문자열을 {@code List<String>}으로 파싱한다.
     *
     * <p>파싱 실패 시 빈 리스트를 반환하며, 예외를 전파하지 않는다.</p>
     *
     * @param jsonArray JSON 배열 문자열 (예: ["액션", "SF"])
     * @return 문자열 목록 (파싱 실패 시 빈 리스트)
     */
    @SuppressWarnings("unchecked")
    private List<String> parseJsonStringArray(String jsonArray) {
        try {
            return objectMapper.readValue(jsonArray, List.class);
        } catch (JacksonException e) {
            log.debug("[BehaviorProfileScheduler] JSON 파싱 실패 (value={}): {}", jsonArray, e.getMessage());
            return List.of();
        }
    }

    /**
     * Map 객체를 JSON 문자열로 직렬화한다.
     *
     * <p>직렬화 실패 시 "{}"를 반환하며, 예외를 전파하지 않는다.</p>
     *
     * @param map 직렬화할 Map 객체
     * @return JSON 문자열 (직렬화 실패 시 "{}")
     */
    private String toJson(Object map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            log.warn("[BehaviorProfileScheduler] JSON 직렬화 실패: {}", e.getMessage());
            return "{}";
        }
    }
}
