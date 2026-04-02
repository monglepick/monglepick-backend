package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.SearchDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 일일 검색 통계 JPA 리포지토리 — search_daily_stats 테이블 데이터 접근.
 *
 * <p>날짜 범위별 검색 통계 집계를 지원한다.
 * 관리자 통계 탭의 검색 품질 분석 및 트렌드 추이에서 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #sumSearchCountByDateRange(LocalDate, LocalDate)} — 기간 내 총 검색 횟수</li>
 *   <li>{@link #countZeroResultKeywordsByDateRange(LocalDate, LocalDate)} — 기간 내 검색량 0 키워드 수 (미구현 시 0 반환)</li>
 *   <li>{@link #findTopZeroResultKeywords(LocalDate, LocalDate)} — 결과 없음 키워드 목록</li>
 * </ul>
 */
public interface SearchDailyStatRepository extends JpaRepository<SearchDailyStat, Long> {

    /**
     * 지정 날짜 범위 내 전체 검색 횟수 합계를 반환한다.
     *
     * <p>관리자 통계 탭에서 기간 내 totalSearches 계산에 사용된다.</p>
     *
     * @param from 시작 날짜 (포함)
     * @param to   종료 날짜 (포함)
     * @return 기간 내 검색 횟수 합계 (레코드 없으면 null)
     */
    @Query("SELECT SUM(s.searchCount) FROM SearchDailyStat s " +
           "WHERE s.searchDate BETWEEN :from AND :to")
    Long sumSearchCountByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 지정 날짜 범위 내 검색 횟수가 1 이하인 키워드(결과 없음 의심) 수를 반환한다.
     *
     * <p>실제 "결과 없음" 이벤트를 기록하는 컬럼이 없으므로,
     * 검색 횟수가 매우 적은 키워드(1회 이하)를 근사치로 활용한다.</p>
     *
     * @param from 시작 날짜 (포함)
     * @param to   종료 날짜 (포함)
     * @return 결과 없음 의심 키워드 수
     */
    @Query("SELECT COUNT(DISTINCT s.keyword) FROM SearchDailyStat s " +
           "WHERE s.searchDate BETWEEN :from AND :to AND s.searchCount <= 1")
    long countZeroResultKeywordsByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 지정 날짜 범위 내 검색 횟수가 1 이하인 키워드 목록을 최대 10개 반환한다.
     *
     * <p>관리자가 "검색 결과 없음 TOP 키워드"를 파악하여 콘텐츠 보강에 활용할 수 있도록 제공한다.
     * 실제 zero-result 컬럼이 없으므로 searchCount <= 1을 근사치로 사용한다.</p>
     *
     * @param from 시작 날짜 (포함)
     * @param to   종료 날짜 (포함)
     * @return 결과 없음 의심 키워드 목록 (최대 10개)
     */
    @Query(value = "SELECT s.keyword FROM SearchDailyStat s " +
                   "WHERE s.searchDate BETWEEN :from AND :to AND s.searchCount <= 1 " +
                   "GROUP BY s.keyword ORDER BY SUM(s.searchCount) ASC",
           countQuery = "SELECT COUNT(DISTINCT s.keyword) FROM SearchDailyStat s " +
                        "WHERE s.searchDate BETWEEN :from AND :to AND s.searchCount <= 1")
    List<String> findTopZeroResultKeywords(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
