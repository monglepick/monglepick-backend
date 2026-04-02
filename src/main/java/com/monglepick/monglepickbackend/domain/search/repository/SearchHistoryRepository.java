package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

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
 *   <li>{@link #findByUser_UserIdAndKeyword(String, String)} — UPSERT용 기존 이력 조회</li>
 *   <li>{@link #findTop20ByUser_UserIdOrderBySearchedAtDesc(String)} — 최근 검색어 20개 조회</li>
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
    Optional<SearchHistory> findByUser_UserIdAndKeyword(String userId, String keyword);

    /**
     * 특정 사용자의 최근 검색어를 searchedAt 내림차순으로 최대 20개 조회한다.
     *
     * <p>클라이언트 검색창의 "최근 검색어" 목록에 표시하기 위해 사용된다.
     * Spring Data JPA의 Top20 키워드와 OrderBy를 활용하여 쿼리를 자동 생성한다.</p>
     *
     * @param userId 검색 이력을 조회할 사용자 ID
     * @return 최근 검색 이력 목록 (최대 20개, searchedAt 내림차순)
     */
    List<SearchHistory> findTop20ByUser_UserIdOrderBySearchedAtDesc(String userId);
}
