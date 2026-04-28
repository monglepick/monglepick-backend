package com.monglepick.monglepickbackend.domain.reward.constants;

/**
 * 포인트 아이템 카테고리 상수 — point_items.item_category 컬럼의 허용 값.
 *
 * <p>설계서 v3.2 §16 "포인트 소비처" 기준으로 카테고리를 정규화한다 (2026-04-28 9종으로 확장).
 * 과거 버전에서 혼재하던 표기({@code "COUPON"} / {@code "coupon"}, {@code "ai_feature"},
 * {@code "profile"}, {@code "roadmap"}, {@code "APPLY"})를 모두 소문자로 통일하여
 * {@link com.monglepick.monglepickbackend.domain.reward.service.PointItemService#exchangeItem}
 * 의 카테고리별 지급 분기 로직이 예측 가능하게 동작하도록 한다.</p>
 *
 * <h3>정규화된 카테고리 (소문자 단일, 2026-04-28 v3.5 — 9종)</h3>
 * <ul>
 *   <li>{@link #COUPON} — 소비성 쿠폰 (AI 이용권, 프리미엄 배지 등)</li>
 *   <li>{@link #AVATAR} — 프로필 아바타 이미지 (영구 보유, 착용형)</li>
 *   <li>{@link #BADGE}  — 프로필/댓글 배지 (기간 제한, 착용형)</li>
 *   <li>{@link #FRAME}  — 프로필 사진 테두리/프레임 (착용형, 영구 또는 기간 제한)</li>
 *   <li>{@link #BACKGROUND} — 프로필 카드 배경 (착용형, 영구 또는 기간 제한)</li>
 *   <li>{@link #TITLE}  — 닉네임 옆/위 칭호 텍스트 라벨 (착용형, 등급 자동 지급 포함)</li>
 *   <li>{@link #EFFECT} — 프로필 위 애니메이션 이펙트 (CSS 합성, 착용형)</li>
 *   <li>{@link #APPLY}  — 응모권 (영화 티켓 추첨 등, 사용 시 소진)</li>
 *   <li>{@link #HINT}   — 퀴즈·도장깨기 힌트 (수량 보유, 사용 시 차감)</li>
 * </ul>
 *
 * <h3>"꾸미기" 6슬롯 그룹 (착용 가능)</h3>
 * <p>{@link #AVATAR} · {@link #BADGE} · {@link #FRAME} · {@link #BACKGROUND} ·
 * {@link #TITLE} · {@link #EFFECT} 의 6 카테고리는 모두 1슬롯 1개만 착용 가능
 * ({@code UserItemStatus.EQUIPPED}). 같은 카테고리 내 신규 착용 시 기존 착용물 자동 해제.</p>
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

    /**
     * 프레임 — 프로필 사진 테두리 (2026-04-28 신규).
     *
     * <p>아바타 위에 z-index 2로 합성되는 SVG/PNG 테두리. 영화 필름·골든 시상식·시즌 한정 등.
     * 영구 보유 또는 기간 제한 모두 지원. 착용 가능 카테고리.</p>
     */
    public static final String FRAME = "frame";

    /**
     * 배경 — 프로필 카드 배경 이미지 (2026-04-28 신규).
     *
     * <p>프로필 카드 z-index 0으로 합성되는 배경 이미지/패턴. 극장 좌석·레드카펫·우주 SF 등.
     * 영구 보유 또는 기간 제한 모두 지원. 착용 가능 카테고리.</p>
     */
    public static final String BACKGROUND = "background";

    /**
     * 칭호 — 닉네임 옆/위 텍스트 라벨 (2026-04-28 신규).
     *
     * <p>"🍿 영화광", "리뷰 마스터", "100편 클럽" 등 텍스트 칭호. 등급 달성 시 자동 지급되는
     * 6종(NORMAL~DIAMOND) + 운영자가 등록하는 일반 칭호. PointItem.itemDescription 에 표시 텍스트
     * 포맷을 보관하여 클라이언트가 렌더링한다. 착용 가능 카테고리.</p>
     */
    public static final String TITLE = "title";

    /**
     * 이펙트 — 프로필 카드 위 애니메이션 이펙트 (2026-04-28 신규).
     *
     * <p>팝콘 떨어짐·별빛 반짝임·필름 스트립 회전 등 CSS/Lottie 애니메이션. 클라이언트는
     * imageUrl 또는 itemType+itemName 매핑으로 effect key 를 결정하여 합성한다. 착용 가능 카테고리.</p>
     */
    public static final String EFFECT = "effect";

    /** 응모권 — 영화 티켓 추첨 참여권 등. 응모 시점에 status=USED. */
    public static final String APPLY = "apply";

    /** 힌트 — 퀴즈·도장깨기 힌트. 수량으로 보유하며 사용 시 1회 차감. */
    public static final String HINT = "hint";

    /**
     * 착용 가능한 카테고리 6종 — "꾸미기" 슬롯 그룹.
     *
     * <p>{@link com.monglepick.monglepickbackend.domain.reward.service.UserItemService} 가 착용 요청을
     * 검증할 때 사용한다. 이 집합 외 카테고리는 USER_ITEM_NOT_EQUIPPABLE 로 차단된다.</p>
     */
    public static final java.util.Set<String> EQUIPPABLE = java.util.Set.of(
            AVATAR, BADGE, FRAME, BACKGROUND, TITLE, EFFECT
    );

    /** 유틸리티 클래스 — 외부 인스턴스화 차단 */
    private PointItemCategory() {
        throw new UnsupportedOperationException("PointItemCategory is a constants holder.");
    }
}
