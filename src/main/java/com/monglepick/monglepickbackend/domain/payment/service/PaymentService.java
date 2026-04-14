package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.client.TossPaymentsClient;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ConfirmResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.CreateOrderRequest;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.OrderHistoryResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.OrderResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.RefundResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.entity.PointPackPrice;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.PointPackPriceRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
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
 *   <li>결제 승인({@link #confirmPayment})은 <b>2-Phase 분리</b>:
 *     <ol>
 *       <li>Phase 1 ({@code confirmPayment}, 트랜잭션 없음):
 *           주문 사전 검증 + Toss API 호출. DB 커넥션 미점유.
 *           동시 결제 N건 시 HikariCP 풀 고갈 방지 (설계서 §8.4).</li>
 *       <li>Phase 2 ({@link #processConfirmedPayment}, {@code @Transactional}):
 *           FOR UPDATE 재조회 → 멱등 확인 → order.complete() + earnPoint() + createSubscription()
 *           원자적 처리. 실패 시 보상(Toss 환불) 패턴 수행.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h3>멱등성 보장</h3>
 * <p>Phase 1에서 PENDING이 아닌 주문은 즉시 {@code DUPLICATE_ORDER}로 거부한다.
 * Phase 2에서 FOR UPDATE 재조회 후 COMPLETED 상태이면 기존 결과를 반환한다 (새로고침 방어).</p>
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

    /** 포인트팩 가격 마스터 리포지토리 (포인트팩 결제 시 가격 검증 — 설계서 v2.3 §13.1 보안 필수) */
    private final PointPackPriceRepository pointPackPriceRepository;

    /** Toss Payments REST API 클라이언트 (결제 승인/취소) */
    private final TossPaymentsClient tossClient;

    /** 포인트 서비스 (결제 완료 후 포인트 지급) */
    private final PointService pointService;

    /** 구독 서비스 (구독 결제 완료 후 UserSubscription 생성) */
    private final SubscriptionService subscriptionService;

    /**
     * 결제 보상 트랜잭션 서비스.
     *
     * <p>Toss 결제 승인 후 DB 저장 실패 + PG 환불도 실패한 경우,
     * COMPENSATION_FAILED 상태를 <b>독립 트랜잭션(REQUIRES_NEW)</b>으로 저장한다.</p>
     *
     * <p>같은 클래스({@code PaymentService}) 내 메서드로 구현하면 Spring AOP 프록시를
     * 경유하지 않아 {@code @Transactional(REQUIRES_NEW)}가 무시된다. 따라서 별도 Bean으로
     * 분리하여 프록시를 통한 트랜잭션 제어가 동작하도록 한다.</p>
     *
     * @see PaymentCompensationService
     */
    private final PaymentCompensationService compensationService;

    /**
     * 결제 승인 Phase 2 전담 프로세서.
     *
     * <p>Phase 2(DB 상태 변경 + 포인트 지급 + 구독 활성화)를 별도 Bean으로 분리하여
     * Spring AOP 프록시를 통한 {@code @Transactional} 적용을 보장한다.</p>
     *
     * <p>같은 클래스 내 메서드 호출(self-invocation)은 프록시를 경유하지 않아
     * {@code @Transactional}이 무시되는 문제가 있었다. 또한 클래스 레벨
     * {@code @Transactional(readOnly=true)}가 confirmPayment()에도 적용되어
     * Phase 1에서 DB 커넥션이 점유되는 설계 의도 위반이 발생했다.</p>
     *
     * <p>별도 Bean 분리로 두 가지 문제를 모두 해결한다:</p>
     * <ol>
     *   <li>Phase 2의 {@code @Transactional} 정상 동작 (readOnly=false)</li>
     *   <li>Phase 1의 트랜잭션 없음 보장 (DB 커넥션 미점유)</li>
     * </ol>
     *
     * @see PaymentConfirmProcessor
     */
    private final PaymentConfirmProcessor confirmProcessor;

    /**
     * Toss Payments 클라이언트 키.
     * 클라이언트가 결제창을 열 때 필요한 키. 시크릿 키와 달리 노출 가능.
     * application.yml의 {@code toss.payments.client-key}에서 주입.
     */
    @Value("${toss.payments.client-key}")
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
    /**
     * 결제 주문을 생성한다 (status=PENDING).
     *
     * <p>멱등키(idempotencyKey)가 제공된 경우:
     * <ul>
     *   <li>동일 키로 기존 주문이 존재하면 기존 주문 응답을 반환 (중복 생성 방지)</li>
     *   <li>기존 주문의 userId/amount가 다르면 IDEMPOTENCY_KEY_REUSE 에러</li>
     * </ul></p>
     *
     * @param request        주문 생성 요청
     * @param idempotencyKey 멱등키 (Idempotency-Key 헤더, nullable)
     * @return 주문 응답 (orderId, amount, clientKey)
     */
    @Transactional
    public OrderResponse createOrder(String userId, CreateOrderRequest request, String idempotencyKey) {
        log.info("주문 생성 시작: userId={}, orderType={}, amount={}, idempotencyKey={}",
                userId, request.orderType(), request.amount(), idempotencyKey);

        // 0. 멱등키가 있으면 기존 주문 확인
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                PaymentOrder existingOrder = existing.get();
                // 동일 멱등키인데 요청 내용이 다르면 에러
                if (!existingOrder.getUserId().equals(userId)
                        || !existingOrder.getAmount().equals(request.amount())) {
                    log.warn("멱등키 재사용 감지: idempotencyKey={}, 기존userId={}, 요청userId={}",
                            idempotencyKey, existingOrder.getUserId(), userId);
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSE);
                }
                // 동일 요청 → 기존 주문 응답 반환
                log.info("멱등키로 기존 주문 반환: orderId={}", existingOrder.getPaymentOrderId());
                return new OrderResponse(existingOrder.getPaymentOrderId(), existingOrder.getAmount(), clientKey);
            }
        }

        // 1. UUID로 고유한 주문 ID 생성
        String orderId = UUID.randomUUID().toString();

        // 2. 주문 유형 파싱 (대소문자 무관)
        PaymentOrder.OrderType orderType = PaymentOrder.OrderType.valueOf(
                request.orderType().toUpperCase());

        // 3. PaymentOrder 빌더 구성 — JWT에서 추출한 userId 사용 (BOLA 방지)
        PaymentOrder.PaymentOrderBuilder builder = PaymentOrder.builder()
                .paymentOrderId(orderId)
                .userId(userId)
                .orderType(orderType)
                .amount(request.amount())
                .pointsAmount(request.pointsAmount())
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
                .status(PaymentOrder.OrderStatus.PENDING);

        // 3-1. ★ 포인트팩인 경우 가격 마스터 검증 (설계서 v3.2 §13.1 — 보안 필수)
        //     클라이언트가 보낸 (amount=결제금액, pointsAmount)가 서버 가격표와 정확히 일치하는지 검증.
        //     미검증 시 {price:1000, pointsAmount:999999} 공격으로 무제한 포인트 획득 가능.
        //     v3.2: PointPackPrice 컬럼명 amount→price 변경에 따라 리포지토리 메서드명도 변경.
        if (orderType == PaymentOrder.OrderType.POINT_PACK) {
            PointPackPrice validPack = pointPackPriceRepository
                    .findByPriceAndPointsAmountAndIsActiveTrue(request.amount(), request.pointsAmount())
                    .orElseThrow(() -> {
                        log.error("포인트팩 가격 검증 실패 (변조 의심): amount={}, pointsAmount={}",
                                request.amount(), request.pointsAmount());
                        return new BusinessException(
                                ErrorCode.PAYMENT_FAILED,
                                "유효하지 않은 포인트팩입니다. 결제 금액: " + request.amount()
                                        + "원, 포인트: " + request.pointsAmount() + "P"
                        );
                    });
            // v3.2: getAmount() → getPrice() (엔티티 필드명 변경 반영)
            log.debug("포인트팩 가격 검증 통과: packName={}, price={}, points={}",
                    validPack.getPackName(), validPack.getPrice(), validPack.getPointsAmount());
        }

        // 4. 구독인 경우 plan 조회 + 금액 검증 + 연결
        if (orderType == PaymentOrder.OrderType.SUBSCRIPTION && request.planCode() != null) {
            SubscriptionPlan plan = planRepository.findByPlanCode(request.planCode())
                    .orElseThrow(() -> {
                        log.error("구독 상품 조회 실패: planCode={}", request.planCode());
                        return new BusinessException(
                                ErrorCode.ORDER_NOT_FOUND,
                                "구독 상품을 찾을 수 없습니다: " + request.planCode()
                        );
                    });

            // 금액 변조 방지: 요청 금액이 상품 가격과 일치하는지 검증
            if (!plan.getPrice().equals(request.amount())) {
                log.error("결제 금액 변조 감지: planCode={}, 상품가격={}, 요청금액={}",
                        request.planCode(), plan.getPrice(), request.amount());
                throw new BusinessException(
                        ErrorCode.PAYMENT_FAILED,
                        "결제 금액이 상품 가격과 일치하지 않습니다. 상품: " + plan.getPrice() + "원, 요청: " + request.amount() + "원"
                );
            }

            // plan FK 연결 + 지급 포인트를 상품 정보에서 가져옴
            builder.plan(plan).pointsAmount(plan.getPointsPerPeriod());
            log.debug("구독 상품 연결: planCode={}, pointsPerPeriod={}",
                    plan.getPlanCode(), plan.getPointsPerPeriod());
        }

        // 5. DB 저장 — UNIQUE 제약 위반(멱등키 동시 요청) 시 기존 주문 반환으로 처리
        try {
            orderRepository.save(builder.build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 2건이 모두 findByIdempotencyKey()에서 "없음"을 읽고 INSERT를 시도한 경우.
            // UNIQUE(idempotency_key) 제약 위반이 발생하면 이미 저장된 주문을 반환한다.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                log.info("멱등키 UNIQUE 제약 위반 — 기존 주문 조회 후 반환: idempotencyKey={}", idempotencyKey);
                return orderRepository.findByIdempotencyKey(idempotencyKey)
                        .map(existing -> new OrderResponse(
                                existing.getPaymentOrderId(), existing.getAmount(), clientKey))
                        .orElseThrow(() -> {
                            log.error("멱등키 UNIQUE 위반이지만 기존 주문 조회 실패 (비정상): key={}", idempotencyKey);
                            return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                        });
            }
            throw e; // 멱등키 없는 경우 원본 예외 전파
        }

        log.info("주문 생성 완료: orderId={}, userId={}, orderType={}, amount={}",
                orderId, userId, orderType, request.amount());

        // 6. orderId + clientKey 반환 (클라이언트가 결제창에 사용)
        return new OrderResponse(orderId, request.amount(), clientKey);
    }

    // ──────────────────────────────────────────────
    // 결제 승인 (2-Phase 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 결제 승인 + 포인트 지급을 2-Phase 트랜잭션으로 처리한다.
     *
     * <h3>왜 2-Phase로 분리하는가?</h3>
     * <p>기존 구조에서는 Toss API 호출(외부 HTTP, 500ms~2s)이 {@code @Transactional} 범위 안에
     * 포함되어 있었다. 동시 결제 10건 시 Toss API 응답을 기다리는 동안 DB 커넥션 10개가
     * HikariCP 풀에서 점유 상태로 유지되어 풀이 고갈되는 문제가 발생한다 (설계서 §8.4).</p>
     *
     * <h3>Phase 1 — 트랜잭션 없음 (이 메서드)</h3>
     * <ol>
     *   <li>주문 조회 + 소유자 검증 + PENDING 상태 확인 + 금액 검증 (읽기, 커넥션 즉시 반납)</li>
     *   <li>Toss Payments 결제 승인 API 호출 → paymentKey 획득 (외부 I/O, DB 커넥션 미점유)</li>
     *   <li>Toss API 실패 시: order.fail() 없이 예외를 던져 PENDING 상태 유지
     *       (Toss에서 아직 승인하지 않았으므로 DB 변경 불필요)</li>
     * </ol>
     *
     * <h3>Phase 2 — {@code @Transactional} ({@link #processConfirmedPayment})</h3>
     * <ol>
     *   <li>주문 재조회 FOR UPDATE (비관적 잠금 — TOCTOU 동시 처리 차단)</li>
     *   <li>COMPLETED 상태이면 멱등 응답 반환 (새로고침/중복 탭 방어)</li>
     *   <li>order.complete() + earnPoint() + createSubscription() 원자적 처리</li>
     *   <li>예외 시 Phase 2 전체 롤백 + Toss 보상 환불 + COMPENSATION_FAILED 기록</li>
     * </ol>
     *
     * <h3>멱등성</h3>
     * <ul>
     *   <li>Phase 1: PENDING이 아니면 즉시 DUPLICATE_ORDER 거부</li>
     *   <li>Phase 2: FOR UPDATE 재조회 후 COMPLETED이면 기존 결과 반환 (새로고침 방어)</li>
     * </ul>
     *
     * @param userId  JWT에서 추출한 현재 사용자 ID
     * @param request 결제 승인 요청 (orderId, paymentKey, amount)
     * @return 승인 결과 (success, pointsGranted, newBalance)
     * @throws BusinessException 주문 미발견(ORDER_NOT_FOUND), 중복 처리(DUPLICATE_ORDER),
     *                           금액 불일치(PAYMENT_FAILED), PG 승인 실패(PAYMENT_FAILED)
     */
    // ★ 트랜잭션 의도적 미적용: 이 메서드는 트랜잭션을 열지 않는다.
    //   클래스 레벨 @Transactional(readOnly=true)가 적용되는 것을 방지하기 위해
    //   NON_TRANSACTIONAL 전략을 사용한다. (설계서 §8.4: DB 커넥션 미점유)
    //
    //   Phase 2(DB 쓰기)는 별도 Bean인 PaymentConfirmProcessor에서 수행되므로
    //   self-invocation 문제 없이 @Transactional이 정상 동작한다.
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ConfirmResponse confirmPayment(String userId, ConfirmRequest request) {
        log.info("[Phase 1 시작] 결제 승인: userId={}, orderId={}, amount={}",
                userId, request.orderId(), request.amount());

        // ════════════════════════════════════════════
        // Phase 1-1: 주문 사전 검증 (트랜잭션 없음 — 읽기 전용 단순 조회)
        //
        // 이 시점의 조회는 "Toss API를 호출해도 되는지"를 빠르게 판단하기 위한 것이다.
        // 트랜잭션 없이 실행되어 커넥션을 즉시 반납한다.
        // 최종 정합성 보장은 Phase 2의 FOR UPDATE 재조회에서 수행한다.
        // ════════════════════════════════════════════

        // 1-1. 주문 조회 (잠금 없는 단순 조회 — Phase 1용)
        PaymentOrder preCheckOrder = orderRepository.findByPaymentOrderId(request.orderId())
                .orElseThrow(() -> {
                    log.error("[Phase 1] 주문 조회 실패: orderId={}", request.orderId());
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        // 1-2. 주문 소유자 검증 (BOLA 방지 — 타인의 주문을 승인할 수 없음)
        if (!preCheckOrder.getUserId().equals(userId)) {
            log.error("[Phase 1] 주문 소유자 불일치: orderId={}, 주문소유자={}, 요청자={}",
                    request.orderId(), preCheckOrder.getUserId(), userId);
            // 소유자 불일치는 ORDER_NOT_FOUND로 응답 (존재 여부 노출 방지)
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 1-3. PENDING 상태 사전 확인 (명백한 중복 요청은 Toss API 호출 전에 차단)
        // COMPLETED/FAILED/REFUNDED/COMPENSATION_FAILED인 경우 즉시 거부한다.
        // 단, 이 검증은 "빠른 실패(fail-fast)"용이며, 경쟁 조건의 최종 방어는
        // Phase 2의 FOR UPDATE 재조회가 담당한다.
        if (preCheckOrder.getStatus() != PaymentOrder.OrderStatus.PENDING) {
            log.warn("[Phase 1] 중복 결제 시도 사전 차단: orderId={}, currentStatus={}",
                    request.orderId(), preCheckOrder.getStatus());
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }

        // 1-4. 금액 일치 검증 (클라이언트 위변조 방지)
        // Integer 박싱 타입은 == 대신 equals() 사용 필수 (값 128 초과 시 == 은 false)
        if (!preCheckOrder.getAmount().equals(request.amount())) {
            log.error("[Phase 1] 결제 금액 불일치: orderId={}, 주문금액={}, 요청금액={}",
                    request.orderId(), preCheckOrder.getAmount(), request.amount());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 금액이 일치하지 않습니다. 주문: " + preCheckOrder.getAmount()
                            + "원, 요청: " + request.amount() + "원"
            );
        }

        // ════════════════════════════════════════════
        // Phase 1-2: Toss Payments 결제 승인 API 호출
        //
        // DB 커넥션을 점유하지 않은 상태에서 외부 HTTP 요청을 수행한다.
        // HikariCP 커넥션 풀 고갈 문제의 근본 원인을 여기서 해결한다.
        //
        // 실패 케이스:
        //   - 네트워크 오류 / Toss 4xx/5xx → BusinessException(PAYMENT_FAILED) 발생
        //   - Toss에서 아직 승인되지 않았으므로 DB 변경 불필요
        //   - PENDING 상태 그대로 유지되어 재시도 가능
        // ════════════════════════════════════════════
        log.info("[Phase 1] Toss API 승인 요청: orderId={}, amount={}", request.orderId(), request.amount());
        tossClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());
        // 이 줄에 도달했다면 Toss API 승인 성공.
        // 이제 반드시 DB에 COMPLETED를 기록해야 한다. Phase 2로 진입한다.
        log.info("[Phase 1 완료] Toss API 승인 성공 → Phase 2 진입: orderId={}", request.orderId());

        // ════════════════════════════════════════════
        // Phase 2: DB 상태 변경 + 포인트 지급 + 구독 활성화 (@Transactional)
        //
        // Toss API 성공 이후 DB 처리 전체를 하나의 트랜잭션으로 묶는다.
        // 실패 시 Phase 2 전체가 롤백되고, 보상 패턴(Toss 환불)이 트리거된다.
        //
        // ★ 별도 Bean(PaymentConfirmProcessor)을 통해 호출하여 Spring AOP 프록시가
        //   @Transactional을 정상 적용하도록 한다. (self-invocation 문제 해결)
        // ════════════════════════════════════════════
        try {
            return confirmProcessor.execute(userId, request);
        } catch (Exception e) {
            // ════════════════════════════════════════════
            // 보상(Compensation) 처리
            //
            // Toss 결제는 성공(Phase 1)했으나 DB 처리(Phase 2)가 실패한 상황.
            // Phase 2 트랜잭션은 이미 롤백된 상태이다.
            //
            // 사용자 관점: 카드는 청구됐으나 포인트/구독이 지급되지 않은 위험 상태.
            // 대응: Toss 환불 시도 → 실패 시 COMPENSATION_FAILED 기록 → 운영팀 수동 조치.
            // ════════════════════════════════════════════
            log.error("[Phase 2][C-B3] DB 처리 실패 — Toss 결제 보상 취소 시도: orderId={}, error={}",
                    request.orderId(), e.getMessage(), e);

            // 보상 취소 재시도 (최대 MAX_CANCEL_RETRIES회)
            boolean cancelSuccess = attemptCancelWithRetry(request.paymentKey(), request.orderId());

            if (!cancelSuccess) {
                // ── CRITICAL: Toss 환불도 실패 → COMPENSATION_FAILED 상태 저장 ──
                //
                // compensationService.recordCompensationFailed()는
                //   @Transactional(REQUIRES_NEW)가 적용된 별도 Spring Bean 메서드다.
                //   Phase 2 트랜잭션이 이미 롤백되었어도 독립 트랜잭션으로 커밋이 보장된다.
                log.error("[CRITICAL][Phase 2][C-B3] Toss 보상 취소 {}회 실패 — " +
                                "COMPENSATION_FAILED 기록 및 수동 조치 필요. " +
                                "orderId={}, paymentKey={}, userId={}, amount={}",
                        MAX_CANCEL_RETRIES,
                        request.orderId(),
                        request.paymentKey(),
                        userId,
                        request.amount());

                // FOR UPDATE 없이 단순 조회 후 COMPENSATION_FAILED 기록
                // (Phase 2 트랜잭션 롤백으로 order 엔티티는 detached 상태)
                orderRepository.findByPaymentOrderId(request.orderId()).ifPresent(detachedOrder -> {
                    String compensationReason = "보상 취소 " + MAX_CANCEL_RETRIES + "회 실패: "
                            + e.getMessage();
                    compensationService.recordCompensationFailed(detachedOrder, compensationReason);
                });
            }

            // 원본 예외를 그대로 전파하여 클라이언트가 결제 실패를 인지하도록 함
            throw e;
        }
    }

    // ──────────────────────────────────────────────
    // 결제 내역 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자용 "결제 내역" 화면에 노출할 상태 집합.
     *
     * <p>PG 표준 플로우에 따라 {@code createOrder} 호출 시점에 PENDING 레코드가 즉시
     * DB 에 저장되고, 결제 창에서 이탈/실패한 주문은 스케줄러가 FAILED 로 전환한다.
     * 하지만 사용자 관점에서는 "결제 시도/이탈/실패"는 노이즈이므로
     * 실제 금전 이동이 발생한 COMPLETED / REFUNDED 만 노출한다.</p>
     *
     * <p>PENDING / FAILED / COMPENSATION_FAILED 는 관리자 화면 전용이며, 관리자
     * 리포지토리 경로({@link PaymentOrderRepository#findByUserIdOrderByCreatedAtDesc})
     * 를 통해 모든 상태가 그대로 보인다.</p>
     */
    private static final List<PaymentOrder.OrderStatus> USER_VISIBLE_ORDER_STATUSES =
            List.of(PaymentOrder.OrderStatus.COMPLETED, PaymentOrder.OrderStatus.REFUNDED);

    /**
     * 사용자의 결제 주문 내역을 페이징으로 조회한다.
     *
     * <p>사용자용 마이페이지 "결제 내역" 전용 — 실제 결제가 완료된 건(COMPLETED)과
     * 환불된 건(REFUNDED) 만 반환한다. 결제 시도 후 이탈해 PENDING/FAILED 상태로
     * 남은 주문은 DB 에는 유지되지만 이 API 결과에서는 제외된다.</p>
     *
     * <p>관리자 화면에서는 별도의 관리자 전용 리포지토리 경로를 사용해 모든 상태를
     * 조회한다(감사/전환율 분석 목적). 본 메서드는 사용자 화면만 담당한다.</p>
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 정보 (page, size)
     * @return 결제 주문 내역 페이지 (COMPLETED/REFUNDED 만)
     */
    public Page<OrderHistoryResponse> getOrderHistory(String userId, Pageable pageable) {
        log.debug("결제 내역 조회(사용자용, COMPLETED/REFUNDED): userId={}, page={}, size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return orderRepository
                .findByUserIdAndStatusInOrderByCreatedAtDesc(userId, USER_VISIBLE_ORDER_STATUSES, pageable)
                .map(this::toHistoryResponse);
    }

    // ──────────────────────────────────────────────
    // 웹훅 처리
    // ──────────────────────────────────────────────

    /**
     * Toss 웹훅 서명을 검증하고 이벤트를 처리한다.
     *
     * <p>서명 검증 실패 시 INVALID_WEBHOOK_SIGNATURE 에러를 던진다.
     * 검증 통과 후 이벤트 로깅을 수행한다 (향후 결제 확인 자동화 확장).</p>
     *
     * @param rawBody   웹훅 요청 Body 원문
     * @param signature TossPayments-Signature 헤더 값
     * @throws BusinessException 서명 검증 실패 시 (INVALID_WEBHOOK_SIGNATURE)
     */
    private static final ObjectMapper WEBHOOK_MAPPER = new ObjectMapper();

    @Transactional
    public void verifyAndProcessWebhook(String rawBody, String signature) {
        /* 1. 서명 검증 */
        if (!tossClient.verifyWebhookSignature(rawBody, signature)) {
            log.error("Toss 웹훅 서명 검증 실패");
            throw new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        log.info("Toss 웹훅 수신 (서명 검증 통과)");

        /* 2. 이벤트 파싱 및 처리 */
        try {
            JsonNode root = WEBHOOK_MAPPER.readTree(rawBody);
            String eventType = root.path("eventType").asText("");
            JsonNode data = root.path("data");

            switch (eventType) {
                case "PAYMENT_STATUS_CHANGED" -> {
                    String orderId = data.path("orderId").asText(null);
                    String status = data.path("status").asText("");

                    if (orderId == null) {
                        log.warn("웹훅 orderId 누락: eventType={}", eventType);
                        return;
                    }

                    /* 취소/환불 처리 */
                    if ("CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status)) {
                        orderRepository.findByPaymentOrderId(orderId).ifPresent(order -> {

                            // ── 멱등성 방어: 이미 REFUNDED 상태이면 웹훅 재수신으로 판단하고 무시 ──
                            // Toss 웹훅은 네트워크 오류 등으로 동일 이벤트를 2회 이상 전송할 수 있다.
                            // 이미 REFUNDED 처리된 주문에 대해 포인트 회수를 중복 실행하면
                            // 사용자 잔액이 부당하게 추가 차감되므로 반드시 상태를 먼저 확인한다.
                            if (order.getStatus() == PaymentOrder.OrderStatus.REFUNDED) {
                                log.info("웹훅 중복 수신 무시 (이미 REFUNDED): orderId={}", orderId);
                                return;
                            }

                            // COMPLETED 상태에서만 환불 처리 진행
                            if (order.getStatus() == PaymentOrder.OrderStatus.COMPLETED) {

                                // ── 포인트 회수 (POINT_PACK인 경우) ──
                                // 구독(SUBSCRIPTION)은 포인트가 서비스 이용 혜택이므로 회수하지 않는다.
                                // 포인트팩은 현금으로 구매한 포인트이므로 환불 시 반드시 회수해야 한다.
                                // 회수 실패 시에도 order.refund()는 계속 진행하여 PG 환불과 DB 상태를 맞춘다.
                                // (포인트 회수 실패는 별도 운영 알람 대상 — 수동 조치 필요)
                                if (order.getOrderType() == PaymentOrder.OrderType.POINT_PACK
                                        && order.getPointsAmount() != null
                                        && order.getPointsAmount() > 0) {
                                    try {
                                        // sessionId에 "_refund" 접미사를 붙여 일반 차감과 이력 구분
                                        pointService.deductPoint(
                                                order.getUserId(),
                                                order.getPointsAmount(),
                                                order.getPaymentOrderId() + "_refund",
                                                "결제 환불 포인트 회수"
                                        );
                                        log.info("웹훅 환불 포인트 회수 완료: orderId={}, userId={}, amount={}P",
                                                orderId, order.getUserId(), order.getPointsAmount());
                                    } catch (Exception e) {
                                        // ── 포인트 회수 실패 시 REFUNDED로 변경하지 않는다 ──
                                        // PG는 환불됐으나 포인트가 미회수된 상태로 REFUNDED를 기록하면
                                        // 사용자가 포인트를 공짜로 획득하는 문제가 발생한다.
                                        // failedReason에 사유를 기록하고 관리자 수동 조치를 유도한다.
                                        log.error("[CRITICAL] 웹훅 환불 포인트 회수 실패 — REFUNDED 전환 차단. " +
                                                        "관리자 수동 조치 필요: orderId={}, userId={}, amount={}P, error={}",
                                                orderId, order.getUserId(), order.getPointsAmount(),
                                                e.getMessage(), e);
                                        // 주문 상태는 COMPLETED 유지, failedReason에 포인트 회수 실패 사유 기록
                                        order.setRefundPointFailed(
                                                "웹훅 환불 포인트 회수 실패: " + e.getMessage());
                                        return; // REFUNDED로 전환하지 않고 종료
                                    }
                                }

                                // ── 주문 상태 REFUNDED로 변경 (포인트 회수 성공 후에만 도달) ──
                                order.refund();
                                log.info("웹훅 환불 처리 완료: orderId={}, orderType={}",
                                        orderId, order.getOrderType());
                            }
                        });
                    }
                }
                default -> log.debug("미처리 웹훅 이벤트: eventType={}", eventType);
            }
        } catch (Exception e) {
            /* 웹훅 파싱 실패 시에도 200 반환해야 Toss 재시도를 방지함 — 로그만 기록 */
            log.error("웹훅 이벤트 처리 실패 (파싱 오류): error={}", e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // 보상 취소 관련 상수
    // ──────────────────────────────────────────────

    /**
     * Toss 보상 취소 최대 재시도 횟수.
     *
     * <p>DB 처리 실패 후 PG 환불을 시도하는 횟수 상한이다.
     * {@code confirmPayment()} catch 블록과 {@code attemptCancelWithRetry()} 헬퍼에서 사용한다.</p>
     */
    private static final int MAX_CANCEL_RETRIES = 3;

    /**
     * Toss 보상 취소 재시도 대기 간격 (밀리초).
     *
     * <p>각 재시도 사이에 이 간격만큼 대기한다.
     * 네트워크 일시 장애에서 빠르게 회복할 수 있도록 짧게 설정한다.</p>
     */
    private static final long CANCEL_RETRY_INTERVAL_MS = 100L;

    // ──────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────

    /**
     * Toss 결제 보상 취소를 최대 {@value #MAX_CANCEL_RETRIES}회 재시도한다.
     *
     * <p>DB 처리 실패 후 이미 승인된 Toss 결제를 환불하기 위해 호출한다.
     * 각 시도 실패 시 {@value #CANCEL_RETRY_INTERVAL_MS}ms 대기 후 재시도하며,
     * 인터럽트 발생 시 즉시 루프를 탈출한다.</p>
     *
     * <p>이 메서드는 트랜잭션 컨텍스트를 사용하지 않으며 (외부 API 호출만 수행),
     * 호출자의 트랜잭션 상태에 영향을 주지 않는다.</p>
     *
     * @param paymentKey Toss 결제 키 (환불 대상)
     * @param orderId    주문 UUID (로깅용)
     * @return 환불 성공 여부 (true: 성공, false: 재시도 소진 또는 인터럽트)
     */
    private boolean attemptCancelWithRetry(String paymentKey, String orderId) {
        for (int attempt = 1; attempt <= MAX_CANCEL_RETRIES; attempt++) {
            try {
                tossClient.cancelPayment(paymentKey, "서버 내부 오류로 인한 자동 보상 취소");
                log.info("[C-B3] Toss 보상 취소 성공 (시도 {}/{}): orderId={}",
                        attempt, MAX_CANCEL_RETRIES, orderId);
                return true; // 취소 성공
            } catch (Exception cancelEx) {
                log.warn("[C-B3] Toss 보상 취소 실패 (시도 {}/{}): orderId={}, error={}",
                        attempt, MAX_CANCEL_RETRIES, orderId, cancelEx.getMessage());

                // 마지막 시도가 아니면 다음 재시도 전 대기
                if (attempt < MAX_CANCEL_RETRIES) {
                    try {
                        Thread.sleep(CANCEL_RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        // 스레드 인터럽트 상태 복원 후 즉시 루프 탈출
                        Thread.currentThread().interrupt();
                        log.warn("[C-B3] 보상 취소 대기 중 인터럽트 — 재시도 중단: orderId={}", orderId);
                        return false;
                    }
                }
            }
        }
        return false; // 재시도 소진
    }

    // ──────────────────────────────────────────────
    // 환불 처리 (사용자 요청 기반)
    // ──────────────────────────────────────────────

    /**
     * 결제 주문을 환불 처리한다.
     *
     * <p>사용자가 직접 환불을 요청하거나, 관리자가 수동으로 환불 처리할 때 호출한다.
     * Toss Payments 취소 API 호출 → 포인트 회수 (POINT_PACK인 경우) → DB 상태 REFUNDED 변경 순으로 수행한다.</p>
     *
     * <h3>환불 정책</h3>
     * <ul>
     *   <li>COMPLETED 상태에서만 환불 가능하다. PENDING/FAILED/REFUNDED 상태는 처리 불가.</li>
     *   <li>REFUNDED 상태이면 멱등 처리한다 (이미 완료된 환불 — 재요청 무시).</li>
     *   <li>POINT_PACK: 구매 시 지급된 포인트를 전액 회수한다. 잔액 부족(이미 소진) 시 환불 불가.</li>
     *   <li>SUBSCRIPTION: 서비스 이용 혜택 포인트이므로 회수하지 않는다.</li>
     * </ul>
     *
     * <h3>Toss Payments 취소 API</h3>
     * <p>TossPaymentsClient가 존재하면 실제 API를 호출한다.
     * 호출 실패 시 예외를 전파하여 DB 상태 변경을 막는다 (PG 취소 없이 DB만 변경하면 불일치 발생).</p>
     *
     * <h3>포인트 회수 실패 처리</h3>
     * <p>잔액 부족으로 포인트 회수가 실패하면 BusinessException을 던져 환불을 차단한다.
     * 운영상 포인트를 이미 전부 소진한 경우 고객센터를 통한 수동 조치가 필요하다.</p>
     *
     * @param orderId 환불할 주문 UUID
     * @param userId  요청자 사용자 ID (소유자 검증에 사용 — BOLA 방지)
     * @param reason  환불 사유 (nullable — null이면 "사용자 환불 요청"으로 기록)
     * @return 환불 결과 (success, orderId, refundAmount, message)
     * @throws BusinessException 주문 미발견(ORDER_NOT_FOUND), 소유자 불일치(ORDER_NOT_FOUND),
     *                           환불 불가 상태(PAYMENT_FAILED), 포인트 회수 실패(INSUFFICIENT_POINT)
     */
    @Transactional
    public RefundResponse refundOrder(String orderId, String userId, String reason) {
        log.info("환불 요청 시작: orderId={}, userId={}, reason={}", orderId, userId, reason);

        // ── 1. 주문 조회 (FOR UPDATE — 동시 환불 요청 경쟁 조건 차단) ──
        // 동일 주문에 대해 두 요청이 동시에 COMPLETED를 읽고 각각 환불을 시도하면
        // 포인트가 이중 회수될 수 있다. SELECT FOR UPDATE로 첫 번째 트랜잭션이 완료될 때까지
        // 두 번째 요청을 DB 레벨에서 대기시킨다.
        PaymentOrder order = orderRepository.findByPaymentOrderIdForUpdate(orderId)
                .orElseThrow(() -> {
                    log.error("환불 실패 — 주문 미발견: orderId={}", orderId);
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        // ── 2. 소유자 검증 (BOLA 방지 — 타인의 주문을 환불할 수 없음) ──
        if (!order.getUserId().equals(userId)) {
            log.error("환불 실패 — 주문 소유자 불일치: orderId={}, 주문소유자={}, 요청자={}",
                    orderId, order.getUserId(), userId);
            // 존재 여부를 노출하지 않기 위해 ORDER_NOT_FOUND로 응답
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // ── 3. 멱등성 처리 — 이미 REFUNDED 상태이면 현재 정보를 그대로 반환 ──
        // 네트워크 재시도나 중복 클릭으로 동일 요청이 2회 도달한 경우를 안전하게 처리한다.
        if (order.getStatus() == PaymentOrder.OrderStatus.REFUNDED) {
            log.info("환불 멱등 처리 — 이미 완료된 환불: orderId={}", orderId);
            return new RefundResponse(
                    true,
                    orderId,
                    order.getRefundAmount() != null ? order.getRefundAmount() : order.getAmount(),
                    "이미 환불 처리된 주문입니다."
            );
        }

        // ── 4. 환불 가능 상태 검증 (COMPLETED만 환불 가능) ──
        if (order.getStatus() != PaymentOrder.OrderStatus.COMPLETED) {
            log.warn("환불 실패 — 환불 불가 상태: orderId={}, status={}", orderId, order.getStatus());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "환불 불가 상태입니다. 현재 상태: " + order.getStatus().name()
                            + " (COMPLETED 상태에서만 환불 가능)"
            );
        }

        // ── 5. POINT_PACK인 경우 포인트 회수 먼저 수행 ──
        // 포인트 회수 실패 시 예외를 전파하여 환불 전체를 중단한다.
        // Toss 취소보다 포인트 회수를 먼저 시도하는 이유:
        //   포인트 회수 실패 → Toss 취소 안 함 → PG 취소 없이 DB 변경 없음 → 일관성 유지
        //   Toss 취소 후 포인트 회수 실패 → DB만 미변경 → PG는 취소됐는데 DB는 COMPLETED → 불일치 발생
        if (order.getOrderType() == PaymentOrder.OrderType.POINT_PACK
                && order.getPointsAmount() != null
                && order.getPointsAmount() > 0) {

            log.info("환불 포인트 회수 시작: orderId={}, userId={}, amount={}P",
                    orderId, order.getUserId(), order.getPointsAmount());
            try {
                // deductPoint()는 @Transactional(REQUIRED)이므로 현재 트랜잭션에 합류한다.
                // 잔액 부족 시 InsufficientPointException → 현재 트랜잭션 전체 롤백
                pointService.deductPoint(
                        order.getUserId(),
                        order.getPointsAmount(),
                        orderId + "_refund",         // sessionId: 이력 중복 방지용 고유 ID
                        reason != null ? reason : "사용자 환불 요청"
                );
                log.info("환불 포인트 회수 완료: orderId={}, amount={}P", orderId, order.getPointsAmount());
            } catch (Exception e) {
                // InsufficientPointException(잔액 부족) 또는 기타 예외 모두 전파
                // — 포인트 회수 없이 환불 진행하면 포인트를 공짜로 얻는 케이스가 발생한다.
                log.error("환불 포인트 회수 실패 (환불 중단): orderId={}, userId={}, error={}",
                        orderId, order.getUserId(), e.getMessage());
                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_POINT,
                        "포인트 잔액이 부족하여 환불을 처리할 수 없습니다. "
                                + "잔액이 부족한 경우 고객센터로 문의해 주세요."
                );
            }
        }

        // ── 6. Toss Payments 취소 API 호출 (Phase 9 — 2026-04-08 SDK 실연동) ──
        //
        // TossPaymentsClient.cancelPayment() 는 실패 시 BusinessException(PAYMENT_FAILED)을
        // 자연스럽게 throw 한다. 트랜잭션이 활성 상태에서 예외가 전파되면 Spring 이 자동으로
        // 롤백하여 5단계의 포인트 회수도 함께 되돌려진다 (일관성 보장).
        //
        // 멱등키: cancelPayment 내부에서 paymentKey + "_cancel_all" 형식으로 자동 생성되므로
        // 동일 환불 요청이 중복 도달해도 Toss 서버에서 1회만 처리된다.
        tossClient.cancelPayment(
                order.getPgTransactionId() != null ? order.getPgTransactionId() : orderId,
                reason != null ? reason : "사용자 환불 요청"
        );
        log.info("Toss 결제 취소 API 호출 완료: orderId={}", orderId);

        // ── 7. 주문 상태 REFUNDED로 변경 ──
        // 전체 환불이므로 refundAmount = order.getAmount() (원래 결제 금액 전액)
        String refundReason = reason != null ? reason : "사용자 환불 요청";
        order.refund(refundReason, order.getAmount());

        log.info("환불 처리 완료: orderId={}, userId={}, refundAmount={}원, orderType={}",
                orderId, userId, order.getAmount(), order.getOrderType());

        return new RefundResponse(
                true,
                orderId,
                order.getAmount(),
                "환불이 완료되었습니다. 카드사 정책에 따라 영업일 기준 3~5일 내 취소됩니다."
        );
    }

    // ──────────────────────────────────────────────
    // 운영 안정성 스케줄러 (설계서 §13.7)
    // ──────────────────────────────────────────────

    /**
     * 30분 이상 PENDING 상태인 주문을 자동으로 FAILED 처리한다.
     *
     * <h3>목적</h3>
     * <p>사용자가 결제창을 열었다가 닫거나(브라우저 탭 종료, 뒤로가기 등)
     * 결제를 완료하지 않고 이탈한 경우, 해당 주문이 DB에 PENDING 상태로 무기한 잔류한다.
     * 누적된 PENDING 주문은 인덱스 비효율, 멱등키 충돌 위험, 통계 왜곡을 유발하므로
     * 주기적으로 FAILED 처리하여 DB를 정리한다.</p>
     *
     * <h3>실행 주기</h3>
     * <p>10분마다 실행된다. cutoff 기준은 현재 시각 - 30분이므로,
     * 주문 생성 후 최대 40분(30분 임계값 + 10분 실행 주기) 이내에 정리된다.</p>
     *
     * <h3>트랜잭션 전략</h3>
     * <p>{@code @Transactional}로 전체 배치를 하나의 트랜잭션으로 묶는다.
     * {@code order.fail()} 호출 후 트랜잭션 커밋 시점에 한 번의 batch update가 발생한다.
     * 처리 도중 예외가 발생하면 전체 롤백되어 절반만 FAILED 처리되는 상황을 방지한다.</p>
     *
     * <h3>주의사항</h3>
     * <p>{@code order.fail()}은 PENDING 상태에서만 호출 가능하다.
     * {@code findByStatusAndCreatedAtBefore(PENDING, ...)}로 조회하므로 상태 불일치는 발생하지 않으나,
     * 혹시 모를 동시성 예외는 try-catch로 개별 처리하여 나머지 주문 정리를 계속한다.</p>
     */
    @Scheduled(cron = "0 */10 * * * *") // 매 10분 0초마다 실행
    @Transactional
    public void cleanupExpiredPendingOrders() {
        // 30분 이상 경과한 주문을 FAILED 처리 대상으로 결정
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);

        // PENDING 상태이면서 cutoff 이전에 생성된 주문 목록 조회
        List<PaymentOrder> expired = orderRepository
                .findByStatusAndCreatedAtBefore(PaymentOrder.OrderStatus.PENDING, cutoff);

        // 처리 대상이 없으면 로그 없이 종료 (불필요한 로그 누적 방지)
        if (expired.isEmpty()) {
            return;
        }

        int failedCount = 0;
        for (PaymentOrder order : expired) {
            try {
                // PENDING → FAILED 상태 변경 (failedReason: 타임아웃 사유 기록)
                order.fail("결제 타임아웃 (30분 초과)");
                failedCount++;
            } catch (Exception e) {
                // 개별 주문 처리 실패 시 나머지 주문은 계속 처리 (부분 실패 허용)
                log.warn("PENDING 주문 자동 FAILED 처리 중 개별 오류: orderId={}, error={}",
                        order.getPaymentOrderId(), e.getMessage());
            }
        }

        // 처리 결과 INFO 로그 (운영 모니터링 지표로 활용)
        log.info("[스케줄러] PENDING 주문 자동 FAILED 처리 완료: 대상={}건, 성공={}건, cutoff={}",
                expired.size(), failedCount, cutoff);
    }

    /**
     * PaymentOrder 엔티티를 OrderHistoryResponse DTO로 변환한다.
     *
     * @param order 결제 주문 엔티티
     * @return 결제 내역 응답 DTO
     */
    private OrderHistoryResponse toHistoryResponse(PaymentOrder order) {
        return new OrderHistoryResponse(
                order.getPaymentOrderId(),
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
