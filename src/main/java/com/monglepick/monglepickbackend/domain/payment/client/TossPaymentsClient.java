package com.monglepick.monglepickbackend.domain.payment.client;

import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

/**
 * Toss Payments REST API 클라이언트.
 *
 * <p>Toss Payments 결제 승인/취소 API를 호출하는 HTTP 클라이언트.
 * Spring Boot 4.0.3의 {@link RestClient}를 사용하여 동기 HTTP 요청을 수행한다.</p>
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
 * <h3>에러 처리</h3>
 * <p>결제 승인 실패 시 {@link BusinessException}({@link ErrorCode#PAYMENT_FAILED})를 던진다.
 * 결제 취소 실패 시에는 예외를 던지지 않고 로그만 남긴다 (수동 환불 처리 필요).</p>
 *
 * @see com.monglepick.monglepickbackend.domain.payment.service.PaymentService
 */
@Component
@Slf4j
public class TossPaymentsClient {

    /** Toss Payments REST API 호출용 HTTP 클라이언트 (Basic 인증 헤더 포함) */
    private final RestClient restClient;

    /** Toss Payments 시크릿 키 (로깅/디버깅용 보관, API 호출에는 restClient의 기본 헤더 사용) */
    private final String secretKey;

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
            @Value("${toss.payments.secret-key:test_sk_ep4IGKBJCBJVOxZGnxz8rMyEYxQ0EBnr}") String secretKey,
            @Value("${toss.payments.base-url:https://api.tosspayments.com/v1}") String baseUrl) {
        this.secretKey = secretKey;

        // Toss Payments Basic 인증: secretKey + ":" 를 Base64 인코딩
        String encodedKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encodedKey)
                .defaultHeader("Content-Type", "application/json")
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
     * <p>승인 실패 시 (네트워크 오류, Toss API 4xx/5xx 등)
     * {@link BusinessException}({@link ErrorCode#PAYMENT_FAILED})를 던진다.
     * 호출부(PaymentService)에서 주문 상태를 FAILED로 변경해야 한다.</p>
     *
     * @param paymentKey Toss Payments에서 발급한 결제 키
     * @param orderId    서버에서 생성한 주문 UUID
     * @param amount     결제 금액 (KRW 원 단위, 주문 금액과 일치해야 함)
     * @return Toss 결제 승인 응답 (paymentKey, orderId, status, totalAmount, method)
     * @throws BusinessException 결제 승인 실패 시 (ErrorCode.PAYMENT_FAILED)
     */
    public TossConfirmResponse confirmPayment(String paymentKey, String orderId, int amount) {
        try {
            // Toss Payments 승인 요청 Body 구성
            Map<String, Object> body = Map.of(
                    "paymentKey", paymentKey,
                    "orderId", orderId,
                    "amount", amount
            );

            log.info("Toss 결제 승인 요청: orderId={}, amount={}", orderId, amount);

            // POST /payments/confirm 호출
            TossConfirmResponse response = restClient.post()
                    .uri("/payments/confirm")
                    .body(body)
                    .retrieve()
                    .body(TossConfirmResponse.class);

            log.info("Toss 결제 승인 성공: orderId={}, paymentKey={}, status={}",
                    orderId, response.paymentKey(), response.status());

            return response;

        } catch (Exception e) {
            log.error("Toss 결제 승인 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "결제 승인에 실패했습니다: " + e.getMessage()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 결제 취소
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 결제 취소(환불) API를 호출한다.
     *
     * <p>결제 완료된 주문에 대해 환불을 수행한다.
     * Toss API: {@code POST /payments/{paymentKey}/cancel}</p>
     *
     * <h4>에러 처리 정책</h4>
     * <p>취소 실패 시 예외를 던지지 <b>않는다</b>.
     * PG 취소 실패는 운영팀이 Toss 대시보드에서 수동 처리해야 하므로,
     * 로그만 남기고 서비스 흐름을 중단하지 않는다.</p>
     *
     * @param paymentKey Toss Payments 결제 키 (결제 승인 시 받은 값)
     * @param reason     취소 사유 (Toss 대시보드에 표시됨)
     */
    public void cancelPayment(String paymentKey, String reason) {
        try {
            log.info("Toss 결제 취소 요청: paymentKey={}, reason={}", paymentKey, reason);

            // POST /payments/{paymentKey}/cancel 호출
            restClient.post()
                    .uri("/payments/{paymentKey}/cancel", paymentKey)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss 결제 취소 성공: paymentKey={}", paymentKey);

        } catch (Exception e) {
            // 취소 실패는 로그만 남기고 진행 (수동 환불 필요)
            log.error("Toss 결제 취소 실패 (수동 처리 필요): paymentKey={}, error={}",
                    paymentKey, e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // 내부 DTO (Toss API 응답)
    // ──────────────────────────────────────────────

    /**
     * Toss Payments 결제 승인 응답 DTO.
     *
     * <p>Toss API의 실제 응답은 더 많은 필드를 포함하지만,
     * 현재 서비스에서 필요한 필드만 매핑한다.
     * Jackson은 알 수 없는 필드를 무시하므로 (Spring Boot 기본 설정)
     * 추가 필드가 있어도 역직렬화에 실패하지 않는다.</p>
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
}
