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
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.PointPackPriceRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
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
     * 사용자 구독 리포지토리.
     *
     * <p>구독 주문 생성 시점에 이미 ACTIVE 상태의 구독이 존재하는지 사전 검증하기 위해 사용한다.
     * 과거에는 이 사전 검증이 없어서 카드 승인까지 진행된 뒤 Phase 2의
     * {@code SubscriptionService.createSubscription()} 에서 {@code ACTIVE_SUBSCRIPTION_EXISTS}
     * 로 예외가 발생하고, 그 시점에 Phase 2 트랜잭션이 통째로 롤백되면서 사용자에게
     * "카드는 승인됐는데 결제 내역·구독에 아무 변화가 없다"는 혼란을 일으켰다
     * (실제로는 Toss 자동 보상 환불이 이어지지만, 유저 관점에서는 결제 내역이 "사라진" 것처럼 보임).
     *
     * <p>이 리포지토리를 통해 {@code createOrder} 단계에서 조기 차단하면
     * 결제창 자체가 뜨지 않으므로 카드 승인/환불 사이클이 발생하지 않는다.</p>
     */
    private final UserSubscriptionRepository userSubscriptionRepository;

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
            // 4-0. ★ 활성 구독 사전 차단 (2026-04-14 버그 수정 + 플랜 변경 허용)
            //
            // 동일 planCode 로 중복 결제를 시도하는 경우만 차단한다.
            // 다른 planCode(업그레이드/다운그레이드/주기 변경)는 Phase 2 의
            // SubscriptionService.createSubscription() 에서 기존 ACTIVE 를
            // CANCELLED 로 전이한 뒤 새 구독을 생성하는 원자적 흐름으로 처리하므로
            // createOrder 시점에서는 통과시킨다.
            //
            // 과거 완전 차단 로직은 플랜 변경(basic ↔ premium, monthly ↔ yearly) 자체가
            // 불가능하게 만들어, 유저가 무조건 "현재 구독 해지 → 잔여 기간 포기 → 재결제"를
            // 거쳐야 하는 UX 결함을 낳았다. 본 완화 조치는 실제 플랜 전환을 가능하게 하되,
            // 동일 플랜 중복 결제는 여전히 조기 차단한다 (이용자 금전 손실 방지).
            userSubscriptionRepository
                    .findByUserIdAndStatusFetchPlan(userId, UserSubscription.Status.ACTIVE)
                    .ifPresent(activeSub -> {
                        String currentPlanCode = activeSub.getPlan() != null
                                ? activeSub.getPlan().getPlanCode() : null;
                        if (currentPlanCode != null
                                && currentPlanCode.equals(request.planCode())) {
                            log.warn("동일 플랜 중복 결제 시도 차단: userId={}, planCode={}",
                                    userId, request.planCode());
                            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_EXISTS);
                        }
                        log.info("플랜 변경 결제 허용: userId={}, 현재={}, 요청={}",
                                userId, currentPlanCode, request.planCode());
                    });

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
    // 웹훅 처리 (2026-04-24 재설계 — Part B)
    // ──────────────────────────────────────────────

    /** 웹훅 JSON 파싱 전용 ObjectMapper (Jackson 3 tools.jackson). */
    private static final ObjectMapper WEBHOOK_MAPPER = new ObjectMapper();

    /**
     * Toss Payments 웹훅 이벤트를 처리한다 (Part B — 2026-04-24 재설계).
     *
     * <h3>설계 배경</h3>
     * <p>이전 구현은 {@code TossPayments-Signature} 헤더와 HMAC-SHA256 시크릿으로 서명 검증을
     * 시도했으나, Toss Payments 공식 문서에 따르면 {@code PAYMENT_STATUS_CHANGED} 이벤트에는
     * <b>서명 헤더가 존재하지 않고 별도 웹훅 시크릿 키 자체가 발급되지 않는다</b>.
     * ({@code tosspayments-webhook-signature} 는 {@code payout.changed}/{@code seller.changed}
     * 에만 해당하며 본 서비스는 이 이벤트를 구독하지 않는다.)</p>
     *
     * <p>그 결과 기존 구현은 {@code TOSS_WEBHOOK_SECRET} 을 어떻게 설정하든 모든 결제 웹훅이
     * 403 으로 거부되는 구조적 결함을 가지고 있었다. 본 재설계는 공식 문서가 권장하는 방식으로
     * 전환한다 — <b>웹훅 body 는 신뢰하지 않고, {@code orderId} 만 추출해 Toss 에 재조회</b>.</p>
     *
     * <h3>동작</h3>
     * <ol>
     *   <li>이벤트 타입 추출 → {@code PAYMENT_STATUS_CHANGED} 가 아니면 무시</li>
     *   <li>{@code data.orderId} 추출</li>
     *   <li>{@link #syncFromPg(String)} 위임 — Toss {@code getPayment} 재조회 + DB 동기화</li>
     * </ol>
     *
     * <p>이 설계로 얻는 것:</p>
     * <ul>
     *   <li><b>위변조 방어</b>: body 의 status 를 믿지 않고 Toss 에 직접 재조회</li>
     *   <li><b>시크릿 불필요</b>: Toss 가 발급하지 않는 키를 더 이상 요구하지 않음</li>
     *   <li><b>단일 진실 원본</b>: 관리자 "PG 재조회" 버튼과 웹훅 처리가 같은 로직을 공유</li>
     *   <li><b>멱등성</b>: {@code syncFromPg} 는 이미 REFUNDED 인 건을 NO_CHANGE 로 건너뛰므로
     *                     Toss 가 동일 웹훅을 재전송해도 안전</li>
     * </ul>
     *
     * <h3>트랜잭션 전략</h3>
     * <p>본 메서드에는 {@code @Transactional} 을 부여하지 않는다. {@code syncFromPg} 가 자체
     * 트랜잭션을 갖기 때문이며, 외부에서 랩핑된 트랜잭션 안에서 또 다른 트랜잭션을 시작하지 않아야
     * FOR UPDATE 잠금 경합을 최소화할 수 있다.</p>
     *
     * <h3>예외 처리</h3>
     * <p>내부에서 발생하는 모든 예외는 로그로만 남기고 삼킨다. 웹훅 엔드포인트는 항상 200 을
     * 반환해야 Toss 가 재시도 폭주를 유발하지 않는다. 처리 실패한 주문은 관리자 페이지 "PG 재조회"
     * 버튼으로 수동 복구할 수 있다.</p>
     *
     * @param rawBody Toss 웹훅 요청 Body 원문 (JSON)
     */
    public void processWebhook(String rawBody) {
        log.info("Toss 웹훅 수신");

        try {
            JsonNode root = WEBHOOK_MAPPER.readTree(rawBody);
            String eventType = root.path("eventType").asText("");

            if (!"PAYMENT_STATUS_CHANGED".equals(eventType)) {
                log.debug("미처리 웹훅 이벤트: eventType={}", eventType);
                return;
            }

            String orderId = root.path("data").path("orderId").asText(null);
            if (orderId == null || orderId.isBlank()) {
                log.warn("웹훅 orderId 누락: eventType={}", eventType);
                return;
            }

            /* Toss 재조회 기반 DB 동기화 — 관리자 "PG 재조회" 와 동일 로직 재사용.
             * NOT_FOUND(다른 가맹점 주문 등) / Toss 장애 등은 내부에서 BusinessException 으로 전파되지만
             * 아래 catch 에서 삼켜서 200 반환 → Toss 재시도 폭주 방지. */
            PgSyncResult result = syncFromPg(orderId);
            log.info("웹훅 처리 완료: orderId={}, result={}, dbStatus={}, pgStatus={}, pointsRecovered={}",
                    orderId, result.result(), result.dbStatus(), result.pgStatus(), result.pointsRecovered());
        } catch (Exception e) {
            /* 웹훅 처리 실패는 로그로만 남기고 200 반환 — Toss 재시도 폭주 방지.
             * 복구가 필요한 주문은 관리자 페이지 "PG 재조회" 버튼으로 수동 처리 가능. */
            log.error("웹훅 이벤트 처리 실패: error={}", e.getMessage(), e);
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
        //
        // ── PG 상태 선조회 (2026-04-24 추가) ──
        //
        // Toss 콘솔에서 직접 취소했거나 웹훅이 유실되어 Toss 측만 CANCELED 상태인
        // 주문에 refund() 가 호출되면, cancelPayment() 가 ALREADY_CANCELED_PAYMENT 에러를
        // 반환해 예외가 전파되고 트랜잭션 전체가 롤백되어 포인트 회수마저 되돌려진다.
        // 그 결과 유저는 Toss 측에서 환불을 받고도 포인트는 그대로 보유하는 불일치 상태가 발생한다.
        //
        // 근본 해결: cancelPayment() 호출 전에 Toss 의 현재 상태를 조회하여
        // 이미 CANCELED / PARTIAL_CANCELED 인 경우 PG 취소 호출을 건너뛰고 DB 동기화만 진행한다.
        //
        // 선조회 자체가 실패한 경우(Toss 일시장애 등)에는 스킵하지 않고 일반 플로우로
        // 진행한다. 정상 케이스에서 cancelPayment() 는 성공할 것이고, 진짜 이미 취소된 건이라면
        // 여전히 종래대로 500 이 나지만 "데이터 유실" 은 이미 이번 변경으로 차단되었으므로 보수적이다.
        String paymentKey = order.getPgTransactionId() != null ? order.getPgTransactionId() : orderId;
        boolean tossAlreadyCanceled = false;
        try {
            TossPaymentsClient.TossConfirmResponse pgStatus = tossClient.getPayment(paymentKey);
            String pgStatusValue = pgStatus.status();
            if ("CANCELED".equals(pgStatusValue) || "PARTIAL_CANCELED".equals(pgStatusValue)) {
                tossAlreadyCanceled = true;
                log.warn("Toss 측 이미 취소됨 — PG 취소 호출 생략, DB 동기화만 진행: " +
                                "orderId={}, paymentKey={}, tossStatus={}",
                        orderId, paymentKey, pgStatusValue);
            }
        } catch (Exception e) {
            // 선조회 실패는 비치명적 — 일반 플로우로 진행 (Toss 일시장애 대응)
            log.warn("Toss 상태 선조회 실패 — 일반 환불 플로우로 진행: orderId={}, error={}",
                    orderId, e.getMessage());
        }

        if (!tossAlreadyCanceled) {
            tossClient.cancelPayment(
                    paymentKey,
                    reason != null ? reason : "사용자 환불 요청"
            );
            log.info("Toss 결제 취소 API 호출 완료: orderId={}", orderId);
        }

        // ── 7. 주문 상태 REFUNDED로 변경 ──
        // 전체 환불이므로 refundAmount = order.getAmount() (원래 결제 금액 전액)
        String refundReason = reason != null ? reason : "사용자 환불 요청";
        if (tossAlreadyCanceled) {
            // PG 선취소 케이스임을 환불 사유에 명시 (감사 추적 용이성)
            refundReason = "[PG 선취소] " + refundReason;
        }
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
    // PG 재조회 동기화 (2026-04-24 추가)
    // ──────────────────────────────────────────────

    /**
     * Toss 측 현재 결제 상태를 조회하여 DB 상태가 PG 와 어긋난 경우 동기화한다.
     *
     * <p><b>배경</b>: Toss 콘솔에서 직접 취소하거나 웹훅이 유실되면
     * Toss 측은 CANCELED 인데 우리 DB 는 COMPLETED 로 남는다. 웹훅 재전송을 보장할 수 없고
     * 관리자가 일반 환불 버튼을 누르면 {@code cancelPayment()} 가 ALREADY_CANCELED 에러를
     * 반환해 트랜잭션이 롤백되며 포인트 회수도 취소되어 불일치가 고착된다.
     * 본 메서드는 {@link TossPaymentsClient#getPayment} 로 PG 현재 상태를 읽어오기만 하고
     * PG 취소 API({@code cancelPayment})는 절대 호출하지 않는다. 즉 웹훅을 "재실행" 하는 효과만 낸다.</p>
     *
     * <h3>동작 규칙</h3>
     * <ul>
     *   <li>Toss {@code CANCELED}/{@code PARTIAL_CANCELED} + DB {@code COMPLETED}
     *       → 포인트 회수({@code POINT_PACK}인 경우) + DB {@code REFUNDED} 마킹 → result={@code SYNCED}</li>
     *   <li>Toss {@code CANCELED} + DB {@code REFUNDED} → 이미 일치 → result={@code NO_CHANGE}</li>
     *   <li>Toss {@code DONE} + DB {@code COMPLETED} → 이미 일치 → result={@code NO_CHANGE}</li>
     *   <li>그 외 조합(예: PG {@code DONE} + DB {@code REFUNDED}) → result={@code MISMATCH},
     *       자동 동기화하지 않고 수동 검토 유도</li>
     * </ul>
     *
     * <h3>트랜잭션 전략</h3>
     * <p>{@code FOR UPDATE} 로 잠금을 걸어 관리자 환불 또는 웹훅 처리와의 경쟁 조건을 차단한다.
     * 포인트 회수는 같은 트랜잭션에 합류하므로 전체가 원자적으로 커밋/롤백된다.
     * Toss 호출은 외부 API 지만 readonly(getPayment) 이므로 장시간 보유해도 부작용이 없다.</p>
     *
     * <h3>멱등성</h3>
     * <p>포인트 회수 sessionId 는 기존 환불 로직과 동일한 {@code orderId + "_refund"} 규약을 사용하므로,
     * 이미 환불 흐름에서 한 번 회수된 주문은 {@code PointService.deductPoint} 중복 방지에 의해 2차 회수되지 않는다.
     * 또한 DB 상태가 COMPLETED 가 아니면 포인트 회수 자체를 건너뛴다.</p>
     *
     * @param orderId 동기화 대상 주문 UUID
     * @return 동기화 결과 ({@link PgSyncResult})
     * @throws BusinessException 주문 미발견({@link ErrorCode#ORDER_NOT_FOUND}) 또는 Toss 조회 실패
     */
    @Transactional
    public PgSyncResult syncFromPg(String orderId) {
        log.info("PG 재조회 동기화 요청: orderId={}", orderId);

        // ── 1. 주문 조회 (FOR UPDATE — 관리자 환불/웹훅 처리와의 경쟁 조건 차단) ──
        PaymentOrder order = orderRepository.findByPaymentOrderIdForUpdate(orderId)
                .orElseThrow(() -> {
                    log.warn("PG 재조회 실패 — 주문 미발견: orderId={}", orderId);
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        PaymentOrder.OrderStatus dbStatus = order.getStatus();

        // ── 2. Toss 측 상태 조회 ──
        // pgTransactionId 가 null 이면 아직 승인되지 않은 주문 — orderId 로 조회 시도 (Toss 가 지원)
        String paymentKey = order.getPgTransactionId() != null ? order.getPgTransactionId() : orderId;
        TossPaymentsClient.TossConfirmResponse pgStatus;
        try {
            pgStatus = tossClient.getPayment(paymentKey);
        } catch (Exception e) {
            log.error("PG 재조회 실패 — Toss 조회 오류: orderId={}, paymentKey={}, error={}",
                    orderId, paymentKey, e.getMessage());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "Toss 결제 조회에 실패했습니다: " + e.getMessage()
            );
        }
        String pgStatusValue = pgStatus.status();
        boolean pgCanceled = "CANCELED".equals(pgStatusValue) || "PARTIAL_CANCELED".equals(pgStatusValue);

        log.info("PG 재조회 결과: orderId={}, dbStatus={}, pgStatus={}", orderId, dbStatus, pgStatusValue);

        // ── 3. 상태 분기 ──
        // Case A: PG 는 CANCELED 인데 DB 는 COMPLETED — 동기화 수행
        if (pgCanceled && dbStatus == PaymentOrder.OrderStatus.COMPLETED) {
            int pointsRecovered = 0;

            // POINT_PACK 은 포인트 회수 필요
            if (order.getOrderType() == PaymentOrder.OrderType.POINT_PACK
                    && order.getPointsAmount() != null
                    && order.getPointsAmount() > 0) {
                try {
                    // sessionId: 기존 환불 규약(orderId + "_refund") 유지 — PointService 중복 방지 동작
                    pointService.deductPoint(
                            order.getUserId(),
                            order.getPointsAmount(),
                            orderId + "_refund",
                            "PG 재조회 동기화 - 포인트 회수"
                    );
                    pointsRecovered = order.getPointsAmount();
                    log.info("PG 재조회 포인트 회수 완료: orderId={}, userId={}, amount={}P",
                            orderId, order.getUserId(), pointsRecovered);
                } catch (Exception e) {
                    // 환불 플로우와 동일하게 잔액 부족 등 전파 → 전체 롤백
                    log.error("PG 재조회 포인트 회수 실패 (동기화 중단): orderId={}, userId={}, error={}",
                            orderId, order.getUserId(), e.getMessage());
                    throw new BusinessException(
                            ErrorCode.INSUFFICIENT_POINT,
                            "포인트 잔액이 부족하여 PG 상태 동기화를 처리할 수 없습니다. 수동 조치가 필요합니다."
                    );
                }
            }

            // DB 상태를 REFUNDED 로 마킹 (Toss 재호출 없음 — getPayment 는 이미 취소 확인만)
            order.refund("[PG 재조회] Toss " + pgStatusValue + " 동기화", order.getAmount());
            log.info("PG 재조회 DB 동기화 완료: orderId={}, newStatus=REFUNDED", orderId);

            return new PgSyncResult(
                    "SYNCED",
                    "REFUNDED",
                    pgStatusValue,
                    pointsRecovered,
                    order.getUserId(),
                    order.getOrderType().name()
            );
        }

        // Case B: PG 와 DB 가 이미 일치 — 변경 없음
        if (pgCanceled && dbStatus == PaymentOrder.OrderStatus.REFUNDED) {
            return new PgSyncResult(
                    "NO_CHANGE", dbStatus.name(), pgStatusValue, 0,
                    order.getUserId(), order.getOrderType().name()
            );
        }
        if (!pgCanceled && dbStatus == PaymentOrder.OrderStatus.COMPLETED) {
            return new PgSyncResult(
                    "NO_CHANGE", dbStatus.name(), pgStatusValue, 0,
                    order.getUserId(), order.getOrderType().name()
            );
        }

        // Case C: 그 외 조합은 자동 규칙 외 — MISMATCH 로 보고하고 관리자 수동 검토 유도
        log.warn("PG 재조회 MISMATCH: orderId={}, dbStatus={}, pgStatus={} — 자동 동기화 규칙 외",
                orderId, dbStatus, pgStatusValue);
        return new PgSyncResult(
                "MISMATCH", dbStatus.name(), pgStatusValue, 0,
                order.getUserId(), order.getOrderType().name()
        );
    }

    /**
     * PG 재조회 동기화 결과 DTO.
     *
     * @param result          동기화 결과 (SYNCED / NO_CHANGE / MISMATCH)
     * @param dbStatus        DB 주문 현재 상태 (동기화 후 최종)
     * @param pgStatus        Toss 결제 현재 상태
     * @param pointsRecovered 회수된 포인트 금액 (SYNCED & POINT_PACK 인 경우에만 양수)
     * @param userId          주문 소유자 ID (감사 로그용)
     * @param orderType       주문 유형 (감사 로그 스냅샷용)
     */
    public record PgSyncResult(
            String result,
            String dbStatus,
            String pgStatus,
            int pointsRecovered,
            String userId,
            String orderType
    ) {}

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
