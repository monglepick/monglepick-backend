package com.monglepick.monglepickbackend.domain.recommendation.repository;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 추천 로그 JPA 리포지토리 — recommendation_log 테이블 데이터 접근.
 *
 * <p>AI Agent가 기록한 추천 이력의 조회 및 통계 집계를 지원한다.
 * 관리자 통계 탭(추천 성과, 추천 로그 목록)에서 주로 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #countByCreatedAtAfter(LocalDateTime)} — 기간 내 추천 총 횟수</li>
 *   <li>{@link #countByClickedTrueAndCreatedAtAfter(LocalDateTime)} — 기간 내 클릭 발생 횟수 (CTR 분자)</li>
 *   <li>{@link #findAverageScoreAfter(LocalDateTime)} — 기간 내 평균 추천 점수</li>
 *   <li>{@link #findAllWithMovie(Pageable)} — 추천 로그 목록 (N+1 방지, 페이징)</li>
 * </ul>
 */
public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    /**
     * 특정 사용자의 추천 로그 수를 집계한다.
     *
     * <p>CSV/관리자 등록 업적 {@code recommendation_*} 진행률 계산에 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 추천 로그 수
     */
    long countByUserId(String userId);

    /**
     * 지정 시각 이후에 생성된 추천 로그 수를 집계한다.
     *
     * <p>관리자 통계에서 기간 내 총 추천 횟수(totalRecommendations)를 계산할 때 사용한다.</p>
     *
     * @param after 기준 시각 (이 시각 이후 레코드만 포함)
     * @return 해당 기간 내 추천 총 횟수
     */
    long countByCreatedAtAfter(LocalDateTime after);

    /**
     * 지정 시각 이후 클릭이 발생한 추천 로그 수를 집계한다.
     *
     * <p>CTR(Click-Through Rate) 계산의 분자로 사용된다.
     * CTR = countByClickedTrueAndCreatedAtAfter / countByCreatedAtAfter × 100</p>
     *
     * @param after 기준 시각
     * @return 해당 기간 내 클릭 발생 추천 횟수
     */
    long countByClickedTrueAndCreatedAtAfter(LocalDateTime after);

    /**
     * 지정 시각 이후 추천 로그의 평균 추천 점수를 계산한다.
     *
     * <p>AVG 집계 쿼리. 레코드가 없으면 null을 반환한다.</p>
     *
     * @param after 기준 시각
     * @return 평균 추천 점수 (레코드 없으면 null)
     */
    @Query("SELECT AVG(r.score) FROM RecommendationLog r WHERE r.createdAt > :after")
    Double findAverageScoreAfter(@Param("after") LocalDateTime after);

    /**
     * 추천 로그 전체를 영화와 함께 페이징 조회한다 (N+1 방지).
     *
     * <p>관리자 추천 로그 목록 화면에서 사용한다.
     * movie를 JOIN FETCH하여 추가 쿼리를 방지한다. user는 String FK 직접 보관 방식이므로
     * (JPA/MyBatis 하이브리드 §15.4) JOIN FETCH 대상이 아니다.</p>
     *
     * @param pageable 페이징 정보
     * @return 추천 로그 페이지
     */
    @Query(value = "SELECT r FROM RecommendationLog r JOIN FETCH r.movie",
           countQuery = "SELECT COUNT(r) FROM RecommendationLog r")
    Page<RecommendationLog> findAllWithMovie(Pageable pageable);

    // ─────────────────────────────────────────────
    // 사용자별 추천 이력 조회 (클라이언트 추천 이력 탭용)
    // ─────────────────────────────────────────────

    /**
     * 특정 사용자의 추천 이력을 최신 순으로 페이징 조회한다 (N+1 방지).
     *
     * <p>클라이언트 추천 이력 탭({@code GET /api/v1/recommendations})에서 사용한다.
     * movie를 JOIN FETCH하여 영화 정보를 한 번에 조회한다.</p>
     *
     * @param userId   조회 대상 사용자 ID
     * @param pageable 페이징 정보 (기본 정렬: createdAt DESC)
     * @return 해당 사용자의 추천 로그 페이지
     */
    @Query(value = "SELECT r FROM RecommendationLog r JOIN FETCH r.movie " +
                   "WHERE r.userId = :userId",
           countQuery = "SELECT COUNT(r) FROM RecommendationLog r WHERE r.userId = :userId")
    Page<RecommendationLog> findByUserIdWithMovie(@Param("userId") String userId, Pageable pageable);

    /**
     * 특정 사용자의 추천 이력 중 RecommendationImpact 에 wishlisted=true 인 레코드만
     * 최신 순으로 페이징 조회한다 (N+1 방지 — movie JOIN FETCH).
     *
     * <p>QA 후속 (2026-04-23): "찜한영화" 탭 전용 쿼리. Impact 레코드와 INNER JOIN 하여
     * 현재 wishlist 상태인 추천만 반환한다. 동일 (user_id, movie_id, rec_log_id) 조합의
     * Impact 는 UNIQUE 제약으로 최대 1건이라 중복 걱정 없다.</p>
     *
     * @param userId   조회 대상 사용자 ID
     * @param pageable 페이징 정보 (정렬 `createdAt DESC` 권장)
     * @return 찜 상태인 추천 로그 페이지
     */
    @Query(value = "SELECT r FROM RecommendationLog r " +
                   "JOIN FETCH r.movie m " +
                   "WHERE r.userId = :userId AND EXISTS (" +
                   "  SELECT 1 FROM RecommendationImpact ri " +
                   "  WHERE ri.recommendationLog = r AND ri.userId = :userId " +
                   "    AND ri.wishlisted = true" +
                   ")",
           countQuery = "SELECT COUNT(r) FROM RecommendationLog r " +
                   "WHERE r.userId = :userId AND EXISTS (" +
                   "  SELECT 1 FROM RecommendationImpact ri " +
                   "  WHERE ri.recommendationLog = r AND ri.userId = :userId " +
                   "    AND ri.wishlisted = true" +
                   ")")
    Page<RecommendationLog> findByUserIdWishlistedWithMovie(
            @Param("userId") String userId, Pageable pageable);

    /**
     * 특정 사용자의 추천 이력 중 RecommendationImpact 에 watched=true 인 레코드만
     * 최신 순으로 페이징 조회한다 (N+1 방지 — movie JOIN FETCH).
     *
     * <p>QA 후속 (2026-04-23): "본영화" 탭 전용 쿼리.</p>
     *
     * @param userId   조회 대상 사용자 ID
     * @param pageable 페이징 정보
     * @return 봤어요 상태인 추천 로그 페이지
     */
    @Query(value = "SELECT r FROM RecommendationLog r " +
                   "JOIN FETCH r.movie m " +
                   "WHERE r.userId = :userId AND EXISTS (" +
                   "  SELECT 1 FROM RecommendationImpact ri " +
                   "  WHERE ri.recommendationLog = r AND ri.userId = :userId " +
                   "    AND ri.watched = true" +
                   ")",
           countQuery = "SELECT COUNT(r) FROM RecommendationLog r " +
                   "WHERE r.userId = :userId AND EXISTS (" +
                   "  SELECT 1 FROM RecommendationImpact ri " +
                   "  WHERE ri.recommendationLog = r AND ri.userId = :userId " +
                   "    AND ri.watched = true" +
                   ")")
    Page<RecommendationLog> findByUserIdWatchedWithMovie(
            @Param("userId") String userId, Pageable pageable);

    /**
     * 특정 사용자의 추천 이력 중 추천 로그 ID로 단건을 조회한다.
     *
     * <p>찜/봤어요 토글 API에서 소유권 검증에 사용한다.
     * userId가 일치하지 않으면 빈 Optional을 반환하여 403 처리에 활용한다.</p>
     *
     * @param recommendationLogId 추천 로그 ID
     * @param userId              소유자 사용자 ID
     * @return 해당 조건의 추천 로그 (없으면 빈 Optional)
     */
    @Query("SELECT r FROM RecommendationLog r JOIN FETCH r.movie " +
           "WHERE r.recommendationLogId = :recommendationLogId AND r.userId = :userId")
    Optional<RecommendationLog> findByRecommendationLogIdAndUserId(
            @Param("recommendationLogId") Long recommendationLogId,
            @Param("userId") String userId);

    // ══════════════════════════════════════════════
    // AI 서비스 통계 V2 — 추천 엔진 KPI/추이/응답시간/모델비교 (2026-04-29)
    // ══════════════════════════════════════════════

    // 전체 추천 로그 수는 JpaRepository.count() 가 이미 제공 → 별도 메서드 선언 불필요.

    /**
     * 전체 클릭된 추천 로그 수 (CTR 분자 — 전체 기준).
     *
     * @return clicked=true 행 수
     */
    long countByClickedTrue();

    /**
     * source_type='EXTERNAL_DDGS' 인 추천 로그 수 (외부검색 비율).
     *
     * @return EXTERNAL_DDGS 비율 분자
     */
    long countBySourceType(String sourceType);

    /**
     * 전체 평균 추천 점수 (RecommendStat.avgScore).
     *
     * @return 평균 score (데이터 없으면 null)
     */
    @Query("SELECT AVG(r.score) FROM RecommendationLog r")
    Double findAverageScore();

    /**
     * 지정 기간 내 추천 로그 수 (일별 추이/CTR 분모).
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 기간 내 추천 호출 수
     */
    @Query("""
            SELECT COUNT(r) FROM RecommendationLog r
            WHERE r.createdAt >= :start AND r.createdAt < :end
            """)
    long countByCreatedAtBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    /**
     * 지정 기간 내 클릭 발생 추천 로그 수 (CTR 분자).
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 기간 내 클릭 수
     */
    @Query("""
            SELECT COUNT(r) FROM RecommendationLog r
            WHERE r.clicked = true AND r.createdAt >= :start AND r.createdAt < :end
            """)
    long countClickedByCreatedAtBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    /**
     * 지정 기간 내 평균 응답시간 (Summary 카드용 — 최근 7일 권장).
     *
     * <p>response_time_ms 가 NULL 인 행은 자동 제외 (AVG 동작).</p>
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 평균 응답시간 ms (데이터 없으면 null)
     */
    @Query("""
            SELECT AVG(r.responseTimeMs) FROM RecommendationLog r
            WHERE r.responseTimeMs IS NOT NULL
              AND r.createdAt >= :start AND r.createdAt < :end
            """)
    Double findAverageLatency(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    /**
     * 지정 기간 내 응답시간 raw 리스트 — Java 정렬로 p50/p95/p99 계산.
     *
     * <p>운영 규모 확대 시 SQL PERCENT_RANK() 또는 모니터링 시스템(Prometheus) 으로 이관.
     * 1차 구현은 단순성/안정성 우선.</p>
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 응답시간 ms 리스트 (오름차순 아님 — 호출자가 정렬)
     */
    @Query("""
            SELECT r.responseTimeMs FROM RecommendationLog r
            WHERE r.responseTimeMs IS NOT NULL
              AND r.createdAt >= :start AND r.createdAt < :end
            """)
    java.util.List<Integer> findLatenciesByCreatedAtBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    /**
     * 모델 버전별 통계 — count, avg(score), avg(latency), CTR 분자/분모.
     *
     * <p>반환 row: [modelVersion, count, avgScore, avgLatency, clickedCount].
     * NULL 모델 버전은 'unknown' 으로 묶지 않고 그대로 NULL 반환 (서비스 레이어에서 라벨링).</p>
     *
     * @return Object[] (modelVersion, count, avgScore, avgLatency, clickedCount) 리스트
     */
    @Query("""
            SELECT r.modelVersion,
                   COUNT(r),
                   AVG(r.score),
                   AVG(r.responseTimeMs),
                   SUM(CASE WHEN r.clicked = true THEN 1 ELSE 0 END)
            FROM RecommendationLog r
            GROUP BY r.modelVersion
            ORDER BY COUNT(r) DESC
            """)
    java.util.List<Object[]> aggregateByModelVersion();
}
