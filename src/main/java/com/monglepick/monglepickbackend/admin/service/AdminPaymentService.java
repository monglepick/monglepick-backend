package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminCancelSubscriptionResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminCompensateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminCompensateResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminExtendSubscriptionRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminExtendSubscriptionResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminManualPointRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminManualPointResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminPgSyncResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminRefundRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminRefundResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PaymentOrderDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PaymentOrderSummary;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointHistoryItem;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointItemCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointItemResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.PointItemUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.SubscriptionSummary;
import com.monglepick.monglepickbackend.admin.repository.AdminPaymentOrderRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminPointsHistoryRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.RefundResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.service.PaymentService;
import com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 결제/포인트 서비스.
 *
 * <p>관리자 페이지 "결제/포인트" 탭의 12개 기능에 대한 비즈니스 로직을 담당한다.
 * 도메인 레이어의 {@link PaymentService}, {@link SubscriptionService}, {@link PointService}를
 * 최대한 재사용하되, 관리자 전용 특수 케이스(예: 사용자 ID 소유자 검증 우회, 수동 보상 복구)는
 * 이 서비스에서 처리한다.</p>
 *
 * <h3>담당 기능 (설계서 §3.1)</h3>
 * <ul>
 *   <li>결제 주문: 목록 / 상세 / 환불 (3)</li>
 *   <li>구독: 목록 / 보상 복구 / 취소 / 연장 (4)</li>
 *   <li>포인트: 이력 조회 / 수동 지급·차감 (2)</li>
 *   <li>포인트 아이템: 목록 / 생성 / 수정 (3)</li>
 * </ul>
 *
 * <h3>트랜잭션 전략</h3>
 * <p>클래스 레벨 {@code @Transactional(readOnly = true)}로 기본 설정하고,
 * 쓰기 메서드에만 {@code @Transactional}을 오버라이드한다.
 * 단, {@link PaymentService#refundOrder}, {@link SubscriptionService#cancelSubscription} 등
 * 도메인 메서드는 각자 {@code @Transactional}을 갖고 있으므로 새 트랜잭션이 시작된다.</p>
 *
 * <h3>설계 결정 — 환불 시 소유자 검증 우회</h3>
 * <p>도메인 {@link PaymentService#refundOrder}는 BOLA(Broken Object Level Authorization) 방지를 위해
 * userId 파라미터를 받아 주문 소유자를 검증한다. 관리자 환불에서는 먼저 주문을 조회하여 실제 소유자 ID를
 * 알아낸 후 그 값을 그대로 전달하여 검증 로직을 통과시킨다 (사실상 관리자 임파서네이션 패턴).
 * 이 방식으로 환불 로직의 PG 취소·포인트 회수·멱등 처리 등 모든 방어 로직을 그대로 재사용한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentService {

    /** 관리자 전용 결제 주문 리포지토리 — 상태별·전체 페이징 조회 */
    private final AdminPaymentOrderRepository adminPaymentOrderRepository;

    /** 관리자 전용 구독 리포지토리 — plan JOIN FETCH 페이징 조회 */
    private final AdminSubscriptionRepository adminSubscriptionRepository;

    /** 관리자 전용 포인트 이력 리포지토리 — 전체/사용자별 페이징 조회 */
    private final AdminPointsHistoryRepository adminPointsHistoryRepository;

    /** 포인트 아이템 리포지토리 (도메인 재사용) */
    private final PointItemRepository pointItemRepository;

    /** 도메인 결제 서비스 — 환불 로직 재사용 */
    private final PaymentService paymentService;

    /** 도메인 구독 서비스 — 취소/연장 로직 재사용 */
    private final SubscriptionService subscriptionService;

    /** 도메인 포인트 서비스 — 수동 지급/차감 로직 재사용 */
    private final PointService pointService;

    /**
     * 관리자 감사 로그 서비스 — 환불/보상/구독/포인트 모든 쓰기 액션의 성공 시점에
     * 호출하여 admin_audit_logs 테이블에 흔적을 남긴다. REQUIRES_NEW 트랜잭션이므로
     * 감사 로그 실패는 업무 트랜잭션에 영향을 주지 않는다. (2026-04-09 P1-1 추가)
     */
    private final AdminAuditService adminAuditService;

    /**
     * 사용자 기본 정보 조회용 MyBatis Mapper.
     * 2026-04-14 추가 — 결제 주문/구독/포인트 이력 DTO 에 nickname/email 를 채워주기 위해 사용한다.
     * admin 도메인이 JPA 기반이지만 User 는 MyBatis 로 R/W 하므로 도메인 Mapper 를 재사용한다.
     */
    private final UserMapper userMapper;

    // ======================== 결제 주문 ========================

    /**
     * 결제 주문 목록을 최신순으로 페이징 조회한다.
     *
     * <p>status 파라미터가 null/공백이면 전체 조회, 그 외에는 해당 상태만 필터링한다.
     * 유효하지 않은 상태 문자열을 전달하면 {@link IllegalArgumentException}이 발생하므로
     * 서비스 레이어에서 대문자 정규화 후 enum으로 변환한다.</p>
     *
     * @param status   주문 상태 문자열 (PENDING / COMPLETED / FAILED / REFUNDED / COMPENSATION_FAILED)
     *                 null 또는 공백이면 전체 조회
     * @param pageable 페이지 정보
     * @return 결제 주문 요약 페이지
     */
    /**
     * 결제 주문 목록을 필터 조합으로 조회한다 (2026-04-14 도입, 2026-04-23 날짜 범위 확장).
     *
     * @param status     주문 상태 문자열 (nullable — 생략 시 전체)
     * @param orderType  주문 유형 문자열 (SUBSCRIPTION/POINT_PACK, nullable)
     * @param userId     사용자 ID (nullable — 이메일/닉네임 검색으로 얻은 UUID)
     * @param fromDate   주문 생성일 시작 inclusive (nullable)
     * @param toDate     주문 생성일 종료 exclusive (nullable)
     * @param pageable   페이지 정보
     * @return 결제 주문 요약 페이지 (nickname/email enrich 포함)
     */
    public Page<PaymentOrderSummary> getOrders(
            String status, String orderType, String userId,
            LocalDateTime fromDate, LocalDateTime toDate,
            Pageable pageable
    ) {
        log.debug("[AdminPayment] 결제 내역 조회 — status={}, orderType={}, userId={}, from={}, to={}, page={}",
                status, orderType, userId, fromDate, toDate, pageable.getPageNumber());

        PaymentOrder.OrderStatus statusEnum = parseOrderStatus(status);
        PaymentOrder.OrderType   typeEnum   = parseOrderType(orderType);
        String                   userIdOrNull = (userId != null && !userId.isBlank()) ? userId : null;

        Page<PaymentOrder> page = adminPaymentOrderRepository.searchByFilters(
                statusEnum, typeEnum, userIdOrNull, fromDate, toDate, pageable
        );

        Map<String, User> userMap = fetchUserMap(page.getContent().stream()
                .map(PaymentOrder::getUserId).toList());

        return page.map((order) -> toOrderSummary(order, userMap.get(order.getUserId())));
    }

    /** 주문 상태 문자열을 enum 으로 안전 변환 (nullable) */
    private PaymentOrder.OrderStatus parseOrderStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PaymentOrder.OrderStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[AdminPayment] 잘못된 주문 상태 필터: {}", raw);
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 주문 상태: " + raw);
        }
    }

    /** 주문 유형 문자열을 enum 으로 안전 변환 (nullable) */
    private PaymentOrder.OrderType parseOrderType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PaymentOrder.OrderType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[AdminPayment] 잘못된 주문 유형 필터: {}", raw);
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 주문 유형: " + raw);
        }
    }

    /**
     * 결제 주문 단건 상세를 조회한다.
     *
     * @param orderId 주문 UUID
     * @return 결제 주문 상세 응답
     * @throws BusinessException 주문 미발견 시 ORDER_NOT_FOUND
     */
    public PaymentOrderDetail getOrderDetail(String orderId) {
        log.debug("[AdminPayment] 결제 상세 조회 — orderId={}", orderId);
        PaymentOrder order = adminPaymentOrderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("[AdminPayment] 결제 상세 실패 — 주문 미발견: orderId={}", orderId);
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });
        return toOrderDetail(order);
    }

    /**
     * 결제 주문을 환불 처리한다 (관리자 요청).
     *
     * <p>실제 환불 로직은 {@link PaymentService#refundOrder(String, String, String)}를 그대로 호출한다.
     * BOLA 방지를 위해 해당 메서드가 요구하는 userId는 주문의 실제 소유자 ID를 먼저 조회해 전달한다.</p>
     *
     * @param orderId 환불할 주문 UUID
     * @param request 환불 요청 DTO (사유 포함, nullable)
     * @return 환불 응답 DTO
     * @throws BusinessException 주문 미발견 / 환불 불가 상태 / 포인트 잔액 부족 등
     */
    public AdminRefundResponse refundOrder(String orderId, AdminRefundRequest request) {
        // 1. 주문을 먼저 조회하여 실제 소유자 ID 확보 (환불 API 소유자 검증 우회용)
        PaymentOrder order = adminPaymentOrderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("[AdminPayment] 환불 실패 — 주문 미발견: orderId={}", orderId);
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        // 2. 환불 사유 (nullable — null 이면 "관리자 환불 처리")
        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "관리자 환불 처리";

        log.info("[AdminPayment] 환불 요청 — orderId={}, ownerUserId={}, reason={}",
                orderId, order.getUserId(), reason);

        // 3. 도메인 레이어 환불 로직 재사용 — 실제 소유자 ID를 전달해서 BOLA 검증 통과
        RefundResponse response = paymentService.refundOrder(orderId, order.getUserId(), reason);

        // 4. 감사 로그 기록 (성공 시에만) — 별도 REQUIRES_NEW 트랜잭션이므로 실패해도 환불은 유지
        //    상세 스냅샷에 환불 금액과 주문 유형을 JSON으로 남겨 추후 분쟁 시 근거 자료로 사용한다.
        // refundAmount 는 RefundResponse 에서 primitive int 이므로 null 체크 불필요
        String afterSnapshot = String.format(
                "{\"orderType\":\"%s\",\"refundAmount\":%d,\"reason\":\"%s\"}",
                order.getOrderType().name(),
                response.refundAmount(),
                escapeJson(reason)
        );
        adminAuditService.log(
                AdminAuditService.ACTION_PAYMENT_REFUND,
                AdminAuditService.TARGET_PAYMENT,
                orderId,
                String.format("주문 %s 환불 처리 — 대상 사용자: %s, 금액: %d원, 사유: %s",
                        orderId, order.getUserId(),
                        response.refundAmount(),
                        reason),
                null,
                afterSnapshot
        );

        return new AdminRefundResponse(
                response.success(),
                response.orderId(),
                response.refundAmount(),
                response.message()
        );
    }

    /**
     * 결제 주문의 PG(Toss) 현재 상태를 조회하여 DB 와 동기화한다 (2026-04-24 추가).
     *
     * <p><b>사용 시점</b>: Toss 콘솔에서 직접 취소하거나 웹훅이 유실되어 PG 는 취소됐으나
     * DB 는 COMPLETED 로 남은 주문. 일반 {@link #refundOrder} 는 cancelPayment 를 재호출해
     * ALREADY_CANCELED 에러 → 500 → 트랜잭션 롤백 → 포인트 회수 실패 를 유발하므로 이 메서드를 대신 사용한다.</p>
     *
     * <p>실제 동기화 로직은 {@link PaymentService#syncFromPg(String)} 이 수행한다.
     * 본 메서드는 결과에 따라 감사 로그를 기록하고 관리자용 응답 DTO 를 조립할 뿐이다.</p>
     *
     * <p><b>감사 로그</b>: SYNCED/NO_CHANGE/MISMATCH 모두 기록한다. MISMATCH 케이스는 관리자의
     * 수동 검토가 필요한 의심스러운 상태이므로 오히려 추적이 더 중요하다.</p>
     *
     * @param orderId 동기화 대상 주문 UUID
     * @return 관리자 UI 용 PG 동기화 결과 응답
     */
    public AdminPgSyncResponse syncFromPg(String orderId) {
        log.info("[AdminPayment] PG 재조회 요청 — orderId={}", orderId);

        // 도메인 레이어에 위임 — FOR UPDATE 잠금 + Toss 조회 + 상태 분기 + 포인트 회수
        PaymentService.PgSyncResult result = paymentService.syncFromPg(orderId);

        // 감사 로그 기록 — 모든 결과 타입을 남겨 운영 추적성 확보
        String afterSnapshot = String.format(
                "{\"result\":\"%s\",\"dbStatus\":\"%s\",\"pgStatus\":\"%s\",\"pointsRecovered\":%d,\"orderType\":\"%s\"}",
                result.result(),
                result.dbStatus(),
                result.pgStatus(),
                result.pointsRecovered(),
                result.orderType()
        );
        adminAuditService.log(
                AdminAuditService.ACTION_PAYMENT_PG_SYNC,
                AdminAuditService.TARGET_PAYMENT,
                orderId,
                String.format("주문 %s PG 재조회 — result=%s, pgStatus=%s, dbStatus=%s, pointsRecovered=%d, ownerUserId=%s",
                        orderId,
                        result.result(),
                        result.pgStatus(),
                        result.dbStatus(),
                        result.pointsRecovered(),
                        result.userId()),
                null,
                afterSnapshot
        );

        // 관리자 UI 노출 메시지 — 결과 타입별 안내문 구성
        String message = switch (result.result()) {
            case "SYNCED" -> (result.pointsRecovered() > 0)
                    ? String.format("PG 상태(%s)로 동기화 완료. 포인트 %dP 회수됨.",
                            result.pgStatus(), result.pointsRecovered())
                    : String.format("PG 상태(%s)로 동기화 완료. (회수 대상 포인트 없음)",
                            result.pgStatus());
            case "NO_CHANGE" -> String.format("DB(%s) 와 PG(%s) 상태가 일치합니다. 변경 사항 없음.",
                    result.dbStatus(), result.pgStatus());
            case "MISMATCH" -> String.format("상태 불일치 — DB=%s, PG=%s. 자동 동기화 규칙에 해당하지 않으므로 수동 검토가 필요합니다.",
                    result.dbStatus(), result.pgStatus());
            default -> "처리 결과: " + result.result();
        };

        return new AdminPgSyncResponse(
                result.result(),
                result.dbStatus(),
                result.pgStatus(),
                result.pointsRecovered(),
                message
        );
    }

    // ======================== 구독 ========================

    /**
     * 구독 목록을 페이징 조회한다.
     *
     * <p>status 파라미터가 null/공백이면 전체 조회, 그 외에는 해당 상태만 필터링한다.
     * plan은 JOIN FETCH로 즉시 로딩된다.</p>
     *
     * @param status   구독 상태 문자열 (ACTIVE / CANCELLED / EXPIRED), 공백이면 전체
     * @param pageable 페이지 정보
     * @return 구독 요약 페이지
     */
    /**
     * 구독 목록을 필터 조합으로 조회한다 (2026-04-14 도입, 2026-04-23 날짜 범위 확장).
     *
     * @param status    구독 상태 문자열 (nullable)
     * @param planCode  구독 플랜 코드 (예: monthly_basic, nullable)
     * @param userId    사용자 ID (nullable)
     * @param fromDate  구독 생성일 시작 inclusive (nullable)
     * @param toDate    구독 생성일 종료 exclusive (nullable)
     * @param pageable  페이지 정보
     * @return 구독 요약 페이지 (nickname/email enrich 포함)
     */
    public Page<SubscriptionSummary> getSubscriptions(
            String status, String planCode, String userId,
            LocalDateTime fromDate, LocalDateTime toDate,
            Pageable pageable
    ) {
        log.debug("[AdminPayment] 구독 목록 조회 — status={}, planCode={}, userId={}, from={}, to={}, page={}",
                status, planCode, userId, fromDate, toDate, pageable.getPageNumber());

        UserSubscription.Status statusEnum = parseSubStatus(status);
        String planCodeOrNull = (planCode != null && !planCode.isBlank()) ? planCode : null;
        String userIdOrNull   = (userId != null && !userId.isBlank()) ? userId : null;

        Page<UserSubscription> page = adminSubscriptionRepository.searchByFilters(
                statusEnum, planCodeOrNull, userIdOrNull, fromDate, toDate, pageable
        );

        Map<String, User> userMap = fetchUserMap(page.getContent().stream()
                .map(UserSubscription::getUserId).toList());

        return page.map((sub) -> toSubscriptionSummary(sub, userMap.get(sub.getUserId())));
    }

    /** 구독 상태 문자열을 enum 으로 안전 변환 (nullable) */
    private UserSubscription.Status parseSubStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UserSubscription.Status.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[AdminPayment] 잘못된 구독 상태 필터: {}", raw);
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 구독 상태: " + raw);
        }
    }

    /**
     * COMPENSATION_FAILED 상태 주문을 COMPLETED 로 수동 복구한다 (관리자 전용).
     *
     * <p>관리자가 Toss Payments 콘솔에서 결제 이력을 확인하고 포인트를 수동 지급한 뒤
     * 이 API로 주문 상태를 최종 COMPLETED 로 복구한다. 이 경로는 사용자 화면에는 노출되지 않는다.</p>
     *
     * <p>도메인 메서드 {@link PaymentOrder#markRecovered(String)}가 상태 전이와 adminNote 기록을
     * 담당한다.</p>
     *
     * @param orderId 복구할 주문 UUID
     * @param request 복구 요청 DTO (adminNote 필수)
     * @return 복구 응답 DTO
     * @throws BusinessException 주문 미발견(ORDER_NOT_FOUND) 또는 상태가 COMPENSATION_FAILED 가 아닌 경우
     */
    @Transactional
    public AdminCompensateResponse compensateOrder(String orderId, AdminCompensateRequest request) {
        log.info("[AdminPayment] 보상 복구 요청 — orderId={}, adminNote={}", orderId, request.adminNote());

        PaymentOrder order = adminPaymentOrderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("[AdminPayment] 보상 복구 실패 — 주문 미발견: orderId={}", orderId);
                    return new BusinessException(ErrorCode.ORDER_NOT_FOUND);
                });

        // 도메인 메서드가 상태 검증 포함 — COMPENSATION_FAILED 가 아니면 IllegalStateException 발생
        try {
            order.markRecovered(request.adminNote());
        } catch (IllegalStateException e) {
            log.warn("[AdminPayment] 보상 복구 실패 — 상태 불일치: orderId={}, status={}",
                    orderId, order.getStatus());
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "COMPENSATION_FAILED 상태에서만 복구할 수 있습니다. 현재 상태: " + order.getStatus());
        }

        log.info("[AdminPayment] 보상 복구 완료 — orderId={}, newStatus=COMPLETED", orderId);

        // 감사 로그 — 보상 복구는 드물지만 금전 이동과 연관된 운영 액션이므로 반드시 기록
        adminAuditService.log(
                AdminAuditService.ACTION_PAYMENT_COMPENSATE,
                AdminAuditService.TARGET_PAYMENT,
                orderId,
                String.format("주문 %s COMPENSATION_FAILED → COMPLETED 복구 (adminNote: %s)",
                        orderId, request.adminNote())
        );

        return new AdminCompensateResponse(
                true,
                orderId,
                order.getStatus().name(),
                "주문 상태를 COMPLETED 로 복구하였습니다."
        );
    }

    /**
     * 구독을 관리자 권한으로 취소한다.
     *
     * <p>도메인 {@link SubscriptionService#cancelSubscription(String)}을 재사용한다.
     * 해당 메서드는 userId 기준으로 활성 구독을 조회하므로, 먼저 subscriptionId로 구독을 조회한 뒤
     * 그 userId를 넘겨 호출한다.</p>
     *
     * @param subscriptionId 취소할 구독 레코드 ID
     * @return 취소 응답 DTO
     * @throws BusinessException 구독 미발견 / ACTIVE 가 아닌 경우
     */
    @Transactional
    public AdminCancelSubscriptionResponse cancelSubscription(Long subscriptionId) {
        log.info("[AdminPayment] 구독 취소 요청 — subscriptionId={}", subscriptionId);

        UserSubscription sub = adminSubscriptionRepository.findByIdWithPlan(subscriptionId)
                .orElseThrow(() -> {
                    log.warn("[AdminPayment] 구독 취소 실패 — 구독 미발견: subscriptionId={}", subscriptionId);
                    return new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
                });

        if (sub.getStatus() != UserSubscription.Status.ACTIVE) {
            log.warn("[AdminPayment] 구독 취소 실패 — ACTIVE 아님: subscriptionId={}, status={}",
                    subscriptionId, sub.getStatus());
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "ACTIVE 상태에서만 취소할 수 있습니다. 현재 상태: " + sub.getStatus());
        }

        // 도메인 서비스 호출 — 내부적으로 @Transactional 이므로 REQUIRED 전파로 현재 트랜잭션 합류
        subscriptionService.cancelSubscription(sub.getUserId());

        // 취소 후 상태 확인을 위해 재조회 (영속성 컨텍스트 내 동일 객체이므로 상태 반영됨)
        log.info("[AdminPayment] 구독 취소 완료 — subscriptionId={}, userId={}",
                subscriptionId, sub.getUserId());

        // 감사 로그 — 구독 강제 취소 (사용자 불만 원인이 될 수 있는 민감 액션)
        adminAuditService.log(
                AdminAuditService.ACTION_SUBSCRIPTION_CANCEL,
                AdminAuditService.TARGET_SUBSCRIPTION,
                String.valueOf(subscriptionId),
                String.format("구독 %d 관리자 취소 — 대상 사용자: %s, 플랜: %s, 만료 예정: %s",
                        subscriptionId, sub.getUserId(),
                        sub.getPlan() != null ? sub.getPlan().getPlanCode() : "UNKNOWN",
                        sub.getExpiresAt())
        );

        return new AdminCancelSubscriptionResponse(
                true,
                subscriptionId,
                UserSubscription.Status.CANCELLED.name(),
                sub.getExpiresAt(),
                "구독이 취소되었습니다. 만료일까지 혜택이 유지됩니다."
        );
    }

    /**
     * 구독을 1주기 연장한다.
     *
     * <p>도메인 {@link SubscriptionService#extendSubscription(Long, String)}를 그대로 호출한다.</p>
     *
     * @param subscriptionId 연장할 구독 레코드 ID
     * @param request        연장 요청 DTO (adminNote nullable)
     * @return 연장 응답 DTO
     */
    public AdminExtendSubscriptionResponse extendSubscription(
            Long subscriptionId, AdminExtendSubscriptionRequest request
    ) {
        String adminNote = (request != null) ? request.adminNote() : null;
        log.info("[AdminPayment] 구독 연장 요청 — subscriptionId={}, adminNote={}",
                subscriptionId, adminNote);

        // 도메인 서비스 호출 — 반환 타입이 도메인 DTO 이므로 관리자 DTO 로 변환
        var domainResponse = subscriptionService.extendSubscription(subscriptionId, adminNote);

        // 감사 로그 — 구독 연장 (사용자에게 유리한 액션이지만 정책 남용 방지를 위해 기록)
        adminAuditService.log(
                AdminAuditService.ACTION_SUBSCRIPTION_EXTEND,
                AdminAuditService.TARGET_SUBSCRIPTION,
                String.valueOf(subscriptionId),
                String.format("구독 %d 관리자 연장 — 새 만료일: %s, adminNote: %s",
                        subscriptionId, domainResponse.newExpiresAt(), adminNote)
        );

        return new AdminExtendSubscriptionResponse(
                domainResponse.success(),
                domainResponse.newExpiresAt(),
                domainResponse.message()
        );
    }

    // ======================== 포인트 이력 ========================

    /**
     * 포인트 변동 이력을 최신순으로 페이징 조회한다 (2026-04-23 날짜 범위 확장).
     *
     * <p>userId 와 기간 범위를 선택적으로 조합해 필터링한다. 모든 파라미터가 null 이면
     * 전체 사용자의 전체 기간 이력을 반환한다.</p>
     *
     * @param userId    사용자 ID (nullable — 생략 시 전체)
     * @param fromDate  이력 생성일 시작 inclusive (nullable)
     * @param toDate    이력 생성일 종료 exclusive (nullable)
     * @param pageable  페이지 정보
     * @return 포인트 이력 페이지
     */
    public Page<PointHistoryItem> getPointHistories(
            String userId, LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable
    ) {
        log.debug("[AdminPayment] 포인트 이력 조회 — userId={}, from={}, to={}, page={}",
                userId, fromDate, toDate, pageable.getPageNumber());

        String userIdOrNull = (userId != null && !userId.isBlank()) ? userId : null;
        Page<PointsHistory> pageResult = adminPointsHistoryRepository.searchByFilters(
                userIdOrNull, fromDate, toDate, pageable
        );

        /* 닉네임/이메일 enrich — 페이지 크기 이내로만 단건 조회를 반복한다. */
        Map<String, User> userMap = fetchUserMap(pageResult.getContent().stream()
                .map(PointsHistory::getUserId).toList());

        return pageResult.map((h) -> toPointHistoryItem(h, userMap.get(h.getUserId())));
    }

    /**
     * 관리자 수동 포인트 지급/차감.
     *
     * <p>amount 양수 → 지급 (earnPoint), 음수 → 차감 (deductPoint), 0 불가.
     * 이 경로는 활동 리워드가 아니므로 등급 계산에 반영되지 않는다 (isActivityReward=false).</p>
     *
     * @param request 수동 지급/차감 요청 DTO
     * @return 처리 결과 응답 DTO
     * @throws BusinessException 금액 0 / 포인트 레코드 없음 / 잔액 부족
     */
    @Transactional
    public AdminManualPointResponse manualPoint(AdminManualPointRequest request) {
        log.info("[AdminPayment] 수동 포인트 변동 — userId={}, amount={}, reason={}",
                request.userId(), request.amount(), request.reason());

        int amount = request.amount();
        if (amount == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "0P 변동은 허용되지 않습니다.");
        }

        // 대상 사용자 존재 검증 — user_id 오타로 들어와도 "포인트 레코드 없음"(P002) 으로 떨어지면
        // 원인이 모호해지므로 여기서 명확한 USER_NOT_FOUND 를 먼저 던진다.
        if (!userMapper.existsById(request.userId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "사용자를 찾을 수 없습니다: " + request.userId());
        }

        /*
         * user_points 방어적 초기화.
         *
         * <p>회원가입 경로 외에 생성된 계정(예: DataInitializer 의 관리자 시드, 과거에 회원가입 후
         * 결제/활동이 없어 잔액 행이 만들어지지 않은 레거시 사용자 등)은 {@code user_points} 행이
         * 없을 수 있다. 이 상태에서 {@code earnPoint}/{@code deductPoint} 는 POINT_NOT_FOUND(P002)
         * 로 실패한다.</p>
         *
         * <p>{@link PointService#initializePoint(String, int)} 은 {@code REQUIRES_NEW} + {@code existsByUserId}
         * 1차 방어 + UNIQUE 제약 2차 방어 구조라 이미 존재하면 no-op, 없으면 기본 레코드를 생성한다.
         * 따라서 매 호출 시 선제적으로 실행해도 성능·정합성 모두 안전하다.</p>
         */
        pointService.initializePoint(request.userId(), 0);

        Integer newBalance;
        String gradeCode;

        if (amount > 0) {
            // 양수 → earnPoint (isActivityReward=false 로 등급 미반영)
            var earnResponse = pointService.earnPoint(
                    request.userId(),
                    amount,
                    "bonus",                     // point_type: bonus (관리자 지급은 등급 배율 미적용)
                    request.reason(),
                    "admin_manual_" + System.currentTimeMillis(),  // referenceId — 중복 지급 방지용 고유값
                    null,                        // actionType: null (리워드 외 변동)
                    false                        // isActivityReward: false
            );
            newBalance = earnResponse.balanceAfter();
            gradeCode = earnResponse.grade();
        } else {
            // 음수 → deductPoint (절댓값 전달)
            int absAmount = -amount;
            var deductResponse = pointService.deductPoint(
                    request.userId(),
                    absAmount,
                    "admin_manual_" + System.currentTimeMillis(),
                    request.reason()
            );
            newBalance = deductResponse.balanceAfter();
            // 차감 경로는 등급 조회가 없으므로 별도 조회
            var balanceResponse = pointService.getBalance(request.userId());
            gradeCode = balanceResponse.grade();
        }

        log.info("[AdminPayment] 수동 포인트 변동 완료 — userId={}, change={}, newBalance={}, grade={}",
                request.userId(), amount, newBalance, gradeCode);

        // 감사 로그 — 수동 포인트 지급/차감은 금전 이동이므로 필수 기록
        // targetType=USER (행위가 특정 사용자 잔액에 적용되므로)
        String afterSnapshot = String.format(
                "{\"delta\":%d,\"balanceAfter\":%d,\"grade\":\"%s\"}",
                amount, newBalance != null ? newBalance : 0,
                gradeCode != null ? gradeCode : ""
        );
        adminAuditService.log(
                AdminAuditService.ACTION_POINT_MANUAL,
                AdminAuditService.TARGET_USER,
                request.userId(),
                String.format("사용자 %s 수동 포인트 %s %dP (사유: %s)",
                        request.userId(),
                        amount > 0 ? "지급" : "차감",
                        Math.abs(amount),
                        request.reason()),
                null,
                afterSnapshot
        );

        String message = amount > 0
                ? String.format("%dP 지급 완료. 잔액: %dP", amount, newBalance)
                : String.format("%dP 차감 완료. 잔액: %dP", -amount, newBalance);

        return new AdminManualPointResponse(
                true,
                request.userId(),
                amount,
                newBalance,
                gradeCode,
                message
        );
    }

    // ======================== 포인트 아이템 ========================

    /**
     * 포인트 아이템 전체 목록을 가격 오름차순으로 조회한다.
     *
     * <p>관리자 화면에서는 비활성 아이템도 함께 표시해야 하므로
     * {@code PointItemRepository.findAll()} 기반으로 변환 후 정렬한다.</p>
     *
     * @return 포인트 아이템 응답 리스트
     */
    public java.util.List<PointItemResponse> getPointItems() {
        log.debug("[AdminPayment] 포인트 아이템 목록 조회");
        return pointItemRepository.findAll().stream()
                // 활성 여부와 무관하게 전체 노출, 가격 오름차순 정렬
                .sorted(java.util.Comparator.comparing(PointItem::getItemPrice))
                .map(this::toPointItemResponse)
                .toList();
    }

    /**
     * 신규 포인트 아이템을 등록한다.
     *
     * @param request 등록 요청 DTO
     * @return 등록된 아이템 응답 DTO
     */
    @Transactional
    public PointItemResponse createPointItem(PointItemCreateRequest request) {
        log.info("[AdminPayment] 포인트 아이템 등록 — name={}, price={}",
                request.itemName(), request.itemPrice());

        PointItem item = PointItem.builder()
                .itemName(request.itemName())
                .itemDescription(request.itemDescription())
                .itemPrice(request.itemPrice())
                .itemCategory(request.itemCategory() != null ? request.itemCategory() : "general")
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();

        PointItem saved = pointItemRepository.save(item);
        return toPointItemResponse(saved);
    }

    /**
     * 기존 포인트 아이템을 수정한다.
     *
     * <p>{@link PointItem} 엔티티는 @Setter 가 없으므로 업데이트는 신규 엔티티를 빌드하여
     * 기존 PK 를 유지한 채 저장하는 방식(merge)을 사용한다.</p>
     *
     * @param itemId  수정할 아이템 ID
     * @param request 수정 요청 DTO
     * @return 수정된 아이템 응답 DTO
     * @throws BusinessException 아이템 미발견 시
     */
    @Transactional
    public PointItemResponse updatePointItem(Long itemId, PointItemUpdateRequest request) {
        log.info("[AdminPayment] 포인트 아이템 수정 — itemId={}", itemId);

        // 존재 여부 확인 (상세 조회는 전체 목록 포함 — 비활성도 허용)
        PointItem existing = pointItemRepository.findById(itemId)
                .orElseThrow(() -> {
                    log.warn("[AdminPayment] 포인트 아이템 수정 실패 — 미발견: itemId={}", itemId);
                    return new BusinessException(ErrorCode.ITEM_NOT_FOUND,
                            "포인트 아이템을 찾을 수 없습니다: id=" + itemId);
                });

        // @Getter 전용 엔티티는 setter 가 없으므로 Builder 로 병합 후 save (JPA merge 패턴)
        PointItem merged = PointItem.builder()
                .pointItemId(existing.getPointItemId())
                .itemName(request.itemName())
                .itemDescription(request.itemDescription())
                .itemPrice(request.itemPrice())
                .itemCategory(request.itemCategory() != null ? request.itemCategory() : "general")
                .isActive(request.isActive())
                .build();

        PointItem saved = pointItemRepository.save(merged);
        return toPointItemResponse(saved);
    }

    // ======================== DTO 변환 ========================

    /**
     * {@link PaymentOrder} 엔티티 → {@link PaymentOrderSummary} 응답 DTO (nickname/email 미포함).
     *
     * @deprecated enrich 버전 {@link #toOrderSummary(PaymentOrder, User)} 를 사용하라.
     */
    @Deprecated
    private PaymentOrderSummary toOrderSummary(PaymentOrder order) {
        return toOrderSummary(order, null);
    }

    /**
     * {@link PaymentOrder} → {@link PaymentOrderSummary} (user 정보로 enrich).
     * 2026-04-14 추가 — 관리자 화면에서 UUID 대신 닉네임/이메일을 함께 보여주기 위함.
     */
    private PaymentOrderSummary toOrderSummary(PaymentOrder order, User user) {
        return new PaymentOrderSummary(
                order.getPaymentOrderId(),
                order.getUserId(),
                user != null ? user.getEmail() : null,
                user != null ? user.getNickname() : null,
                order.getOrderType().name(),
                order.getAmount(),
                order.getPointsAmount(),
                order.getStatus().name(),
                order.getPgProvider(),
                order.getFailedReason(),
                order.getCreatedAt(),
                order.getCompletedAt()
        );
    }

    /**
     * {@link PaymentOrder} 엔티티 → {@link PaymentOrderDetail} 응답 DTO (상세).
     */
    private PaymentOrderDetail toOrderDetail(PaymentOrder order) {
        return new PaymentOrderDetail(
                order.getPaymentOrderId(),
                order.getUserId(),
                order.getOrderType().name(),
                order.getAmount(),
                order.getPointsAmount(),
                order.getStatus().name(),
                order.getPgProvider(),
                order.getPgTransactionId(),
                order.getCardInfo(),
                order.getReceiptUrl(),
                order.getFailedReason(),
                order.getRefundReason(),
                order.getRefundAmount(),
                order.getRefundedAt(),
                order.getCreatedAt(),
                order.getCompletedAt()
        );
    }

    /**
     * {@link UserSubscription} → {@link SubscriptionSummary} (user 정보 미포함, 시그니처 호환).
     *
     * @deprecated enrich 버전 {@link #toSubscriptionSummary(UserSubscription, User)} 를 사용하라.
     */
    @Deprecated
    private SubscriptionSummary toSubscriptionSummary(UserSubscription sub) {
        return toSubscriptionSummary(sub, null);
    }

    /**
     * {@link UserSubscription} → {@link SubscriptionSummary} (user 정보로 enrich).
     *
     * <p>plan 은 반드시 JOIN FETCH 로 로딩된 상태여야 한다 (LazyInitializationException 방지).</p>
     */
    private SubscriptionSummary toSubscriptionSummary(UserSubscription sub, User user) {
        SubscriptionPlan plan = sub.getPlan();
        return new SubscriptionSummary(
                sub.getUserSubscriptionId(),
                sub.getUserId(),
                user != null ? user.getEmail() : null,
                user != null ? user.getNickname() : null,
                plan.getPlanCode(),
                plan.getName(),
                plan.getPeriodType().name(),
                plan.getPrice(),
                sub.getStatus().name(),
                sub.getAutoRenew(),
                sub.getRemainingAiBonus(),
                sub.getStartedAt(),
                sub.getExpiresAt(),
                sub.getCancelledAt()
        );
    }

    /**
     * userId 리스트를 받아 userId → User Map 을 반환한다 (nullable 값 허용).
     *
     * <p>N+1 방지를 위해 중복을 제거하고 존재하는 사용자만 맵에 담는다.
     * UserMapper 가 배치 조회 메서드를 제공하지 않으므로 단건 호출을 반복한다 —
     * 관리자 페이지의 최대 페이지 크기 (20~100) 이내에서 충분히 수용 가능.</p>
     */
    private Map<String, User> fetchUserMap(List<String> userIds) {
        Map<String, User> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return map;
        for (String uid : userIds) {
            if (uid == null || map.containsKey(uid)) continue;
            try {
                User u = userMapper.findById(uid);
                if (u != null) map.put(uid, u);
            } catch (Exception e) {
                /* 개별 조회 실패는 enrichment 만 비워둔다 — 메인 응답에 영향 없음 */
                log.warn("[AdminPayment] 사용자 조회 실패 — userId={}, err={}", uid, e.getMessage());
            }
        }
        return map;
    }

    /**
     * {@link PointsHistory} → {@link PointHistoryItem} (user 정보 미포함, 시그니처 호환).
     *
     * @deprecated enrich 버전 {@link #toPointHistoryItem(PointsHistory, User)} 를 사용하라.
     */
    @Deprecated
    private PointHistoryItem toPointHistoryItem(PointsHistory history) {
        return toPointHistoryItem(history, null);
    }

    /** {@link PointsHistory} → {@link PointHistoryItem} (user 정보로 enrich, 2026-04-14 추가) */
    private PointHistoryItem toPointHistoryItem(PointsHistory history, User user) {
        return new PointHistoryItem(
                history.getPointsHistoryId(),
                history.getUserId(),
                user != null ? user.getEmail() : null,
                user != null ? user.getNickname() : null,
                history.getPointChange(),
                history.getPointAfter(),
                history.getPointType(),
                history.getActionType(),
                history.getDescription(),
                history.getReferenceId(),
                history.getCreatedAt()
        );
    }

    /**
     * {@link PointItem} 엔티티 → {@link PointItemResponse} 응답 DTO.
     */
    private PointItemResponse toPointItemResponse(PointItem item) {
        return new PointItemResponse(
                item.getPointItemId(),
                item.getItemName(),
                item.getItemDescription(),
                item.getItemPrice(),
                item.getItemCategory(),
                item.getIsActive(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    // ======================== 내부 헬퍼 ========================

    /**
     * 감사 로그 JSON 스냅샷 구성 시 사용자 입력 문자열(예: 환불 사유)을
     * 큰따옴표/역슬래시 이스케이프하여 JSON 파싱 오류를 방지한다.
     *
     * <p>감사 로그는 Jackson 직렬화를 거치지 않고 문자열 조립으로 JSON 을 만들기 때문에
     * 사용자가 입력한 {@code "} 나 {@code \} 가 그대로 삽입되면 파싱 실패가 발생한다.
     * 라이브러리 의존을 늘리지 않기 위해 최소 이스케이프(백슬래시, 큰따옴표, 개행)만 수행한다.</p>
     *
     * @param raw 원본 문자열 (nullable)
     * @return JSON-safe 문자열 (null → 빈 문자열)
     */
    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
