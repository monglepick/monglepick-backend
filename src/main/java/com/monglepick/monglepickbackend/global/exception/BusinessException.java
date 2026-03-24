package com.monglepick.monglepickbackend.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 발생하는 예외의 공통 부모 클래스.
 *
 * <p>모든 비즈니스 예외는 {@link ErrorCode}를 반드시 보유하며,
 * {@link GlobalExceptionHandler}에서 이 클래스를 기준으로 일괄 처리한다.</p>
 *
 * <h3>사용 방법</h3>
 * <ol>
 *   <li>기본 메시지 사용: {@code throw new BusinessException(ErrorCode.USER_NOT_FOUND)}</li>
 *   <li>커스텀 메시지 사용: {@code throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 형식이 잘못되었습니다")}</li>
 *   <li>특수 예외 확장: {@link InsufficientPointException} 등 하위 클래스 사용</li>
 * </ol>
 *
 * @see ErrorCode
 * @see GlobalExceptionHandler
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 이 예외에 대응하는 에러 코드.
     * HTTP 상태 코드, 에러 코드 문자열, 기본 메시지를 포함한다.
     */
    private final ErrorCode errorCode;

    /**
     * ErrorCode의 기본 메시지를 사용하는 생성자.
     *
     * <p>예시: {@code throw new BusinessException(ErrorCode.USER_NOT_FOUND)}</p>
     *
     * @param errorCode 에러 코드 (null 불가)
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 커스텀 메시지를 지정하는 생성자.
     *
     * <p>ErrorCode의 기본 메시지 대신 상황에 맞는 구체적인 메시지를 전달할 때 사용한다.</p>
     * <p>예시: {@code throw new BusinessException(ErrorCode.INVALID_INPUT, "생년월일은 YYYYMMDD 형식이어야 합니다")}</p>
     *
     * @param errorCode     에러 코드 (null 불가)
     * @param customMessage 사용자에게 표시할 커스텀 메시지
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
