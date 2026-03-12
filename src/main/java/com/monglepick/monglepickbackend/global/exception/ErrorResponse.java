package com.monglepick.monglepickbackend.global.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 표준 에러 응답 DTO
 *
 * <p>모든 API 에러 응답의 통일된 형식을 정의합니다.
 * 프론트엔드에서 에러 코드(code)를 기반으로 에러를 처리할 수 있습니다.</p>
 *
 * <p>응답 형식 예시:</p>
 * <pre>{@code
 * {
 *   "code": "USER_001",
 *   "message": "사용자를 찾을 수 없습니다.",
 *   "timestamp": "2026-03-09T14:30:00"
 * }
 * }</pre>
 */
@Getter
@Builder
public class ErrorResponse {

    /** 에러 코드 문자열 (예: "USER_001", "AUTH_003") */
    private final String code;

    /** 사용자에게 표시할 에러 메시지 (한국어) */
    private final String message;

    /** 에러 발생 시각 (ISO 8601 형식) */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    /**
     * ErrorCode로부터 ErrorResponse를 생성하는 팩토리 메서드
     *
     * <p>ErrorCode에 정의된 코드와 기본 메시지를 사용합니다.
     * timestamp는 현재 시각으로 자동 설정됩니다.</p>
     *
     * @param errorCode 에러 코드 열거형 값
     * @return 구성된 ErrorResponse 객체
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * ErrorCode와 커스텀 메시지로 ErrorResponse를 생성하는 팩토리 메서드
     *
     * <p>기본 메시지 대신 상세한 에러 메시지를 포함할 때 사용합니다.
     * 주로 Validation 에러에서 필드별 상세 메시지를 전달할 때 활용됩니다.</p>
     *
     * @param errorCode 에러 코드 열거형 값
     * @param message 커스텀 에러 메시지
     * @return 구성된 ErrorResponse 객체
     */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
