package com.monglepick.monglepickbackend.admin.dto;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketEntry;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketLottery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 — 영화 티켓 추첨 관리 DTO 모음 (2026-04-28 신규).
 *
 * <p>관리자 페이지 "결제/포인트 → 추첨 관리" 서브탭에서 사용한다.
 * 회차 목록/상세, winner_count·notes 수정, 수동 추첨, 응모자 명단 조회 5개 EP 의 데이터 모델을
 * inner record 로 정의한다.</p>
 *
 * <h3>설계 의도</h3>
 * <ul>
 *   <li>도메인 {@link com.monglepick.monglepickbackend.domain.reward.dto.MovieTicketLotteryDto}
 *       는 사용자 본인 응모 현황만 노출하므로, 관리자 화면 전용 DTO 를 분리한다.</li>
 *   <li>운영자가 회차별 통계(응모자 수, 당첨자 수, 미당첨자 수)와 응모자 식별 정보(닉네임/이메일) 를
 *       함께 보고 결정을 내리도록 풍부한 필드를 제공한다.</li>
 * </ul>
 */
public final class AdminLotteryDto {

    /** 인스턴스 생성 방지 — 유틸리티 클래스 패턴. */
    private AdminLotteryDto() {
    }

    // ======================== 회차 ========================

    /**
     * 회차 요약 응답 (관리자 목록·상세 공용).
     *
     * <p>목록 화면은 {@code totalEntries} / {@code wonCount} / {@code lostCount} 통계와 함께
     * 노출하며, 상세 화면도 동일한 record 를 재사용한다 (응모자 명단은 별도 EP 로 페이징).</p>
     *
     * @param lotteryId       회차 PK
     * @param cycleYearMonth  회차 식별자 ('YYYY-MM')
     * @param status          회차 상태 (PENDING/DRAWING/COMPLETED)
     * @param winnerCount     설정된 당첨자 수 (회차별 가변)
     * @param totalEntries    응모 총 건수 (PENDING + WON + LOST)
     * @param wonCount        실제 당첨된 entry 수 (COMPLETED 이전엔 0)
     * @param lostCount       미당첨 entry 수
     * @param drawnAt         추첨 완료 시각 (null = 아직 미추첨)
     * @param notes           운영자 메모
     * @param createdAt       회차 생성 시각
     */
    public record LotterySummary(
            Long lotteryId,
            String cycleYearMonth,
            MovieTicketLotteryStatus status,
            Integer winnerCount,
            long totalEntries,
            long wonCount,
            long lostCount,
            LocalDateTime drawnAt,
            String notes,
            LocalDateTime createdAt
    ) {
        /**
         * Entity + 회차별 entry 통계로부터 LotterySummary 를 생성한다.
         *
         * @param lottery      회차 Entity
         * @param totalEntries 회차의 전체 entry 수
         * @param wonCount     WON 상태 entry 수
         * @param lostCount    LOST 상태 entry 수
         */
        public static LotterySummary from(MovieTicketLottery lottery,
                                          long totalEntries,
                                          long wonCount,
                                          long lostCount) {
            return new LotterySummary(
                    lottery.getLotteryId(),
                    lottery.getCycleYearMonth(),
                    lottery.getStatus(),
                    lottery.getWinnerCount(),
                    totalEntries,
                    wonCount,
                    lostCount,
                    lottery.getDrawnAt(),
                    lottery.getNotes(),
                    lottery.getCreatedAt()
            );
        }
    }

    /**
     * 회차 수정 요청 — winner_count / notes 부분 업데이트.
     *
     * <p>두 필드 모두 nullable 로 받아 null 인 필드는 무시한다 (PATCH 시멘틱).
     * winner_count 범위 검증은 상한선 1000 으로 잡는다 (현실 운영 시 5~수십 명).</p>
     *
     * @param winnerCount 새 당첨자 수 (1~1000, null 이면 미변경)
     * @param notes       운영자 메모 (최대 500자, null 이면 미변경)
     */
    public record LotteryUpdateRequest(
            @Min(value = 1, message = "당첨자 수는 1 이상이어야 합니다")
            @Max(value = 1000, message = "당첨자 수는 1000 이하여야 합니다")
            Integer winnerCount,
            @Size(max = 500, message = "메모는 500자 이하여야 합니다")
            String notes
    ) {}

    /**
     * 수동 추첨 응답 — 추첨 완료 후 컨트롤러가 반환.
     *
     * @param lotteryId  추첨한 회차 PK
     * @param cycleYearMonth 회차 식별자
     * @param drawnCount 이번 호출로 당첨 처리된 entry 수
     * @param status     추첨 후 회차 상태 (COMPLETED 가 정상)
     */
    public record ManualDrawResponse(
            Long lotteryId,
            String cycleYearMonth,
            int drawnCount,
            MovieTicketLotteryStatus status
    ) {}

    // ======================== 응모자 ========================

    /**
     * 회차별 응모자 항목 — 관리자 응모자 명단 화면.
     *
     * <p>userId 만으로는 운영자가 누구인지 알기 어려우므로 닉네임·이메일 도 함께 노출한다.
     * UserMapper.findById 를 이용해 별도 lookup 으로 채운다 (entry 수가 많아도 회차당 수만 건 이내
     * 가정이라 단순 N+1 없이 batch fetch 한다).</p>
     *
     * @param entryId        entry PK
     * @param userId         응모자 ID
     * @param nickname       닉네임 (없으면 null — 탈퇴 또는 lookup 실패)
     * @param email          이메일 (없으면 null)
     * @param status         entry 상태 (PENDING/WON/LOST)
     * @param userItemId     소비된 응모권 user_item PK (감사 추적)
     * @param enrolledAt     응모 시각
     */
    public record LotteryEntryItem(
            Long entryId,
            String userId,
            String nickname,
            String email,
            MovieTicketEntryStatus status,
            Long userItemId,
            LocalDateTime enrolledAt
    ) {
        /**
         * Entity + 사용자 정보(닉네임/이메일) 로부터 항목을 생성한다.
         *
         * <p>{@code nickname}/{@code email} 은 사전에 lookup 하여 전달한다 — 본 메서드는 단순 매핑.
         * userItem 이 LAZY 라서 필드 호출 시 fetch 가 일어나는데, 호출 측에서
         * userItem.userItemId 만 접근하므로 select-by-id 단순 쿼리 1회로 끝난다.</p>
         */
        public static LotteryEntryItem from(MovieTicketEntry entry, String nickname, String email) {
            return new LotteryEntryItem(
                    entry.getEntryId(),
                    entry.getUserId(),
                    nickname,
                    email,
                    entry.getStatus(),
                    /* userItem.id 만 노출 — 응모권 자체 정보까지 보일 필요는 없음 */
                    entry.getUserItem() != null ? entry.getUserItem().getUserItemId() : null,
                    entry.getCreatedAt()
            );
        }
    }

    /**
     * 회차 ID 미존재 시 자동 생성용 — 일부 관리자 시나리오에서 미래 회차를 사전 생성하고 싶을 때 사용.
     *
     * <p>현재 MVP 에서는 EP 로 노출하지 않지만, 후속 단계에서 "회차 강제 생성" 버튼을 도입할 경우를
     * 대비해 record 만 미리 정의해 둔다.</p>
     *
     * @param cycleYearMonth 'YYYY-MM' 형식
     * @param winnerCount    당첨자 수 (기본 5)
     */
    public record CreateLotteryRequest(
            @NotNull
            @Size(min = 7, max = 7, message = "회차 형식은 YYYY-MM 이어야 합니다")
            String cycleYearMonth,
            @Min(value = 1, message = "당첨자 수는 1 이상이어야 합니다")
            @Max(value = 1000, message = "당첨자 수는 1000 이하여야 합니다")
            Integer winnerCount
    ) {}
}
