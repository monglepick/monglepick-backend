package com.monglepick.monglepickbackend.domain.reward.constants;

/**
 * 영화 티켓 응모 entry 상태 — movie_ticket_entry.status 컬럼 값 (2026-04-14 신규).
 *
 * <h3>Entry 라이프사이클</h3>
 * <pre>
 *   PENDING ──(추첨 배치 + 당첨)──→ WON
 *           └(추첨 배치 + 미당첨)─→ LOST
 * </pre>
 *
 * <ul>
 *   <li>{@link #PENDING} — 응모 접수, 추첨 대기.</li>
 *   <li>{@link #WON}     — 당첨. 별도 알림 발송 큐에 적재 (현재 MVP는 알림 미구현).</li>
 *   <li>{@link #LOST}    — 미당첨.</li>
 * </ul>
 */
public enum MovieTicketEntryStatus {
    /** 응모 접수, 추첨 대기 */
    PENDING,
    /** 당첨 확정 */
    WON,
    /** 미당첨 */
    LOST
}
