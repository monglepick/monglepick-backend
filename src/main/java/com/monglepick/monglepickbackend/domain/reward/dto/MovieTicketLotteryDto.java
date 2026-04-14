package com.monglepick.monglepickbackend.domain.reward.dto;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketEntry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 영화 티켓 추첨 DTO 모음 (2026-04-14 신규, 후속 #3).
 *
 * <p>유저 응모 현황 조회에 사용된다. 회차 정보(yearMonth/drawnAt) 와 응모 결과(status) 를 함께 노출.</p>
 */
public final class MovieTicketLotteryDto {

    private MovieTicketLotteryDto() {
    }

    /**
     * 응모 entry 단건 응답.
     *
     * @param entryId         entry PK
     * @param cycleYearMonth  회차 식별자 ('YYYY-MM')
     * @param status          PENDING/WON/LOST
     * @param enrolledAt      응모 시각 (entry.createdAt)
     * @param drawnAt         회차 추첨 완료 시각 (PENDING 이면 null)
     */
    public record EntryResponse(
            Long entryId,
            String cycleYearMonth,
            MovieTicketEntryStatus status,
            LocalDateTime enrolledAt,
            LocalDateTime drawnAt
    ) {
        public static EntryResponse from(MovieTicketEntry e) {
            return new EntryResponse(
                    e.getEntryId(),
                    e.getLottery().getCycleYearMonth(),
                    e.getStatus(),
                    e.getCreatedAt(),
                    e.getLottery().getDrawnAt()
            );
        }
    }

    /**
     * 페이지 응답 (Spring Page 호환).
     */
    public record EntryPageResponse(
            List<EntryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
