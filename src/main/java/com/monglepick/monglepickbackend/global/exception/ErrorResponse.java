package com.monglepick.monglepickbackend.global.exception;

import java.util.Collections;
import java.util.Map;

/**
 * API 에러 응답 DTO (불변 record).
 *
 * <p>{@link GlobalExceptionHandler}에서 모든 예외를 이 형식으로 직렬화하여 반환한다.
 * 클라이언트와 AI Agent는 이 JSON 구조를 파싱하여 에러를 처리한다.</p>
 *
 * <h3>응답 JSON 예시</h3>
 * <pre>{@code
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
 * <h3>팩토리 메서드</h3>
 * <ul>
 *   <li>{@link #of(ErrorCode)} — 기본 에러 응답 (details 없음)</li>
 *   <li>{@link #of(ErrorCode, Map)} — 상세 정보 포함 에러 응답</li>
 * </ul>
 *
 * @param code    애플리케이션 내부 에러 코드 (예: "P001", "G001")
 * @param message 사용자에게 표시할 에러 메시지 (한국어)
 * @param details 추가 상세 정보 (nullable → 빈 맵으로 정규화됨)
 *
 * @see ErrorCode
 * @see GlobalExceptionHandler
 */
public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {

    /**
     * ErrorCode로부터 기본 에러 응답을 생성한다.
     * details는 빈 맵으로 설정된다.
     *
     * @param errorCode 에러 코드 (null 불가)
     * @return 에러 응답 인스턴스
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                Collections.emptyMap()
        );
    }

    /**
     * ErrorCode와 상세 정보를 포함한 에러 응답을 생성한다.
     *
     * <p>details가 null인 경우 빈 맵으로 대체하여 NPE를 방지한다.</p>
     *
     * @param errorCode 에러 코드 (null 불가)
     * @param details   추가 상세 정보 (null 허용 → 빈 맵으로 변환)
     * @return 에러 응답 인스턴스
     */
    public static ErrorResponse of(ErrorCode errorCode, Map<String, Object> details) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                details != null ? details : Collections.emptyMap()
        );
    }
}
