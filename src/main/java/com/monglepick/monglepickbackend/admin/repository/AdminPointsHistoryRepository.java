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
