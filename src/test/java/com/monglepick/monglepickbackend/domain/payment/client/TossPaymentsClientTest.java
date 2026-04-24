package com.monglepick.monglepickbackend.domain.payment.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TossPaymentsClient 단위 테스트.
 *
 * <p>HTTP 호출 부분은 Toss 운영 서버에 의존하므로 단위 테스트에서는 검증하지 않는다.
 * 대신 다음 로직을 격리해서 검증한다:</p>
 * <ol>
 *   <li>{@link TossApiException} 필드 보존 (statusCode/tossCode/tossMessage/rawBody)</li>
 *   <li>{@link TossPaymentsClient.TossErrorBody} record 동작</li>
 *   <li>{@link TossPaymentsClient.TossConfirmResponse} record 동작</li>
 * </ol>
 *
 * <h3>웹훅 서명 검증 테스트 제거 (2026-04-24)</h3>
 * <p>Toss Payments 공식 문서상 {@code PAYMENT_STATUS_CHANGED} 이벤트에는 HMAC 서명 헤더나 별도
 * 웹훅 시크릿이 존재하지 않는다. 위변조 방어는 {@code orderId} 기반 {@code getPayment()} 재조회로
 * 일원화되었으므로 기존 {@code verifyWebhookSignature} 메서드와 관련 테스트 4건을 모두 제거했다.
 * 웹훅 처리 동작은 {@code PaymentServiceRefundAndSyncTest#processWebhook*} 에서 검증한다.</p>
 *
 * <h3>HTTP 호출 통합 테스트</h3>
 * <p>실제 HTTP 호출 검증은 {@code @SpringBootTest} + WireMock 또는 Toss 샌드박스 키로 별도 구성한다.
 * 본 단위 테스트는 빌드 파이프라인에서 빠르게 실행 가능한 부분에 집중한다.</p>
 */
class TossPaymentsClientTest {

    // ============================================================
    // TossApiException 필드 보존 검증
    // ============================================================

    @Nested
    @DisplayName("TossApiException")
    class TossApiExceptionTest {

        @Test
        @DisplayName("4xx 에러 응답을 statusCode/tossCode/tossMessage/rawBody 로 정확히 보존한다")
        void preservesAllFields() {
            String rawBody = "{\"code\":\"INVALID_REQUEST\",\"message\":\"잘못된 요청\"}";
            TossApiException exception = new TossApiException(400, "INVALID_REQUEST", "잘못된 요청", rawBody);

            assertEquals(400, exception.getStatusCode());
            assertEquals("INVALID_REQUEST", exception.getTossCode());
            assertEquals("잘못된 요청", exception.getTossMessage());
            assertEquals(rawBody, exception.getRawBody());
            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("400"),
                    "예외 메시지에 HTTP 상태 코드가 포함되어야 함");
            assertTrue(exception.getMessage().contains("INVALID_REQUEST"),
                    "예외 메시지에 Toss 에러 코드가 포함되어야 함");
        }

        @Test
        @DisplayName("5xx 에러 응답도 동일한 구조로 처리된다")
        void handles5xxResponse() {
            TossApiException exception = new TossApiException(
                    500, "INTERNAL_SERVER_ERROR", "Toss 서버 일시 오류", "{}"
            );

            assertEquals(500, exception.getStatusCode());
            assertEquals("INTERNAL_SERVER_ERROR", exception.getTossCode());
        }

        @Test
        @DisplayName("rawBody가 비어있어도 예외 생성에 실패하지 않는다")
        void handlesEmptyRawBody() {
            TossApiException exception = new TossApiException(503, "UNKNOWN", "서비스 사용 불가", "");

            assertEquals("", exception.getRawBody());
            assertNotNull(exception.getMessage());
        }
    }

    // ============================================================
    // record DTO 검증
    // ============================================================

    @Nested
    @DisplayName("TossConfirmResponse record")
    class TossConfirmResponseTest {

        @Test
        @DisplayName("모든 필드를 정상 보존한다")
        void preservesAllFields() {
            TossPaymentsClient.TossConfirmResponse response =
                    new TossPaymentsClient.TossConfirmResponse(
                            "test_payment_key_xyz",
                            "order_uuid_123",
                            "DONE",
                            3900,
                            "카드"
                    );

            assertEquals("test_payment_key_xyz", response.paymentKey());
            assertEquals("order_uuid_123", response.orderId());
            assertEquals("DONE", response.status());
            assertEquals(3900, response.totalAmount());
            assertEquals("카드", response.method());
        }
    }

    @Nested
    @DisplayName("TossErrorBody record")
    class TossErrorBodyTest {

        @Test
        @DisplayName("code/message 필드를 정상 보존한다")
        void preservesAllFields() {
            TossPaymentsClient.TossErrorBody body =
                    new TossPaymentsClient.TossErrorBody("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제입니다");

            assertEquals("ALREADY_PROCESSED_PAYMENT", body.code());
            assertEquals("이미 처리된 결제입니다", body.message());
        }
    }
}
