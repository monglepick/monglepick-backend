package com.monglepick.monglepickbackend.global.exception;

import lombok.Getter;

/**
 * 포인트 잔액 부족 시 발생하는 예외.
 *
 * <p>AI Agent의 {@code point_client.py}가 402(Payment Required) 응답을 수신하면
 * 응답 본문에서 {@code balance}와 {@code required} 필드를 파싱하여
 * SSE error 이벤트({@code INSUFFICIENT_POINT})를 클라이언트에 전달한다.</p>
 *
 * <h3>응답 형식 (GlobalExceptionHandler가 생성)</h3>
 * <pre>{@code
 * HTTP 402 Payment Required
 * {
 *   "code": "P001",
 *   "message": "포인트가 부족합니다",
 *   "details": {
 *     "balance": 50,
 *     "required": 100
 *   }
 * }
 * }</pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * if (userPoint.getBalance() < cost) {
 *     throw new InsufficientPointException(userPoint.getBalance(), cost);
 * }
 * }</pre>
 *
 * @see ErrorCode#INSUFFICIENT_POINT
 * @see GlobalExceptionHandler
 */
@Getter
public class InsufficientPointException extends BusinessException {

    /**
     * 현재 사용자의 포인트 잔액.
     * Agent가 402 응답의 details.balance 필드로 읽는다.
     */
    private final int balance;

    /**
     * 요청된 작업에 필요한 포인트.
     * Agent가 402 응답의 details.required 필드로 읽는다.
     */
    private final int required;

    /**
     * 포인트 잔액 부족 예외를 생성한다.
     *
     * @param balance  현재 보유 포인트 (0 이상)
     * @param required 필요한 포인트 (balance보다 큰 값)
     */
    public InsufficientPointException(int balance, int required) {
        super(ErrorCode.INSUFFICIENT_POINT);
        this.balance = balance;
        this.required = required;
    }
}
