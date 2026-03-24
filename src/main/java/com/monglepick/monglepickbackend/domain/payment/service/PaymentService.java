package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.client.TossPaymentsClient;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.CreateOrderRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.OrderHistoryResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.OrderResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 서비스 — Toss Payments 결제 주문 생성, 승인, 내역 조회 비즈니스 로직.
 *
 * <p>클라이언트(monglepick-client)의 결제 플로우를 처리하는 핵심 서비스이다.
 * 결제 플로우는 다음 3단계로 진행된다:</p>
 *
 * <h3>결제 플로우</h3>
 * <ol>
 *   <li><b>주문 생성</b> ({@link #createOrder}): 클라이언트 요청 → DB에 PENDING 주문 생성 → orderId + clientKey 반환</li>
 *   <li><b>결제창 표시</b>: 클라이언트가 Toss SDK로 결제창 호출 (orderId, clientKey 사용)</li>
 *   <li><b>결제 승인</b> ({@link #confirmPayment}): 클라이언트가 paymentKey 전달 → Toss API 승인 → 포인트 지급</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 기본 읽기 전용</li>
 *   <li>변경 메서드: 개별 {@code @Transactional} 오버라이드 — 쓰기 트랜잭션</li>
 *   <li>결제 승인 시 Toss API 호출과 DB 상태 변경이 하나의 트랜잭션에 포함됨</li>
 * </ul>
 *
 * <h3>멱등성 보장</h3>
 * <p>같은 orderId로 중복 승인을 시도하면 {@code DUPLICATE_ORDER} 에러가 반환된다.
 * PENDING이 아닌 주문에 대한 승인 시도를 차단하여 이중 결제를 방지한다.</p>
 *
 * @see TossPaymentsClient Toss Payments REST API 클라이언트
 * @see SubscriptionService 구독 관련 비즈니스 로직
 * @see PointService 포인트 지급 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    /** 결제 주문 리포지토리 */
    private final PaymentOrderRepository orderRepository;

    /** 구독 상품 리포지토리 (구독 결제 시 plan 조회) */
    private final SubscriptionPlanRepository planRepository;

    /** Toss Payments REST API 클라이언트 (결제 승인/취소) */
    private final TossPaymentsClient tossClient;

    /** 포인트 서비스 (결제 완료 후 포인트 지급) */
    private final PointService pointService;

    /** 구독 서비스 (구독 결제 완료 후 UserSubscription 생성) */
    private final SubscriptionService subscriptionService;

    /**
     * Toss Payments 클라이언트 키.
     * 클라이언트가 결제창을 열 때 필요한 키. 시크릿 키와 달리 노출 가능.
     * application.yml의 {@code toss.payments.client-key}에서 주입.
     */
    @Value("${toss.payments.client-key:test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq}")
    private String clientKey;

    // ──────────────────────────────────────────────
    // 주문 생성
    // ──────────────────────────────────────────────

    /**
     * 결제 주문을 생성한다 (status=PENDING).
     *
     * <p>UUID로 orderId를 생성하고 DB에 저장한 뒤,
     * orderId + clientKey를 반환한다.
     * 클라이언트는 이 값으로 Toss Payments 결제창을 호출한다.</p>
     *
     * <h4>주문 유형별 처리</h4>
     * <ul>
     *   <li>{@code POINT_PACK}: 요청의 pointsAmount를 그대로 사용</li>
     *   <li>{@code SUBSCRIPTION}: planCode로 구독 상품을 조회하여 plan FK 연결 + pointsPerPeriod 자동 설정</li>
     * </ul>
     *
     * @param request 주문 생성 요청 (userId, orderType, amount, pointsAmount?, planCode?)
     * @return 주문 응답 (orderId, amount, clientKey)
     * @throws BusinessException 구독 주문인데 planCode가 유효하지 않은 경우 (ORDER_NOT_FOUND)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("주문 생성 시작: userId={}, orderType={}, amount={}",
                request.userId(), request.orderType(), request.amount());

        // 1. UUID로 고유한 주문 ID 생성
        String orderId = UUID.randomUUID().toString();

        // 2. 주문 유형 파싱 (대소문자 무관)
        PaymentOrder.OrderType orderType = PaymentOrder.OrderType.valueOf(
                request.orderType().toUpperCase());

        // 3. PaymentOrder 빌더 구성
        PaymentOrder.PaymentOrderBuilder builder = PaymentOrder.builder()
                .orderId(orderId)
                .userId(request.userId())
                .orderType(orderType)
                .amount(request.amount())
                .pointsAmount(request.pointsAmount())
                .status(PaymentOrder.OrderStatus.PENDING);

        // 4. 구독인 경우 plan 조회 및 연결
        if (orderType == PaymentOrder.OrderType.SUBSCRIPTION && request.planCode() != null) {
            SubscriptionPlan plan = planRepository.findByPlanCode(request.planCode())
                    .orElseThrow(() -> {
                        log.error("구독 상품 조회 실패: planCode={}", request.planCode());
                        return new BusinessException(
                                ErrorCode.ORDER_NOT_FOUND,
                                "구독 상품을 찾을 수 없습니다: " + request.planCode()
                        );
                    });

            // plan FK 연결 + 지급 포인트를 상품 정보에서 가져옴
            builder.plan(plan).pointsAmount(plan.getPointsPerPeriod());
            log.debug("구독 상품 연결: planCode={}, pointsPerPeriod={}",
                    plan.getPlanCode(), plan.getPointsPerPeriod());
        }

        // 5. DB 저장
        orderRepository.save(builder.build());

        log.info("주문 생성 완료: orderId={}, userId={}, orderType={}, amount={}",
                orderId, request.userId(), orderType, request.amount());

        // 6. orderId + clientKey 반환 (클라이언트가 결제창에 사용)
        return new OrderResponse(orderId, request.amount(), clientKey);
    }

    // ──────────────────────────────────────────────
    // 결제 승인
    // ──────────────────────────────────────────────

    /**
     * 결제 승인 + 포인트 지급을 단일 트랜잭션으로 처리한다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>orderId로 주문 조회 (PENDING 상태 확인)</li>
     *   <li>결제 금액 일치 검증 (위변조 방지)</li>
     *   <li>Toss Payments 승인 API 호출</li>
     *   <li>PaymentOrder 상태 → COMPLETED (pgTransactionId, pgProvider 기록)</li>
     *   <li>포인트 지급 (pointService.earnPoint)</li>
     * </ol>
     *
     * <h4>멱등성</h4>
     * <p>PENDING이 아닌 주문에 대한 승인 요청은 {@code DUPLICATE_ORDER}로 거부된다.</p>
     *
     * <h4>실패 시</h4>
     * <p>Toss API 승인 실패 시 {@code PAYMENT_FAILED} 예외가 발생하고,
     * 트랜잭션 롤백으로 DB 변경이 모두 취소된다.</p>
     *
     * @param request 결제 승인 요청 (orderId, paymentKey, amount)
     * @return 승인 결과 (success, pointsGranted, newBalance)
     * @throws BusinessException 주문 미발견(ORDER_NOT_FOUND), 중복 처리(DUPLICATE_ORDER),
     *                           금액 불일치(PAYMENT_FAILED), PG 승인 실패(PAYMENT_FAILED)
     */
    @Transactional
    public ConfirmResponse confirmPayment(ConfirmRequest request) {
        log.info("결제 승인 시작: orderId={}, amount={}", request.orderId(), request.amount());

        // 1. 주문 조회
        PaymentOrder order = orderRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> {
                    log.error("주문 조회 실패: orderId={}", request.orderId());
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        // 2. 이미 처리된 주문 방지 (멱등성 보장)
        if (order.getStatus() != PaymentOrder.OrderStatus.PENDING) {
            log.warn("중복 결제 시도 차단: orderId={}, currentStatus={}",
                    request.orderId(), order.getStatus());
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }

        // 3. 금액 일치 검증 (위변조 방지)
        if (order.getAmount() != request.amount()) {
            log.error("결제 금액 불일치: orderId={}, 주문금액={}, 요청금액={}",
                    request.orderId(), order.getAmount(), request.amount());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 금액이 일치하지 않습니다. 주문: " + order.getAmount() + "원, 요청: " + request.amount() + "원"
            );
        }

        // 4. Toss Payments 결제 승인 API 호출
        TossPaymentsClient.TossConfirmResponse tossResponse =
                tossClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());

        // 5. 주문 완료 처리 (도메인 메서드: status=COMPLETED, pgTransactionId, pgProvider, completedAt 기록)
        order.complete(request.paymentKey(), "TOSS");
        log.info("주문 상태 변경: orderId={}, PENDING → COMPLETED", request.orderId());

        // 6. 포인트 지급
        int pointsToGrant = order.getPointsAmount() != null ? order.getPointsAmount() : 0;
        String description = order.getOrderType() == PaymentOrder.OrderType.SUBSCRIPTION
                ? "구독 포인트 지급" : "포인트팩 구매";

        PointDto.EarnResponse earnResult = null;
        if (pointsToGrant > 0) {
            earnResult = pointService.earnPoint(
                    order.getUserId(),
                    pointsToGrant,
                    "earn",
                    description,
                    order.getOrderId()
            );
            log.info("포인트 지급 완료: userId={}, points={}, newBalance={}",
                    order.getUserId(), pointsToGrant,
                    earnResult != null ? earnResult.balanceAfter() : "N/A");
        }

        // 7. 구독 결제인 경우 UserSubscription 생성
        if (order.getOrderType() == PaymentOrder.OrderType.SUBSCRIPTION && order.getPlan() != null) {
            subscriptionService.createSubscription(order.getUserId(), order.getPlan());
            log.info("구독 활성화: userId={}, plan={}", order.getUserId(), order.getPlan().getPlanCode());
        }

        // 8. 응답 반환
        int newBalance = earnResult != null ? earnResult.balanceAfter() : 0;

        log.info("결제 승인 완료: orderId={}, pointsGranted={}, newBalance={}",
                request.orderId(), pointsToGrant, newBalance);

        return new ConfirmResponse(true, pointsToGrant, newBalance);
    }

    // ──────────────────────────────────────────────
    // 결제 내역 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자의 결제 주문 내역을 페이징으로 조회한다.
     *
     * <p>모든 상태(PENDING, COMPLETED, FAILED, REFUNDED)의 주문이 포함되며,
     * 생성 시각 기준 최신순으로 정렬된다.
     * 클라이언트의 "결제 내역" 화면에서 사용된다.</p>
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 정보 (page, size)
     * @return 결제 주문 내역 페이지
     */
    public Page<OrderHistoryResponse> getOrderHistory(String userId, Pageable pageable) {
        log.debug("결제 내역 조회: userId={}, page={}, size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toHistoryResponse);
    }

    // ──────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────

    /**
     * PaymentOrder 엔티티를 OrderHistoryResponse DTO로 변환한다.
     *
     * @param order 결제 주문 엔티티
     * @return 결제 내역 응답 DTO
     */
    private OrderHistoryResponse toHistoryResponse(PaymentOrder order) {
        return new OrderHistoryResponse(
                order.getOrderId(),
                order.getOrderType().name(),
                order.getAmount(),
                order.getPointsAmount(),
                order.getStatus().name(),
                order.getPgProvider(),
                order.getCompletedAt(),
                order.getCreatedAt()
        );
    }
}
