package com.monglepick.monglepickbackend.domain.payment.client;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toss Payments REST API 클라이언트.
 *
 * <p>Toss Payments 결제 승인/취소/조회 API를 호출하는 HTTP 클라이언트.
 * Spring Boot 4의 {@link RestClient}를 사용하여 동기 HTTP 요청을 수행한다.</p>
 *
 * <h3>인증 방식</h3>
 * <p>Toss Payments API는 HTTP Basic 인증을 사용한다.
 * Secret Key를 Base64 인코딩하여 {@code Authorization: Basic {encoded}} 헤더로 전송한다.
 * Secret Key 뒤에 콜론(:)을 붙여 인코딩하는 것이 Toss 규격이다.</p>
 *
 * <h3>설정값 (application.yml)</h3>
 * <ul>
 *   <li>{@code toss.payments.secret-key} — Toss Payments 시크릿 키 (서버용, 외부 노출 금지)</li>
 *   <li>{@code toss.payments.base-url} — Toss API 베이스 URL (기본: https://api.tosspayments.com/v1)</li>
 * </ul>
 *
 * <h3>웹훅 서명 검증 정책 (2026-04-24 재설계)</h3>
 * <p>Toss Payments 공식 문서에 따르면 {@code PAYMENT_STATUS_CHANGED} 이벤트에는 서명 헤더가
 * 존재하지 않고 별도 웹훅 시크릿도 발급되지 않는다. ({@code tosspayments-webhook-signature}
 * 헤더는 {@code payout.changed}/{@code seller.changed} 에만 해당 — 본 서비스 미구독)
 * 따라서 이 클라이언트에서는 HMAC 서명 검증 기능을 제공하지 않는다. 위변조 방어는 웹훅 핸들러가
 * body 대신 {@code paymentKey} 로 {@link #getPayment}를 재조회하는 방식으로 구현된다.</p>
 *
 * <h3>에러 처리 (2026-04-08 Phase 9 개선)</h3>
 * <ul>
 *   <li>모든 4xx/5xx 응답을 {@link RestClient#status()} 핸들러로 가로채서 {@link TossApiException} 발생</li>
 *   <li>Toss 에러 응답 body의 {@code code}/{@code message}를 파싱하여 예외에 포함</li>
 *   <li>네트워크 오류는 {@link RestClientException} → catch 블록에서 {@link TossApiException} 으로 래핑</li>
 *   <li>{@link #cancelPayment} 는 더 이상 예외를 swallow하지 않는다 — 호출자가 보상/재시도 로직을 처리한다</li>
 * </ul>
 *
 * <h3>멱등성 (Idempotency)</h3>
 * <p>결제 승인/취소 호출 시 {@code Idempotency-Key} 헤더로 동일 요청 중복 방지를 보장한다.
 * 일반적으로 orderId 또는 paymentKey + 액션명을 멱등키로 사용한다.
 * Toss 가이드: https://docs.tosspayments.com/reference/using-api/idempotency-key</p>
 *
 * @see com.monglepick.monglepickbackend.domain.payment.service.PaymentService
 */
@Component
@Slf4j
public class TossPaymentsClient {

    /** Toss Payments REST API 호출용 HTTP 클라이언트 (Basic 인증 헤더 포함) */
    private final RestClient restClient;

    /** Toss 에러 응답 파싱용 Jackson ObjectMapper (싱글턴) */
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    /**
     * TossPaymentsClient 생성자.
     *
     * <p>시크릿 키를 Base64 인코딩하여 RestClient의 기본 Authorization 헤더에 설정한다.
     * Toss Payments 규격: {@code Base64(secretKey + ":")} → Basic 인증 헤더.</p>
     *
     * @param secretKey Toss Payments 시크릿 키 (application.yml의 toss.payments.secret-key)
     * @param baseUrl   Toss Payments API 베이스 URL (application.yml의 toss.payments.base-url)
     */
    public TossPaymentsClient(
            @Value("${toss.payments.secret-key}") String secretKey,
            @Value("${toss.payments.base-url:https://api.tosspayments.com/v1}") String baseUrl) {
        // Toss Payments Basic 인증: secretKey + ":" 를 Base64 인코딩
        String encodedKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encodedKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
                    // 4xx/5xx 응답을 일관된 예외로 변환 — Toss 에러 body 파싱
                    String rawBody = "";
                    try {
                        rawBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException ignored) {
                        // body 읽기 실패는 무시 — status code 만으로 충분한 정보
                    }
                    TossErrorBody parsed = parseTossError(rawBody);
                    log.error("Toss API 에러 응답: status={}, code={}, message={}",
                            res.getStatusCode(), parsed.code(), parsed.message());
                    throw new TossApiException(
                            res.getStatusCode().value(),
                            parsed.code(),
                            parsed.message(),
                            rawBody
                    );
                })
                .build();

        log.info("TossPaymentsClient 초기화 완료: baseUrl={}", baseUrl);
    }

    // ──────────────────────────────────────────────
    // 결제 승인
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 결제 승인 API를 호출한다.
     *
     * <p>클라이언트가 결제창에서 결제를 완료한 후, 서버에서 최종 승인을 수행한다.
     * Toss API: {@code POST /payments/confirm}</p>
     *
     * <h4>Idempotency-Key</h4>
     * <p>orderId 를 그대로 멱등키로 사용한다 — 동일 주문에 대한 중복 승인 요청을 Toss 서버에서
     * 차단하므로 클라이언트 새로고침/네트워크 재시도 시에도 결제가 1회만 처리된다.</p>
     *
     * <h4>요청 Body</h4>
     * <pre>{@code
     * {
     *   "paymentKey": "toss에서 발급한 결제키",
     *   "orderId": "서버에서 생성한 주문 UUID",
     *   "amount": 3900
     * }
     * }</pre>
     *
     * <h4>에러 처리</h4>
     * <p>승인 실패 시 (네트워크 오류, Toss API 4xx/5xx 등) {@link BusinessException}({@link ErrorCode#PAYMENT_FAILED})를
     * 던진다. 호출부(PaymentService)에서 주문 상태를 FAILED로 변경해야 한다.</p>
     *
     * @param paymentKey Toss Payments에서 발급한 결제 키
     * @param orderId    서버에서 생성한 주문 UUID
     * @param amount     결제 금액 (KRW 원 단위, 주문 금액과 일치해야 함)
     * @return Toss 결제 승인 응답 (paymentKey, orderId, status, totalAmount, method)
     * @throws BusinessException 결제 승인 실패 시 (ErrorCode.PAYMENT_FAILED)
     */
    public TossConfirmResponse confirmPayment(String paymentKey, String orderId, int amount) {
        // Toss Payments 승인 요청 Body 구성
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        log.info("Toss 결제 승인 요청: orderId={}, amount={}", orderId, amount);

        try {
            // POST /payments/confirm 호출 — Idempotency-Key 헤더에 orderId 전달
            TossConfirmResponse response = restClient.post()
                    .uri("/payments/confirm")
                    .header("Idempotency-Key", orderId)
                    .body(body)
                    .retrieve()
                    .body(TossConfirmResponse.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.PAYMENT_FAILED, "Toss 응답 본문이 비어있습니다");
            }

            log.info("Toss 결제 승인 성공: orderId={}, paymentKey={}, status={}",
                    orderId, response.paymentKey(), response.status());

            return response;
        } catch (TossApiException e) {
            log.error("Toss 결제 승인 실패: orderId={}, code={}, message={}",
                    orderId, e.getTossCode(), e.getTossMessage());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 승인에 실패했습니다 (" + e.getTossCode() + "): " + e.getTossMessage()
            );
        } catch (RestClientException e) {
            // 네트워크 오류 / 타임아웃 / SSL 오류 등
            log.error("Toss 결제 승인 네트워크 오류: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 승인 중 네트워크 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 결제 취소 (전체)
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 결제 취소(전체 환불) API를 호출한다.
     *
     * <p>결제 완료된 주문을 전액 환불한다.
     * Toss API: {@code POST /payments/{paymentKey}/cancel}</p>
     *
     * <h4>예외 정책 (2026-04-08 Phase 9 변경)</h4>
     * <p>이 메서드는 더 이상 예외를 swallow 하지 않는다.
     * 호출자(PaymentService.refundOrder, attemptCancelWithRetry)가 예외를 받아
     * 보상/재시도 로직을 적용한다.</p>
     *
     * @param paymentKey Toss Payments 결제 키 (결제 승인 시 받은 값)
     * @param reason     취소 사유 (Toss 대시보드에 표시됨)
     * @throws BusinessException 취소 실패 시 (ErrorCode.PAYMENT_FAILED) — 호출자가 보상 로직 적용
     */
    public void cancelPayment(String paymentKey, String reason) {
        cancelPayment(paymentKey, reason, null);
    }

    /**
     * Toss Payments 결제 취소(부분 또는 전체 환불) API를 호출한다.
     *
     * <p>결제 완료된 주문에 대해 전체 또는 부분 환불을 수행한다.
     * Toss API: {@code POST /payments/{paymentKey}/cancel}</p>
     *
     * <h4>요청 Body</h4>
     * <pre>{@code
     * {
     *   "cancelReason": "사용자 요청",
     *   "cancelAmount": 3900       // 부분 환불 시에만 (전체면 생략)
     * }
     * }</pre>
     *
     * <h4>Idempotency-Key</h4>
     * <p>{@code paymentKey + "_cancel_" + (cancelAmount != null ? cancelAmount : "all")} 형식으로 멱등키를 구성한다.
     * 동일한 paymentKey + 동일 금액 취소 요청이 중복 도달해도 Toss 서버에서 1회만 처리된다.</p>
     *
     * @param paymentKey   Toss Payments 결제 키 (결제 승인 시 받은 값)
     * @param reason       취소 사유 (Toss 대시보드에 표시됨)
     * @param cancelAmount 환불 금액 (null 이면 전체 환불, 양수면 부분 환불)
     * @throws BusinessException 취소 실패 시 (ErrorCode.PAYMENT_FAILED)
     */
    public void cancelPayment(String paymentKey, String reason, Integer cancelAmount) {
        // 요청 Body 구성 — cancelAmount 가 있으면 부분 환불
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", reason != null ? reason : "환불 요청");
        if (cancelAmount != null && cancelAmount > 0) {
            body.put("cancelAmount", cancelAmount);
        }

        // 멱등키: 동일 paymentKey + 동일 금액 취소 요청이 중복되면 Toss 서버에서 1회만 처리
        String idempotencyKey = paymentKey + "_cancel_" + (cancelAmount != null ? cancelAmount : "all");

        log.info("Toss 결제 취소 요청: paymentKey={}, reason={}, cancelAmount={}",
                paymentKey, reason, cancelAmount);

        try {
            // POST /payments/{paymentKey}/cancel 호출
            restClient.post()
                    .uri("/payments/{paymentKey}/cancel", paymentKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss 결제 취소 성공: paymentKey={}", paymentKey);
        } catch (TossApiException e) {
            log.error("Toss 결제 취소 실패: paymentKey={}, code={}, message={}",
                    paymentKey, e.getTossCode(), e.getTossMessage());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 취소에 실패했습니다 (" + e.getTossCode() + "): " + e.getTossMessage()
            );
        } catch (RestClientException e) {
            log.error("Toss 결제 취소 네트워크 오류: paymentKey={}, error={}",
                    paymentKey, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 취소 중 네트워크 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 결제 조회
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 결제 조회 API를 호출한다.
     *
     * <p>paymentKey 로 결제의 현재 상태를 조회한다. 보상 패턴에서 "Toss 결제가 실제로 성공했는지"
     * 또는 "취소가 정말로 반영됐는지" 확인하는 용도로 사용한다.
     * Toss API: {@code GET /payments/{paymentKey}}</p>
     *
     * @param paymentKey Toss Payments 결제 키
     * @return Toss 결제 상세 응답 (status 등)
     * @throws BusinessException 조회 실패 시 (ErrorCode.PAYMENT_FAILED)
     */
    public TossConfirmResponse getPayment(String paymentKey) {
        log.debug("Toss 결제 조회 요청: paymentKey={}", paymentKey);

        try {
            TossConfirmResponse response = restClient.get()
                    .uri("/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .body(TossConfirmResponse.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.PAYMENT_FAILED, "Toss 응답 본문이 비어있습니다");
            }

            return response;
        } catch (TossApiException e) {
            log.error("Toss 결제 조회 실패: paymentKey={}, code={}, message={}",
                    paymentKey, e.getTossCode(), e.getTossMessage());
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 조회에 실패했습니다 (" + e.getTossCode() + "): " + e.getTossMessage()
            );
        } catch (RestClientException e) {
            log.error("Toss 결제 조회 네트워크 오류: paymentKey={}, error={}",
                    paymentKey, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 조회 중 네트워크 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 내부 유틸 — Toss 에러 응답 파싱
    // ──────────────────────────────────────────────

    /**
     * Toss 에러 응답 JSON 을 파싱한다.
     *
     * <p>Toss 에러 응답 구조: {@code {"code": "INVALID_REQUEST", "message": "..."}}</p>
     * <p>파싱 실패 시 fallback 으로 raw body 를 message 에 담아 반환한다.</p>
     */
    private static TossErrorBody parseTossError(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new TossErrorBody("UNKNOWN", "Toss 에러 응답 본문이 비어있습니다");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = ERROR_MAPPER.readValue(rawBody, HashMap.class);
            String code = String.valueOf(map.getOrDefault("code", "UNKNOWN"));
            String message = String.valueOf(map.getOrDefault("message", rawBody));
            return new TossErrorBody(code, message);
        } catch (Exception e) {
            return new TossErrorBody("PARSE_ERROR", rawBody);
        }
    }

    // ──────────────────────────────────────────────
    // 내부 DTO (Toss API 응답)
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 결제 승인/조회 응답 DTO.
     *
     * <p>Toss API의 실제 응답은 더 많은 필드를 포함하지만, 현재 서비스에서 필요한 필드만 매핑한다.
     * Jackson은 알 수 없는 필드를 무시하므로 (Spring Boot 기본 설정) 추가 필드가 있어도 역직렬화에 실패하지 않는다.</p>
     *
     * @param paymentKey  Toss 결제 키 (환불/조회 시 사용)
     * @param orderId     서버에서 생성한 주문 UUID
     * @param status      결제 상태 (예: "DONE", "CANCELED", "EXPIRED")
     * @param totalAmount 총 결제 금액 (KRW)
     * @param method      결제 수단 (예: "카드", "가상계좌", "간편결제")
     */
    public record TossConfirmResponse(
            String paymentKey,
            String orderId,
            String status,
            int totalAmount,
            String method
    ) {
    }

    /**
     * Toss API 에러 응답 본문 DTO.
     *
     * <p>Toss API는 4xx/5xx 응답에서 {@code {"code": "...", "message": "..."}} 형식을 사용한다.</p>
     */
    public record TossErrorBody(
            String code,
            String message
    ) {
    }
}
