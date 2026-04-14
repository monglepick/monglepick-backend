package com.monglepick.monglepickbackend.domain.reward.constants;

/**
 * 포인트 아이템 카테고리 상수 — point_items.item_category 컬럼의 허용 값.
 *
 * <p>설계서 v3.2 §16 "포인트 소비처" 기준으로 카테고리를 5종으로 정규화한다.
 * 과거 버전에서 혼재하던 표기({@code "COUPON"} / {@code "coupon"}, {@code "ai_feature"},
 * {@code "profile"}, {@code "roadmap"}, {@code "APPLY"})를 모두 소문자 5종으로 통일하여
 * {@link com.monglepick.monglepickbackend.domain.reward.service.PointItemService#exchangeItem}
 * 의 카테고리별 지급 분기 로직이 예측 가능하게 동작하도록 한다.</p>
 *
 * <h3>정규화된 카테고리 (소문자 단일)</h3>
 * <ul>
 *   <li>{@link #COUPON} — 소비성 쿠폰 (AI 이용권, 프리미엄 배지 등)</li>
 *   <li>{@link #AVATAR} — 프로필 아바타 이미지 (영구 보유, 착용형)</li>
 *   <li>{@link #BADGE}  — 프로필/댓글 배지 (기간 제한, 착용형)</li>
 *   <li>{@link #APPLY}  — 응모권 (영화 티켓 추첨 등, 사용 시 소진)</li>
 *   <li>{@link #HINT}   — 퀴즈·도장깨기 힌트 (수량 보유, 사용 시 차감)</li>
 * </ul>
 *
 * <p>이 클래스는 인스턴스화되지 않으며, 문자열 상수 모음이다.</p>
 */
public final class PointItemCategory {

    /** 쿠폰 — AI 이용권 4종 통합 관리용 카테고리. 지급 방식은 {@link PointItemType}으로 분기. */
    public static final String COUPON = "coupon";

    /** 아바타 — 프로필 캐릭터 이미지. 영구 보유, 착용/해제 지원. */
    public static final String AVATAR = "avatar";

    /** 배지 — 프로필/댓글 표시용. 기간 제한(예: 30일) 있는 착용형. */
    public static final String BADGE = "badge";

    /** 응모권 — 영화 티켓 추첨 참여권 등. 응모 시점에 status=USED. */
    public static final String APPLY = "apply";

    /** 힌트 — 퀴즈·도장깨기 힌트. 수량으로 보유하며 사용 시 1회 차감. */
    public static final String HINT = "hint";

    /** 유틸리티 클래스 — 외부 인스턴스화 차단 */
    private PointItemCategory() {
        throw new UnsupportedOperationException("PointItemCategory is a constants holder.");
    }
}
