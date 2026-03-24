package com.monglepick.monglepickbackend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 전역 예외 처리기.
 *
 * <p>모든 컨트롤러에서 발생하는 예외를 가로채어
 * {@link ErrorResponse} 형식의 일관된 JSON 응답으로 변환한다.</p>
 *
 * <h3>처리 우선순위 (구체적인 예외 → 일반적인 예외)</h3>
 * <ol>
 *   <li>{@link InsufficientPointException} — 402 + balance/required details</li>
 *   <li>{@link BusinessException} — ErrorCode의 httpStatus 사용</li>
 *   <li>{@link MethodArgumentNotValidException} — 400 + 필드별 검증 에러 목록</li>
 *   <li>{@link Exception} — 500 catch-all (예상하지 못한 오류)</li>
 * </ol>
 *
 * <h3>AI Agent 연동</h3>
 * <p>{@link InsufficientPointException} 핸들러는 402 응답 본문에
 * {@code balance}와 {@code required} 필드를 포함하여,
 * AI Agent의 {@code point_client.py}가 잔액 부족 정보를 파싱할 수 있도록 한다.</p>
 *
 * @see ErrorResponse
 * @see BusinessException
 * @see InsufficientPointException
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 포인트 잔액 부족 예외 처리.
     *
     * <p>HTTP 402 Payment Required를 반환하며,
     * details에 현재 잔액(balance)과 필요 포인트(required)를 포함한다.</p>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * HTTP 402 Payment Required
     * {
     *   "code": "P001",
     *   "message": "포인트가 부족합니다",
     *   "details": { "balance": 50, "required": 100 }
     * }
     * }</pre>
     *
     * @param ex 포인트 잔액 부족 예외
     * @return 402 응답 (balance, required 상세 포함)
     */
    @ExceptionHandler(InsufficientPointException.class)
    protected ResponseEntity<ErrorResponse> handleInsufficientPoint(InsufficientPointException ex) {
        log.warn("포인트 잔액 부족: balance={}, required={}", ex.getBalance(), ex.getRequired());

        // AI Agent의 point_client.py가 details.balance, details.required를 파싱한다
        Map<String, Object> details = new HashMap<>();
        details.put("balance", ex.getBalance());
        details.put("required", ex.getRequired());

        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, details);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    /**
     * 비즈니스 예외 공통 처리.
     *
     * <p>{@link BusinessException}의 {@link ErrorCode}에 정의된 HTTP 상태 코드를 사용한다.
     * {@link InsufficientPointException}은 더 구체적인 핸들러가 우선 처리한다.</p>
     *
     * @param ex 비즈니스 예외
     * @return ErrorCode에 정의된 HTTP 상태 코드 + 에러 응답
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.warn("비즈니스 예외 발생: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());

        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    /**
     * Bean Validation 검증 실패 처리 ({@code @Valid} 어노테이션).
     *
     * <p>요청 DTO의 필드 검증(@NotBlank, @Min, @Email 등)이 실패하면
     * 필드별 에러 메시지를 details.errors 배열로 반환한다.</p>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * HTTP 400 Bad Request
     * {
     *   "code": "G002",
     *   "message": "잘못된 입력입니다",
     *   "details": {
     *     "errors": [
     *       { "field": "email", "message": "이메일 형식이 올바르지 않습니다" },
     *       { "field": "nickname", "message": "닉네임은 2자 이상이어야 합니다" }
     *     ]
     *   }
     * }
     * }</pre>
     *
     * @param ex 메서드 인자 검증 예외
     * @return 400 응답 (필드별 검증 에러 목록 포함)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("요청 검증 실패: {}", ex.getMessage());

        // 필드별 에러 메시지를 리스트로 수집
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> {
                    Map<String, String> fieldError = new HashMap<>();
                    fieldError.put("field", error.getField());
                    fieldError.put("message", error.getDefaultMessage());
                    return fieldError;
                })
                .toList();

        Map<String, Object> details = new HashMap<>();
        details.put("errors", fieldErrors);

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ErrorResponse response = ErrorResponse.of(errorCode, details);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    /**
     * 예상하지 못한 모든 예외의 catch-all 핸들러.
     *
     * <p>NullPointerException, IllegalStateException 등
     * 비즈니스 로직에서 잡지 못한 예외가 여기로 도달한다.
     * 스택 트레이스를 error 레벨로 로깅하여 모니터링 시스템(Loki/Grafana)에서 감지한다.</p>
     *
     * @param ex 예상하지 못한 예외
     * @return 500 응답 (내부 오류 메시지, 상세 정보 없음)
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("예상하지 못한 서버 오류 발생", ex);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = ErrorResponse.of(errorCode);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
}
