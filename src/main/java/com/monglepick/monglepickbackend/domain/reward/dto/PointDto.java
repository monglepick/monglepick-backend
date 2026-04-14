package com.monglepick.monglepickbackend.domain.reward.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 포인트 시스템 DTO 모음.
 *
 * <p>모든 포인트 관련 요청/응답 DTO를 inner record로 정의한다.
 * record를 사용하여 불변 객체로 관리하며, Jackson 직렬화/역직렬화를 지원한다.</p>
 *
 * <h3>Agent 연동 주의사항</h3>
 * <p>AI Agent(monglepick-agent)의 {@code point_client.py}가 이 DTO의 JSON 응답을
 * 직접 파싱한다. 따라서 필드명 변경 시 Agent 쪽도 반드시 동기화해야 한다.</p>
 * <ul>
 *   <li>CheckResponse: {@code allowed, balance, cost, message} (camelCase)</li>
 *   <li>DeductResponse: {@code success, balanceAfter, transactionId} (camelCase)</li>
 * </ul>
 */
public final class PointDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private PointDto() {
    }

    // ──────────────────────────────────────────────
    // 잔액 확인 (Agent → Backend)
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 확인 요청.
     *
     * <p>AI Agent가 추천 실행 전에 사용자의 잔액이 충분한지 확인할 때 사용한다.</p>
     *
     * @param userId 사용자 ID (필수)
     * @param cost   필요 포인트 (0 이상)
     */
    public record CheckRequest(
            @NotBlank(message = "사용자 ID는 필수입니다")
            String userId,

            @Min(value = 0, message = "비용은 0 이상이어야 합니다")
            int cost
    ) {
    }

    /**
     * 포인트 잔액 확인 응답 (v3.0 QuotaCheckResult 연동).
     *
     * <p>Agent의 {@code point_client.py}가 이 JSON을 직접 파싱한다.
     * <b>기존 5개 필드(allowed, balance, cost, message, maxInputLength)는 절대 변경 금지.</b>
     * Agent는 기존 필드만 파싱하므로 추가 필드는 Agent가 무시한다 (하위 호환성 유지).</p>
     *
     * <h4>v3.0 변경사항 (QuotaCheckResult 구조 변경 반영)</h4>
     * <p>월간 한도 폐지에 따라 monthlyUsed/monthlyLimit/freeRemaining/effectiveCost 필드가 제거되고,
     * 구독 보너스 및 구매 토큰 기반 소스 정보로 대체된다.</p>
     *
     * <h4>v3.0 신규 필드 (쿼터 소스 정보)</h4>
     * <p>등급별 일일 사용 현황 및 허용 소스 정보를 포함한다.
     * 클라이언트에서 "오늘 N/M회 사용" 및 구독/토큰 잔여 안내 UI에 활용한다.</p>
     *
     * @param allowed            AI 사용 가능 여부 (쿼터 통과 시 true)
     * @param balance            현재 보유 포인트
     * @param cost               요청된 비용 (v3.0에서 AI 무과금이므로 항상 0)
     * @param message            결과 메시지 (한도 초과 시 안내 메시지 포함)
     * @param maxInputLength     등급별 최대 입력 글자 수 (NORMAL:200, BRONZE:300, SILVER:500, GOLD:1000, PLATINUM:2000)
     * @param dailyUsed          오늘 AI 추천 사용 횟수 (등급 무료 한도 기준)
     * @param dailyLimit         일일 등급 무료 한도 (-1이면 PLATINUM 무제한)
     * @param source             허용 소스 식별자 — "GRADE_FREE" | "SUB_BONUS" | "PURCHASED" | "BLOCKED"
     * @param subBonusRemaining  구독 보너스 잔여 횟수 (-1이면 구독 없음)
     * @param purchasedRemaining 구매 토큰 잔여 횟수
     */
    public record CheckResponse(
            boolean allowed,
            int balance,
            int cost,
            String message,
            int maxInputLength,
            // ── v3.0 쿼터 필드 (하위 호환: Agent는 이 필드를 무시) ──
            int dailyUsed,
            int dailyLimit,
            String source,           // "GRADE_FREE" | "SUB_BONUS" | "PURCHASED" | "BLOCKED"
            int subBonusRemaining,   // 구독 보너스 잔여 (-1이면 구독 없음)
            int purchasedRemaining   // 구매 토큰 잔여
    ) {
    }

    // ──────────────────────────────────────────────
    // 포인트 차감 (Agent → Backend)
    // ──────────────────────────────────────────────

    /**
     * 포인트 차감 요청.
     *
     * <p>AI Agent가 추천 완료 후 포인트를 차감할 때 사용한다.
     * sessionId로 차감 원인(채팅 세션)을 추적할 수 있다.</p>
     *
     * @param userId      사용자 ID (필수)
     * @param amount      차감 포인트 (1 이상)
     * @param sessionId   채팅 세션 ID (선택, 추적용)
     * @param description 차감 사유 (선택, 예: "AI 추천 사용")
     */
    public record DeductRequest(
            @NotBlank(message = "사용자 ID는 필수입니다")
            String userId,

            @Min(value = 1, message = "차감 금액은 1 이상이어야 합니다")
            int amount,

            String sessionId,

            String description
    ) {
    }

    /**
     * 포인트 차감 응답.
     *
     * <p>Agent의 {@code point_client.py}가 이 JSON을 직접 파싱한다.
     * 필드명(success, balanceAfter, transactionId)은 절대 변경 금지.</p>
     *
     * @param success       차감 성공 여부
     * @param balanceAfter  차감 후 잔액
     * @param transactionId 포인트 이력 ID (points_history PK)
     */
    public record DeductResponse(
            boolean success,
            int balanceAfter,
            Long transactionId
    ) {
    }

    // ──────────────────────────────────────────────
    // 잔액 조회 (클라이언트/내부 공용)
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 조회 응답.
     *
     * <p>클라이언트의 "내 포인트" 화면이나 내부 API에서 사용한다.</p>
     *
     * @param balance    현재 보유 포인트
     * @param grade      사용자 등급 (BRONZE, SILVER, GOLD, PLATINUM)
     * @param totalEarned 누적 획득 포인트
     */
    public record BalanceResponse(
            int balance,
            String grade,
            int totalEarned
    ) {
    }

    // ──────────────────────────────────────────────
    // 변동 이력 조회 (클라이언트 전용)
    // ──────────────────────────────────────────────

    /**
     * 포인트 변동 이력 응답 (단건).
     *
     * <p>클라이언트의 "포인트 내역" 목록에서 한 건의 이력을 표현한다.</p>
     *
     * @param id          이력 ID (points_history PK)
     * @param pointChange 변동량 (양수: 획득, 음수: 차감)
     * @param pointAfter  변동 후 잔액
     * @param pointType   변동 유형 (earn, spend, expire, bonus)
     * @param description 변동 사유
     * @param createdAt   변동 시각
     */
    public record HistoryResponse(
            Long id,
            int pointChange,
            int pointAfter,
            String pointType,
            String description,
            LocalDateTime createdAt
    ) {
    }

    // ──────────────────────────────────────────────
    // 포인트 획득 (내부 서비스 간 호출)
    // ──────────────────────────────────────────────

    /**
     * 포인트 획득 요청.
     *
     * <p>출석 체크, 이벤트 보상, 퀴즈 보상 등
     * 포인트를 지급할 때 내부적으로 사용한다.</p>
     *
     * @param userId      사용자 ID (필수)
     * @param amount      획득 포인트 (1 이상)
     * @param pointType   변동 유형 (필수, 예: "earn", "bonus")
     * @param description 획득 사유 (선택, 예: "출석 체크 보상")
     * @param referenceId 참조 ID (선택, 예: 이벤트 ID, 퀴즈 ID)
     */
    public record EarnRequest(
            @NotBlank(message = "사용자 ID는 필수입니다")
            String userId,

            @Min(value = 1, message = "획득 금액은 1 이상이어야 합니다")
            int amount,

            @NotBlank(message = "포인트 유형은 필수입니다")
            String pointType,

            String description,

            String referenceId
    ) {
    }

    /**
     * 포인트 획득 응답.
     *
     * @param balanceAfter 획득 후 잔액
     * @param grade        현재 등급 (변경될 수 있음)
     */
    /**
     * 포인트 획득 응답.
     *
     * @param balanceAfter  획득 후 잔액
     * @param grade         현재(변경 후) 등급 코드
     * @param previousGrade 변경 전 등급 코드 (등급 변경 없으면 grade와 동일)
     */
    public record EarnResponse(
            int balanceAfter,
            String grade,
            String previousGrade
    ) {
    }

    // ──────────────────────────────────────────────
    // 출석 체크
    // ──────────────────────────────────────────────

    /**
     * 출석 체크 응답.
     *
     * <p>출석 체크 성공 시 출석일, 연속 출석일수, 획득한 포인트, 현재 잔액을 반환한다.</p>
     *
     * @param checkDate      출석 체크 날짜
     * @param streakCount    연속 출석일 수 (1 이상)
     * @param earnedPoints   이번 출석으로 획득한 포인트 (기본 10P + 보너스)
     * @param currentBalance 출석 보상 지급 후 현재 보유 포인트
     */
    public record AttendanceResponse(
            LocalDate checkDate,
            int streakCount,
            int earnedPoints,
            int currentBalance
    ) {
    }

    /**
     * 출석 현황 응답.
     *
     * <p>클라이언트의 출석 체크 화면에서 캘린더 표시 및 현황 요약에 사용된다.</p>
     *
     * @param currentStreak 현재 연속 출석일 수 (연속 끊기면 0)
     * @param totalDays     누적 총 출석 일수
     * @param checkedToday  오늘 출석 완료 여부 (true: 출석 완료, false: 미출석)
     * @param monthlyDates  이번 달 출석한 날짜 목록 (캘린더 표시용)
     */
    public record AttendanceStatusResponse(
            int currentStreak,
            int totalDays,
            boolean checkedToday,
            List<LocalDate> monthlyDates
    ) {
    }

    // ──────────────────────────────────────────────
    // 포인트 아이템 (상점)
    // ──────────────────────────────────────────────

    /**
     * 포인트 아이템 응답 (단건).
     *
     * <p>클라이언트의 "포인트 상점" 목록에서 한 건의 아이템을 표현한다.</p>
     *
     * @param itemId      아이템 ID (point_items PK)
     * @param name        아이템명
     * @param description 아이템 설명 (nullable)
     * @param price       필요 포인트
     * @param category    아이템 카테고리 (general, coupon, avatar, ai 등)
     */
    /** Serializable 필수: @Cacheable Redis 직렬화에 필요 (V5 테스트에서 발견) */
    public record PointItemResponse(
            Long itemId,
            String name,
            String description,
            int price,
            String category
    ) implements Serializable {
    }

    /**
     * 아이템 교환 응답 (v2, 2026-04-14 — C 방향 카테고리별 지급 로직 대응).
     *
     * <p>포인트 아이템 교환(구매) 성공 시 결과를 반환한다.
     * v2에서 카테고리별 지급 결과 필드 5종(userItemId, itemType, category, addedAiTokens,
     * totalAiTokens)을 추가하여 클라이언트가 "무엇이 지급되었는지"를 즉시 표시할 수 있게 한다.</p>
     *
     * <h3>카테고리별 응답 예시</h3>
     * <ul>
     *   <li>AI 이용권(coupon + AI_TOKEN_*) — userItemId=null, addedAiTokens=5, totalAiTokens=12</li>
     *   <li>아바타/배지/응모권/힌트(inventory) — userItemId=123, addedAiTokens=null, totalAiTokens=null</li>
     * </ul>
     *
     * @param success        교환 성공 여부
     * @param balanceAfter   교환 후 잔여 포인트
     * @param itemName       교환한 아이템명
     * @param userItemId     발급된 user_items 레코드 ID (AI 이용권이면 null)
     * @param itemType       지급된 아이템 타입 코드 (PointItemType.name(), null 허용)
     * @param category       아이템 카테고리 ("coupon"/"avatar"/"badge"/"apply"/"hint")
     * @param addedAiTokens  추가된 AI 이용권 수량 (AI 이용권 교환 시, 아니면 null)
     * @param totalAiTokens  교환 후 총 AI 이용권 잔여 (AI 이용권 교환 시, 아니면 null)
     */
    public record ExchangeResponse(
            boolean success,
            int balanceAfter,
            String itemName,
            Long userItemId,
            String itemType,
            String category,
            Integer addedAiTokens,
            Integer totalAiTokens
    ) {
        /**
         * AI 이용권 교환 결과 빌더.
         *
         * @param balanceAfter  차감 후 잔액
         * @param itemName      상품명
         * @param itemType      지급 타입 (AI_TOKEN_5 등)
         * @param category      카테고리 (항상 "coupon")
         * @param addedAiTokens 추가된 수량
         * @param totalAiTokens 총 잔여 수량
         */
        public static ExchangeResponse aiToken(int balanceAfter, String itemName, String itemType,
                                                String category, int addedAiTokens, int totalAiTokens) {
            return new ExchangeResponse(true, balanceAfter, itemName,
                    null, itemType, category, addedAiTokens, totalAiTokens);
        }

        /**
         * 인벤토리 아이템 교환 결과 빌더 (아바타/배지/응모권/힌트).
         *
         * @param balanceAfter 차감 후 잔액
         * @param itemName     상품명
         * @param userItemId   발급된 user_items PK
         * @param itemType     지급 타입 (AVATAR_MONGLE 등)
         * @param category     카테고리
         */
        public static ExchangeResponse inventory(int balanceAfter, String itemName, Long userItemId,
                                                  String itemType, String category) {
            return new ExchangeResponse(true, balanceAfter, itemName,
                    userItemId, itemType, category, null, null);
        }
    }
}
