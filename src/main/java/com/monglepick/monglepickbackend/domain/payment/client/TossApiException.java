package com.monglepick.monglepickbackend.domain.payment.client;

import lombok.Getter;

/**
 * Toss Payments API 호출 시 4xx/5xx 응답을 표현하는 예외.
 *
 * <p>{@link TossPaymentsClient} 의 {@code defaultStatusHandler} 에서 발생하며,
 * Toss API 의 에러 응답 본문({@code {code, message}})을 파싱하여 보관한다.
 * 호출자(PaymentService 등)가 {@code BusinessException(PAYMENT_FAILED)} 으로 변환한다.</p>
 *
 * <h3>왜 RuntimeException 인가?</h3>
 * <p>{@link org.springframework.web.client.RestClient} 의 status handler 콜백은 checked exception 을
 * 던질 수 없으므로, RuntimeException 으로 정의한다. 호출자에서는 try/catch 로 명시적으로 잡아 BusinessException 으로 변환한다.</p>
 *
 * @see TossPaymentsClient#confirmPayment(String, String, int)
 * @see TossPaymentsClient#cancelPayment(String, String, Integer)
 */
@Getter
public class TossApiException extends RuntimeException {

    /** HTTP 응답 상태 코드 (예: 400, 401, 500) */
    private final int statusCode;

    /** Toss 에러 응답의 {@code code} 필드 (예: "INVALID_REQUEST", "ALREADY_PROCESSED_PAYMENT") */
    private final String tossCode;

    /** Toss 에러 응답의 {@code message} 필드 (사람이 읽을 수 있는 한국어 설명) */
    private final String tossMessage;

    /** Toss 에러 응답 원본 body (디버깅용) */
    private final String rawBody;

    /**
     * TossApiException 생성자.
     *
     * @param statusCode  HTTP 상태 코드 (4xx/5xx)
     * @param tossCode    Toss 에러 코드 문자열
     * @param tossMessage Toss 에러 메시지 (한국어)
     * @param rawBody     원본 응답 본문 (파싱 실패 시 fallback)
     */
    public TossApiException(int statusCode, String tossCode, String tossMessage, String rawBody) {
        super("Toss API 에러 [HTTP " + statusCode + "] " + tossCode + ": " + tossMessage);
        this.statusCode = statusCode;
        this.tossCode = tossCode;
        this.tossMessage = tossMessage;
        this.rawBody = rawBody;
    }
}
