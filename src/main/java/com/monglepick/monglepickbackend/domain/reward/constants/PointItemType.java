package com.monglepick.monglepickbackend.domain.reward.constants;

import java.util.Arrays;

/**
 * 포인트 아이템 타입 — point_items.item_type 컬럼 값.
 *
 * <p>{@link PointItemCategory}가 대분류(쿠폰/아바타/배지/응모권/힌트)를 나타낸다면,
 * {@code PointItemType}은 "이 아이템이 교환되었을 때 실제로 무엇을 지급해야 하는지"를 결정하는
 * 분기 키다. {@link com.monglepick.monglepickbackend.domain.reward.service.PointItemService#exchangeItem}
 * 는 이 타입을 읽어 {@link com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota}에 토큰을 적립하거나,
 * {@code user_items} 테이블에 보유 레코드를 INSERT한다.</p>
 *
 * <h3>지급 처리 분류</h3>
 * <table border="1">
 *   <tr><th>Type</th><th>Category</th><th>지급 방식</th><th>유효기간</th></tr>
 *   <tr><td>AI_TOKEN_1/5/20/50</td><td>coupon</td><td>UserAiQuota.purchased_ai_tokens 증가</td><td>30~60일</td></tr>
 *   <tr><td>AVATAR_MONGLE</td><td>avatar</td><td>UserItem INSERT (영구)</td><td>NULL</td></tr>
 *   <tr><td>BADGE_PREMIUM</td><td>badge</td><td>UserItem INSERT + expires_at=+30일</td><td>30일</td></tr>
 *   <tr><td>APPLY_MOVIE_TICKET</td><td>apply</td><td>UserItem INSERT (추첨 대기)</td><td>월말까지</td></tr>
 *   <tr><td>QUIZ_HINT</td><td>hint</td><td>UserItem INSERT (수량 보유)</td><td>NULL</td></tr>
 * </table>
 *
 * <h3>지급 전략</h3>
 * <ul>
 *   <li>{@link Dispense#AI_TOKEN} — UserAiQuota에 토큰 적립 ({@code purchased_ai_tokens} 컬럼 증가)</li>
 *   <li>{@link Dispense#INVENTORY} — {@code user_items} 테이블에 보유 레코드 INSERT</li>
 * </ul>
 *
 * <p>알 수 없는 타입(레거시 데이터)은 {@link #UNKNOWN}으로 처리하여
 * exchangeItem 호출 시 예외를 발생시킨다 — 안전한 fallback.</p>
 */
public enum PointItemType {

    /* ── AI 이용권 (UserAiQuota.purchased_ai_tokens 증가) ─────── */

    /** AI 이용권 1회 (10P) */
    AI_TOKEN_1(Dispense.AI_TOKEN, 1, 30),

    /** AI 이용권 5회 (50P) */
    AI_TOKEN_5(Dispense.AI_TOKEN, 5, 30),

    /** AI 이용권 20회 (200P) */
    AI_TOKEN_20(Dispense.AI_TOKEN, 20, 30),

    /** AI 이용권 50회 (500P) */
    AI_TOKEN_50(Dispense.AI_TOKEN, 50, 60),

    /* ── 인벤토리 아이템 (user_items INSERT) ─────────────────── */

    /** 몽글이 프로필 아바타 (150P, 영구) */
    AVATAR_MONGLE(Dispense.INVENTORY, 1, null),

    /** 프리미엄 배지 1개월 (100P) */
    BADGE_PREMIUM(Dispense.INVENTORY, 1, 30),

    /** 영화 티켓 응모권 (150P, 월말 추첨) */
    APPLY_MOVIE_TICKET(Dispense.INVENTORY, 1, null),

    /** 퀴즈·도장깨기 힌트 (50P, 수량 보유) */
    QUIZ_HINT(Dispense.INVENTORY, 1, null),

    /** 알 수 없는 타입 (레거시 시드 안전 fallback — 교환 시 예외 발생) */
    UNKNOWN(Dispense.UNSUPPORTED, 0, null);

    /** 지급 방식 구분 */
    public enum Dispense {
        /** UserAiQuota.purchased_ai_tokens 적립 */
        AI_TOKEN,
        /** user_items 테이블 INSERT */
        INVENTORY,
        /** 지원하지 않음 — 교환 시도 시 BusinessException */
        UNSUPPORTED
    }

    /** 지급 방식 */
    private final Dispense dispense;

    /**
     * 지급 수량 — 의미는 타입별로 다르다.
     * <ul>
     *   <li>AI_TOKEN_* : 지급할 AI 이용권 횟수 (1/5/20/50)</li>
     *   <li>INVENTORY  : 발급할 user_items 레코드 수 (기본 1)</li>
     * </ul>
     */
    private final int amount;

    /**
     * 유효기간(일) — NULL이면 무기한.
     * UserItem.expires_at 또는 쿠폰 유효기간 계산에 사용된다.
     */
    private final Integer durationDays;

    PointItemType(Dispense dispense, int amount, Integer durationDays) {
        this.dispense = dispense;
        this.amount = amount;
        this.durationDays = durationDays;
    }

    public Dispense getDispense() {
        return dispense;
    }

    public int getAmount() {
        return amount;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    /**
     * 문자열을 {@link PointItemType}으로 파싱한다. null/공백/미지원 값이면 {@link #UNKNOWN} 반환.
     *
     * <p>DB에 저장된 {@code item_type} 컬럼을 읽을 때 안전하게 변환하기 위한 헬퍼.
     * 예외를 던지지 않아 레거시 데이터로 인한 서버 장애를 방지한다.</p>
     *
     * @param raw DB에서 읽은 원본 문자열
     * @return 매칭되는 타입 (없으면 {@link #UNKNOWN})
     */
    public static PointItemType fromCodeOrUnknown(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
