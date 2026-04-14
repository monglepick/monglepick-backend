package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminCancelSubscriptionResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminCompensateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminCompensateResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminExtendSubscriptionRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminExtendSubscriptionResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminManualPointRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminManualPointResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminRefundRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminRefundResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PaymentOrderDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PaymentOrderSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointHistoryItem;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointItemCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointItemResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointItemUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.SubscriptionSummary;
import com.monglepick.monglepickbackend.admin.service.AdminPaymentService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 결제/포인트 API 컨트롤러.
 *
 * <p>관리자 페이지 "결제/포인트" 탭의 12개 엔드포인트를 제공한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.1 결제/포인트(12 API) 범위의 라우팅을 담당한다.</p>
 *
 * <h3>담당 엔드포인트 (12개)</h3>
 * <ul>
 *   <li>결제 주문 (3):
 *     GET /payment/orders, GET /payment/orders/{id}, POST /payment/orders/{id}/refund</li>
 *   <li>구독 (4):
 *     GET /subscription, POST /subscription/{id}/compensate,
 *     PUT /subscription/{id}/cancel, PUT /subscription/{id}/extend</li>
 *   <li>포인트 (2):
 *     GET /point/histories, POST /point/manual</li>
 *   <li>포인트 아이템 (3):
 *     GET /point/items, POST /point/items, PUT /point/items/{id}</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다 (SecurityConfig 의 anyRequest().authenticated() +
 * AdminLoginFilter 에서 ROLE_ADMIN 검증).</p>
 *
 * <h3>공통 응답 형식</h3>
 * <p>모든 응답은 {@link ApiResponse} 래퍼로 감싼다. 리소스 생성(POST)은 HTTP 201 Created,
 * 나머지는 HTTP 200 OK 를 반환한다.</p>
 */
@Tag(name = "관리자 — 결제/포인트", description = "결제 주문, 구독, 포인트 이력, 포인트 아이템 관리")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentController {

    /** 결제/포인트 관리 비즈니스 로직 서비스 */
    private final AdminPaymentService adminPaymentService;

    // ======================== 결제 주문 ========================

    /**
     * 결제 주문 목록을 최신순으로 페이징 조회한다.
     *
     * <p>status 쿼리 파라미터로 상태 필터링이 가능하다.
     * 생략 또는 빈 문자열이면 전체 주문을 반환한다.</p>
     *
     * @param status   주문 상태 필터 (PENDING/COMPLETED/FAILED/REFUNDED/COMPENSATION_FAILED)
     * @param pageable 페이지 정보 (기본 size=20)
     * @return 결제 주문 요약 페이지
     */
    @Operation(
            summary = "결제 주문 목록 조회",
            description = "결제 주문 목록을 최신순으로 페이징 조회한다. status 파라미터로 상태 필터링이 가능하다."
    )
    @GetMapping("/payment/orders")
    public ResponseEntity<ApiResponse<Page<PaymentOrderSummary>>> getOrders(
            @Parameter(description = "주문 상태 필터 (생략 시 전체)")
            @RequestParam(required = false) String status,
            @Parameter(description = "주문 유형 필터 (SUBSCRIPTION/POINT_PACK, 생략 시 전체)")
            @RequestParam(required = false) String orderType,
            @Parameter(description = "특정 사용자 ID 필터 (생략 시 전체)")
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminPayment] 결제 내역 조회 요청 — status={}, orderType={}, userId={}, page={}",
                status, orderType, userId, pageable.getPageNumber());
        Page<PaymentOrderSummary> result = adminPaymentService.getOrders(status, orderType, userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 결제 주문 단건 상세를 조회한다.
     *
     * @param orderId 주문 UUID (경로 변수)
     * @return 결제 주문 상세 응답
     */
    @Operation(
            summary = "결제 주문 상세 조회",
            description = "결제 주문의 전체 필드(환불 정보, PG 거래 ID, 영수증 URL 등)를 조회한다."
    )
    @GetMapping("/payment/orders/{orderId}")
    public ResponseEntity<ApiResponse<PaymentOrderDetail>> getOrderDetail(
            @Parameter(description = "조회할 주문 UUID") @PathVariable String orderId
    ) {
        log.debug("[AdminPayment] 결제 상세 조회 요청 — orderId={}", orderId);
        PaymentOrderDetail result = adminPaymentService.getOrderDetail(orderId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 결제 주문을 환불 처리한다.
     *
     * <p>도메인 PaymentService.refundOrder 를 재사용하여 PG 취소 → 포인트 회수 → DB 상태 변경을
     * 일괄 수행한다. 이미 환불된 주문은 멱등 응답으로 성공 처리된다.</p>
     *
     * @param orderId 환불할 주문 UUID
     * @param request 환불 요청 DTO (사유, nullable)
     * @return 환불 응답 DTO
     */
    @Operation(
            summary = "결제 주문 환불",
            description = "COMPLETED 주문을 환불 처리한다. POINT_PACK 은 포인트를 자동 회수한다."
    )
    @PostMapping("/payment/orders/{orderId}/refund")
    public ResponseEntity<ApiResponse<AdminRefundResponse>> refundOrder(
            @Parameter(description = "환불할 주문 UUID") @PathVariable String orderId,
            @RequestBody(required = false) @Valid AdminRefundRequest request
    ) {
        log.info("[AdminPayment] 환불 처리 요청 — orderId={}", orderId);
        AdminRefundResponse result = adminPaymentService.refundOrder(orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ======================== 구독 ========================

    /**
     * 구독 목록을 페이징 조회한다.
     *
     * @param status   구독 상태 필터 (ACTIVE/CANCELLED/EXPIRED, 생략 시 전체)
     * @param pageable 페이지 정보
     * @return 구독 요약 페이지
     */
    @Operation(
            summary = "구독 목록 조회",
            description = "사용자 구독 현황을 페이징 조회한다. status 파라미터로 상태 필터링이 가능하다."
    )
    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<Page<SubscriptionSummary>>> getSubscriptions(
            @Parameter(description = "구독 상태 필터 (생략 시 전체)")
            @RequestParam(required = false) String status,
            @Parameter(description = "구독 플랜 코드 필터 (예: monthly_basic, 생략 시 전체)")
            @RequestParam(required = false) String planCode,
            @Parameter(description = "특정 사용자 ID 필터 (생략 시 전체)")
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminPayment] 구독 목록 조회 요청 — status={}, planCode={}, userId={}, page={}",
                status, planCode, userId, pageable.getPageNumber());
        Page<SubscriptionSummary> result =
                adminPaymentService.getSubscriptions(status, planCode, userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * COMPENSATION_FAILED 주문을 COMPLETED 로 수동 복구한다.
     *
     * <p>실제 Toss Payments 콘솔에서 포인트 수동 지급을 완료한 뒤,
     * 이 API 로 주문 상태를 최종 복구한다. adminNote 는 감사 로그에 기록된다.</p>
     *
     * <p>주의: 설계서 상에서는 "구독 보상" 엔드포인트로 명명되었으나,
     * 실제로는 COMPENSATION_FAILED 상태 {@code payment_orders} 를 복구하는 작업이므로
     * 경로는 /subscription/{id}/compensate 를 유지하되 path 변수는 주문 UUID 로 해석한다.</p>
     *
     * @param orderId 복구할 주문 UUID (설계서 경로의 {id}를 주문 UUID 로 해석)
     * @param request 복구 요청 DTO (adminNote 필수)
     * @return 복구 응답 DTO
     */
    @Operation(
            summary = "결제 보상 수동 복구",
            description = "COMPENSATION_FAILED 상태 주문을 관리자 메모와 함께 COMPLETED 로 복구한다."
    )
    @PostMapping("/subscription/{orderId}/compensate")
    public ResponseEntity<ApiResponse<AdminCompensateResponse>> compensateOrder(
            @Parameter(description = "복구할 주문 UUID") @PathVariable String orderId,
            @RequestBody @Valid AdminCompensateRequest request
    ) {
        log.info("[AdminPayment] 보상 복구 요청 — orderId={}", orderId);
        AdminCompensateResponse result = adminPaymentService.compensateOrder(orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 구독을 관리자 권한으로 취소한다.
     *
     * @param subscriptionId 취소할 구독 레코드 ID (PK)
     * @return 취소 응답 DTO
     */
    @Operation(
            summary = "구독 취소 (관리자)",
            description = "ACTIVE 상태 구독을 CANCELLED 로 변경하고 자동 갱신을 중지한다. 혜택은 만료일까지 유지된다."
    )
    @PutMapping("/subscription/{subscriptionId}/cancel")
    public ResponseEntity<ApiResponse<AdminCancelSubscriptionResponse>> cancelSubscription(
            @Parameter(description = "취소할 구독 레코드 ID") @PathVariable Long subscriptionId
    ) {
        log.info("[AdminPayment] 구독 취소 요청 — subscriptionId={}", subscriptionId);
        AdminCancelSubscriptionResponse result = adminPaymentService.cancelSubscription(subscriptionId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 구독을 1주기 연장한다.
     *
     * @param subscriptionId 연장할 구독 레코드 ID
     * @param request        연장 요청 DTO (adminNote nullable)
     * @return 연장 응답 DTO
     */
    @Operation(
            summary = "구독 연장 (관리자)",
            description = "구독을 1주기(월간=1개월, 연간=1년) 연장한다. CANCELLED 상태는 ACTIVE 로 재활성화된다."
    )
    @PutMapping("/subscription/{subscriptionId}/extend")
    public ResponseEntity<ApiResponse<AdminExtendSubscriptionResponse>> extendSubscription(
            @Parameter(description = "연장할 구독 레코드 ID") @PathVariable Long subscriptionId,
            @RequestBody(required = false) @Valid AdminExtendSubscriptionRequest request
    ) {
        log.info("[AdminPayment] 구독 연장 요청 — subscriptionId={}", subscriptionId);
        AdminExtendSubscriptionResponse result =
                adminPaymentService.extendSubscription(subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ======================== 포인트 이력 ========================

    /**
     * 포인트 변동 이력을 최신순으로 페이징 조회한다.
     *
     * <p>userId 가 있으면 해당 사용자 이력만, 없으면 전체 사용자 이력을 반환한다.</p>
     *
     * @param userId   사용자 ID 필터 (nullable)
     * @param pageable 페이지 정보
     * @return 포인트 이력 페이지
     */
    @Operation(
            summary = "포인트 변동 이력 조회",
            description = "포인트 변동 이력을 최신순으로 페이징 조회한다. userId 파라미터로 사용자 필터링이 가능하다."
    )
    @GetMapping("/point/histories")
    public ResponseEntity<ApiResponse<Page<PointHistoryItem>>> getPointHistories(
            @Parameter(description = "사용자 ID 필터 (생략 시 전체)")
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("[AdminPayment] 포인트 이력 조회 요청 — userId={}, page={}",
                userId, pageable.getPageNumber());
        Page<PointHistoryItem> result = adminPaymentService.getPointHistories(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 관리자 수동 포인트 지급/차감.
     *
     * <p>amount 양수 → 지급, 음수 → 차감, 0 → 400 에러.
     * 이 경로는 활동 리워드가 아니므로 등급 계산에 반영되지 않는다.</p>
     *
     * @param request 수동 변동 요청 DTO
     * @return 처리 결과 응답 DTO
     */
    @Operation(
            summary = "포인트 수동 지급/차감",
            description = "amount 양수는 지급, 음수는 차감. 등급 계산에는 반영되지 않는다 (isActivityReward=false)."
    )
    @PostMapping("/point/manual")
    public ResponseEntity<ApiResponse<AdminManualPointResponse>> manualPoint(
            @RequestBody @Valid AdminManualPointRequest request
    ) {
        log.info("[AdminPayment] 수동 포인트 요청 — userId={}, amount={}",
                request.userId(), request.amount());
        AdminManualPointResponse result = adminPaymentService.manualPoint(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ======================== 포인트 아이템 ========================

    /**
     * 포인트 아이템 전체 목록을 조회한다 (비활성 포함).
     *
     * @return 포인트 아이템 응답 리스트
     */
    @Operation(
            summary = "포인트 아이템 목록 조회",
            description = "포인트 상점 아이템 전체 목록을 가격 오름차순으로 조회한다. 비활성 아이템도 포함된다."
    )
    @GetMapping("/point/items")
    public ResponseEntity<ApiResponse<List<PointItemResponse>>> getPointItems() {
        log.debug("[AdminPayment] 포인트 아이템 목록 조회 요청");
        List<PointItemResponse> result = adminPaymentService.getPointItems();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 신규 포인트 아이템을 등록한다.
     *
     * @param request 등록 요청 DTO
     * @return 등록된 아이템 응답 (HTTP 201)
     */
    @Operation(
            summary = "포인트 아이템 등록",
            description = "신규 포인트 아이템을 등록한다. 상품명/필요 포인트가 필수이다."
    )
    @PostMapping("/point/items")
    public ResponseEntity<ApiResponse<PointItemResponse>> createPointItem(
            @RequestBody @Valid PointItemCreateRequest request
    ) {
        log.info("[AdminPayment] 포인트 아이템 등록 요청 — name={}", request.itemName());
        PointItemResponse result = adminPaymentService.createPointItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    /**
     * 기존 포인트 아이템을 수정한다.
     *
     * @param itemId  수정할 아이템 ID
     * @param request 수정 요청 DTO
     * @return 수정된 아이템 응답
     */
    @Operation(
            summary = "포인트 아이템 수정",
            description = "기존 포인트 아이템의 이름/설명/가격/카테고리/활성 여부를 수정한다."
    )
    @PutMapping("/point/items/{itemId}")
    public ResponseEntity<ApiResponse<PointItemResponse>> updatePointItem(
            @Parameter(description = "수정할 포인트 아이템 ID") @PathVariable Long itemId,
            @RequestBody @Valid PointItemUpdateRequest request
    ) {
        log.info("[AdminPayment] 포인트 아이템 수정 요청 — itemId={}", itemId);
        PointItemResponse result = adminPaymentService.updatePointItem(itemId, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // 설계서 범위: 포인트 아이템은 물리 삭제 대신 isActive=false 로 비활성화한다.
    // (따라서 DELETE EP 는 정의하지 않는다)
}
