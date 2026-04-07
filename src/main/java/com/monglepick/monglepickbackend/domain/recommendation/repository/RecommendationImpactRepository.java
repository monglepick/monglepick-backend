package com.monglepick.monglepickbackend.domain.recommendation.repository;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 추천 임팩트 JPA 리포지토리 — recommendation_impact 테이블 데이터 접근.
 *
 * <p>AI 추천 후 발생하는 사용자 행동(클릭·상세조회·위시리스트·시청·평점)의
 * 저장 및 조회를 담당한다. {@link RecommendationImpactService}에서 주로 사용된다.</p>
 *
 * <h3>주요 조회 패턴</h3>
 * <ul>
 *   <li>단일 임팩트 조회 — (userId, movieId, recommendationLogId) 3중 유니크 키 기반</li>
 *   <li>다중 임팩트 조회 — (userId, movieId) 기반으로 동일 영화의 이전 추천 이력 전체 조회</li>
 * </ul>
 */
public interface RecommendationImpactRepository extends JpaRepository<RecommendationImpact, Long> {

    /**
     * (userId, movieId, recommendationLogId) 조합으로 임팩트 레코드를 단건 조회한다.
     *
     * <p>recommendation_impact 테이블의 유니크 제약({@code uk_impact_user_movie_rec})과
     * 동일한 키 조합을 사용하므로 결과는 항상 0건 또는 1건이다.
     * {@link RecommendationImpactService#updateImpact} 에서 기존 레코드를 찾을 때 사용한다.</p>
     *
     * @param userId              행동 주체 사용자 ID
     * @param movieId             추천된 영화 ID
     * @param recLogId            연관 추천 로그 ID
     * @return 임팩트 레코드 (없으면 {@link Optional#empty()})
     */
    Optional<RecommendationImpact> findByUserIdAndMovieIdAndRecommendationLog_RecommendationLogId(
            String userId,
            String movieId,
            Long recLogId
    );

    /**
     * (userId, movieId) 조합으로 해당 영화에 대한 모든 추천 세션의 임팩트를 조회한다.
     *
     * <p>동일 영화가 서로 다른 세션에서 여러 번 추천된 경우, 각 세션의 임팩트 레코드를
     * 모두 반환한다. 추천 반복 노출 효과 분석 및 사용자별 영화 관심 누적 추적에 활용된다.</p>
     *
     * @param userId  행동 주체 사용자 ID
     * @param movieId 조회 대상 영화 ID
     * @return 해당 사용자·영화 조합의 임팩트 목록 (없으면 빈 리스트)
     */
    List<RecommendationImpact> findByUserIdAndMovieId(String userId, String movieId);

    /**
     * 행동 프로필 배치용 — 유저의 추천 수용률 집계.
     *
     * <p>전체 추천 레코드 수, 클릭 수, 시청 수를 한 번의 쿼리로 집계한다.
     * BehaviorProfileScheduler에서 recommendationAcceptanceRate 계산에 사용한다.</p>
     *
     * <p>반환 배열 구조: [총 추천 수(Long), 클릭 수(Long), 시청 수(Long)]</p>
     *
     * @param userId 집계 대상 사용자 ID
     * @return Object 배열 1건 [totalCount, clickedCount, watchedCount]
     */
    @Query("""
            SELECT COUNT(ri), SUM(CASE WHEN ri.clicked = true THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ri.watched = true THEN 1 ELSE 0 END)
            FROM RecommendationImpact ri
            WHERE ri.userId = :userId
            """)
    Object[] countImpactStatsByUserId(@Param("userId") String userId);

    /**
     * 행동 프로필 배치용 — 유저의 평균 세션 탐색 깊이(카드 클릭 수) 집계.
     *
     * <p>recommendation_log_id별 클릭 카드 수를 세션 단위로 그룹화하여 평균을 계산한다.
     * 값이 클수록 탐색적 성향의 유저임을 나타낸다.</p>
     *
     * @param userId 집계 대상 사용자 ID
     * @return 세션당 평균 클릭 카드 수 (데이터 없으면 null)
     */
    @Query("""
            SELECT AVG(sessionClick)
            FROM (
                SELECT COUNT(ri) AS sessionClick
                FROM RecommendationImpact ri
                WHERE ri.userId = :userId AND ri.clicked = true
                GROUP BY ri.recommendationLog.recommendationLogId
            )
            """)
    Double avgExplorationDepthByUserId(@Param("userId") String userId);

    /**
     * 클릭된 추천 임팩트 수를 반환한다 (전체 CTR 산출용).
     *
     * <p>전체 임팩트 수({@code count()}) 대비 이 값의 비율로
     * 추천 카드 클릭률(CTR, Click-Through Rate)을 계산한다.
     * AdminStatsService.getRecommendationPerformance()에서 사용된다.</p>
     *
     * @return clicked = true 인 레코드 수
     */
    long countByClickedTrue();

    /**
     * 위시리스트 추가된 추천 임팩트 수를 반환한다 (위시리스트 전환율 산출용).
     *
     * <p>전체 임팩트 수 대비 이 값의 비율로
     * 위시리스트 저장률(Save Rate)을 계산한다.
     * AdminStatsService.getRecommendationPerformance()에서 사용된다.</p>
     *
     * @return wishlisted = true 인 레코드 수
     */
    long countByWishlistedTrue();
}
