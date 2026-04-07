package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 검색 이력 JPA 리포지토리 — search_history 테이블 데이터 접근.
 *
 * <p>사용자의 검색 키워드 이력을 저장하고 조회하는 기능을 제공한다.
 * 동일 사용자가 같은 키워드를 재검색하면 searchedAt을 갱신하는 UPSERT 방식으로 처리한다
 * (user_id, keyword UNIQUE 제약 — 애플리케이션 레벨에서 처리).</p>
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
     * <p>search_history 테이블의 (user_id, keyword) UNIQUE 제약을
     * 애플리케이션 레벨에서 안전하게 처리하기 위한 메서드이다.</p>
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
     * <p>search_history는 (user_id, keyword) UNIQUE 제약이 있으므로,
     * COUNT(sh)는 해당 키워드를 검색한 고유 사용자 수를 의미한다.
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
