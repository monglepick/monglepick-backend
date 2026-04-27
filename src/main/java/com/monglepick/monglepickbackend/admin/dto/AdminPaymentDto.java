package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 결제/포인트 관리 API DTO 모음.
 *
 * <p>관리자 페이지 "결제/포인트" 탭에서 사용하는 요청/응답 DTO를 inner record 로 정의한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.1 결제/포인트(12 API) 범위의 데이터 모델을 담당한다.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>결제 주문: {@link PaymentOrderSummary}, {@link PaymentOrderDetail}, {@link AdminRefundRequest}, {@link AdminRefundResponse}</li>
 *   <li>구독: {@link SubscriptionSummary}, {@link AdminCompensateRequest}, {@link AdminCompensateResponse},
 *       {@link AdminCancelSubscriptionResponse}, {@link AdminExtendSubscriptionRequest}, {@link AdminExtendSubscriptionResponse}</li>
 *   <li>포인트 이력: {@link PointHistoryItem}, {@link AdminManualPointRequest}, {@link AdminManualPointResponse}</li>
 *   <li>포인트 아이템: {@link PointItemResponse}, {@link PointItemCreateRequest}, {@link PointItemUpdateRequest}</li>
 * </ul>
 *
 * <h3>설계 의도</h3>
 * <p>도메인 레이어의 PaymentService/SubscriptionService/PointService 로직을 그대로 재사용하되,
 * 관리자 화면 전용 표현을 위해 별도 DTO 레이어를 분리한다. 사용자용 PaymentDto/PointDto와 혼용하면
 * 필드 확장 시 호환성 문제가 발생하므로 관리자 전용 DTO 를 독립적으로 유지한다.</p>
 */
public final class AdminPaymentDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private AdminPaymentDto() {
    }

    // ======================== 결제 주문 ========================

    /**
     * 결제 주문 요약 응답 DTO (목록용).
     *
     * <p>관리자 결제 내역 테이블에서 한 건의 주문을 표현한다.
     * 페이지 응답 형태로 반환되며, 전체 컬럼 대신 목록 렌더링에 필요한 최소 필드만 담는다.</p>
     *
     * @param orderId     주문 UUID (VARCHAR(50) PK)
     * @param userId      주문자 사용자 ID
     * @param orderType   주문 유형 ("POINT_PACK" / "SUBSCRIPTION")
     * @param amount      결제 금액 (KRW)
     * @param pointsAmount 지급 포인트 (nullable — 구독은 null)
     * @param status      주문 상태 ("PENDING", "COMPLETED", "FAILED", "REFUNDED", "COMPENSATION_FAILED")
     * @param pgProvider  PG사 (nullable)
     * @param createdAt   주문 생성 시각
     * @param completedAt 결제 완료 시각 (nullable)
     */
    public record PaymentOrderSummary(
            String orderId,
            String userId,
            /** 주문자 이메일 (관리자 표시용, 2026-04-14 추가) */
            String email,
            /** 주문자 닉네임 (관리자 표시용, 2026-04-14 추가) */
            String nickname,
            String orderType,
            Integer amount,
            Integer pointsAmount,
            String status,
            String pgProvider,
            /** 실패/보상 실패 사유 (관리자 실패 대응용, 2026-04-14 추가) */
            String failedReason,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {}

    /**
     * 결제 주문 상세 응답 DTO.
     *
     * <p>관리자 결제 상세 화면에서 사용하며, 요약 응답보다 많은 필드를 포함한다.
     * 환불 상태, 실패 사유, 영수증 URL, 카드 정보 등이 추가된다.</p>
     *
     * @param orderId         주문 UUID
     * @param userId          주문자 사용자 ID
     * @param orderType       주문 유형
     * @param amount          결제 금액
     * @param pointsAmount    지급 포인트 (nullable)
     * @param status          주문 상태
     * @param pgProvider      PG사 (nullable)
     * @param pgTransactionId PG 거래 ID (nullable)
     * @param cardInfo        카드 정보 (nullable, 예: "1234 / 신한카드")
     * @param receiptUrl      결제 영수증 URL (nullable)
     * @param failedReason    실패 사유 (nullable)
     * @param refundReason    환불 사유 (nullable)
     * @param refundAmount    환불 금액 (nullable)
     * @param refundedAt      환불 일시 (nullable)
     * @param createdAt       주문 생성 시각
     * @param completedAt     결제 완료 시각 (nullable)
     */
    public record PaymentOrderDetail(
            String orderId,
            String userId,
            String orderType,
            Integer amount,
            Integer pointsAmount,
            String status,
            String pgProvider,
            String pgTransactionId,
            String cardInfo,
            String receiptUrl,
            String failedReason,
            String refundReason,
            Integer refundAmount,
            LocalDateTime refundedAt,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {}

    /**
     * 관리자 환불 요청 DTO.
     *
     * <p>관리자 페이지의 환불 모달에서 사유를 입력받아 전달한다.
     * 도메인 환불 로직(PaymentService.refundOrder)을 그대로 재사용하되,
     * userId는 경로 파라미터의 주문에서 직접 조회하므로 요청 본문에는 포함하지 않는다.</p>
     *
     * @param reason 환불 사유 (선택 — 생략 시 "관리자 환불 처리"로 기록)
     */
    public record AdminRefundRequest(
            @Size(max = 500, message = "환불 사유는 최대 500자입니다.")
            String reason
    ) {}

    /**
     * 관리자 환불 응답 DTO.
     *
     * @param success      환불 성공 여부 (멱등 응답 포함 — 이미 환불된 건도 true)
     * @param orderId      환불 처리된 주문 UUID
     * @param refundAmount 환불 금액 (KRW)
     * @param message      처리 결과 안내 메시지
     */
    public record AdminRefundResponse(
            boolean success,
            String orderId,
            Integer refundAmount,
            String message
    ) {}

    /**
     * 관리자 PG 재조회 동기화 응답 DTO (2026-04-24 추가).
     *
     * <p>Toss 콘솔에서 직접 취소하거나 웹훅이 유실되어 PG 만 취소되고 우리 DB 는
     * COMPLETED 로 남은 주문을, 관리자가 "PG 재조회" 버튼으로 Toss 의 현재 상태를 조회해
     * DB 에 맞춰 동기화한 결과를 표현한다. Toss 재호출(cancelPayment) 은 수행하지 않고
     * 조회(getPayment) 만 하며, 불일치 시 포인트 회수 + DB REFUNDED 마킹을 수행한다.</p>
     *
     * @param result          동기화 결과 구분자
     *                        <ul>
     *                          <li>{@code SYNCED}     — Toss 가 CANCELED/PARTIAL_CANCELED 이고 DB 가 COMPLETED 였음 →
     *                                                    포인트 회수 후 DB REFUNDED 로 갱신 완료</li>
     *                          <li>{@code NO_CHANGE}  — DB/PG 상태가 이미 일치함 (변경 없음)</li>
     *                          <li>{@code MISMATCH}   — 일치하지 않으나 자동 동기화 규칙에 해당하지 않음
     *                                                    (예: PG DONE, DB REFUNDED — 수동 검토 필요)</li>
     *                        </ul>
     * @param dbStatus        DB 주문 현재 상태 (동기화 후 최종 상태)
     * @param pgStatus        Toss 결제 현재 상태 (조회 결과 그대로)
     * @param pointsRecovered 회수된 포인트 금액 (SYNCED 이고 POINT_PACK 인 경우에만 양수, 그 외 0)
     * @param message         관리자 UI 노출용 처리 요약 메시지
     */
    public record AdminPgSyncResponse(
            String result,
            String dbStatus,
            String pgStatus,
            Integer pointsRecovered,
            String message
    ) {}

    // ======================== 구독 ========================

    /**
     * 구독 요약 응답 DTO (목록용).
     *
     * <p>관리자 구독 관리 테이블에서 한 건의 구독을 표현한다.
     * plan 정보는 JOIN FETCH로 즉시 로딩하여 N+1을 방지한다 (서비스 레이어 책임).</p>
     *
     * @param subscriptionId    구독 레코드 ID (PK)
     * @param userId            구독자 사용자 ID
     * @param planCode          구독 상품 코드 (예: "monthly_basic")
     * @param planName          구독 상품명 (예: "월간 기본")
     * @param periodType        주기 ("MONTHLY" / "YEARLY")
     * @param price             상품 가격 (KRW)
     * @param status            구독 상태 ("ACTIVE" / "CANCELLED" / "EXPIRED")
     * @param autoRenew         자동 갱신 여부
     * @param remainingAiBonus  이번 달 남은 AI 보너스 횟수
     * @param startedAt         구독 시작 시각
     * @param expiresAt         만료 예정 시각
     * @param cancelledAt       취소 시각 (nullable)
     */
    public record SubscriptionSummary(
            Long subscriptionId,
            String userId,
            /** 구독자 이메일 (관리자 표시용, 2026-04-14 추가) */
            String email,
            /** 구독자 닉네임 (관리자 표시용, 2026-04-14 추가) */
            String nickname,
            String planCode,
            String planName,
            String periodType,
            Integer price,
            String status,
            Boolean autoRenew,
            Integer remainingAiBonus,
            LocalDateTime startedAt,
            LocalDateTime expiresAt,
            LocalDateTime cancelledAt
    ) {}

    /**
     * 관리자 보상 복구 요청 DTO.
     *
     * <p>COMPENSATION_FAILED 상태 주문을 COMPLETED 로 복구할 때 사용한다.
     * 관리자가 Toss Payments 콘솔에서 결제 이력을 확인하고 포인트를 수동 지급한 뒤
     * 이 API 로 상태를 최종 복구한다.</p>
     *
     * @param adminNote 관리자 복구 메모 (감사 로그 용, 필수)
     */
    public record AdminCompensateRequest(
            @NotBlank(message = "복구 메모는 필수입니다.")
            @Size(max = 500, message = "복구 메모는 최대 500자입니다.")
            String adminNote
    ) {}

    /**
     * 관리자 보상 복구 응답 DTO.
     *
     * @param success  복구 성공 여부
     * @param orderId  복구 처리된 주문 UUID
     * @param status   최종 주문 상태 (성공 시 "COMPLETED")
     * @param message  처리 결과 안내 메시지
     */
    public record AdminCompensateResponse(
            boolean success,
            String orderId,
            String status,
            String message
    ) {}

    /**
     * 관리자 구독 취소 응답 DTO.
     *
     * @param success        취소 성공 여부
     * @param subscriptionId 취소 처리된 구독 ID
     * @param status         최종 구독 상태 (CANCELLED)
     * @param expiresAt      남은 혜택 만료 시각
     * @param message        처리 결과 안내 메시지
     */
    public record AdminCancelSubscriptionResponse(
            boolean success,
            Long subscriptionId,
            String status,
            LocalDateTime expiresAt,
            String message
    ) {}

    /**
     * 관리자 구독 연장 요청 DTO.
     *
     * @param adminNote 연장 사유 메모 (감사 로그 용, nullable — null 이면 "관리자 연장"으로 기록)
     */
    public record AdminExtendSubscriptionRequest(
            @Size(max = 500, message = "연장 메모는 최대 500자입니다.")
            String adminNote
    ) {}

    /**
     * 관리자 구독 연장 응답 DTO.
     *
     * @param success      연장 성공 여부
     * @param newExpiresAt 연장 후 새 만료 시각
     * @param message      처리 결과 안내 메시지
     */
    public record AdminExtendSubscriptionResponse(
            boolean success,
            LocalDateTime newExpiresAt,
            String message
    ) {}

    // ======================== 포인트 ========================

    /**
     * 포인트 변동 이력 단건 응답 DTO.
     *
     * <p>관리자 포인트 이력 화면에서 한 건의 변동 내역을 표현한다.
     * PointsHistory 엔티티의 필드를 그대로 노출한다.</p>
     *
     * @param historyId    이력 레코드 ID (PK)
     * @param userId       사용자 ID
     * @param pointChange  포인트 변동량 (양수=적립, 음수=차감)
     * @param pointAfter   변동 후 잔액
     * @param pointType    변동 유형 (earn, spend, refund, revoke 등)
     * @param actionType   활동 유형 코드 (nullable, 리워드 외 변동은 null)
     * @param description  변동 사유 설명
     * @param referenceId  참조 ID (nullable)
     * @param createdAt    변동 발생 시각
     */
    public record PointHistoryItem(
            Long historyId,
            String userId,
            /** 사용자 이메일 (관리자 표시용, 2026-04-14 추가) */
            String email,
            /** 사용자 닉네임 (관리자 표시용, 2026-04-14 추가) */
            String nickname,
            Integer pointChange,
            Integer pointAfter,
            String pointType,
            String actionType,
            String description,
            String referenceId,
            LocalDateTime createdAt
    ) {}

    /**
     * 관리자 수동 포인트 지급/차감 요청 DTO.
     *
     * <p>amount 양수 → 지급 (earnPoint), 음수 → 차감 (deductPoint).
     * 이 경로는 활동 리워드가 아니므로 {@code isActivityReward=false}로 호출되어
     * 등급 계산에 반영되지 않는다.</p>
     *
     * @param userId      대상 사용자 ID (필수)
     * @param amount      포인트 변동량 (양수 또는 음수, 0 불가)
     * @param reason      변동 사유 (필수, 감사 추적용)
     */
    public record AdminManualPointRequest(
            @NotBlank(message = "사용자 ID는 필수입니다.")
            @Size(max = 50, message = "사용자 ID는 최대 50자입니다.")
            String userId,

            /*
             * 포인트 변동량 (양수=지급, 음수=차감, 0 불가).
             *
             * <p>상한/하한을 두는 이유:</p>
             * <ul>
             *   <li>{@code Integer} 범위 초과 값을 JSON 으로 보내면 Jackson 이 직렬화 단계에서
             *       {@code HttpMessageNotReadableException} 을 던져 500 응답으로 떨어진다 (1P=10원이므로
             *       오타로 10조 등 비현실적 값이 들어오는 것을 방어).</li>
             *   <li>{@code user_points.balance} / {@code points_history.point_change} 컬럼이 {@code INT}
             *       (±21억) 이므로 누적 잔액까지 고려해 단일 요청을 훨씬 작은 범위로 제한한다.</li>
             * </ul>
             *
             * <p>상한 {@code 100_000_000} = 1억P = 10억원 상당. 운영 1회 보상으로는 과도하게 큰 값이지만
             * CS 대응·서비스 장애 보상 범위를 충분히 커버한다. 관리자 UI 에서 실수 방지용 한 번 더 확인을
             * 거치는 금액대.</p>
             */
            @NotNull(message = "포인트 변동량은 필수입니다.")
            @Min(value = -100_000_000, message = "포인트 변동량은 -1억 이상이어야 합니다.")
            @Max(value = 100_000_000, message = "포인트 변동량은 1억 이하여야 합니다.")
            Integer amount,

            @NotBlank(message = "변동 사유는 필수입니다.")
            @Size(max = 500, message = "변동 사유는 최대 500자입니다.")
            String reason
    ) {}

    /**
     * 관리자 수동 포인트 지급/차감 응답 DTO.
     *
     * @param success      처리 성공 여부
     * @param userId       대상 사용자 ID
     * @param pointChange  적용된 변동량
     * @param newBalance   변동 후 잔액
     * @param gradeCode    변동 후 등급 코드
     * @param message      처리 결과 안내 메시지
     */
    public record AdminManualPointResponse(
            boolean success,
            String userId,
            Integer pointChange,
            Integer newBalance,
            String gradeCode,
            String message
    ) {}

    // ======================== 포인트 아이템 ========================

    /**
     * 포인트 아이템 응답 DTO.
     *
     * @param pointItemId     아이템 고유 ID
     * @param itemName        상품명
     * @param itemDescription 상품 설명 (nullable)
     * @param itemPrice       필요 포인트
     * @param itemCategory    카테고리 (general/coupon/avatar/ai 등)
     * @param isActive        판매 활성 여부
     * @param createdAt       등록 시각
     * @param updatedAt       최종 수정 시각
     */
    public record PointItemResponse(
            Long pointItemId,
            String itemName,
            String itemDescription,
            Integer itemPrice,
            String itemCategory,
            /**
             * 지급 분기 키 — {@link com.monglepick.monglepickbackend.domain.reward.constants.PointItemType} enum name.
             * 2026-04-27 신규: Admin UI 에서 신규 아바타/배지 등록 시 NULL 로 저장되어 BusinessException(UNSUPPORTED) 차단을 발생시키던 결함 보정.
             */
            String itemType,
            /** 지급 수량 (AI 토큰 횟수 또는 인벤토리 발급 수). NULL이면 itemType 기본값 사용. */
            Integer amount,
            /** 유효기간(일). NULL이면 무기한 또는 itemType 기본값 사용. */
            Integer durationDays,
            /** 아바타·배지 이미지 경로 (정적 리소스 또는 CDN). NULL이면 카테고리별 기본 아이콘. */
            String imageUrl,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * 포인트 아이템 신규 등록 요청 DTO.
     *
     * <p>2026-04-27: 프로필 꾸미기 도입을 위해 itemType/amount/durationDays/imageUrl 노출.
     * Admin UI 에서 신규 아바타·배지 등록 시 itemType 미지정으로 인한 UNSUPPORTED 교환 차단을 해결한다.</p>
     *
     * @param itemName        상품명 (필수, 최대 200자)
     * @param itemDescription 상품 설명 (nullable, TEXT)
     * @param itemPrice       필요 포인트 (필수, 0 이상)
     * @param itemCategory    카테고리 (nullable → 기본값 "general")
     * @param itemType        지급 분기 키 (nullable — 등록 시 미지정 가능. 단 미지정 시 교환 차단된다)
     * @param amount          지급 수량 (nullable — itemType 기본값 fallback)
     * @param durationDays    유효기간(일, nullable — 무기한 또는 itemType 기본값)
     * @param imageUrl        아바타/배지 이미지 경로 (nullable, 최대 500자)
     * @param isActive        초기 활성 여부 (nullable → 기본값 true)
     */
    public record PointItemCreateRequest(
            @NotBlank(message = "상품명은 필수입니다.")
            @Size(max = 200, message = "상품명은 최대 200자입니다.")
            String itemName,

            String itemDescription,

            @NotNull(message = "필요 포인트는 필수입니다.")
            @Min(value = 0, message = "필요 포인트는 0 이상이어야 합니다.")
            Integer itemPrice,

            @Size(max = 50, message = "카테고리는 최대 50자입니다.")
            String itemCategory,

            @Size(max = 50, message = "itemType 은 최대 50자입니다.")
            String itemType,

            @Min(value = 0, message = "지급 수량은 0 이상이어야 합니다.")
            Integer amount,

            @Min(value = 0, message = "유효기간은 0 이상이어야 합니다.")
            Integer durationDays,

            @Size(max = 500, message = "이미지 경로는 최대 500자입니다.")
            String imageUrl,

            Boolean isActive
    ) {}

    /**
     * 포인트 아이템 수정 요청 DTO.
     *
     * <p>존재하는 아이템의 필드를 갱신한다. 핵심 필드(name/price/active)는 필수이며,
     * itemType/amount/durationDays/imageUrl 은 nullable — 기존 값을 보존하려면 프런트가 그대로 다시 보낸다.</p>
     */
    public record PointItemUpdateRequest(
            @NotBlank(message = "상품명은 필수입니다.")
            @Size(max = 200, message = "상품명은 최대 200자입니다.")
            String itemName,

            String itemDescription,

            @NotNull(message = "필요 포인트는 필수입니다.")
            @Min(value = 0, message = "필요 포인트는 0 이상이어야 합니다.")
            Integer itemPrice,

            @Size(max = 50, message = "카테고리는 최대 50자입니다.")
            String itemCategory,

            @Size(max = 50, message = "itemType 은 최대 50자입니다.")
            String itemType,

            @Min(value = 0, message = "지급 수량은 0 이상이어야 합니다.")
            Integer amount,

            @Min(value = 0, message = "유효기간은 0 이상이어야 합니다.")
            Integer durationDays,

            @Size(max = 500, message = "이미지 경로는 최대 500자입니다.")
            String imageUrl,

            @NotNull(message = "활성화 여부는 필수입니다.")
            Boolean isActive
    ) {}
}
