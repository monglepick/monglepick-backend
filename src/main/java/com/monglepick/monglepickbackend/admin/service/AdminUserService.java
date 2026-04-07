package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ActivityResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.PaymentHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.PointHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.RoleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.SuspendRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.UserDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.UserListResponse;
import com.monglepick.monglepickbackend.admin.mapper.AdminUserMapper;
import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 관리자 사용자 관리 서비스.
 *
 * <p>회원 목록/상세 조회, 역할 변경, 계정 정지/복구,
 * 활동 이력·포인트 내역·결제 내역 조회 기능을 제공한다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 모든 조회 메서드에 적용</li>
 *   <li>쓰기 메서드({@code updateUserRole}, {@code suspendUser}, {@code activateUser}):
 *       {@code @Transactional} 오버라이드</li>
 * </ul>
 *
 * <h3>포인트 정보 처리</h3>
 * <p>user_points 레코드가 없는 사용자(가입 직후 초기화 미완료 등)에 대해
 * balance=0, grade="NORMAL"로 안전하게 fallback 처리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    // ── 의존성 주입 ──
    /** 사용자 CRUD — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;

    /** 관리자 전용 사용자 검색 — MyBatis Mapper (동적 SQL <if> 기반 통합 쿼리) */
    private final AdminUserMapper adminUserMapper;

    /** 사용자 포인트 잔액·등급 조회 */
    private final UserPointRepository userPointRepository;

    /** 포인트 변동 이력 조회 */
    private final PointsHistoryRepository pointsHistoryRepository;

    /** 결제 주문 이력 조회 */
    private final PaymentOrderRepository paymentOrderRepository;

    /** 게시글·댓글 통합 Mapper — 관리자 활동 조회 (JpaRepository 폐기, 설계서 §15) */
    private final PostMapper postMapper;

    /** 리뷰 수 카운트/활동 조회 — MyBatis Mapper (§15) */
    private final ReviewMapper reviewMapper;

    // ──────────────────────────────────────────────
    // 조회 메서드 (readOnly = true 상속)
    // ──────────────────────────────────────────────

    /**
     * 사용자 목록을 조회한다.
     *
     * <p>keyword, status, role 파라미터 조합에 따라 적절한 Repository 쿼리를 선택한다.
     * 소프트 삭제(is_deleted=true)된 탈퇴 회원은 항상 제외한다.</p>
     *
     * <h4>필터 조합 우선순위</h4>
     * <ol>
     *   <li>keyword + status + role → 3중 복합 검색</li>
     *   <li>keyword + status → 키워드+상태 검색</li>
     *   <li>keyword + role → 키워드+역할 검색</li>
     *   <li>keyword → 키워드 검색</li>
     *   <li>status + role → 상태+역할 필터</li>
     *   <li>status → 상태 필터</li>
     *   <li>role → 역할 필터</li>
     *   <li>(없음) → 전체 조회</li>
     * </ol>
     *
     * @param keyword  닉네임/이메일 검색 키워드 (null 또는 빈 문자열이면 미적용)
     * @param status   계정 상태 필터 (null이면 미적용, "ACTIVE"/"SUSPENDED"/"LOCKED")
     * @param role     역할 필터 (null이면 미적용, "USER"/"ADMIN")
     * @param pageable 페이징 정보
     * @return 필터 조건에 맞는 사용자 목록 페이지
     */
    public Page<UserListResponse> getUsers(String keyword, String status, String role, Pageable pageable) {
        // 필터 파싱 — null 또는 빈 문자열이면 해당 필터 미적용
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasStatus  = StringUtils.hasText(status);
        boolean hasRole    = StringUtils.hasText(role);

        // 상태 enum 파싱 (잘못된 값이면 INVALID_INPUT 예외)
        User.UserStatus statusEnum = null;
        if (hasStatus) {
            try {
                statusEnum = User.UserStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 status 값입니다: " + status + " (허용값: ACTIVE, SUSPENDED, LOCKED)");
            }
        }

        // 역할 enum 파싱 (잘못된 값이면 INVALID_INPUT 예외)
        UserRole roleEnum = null;
        if (hasRole) {
            try {
                roleEnum = UserRole.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 role 값입니다: " + role + " (허용값: USER, ADMIN)");
            }
        }

        /*
         * AdminUserMapper로 통합 동적 SQL 호출 (JPA/MyBatis 하이브리드 §15).
         *  - 기존 8개 분기(case 1~8)는 XML의 <if> 조건 조합으로 한 번에 처리된다.
         *  - Spring Page 대신 List + count를 받아 PageImpl로 직접 조립한다.
         */
        String normalizedKeyword = hasKeyword ? keyword : null;
        int offset = (int) pageable.getOffset();
        int size   = pageable.getPageSize();

        List<User> users = adminUserMapper.searchUsers(normalizedKeyword, statusEnum, roleEnum, offset, size);
        long total = adminUserMapper.countSearchUsers(normalizedKeyword, statusEnum, roleEnum);

        List<UserListResponse> content = users.stream()
                .map(this::toUserListResponse)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 사용자 상세 정보를 조회한다.
     *
     * <p>기본 프로필 + 포인트/등급 + 활동 카운트(게시글·리뷰·댓글)를 한 번에 반환한다.
     * user_points 레코드가 없어도 balance=0, grade="NORMAL"로 안전하게 처리한다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자 상세 응답 DTO
     * @throws BusinessException USER_NOT_FOUND — 해당 userId의 사용자가 없거나 탈퇴한 경우
     */
    public UserDetailResponse getUserDetail(String userId) {
        // 사용자 조회 — 탈퇴 회원 포함 조회 후 isDeleted 체크 (MyBatis, null 반환 시 예외)
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "사용자를 찾을 수 없습니다: " + userId);
        }

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "이미 탈퇴한 사용자입니다: " + userId);
        }

        // 포인트 정보 조회 — 레코드 없으면 balance=0, grade="NORMAL" fallback
        UserPoint userPoint = userPointRepository.findByUserId(userId).orElse(null);
        Integer balance    = (userPoint != null) ? userPoint.getBalance()    : 0;
        Integer totalEarned = (userPoint != null) ? userPoint.getTotalEarned() : 0;
        // grade가 null이면 getGradeCode()가 "NORMAL"을 반환하므로 안전
        String gradeName   = (userPoint != null) ? userPoint.getGradeCode() : "NORMAL";

        // 활동 카운트 집계 — 게시글·리뷰는 전체, 댓글은 소프트 삭제 제외 (MyBatis §15.4)
        long postCount    = postMapper.countByUserId(userId);
        long reviewCount  = reviewMapper.countByUserId(userId);
        long commentCount = postMapper.countCommentsByUserIdAndIsDeletedFalse(userId);

        log.debug("[AdminUserService] getUserDetail — userId={}, balance={}, grade={}, posts={}, reviews={}, comments={}",
                userId, balance, gradeName, postCount, reviewCount, commentCount);

        return new UserDetailResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getName(),
                user.getUserRole() != null ? user.getUserRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getProvider() != null ? user.getProvider().name() : null,
                user.getProfileImage(),
                balance,
                totalEarned,
                gradeName,
                postCount,
                reviewCount,
                commentCount,
                user.getSuspendReason(),
                user.getSuspendedAt(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    // ──────────────────────────────────────────────
    // 쓰기 메서드 (@Transactional 오버라이드)
    // ──────────────────────────────────────────────

    /**
     * 사용자 역할을 변경한다 (USER ↔ ADMIN).
     *
     * <p>역할 변경은 즉시 적용되며, 이미 동일한 역할인 경우에도 정상 처리된다.
     * 향후 감사 로그(AdminAuditLog) 기록이 필요하다면 이 메서드에 추가한다.</p>
     *
     * @param userId  역할을 변경할 사용자 ID
     * @param request 변경할 역할 정보 ({@code role}: "USER" 또는 "ADMIN")
     * @return 변경 후 사용자 상세 응답 DTO
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     * @throws BusinessException INVALID_INPUT  — role 값이 USER/ADMIN이 아닌 경우
     */
    @Transactional
    public UserDetailResponse updateUserRole(String userId, RoleUpdateRequest request) {
        // 사용자 조회
        User user = findActiveUser(userId);

        // 역할 enum 파싱
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "유효하지 않은 역할 값입니다: " + request.role() + " (허용값: USER, ADMIN)");
        }

        // 도메인 메서드로 in-memory 변경 후 MyBatis UPDATE 명시 호출
        // (JPA/MyBatis 하이브리드 §15 — MyBatis는 dirty checking 미지원)
        user.updateUserRole(newRole);
        userMapper.update(user);

        log.info("[AdminUserService] 역할 변경 — userId={}, newRole={}", userId, newRole);

        // 저장 후 상세 정보 반환
        return getUserDetail(userId);
    }

    /**
     * 사용자 계정을 정지한다.
     *
     * <p>{@link User#suspend(String)}을 호출하여 status=SUSPENDED로 변경하고
     * suspendedAt과 suspendReason을 기록한다.</p>
     *
     * @param userId  정지할 사용자 ID
     * @param request 정지 사유 ({@code reason}: null 허용)
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     */
    @Transactional
    public void suspendUser(String userId, SuspendRequest request) {
        User user = findActiveUser(userId);

        // User 엔티티의 도메인 메서드 suspend() 호출 — status, suspendedAt, suspendReason 갱신
        String reason = (request != null && StringUtils.hasText(request.reason()))
                ? request.reason()
                : "관리자에 의해 정지되었습니다";
        user.suspend(reason);

        // MyBatis는 dirty checking 미지원 — 명시적으로 UPDATE 호출 (§15)
        userMapper.update(user);

        log.info("[AdminUserService] 계정 정지 — userId={}, reason={}", userId, reason);
    }

    /**
     * 정지된 사용자 계정을 복구(활성화)한다.
     *
     * <p>{@link User#activate()}를 호출하여 status=ACTIVE로 변경하고
     * suspendedAt과 suspendReason을 null로 초기화한다.</p>
     *
     * @param userId 복구할 사용자 ID
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     */
    @Transactional
    public void activateUser(String userId) {
        // 탈퇴 회원은 복구 불가이므로 findActiveUser 대신 탈퇴 여부만 체크 (MyBatis, null → 예외)
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "사용자를 찾을 수 없습니다: " + userId);
        }

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "이미 탈퇴한 사용자입니다. 복구가 불가능합니다: " + userId);
        }

        // User 엔티티의 도메인 메서드 activate() 호출 — status=ACTIVE, suspendedAt/suspendReason 초기화
        user.activate();

        // MyBatis는 dirty checking 미지원 — 명시적으로 UPDATE 호출 (§15)
        userMapper.update(user);

        log.info("[AdminUserService] 계정 복구 — userId={}", userId);
    }

    // ──────────────────────────────────────────────
    // 이력 조회 메서드 (readOnly = true 상속)
    // ──────────────────────────────────────────────

    /**
     * 사용자의 최근 활동 이력을 조회한다.
     *
     * <p>게시글 작성, 리뷰 작성, 댓글 작성을 각각 최신 N개씩 조회한 후
     * 통합하여 createdAt 기준 내림차순으로 정렬하여 반환한다.</p>
     *
     * <p>각 유형별로 최대 {@code pageable.getPageSize()} 건씩 조회하여 통합하므로,
     * 실제 반환 건수는 요청한 size의 최대 3배일 수 있다.
     * 프론트엔드에서는 별도 페이지네이션 없이 통합 목록으로 표시할 것을 권장한다.</p>
     *
     * @param userId   조회할 사용자 ID
     * @param pageable 각 유형별 페이징 정보 (page=0, size=N 권장)
     * @return 통합 활동 이력 목록 (createdAt 내림차순)
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     */
    public List<ActivityResponse> getUserActivity(String userId, Pageable pageable) {
        // 사용자 존재 여부 확인
        validateUserExists(userId);

        List<ActivityResponse> activities = new ArrayList<>();

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        // 게시글 활동 조회 — MyBatis Mapper (§15.4)
        postMapper.findByUserId(userId, offset, limit)
                .forEach(post -> activities.add(new ActivityResponse(
                        "POST",
                        post.getTitle(),
                        post.getCategory() != null ? post.getCategory().name() : null,
                        post.getPostId(),
                        post.getCreatedAt()
                )));

        // 리뷰 활동 조회 — MyBatis Mapper (§15)
        reviewMapper.findByUserId(userId, offset, limit)
                .forEach(review -> activities.add(new ActivityResponse(
                        "REVIEW",
                        review.getMovieId(),
                        review.getRating() != null
                                ? "별점 " + review.getRating() + "점"
                                : null,
                        review.getReviewId(),
                        review.getCreatedAt()
                )));

        // 댓글 활동 조회 — MyBatis Mapper (소프트 삭제 포함)
        postMapper.findCommentsByUserId(userId, offset, limit)
                .forEach(comment -> {
                    // 댓글 내용 앞 30자 추출 (긴 내용 truncate)
                    String content = comment.getContent();
                    String title = (content != null && content.length() > 30)
                            ? content.substring(0, 30) + "..."
                            : content;
                    activities.add(new ActivityResponse(
                            "COMMENT",
                            title,
                            Boolean.TRUE.equals(comment.getIsDeleted()) ? "[삭제된 댓글]" : null,
                            comment.getPostCommentId(),
                            comment.getCreatedAt()
                    ));
                });

        // 통합 후 createdAt 내림차순 정렬 (최신 활동이 먼저)
        activities.sort(Comparator.comparing(ActivityResponse::createdAt).reversed());

        return activities;
    }

    /**
     * 사용자의 포인트 변동 이력을 최신순으로 페이징 조회한다.
     *
     * <p>points_history 테이블에서 해당 사용자의 모든 포인트 변동 이력을 반환한다.
     * INSERT-ONLY 원장이므로 취소/환불도 별도 레코드로 존재한다.</p>
     *
     * @param userId   조회할 사용자 ID
     * @param pageable 페이징 정보
     * @return 포인트 변동 이력 페이지
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     */
    public Page<PointHistoryResponse> getUserPointHistory(String userId, Pageable pageable) {
        // 사용자 존재 여부 확인
        validateUserExists(userId);

        // PointsHistory → PointHistoryResponse DTO 변환
        return pointsHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toPointHistoryResponse);
    }

    /**
     * 사용자의 결제 이력을 최신순으로 페이징 조회한다.
     *
     * <p>payment_orders 테이블에서 해당 사용자의 모든 결제 주문 이력을 반환한다.
     * PENDING, COMPLETED, FAILED, REFUNDED, COMPENSATION_FAILED 상태 모두 포함한다.</p>
     *
     * @param userId   조회할 사용자 ID
     * @param pageable 페이징 정보
     * @return 결제 이력 페이지
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     */
    public Page<PaymentHistoryResponse> getUserPaymentHistory(String userId, Pageable pageable) {
        // 사용자 존재 여부 확인
        validateUserExists(userId);

        // PaymentOrder → PaymentHistoryResponse DTO 변환
        return paymentOrderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toPaymentHistoryResponse);
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ──────────────────────────────────────────────

    /**
     * 활성 상태의 사용자를 조회한다.
     *
     * <p>탈퇴(is_deleted=true)한 사용자는 조회 대상에서 제외하여
     * USER_NOT_FOUND 예외를 발생시킨다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @return 활성 사용자 엔티티
     * @throws BusinessException USER_NOT_FOUND — 사용자가 없거나 탈퇴한 경우
     */
    private User findActiveUser(String userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "사용자를 찾을 수 없습니다: " + userId);
        }

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "이미 탈퇴한 사용자입니다: " + userId);
        }
        return user;
    }

    /**
     * 사용자 존재 여부를 검증한다 (이력 조회 시 사전 확인용).
     *
     * <p>탈퇴 회원은 이력 조회가 가능하도록 isDeleted 체크를 하지 않는다.
     * 사용자가 아예 없는 경우만 예외를 발생시킨다.</p>
     *
     * @param userId 확인할 사용자 ID
     * @throws BusinessException USER_NOT_FOUND — 사용자가 존재하지 않는 경우
     */
    private void validateUserExists(String userId) {
        if (!userMapper.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "사용자를 찾을 수 없습니다: " + userId);
        }
    }

    /**
     * User 엔티티를 UserListResponse DTO로 변환한다.
     *
     * @param user 사용자 엔티티
     * @return 사용자 목록 응답 DTO
     */
    private UserListResponse toUserListResponse(User user) {
        return new UserListResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getUserRole() != null ? user.getUserRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getProvider() != null ? user.getProvider().name() : null,
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    /**
     * PointsHistory 엔티티를 PointHistoryResponse DTO로 변환한다.
     *
     * @param history 포인트 변동 이력 엔티티
     * @return 포인트 이력 응답 DTO
     */
    private PointHistoryResponse toPointHistoryResponse(PointsHistory history) {
        return new PointHistoryResponse(
                history.getPointsHistoryId(),
                history.getPointType(),
                history.getPointChange(),
                history.getPointAfter(),
                history.getDescription(),
                history.getActionType(),
                history.getCreatedAt()
        );
    }

    /**
     * PaymentOrder 엔티티를 PaymentHistoryResponse DTO로 변환한다.
     *
     * @param order 결제 주문 엔티티
     * @return 결제 이력 응답 DTO
     */
    private PaymentHistoryResponse toPaymentHistoryResponse(PaymentOrder order) {
        return new PaymentHistoryResponse(
                order.getPaymentOrderId(),
                order.getOrderType() != null ? order.getOrderType().name() : null,
                order.getAmount(),
                order.getPointsAmount(),
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getPgProvider(),
                order.getPgTransactionId(),
                order.getCreatedAt(),
                order.getCompletedAt()
        );
    }
}
