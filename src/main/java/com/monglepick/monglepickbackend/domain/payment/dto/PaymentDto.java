package com.monglepick.monglepickbackend.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 결제/구독 시스템 DTO 모음.
 *
 * <p>모든 결제 관련 요청/응답 DTO를 inner record로 정의한다.
 * record를 사용하여 불변 객체로 관리하며, Jackson 직렬화/역직렬화를 지원한다.</p>
 *
 * <h3>DTO 목록</h3>
 * <ul>
 *   <li><b>주문 생성</b>: {@link CreateOrderRequest}, {@link OrderResponse}</li>
 *   <li><b>결제 승인</b>: {@link ConfirmRequest}, {@link ConfirmResponse}</li>
 *   <li><b>결제 내역</b>: {@link OrderHistoryResponse}</li>
 *   <li><b>구독 상품</b>: {@link SubscriptionPlanResponse}</li>
 *   <li><b>구독 상태</b>: {@link SubscriptionStatusResponse}</li>
 *   <li><b>웹훅</b>: {@link TossWebhookPayload}</li>
 * </ul>
 *
 * <h3>클라이언트 연동 주의사항</h3>
 * <p>monglepick-client가 이 DTO의 JSON 응답을 직접 파싱한다.
 * 필드명(camelCase) 변경 시 클라이언트 쪽도 반드시 동기화해야 한다.</p>
 *
 * @see com.monglepick.monglepickbackend.domain.payment.service.PaymentService
 * @see com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService
 */
public final class PaymentDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private PaymentDto() {
    }

    // ──────────────────────────────────────────────
    // 주문 생성 (클라이언트 → Backend)
    // ──────────────────────────────────────────────

    /**
     * 결제 주문 생성 요청.
     *
     * <p>클라이언트가 Toss Payments 결제창을 열기 전에
     * 주문을 먼저 생성하여 orderId를 받아야 한다.
     * orderId는 서버에서 UUID로 생성하며, PG에 전달하는 고유 주문 번호가 된다.</p>
     *
     * <h4>주문 유형별 필수 필드</h4>
     * <ul>
     *   <li>{@code point_pack}: pointsAmount 필수 (지급할 포인트 수량)</li>
     *   <li>{@code subscription}: planCode 필수 (구독 상품 코드, 예: "monthly_basic")</li>
     * </ul>
     *
     * @param userId       주문자 사용자 ID (필수)
     * @param orderType    주문 유형 — "point_pack" 또는 "subscription" (필수)
     * @param amount       결제 금액 (KRW 원 단위, 100원 이상)
     * @param pointsAmount 지급 포인트 수량 (포인트팩인 경우 필수, 구독이면 null 가능)
     * @param planCode     구독 상품 코드 (구독인 경우 필수, 포인트팩이면 null 가능)
     */
    public record CreateOrderRequest(
            @NotBlank(message = "사용자 ID는 필수입니다")
            String userId,

            @NotBlank(message = "주문 유형은 필수입니다 (point_pack 또는 subscription)")
            String orderType,

            @Min(value = 100, message = "결제 금액은 100원 이상이어야 합니다")
            int amount,

            Integer pointsAmount,

            String planCode
    ) {
    }

    /**
     * 결제 주문 생성 응답.
     *
     * <p>클라이언트는 이 응답의 {@code orderId}와 {@code clientKey}를 사용하여
     * Toss Payments JavaScript SDK로 결제창을 호출한다.</p>
     *
     * <h4>클라이언트 사용 예시</h4>
     * <pre>{@code
     * const tossPayments = TossPayments(clientKey);
     * tossPayments.requestPayment({
     *     amount: amount,
     *     orderId: orderId,
     *     orderName: "포인트 1000P",
     *     ...
     * });
     * }</pre>
     *
     * @param orderId   서버 생성 주문 UUID (PG에 전달되는 고유 번호)
     * @param amount    결제 금액 (KRW)
     * @param clientKey Toss Payments 클라이언트 키 (결제창 SDK에 필요)
     */
    public record OrderResponse(
            String orderId,
            int amount,
            String clientKey
    ) {
    }

    // ──────────────────────────────────────────────
    // 결제 승인 (클라이언트 → Backend)
    // ──────────────────────────────────────────────

    /**
     * 결제 승인 요청.
     *
     * <p>Toss Payments 결제창에서 사용자가 결제를 완료하면,
     * 클라이언트가 리다이렉트 URL에서 받은 {@code paymentKey}와 {@code orderId},
     * {@code amount}를 서버에 전달하여 최종 승인을 요청한다.</p>
     *
     * <h4>멱등성 보장</h4>
     * <p>같은 orderId로 중복 승인을 시도하면 {@code DUPLICATE_ORDER} 에러가 반환된다.
     * 이미 COMPLETED 상태인 주문은 재처리하지 않는다.</p>
     *
     * @param orderId    주문 UUID (주문 생성 시 발급받은 값)
     * @param paymentKey Toss Payments에서 발급한 결제 키 (결제 완료 시 리다이렉트로 전달됨)
     * @param amount     결제 금액 (KRW, 주문 생성 시 금액과 일치해야 함)
     */
    public record ConfirmRequest(
            @NotBlank(message = "주문 ID는 필수입니다")
            String orderId,

            @NotBlank(message = "결제 키는 필수입니다")
            String paymentKey,

            @Min(value = 100, message = "결제 금액은 100원 이상이어야 합니다")
            int amount
    ) {
    }

    /**
     * 결제 승인 응답.
     *
     * <p>결제 승인 성공 시 포인트 지급 결과를 함께 반환한다.
     * 클라이언트는 이 응답으로 성공 화면을 표시하고 포인트 잔액을 갱신한다.</p>
     *
     * @param success       승인 성공 여부 (true: 성공)
     * @param pointsGranted 지급된 포인트 수량 (0이면 포인트 지급 없음)
     * @param newBalance    지급 후 포인트 잔액
     */
    public record ConfirmResponse(
            boolean success,
            int pointsGranted,
            int newBalance
    ) {
    }

    // ──────────────────────────────────────────────
    // 결제 내역 (클라이언트 조회)
    // ──────────────────────────────────────────────

    /**
     * 결제 주문 내역 응답 (단건).
     *
     * <p>클라이언트의 "결제 내역" 목록에서 한 건의 주문을 표현한다.
     * 페이징 조회 시 {@link org.springframework.data.domain.Page}에 담겨 반환된다.</p>
     *
     * @param orderId     주문 UUID
     * @param orderType   주문 유형 ("POINT_PACK" 또는 "SUBSCRIPTION")
     * @param amount      결제 금액 (KRW)
     * @param pointsAmount 지급 포인트 수량 (nullable)
     * @param status      주문 상태 ("PENDING", "COMPLETED", "FAILED", "REFUNDED")
     * @param pgProvider  PG사 이름 (nullable, 예: "TOSS")
     * @param completedAt 결제 완료 시각 (nullable, PENDING/FAILED 시 null)
     * @param createdAt   주문 생성 시각
     */
    public record OrderHistoryResponse(
            String orderId,
            String orderType,
            int amount,
            Integer pointsAmount,
            String status,
            String pgProvider,
            LocalDateTime completedAt,
            LocalDateTime createdAt
    ) {
    }

    // ──────────────────────────────────────────────
    // 구독 상품 조회
    // ──────────────────────────────────────────────

    /**
     * 구독 상품 응답 (단건).
     *
     * <p>클라이언트의 "구독 상품 목록" 화면에서 한 건의 상품을 표현한다.
     * 활성 상품만 가격 오름차순으로 반환된다.</p>
     *
     * @param planId         상품 ID (PK)
     * @param planCode       상품 코드 (예: "monthly_basic")
     * @param name           상품명 (예: "월간 기본")
     * @param periodType     구독 주기 ("MONTHLY" 또는 "YEARLY")
     * @param price          가격 (KRW 원 단위)
     * @param pointsPerPeriod 주기당 지급 포인트
     * @param description    상품 설명 (nullable)
     */
    public record SubscriptionPlanResponse(
            Long planId,
            String planCode,
            String name,
            String periodType,
            int price,
            int pointsPerPeriod,
            String description
    ) {
    }

    // ──────────────────────────────────────────────
    // 구독 상태 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자 구독 상태 응답.
     *
     * <p>활성 구독이 있으면 상세 정보를 포함하고,
     * 없으면 {@code hasActiveSubscription=false}만 반환한다.
     * 클라이언트의 "내 구독" 화면에서 사용된다.</p>
     *
     * @param hasActiveSubscription 활성 구독 보유 여부
     * @param planName              구독 상품명 (없으면 null)
     * @param status                구독 상태 — "ACTIVE", "CANCELLED", "EXPIRED" (없으면 null)
     * @param startedAt             구독 시작 시각 (없으면 null)
     * @param expiresAt             만료 예정 시각 (없으면 null)
     * @param autoRenew             자동 갱신 여부 (없으면 false)
     */
    public record SubscriptionStatusResponse(
            boolean hasActiveSubscription,
            String planName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime expiresAt,
            boolean autoRenew
    ) {
    }

    // ──────────────────────────────────────────────
    // Toss Payments 웹훅 (향후 확장)
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 웹훅 페이로드 (간소화 버전).
     *
     * <p>Toss Payments가 결제 상태 변경 시 POST로 전송하는 웹훅 데이터.
     * 현재는 로깅용으로만 수신하며, 향후 결제 확인 자동화에 활용 예정.</p>
     *
     * <h4>주요 eventType</h4>
     * <ul>
     *   <li>{@code PAYMENT_STATUS_CHANGED} — 결제 상태 변경 (승인/취소/환불)</li>
     *   <li>{@code PAYOUT_STATUS_CHANGED} — 정산 상태 변경</li>
     * </ul>
     *
     * @param eventType 웹훅 이벤트 유형 (예: "PAYMENT_STATUS_CHANGED")
     * @param data      Toss 웹훅 데이터 구조 (JSON 형태, 향후 타입 세분화 예정)
     */
    public record TossWebhookPayload(
            String eventType,
            Object data
    ) {
    }
}
