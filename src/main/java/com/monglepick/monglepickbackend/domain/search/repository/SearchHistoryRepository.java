package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 검색 이력 JPA 리포지토리 — search_history 테이블 데이터 접근.
 *
 * <p>사용자의 검색 키워드 이력을 저장하고 조회하는 기능을 제공한다.
 * 동일 사용자가 같은 키워드를 재검색하면 searchedAt을 갱신하는 UPSERT 방식으로 처리한다
 * (현재 서비스 정책 기준).</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndKeyword(String, String)} — UPSERT용 기존 이력 조회</li>
 *   <li>{@link #findTop20ByUserIdOrderBySearchedAtDesc(String)} — 최근 검색어 20개 조회</li>
 * </ul>
 */
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /**
     * 특정 사용자와 키워드 조합으로 기존 검색 이력을 조회한다.
     *
     * <p>검색어 저장 시 UPSERT 처리를 위해 사용된다.
     * 이미 검색 이력이 존재하면 searchedAt을 갱신하고,
     * 없으면 새 이력을 생성한다.</p>
     *
     * <p>동일 키워드를 하나의 최근 검색 항목으로 유지하려는
     * 서비스 정책을 구현할 때 사용하는 조회 메서드이다.</p>
     *
     * @param userId  검색한 사용자 ID
     * @param keyword 검색 키워드
     * @return 기존 검색 이력 (없으면 빈 Optional)
     */
    Optional<SearchHistory> findByUserIdAndKeyword(String userId, String keyword);

    /**
     * 특정 사용자의 최근 검색어를 searchedAt 내림차순으로 최대 20개 조회한다.
     *
     * <p>클라이언트 검색창의 "최근 검색어" 목록에 표시하기 위해 사용된다.
     * Spring Data JPA의 Top20 키워드와 OrderBy를 활용하여 쿼리를 자동 생성한다.</p>
     *
     * @param userId 검색 이력을 조회할 사용자 ID
     * @return 최근 검색 이력 목록 (최대 20개, searchedAt 내림차순)
     */
    List<SearchHistory> findTop20ByUserIdOrderBySearchedAtDesc(String userId);

    /**
     * 검색 키워드별 검색 사용자 수를 내림차순으로 반환한다 (인기 검색어 집계용).
     *
     * <p>COUNT(sh)는 현재 저장된 검색 이력 레코드 수를 의미한다.
     * result_count 합산 값도 함께 반환하여 검색 품질(결과 있음 여부) 판단에 활용한다.</p>
     *
     * <p>반환 배열 구조: [keyword(String), userCount(Long), totalResultCount(Long)]</p>
     *
     * @param pageable TOP N 제한용 페이지 요청 (예: PageRequest.of(0, 10))
     * @return Object 배열 목록 — 각 원소: [keyword, userCount, totalResultCount]
     */
    @Query("""
            SELECT sh.keyword, COUNT(sh), COALESCE(SUM(sh.resultCount), 0)
            FROM SearchHistory sh
            GROUP BY sh.keyword
            ORDER BY COUNT(sh) DESC
            """)
    List<Object[]> findTopKeywordsByUserCount(Pageable pageable);

    /**
     * 기간 내 키워드별 검색/결과/클릭 통계를 반환한다.
     *
     * <p>검색 이벤트(clickedMovieId IS NULL)만 검색 수와 결과 수의 분모로 사용한다.
     * 클릭 이벤트(clickedMovieId IS NOT NULL)는 전환율 계산용 클릭 수로만 별도 집계한다.</p>
     *
     * <p>반환 배열 구조:
     * [keyword(String), searchCount(Long), totalResultCount(Long), clickCount(Long)]</p>
     */
    @Query("""
            SELECT sh.keyword,
                   SUM(CASE WHEN sh.clickedMovieId IS NULL THEN 1 ELSE 0 END),
                   COALESCE(SUM(CASE WHEN sh.clickedMovieId IS NULL THEN sh.resultCount ELSE 0 END), 0),
                   SUM(CASE WHEN sh.clickedMovieId IS NOT NULL THEN 1 ELSE 0 END)
            FROM SearchHistory sh
            WHERE sh.searchedAt >= :since
            GROUP BY sh.keyword
            HAVING SUM(CASE WHEN sh.clickedMovieId IS NULL THEN 1 ELSE 0 END) > 0
            ORDER BY SUM(CASE WHEN sh.clickedMovieId IS NULL THEN 1 ELSE 0 END) DESC,
                     COALESCE(SUM(CASE WHEN sh.clickedMovieId IS NULL THEN sh.resultCount ELSE 0 END), 0) DESC
            """)
    List<Object[]> findKeywordStatsSince(
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /**
     * 기간 내 특정 키워드의 클릭 영화 통계를 반환한다.
     *
     * <p>반환 배열 구조: [clickedMovieId(String), clickCount(Long)]</p>
     */
    @Query("""
            SELECT sh.clickedMovieId, COUNT(sh)
            FROM SearchHistory sh
            WHERE sh.searchedAt >= :since
              AND sh.keyword = :keyword
              AND sh.clickedMovieId IS NOT NULL
            GROUP BY sh.clickedMovieId
            ORDER BY COUNT(sh) DESC
            """)
    List<Object[]> findClickedMoviesByKeywordSince(
            @Param("keyword") String keyword,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /**
     * 기간 내 특정 키워드들의 검색/클릭 이벤트 원본을 시간순으로 반환한다.
     *
     * <p>관리자 검색 분석에서 30분 inactivity 기준 검색 세션을 재구성할 때 사용한다.</p>
     */
    @Query("""
            SELECT sh
            FROM SearchHistory sh
            WHERE sh.searchedAt >= :since
              AND sh.keyword IN :keywords
            ORDER BY sh.keyword ASC, sh.userId ASC, sh.searchedAt ASC, sh.searchHistoryId ASC
            """)
    List<SearchHistory> findSessionEventsSince(
            @Param("since") LocalDateTime since,
            @Param("keywords") Collection<String> keywords
    );

    /**
     * 기준 시각 이후의 검색 이벤트 수를 반환한다.
     *
     * <p>클릭 로그는 같은 테이블에 저장되므로 clickedMovieId 가 NULL 인 레코드만
     * 실제 검색으로 간주한다.</p>
     */
    long countBySearchedAtGreaterThanEqualAndClickedMovieIdIsNull(LocalDateTime since);

    /**
     * 기준 시각 이후의 무결과 검색 이벤트 수를 반환한다.
     *
     * <p>result_count = 0 이고 clickedMovieId 가 NULL 인 레코드만 집계한다.</p>
     */
    long countByResultCountAndSearchedAtGreaterThanEqualAndClickedMovieIdIsNull(
            int resultCount,
            LocalDateTime since
    );

    /**
     * result_count = 0인 검색 이력 수를 반환한다 (무결과 검색 비율 산출용).
     *
     * <p>전체 검색 수 대비 이 값의 비율로 검색 실패율을 계산한다.
     * 검색 품질 지표(SearchQualityResponse)의 zeroResultCount 필드에 사용된다.</p>
     *
     * @param resultCount 조회 기준 결과 수 (0을 전달하면 무결과 검색 기록 수 반환)
     * @return 해당 resultCount와 일치하는 검색 이력 수
     */
    long countByResultCount(int resultCount);
}
