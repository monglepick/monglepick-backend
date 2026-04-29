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

    /** 보유 아이템(user_items)을 찾을 수 없음 — 2026-04-14 C 방향 신규. */
    USER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "P006", "보유 아이템을 찾을 수 없습니다"),

    /** 보유 아이템에 대한 타인 접근 — 본인 소유가 아닌 경우. */
    USER_ITEM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "P007", "해당 아이템에 접근할 권한이 없습니다"),

    /** 착용 불가 상태(만료/사용 완료)의 아이템에 대한 착용·사용 시도. */
    USER_ITEM_INVALID_STATE(HttpStatus.BAD_REQUEST, "P008", "현재 사용할 수 없는 아이템 상태입니다"),

    /** 아바타·배지가 아닌 카테고리에 대한 착용·해제 시도. */
    USER_ITEM_NOT_EQUIPPABLE(HttpStatus.BAD_REQUEST, "P009", "착용할 수 없는 카테고리의 아이템입니다"),

    // ─────────────────────────────────────────────
    // 쿼터/한도 (Q0xx)
    // ─────────────────────────────────────────────

    /** 일일 사용 한도 초과 (daily_used >= daily_limit). */
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Q001", "일일 사용 한도를 초과했습니다"),

    /** 월간 사용 한도 초과. */
    MONTHLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Q002", "월간 사용 한도를 초과했습니다"),

    /**
     * 비로그인(게스트) 평생 1회 무료 체험 쿼터를 모두 사용한 경우.
     * 쿠키(mongle_guest) 또는 IP 기반 Redis 키(chat:guest_used_*) 둘 중 하나라도 소비 기록이 있으면 반환한다.
     * Client 는 이 에러 코드를 받으면 로그인/회원가입 유도 모달을 노출한다.
     */
    GUEST_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Q003", "무료 체험 1회를 모두 사용하셨습니다. 로그인 후 이용해주세요"),

    // ─────────────────────────────────────────────
    // 보안/인증 (S0xx)
    // ─────────────────────────────────────────────

    /** AI Agent → Spring Boot 내부 통신 시 서비스 키 불일치. */
    INVALID_SERVICE_KEY(HttpStatus.UNAUTHORIZED, "S001", "유효하지 않은 서비스 키입니다"),

    /** JWT 토큰 없음 또는 만료. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "S002", "인증이 필요합니다"),

    // ─────────────────────────────────────────────
    // 인증 (A0xx)
    // ─────────────────────────────────────────────

    /** 이미 사용 중인 이메일로 회원가입 시도. */
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "A001", "이미 사용 중인 이메일입니다"),

    /** 이미 사용 중인 닉네임으로 회원가입 시도. */
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "A002", "이미 사용 중인 닉네임입니다"),

    /** 로그인 시 이메일 또는 비밀번호 불일치. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A003", "이메일 또는 비밀번호가 올바르지 않습니다"),

    /** JWT 토큰 형식 오류 또는 서명 불일치. */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않은 토큰입니다"),

    /** JWT 토큰 유효기간 만료. */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A005", "토큰이 만료되었습니다"),

    /** 소셜 로그인(OAuth) 처리 중 오류 발생. */
    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "A006", "소셜 로그인에 실패했습니다"),

    /** 소셜 로그인 시 해당 이메일이 다른 제공자로 이미 가입된 경우. */
    SOCIAL_EMAIL_EXISTS(HttpStatus.CONFLICT, "A007", "해당 이메일로 이미 다른 방식으로 가입되어 있습니다"),

    /** Refresh Token이 DB 화이트리스트에 존재하지 않음 (탈취 의심 또는 이미 사용됨). */
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A008", "유효하지 않은 Refresh Token입니다"),

    /** OAuth2 쿠키에 Refresh Token이 없음 (소셜 로그인 토큰 교환 실패). */
    COOKIE_NOT_FOUND(HttpStatus.BAD_REQUEST, "A009", "쿠키가 존재하지 않습니다"),

    /** 관리자 전용 로그인 엔드포인트에 일반 사용자가 접근 시도. */
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "A010", "관리자 계정만 로그인할 수 있습니다"),

    /** 관리자에 의해 정지된 계정. 유효한 JWT가 있어도 API 접근 차단. */
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "A011", "정지된 계정입니다. 관리자에게 문의하세요."),

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

    /** 소셜 로그인 사용자는 비밀번호 변경 불가. */
    SOCIAL_USER_CANNOT_CHANGE_PASSWORD(HttpStatus.FORBIDDEN, "U002", "소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다"),

    /** 현재 비밀번호가 일치하지 않음. */
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "U003", "현재 비밀번호가 올바르지 않습니다"),

    // ─────────────────────────────────────────────
    // 결제 (PAY0xx)
    // ─────────────────────────────────────────────

    /** PG사 결제 승인 실패 또는 결제 데이터 이상. */
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAY001", "결제에 실패했습니다"),

    /** 결제 주문(order_id)을 찾을 수 없음. */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY002", "주문을 찾을 수 없습니다"),

    /** 이미 완료된 주문에 대한 중복 결제 시도 (멱등성 보장). */
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "PAY003", "이미 처리된 주문입니다"),

    /** 동일 멱등키로 다른 요청 파라미터를 전송한 경우. */
    IDEMPOTENCY_KEY_REUSE(HttpStatus.UNPROCESSABLE_ENTITY, "PAY004", "동일한 멱등키로 다른 요청을 보낼 수 없습니다"),

    /* PAY005 INVALID_WEBHOOK_SIGNATURE 는 2026-04-24 제거됨 —
     * Toss Payments 는 PAYMENT_STATUS_CHANGED 웹훅에 서명 헤더를 제공하지 않으며,
     * 위변조 방어는 orderId 기반 getPayment() 재조회로 대체되었다.
     * (payout.changed / seller.changed 에만 tosspayments-webhook-signature 가 존재하나 본 서비스 미구독) */

    // ─────────────────────────────────────────────
    // 구독 (SUB0xx)
    // ─────────────────────────────────────────────

    /** 활성 구독이 이미 존재하는 상태에서 신규 구독 시도. */
    ACTIVE_SUBSCRIPTION_EXISTS(HttpStatus.CONFLICT, "SUB001", "이미 활성 구독이 있습니다"),

    /** 활성 구독을 찾을 수 없음 (취소 시). */
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SUB002", "활성 구독을 찾을 수 없습니다"),

    // ─────────────────────────────────────────────
    // 커뮤니티 (POST0xx)
    // ─────────────────────────────────────────────

    /** 게시글 ID에 해당하는 게시글을 찾을 수 없음. */
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST001", "게시글을 찾을 수 없습니다"),

    /** 게시글 수정/삭제 시 작성자 본인이 아닌 경우. */
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST002", "게시글 수정/삭제 권한이 없습니다"),

    /** 같은 사용자가 같은 게시글을 여러 번 신고하려는 경우(멱등 보장). */
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "POST003", "이미 신고한 게시글입니다"),

    /** 자신이 작성한 게시글을 본인이 신고하려는 경우. */
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "POST004", "본인이 작성한 게시글은 신고할 수 없습니다"),

    // ─────────────────────────────────────────────
    // 리뷰 (REV0xx)
    // ─────────────────────────────────────────────

    /** 같은 사용자가 같은 영화에 중복 리뷰 작성 시도. */
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "REV001", "이미 리뷰를 작성했습니다"),

    /** 이미 소프트 삭제된 리뷰에 대해 다시 삭제를 시도한 경우. */
    REVIEW_ALREADY_DELETED(HttpStatus.CONFLICT, "REV002", "이미 삭제된 리뷰입니다"),

    // ─────────────────────────────────────────────
    // 영화 (MOV0xx)
    // ─────────────────────────────────────────────

    /** 영화 ID에 해당하는 영화를 찾을 수 없음. */
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOV001", "영화를 찾을 수 없습니다"),

    /** 관리자 — 영화 ID(movie_id) 중복 등록 시도. */
    DUPLICATE_MOVIE_ID(HttpStatus.CONFLICT, "MOV002", "이미 사용 중인 영화 ID입니다"),

    /** 관리자 — TMDB ID 중복 등록 시도. */
    DUPLICATE_TMDB_ID(HttpStatus.CONFLICT, "MOV003", "이미 등록된 TMDB ID입니다"),

    /** 관리자 — 장르 ID에 해당하는 장르 마스터를 찾을 수 없음. */
    GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "GEN001", "장르를 찾을 수 없습니다"),

    /** 관리자 — 장르 코드 중복 등록 시도. */
    DUPLICATE_GENRE_CODE(HttpStatus.CONFLICT, "GEN002", "이미 사용 중인 장르 코드입니다"),

    /** 관리자 — OCR 이벤트 ID에 해당하는 이벤트를 찾을 수 없음. */
    OCR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "OCR001", "OCR 인증 이벤트를 찾을 수 없습니다"),

    /** 관리자 — OCR 이벤트 시작/종료일이 잘못된 경우 (end &lt; start). */
    INVALID_OCR_EVENT_PERIOD(HttpStatus.BAD_REQUEST, "OCR002", "이벤트 종료일은 시작일 이후여야 합니다"),

    /** 유저 인증 — 해당 이벤트가 현재 진행 중이 아니거나 기간이 종료됨. */
    OCR_EVENT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "OCR003", "현재 진행 중인 이벤트가 아닙니다"),

    /** 유저 인증 — 같은 이벤트에 중복 인증 제출 시도. */
    DUPLICATE_OCR_VERIFICATION(HttpStatus.CONFLICT, "OCR004", "이미 해당 이벤트에 인증을 제출했습니다"),

    /** 유저 인증 — OCR 신뢰도가 기준(50%) 미만이어서 제출 불가. */
    OCR_CONFIDENCE_TOO_LOW(HttpStatus.UNPROCESSABLE_ENTITY, "OCR005", "OCR 신뢰도가 낮아 제출할 수 없습니다. 다른 이미지를 업로드해주세요."),

    /** 관리자 — 인기 검색어 키워드를 찾을 수 없음. */
    POPULAR_SEARCH_NOT_FOUND(HttpStatus.NOT_FOUND, "PSK001", "인기 검색어 항목을 찾을 수 없습니다"),

    /** 관리자 — 인기 검색어 키워드 중복 등록 시도. */
    DUPLICATE_POPULAR_KEYWORD(HttpStatus.CONFLICT, "PSK002", "이미 등록된 인기 검색어입니다"),

    /** 관리자 — 월드컵 후보 영화를 찾을 수 없음. */
    WORLDCUP_CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "WCC001", "월드컵 후보 영화를 찾을 수 없습니다"),

    /** 관리자 — 월드컵 후보 영화 중복 등록 시도(같은 movieId+category). */
    DUPLICATE_WORLDCUP_CANDIDATE(HttpStatus.CONFLICT, "WCC002", "이미 등록된 월드컵 후보 영화입니다"),

    /** 관리자 — 월드컵 후보 카테고리를 찾을 수 없음. */
    WORLDCUP_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "WCG001", "월드컵 후보 카테고리를 찾을 수 없습니다"),

    /** 관리자 — 월드컵 후보 카테고리 코드 중복 등록 시도. */
    DUPLICATE_WORLDCUP_CATEGORY_CODE(HttpStatus.CONFLICT, "WCG002", "이미 사용 중인 월드컵 카테고리 코드입니다"),

    /** 관리자 — 후보 영화가 연결된 카테고리를 삭제하려는 시도. */
    WORLDCUP_CATEGORY_IN_USE(HttpStatus.CONFLICT, "WCG003", "후보 영화가 연결된 월드컵 카테고리는 삭제할 수 없습니다"),

    /** 관리자 — 포인트팩(point_pack_prices) 마스터를 찾을 수 없음. */
    POINT_PACK_NOT_FOUND(HttpStatus.NOT_FOUND, "PPK001", "포인트팩을 찾을 수 없습니다"),

    /** 관리자 — (price, pointsAmount) 조합 중복 등록 시도. */
    DUPLICATE_POINT_PACK(HttpStatus.CONFLICT, "PPK002", "이미 등록된 포인트팩 조합입니다"),

    /** 관리자 — 게시글 카테고리(상위/하위)를 찾을 수 없음. */
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT001", "카테고리를 찾을 수 없습니다"),

    /** 관리자 — 상위 카테고리명(up_category) 중복 등록 시도. */
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "CAT002", "이미 사용 중인 카테고리명입니다"),

    /** 관리자 — 같은 상위 카테고리 내 하위 카테고리명 중복 등록 시도. */
    DUPLICATE_CATEGORY_CHILD(HttpStatus.CONFLICT, "CAT003", "이미 사용 중인 하위 카테고리명입니다"),

    /** 관리자 — 리워드 정책(policy_id 또는 action_type)을 찾을 수 없음. */
    REWARD_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "RWP001", "리워드 정책을 찾을 수 없습니다"),

    /** 관리자 — action_type 중복 등록 시도. */
    DUPLICATE_REWARD_POLICY(HttpStatus.CONFLICT, "RWP002", "이미 등록된 활동 코드입니다"),

    /** 관리자 — 공지 시작일/종료일 유효성 위반 (BANNER/POPUP/MODAL 노출 기간). */
    INVALID_NOTICE_PERIOD(HttpStatus.BAD_REQUEST, "NOT002", "공지 종료일은 시작일 이후여야 합니다"),
    // 2026-04-08: APP_NOTICE_NOT_FOUND 제거 — AppNotice 엔티티 폐기로 SupportNotice에 통합.
    //             공지 단건 조회 실패 시에는 INVALID_INPUT(공통 예외)을 사용한다.

    // ─────────────────────────────────────────────
    // 위시리스트 (WISH0xx)
    // ─────────────────────────────────────────────

    /** 이미 위시리스트에 추가된 영화에 대한 중복 추가 시도. */
    DUPLICATE_WISHLIST(HttpStatus.CONFLICT, "WISH001", "이미 위시리스트에 추가된 영화입니다"),

    /** 위시리스트에서 해당 영화 항목을 찾을 수 없음. */
    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "WISH002", "위시리스트 항목을 찾을 수 없습니다"),

    // ─────────────────────────────────────────────
    // 고객센터 (SUPPORT0xx)
    // ─────────────────────────────────────────────

    /** faqId에 해당하는 FAQ를 찾을 수 없음. */
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "SUPPORT001", "FAQ를 찾을 수 없습니다"),

    /** articleId에 해당하는 도움말 문서를 찾을 수 없음. */
    HELP_ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "SUPPORT002", "도움말 문서를 찾을 수 없습니다"),

    /** ticketId에 해당하는 상담 티켓을 찾을 수 없음. */
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "SUPPORT003", "상담 티켓을 찾을 수 없습니다"),

    /** 본인 소유가 아닌 티켓에 접근하려 한 경우 (사용자용 상세 조회 API 보안). */
    TICKET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SUPPORT004", "해당 상담 티켓에 접근할 권한이 없습니다"),

    /** 동일 사용자가 같은 FAQ에 피드백을 이미 제출한 경우 (faq_id + user_id UK 위반). */
    FAQ_FEEDBACK_DUPLICATE(HttpStatus.CONFLICT, "SUPPORT005", "이미 피드백을 제출했습니다"),

    // ─────────────────────────────────────────────
    // 업적 (ACH0xx)
    // ─────────────────────────────────────────────

    /** user_achievement_id에 해당하는 달성 업적을 찾을 수 없음. */
    ACHIEVEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACH001", "업적을 찾을 수 없습니다"),

    /** 본인 업적이 아닌 경우 접근 거부. */
    ACHIEVEMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACH002", "업적 조회 권한이 없습니다"),

    /** AchievementType 마스터 — 업적 코드 중복 (관리자 신규 등록 시). */
    DUPLICATE_ACHIEVEMENT_CODE(HttpStatus.CONFLICT, "ACH003", "이미 사용 중인 업적 코드입니다"),

    /** AchievementType 마스터 — 업적 유형 ID에 해당하는 마스터를 찾을 수 없음. */
    ACHIEVEMENT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "ACH004", "업적 유형을 찾을 수 없습니다"),

    // ─────────────────────────────────────────────
    // 채팅 (CHAT0xx)
    // ─────────────────────────────────────────────

    /** sessionId에 해당하는 채팅 세션을 찾을 수 없음. */
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT001", "채팅 세션을 찾을 수 없습니다"),

    /** 본인 세션이 아닌 경우 접근 거부. */
    CHAT_SESSION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT002", "채팅 세션 접근 권한이 없습니다"),

    /** suggestionId에 해당하는 채팅 추천 칩을 찾을 수 없음 (관리자 CRUD). */
    CHAT_SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT003", "채팅 추천 칩을 찾을 수 없습니다"),

    // ─────────────────────────────────────────────
    // 플레이리스트 (PL0xx)
    // ─────────────────────────────────────────────

    /** playlistId에 해당하는 플레이리스트를 찾을 수 없음. */
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PL001", "플레이리스트를 찾을 수 없습니다"),

    /** 플레이리스트 소유자가 아닌 사용자가 수정/삭제/상세 조회 시도. */
    PLAYLIST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PL002", "플레이리스트에 접근할 권한이 없습니다"),

    /** 동일 플레이리스트에 이미 추가된 영화를 중복 추가 시도. */
    PLAYLIST_ITEM_DUPLICATE(HttpStatus.CONFLICT, "PL003", "이미 플레이리스트에 추가된 영화입니다"),

    /** 플레이리스트에서 제거하려는 영화 항목이 존재하지 않음. */
    PLAYLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "PL004", "플레이리스트에 해당 영화가 없습니다"),

    /** 이미 좋아요를 누른 플레이리스트에 중복 좋아요 시도. */
    PLAYLIST_LIKE_DUPLICATE(HttpStatus.CONFLICT, "PL005", "이미 좋아요를 누른 플레이리스트입니다"),

    /** 좋아요를 누르지 않은 플레이리스트에 좋아요 취소 시도. */
    PLAYLIST_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "PL006", "좋아요 기록이 없습니다"),

    /** 비공개 플레이리스트에 소유자가 아닌 사용자가 좋아요 시도. */
    PLAYLIST_PRIVATE(HttpStatus.FORBIDDEN, "PL007", "비공개 플레이리스트입니다"),

    /** 이미 가져온 플레이리스트를 중복으로 가져오기 시도. */
    PLAYLIST_SCRAP_DUPLICATE(HttpStatus.CONFLICT, "PL008", "이미 가져온 플레이리스트입니다"),

    /** PLAYLIST_SHARE 게시글 작성 시 자신의 공개 플레이리스트가 아닌 경우. */
    PLAYLIST_SHARE_INVALID(HttpStatus.BAD_REQUEST, "PL009", "공개 상태인 본인 플레이리스트만 공유할 수 있습니다"),

    /** 본인의 플레이리스트를 스스로 가져오기 시도. */
    PLAYLIST_SELF_IMPORT(HttpStatus.BAD_REQUEST, "PL010", "본인의 플레이리스트는 가져올 수 없습니다"),

    /** 가져온(복사한) 플레이리스트를 공개로 전환 시도. */
    PLAYLIST_IMPORTED_CANNOT_SHARE(HttpStatus.BAD_REQUEST, "PL011", "가져온 플레이리스트는 공개할 수 없습니다"),

    // ─────────────────────────────────────────────
    // 추천 이력 (REC0xx)
    // ─────────────────────────────────────────────

    /** recommendationLogId에 해당하는 추천 로그를 찾을 수 없음. */
    RECOMMENDATION_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "REC001", "추천 이력을 찾을 수 없습니다"),

    /** 본인 추천 이력이 아닌 경우 접근 거부. */
    RECOMMENDATION_LOG_ACCESS_DENIED(HttpStatus.FORBIDDEN, "REC002", "추천 이력에 접근할 권한이 없습니다"),

    // ─────────────────────────────────────────────
    // 도장깨기 / 로드맵 (ROAD0xx)
    // ─────────────────────────────────────────────

    /** courseId(slug)에 해당하는 도장깨기 코스를 찾을 수 없음. */
    ROADMAP_COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROAD001", "존재하지 않는 도장깨기 코스입니다"),
    ROADMAP_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ROAD002", "도장깨기 인증 레코드를 찾을 수 없습니다"),

    /** quizId에 해당하는 퀴즈가 존재하지 않거나 PUBLISHED 상태가 아님. */
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "ROAD002", "존재하지 않거나 출제 중이 아닌 퀴즈입니다"),

    /** 관리자 — 코스 슬러그(course_id) 중복 등록 시도. */
    DUPLICATE_COURSE_ID(HttpStatus.CONFLICT, "ROAD003", "이미 사용 중인 코스 ID입니다"),

    /** 관리자 — movie_ids JSON 직렬화 실패. */
    INVALID_COURSE_MOVIE_IDS(HttpStatus.BAD_REQUEST, "ROAD004", "코스 영화 ID 형식이 올바르지 않습니다"),

    /** 관리자 — 허용되지 않은 퀴즈 상태 전이 시도. */
    INVALID_QUIZ_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "ROAD005", "허용되지 않은 퀴즈 상태 전이입니다"),

    /** 도장깨기 — 이미 완료 처리한 영화를 중복 완료 시도. */
    ALREADY_VERIFIED_MOVIE(HttpStatus.CONFLICT, "ROAD006", "이미 완료 처리한 영화입니다"),

    /** 도장깨기 — 리뷰 인증 기록을 찾을 수 없음 (AI 운영 — 리뷰 인증 탭). */
    COURSE_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ROAD007", "리뷰 인증 기록을 찾을 수 없습니다"),

    /** 도장깨기 — REVIEW 타입이 아닌 인증 기록에 리뷰 인증 액션을 시도. */
    NOT_REVIEW_VERIFICATION(HttpStatus.BAD_REQUEST, "ROAD008", "리뷰 인증 기록이 아닙니다"),

    // ─────────────────────────────────────────────
    // 영화 티켓 추첨 (LTR0xx) — 2026-04-28 신규 (관리자 추첨 관리 EP 도입)
    // ─────────────────────────────────────────────

    /**
     * lotteryId 또는 cycleYearMonth 에 해당하는 추첨 회차를 찾을 수 없음.
     * 관리자 회차 상세/수정/추첨 EP 가 모두 이 코드를 공유한다.
     */
    LOTTERY_NOT_FOUND(HttpStatus.NOT_FOUND, "LTR001", "추첨 회차를 찾을 수 없습니다"),

    /**
     * 회차 상태가 추첨 가능 상태(PENDING/DRAWING)가 아닌 경우.
     * 예) COMPLETED 회차에 수동 추첨을 다시 트리거하려는 시도.
     */
    LOTTERY_INVALID_STATE(HttpStatus.BAD_REQUEST, "LTR002", "현재 회차 상태에서는 수행할 수 없는 작업입니다"),

    /**
     * 관리자 회차 수정 시 winner_count 가 음수이거나 응모자 수보다 큰 등 비정상 값.
     * 정확한 사유는 message 파라미터로 덮어써서 전달한다.
     */
    LOTTERY_INVALID_WINNER_COUNT(HttpStatus.BAD_REQUEST, "LTR003", "당첨자 수가 올바르지 않습니다"),

    /** 회차 cycle_year_month 형식이 'YYYY-MM' 가 아닌 경우. */
    LOTTERY_INVALID_CYCLE_FORMAT(HttpStatus.BAD_REQUEST, "LTR004", "회차 형식은 YYYY-MM 이어야 합니다");

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
