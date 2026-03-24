package com.monglepick.monglepickbackend.domain.reward.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

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
     * 포인트 잔액 확인 응답.
     *
     * <p>Agent의 {@code point_client.py}가 이 JSON을 직접 파싱한다.
     * <b>기존 5개 필드(allowed, balance, cost, message, maxInputLength)는 절대 변경 금지.</b>
     * Agent는 기존 필드만 파싱하므로 추가 필드는 Agent가 무시한다 (하위 호환성 유지).</p>
     *
     * <h4>Phase R-3 추가 필드 (쿼터 관련)</h4>
     * <p>등급별 일일/월간 사용 한도 정보를 포함한다. 클라이언트(monglepick-client)에서
     * "오늘 N/M회 사용" 등의 UI 표시에 활용할 수 있다.</p>
     *
     * @param allowed        사용 가능 여부 (잔액 + 쿼터 모두 통과 시 true)
     * @param balance        현재 보유 포인트
     * @param cost           요청된 비용 (원래 비용, 무료 적용 전)
     * @param message        결과 메시지 (부족/한도초과 시 안내 메시지 포함)
     * @param maxInputLength 등급별 최대 입력 글자 수 (BRONZE:200, SILVER:500, GOLD:1000, PLATINUM:2000)
     * @param dailyUsed      오늘 AI 추천 사용 횟수 (Phase R-3 추가)
     * @param dailyLimit     일일 한도 (-1이면 무제한) (Phase R-3 추가)
     * @param monthlyUsed    이번 달 AI 추천 사용 횟수 (Phase R-3 추가)
     * @param monthlyLimit   월간 한도 (-1이면 무제한) (Phase R-3 추가)
     * @param freeRemaining  오늘 남은 무료 사용 횟수 (Phase R-3 추가)
     * @param effectiveCost  실제 차감 포인트 — 무료 잔여가 있으면 0, 없으면 cost와 동일 (Phase R-3 추가)
     */
    public record CheckResponse(
            boolean allowed,
            int balance,
            int cost,
            String message,
            int maxInputLength,
            // ── Phase R-3 쿼터 필드 (하위 호환: Agent는 이 필드를 무시) ──
            int dailyUsed,
            int dailyLimit,
            int monthlyUsed,
            int monthlyLimit,
            int freeRemaining,
            int effectiveCost
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
    public record EarnResponse(
            int balanceAfter,
            String grade
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
    public record PointItemResponse(
            Long itemId,
            String name,
            String description,
            int price,
            String category
    ) {
    }

    /**
     * 아이템 교환 응답.
     *
     * <p>포인트 아이템 교환(구매) 성공 시 결과를 반환한다.</p>
     *
     * @param success      교환 성공 여부
     * @param balanceAfter 교환 후 잔여 포인트
     * @param itemName     교환한 아이템명
     */
    public record ExchangeResponse(
            boolean success,
            int balanceAfter,
            String itemName
    ) {
    }
}
