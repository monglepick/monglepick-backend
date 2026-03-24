package com.monglepick.monglepickbackend.global.dto;

import com.monglepick.monglepickbackend.global.exception.ErrorResponse;

/**
 * API 공통 응답 래퍼 DTO (불변 record, 제네릭).
 *
 * <p>모든 API 엔드포인트는 이 래퍼로 응답을 감싸서 일관된 형식을 제공한다.
 * 성공 시 {@code data}에 결과를, 실패 시 {@code error}에 에러 정보를 담는다.</p>
 *
 * <h3>성공 응답 예시 (200 OK)</h3>
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "balance": 1500,
 *     "totalEarned": 3000,
 *     "grade": "SILVER"
 *   },
 *   "error": null
 * }
 * }</pre>
 *
 * <h3>성공 응답 예시 (201 Created)</h3>
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { "pointHistoryId": 42 },
 *   "error": null
 * }
 * }</pre>
 *
 * <h3>실패 응답 예시</h3>
 * <pre>{@code
 * {
 *   "success": false,
 *   "data": null,
 *   "error": {
 *     "code": "P001",
 *     "message": "포인트가 부족합니다",
 *     "details": { "balance": 50, "required": 100 }
 *   }
 * }
 * }</pre>
 *
 * <h3>팩토리 메서드</h3>
 * <ul>
 *   <li>{@link #ok(Object)} — 200 OK 성공 응답</li>
 *   <li>{@link #created(Object)} — 201 Created 성공 응답 (리소스 생성)</li>
 *   <li>{@link #fail(ErrorResponse)} — 실패 응답</li>
 * </ul>
 *
 * @param <T>     data 필드의 타입 (성공 시 응답 본문)
 * @param success 요청 처리 성공 여부 (true: 성공, false: 실패)
 * @param data    성공 시 응답 데이터 (실패 시 null)
 * @param error   실패 시 에러 정보 (성공 시 null)
 *
 * @see ErrorResponse
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {

    /**
     * 200 OK 성공 응답을 생성한다.
     *
     * <p>조회, 수정, 삭제 등 기존 리소스에 대한 작업 성공 시 사용한다.</p>
     *
     * @param data 응답 데이터 (null 허용)
     * @param <T>  데이터 타입
     * @return success=true, error=null인 응답
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 201 Created 성공 응답을 생성한다.
     *
     * <p>새 리소스 생성(출석 체크, 포인트 충전 등) 성공 시 사용한다.
     * 컨트롤러에서 {@code ResponseEntity.status(201).body(ApiResponse.created(data))}로 반환한다.</p>
     *
     * @param data 생성된 리소스 데이터
     * @param <T>  데이터 타입
     * @return success=true, error=null인 응답
     */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패 응답을 생성한다.
     *
     * <p>{@link com.monglepick.monglepickbackend.global.exception.GlobalExceptionHandler}에서
     * 예외를 {@link ErrorResponse}로 변환한 뒤 이 메서드로 래핑할 수 있다.</p>
     *
     * @param error 에러 응답 정보
     * @param <T>   데이터 타입 (항상 null이지만 타입 추론을 위해 제네릭 유지)
     * @return success=false, data=null인 응답
     */
    public static <T> ApiResponse<T> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
