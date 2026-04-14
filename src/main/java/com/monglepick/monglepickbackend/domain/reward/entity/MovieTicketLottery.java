package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영화 티켓 추첨 회차 마스터 — movie_ticket_lottery 테이블 매핑 (2026-04-14 신규, 후속 #3).
 *
 * <p>응모권({@code PointItemType.APPLY_MOVIE_TICKET}) 사용 시 entry 가 이 회차에 누적되며,
 * {@code MovieTicketLotteryBatch} 가 매월 1일 0시에 직전 회차를 추첨한다.</p>
 *
 * <h3>회차 단위</h3>
 * <p>{@code cycleYearMonth} (`YYYY-MM` 형식 문자열) 로 회차를 식별한다.
 * 한 회차당 1개 row 만 존재 (UQ). 응모 접수 → DRAWING(배치 진입) → COMPLETED(추첨 완료) 전이.</p>
 *
 * <h3>인덱스/제약</h3>
 * <ul>
 *   <li>UQ: {@code cycle_year_month} — 회차 중복 생성 방지</li>
 *   <li>IDX: {@code (status)} — 배치가 PENDING 회차 빠르게 스캔</li>
 * </ul>
 */
@Entity
@Table(
        name = "movie_ticket_lottery",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lottery_cycle", columnNames = "cycle_year_month")
        },
        indexes = {
                @Index(name = "idx_lottery_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MovieTicketLottery extends BaseTimeEntity {

    /** PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lottery_id")
    private Long lotteryId;

    /**
     * 회차 식별자 — `YYYY-MM` (예: "2026-04").
     *
     * <p>운영상 한 달 1회차 단위로 추첨한다. 회차 중복 INSERT는 UQ 로 차단.</p>
     */
    @Column(name = "cycle_year_month", length = 7, nullable = false)
    private String cycleYearMonth;

    /** 회차 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private MovieTicketLotteryStatus status = MovieTicketLotteryStatus.PENDING;

    /**
     * 당첨자 수 — 회차 단위로 결정(기본 5명).
     *
     * <p>운영자가 회차별로 조정 가능하도록 컬럼화. 추첨 배치는 이 값을 기준으로 무작위 선정.</p>
     */
    @Column(name = "winner_count", nullable = false)
    @Builder.Default
    private Integer winnerCount = 5;

    /** 추첨 완료 시각 (NULL = 아직 미추첨) */
    @Column(name = "drawn_at")
    private LocalDateTime drawnAt;

    /** 비고 — 운영자 메모, 추첨 이슈 기록 등 */
    @Column(name = "notes", length = 500)
    private String notes;

    // ──────────────────────────────────────────────
    // 도메인 메서드 — 상태 전이
    // ──────────────────────────────────────────────

    /**
     * 추첨 시작 — PENDING → DRAWING.
     *
     * <p>배치가 추첨 작업 진입 시 호출. 실패 시 markPending()으로 복귀.</p>
     */
    public void markDrawing() {
        if (status != MovieTicketLotteryStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 회차의 추첨을 시작할 수 없습니다: " + status);
        }
        this.status = MovieTicketLotteryStatus.DRAWING;
    }

    /** 추첨 완료 — DRAWING → COMPLETED. 추첨 시각 기록. */
    public void markCompleted() {
        if (status != MovieTicketLotteryStatus.DRAWING) {
            throw new IllegalStateException("DRAWING 상태가 아닌 회차를 COMPLETED 전이할 수 없습니다: " + status);
        }
        this.status = MovieTicketLotteryStatus.COMPLETED;
        this.drawnAt = LocalDateTime.now();
    }

    /** 추첨 실패 시 PENDING 으로 복귀 (배치 재시도용). */
    public void markPending() {
        this.status = MovieTicketLotteryStatus.PENDING;
    }
}
