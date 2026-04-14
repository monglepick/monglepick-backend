package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 영화 티켓 응모 entry 리포지토리 (2026-04-14 신규, 후속 #3).
 */
public interface MovieTicketEntryRepository extends JpaRepository<MovieTicketEntry, Long> {

    /**
     * 특정 회차의 PENDING entry 전체 조회 — 추첨 배치가 무작위 선정용으로 로드.
     *
     * <p>회차당 entry 가 수만 건이 될 수 있는 가능성이 있으나 MVP 에서는 in-memory shuffle 로
     * 충분. 운영 규모가 커지면 페이지 단위 sampling 으로 전환.</p>
     *
     * @param lotteryId 회차 PK
     * @return PENDING entry 리스트
     */
    @Query("SELECT e FROM MovieTicketEntry e "
            + "WHERE e.lottery.lotteryId = :lotteryId AND e.status = 'PENDING'")
    List<MovieTicketEntry> findPendingByLotteryId(@Param("lotteryId") Long lotteryId);

    /**
     * 특정 회차의 entry 개수 — 운영 통계용.
     */
    @Query("SELECT COUNT(e) FROM MovieTicketEntry e "
            + "WHERE e.lottery.lotteryId = :lotteryId AND e.status = :status")
    long countByLotteryAndStatus(@Param("lotteryId") Long lotteryId,
                                 @Param("status") MovieTicketEntryStatus status);

    /**
     * 유저 응모 현황 — JOIN FETCH 로 lottery + userItem 동반 로드.
     *
     * <p>"내 응모 현황" 페이지에서 회차 정보(yearMonth, drawnAt) 까지 한 번에 표시하기 위함.</p>
     */
    @Query(
            value = "SELECT e FROM MovieTicketEntry e JOIN FETCH e.lottery "
                    + "WHERE e.userId = :userId",
            countQuery = "SELECT COUNT(e) FROM MovieTicketEntry e WHERE e.userId = :userId"
    )
    Page<MovieTicketEntry> findByUserIdWithLottery(@Param("userId") String userId, Pageable pageable);
}
