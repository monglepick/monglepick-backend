package com.monglepick.monglepickbackend.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 커스텀 예외 클래스
 *
 * <p>애플리케이션의 비즈니스 규칙 위반 시 발생하는 런타임 예외입니다.
 * ErrorCode 열거형과 연동하여 일관된 에러 응답을 제공합니다.</p>
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * // 사용자를 찾을 수 없는 경우
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 *
 * // 추가 메시지와 함께
 * throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이메일 형식이 올바르지 않습니다.");
 * }</pre>
 *
 * <p>GlobalExceptionHandler에서 이 예외를 잡아 ErrorResponse로 변환합니다.</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 에러 코드 (HTTP 상태, 코드 문자열, 기본 메시지 포함) */
    private final ErrorCode errorCode;

    /**
     * ErrorCode만으로 예외를 생성합니다.
     * <p>에러 메시지는 ErrorCode에 정의된 기본 메시지를 사용합니다.</p>
     *
     * @param errorCode 에러 코드 열거형 값
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode와 커스텀 메시지로 예외를 생성합니다.
     * <p>기본 메시지 대신 상세한 에러 메시지를 전달할 때 사용합니다.</p>
     *
     * @param errorCode 에러 코드 열거형 값
     * @param message 커스텀 에러 메시지
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode와 원인 예외로 예외를 생성합니다.
     * <p>하위 레이어에서 발생한 예외를 래핑할 때 사용합니다.</p>
     *
     * @param errorCode 에러 코드 열거형 값
     * @param cause 원인 예외
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
