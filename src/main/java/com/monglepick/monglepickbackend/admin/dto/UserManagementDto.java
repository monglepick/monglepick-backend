package com.monglepick.monglepickbackend.admin.dto;

import java.time.LocalDateTime;

/**
 * 관리자 사용자 관리 API DTO 모음.
 *
 * <p>회원 목록 조회, 상세 조회, 역할 변경, 계정 정지/복구,
 * 활동 이력, 포인트 내역, 결제 내역 API에서 사용되는 DTO를 모두 정의한다.</p>
 *
 * <p>모든 DTO는 Java 17+ record 클래스를 사용하여 불변 객체로 정의한다.</p>
 *
 * <h3>포함된 DTO 목록</h3>
 * <ul>
 *   <li>{@link UserListResponse} — 사용자 목록 응답 (페이징)</li>
 *   <li>{@link UserDetailResponse} — 사용자 상세 응답 (포인트·활동 카운트 포함)</li>
 *   <li>{@link RoleUpdateRequest} — 역할 변경 요청</li>
 *   <li>{@link SuspendRequest} — 계정 정지 요청 (사유 포함)</li>
 *   <li>{@link ActivityResponse} — 최근 활동 이력 응답</li>
 *   <li>{@link PointHistoryResponse} — 포인트 변동 이력 응답</li>
 *   <li>{@link PaymentHistoryResponse} — 결제 이력 응답</li>
 * </ul>
 */
public class UserManagementDto {

    // ──────────────────────────────────────────────
    // 응답 DTO
    // ──────────────────────────────────────────────

    /**
     * 사용자 목록 조회 응답 DTO.
     *
     * <p>관리자 회원 목록 화면에서 페이징 테이블로 표시되는 요약 정보.
     * 상세 정보(포인트, 활동 카운트 등)는 포함하지 않으며 {@link UserDetailResponse}에서 제공한다.</p>
     *
     * @param userId        사용자 고유 ID (VARCHAR 50)
     * @param email         이메일 주소
     * @param nickname      닉네임
     * @param userRole      역할 (USER, ADMIN)
     * @param status        계정 상태 (ACTIVE, SUSPENDED, LOCKED)
     * @param provider      로그인 제공자 (LOCAL, GOOGLE, KAKAO, NAVER)
     * @param createdAt     가입 일시
     * @param lastLoginAt   최종 로그인 일시 (미로그인 시 null)
     */
    public record UserListResponse(
            String userId,
            String email,
            String nickname,
            String userRole,
            String status,
            String provider,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt
    ) {}

    /**
     * 사용자 상세 조회 응답 DTO.
     *
     * <p>관리자 사용자 상세 화면에서 사용한다.
     * 기본 프로필 정보 + 포인트/등급 + 활동 통계(게시글·리뷰·댓글 건수)를 포함한다.</p>
     *
     * @param userId           사용자 고유 ID
     * @param email            이메일 주소
     * @param nickname         닉네임
     * @param userRole         역할 (USER, ADMIN)
     * @param status           계정 상태 (ACTIVE, SUSPENDED, LOCKED)
     * @param provider         로그인 제공자 (LOCAL, GOOGLE, KAKAO, NAVER)
     * @param profileImageUrl  프로필 이미지 URL (없으면 null)
     * @param pointBalance     현재 포인트 잔액 (user_points.balance)
     * @param totalEarned      누적 획득 포인트 (user_points.total_earned)
     * @param gradeName        등급 이름 (NORMAL, BRONZE, SILVER, GOLD, PLATINUM; 포인트 미생성 시 "NORMAL")
     * @param postCount        작성 게시글 수
     * @param reviewCount      작성 리뷰 수
     * @param commentCount     작성 댓글 수 (소프트 삭제 제외)
     * @param suspendReason    정지 사유 (ACTIVE 상태면 null)
     * @param suspendedAt      정지 일시 (ACTIVE 상태면 null)
     * @param createdAt        가입 일시
     * @param lastLoginAt      최종 로그인 일시 (미로그인 시 null)
     */
    public record UserDetailResponse(
            String userId,
            String email,
            String nickname,
            String userRole,
            String status,
            String provider,
            String profileImageUrl,
            Integer pointBalance,
            Integer totalEarned,
            String gradeName,
            long postCount,
            long reviewCount,
            long commentCount,
            String suspendReason,
            LocalDateTime suspendedAt,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt
    ) {}

    // ──────────────────────────────────────────────
    // 요청 DTO
    // ──────────────────────────────────────────────

    /**
     * 사용자 역할 변경 요청 DTO.
     *
     * <p>관리자가 특정 사용자의 역할을 USER ↔ ADMIN으로 변경할 때 사용한다.
     * role 값은 {@code UserRole} enum 이름과 일치해야 한다.</p>
     *
     * @param role 변경할 역할 ("USER" 또는 "ADMIN")
     */
    public record RoleUpdateRequest(
            String role
    ) {}

    /**
     * 계정 정지 요청 DTO.
     *
     * <p>관리자가 사용자 계정을 정지할 때 정지 사유 및 임시 정지 기간을 함께 전달한다.
     * {@code reason}은 선택 입력이며, 없으면 기본 메시지가 적용된다.
     * {@code durationDays}가 null이면 영구 정지, 양수이면 해당 일수 후 자동 복구 대상.</p>
     *
     * @param reason       정지 사유 (null 허용, 최대 500자)
     * @param durationDays 임시 정지 일수 (null=영구, 1 이상 정수)
     */
    public record SuspendRequest(
            String reason,
            Integer durationDays
    ) {}

    /**
     * 계정 제재 이력 단일 항목 응답 DTO (user_status 테이블 기반).
     */
    public record SuspensionHistoryResponse(
            Long userStatusId,
            String status,
            java.time.LocalDateTime suspendedAt,
            java.time.LocalDateTime suspendedUntil,
            String suspendReason,
            String suspendedBy,
            java.time.LocalDateTime createdAt
    ) {}

    /**
     * 관리자 수동 포인트 지급/회수 요청 DTO.
     *
     * <p>{@code amount} 양수: 지급(획득, point_type='bonus'),
     * 음수: 회수(차감, point_type='revoke'), 0: 400 실패.</p>
     *
     * <p>{@code reason}은 PointsHistory.description에 기록되어 관리자 감사 로그로 남는다.
     * CS 보상 처리, 운영 사고 복구, 프로모션 수동 지급 등에 활용.</p>
     *
     * @param amount 변동량 (양수=지급, 음수=회수, 0 금지)
     * @param reason 지급/회수 사유 (필수, 최대 300자)
     */
    public record ManualPointAdjustRequest(
            /*
             * 포인트 변동량 (양수=지급, 음수=회수, 0 금지).
             *
             * <p>상한/하한을 두는 이유는 {@code AdminPaymentDto.AdminManualPointRequest#amount} 와 동일하다.
             * Jackson 직렬화 단계에서 {@code Integer} 범위를 벗어난 값이 들어오면 500 응답으로 떨어지므로
             * 현실적인 단일 트랜잭션 한도(±1억P)로 사전에 차단한다.</p>
             */
            @jakarta.validation.constraints.NotNull(message = "amount는 필수입니다.")
            @jakarta.validation.constraints.Min(value = -100_000_000, message = "amount는 -1억 이상이어야 합니다.")
            @jakarta.validation.constraints.Max(value = 100_000_000, message = "amount는 1억 이하여야 합니다.")
            Integer amount,
            @jakarta.validation.constraints.NotBlank(message = "사유는 필수입니다.")
            @jakarta.validation.constraints.Size(max = 300, message = "사유는 300자 이하여야 합니다.")
            String reason
    ) {}

    /**
     * 관리자 수동 포인트 처리 결과 응답 DTO.
     */
    public record ManualPointResponse(
            String userId,
            Integer deltaApplied,
            Integer balanceBefore,
            Integer balanceAfter,
            String pointType,
            String reason,
            Long historyId
    ) {}

    /**
     * 관리자 수동 AI 이용권(쿠폰) 발급 요청 DTO.
     *
     * <p>특정 사용자의 {@code user_ai_quota.purchased_ai_tokens}를 지정 수량만큼 증가시킨다.
     * 사과 보상, 마케팅 캠페인, 운영 사고 복구 등에 사용.</p>
     *
     * @param count  발급할 이용권 수 (1 이상 정수, 필수)
     * @param reason 발급 사유 (필수, 최대 300자)
     */
    public record GrantAiTokenRequest(
            @jakarta.validation.constraints.NotNull(message = "count는 필수입니다.")
            @jakarta.validation.constraints.Min(value = 1, message = "최소 1장 이상 발급해야 합니다.")
            Integer count,
            @jakarta.validation.constraints.NotBlank(message = "사유는 필수입니다.")
            @jakarta.validation.constraints.Size(max = 300, message = "사유는 300자 이하여야 합니다.")
            String reason
    ) {}

    /**
     * 관리자 수동 이용권 발급 응답 DTO.
     *
     * @param userId           대상 사용자 ID
     * @param grantedCount     발급된 이용권 수
     * @param tokensBefore     발급 전 purchased_ai_tokens
     * @param tokensAfter      발급 후 purchased_ai_tokens
     * @param reason           발급 사유
     */
    public record GrantAiTokenResponse(
            String userId,
            Integer grantedCount,
            Integer tokensBefore,
            Integer tokensAfter,
            String reason
    ) {}

    // ──────────────────────────────────────────────
    // 이력 응답 DTO
    // ──────────────────────────────────────────────

    /**
     * 사용자 최근 활동 이력 응답 DTO.
     *
     * <p>게시글 작성, 리뷰 작성, 댓글 작성 이력을 통합하여 최신순으로 제공한다.
     * type 필드로 어떤 종류의 활동인지 구분할 수 있다.</p>
     *
     * @param type        활동 유형 ("POST", "REVIEW", "COMMENT")
     * @param title       활동 제목 (게시글 제목, 리뷰 영화명, 댓글 내용 앞 30자)
     * @param description 활동 상세 설명 (nullable)
     * @param referenceId 참조 ID (게시글 ID, 리뷰 ID, 댓글 ID 등)
     * @param createdAt   활동 발생 일시
     */
    public record ActivityResponse(
            String type,
            String title,
            String description,
            Long referenceId,
            LocalDateTime createdAt
    ) {}

    /**
     * 포인트 변동 이력 응답 DTO.
     *
     * <p>points_history 테이블의 레코드를 관리자 화면에 맞게 정제하여 반환한다.
     * 양수 pointChange는 획득, 음수는 사용/차감을 의미한다.</p>
     *
     * @param id          포인트 이력 ID (points_history_id)
     * @param pointType   포인트 변동 유형 (earn, spend, bonus, expire, refund, revoke)
     * @param pointChange 변동량 (양수: 획득, 음수: 사용)
     * @param pointAfter  변동 후 잔액 스냅샷
     * @param description 변동 사유 설명
     * @param actionType  활동 유형 코드 (nullable, 예: REVIEW_CREATE)
     * @param createdAt   변동 발생 일시
     */
    public record PointHistoryResponse(
            Long id,
            String pointType,
            Integer pointChange,
            Integer pointAfter,
            String description,
            String actionType,
            LocalDateTime createdAt
    ) {}

    /**
     * 결제 이력 응답 DTO.
     *
     * <p>payment_orders 테이블의 레코드를 관리자 화면에 맞게 정제하여 반환한다.</p>
     *
     * @param orderId       주문 UUID (payment_order_id)
     * @param orderType     주문 유형 (POINT_PACK, SUBSCRIPTION)
     * @param amount        결제 금액 (KRW 원 단위)
     * @param pointsAmount  지급 포인트 수량 (포인트팩 주문 시에만 설정, 구독은 null)
     * @param status        결제 상태 (PENDING, COMPLETED, FAILED, REFUNDED, COMPENSATION_FAILED)
     * @param pgProvider    PG사 이름 (예: "tosspayments", nullable)
     * @param pgTransactionId PG사 거래 ID (결제 완료 후 설정, nullable)
     * @param createdAt     주문 생성 일시
     * @param completedAt   결제 완료 일시 (PENDING/FAILED 상태면 null)
     */
    public record PaymentHistoryResponse(
            String orderId,
            String orderType,
            Integer amount,
            Integer pointsAmount,
            String status,
            String pgProvider,
            String pgTransactionId,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {}
}
