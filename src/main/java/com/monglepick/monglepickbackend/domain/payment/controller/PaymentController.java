package com.monglepick.monglepickbackend.domain.payment.controller;

import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.CreateOrderRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.OrderHistoryResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.OrderResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.RefundRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.RefundResponse;
import com.monglepick.monglepickbackend.domain.payment.service.PaymentService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 결제 API 컨트롤러 — Toss Payments 결제 주문 생성, 승인, 내역 조회, 웹훅 처리.
 *
 * <p>클라이언트(monglepick-client)가 호출하는 결제 REST API 4개를 제공한다.
 * JWT 인증 기반으로 Principal에서 userId를 추출한다.</p>
 *
 * @see PaymentService 결제 비즈니스 로직
 */
@Tag(name = "결제", description = "Toss Payments 연동 결제")
@RestController
@RequestMapping("/api/v1/payment")
@Slf4j
@RequiredArgsConstructor
public class PaymentController extends BaseController {

    /** 결제 서비스 */
    private final PaymentService paymentService;

    // ──────────────────────────────────────────────
    // POST /api/v1/payment/orders — 주문 생성
    // ──────────────────────────────────────────────

    /**
     * 결제 주문을 생성한다.
     *
     * <p>클라이언트가 Toss Payments 결제창을 열기 전에 호출한다.
     * 서버에서 UUID 기반의 orderId를 생성하고, Toss clientKey와 함께 반환한다.</p>
     *
     * @param request        주문 생성 요청 (userId, orderType, amount, pointsAmount?, planCode?)
     * @param idempotencyKey 멱등키 (중복 주문 방지, 선택)
     * @return 200 OK + OrderResponse (orderId, amount, clientKey)
     */
    @Operation(
            summary = "결제 주문 생성",
            description = "Toss Payments 결제창 호출 전 주문 생성. 멱등키로 중복 방지 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "422", description = "멱등키 재사용 (동일 키, 다른 파라미터)")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            Principal principal,
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(name = "Idempotency-Key", description = "멱등키 (UUID, 중복 주문 방지)", in = ParameterIn.HEADER)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // JWT에서 userId를 추출하여 사용 (request body의 userId 무시 — BOLA 방지)
        String userId = resolveUserId(principal);
        log.info("주문 생성 API 호출: userId={}, orderType={}, amount={}, idempotencyKey={}",
                userId, request.orderType(), request.amount(), idempotencyKey);

        OrderResponse response = paymentService.createOrder(userId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // POST /api/v1/payment/confirm — 결제 승인
    // ──────────────────────────────────────────────

    /**
     * 결제를 승인하고 포인트를 지급한다.
     *
     * <p>Toss Payments 결제창 완료 후 paymentKey와 함께 호출한다.</p>
     *
     * @param request 결제 승인 요청 (orderId, paymentKey, amount)
     * @return 200 OK + ConfirmResponse (success, pointsGranted, newBalance)
     */
    @Operation(summary = "결제 승인", description = "Toss 결제 승인 → DB 상태 변경 → 포인트 지급 (원자적 처리)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 승인 성공"),
            @ApiResponse(responseCode = "400", description = "금액 불일치 또는 PG 승인 실패"),
            @ApiResponse(responseCode = "404", description = "주문 미존재"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 주문")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/confirm")
    public ResponseEntity<ConfirmResponse> confirmPayment(
            Principal principal,
            @Valid @RequestBody ConfirmRequest request) {
        // JWT에서 userId 추출 — 주문 소유자 검증에 사용 (BOLA 방지)
        String userId = resolveUserId(principal);
        log.info("결제 승인 API 호출: userId={}, orderId={}, amount={}", userId, request.orderId(), request.amount());

        ConfirmResponse response = paymentService.confirmPayment(userId, request);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // GET /api/v1/payment/orders — 결제 내역 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자의 결제 내역을 페이징으로 조회한다.
     *
     * @param page 페이지 번호 (0부터 시작, 기본값: 0)
     * @param size 페이지 크기 (기본값: 20)
     * @return 200 OK + Page<OrderHistoryResponse>
     */
    @Operation(summary = "결제 내역 조회", description = "사용자의 전체 결제 주문 내역 페이징 조회")
    @ApiResponse(responseCode = "200", description = "결제 내역 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/orders")
    public ResponseEntity<Page<OrderHistoryResponse>> getOrders(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = resolveUserId(principal);
        log.debug("결제 내역 조회 API 호출: userId={}, page={}, size={}", userId, page, size);

        // 페이지 크기 상한 제한 (과도한 요청으로 인한 DB 과부하 방지)
        Page<OrderHistoryResponse> orders = paymentService.getOrderHistory(
                userId, PageRequest.of(page, limitPageSize(size)));
        return ResponseEntity.ok(orders);
    }

    // ──────────────────────────────────────────────
    // POST /api/v1/payment/orders/{orderId}/refund — 환불
    // ──────────────────────────────────────────────

    /**
     * 완료된 결제 주문을 환불 처리한다.
     *
     * <p>COMPLETED 상태의 주문에 대해 환불을 요청한다.
     * POINT_PACK 주문의 경우 지급된 포인트를 회수한 뒤 Toss Payments 취소 API를 호출하고,
     * DB 상태를 REFUNDED로 변경한다.
     * SUBSCRIPTION 주문의 경우 포인트 회수 없이 상태만 변경한다.</p>
     *
     * <h4>환불 가능 조건</h4>
     * <ul>
     *   <li>요청자가 주문 소유자여야 한다 (JWT에서 추출한 userId 기준 — BOLA 방지)</li>
     *   <li>주문 상태가 COMPLETED이어야 한다</li>
     *   <li>POINT_PACK인 경우 현재 포인트 잔액이 지급받은 포인트 이상이어야 한다</li>
     * </ul>
     *
     * <h4>멱등성</h4>
     * <p>이미 REFUNDED 상태인 주문은 200 OK와 함께 "이미 환불 처리된 주문입니다." 메시지를 반환한다.</p>
     *
     * @param orderId 환불할 주문 UUID (경로 변수)
     * @param request 환불 사유 ({@code reason} 필드, nullable)
     * @return 200 OK + RefundResponse (success, orderId, refundAmount, message)
     */
    @Operation(
            summary = "결제 환불",
            description = "COMPLETED 주문 환불. POINT_PACK은 포인트 회수 후 PG 취소. 멱등 처리 지원."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "환불 성공 (또는 이미 환불 완료된 멱등 응답)"),
            @ApiResponse(responseCode = "402", description = "포인트 잔액 부족으로 환불 불가"),
            @ApiResponse(responseCode = "404", description = "주문 미발견 또는 소유자 불일치"),
            @ApiResponse(responseCode = "400", description = "환불 불가 상태 (PENDING/FAILED/COMPENSATION_FAILED)")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/orders/{orderId}/refund")
    public ResponseEntity<RefundResponse> refundOrder(
            Principal principal,
            @PathVariable String orderId,
            @RequestBody(required = false) RefundRequest request) {
        // JWT에서 userId 추출 (소유자 검증용 — BOLA 방지)
        String userId = resolveUserId(principal);
        // request가 null이면 (body 없이 호출) reason을 null로 처리
        String reason = (request != null) ? request.reason() : null;

        log.info("환불 API 호출: userId={}, orderId={}, reason={}", userId, orderId, reason);

        RefundResponse response = paymentService.refundOrder(orderId, userId, reason);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // POST /api/v1/payment/webhook — Toss 웹훅
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 웹훅을 수신한다 (2026-04-24 재설계).
     *
     * <p>Toss Payments 공식 문서에 따르면 {@code PAYMENT_STATUS_CHANGED} 이벤트에는 HMAC 서명
     * 헤더나 별도 시크릿이 제공되지 않는다. 위변조 방어는 body 의 status 를 신뢰하는 대신 body 에서
     * {@code orderId} 만 추출해 Toss {@code getPayment} 로 재조회하는 방식으로 구현된다.
     * 상세 설계는 {@link com.monglepick.monglepickbackend.domain.payment.service.PaymentService#processWebhook} Javadoc 참조.</p>
     *
     * <p>내부 처리 실패 여부와 무관하게 항상 200 을 반환해 Toss 재시도 폭주를 방지한다.
     * 복구가 필요한 주문은 관리자 페이지의 "PG 재조회" 버튼으로 수동 처리할 수 있다.</p>
     *
     * @param rawBody Toss 웹훅 페이로드 원문
     * @return 200 OK (항상)
     */
    @Operation(summary = "Toss 웹훅", description = "Toss Payments 결제 상태 변경 웹훅 수신 — orderId 로 getPayment 재조회하여 DB 동기화")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "웹훅 수신 성공 (처리 결과와 무관)")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody String rawBody) {
        paymentService.processWebhook(rawBody);
        return ResponseEntity.ok().build();
    }

}
