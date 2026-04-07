package com.monglepick.monglepickbackend.domain.reward.dto;

import java.util.List;

/**
 * 포인트 상점 DTO — AI 이용권 아이템 목록 조회·구매 요청/응답.
 *
 * <p>포인트 상점에서는 포인트를 소비하여 AI 추천 이용권을 구매할 수 있다.
 * 현재 지원하는 상품은 3종이며, 모두 {@code purchased_ai_tokens}에 적립된다.</p>
 *
 * <h3>지원 상품 (packType → 차감P / 지급 토큰)</h3>
 * <ul>
 *   <li>{@code AI_TOKEN_5}    — 200P 차감, AI 이용권 5회 지급</li>
 *   <li>{@code AI_TOKEN_20}   — 700P 차감, AI 이용권 20회 지급 (번들 할인)</li>
 *   <li>{@code AI_DAILY_EXTEND} — 100P 차감, AI 이용권 5회 지급 (일일 한도 우회 목적)</li>
 * </ul>
 *
 * <h3>관련 엔티티</h3>
 * <ul>
 *   <li>{@link com.monglepick.monglepickbackend.domain.reward.entity.UserPoint#purchasedAiTokens}
 *       — 구매 토큰 잔여 횟수 저장 필드</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.service.PointShopService
 * @see com.monglepick.monglepickbackend.domain.reward.controller.PointShopController
 */
public class PointShopDto {

    /**
     * 포인트 상점 단일 아이템 정보.
     *
     * <p>상점 목록 조회({@code GET /api/v1/point/shop/items}) 응답의 개별 항목으로 사용된다.</p>
     *
     * @param itemId      아이템 식별자 (예: "AI_TOKEN_5", "AI_TOKEN_20", "AI_DAILY_EXTEND")
     * @param name        아이템 표시 이름 (예: "AI 이용권 5회")
     * @param cost        구매에 필요한 포인트 (예: 200)
     * @param amount      구매 시 지급되는 AI 이용권 횟수 (예: 5)
     * @param description 아이템 설명 (예: "AI 추천 5회를 추가로 사용할 수 있습니다.")
     */
    public record ShopItem(
            String itemId,
            String name,
            int cost,
            int amount,
            String description
    ) {}

    /**
     * 상점 아이템 목록 + 현재 잔액 응답.
     *
     * <p>{@code GET /api/v1/point/shop/items} 응답 DTO.
     * 클라이언트가 잔액을 보면서 구매 가능 여부를 즉시 판단할 수 있도록 현재 잔액을 포함한다.</p>
     *
     * @param currentBalance     현재 보유 포인트 잔액
     * @param currentAiTokens    현재 보유 AI 이용권 잔여 횟수 (purchased_ai_tokens)
     * @param items              상점에 등록된 아이템 목록
     */
    public record ShopItemsResponse(
            int currentBalance,
            int currentAiTokens,
            List<ShopItem> items
    ) {}

    /**
     * AI 이용권 구매 결과 응답.
     *
     * <p>{@code POST /api/v1/point/shop/ai-tokens} 및
     * {@code POST /api/v1/point/shop/ai-extend} 응답 DTO.
     * 차감된 포인트, 추가된 토큰, 구매 후 잔여 정보를 모두 포함한다.</p>
     *
     * @param deductedPoints        이번 구매로 차감된 포인트
     * @param addedTokens           이번 구매로 추가된 AI 이용권 횟수
     * @param remainingBalance      구매 후 남은 포인트 잔액
     * @param totalPurchasedTokens  구매 후 전체 AI 이용권 잔여 횟수 (purchased_ai_tokens)
     */
    public record PurchaseResponse(
            int deductedPoints,
            int addedTokens,
            int remainingBalance,
            int totalPurchasedTokens
    ) {}
}
