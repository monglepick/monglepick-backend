package com.monglepick.monglepickbackend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                ex.getMessage(),
                java.util.Collections.emptyMap()
        );

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
        // 첫 필드 에러 메시지를 최상위 message 로 노출한다.
        // 프론트 fetch 래퍼는 기본적으로 `data.message` 만 err.message 로 매핑하므로,
        // details.errors 를 훑지 않아도 구체 원인("포인트 변동량은 1억 이하여야 합니다." 등)이
        // 사용자에게 즉시 보이도록 한다.
        String topMessage = fieldErrors.isEmpty()
                ? errorCode.getMessage()
                : fieldErrors.get(0).get("message");
        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                topMessage,
                details
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    /**
     * 요청 본문 JSON 파싱 실패 처리.
     *
     * <p>대표 케이스:</p>
     * <ul>
     *   <li>숫자 필드에 타입 범위 초과 값 (예: {@code Integer} 에 10조 입력 → int overflow)</li>
     *   <li>필수 필드 누락으로 JSON 구조 불완전</li>
     *   <li>잘못된 JSON 포맷 (따옴표/콤마 누락 등)</li>
     * </ul>
     *
     * <p>기본 {@code Exception} catch-all 로 가면 500 으로 떨어져 원인이 불명확해지므로,
     * 400 + 구체적 메시지로 변환한다. Jackson 메시지는 디버깅용 내부 정보를 포함할 수 있어
     * 루트 원인(`getMostSpecificCause`)의 간단한 메시지만 전달한다.</p>
     *
     * @param ex 메시지 바디 파싱 실패 예외
     * @return 400 응답 (원인 메시지 포함)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        // 루트 원인 추출 — Jackson 의 InputCoercionException 등이 대표적
        Throwable rootCause = ex.getMostSpecificCause();
        String rootMessage = rootCause != null ? rootCause.getMessage() : ex.getMessage();

        // 숫자 범위 초과 (InputCoercionException: "Numeric value ... out of range of `int`") 전용 UX 메시지
        String userMessage;
        if (rootMessage != null && rootMessage.contains("out of range")) {
            userMessage = "입력한 숫자가 허용 범위를 벗어났습니다. 값을 다시 확인해주세요.";
        } else {
            userMessage = "요청 본문을 읽을 수 없습니다. 입력 값을 다시 확인해주세요.";
        }

        log.warn("요청 JSON 파싱 실패 — message={}", rootMessage);

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                userMessage,
                Map.of()
        );

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
    /**
     * IllegalArgumentException 처리 (잘못된 Enum 변환 등).
     *
     * <p>Post.Category.valueOf(), OrderType.valueOf() 등에서 유효하지 않은 값이
     * 전달되었을 때 발생하는 예외를 400 Bad Request로 변환한다.</p>
     *
     * @param ex IllegalArgumentException
     * @return 400 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("잘못된 인자: {}", ex.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                ex.getMessage(),
                Map.of()
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    /**
     * 존재하지 않는 정적 리소스/매핑 경로 요청 처리.
     *
     * <p>Spring Framework 6에서는 핸들러가 없는 경로가
     * {@link NoResourceFoundException}으로 들어올 수 있다.
     * 이 경우 500 catch-all로 떨어뜨리지 않고 404로 응답한다.</p>
     *
     * @param ex NoResourceFoundException
     * @return 404 응답
     */
    /**
     * 경로 변수/쿼리 파라미터 타입 변환 실패 처리.
     *
     * <p>예: {@code GET /api/v1/admin/tickets/{id}} 에서 {@code {id}} 가 {@code Long} 으로 정의돼 있는데
     * 클라이언트가 {@code "undefined"} 같은 비숫자를 보내면 Spring 이 이 예외를 던진다.
     * 기본 {@code Exception} 핸들러로 떨어지면 500 + "서버오류" 가 떠서 원인이 불명확해지므로,
     * 400 + 어떤 파라미터가 어떻게 잘못됐는지 알려주는 응답으로 변환한다.</p>
     *
     * <p>실제 사례: 관리자 화면에서 backend 가 {@code ticketId} 를 반환하는데 프론트가 {@code id}
     * 를 읽어 {@code undefined} 가 path 로 들어가 500 이 떴다. DTO 정합성을 잡는 동안에도
     * 동일한 사고가 다른 화면에서 재발하지 않도록 이 핸들러를 추가했다.</p>
     *
     * @param ex 타입 변환 실패 예외
     * @return 400 응답 (파라미터명 + 입력값 + 기대 타입 포함)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Object value = ex.getValue();
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?";
        log.warn("요청 파라미터 타입 불일치 — name={}, value={}, expected={}", name, value, expected);

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                "요청 파라미터 형식이 올바르지 않습니다: " + name + "=" + value + " (기대 타입: " + expected + ")",
                Map.of("parameter", name, "value", String.valueOf(value), "expectedType", expected)
        );
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("요청 경로를 찾을 수 없음: {}", ex.getResourcePath());

        ErrorResponse response = new ErrorResponse(
                ErrorCode.INVALID_INPUT.getCode(),
                "요청한 경로를 찾을 수 없습니다.",
                Map.of("path", ex.getResourcePath())
        );

        return ResponseEntity
                .status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(response);
    }

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
