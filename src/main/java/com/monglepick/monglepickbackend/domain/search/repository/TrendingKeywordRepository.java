package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.TrendingKeyword;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 인기 검색어 JPA 리포지토리 — trending_keywords 테이블 데이터 접근.
 *
 * <p>전체 누적 인기 검색어 조회를 지원한다.
 * 관리자 통계 탭의 인기 검색어 목록 조회에서 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByOrderBySearchCountDesc(Pageable)} — 검색 횟수 내림차순 TOP N 조회</li>
 * </ul>
 */
public interface TrendingKeywordRepository extends JpaRepository<TrendingKeyword, Long> {

    /**
     * 검색 횟수 내림차순으로 인기 검색어를 페이징 조회한다.
     *
     * <p>관리자 통계 탭의 "인기 검색어 TOP N" 목록 조회에 사용한다.
     * Pageable의 pageSize로 반환 개수(limit)를 제어한다.
     * 예: PageRequest.of(0, 10)으로 TOP 10만 조회 가능.</p>
     *
     * @param pageable 페이징 정보 (pageSize = 조회할 키워드 개수)
     * @return 검색 횟수 내림차순으로 정렬된 인기 검색어 목록
     */
    List<TrendingKeyword> findByOrderBySearchCountDesc(Pageable pageable);
}
