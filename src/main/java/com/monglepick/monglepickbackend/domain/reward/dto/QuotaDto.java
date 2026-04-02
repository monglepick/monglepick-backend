package com.monglepick.monglepickbackend.domain.reward.dto;

/**
 * 등급별 쿼터(이용 한도) 관련 DTO 모음.
 *
 * <h3>v3.0 AI 3-소스 모델</h3>
 * <p>기존의 freeDaily/effectiveCost/monthlyUsed/monthlyLimit 개념을 제거하고
 * 3계층 소스 기반 모델로 전면 교체한다.</p>
 *
 * <h3>v3.0 AI 사용 처리 순서</h3>
 * <ol>
 *   <li><b>GRADE_FREE</b>: grade.daily_ai_limit 확인 → daily_ai_used &lt; limit → 무료 허용</li>
 *   <li><b>SUB_BONUS</b>: 활성 구독 remaining_ai_bonus &gt; 0 → 구독 보너스 풀 차감</li>
 *   <li><b>PURCHASED</b>: user_points.purchased_ai_tokens &gt; 0 → 구매 토큰 차감</li>
 *   <li><b>BLOCKED</b>: 모두 소진 → 차단 + 안내 메시지</li>
 * </ol>
 *
 * <h3>v3.0 변경 사항</h3>
 * <ul>
 *   <li>제거: {@code freeRemaining} — 무료 일일 횟수 개념 폐지 (daily 한도 내 전부 무료)</li>
 *   <li>제거: {@code effectiveCost} — 10P/회 과금 폐지</li>
 *   <li>제거: {@code monthlyUsed}, {@code monthlyLimit} — 월간 한도 폐지</li>
 *   <li>추가: {@code source} — 허용 소스 식별자 (GRADE_FREE/SUB_BONUS/PURCHASED/BLOCKED)</li>
 *   <li>추가: {@code subBonusRemaining} — 구독 보너스 잔여 (-1이면 구독 없음)</li>
 *   <li>추가: {@code purchasedRemaining} — 구매 토큰 잔여</li>
 * </ul>
 *
 * <h3>GradeQuota 변경</h3>
 * <ul>
 *   <li>제거: {@code monthlyLimit} — 월간 한도 필드 폐지</li>
 *   <li>제거: {@code freeDaily} — 무료 일일 횟수 개념 폐지</li>
 *   <li>유지: {@code dailyLimit}, {@code maxInputLength}</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.service.QuotaService
 * @see PointDto.CheckResponse
 */
public final class QuotaDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private QuotaDto() {
    }

    // ──────────────────────────────────────────────
    // 쿼터 확인 결과 (v3.0)
    // ──────────────────────────────────────────────

    /**
     * v3.0 등급별 쿼터 확인 결과.
     *
     * <p>{@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService#checkQuota}의
     * 반환값으로, 3-소스 모델에 따라 AI 사용 허용 여부와 허용 소스를 반환한다.</p>
     *
     * <h4>source 값 정의</h4>
     * <ul>
     *   <li>{@code "GRADE_FREE"} — grade.daily_ai_limit 한도 내 무료 사용 허용</li>
     *   <li>{@code "SUB_BONUS"} — 활성 구독의 remaining_ai_bonus에서 차감 허용</li>
     *   <li>{@code "PURCHASED"} — user_points.purchased_ai_tokens에서 차감 허용</li>
     *   <li>{@code "BLOCKED"} — 모든 소스 소진, AI 사용 차단</li>
     * </ul>
     *
     * <h4>subBonusRemaining 해석</h4>
     * <ul>
     *   <li>{@code -1} — 활성 구독 없음</li>
     *   <li>{@code 0} — 구독 있으나 이번 달 보너스 소진</li>
     *   <li>{@code > 0} — 구독 보너스 잔여 횟수</li>
     * </ul>
     *
     * <h4>BLOCKED 시 클라이언트 안내 전략</h4>
     * <ul>
     *   <li>구독 없음(subBonusRemaining=-1) → 구독 가입 유도</li>
     *   <li>구독 있으나 소진(subBonusRemaining=0) → 구매 토큰 안내 또는 등급 업 안내</li>
     *   <li>모두 소진 → 포인트 충전 + 이용권 구매 안내</li>
     * </ul>
     *
     * @param allowed            AI 사용 허용 여부
     * @param source             허용 소스 식별자 (GRADE_FREE/SUB_BONUS/PURCHASED/BLOCKED)
     * @param dailyUsed          오늘 AI 사용 횟수 (grade 무료 한도 기준)
     * @param dailyLimit         grade 일일 무료 한도 (-1이면 PLATINUM 무제한)
     * @param subBonusRemaining  구독 보너스 잔여 횟수 (-1이면 구독 없음)
     * @param purchasedRemaining 구매 토큰 잔여 횟수
     * @param maxInputLength     등급별 최대 입력 글자 수
     * @param message            차단 시 사용자 안내 메시지 (허용이면 빈 문자열)
     */
    public record QuotaCheckResult(
            boolean allowed,
            String source,
            int dailyUsed,
            int dailyLimit,
            int subBonusRemaining,
            int purchasedRemaining,
            int maxInputLength,
            String message
    ) {
    }

    // ──────────────────────────────────────────────
    // 등급별 쿼터 설정값 (v3.0)
    // ──────────────────────────────────────────────

    /**
     * v3.0 등급별 쿼터 설정값.
     *
     * <p>v3.0에서 월간 한도(monthlyLimit)와 무료 일일 횟수(freeDaily) 개념이 제거되었다.
     * daily_ai_limit 내 사용은 전부 무료이며, 초과 시 구독 보너스/구매 토큰으로 처리한다.</p>
     *
     * <h4>QuotaProperties와의 매핑</h4>
     * <p>{@code application.yml}의 {@code app.quota.<등급>.*} 값에서
     * {@code daily-limit}과 {@code max-input-length}만 사용한다.
     * {@code monthly-limit}과 {@code free-daily}는 하위 호환성을 위해 yml에 남아있을 수 있으나
     * 이 DTO에서는 무시된다.</p>
     *
     * <h4>v3.0 등급별 기본값 (GradeInitializer 기준)</h4>
     * <table border="1">
     *   <tr><th>등급</th><th>dailyLimit</th><th>maxInputLength</th></tr>
     *   <tr><td>NORMAL</td><td>3</td><td>200</td></tr>
     *   <tr><td>BRONZE</td><td>7</td><td>300</td></tr>
     *   <tr><td>SILVER</td><td>15</td><td>500</td></tr>
     *   <tr><td>GOLD</td><td>30</td><td>1,000</td></tr>
     *   <tr><td>PLATINUM</td><td>-1(무제한)</td><td>2,000</td></tr>
     * </table>
     *
     * @param dailyLimit     일일 AI 무료 사용 한도 (-1이면 PLATINUM 무제한)
     * @param maxInputLength 등급별 최대 입력 글자 수
     */
    public record GradeQuota(
            int dailyLimit,
            int maxInputLength
    ) {
    }
}
