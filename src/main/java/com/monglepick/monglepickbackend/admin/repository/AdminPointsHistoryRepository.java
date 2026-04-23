package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 전용 포인트 이력 리포지토리.
 *
 * <p>관리자 페이지 "결제/포인트 → 포인트 관리" 탭에서 모든 사용자의 포인트 변동 이력을
 * 조회하기 위한 쿼리 메서드를 제공한다. 도메인 레이어의 {@code PointsHistoryRepository}는
 * 사용자별 조회(findByUserIdOrderByCreatedAtDesc)에 특화되어 있으므로, 관리자용 전체 이력
 * 조회는 별도 리포지토리로 분리한다.</p>
 *
 * <h3>주요 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findAllByOrderByCreatedAtDesc} — 전체 사용자 이력 최신순 페이징 조회</li>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc} — 특정 사용자 이력 최신순 페이징 조회</li>
 * </ul>
 *
 * <h3>INSERT-ONLY 정책</h3>
 * <p>{@code PointsHistory}는 @PreUpdate/@PreRemove로 UPDATE/DELETE가 차단되어 있으므로,
 * 이 리포지토리에서도 읽기 전용 쿼리만 정의한다.</p>
 */
public interface AdminPointsHistoryRepository extends JpaRepository<PointsHistory, Long> {

    /**
     * 전체 사용자의 포인트 변동 이력을 최신순으로 페이징 조회한다.
     *
     * <p>관리자 화면에서 특정 사용자 필터 없이 시스템 전체의 포인트 변동 현황을
     * 감사(audit)할 때 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size)
     * @return 전체 포인트 변동 이력 페이지 (최신순)
     */
    Page<PointsHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 사용자의 포인트 변동 이력을 최신순으로 페이징 조회한다.
     *
     * <p>관리자 화면에서 사용자 ID를 지정하여 해당 사용자의 포인트 변동 내역을
     * 추적할 때 사용한다. 도메인 레이어의 동일 메서드와 시그니처는 같지만,
     * 관리자 권한 검증 흐름에서 사용되므로 별도 리포지토리로 분리한다.</p>
     *
     * @param userId   사용자 ID (VARCHAR(50))
     * @param pageable 페이지 정보
     * @return 해당 사용자의 포인트 변동 이력 페이지 (최신순)
     */
    Page<PointsHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 관리자 포인트 이력 탭의 복합 필터 검색 (2026-04-23 추가).
     *
     * <p>사용자 ID / 생성일 범위(fromDate~toDate)를 선택적으로 조합해 검색한다.
     * null 파라미터는 WHERE 조건에서 자동 제외되므로, 사용자·기간 어떤 조합이든 지원한다.</p>
     *
     * <p>날짜 필터 의미론 — {@code fromDate} 는 inclusive(이상), {@code toDate} 는 exclusive(미만).
     * 프로젝트 전 Admin Repository 의 날짜 범위 규약과 일치.</p>
     *
     * @param userId    사용자 ID (nullable — 생략 시 전체 사용자)
     * @param fromDate  포인트 변동일 시작 inclusive (nullable)
     * @param toDate    포인트 변동일 종료 exclusive (nullable)
     * @param pageable  페이지 정보
     * @return 조건에 매칭되는 포인트 이력 페이지 (최신순)
     */
    @Query(
            value = "SELECT h FROM PointsHistory h " +
                    "WHERE (:userId   IS NULL OR h.userId = :userId) " +
                    "  AND (:fromDate IS NULL OR h.createdAt >= :fromDate) " +
                    "  AND (:toDate   IS NULL OR h.createdAt <  :toDate) " +
                    "ORDER BY h.createdAt DESC",
            countQuery = "SELECT COUNT(h) FROM PointsHistory h " +
                    "WHERE (:userId   IS NULL OR h.userId = :userId) " +
                    "  AND (:fromDate IS NULL OR h.createdAt >= :fromDate) " +
                    "  AND (:toDate   IS NULL OR h.createdAt <  :toDate)"
    )
    Page<PointsHistory> searchByFilters(
            @Param("userId") String userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    // ═══ 포인트 경제 통계 집계 쿼리 ═══

    /**
     * 포인트 유형별 건수와 포인트 합계를 집계한다.
     *
     * <p>반환 배열: [pointType(String), count(Long), totalAmount(Long)]</p>
     */
    @Query("SELECT p.pointType, COUNT(p), COALESCE(SUM(ABS(p.pointChange)), 0) " +
           "FROM PointsHistory p GROUP BY p.pointType ORDER BY COUNT(p) DESC")
    List<Object[]> countAndSumGroupByPointType();

    /**
     * 지정 기간 내 발행(earn+bonus) 포인트 합계를 조회한다.
     */
    @Query("SELECT COALESCE(SUM(p.pointChange), 0) FROM PointsHistory p " +
           "WHERE p.pointType IN ('earn', 'bonus') " +
           "AND p.createdAt >= :start AND p.createdAt < :end")
    long sumIssuedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 지정 기간 내 소비(spend) 포인트 합계를 조회한다 (절대값 반환).
     */
    @Query("SELECT COALESCE(SUM(ABS(p.pointChange)), 0) FROM PointsHistory p " +
           "WHERE p.pointType = 'spend' " +
           "AND p.createdAt >= :start AND p.createdAt < :end")
    long sumSpentBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 전체 발행 포인트 합계 (earn + bonus).
     */
    @Query("SELECT COALESCE(SUM(p.pointChange), 0) FROM PointsHistory p " +
           "WHERE p.pointType IN ('earn', 'bonus')")
    long sumTotalIssued();

    /**
     * 전체 소비 포인트 합계 (spend, 절대값).
     */
    @Query("SELECT COALESCE(SUM(ABS(p.pointChange)), 0) FROM PointsHistory p " +
           "WHERE p.pointType = 'spend'")
    long sumTotalSpent();
}
