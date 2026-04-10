package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 승인 Phase 2 전담 프로세서 — DB 상태 변경 + 포인트 지급 + 구독 활성화.
 *
 * <h3>왜 별도 Bean으로 분리했는가?</h3>
 * <p>Spring의 {@code @Transactional}은 AOP 프록시 기반으로 동작한다.
 * 같은 클래스 내부에서 {@code this.method()}로 호출하면 프록시를 경유하지 않아
 * {@code @Transactional} 어노테이션이 무시된다 (self-invocation 문제).</p>
 *
 * <p>{@link PaymentService#confirmPayment}는 Phase 1(트랜잭션 없음)에서 Toss API를 호출한 후
 * 이 클래스의 {@link #execute}를 호출한다. 별도 Bean이므로 프록시를 경유하여
 * {@code @Transactional}이 정상 동작한다.</p>
 *
 * <p>같은 이유로 {@link PaymentCompensationService}도 별도 Bean으로 분리되어 있다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>{@code @Transactional} — 읽기/쓰기 트랜잭션 (readOnly=false)</li>
 *   <li>FOR UPDATE 비관적 잠금으로 동시 처리 방지</li>
 *   <li>실패 시 전체 롤백 → 호출자(PaymentService)가 보상 패턴 수행</li>
 * </ul>
 *
 * @see PaymentService#confirmPayment 호출자 (Phase 1)
 * @see PaymentCompensationService 보상 실패 기록 (동일한 분리 패턴)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentConfirmProcessor {

    /** 결제 주문 리포지토리 — FOR UPDATE 재조회용 */
    private final PaymentOrderRepository orderRepository;

    /** 포인트 서비스 — 결제 완료 후 포인트 지급 */
    private final PointService pointService;

    /** 구독 서비스 — 구독 결제 완료 후 UserSubscription 생성 */
    private final SubscriptionService subscriptionService;

    /**
     * 결제 승인 Phase 2를 실행한다: FOR UPDATE 재조회 → 멱등 확인 → DB 상태 변경 + 포인트 지급 + 구독 활성화.
     *
     * <h3>호출 조건</h3>
     * <p>반드시 Toss Payments 결제 승인 API 호출이 성공한 직후에만 호출한다.
     * 이 메서드 진입 시점에 Toss에서 이미 결제가 승인된 상태이므로,
     * DB 처리 실패 시 호출자(PaymentService)가 반드시 보상(Toss 환불)을 수행해야 한다.</p>
     *
     * <h3>FOR UPDATE 재조회 (멱등성 + 동시성 방어)</h3>
     * <p>Phase 1과 Phase 2 사이에 동일 orderId로 중복 요청이 도달할 수 있다.
     * FOR UPDATE 잠금으로 첫 번째 처리가 완료될 때까지 후속 요청을 대기시킨다.
     * 대기 후 진입한 요청은 COMPLETED 상태를 보고 멱등 응답을 반환한다.</p>
     *
     * @param userId  JWT에서 추출한 사용자 ID
     * @param request 결제 승인 요청 (orderId, paymentKey, amount)
     * @return 승인 결과 (success, pointsGranted, newBalance)
     * @throws BusinessException 주문 미발견, 중복 처리, 포인트 지급/구독 생성 실패 시
     */
    @Transactional
    public ConfirmResponse execute(String userId, ConfirmRequest request) {
        log.info("[Phase 2 시작] DB 처리: userId={}, orderId={}", userId, request.orderId());

        // ── Phase 2-1: FOR UPDATE 비관적 잠금으로 주문 재조회 ──
        // Phase 1의 사전 검증과 이 시점 사이에 동일 orderId로 중복 요청이 도달했을 수 있다.
        // FOR UPDATE는 첫 번째 트랜잭션이 커밋/롤백할 때까지 후속 요청을 DB 레벨에서 대기시킨다.
        PaymentOrder order = orderRepository.findByPaymentOrderIdForUpdate(request.orderId())
                .orElseThrow(() -> {
                    // Phase 1에서 조회됐으나 여기서 없다면 비정상 상황 — 운영 확인 필요
                    log.error("[Phase 2] FOR UPDATE 주문 재조회 실패 (비정상): orderId={}", request.orderId());
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        // ── Phase 2-2: COMPLETED 상태 멱등 응답 (새로고침/중복 탭 방어) ──
        // FOR UPDATE 대기 후 진입한 요청이 이미 COMPLETED 상태를 만나는 케이스:
        //   1. 사용자가 결제 완료 후 브라우저를 새로고침
        //   2. 여러 탭에서 동시에 결제 완료 요청
        // → 중복 포인트 지급 없이 기존 완료 결과를 그대로 반환한다.
        if (order.getStatus() == PaymentOrder.OrderStatus.COMPLETED) {
            log.info("[Phase 2] 이미 완료된 주문 — 멱등 응답 반환: orderId={}", request.orderId());
            // 완료된 주문의 pointsAmount를 그대로 반환 (이미 지급된 포인트 수량)
            int alreadyGranted = order.getPointsAmount() != null ? order.getPointsAmount() : 0;
            return new ConfirmResponse(true, alreadyGranted, 0);
        }

        // ── Phase 2-3: PENDING이 아닌 다른 상태 (FAILED/REFUNDED/COMPENSATION_FAILED) ──
        // Toss API 승인은 성공했는데 DB가 이미 FAILED 상태라면 비정상 상황이다.
        // 중복 처리를 방지하기 위해 DUPLICATE_ORDER로 거부한다.
        if (order.getStatus() != PaymentOrder.OrderStatus.PENDING) {
            log.warn("[Phase 2] 처리 불가 상태 — 승인 거부: orderId={}, status={}",
                    request.orderId(), order.getStatus());
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }

        // ── Phase 2-4. DB 상태 변경 + 포인트 지급 + 구독 활성화 ──
        // 이 블록 전체가 하나의 트랜잭션으로 묶인다.
        //   - 포인트 지급 실패 → order.complete()도 롤백 → 일관성 보장
        //   - 구독 생성 실패 → 포인트 지급 + 주문 완료 모두 롤백 → 이중 지급 방지

        // 4-1. 주문 COMPLETED 처리 (pgTransactionId="paymentKey", pgProvider="TOSS", completedAt 기록)
        order.complete(request.paymentKey(), "TOSS");
        log.info("[Phase 2] 주문 상태 변경: orderId={}, PENDING → COMPLETED", order.getPaymentOrderId());

        // 4-2. 포인트 지급
        // earnPoint()는 @Transactional(REQUIRED)이므로 현재 Phase 2 트랜잭션에 합류한다.
        // 실패 시 예외가 전파되어 Phase 2 전체 롤백 → order.complete() 취소됨.
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
                    order.getPaymentOrderId()
            );
            log.info("[Phase 2] 포인트 지급 완료: userId={}, points={}, newBalance={}",
                    order.getUserId(), pointsToGrant,
                    earnResult != null ? earnResult.balanceAfter() : "N/A");
        }

        // 4-3. 구독 결제인 경우 UserSubscription 생성
        // 실패 시 예외 전파 → Phase 2 전체 롤백 (포인트 지급 + 주문 완료 모두 취소)
        if (order.getOrderType() == PaymentOrder.OrderType.SUBSCRIPTION && order.getPlan() != null) {
            subscriptionService.createSubscription(order.getUserId(), order.getPlan());
            log.info("[Phase 2] 구독 활성화: userId={}, plan={}",
                    order.getUserId(), order.getPlan().getPlanCode());
        }

        int newBalance = earnResult != null ? earnResult.balanceAfter() : 0;
        log.info("[Phase 2 완료] 결제 승인 처리 성공: orderId={}, pointsGranted={}, newBalance={}",
                order.getPaymentOrderId(), pointsToGrant, newBalance);

        return new ConfirmResponse(true, pointsToGrant, newBalance);
    }
}
