package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영화 티켓 응모 기록 — movie_ticket_entry 테이블 매핑 (2026-04-14 신규, 후속 #3).
 *
 * <p>유저가 응모권({@code APPLY_MOVIE_TICKET}) 을 사용할 때마다 1건이 INSERT 된다.
 * 동일 회차에 같은 유저가 여러 응모권을 사용하면 그만큼 entry 가 누적되어 당첨 확률이 비례해서 올라간다.</p>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li><b>userId VARCHAR FK</b> — Phase 1 원칙 (UserItem 과 동일).</li>
 *   <li><b>userItem @ManyToOne</b> — 어느 응모권으로 응모했는지 감사 추적. LAZY 로딩.</li>
 *   <li><b>lottery @ManyToOne</b> — 어느 회차에 들어갔는지. 응모 시점에 결정 (PENDING 회차).</li>
 * </ul>
 *
 * <h3>인덱스</h3>
 * <ul>
 *   <li>{@code idx_entry_lottery_status} (lottery_id, status) — 배치가 회차별 PENDING entry 빠르게 스캔</li>
 *   <li>{@code idx_entry_user} (user_id) — 유저 응모 현황 조회</li>
 * </ul>
 */
@Entity
@Table(
        name = "movie_ticket_entry",
        indexes = {
                @Index(name = "idx_entry_lottery_status", columnList = "lottery_id, status"),
                @Index(name = "idx_entry_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MovieTicketEntry extends BaseTimeEntity {

    /** PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    /** 응모자 ID (Phase 1 원칙: VARCHAR FK) */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 사용된 응모권 보유 레코드 (FK user_items.user_item_id).
     *
     * <p>감사·정합성 추적용. UserItem.status=USED 로 전환된 시점에 entry INSERT 가 함께 일어난다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_id", nullable = false)
    private UserItem userItem;

    /** 응모한 추첨 회차 (FK movie_ticket_lottery.lottery_id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lottery_id", nullable = false)
    private MovieTicketLottery lottery;

    /** 응모 결과 상태 — 추첨 전 PENDING, 배치 후 WON/LOST */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private MovieTicketEntryStatus status = MovieTicketEntryStatus.PENDING;

    // ──────────────────────────────────────────────
    // 도메인 메서드 — 상태 전이
    // ──────────────────────────────────────────────

    /** 당첨 처리 — PENDING → WON. */
    public void markWon() {
        if (status != MovieTicketEntryStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 entry 를 WON 처리할 수 없습니다: " + status);
        }
        this.status = MovieTicketEntryStatus.WON;
    }

    /** 미당첨 처리 — PENDING → LOST. */
    public void markLost() {
        if (status != MovieTicketEntryStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 entry 를 LOST 처리할 수 없습니다: " + status);
        }
        this.status = MovieTicketEntryStatus.LOST;
    }
}
