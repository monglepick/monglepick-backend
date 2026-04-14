package com.monglepick.monglepickbackend.domain.reward.constants;

/**
 * 영화 티켓 추첨 회차 상태 — movie_ticket_lottery.status 컬럼 값 (2026-04-14 신규).
 *
 * <h3>회차 라이프사이클</h3>
 * <pre>
 *   PENDING ──(매월 1일 0시 배치)──→ DRAWING ──(추첨 완료)──→ COMPLETED
 * </pre>
 *
 * <ul>
 *   <li>{@link #PENDING}    — 응모 접수 중. 당월 회차의 기본 상태. useItem(응모권) 으로 entry 추가됨.</li>
 *   <li>{@link #DRAWING}    — 배치가 추첨 작업을 시작하면 잠시 거치는 상태 (실패/롤백 시 PENDING 으로 복귀).</li>
 *   <li>{@link #COMPLETED}  — 추첨 완료. 당첨자 status=WON, 나머지 LOST 확정. 더 이상 entry 추가 불가.</li>
 * </ul>
 */
public enum MovieTicketLotteryStatus {
    /** 응모 접수 중 */
    PENDING,
    /** 추첨 진행 중 (배치 실행) */
    DRAWING,
    /** 추첨 완료 */
    COMPLETED
}
