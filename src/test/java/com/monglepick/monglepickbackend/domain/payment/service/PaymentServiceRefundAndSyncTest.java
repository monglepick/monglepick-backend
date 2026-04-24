package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.client.TossPaymentsClient;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.RefundResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.PointPackPriceRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentService 의 환불({@link PaymentService#refundOrder}) + PG 재조회({@link PaymentService#syncFromPg})
 * 단위 테스트 (2026-04-24 추가).
 *
 * <p><b>배경</b>: Toss 콘솔에서 직접 취소하거나 웹훅이 유실되면 PG 상태는 CANCELED 인데 DB 는 COMPLETED 로
 * 남는다. 기존 {@code refundOrder} 는 {@code cancelPayment} 를 재호출해 ALREADY_CANCELED 500 을 유발했고,
 * 그 결과 트랜잭션 롤백으로 포인트 회수까지 되돌려져 유저는 환불+포인트 둘 다 받는 부당 이득 케이스가 발생했다.
 * 본 테스트는 다음 두 가지 근본 해결책을 검증한다.</p>
 *
 * <ul>
 *   <li>Part A — {@code refundOrder} 내부에 {@code getPayment} 선조회를 삽입해 PG 가 이미 취소됐으면
 *       {@code cancelPayment} 호출을 건너뛰고 DB 동기화만 진행한다.</li>
 *   <li>Part C — 관리자 전용 {@code syncFromPg} 를 신설해 Toss 재호출 없이 DB 만 PG 에 맞춘다.</li>
 * </ul>
 *
 * <p>DB 의존성을 제거하기 위해 Mockito 기반 순수 단위 테스트 패턴을 사용한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceRefundAndSyncTest {

    // ── 실제로 호출되는 의존성 ──
    @Mock private PaymentOrderRepository orderRepository;
    @Mock private TossPaymentsClient tossClient;
    @Mock private PointService pointService;

    // ── 호출되지 않지만 @RequiredArgsConstructor 가 요구하는 의존성 (null 방지 목적) ──
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PointPackPriceRepository pointPackPriceRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @Mock private PaymentCompensationService compensationService;
    @Mock private PaymentConfirmProcessor confirmProcessor;

    @InjectMocks
    private PaymentService paymentService;

    // ──────────────────────────────────────────────
    // 테스트 헬퍼
    // ──────────────────────────────────────────────

    /** POINT_PACK + COMPLETED 상태의 테스트용 주문 생성. */
    private PaymentOrder completedPointPackOrder(String orderId, String userId, int amount, int points, String paymentKey) {
        return PaymentOrder.builder()
                .paymentOrderId(orderId)
                .userId(userId)
                .orderType(PaymentOrder.OrderType.POINT_PACK)
                .amount(amount)
                .pointsAmount(points)
                .idempotencyKey(orderId + "_idem")
                .status(PaymentOrder.OrderStatus.COMPLETED)
                .pgTransactionId(paymentKey)
                .pgProvider("TOSS")
                .build();
    }

    /** Toss getPayment 응답 스텁 생성. */
    private TossPaymentsClient.TossConfirmResponse tossResponse(String paymentKey, String orderId, String status, int amount) {
        return new TossPaymentsClient.TossConfirmResponse(paymentKey, orderId, status, amount, "카드");
    }

    // ══════════════════════════════════════════════════════════════
    // Part A — refundOrder Toss 선조회 삽입 검증
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("refundOrder — Toss 선조회 동작")
    class RefundOrderToss선조회Test {

        @Test
        @DisplayName("[근본 해결 A] Toss 이미 CANCELED 이면 cancelPayment 호출 생략하고 DB 동기화만 진행")
        void refundOrder_WhenTossAlreadyCanceled_SkipsCancelAndSyncsDbOnly() {
            // Given — DB 는 COMPLETED 인데 Toss 에는 이미 CANCELED 로 반영된 주문
            String orderId = "order-1";
            String userId = "user-1";
            String paymentKey = "pay-toss-1";
            PaymentOrder order = completedPointPackOrder(orderId, userId, 10_000, 1_000, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "CANCELED", 10_000));

            // When
            RefundResponse response = paymentService.refundOrder(orderId, userId, "유저 환불 요청");

            // Then — cancelPayment 는 절대 호출되지 않아야 한다 (핵심 회귀 포인트)
            verify(tossClient, never()).cancelPayment(anyString(), anyString());
            verify(tossClient, never()).cancelPayment(anyString(), anyString(), anyInt());

            // 포인트는 회수되어야 한다
            verify(pointService).deductPoint(eq(userId), eq(1_000), eq(orderId + "_refund"), anyString());

            // DB 상태가 REFUNDED 로 변경됐는지 — 환불 사유에 [PG 선취소] 접두가 붙는다
            assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.REFUNDED);
            assertThat(order.getRefundReason()).startsWith("[PG 선취소]");
            assertThat(response.success()).isTrue();
            assertThat(response.refundAmount()).isEqualTo(10_000);
        }

        @Test
        @DisplayName("정상 경로 회귀: Toss DONE 이면 종래대로 cancelPayment 를 호출하고 DB 동기화")
        void refundOrder_WhenTossDone_CallsCancelPaymentAsUsual() {
            // Given — Toss 가 아직 DONE 상태인 정상 환불 흐름
            String orderId = "order-2";
            String userId = "user-2";
            String paymentKey = "pay-toss-2";
            PaymentOrder order = completedPointPackOrder(orderId, userId, 5_000, 500, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "DONE", 5_000));

            // When
            RefundResponse response = paymentService.refundOrder(orderId, userId, "환불");

            // Then — cancelPayment 1회 호출 + DB REFUNDED
            verify(tossClient, times(1)).cancelPayment(eq(paymentKey), anyString());
            assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.REFUNDED);
            assertThat(order.getRefundReason()).doesNotContain("[PG 선취소]");
            assertThat(response.success()).isTrue();
        }

        @Test
        @DisplayName("선조회 실패는 일반 플로우로 진행 (Toss 일시 장애 대응)")
        void refundOrder_WhenGetPaymentThrows_FallsBackToNormalCancelFlow() {
            // Given — Toss 조회가 네트워크 오류로 실패하는 상황
            String orderId = "order-3";
            String userId = "user-3";
            String paymentKey = "pay-toss-3";
            PaymentOrder order = completedPointPackOrder(orderId, userId, 3_000, 300, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenThrow(new RuntimeException("Toss 일시 장애"));

            // When
            paymentService.refundOrder(orderId, userId, "환불");

            // Then — 선조회 실패해도 cancelPayment 는 호출되어야 한다 (종래 동작 유지)
            verify(tossClient, times(1)).cancelPayment(eq(paymentKey), anyString());
            assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.REFUNDED);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Part C — syncFromPg (관리자 PG 재조회 동기화)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("syncFromPg — PG 재조회 동기화 결과 분기")
    class SyncFromPgTest {

        @Test
        @DisplayName("[SYNCED] PG CANCELED + DB COMPLETED + POINT_PACK → 포인트 회수 + DB REFUNDED")
        void syncFromPg_WhenPgCanceledAndDbCompleted_SyncsAndRecoversPoints() {
            // Given
            String orderId = "order-sync-1";
            String userId = "user-sync-1";
            String paymentKey = "pay-sync-1";
            PaymentOrder order = completedPointPackOrder(orderId, userId, 20_000, 2_000, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "CANCELED", 20_000));

            // When
            PaymentService.PgSyncResult result = paymentService.syncFromPg(orderId);

            // Then — Toss 재취소는 절대 호출되지 않는다 (readonly 조회만)
            verify(tossClient, never()).cancelPayment(anyString(), anyString());
            verify(tossClient, never()).cancelPayment(anyString(), anyString(), anyInt());

            // 포인트 회수 + DB 상태 변경
            verify(pointService).deductPoint(eq(userId), eq(2_000), eq(orderId + "_refund"), anyString());
            assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.REFUNDED);

            // 결과 DTO 검증
            assertThat(result.result()).isEqualTo("SYNCED");
            assertThat(result.dbStatus()).isEqualTo("REFUNDED");
            assertThat(result.pgStatus()).isEqualTo("CANCELED");
            assertThat(result.pointsRecovered()).isEqualTo(2_000);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.orderType()).isEqualTo("POINT_PACK");
        }

        @Test
        @DisplayName("[NO_CHANGE] PG CANCELED + DB REFUNDED → 이미 일치 (변경 없음)")
        void syncFromPg_WhenAlreadyRefunded_ReturnsNoChange() {
            // Given — DB 와 PG 가 이미 모두 취소된 상태
            String orderId = "order-sync-2";
            String paymentKey = "pay-sync-2";
            PaymentOrder order = completedPointPackOrder(orderId, "user", 10_000, 1_000, paymentKey);
            order.refund("이전 환불", 10_000); // DB 를 REFUNDED 로 만든다

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "CANCELED", 10_000));

            // When
            PaymentService.PgSyncResult result = paymentService.syncFromPg(orderId);

            // Then — 포인트 회수/DB 변경 없음
            verify(pointService, never()).deductPoint(anyString(), anyInt(), anyString(), anyString());
            assertThat(result.result()).isEqualTo("NO_CHANGE");
            assertThat(result.dbStatus()).isEqualTo("REFUNDED");
            assertThat(result.pointsRecovered()).isZero();
        }

        @Test
        @DisplayName("[NO_CHANGE] PG DONE + DB COMPLETED → 이미 일치 (변경 없음)")
        void syncFromPg_WhenBothDoneAndCompleted_ReturnsNoChange() {
            // Given
            String orderId = "order-sync-3";
            String paymentKey = "pay-sync-3";
            PaymentOrder order = completedPointPackOrder(orderId, "user", 10_000, 1_000, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "DONE", 10_000));

            // When
            PaymentService.PgSyncResult result = paymentService.syncFromPg(orderId);

            // Then — 아무 변경 없음
            verify(pointService, never()).deductPoint(anyString(), anyInt(), anyString(), anyString());
            assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.COMPLETED);
            assertThat(result.result()).isEqualTo("NO_CHANGE");
        }

        @Test
        @DisplayName("[MISMATCH] PG DONE + DB REFUNDED → 자동 동기화 규칙 외, 수동 검토 유도")
        void syncFromPg_WhenPgDoneButDbRefunded_ReturnsMismatch() {
            // Given — DB 는 환불 처리됐는데 PG 가 여전히 DONE — 드물지만 수동 검토 필요한 의심 케이스
            String orderId = "order-sync-4";
            String paymentKey = "pay-sync-4";
            PaymentOrder order = completedPointPackOrder(orderId, "user", 10_000, 1_000, paymentKey);
            order.refund("이전 환불", 10_000);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "DONE", 10_000));

            // When
            PaymentService.PgSyncResult result = paymentService.syncFromPg(orderId);

            // Then — 자동 동기화하지 않음
            verify(pointService, never()).deductPoint(anyString(), anyInt(), anyString(), anyString());
            assertThat(result.result()).isEqualTo("MISMATCH");
            assertThat(result.dbStatus()).isEqualTo("REFUNDED");
            assertThat(result.pgStatus()).isEqualTo("DONE");
        }

        @Test
        @DisplayName("[ERROR] 주문 미발견 → ORDER_NOT_FOUND")
        void syncFromPg_WhenOrderNotFound_ThrowsOrderNotFound() {
            // Given
            when(orderRepository.findByPaymentOrderIdForUpdate(anyString()))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentService.syncFromPg("nonexistent"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("[ERROR] Toss 조회 실패 → PAYMENT_FAILED")
        void syncFromPg_WhenTossGetFails_ThrowsPaymentFailed() {
            // Given
            String orderId = "order-sync-5";
            String paymentKey = "pay-sync-5";
            PaymentOrder order = completedPointPackOrder(orderId, "user", 10_000, 1_000, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenThrow(new RuntimeException("Toss 일시 장애"));

            // When / Then
            assertThatThrownBy(() -> paymentService.syncFromPg(orderId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_FAILED);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Part B — processWebhook (Toss 웹훅 재설계: 서명 검증 제거 + syncFromPg 위임)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processWebhook — Toss 웹훅 수신 및 syncFromPg 위임")
    class ProcessWebhookTest {

        @Test
        @DisplayName("PAYMENT_STATUS_CHANGED + orderId 이면 syncFromPg 로 위임되어 DB 동기화된다")
        void processWebhook_WhenPaymentStatusChanged_DelegatesToSyncFromPg() {
            // Given — Toss 콘솔에서 취소된 상태의 웹훅이 도착
            String orderId = "order-wh-1";
            String paymentKey = "pay-wh-1";
            PaymentOrder order = completedPointPackOrder(orderId, "user-wh", 10_000, 1_000, paymentKey);

            when(orderRepository.findByPaymentOrderIdForUpdate(orderId))
                    .thenReturn(Optional.of(order));
            when(tossClient.getPayment(paymentKey))
                    .thenReturn(tossResponse(paymentKey, orderId, "CANCELED", 10_000));

            String webhookBody = String.format(
                    "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"%s\",\"status\":\"CANCELED\"}}",
                    orderId);

            // When
            paymentService.processWebhook(webhookBody);

            // Then — syncFromPg 경로를 탔으므로 cancelPayment 호출 0회 + DB REFUNDED + 포인트 회수
            verify(tossClient, never()).cancelPayment(anyString(), anyString());
            verify(tossClient, never()).cancelPayment(anyString(), anyString(), anyInt());
            verify(pointService).deductPoint(eq("user-wh"), eq(1_000), eq(orderId + "_refund"), anyString());
            assertThat(order.getStatus()).isEqualTo(PaymentOrder.OrderStatus.REFUNDED);
        }

        @Test
        @DisplayName("미구독 이벤트 타입은 무시한다 (DEPOSIT_CALLBACK 등)")
        void processWebhook_WhenUnsupportedEvent_Ignored() {
            // Given
            String webhookBody = "{\"eventType\":\"DEPOSIT_CALLBACK\",\"data\":{\"orderId\":\"x\"}}";

            // When
            paymentService.processWebhook(webhookBody);

            // Then — 아무 서비스 호출도 일어나지 않는다
            verify(orderRepository, never()).findByPaymentOrderIdForUpdate(anyString());
            verify(tossClient, never()).getPayment(anyString());
        }

        @Test
        @DisplayName("data.orderId 누락 시 조용히 종료 (200 OK 유지)")
        void processWebhook_WhenOrderIdMissing_SilentlyReturns() {
            // Given — data 블록은 있으나 orderId 가 누락된 기형 페이로드
            String webhookBody = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"status\":\"DONE\"}}";

            // When
            paymentService.processWebhook(webhookBody);

            // Then — 예외 전파 없이 정상 종료
            verify(orderRepository, never()).findByPaymentOrderIdForUpdate(anyString());
        }

        @Test
        @DisplayName("파싱 실패(잘못된 JSON) 시 예외를 삼켜 200 OK 유지 — Toss 재시도 폭주 방지")
        void processWebhook_WhenInvalidJson_SwallowsException() {
            // Given — JSON 이 아닌 문자열
            String malformed = "this is not json";

            // When / Then — 예외가 밖으로 전파되지 않아야 한다
            paymentService.processWebhook(malformed);
            verify(orderRepository, never()).findByPaymentOrderIdForUpdate(anyString());
        }

        @Test
        @DisplayName("syncFromPg 내부 예외(주문 미발견 등)도 삼켜 200 OK 유지")
        void processWebhook_WhenSyncFromPgThrows_SwallowsException() {
            // Given — 다른 가맹점 주문이거나 이미 삭제된 건이 웹훅으로 온 시나리오
            when(orderRepository.findByPaymentOrderIdForUpdate(anyString()))
                    .thenReturn(Optional.empty()); // syncFromPg 가 ORDER_NOT_FOUND throw

            String webhookBody = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"orphan-order\"}}";

            // When / Then — 예외가 외부로 전파되면 Toss 가 재시도함. 반드시 삼켜야 함.
            paymentService.processWebhook(webhookBody);
        }
    }
}
