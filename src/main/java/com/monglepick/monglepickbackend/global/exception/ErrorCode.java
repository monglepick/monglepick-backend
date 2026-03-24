package com.monglepick.monglepickbackend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드 열거형.
 *
 * <p>모든 비즈니스 예외는 이 열거형의 상수를 참조하여
 * HTTP 상태 코드, 에러 코드 문자열, 사용자 메시지를 일관되게 반환한다.</p>
 *
 * <h3>코드 체계</h3>
 * <ul>
 *   <li>{@code P0xx} — 포인트 관련 에러 (잔액 부족, 출석 중복, 아이템 관련)</li>
 *   <li>{@code Q0xx} — 쿼터/한도 관련 에러 (일일/월간 사용량 초과)</li>
 *   <li>{@code S0xx} — 보안/인증 관련 에러 (서비스 키, 인증)</li>
 *   <li>{@code G0xx} — 공통 에러 (서버 내부 오류, 잘못된 입력)</li>
 *   <li>{@code U0xx} — 사용자 관련 에러 (사용자 조회 실패)</li>
 *   <li>{@code PAY0xx} — 결제 관련 에러 (결제 실패, 주문 조회/중복)</li>
 *   <li>{@code SUB0xx} — 구독 관련 에러 (활성 구독 중복)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
 * throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다");
 * }</pre>
 *
 * @see BusinessException
 * @see InsufficientPointException
 * @see GlobalExceptionHandler
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ─────────────────────────────────────────────
    // 포인트 (P0xx)
    // ─────────────────────────────────────────────

    /**
     * 포인트 잔액 부족.
     * AI Agent의 point_client.py가 402 응답 + balance/required 필드를 기대한다.
     */
    INSUFFICIENT_POINT(HttpStatus.PAYMENT_REQUIRED, "P001", "포인트가 부족합니다"),

    /** 해당 사용자의 포인트 정보(user_points 레코드)를 찾을 수 없음. */
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "P002", "포인트 정보를 찾을 수 없습니다"),

    /** 같은 날 출석 체크를 이미 완료한 경우 (user_attendance UK 위반). */
    ALREADY_ATTENDED(HttpStatus.CONFLICT, "P003", "오늘 이미 출석했습니다"),

    /** 교환 대상 포인트 아이템(point_items)을 찾을 수 없음. */
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "P004", "아이템을 찾을 수 없습니다"),

    /** 비활성화(is_active=false)된 포인트 아이템에 대한 교환 시도. */
    ITEM_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "P005", "비활성화된 아이템입니다"),

    // ─────────────────────────────────────────────
    // 쿼터/한도 (Q0xx)
    // ─────────────────────────────────────────────

    /** 일일 사용 한도 초과 (daily_used >= daily_limit). */
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Q001", "일일 사용 한도를 초과했습니다"),

    /** 월간 사용 한도 초과. */
    MONTHLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Q002", "월간 사용 한도를 초과했습니다"),

    // ─────────────────────────────────────────────
    // 보안/인증 (S0xx)
    // ─────────────────────────────────────────────

    /** AI Agent → Spring Boot 내부 통신 시 서비스 키 불일치. */
    INVALID_SERVICE_KEY(HttpStatus.UNAUTHORIZED, "S001", "유효하지 않은 서비스 키입니다"),

    /** JWT 토큰 없음 또는 만료. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "S002", "인증이 필요합니다"),

    // ─────────────────────────────────────────────
    // 공통 (G0xx)
    // ─────────────────────────────────────────────

    /** 예상하지 못한 서버 내부 오류 (catch-all). */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다"),

    /** 요청 파라미터/바디 유효성 검증 실패. */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "G002", "잘못된 입력입니다"),

    // ─────────────────────────────────────────────
    // 사용자 (U0xx)
    // ─────────────────────────────────────────────

    /** user_id에 해당하는 사용자를 찾을 수 없음. */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다"),

    // ─────────────────────────────────────────────
    // 결제 (PAY0xx)
    // ─────────────────────────────────────────────

    /** PG사 결제 승인 실패 또는 결제 데이터 이상. */
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAY001", "결제에 실패했습니다"),

    /** 결제 주문(order_id)을 찾을 수 없음. */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY002", "주문을 찾을 수 없습니다"),

    /** 이미 완료된 주문에 대한 중복 결제 시도 (멱등성 보장). */
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "PAY003", "이미 처리된 주문입니다"),

    // ─────────────────────────────────────────────
    // 구독 (SUB0xx)
    // ─────────────────────────────────────────────

    /** 활성 구독이 이미 존재하는 상태에서 신규 구독 시도. */
    ACTIVE_SUBSCRIPTION_EXISTS(HttpStatus.CONFLICT, "SUB001", "이미 활성 구독이 있습니다");

    // ─────────────────────────────────────────────
    // 필드
    // ─────────────────────────────────────────────

    /** HTTP 상태 코드 (예: 402, 404, 409, 500 등). */
    private final HttpStatus httpStatus;

    /** 애플리케이션 내부 에러 코드 문자열 (예: "P001", "G001"). */
    private final String code;

    /** 사용자에게 표시할 에러 메시지 (한국어). */
    private final String message;
}
