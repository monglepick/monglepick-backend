package com.monglepick.monglepickbackend.domain.reward.dto;

import java.time.OffsetDateTime;

/**
 * AI 쿼터 현황 응답 DTO — 고객센터 AI 봇 진단용 (v4 신규, 2026-04-28).
 *
 * <p>고객센터 지원 봇(support_assistant v4)이 "AI 추천 더 못 써요" 류의 발화를
 * 진단할 때 호출한다. 사용자 본인의 쿼터 현황만 반환하며, BOLA 방지를 위해
 * JWT에서 강제로 userId를 추출한다 (쿼리 파라미터 userId 미허용).</p>
 *
 * <h3>3-소스 필드 매핑 (CLAUDE.md AI 쿼터 3-소스 정책)</h3>
 * <ul>
 *   <li>소스 1 — GRADE_FREE: {@code dailyAiUsed} vs {@code dailyAiLimit}</li>
 *   <li>소스 2 — SUB_BONUS: {@code remainingAiBonus} (UserSubscription)</li>
 *   <li>소스 3 — PURCHASED: {@code purchasedAiTokens}, {@code monthlyCouponUsed} vs {@code monthlyCouponLimit}</li>
 * </ul>
 *
 * @param dailyAiUsed        오늘 소비한 GRADE_FREE 횟수 (user_ai_quota.daily_ai_used, lazy reset 적용)
 * @param dailyAiLimit       등급별 일일 무료 한도 (grades.daily_ai_limit, DIAMOND=-1 무제한)
 * @param remainingAiBonus   구독 SUB_BONUS 잔여 횟수 (-1=구독 없음, 0=소진, 양수=잔여)
 * @param purchasedAiTokens  이용권 PURCHASED 잔여 횟수 (user_ai_quota.purchased_ai_tokens)
 * @param monthlyCouponUsed  이번 달 이용권 사용 횟수 (user_ai_quota.monthly_coupon_used, lazy reset 적용)
 * @param monthlyCouponLimit 등급별 월간 쿠폰 한도 (grades.monthly_ai_limit, DIAMOND=-1 무제한)
 * @param resetAt            다음 일일 리셋 시각 (KST 다음 자정, ISO 8601 오프셋 포함)
 */
public record AiQuotaStatusResponse(
        int dailyAiUsed,
        int dailyAiLimit,
        int remainingAiBonus,
        int purchasedAiTokens,
        int monthlyCouponUsed,
        int monthlyCouponLimit,
        OffsetDateTime resetAt
) {
}
