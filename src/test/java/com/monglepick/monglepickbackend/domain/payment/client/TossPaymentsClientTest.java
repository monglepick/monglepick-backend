package com.monglepick.monglepickbackend.domain.payment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TossPaymentsClient 단위 테스트.
 *
 * <p>HTTP 호출 부분은 Toss 운영 서버에 의존하므로 단위 테스트에서는 검증하지 않는다.
 * 대신 다음 로직을 격리해서 검증한다:</p>
 * <ol>
 *   <li>{@link TossApiException} 필드 보존 (statusCode/tossCode/tossMessage/rawBody)</li>
 *   <li>웹훅 서명 검증 활성화/비활성화 동작</li>
 *   <li>웹훅 서명 검증 알고리즘 정합성 (HMAC-SHA256 + Base64)</li>
 *   <li>{@link TossPaymentsClient.TossErrorBody} record 동작</li>
 *   <li>{@link TossPaymentsClient.TossConfirmResponse} record 동작</li>
 * </ol>
 *
 * <h3>HTTP 호출 통합 테스트</h3>
 * <p>실제 HTTP 호출 검증은 {@code @SpringBootTest} + WireMock 또는 Toss 샌드박스 키로 별도 구성한다.
 * 본 단위 테스트는 빌드 파이프라인에서 빠르게 실행 가능한 부분에 집중한다.</p>
 */
class TossPaymentsClientTest {

    /** 테스트용 시크릿 키 (Toss 샌드박스 형식과 유사) */
    private static final String TEST_SECRET_KEY = "test_sk_dummy_secret_key_value";

    /** 테스트용 웹훅 시크릿 (HMAC-SHA256 키) */
    private static final String TEST_WEBHOOK_SECRET = "test_webhook_secret_for_unit_test";

    private TossPaymentsClient clientWithWebhook;
    private TossPaymentsClient clientWithoutWebhook;

    @BeforeEach
    void setUp() {
        // 시크릿 키 + 베이스 URL + 웹훅 시크릿이 모두 있는 클라이언트
        clientWithWebhook = new TossPaymentsClient(
                TEST_SECRET_KEY,
                "https://api.tosspayments.com/v1",
                TEST_WEBHOOK_SECRET
        );
        // 웹훅 시크릿이 없는 클라이언트 (개발 환경 시뮬레이션)
        clientWithoutWebhook = new TossPaymentsClient(
                TEST_SECRET_KEY,
                "https://api.tosspayments.com/v1",
                ""
        );
    }

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
    // 웹훅 서명 검증 — 시크릿 미설정 시 보안상 거부
    // ============================================================
    //
    // [2026-04-10 보안 수정 — CLAUDE.md CRITICAL-2]
    //   기존 TossPaymentsClient 는 webhook-secret 미설정 시 return true 로 모든 웹훅을
    //   통과시켰으나, 운영환경에서 환경변수가 누락되면 인증되지 않은 웹훅이 수용되는
    //   보안 취약점을 초래했다. 이제 webhookSecret 이 비어있으면 항상 false 를 반환한다.
    //   본 테스트 케이스도 "미설정 = 거부" 정책으로 갱신.

    @Nested
    @DisplayName("verifyWebhookSignature — 시크릿 미설정 시 보안상 거부")
    class WebhookSignatureDisabled {

        @Test
        @DisplayName("webhook-secret 미설정 시 모든 요청을 거부한다 (보안)")
        void rejectsAllRequestsWhenSecretMissing() {
            assertFalse(clientWithoutWebhook.verifyWebhookSignature(
                    "any body", "any signature"));
        }

        @Test
        @DisplayName("webhook-secret 미설정 시 서명이 null/빈 문자열이어도 거부한다")
        void rejectsNullSignatureWhenSecretMissing() {
            assertFalse(clientWithoutWebhook.verifyWebhookSignature("any body", null));
            assertFalse(clientWithoutWebhook.verifyWebhookSignature("any body", ""));
        }
    }

    // ============================================================
    // 웹훅 서명 검증 — 활성화 케이스
    // ============================================================

    @Nested
    @DisplayName("verifyWebhookSignature — 검증 활성화 (운영 환경)")
    class WebhookSignatureEnabled {

        @Test
        @DisplayName("올바른 HMAC-SHA256 + Base64 서명을 통과시킨다")
        void verifiesCorrectSignature() throws Exception {
            String body = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"status\":\"DONE\"}}";
            String validSignature = computeHmacSha256(body, TEST_WEBHOOK_SECRET);

            assertTrue(clientWithWebhook.verifyWebhookSignature(body, validSignature),
                    "올바른 HMAC-SHA256 서명은 통과해야 함");
        }

        @Test
        @DisplayName("잘못된 서명을 거부한다")
        void rejectsInvalidSignature() {
            String body = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}";
            String wrongSignature = "definitely-wrong-signature";

            assertFalse(clientWithWebhook.verifyWebhookSignature(body, wrongSignature),
                    "잘못된 서명은 거부해야 함");
        }

        @Test
        @DisplayName("서명 헤더 누락 시 거부한다 (검증 활성화 상태)")
        void rejectsMissingSignature() {
            String body = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}";

            assertFalse(clientWithWebhook.verifyWebhookSignature(body, null));
            assertFalse(clientWithWebhook.verifyWebhookSignature(body, ""));
            assertFalse(clientWithWebhook.verifyWebhookSignature(body, "  "));
        }

        @Test
        @DisplayName("body 가 변조되면 동일 서명으로 검증에 실패한다 (서명 위변조 방어)")
        void rejectsTamperedBody() throws Exception {
            String originalBody = "{\"amount\":1000}";
            String tamperedBody = "{\"amount\":99999}";
            String signatureForOriginal = computeHmacSha256(originalBody, TEST_WEBHOOK_SECRET);

            // 원본 body 는 통과
            assertTrue(clientWithWebhook.verifyWebhookSignature(originalBody, signatureForOriginal));
            // 변조된 body 는 동일 서명으로 검증 실패
            assertFalse(clientWithWebhook.verifyWebhookSignature(tamperedBody, signatureForOriginal),
                    "body 가 변조되면 동일 서명으로 검증 실패해야 함 (위변조 방어)");
        }

        /**
         * 테스트 헬퍼 — HMAC-SHA256 + Base64 서명 생성.
         *
         * <p>{@link TossPaymentsClient#verifyWebhookSignature} 가 사용하는 알고리즘과 동일한 방식으로
         * 서명을 생성하여 검증 로직을 테스트한다.</p>
         */
        private String computeHmacSha256(String body, String secret) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
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
