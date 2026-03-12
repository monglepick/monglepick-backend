package com.monglepick.monglepickbackend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 *
 * <p>애플리케이션 전체에서 발생하는 예외를 일괄 처리하여
 * 일관된 ErrorResponse 형식으로 클라이언트에 반환합니다.</p>
 *
 * <p>처리하는 예외 유형:</p>
 * <ul>
 *   <li>BusinessException: 비즈니스 로직 예외 (커스텀)</li>
 *   <li>MethodArgumentNotValidException: @Valid 검증 실패</li>
 *   <li>BindException: 바인딩 검증 실패</li>
 *   <li>MethodArgumentTypeMismatchException: 타입 불일치</li>
 *   <li>MissingServletRequestParameterException: 필수 파라미터 누락</li>
 *   <li>HttpRequestMethodNotSupportedException: 지원하지 않는 HTTP 메서드</li>
 *   <li>Exception: 예상치 못한 모든 예외 (500)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 예외 처리
     *
     * <p>서비스 레이어에서 발생시키는 BusinessException을 처리합니다.
     * ErrorCode에 정의된 HTTP 상태 코드와 메시지를 그대로 반환합니다.</p>
     *
     * @param e BusinessException 인스턴스
     * @return ErrorResponse를 담은 ResponseEntity
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("비즈니스 예외 발생 - 코드: {}, 메시지: {}",
                e.getErrorCode().getCode(), e.getMessage());

        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    /**
     * @Valid 검증 실패 예외 처리
     *
     * <p>@RequestBody에 @Valid를 적용했을 때 검증 실패 시 발생합니다.
     * 모든 필드 에러 메시지를 수집하여 하나의 문자열로 반환합니다.</p>
     *
     * @param e MethodArgumentNotValidException 인스턴스
     * @return 400 Bad Request와 상세 검증 오류 메시지
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {

        // 모든 필드 에러 메시지를 쉼표로 연결
        String errorMessages = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("요청 검증 실패: {}", errorMessages);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errorMessages);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 바인딩 검증 실패 예외 처리
     *
     * <p>@ModelAttribute 바인딩 시 검증 실패하면 발생합니다.
     * MethodArgumentNotValidException과 유사하게 처리합니다.</p>
     *
     * @param e BindException 인스턴스
     * @return 400 Bad Request와 검증 오류 메시지
     */
    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        String errorMessages = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("바인딩 검증 실패: {}", errorMessages);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errorMessages);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 파라미터 타입 불일치 예외 처리
     *
     * <p>경로 변수나 쿼리 파라미터의 타입이 예상과 다를 때 발생합니다.
     * 예: /api/v1/movies/abc (Long 타입 기대, String 전달)</p>
     *
     * @param e MethodArgumentTypeMismatchException 인스턴스
     * @return 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e) {

        String message = String.format("파라미터 '%s'의 값 '%s'이(가) 올바르지 않습니다.",
                e.getName(), e.getValue());

        log.warn("파라미터 타입 불일치: {}", message);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 필수 쿼리 파라미터 누락 예외 처리
     *
     * @param e MissingServletRequestParameterException 인스턴스
     * @return 400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e) {

        String message = String.format("필수 파라미터 '%s'이(가) 누락되었습니다.", e.getParameterName());

        log.warn("필수 파라미터 누락: {}", message);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 지원하지 않는 HTTP 메서드 예외 처리
     *
     * <p>예: POST 전용 엔드포인트에 GET 요청</p>
     *
     * @param e HttpRequestMethodNotSupportedException 인스턴스
     * @return 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {

        log.warn("허용되지 않은 HTTP 메서드: {}", e.getMethod());

        ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * 기타 모든 예외 처리 (최후의 방어선)
     *
     * <p>위에서 처리하지 못한 모든 예외를 500 Internal Server Error로 반환합니다.
     * 상세한 에러 정보는 로그에만 기록하고, 클라이언트에는 일반적인 메시지만 전달합니다.</p>
     *
     * @param e 처리되지 않은 예외
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 예상치 못한 예외는 ERROR 레벨로 스택트레이스 포함하여 기록
        log.error("예상치 못한 서버 오류 발생", e);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
