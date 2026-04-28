package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketLottery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 영화 티켓 추첨 회차 리포지토리 (2026-04-14 신규, 후속 #3).
 *
 * <p>2026-04-28 — 관리자 추첨 관리 EP 도입에 따라 페이징/상태 필터 메서드를 보강한다.
 * 관리자 화면이 회차 목록을 cycle DESC 로 표시하기 때문에 정렬은 컨트롤러 측 Pageable
 * 이 결정하도록 했다.</p>
 */
public interface MovieTicketLotteryRepository extends JpaRepository<MovieTicketLottery, Long> {

    /**
     * 특정 회차(yearMonth) 조회 — 단건. 회차당 1개만 존재(UQ).
     *
     * @param cycleYearMonth 'YYYY-MM' 형식 (예: "2026-04")
     */
    Optional<MovieTicketLottery> findByCycleYearMonth(String cycleYearMonth);

    /**
     * 특정 회차를 비관적 락으로 조회 — 추첨 배치가 사용.
     *
     * <p>SELECT ... FOR UPDATE 로 다른 트랜잭션의 status 변경을 차단.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM MovieTicketLottery l WHERE l.cycleYearMonth = :cycleYearMonth")
    Optional<MovieTicketLottery> findByCycleYearMonthWithLock(@Param("cycleYearMonth") String cycleYearMonth);

    /**
     * 추첨 대기 중인 회차 목록 조회 — 배치 스캔용.
     *
     * @param status 보통 PENDING 또는 DRAWING (재시도 시 DRAWING 도 포함 가능)
     */
    List<MovieTicketLottery> findAllByStatus(MovieTicketLotteryStatus status);

    /**
     * 회차 페이징 조회 — 관리자 추첨 관리 화면 (2026-04-28 신규).
     *
     * <p>status 가 null 이면 전체 회차, 값이 있으면 해당 상태로 필터링한다.</p>
     *
     * @param status   필터링할 상태 (nullable — null 이면 전체)
     * @param pageable 페이징 정보 (정렬은 보통 cycleYearMonth DESC)
     * @return 회차 페이지
     */
    @Query(
            value = "SELECT l FROM MovieTicketLottery l "
                    + "WHERE (:status IS NULL OR l.status = :status)",
            countQuery = "SELECT COUNT(l) FROM MovieTicketLottery l "
                    + "WHERE (:status IS NULL OR l.status = :status)"
    )
    Page<MovieTicketLottery> findAllForAdmin(
            @Param("status") MovieTicketLotteryStatus status,
            Pageable pageable
    );
}
