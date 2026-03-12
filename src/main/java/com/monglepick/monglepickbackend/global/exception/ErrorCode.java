package com.monglepick.monglepickbackend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 에러 코드 열거형
 *
 * <p>비즈니스 로직에서 발생하는 모든 에러를 코드화하여 관리합니다.
 * 각 에러 코드는 HTTP 상태, 에러 코드 문자열, 한국어 메시지를 포함합니다.</p>
 *
 * <p>에러 코드 명명 규칙:</p>
 * <ul>
 *   <li>도메인_동작_상태 (예: USER_NOT_FOUND, TOKEN_EXPIRED)</li>
 *   <li>공통 에러는 COMMON_ 접두사 사용</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 에러 =====
    /** 내부 서버 오류 (예상치 못한 예외) */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_001", "서버 내부 오류가 발생했습니다."),
    /** 잘못된 요청 파라미터 */
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_002", "잘못된 입력값입니다."),
    /** 요청 본문이 누락되었거나 형식이 잘못됨 */
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON_003", "요청 본문이 올바르지 않습니다."),
    /** 허용되지 않는 HTTP 메서드 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_004", "허용되지 않은 HTTP 메서드입니다."),

    // ===== 인증/인가 에러 =====
    /** 인증되지 않은 접근 (토큰 없음 또는 무효) */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
    /** 접근 권한 없음 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_002", "접근 권한이 없습니다."),
    /** JWT 토큰이 유효하지 않음 */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    /** JWT 토큰이 만료됨 */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_004", "토큰이 만료되었습니다."),
    /** 로그인 실패 (이메일/비밀번호 불일치) */
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_005", "이메일 또는 비밀번호가 일치하지 않습니다."),

    // ===== 사용자 에러 =====
    /** 사용자를 찾을 수 없음 */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    /** 이미 존재하는 이메일 */
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 이메일입니다."),
    /** 이미 존재하는 닉네임 */
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER_003", "이미 사용 중인 닉네임입니다."),

    // ===== 게시글 에러 =====
    /** 게시글을 찾을 수 없음 */
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "게시글을 찾을 수 없습니다."),
    /** 게시글 작성자만 수정/삭제 가능 */
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_002", "게시글 작성자만 수정할 수 있습니다."),

    // ===== 리뷰 에러 =====
    /** 리뷰를 찾을 수 없음 */
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_001", "리뷰를 찾을 수 없습니다."),
    /** 동일 영화에 대한 중복 리뷰 */
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "REVIEW_002", "이미 해당 영화에 리뷰를 작성했습니다."),

    // ===== 영화 에러 =====
    /** 영화를 찾을 수 없음 */
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOVIE_001", "영화를 찾을 수 없습니다."),

    // ===== 위시리스트 에러 =====
    /** 이미 위시리스트에 추가된 영화 */
    DUPLICATE_WISHLIST(HttpStatus.CONFLICT, "WISHLIST_001", "이미 위시리스트에 추가된 영화입니다."),
    /** 위시리스트 항목을 찾을 수 없음 */
    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "WISHLIST_002", "위시리스트 항목을 찾을 수 없습니다."),

    // ===== AI Agent 에러 =====
    /** AI Agent 서버 통신 실패 */
    AI_AGENT_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI_001", "AI 서비스에 연결할 수 없습니다."),
    /** AI Agent 응답 처리 실패 */
    AI_AGENT_RESPONSE_ERROR(HttpStatus.BAD_GATEWAY, "AI_002", "AI 서비스 응답 처리 중 오류가 발생했습니다.");

    /** HTTP 응답 상태 코드 */
    private final HttpStatus httpStatus;

    /** 에러 코드 문자열 (프론트엔드 에러 핸들링에 사용) */
    private final String code;

    /** 사용자에게 표시할 한국어 에러 메시지 */
    private final String message;
}
