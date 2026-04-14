package com.monglepick.monglepickbackend.domain.reward.constants;

/**
 * 보유 아이템 상태 — user_items.status 컬럼 값.
 *
 * <h3>상태 전이</h3>
 * <pre>
 *   ACTIVE ──(equip)──→ EQUIPPED ──(unequip)──→ ACTIVE
 *      │                   │
 *      │                   └──(expire 배치)──→ EXPIRED
 *      │
 *      ├──(use: 힌트/응모권)──→ USED
 *      └──(expire 배치)──→ EXPIRED
 * </pre>
 *
 * <ul>
 *   <li>{@link #ACTIVE}    — 보유 중, 미착용/미사용. 착용·사용 가능.</li>
 *   <li>{@link #EQUIPPED}  — 착용 중 (아바타/배지 한정). 카테고리당 1개만 가능.</li>
 *   <li>{@link #USED}      — 사용 완료 (힌트 소비/응모권 사용 등). 재활성 불가.</li>
 *   <li>{@link #EXPIRED}   — 유효기간 경과. 자동 해제되며 재활성 불가.</li>
 * </ul>
 */
public enum UserItemStatus {
    /** 보유 중, 미착용/미사용 */
    ACTIVE,
    /** 착용 중 (아바타/배지 한정) */
    EQUIPPED,
    /** 사용 완료 (힌트/응모권 등) */
    USED,
    /** 유효기간 경과로 만료 */
    EXPIRED
}
