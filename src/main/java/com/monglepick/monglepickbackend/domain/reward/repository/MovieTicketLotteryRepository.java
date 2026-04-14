package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketLottery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 영화 티켓 추첨 회차 리포지토리 (2026-04-14 신규, 후속 #3).
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
}
