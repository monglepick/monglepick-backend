package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ActivityResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.GrantAiTokenRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.GrantAiTokenResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ManualPointAdjustRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.ManualPointResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.PaymentHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.PointHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.RoleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.SuspendRequest;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.SuspensionHistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.UserDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.UserManagementDto.UserListResponse;
import com.monglepick.monglepickbackend.admin.mapper.AdminUserMapper;
import com.monglepick.monglepickbackend.admin.repository.AdminUserStatusRepository;
import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository;
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

    /**
     * 사용자 제재 이력(user_status) JPA 리포지토리.
     *
     * <p>계정 정지/복구 이력을 INSERT-ONLY 원장으로 기록한다.
     * UserStatus 엔티티는 김민규 user 도메인이지만, admin 이력 기록은 별도 관리.</p>
     */
    private final AdminUserStatusRepository adminUserStatusRepository;

    /** AI 쿼터(이용권) 레포지토리 — 관리자 수동 이용권 발급에서 사용 */
    private final UserAiQuotaRepository userAiQuotaRepository;

    /**
     * 관리자 감사 로그 서비스 — 역할 변경/정지/복구/수동 포인트/이용권 발급 5개
     * 쓰기 액션의 성공 시점에 호출하여 admin_audit_logs 에 흔적을 남긴다.
     * REQUIRES_NEW 트랜잭션이므로 감사 로그 실패는 업무 트랜잭션에 영향을 주지 않는다.
     * (2026-04-09 P1-1 추가)
     */
    private final AdminAuditService adminAuditService;

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

        // 변경 전 역할 스냅샷 — 감사 로그 beforeData 로 사용
        UserRole previousRole = user.getUserRole();

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

        // 감사 로그 — 권한 상승/하강은 보안 크리티컬 액션이므로 반드시 기록 (beforeData/afterData 포함)
        adminAuditService.log(
                AdminAuditService.ACTION_USER_ROLE_UPDATE,
                AdminAuditService.TARGET_USER,
                userId,
                String.format("사용자 %s 역할 변경 — %s → %s",
                        userId,
                        previousRole != null ? previousRole.name() : "NULL",
                        newRole.name()),
                String.format("{\"role\":\"%s\"}", previousRole != null ? previousRole.name() : ""),
                String.format("{\"role\":\"%s\"}", newRole.name())
        );

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

        // 사유 결정 — 없으면 기본 메시지
        String reason = (request != null && StringUtils.hasText(request.reason()))
                ? request.reason()
                : "관리자에 의해 정지되었습니다";

        // 임시 정지 기간(durationDays) 처리 — null/0 이하면 영구 정지
        Integer days = (request != null) ? request.durationDays() : null;
        java.time.LocalDateTime suspendedUntil = null;
        if (days != null && days > 0) {
            suspendedUntil = java.time.LocalDateTime.now().plusDays(days);
            user.suspendUntil(reason, suspendedUntil);
        } else {
            user.suspend(reason);
        }

        /*
         * 정지 상태만 단건 UPDATE — 2026-04-08 버그 수정.
         * 기존 userMapper.update(user) 경로는 12개 컬럼을 일괄 UPDATE 하는데,
         * 일부 환경에서 실제 SQL 이 날아가지 않는 현상이 보고되어(화면에는 정지 반영,
         * DB 는 그대로) 전용 쿼리 updateSuspensionStatus 로 교체한다.
         * 반환값(영향 행 수)이 0 이면 명시적으로 예외를 던져 실패를 가시화한다.
         */
        int affected = userMapper.updateSuspensionStatus(
                userId,
                User.UserStatus.SUSPENDED.name(),
                user.getSuspendedAt(),
                suspendedUntil,
                reason
        );
        if (affected == 0) {
            log.error("[AdminUserService] 계정 정지 UPDATE 실패 — userId={} (영향 행 0)", userId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "계정 정지 처리 중 DB 갱신에 실패했습니다: " + userId);
        }

        // user_status 이력 테이블에 정지 레코드 INSERT-ONLY 기록
        String adminId = resolveCurrentAdminId();
        com.monglepick.monglepickbackend.domain.user.entity.UserStatus statusHistory =
                com.monglepick.monglepickbackend.domain.user.entity.UserStatus.builder()
                        .userId(userId)
                        .status(com.monglepick.monglepickbackend.domain.user.entity.UserStatus
                                .AccountStatus.SUSPENDED)
                        .suspendedAt(user.getSuspendedAt())
                        .suspendedUntil(suspendedUntil)
                        .suspendReason(reason)
                        .suspendedBy(adminId)
                        .build();
        adminUserStatusRepository.save(statusHistory);

        log.info("[AdminUserService] 계정 정지 — userId={}, reason={}, until={}, adminId={}",
                userId, reason, suspendedUntil, adminId);

        // 감사 로그 — 사용자 제재는 민원·법적 분쟁 가능성이 있으므로 반드시 기록
        // user_status 이력은 김민규 도메인 원장이고, admin_audit_logs 는 운영 감사 관점의 별도 기록이다.
        String afterSnapshot = String.format(
                "{\"status\":\"SUSPENDED\",\"suspendedUntil\":%s,\"durationDays\":%s}",
                suspendedUntil != null ? "\"" + suspendedUntil + "\"" : "null",
                days != null ? days : "null"
        );
        adminAuditService.log(
                AdminAuditService.ACTION_USER_SUSPEND,
                AdminAuditService.TARGET_USER,
                userId,
                String.format("사용자 %s 계정 정지 (기간: %s, 사유: %s)",
                        userId,
                        suspendedUntil != null ? days + "일" : "영구",
                        reason),
                "{\"status\":\"ACTIVE\"}",
                afterSnapshot
        );
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

        // User 엔티티의 도메인 메서드 activate() 호출 — status=ACTIVE, suspendedAt/until/reason 초기화
        user.activate();

        /*
         * 복구 상태만 단건 UPDATE — suspendUser 와 동일한 이유로 전용 쿼리 사용 (2026-04-08).
         * ACTIVE 로 전환하면서 suspendedAt/suspendedUntil/suspendReason 을 모두 null 로 초기화한다.
         */
        int activated = userMapper.updateSuspensionStatus(
                userId,
                User.UserStatus.ACTIVE.name(),
                null,
                null,
                null
        );
        if (activated == 0) {
            log.error("[AdminUserService] 계정 복구 UPDATE 실패 — userId={} (영향 행 0)", userId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "계정 복구 처리 중 DB 갱신에 실패했습니다: " + userId);
        }

        // user_status 이력 테이블에 복구 레코드 INSERT-ONLY 기록
        String adminId = resolveCurrentAdminId();
        com.monglepick.monglepickbackend.domain.user.entity.UserStatus activateHistory =
                com.monglepick.monglepickbackend.domain.user.entity.UserStatus.builder()
                        .userId(userId)
                        .status(com.monglepick.monglepickbackend.domain.user.entity.UserStatus
                                .AccountStatus.ACTIVE)
                        .suspendedAt(null)
                        .suspendedUntil(null)
                        .suspendReason("관리자 계정 복구")
                        .suspendedBy(adminId)
                        .build();
        adminUserStatusRepository.save(activateHistory);

        log.info("[AdminUserService] 계정 복구 — userId={}, adminId={}", userId, adminId);

        // 감사 로그 — 정지 해제 (정지와 짝을 이루는 운영 액션이므로 기록 필수)
        adminAuditService.log(
                AdminAuditService.ACTION_USER_UNSUSPEND,
                AdminAuditService.TARGET_USER,
                userId,
                String.format("사용자 %s 계정 정지 해제 (관리자 복구)", userId),
                "{\"status\":\"SUSPENDED\"}",
                "{\"status\":\"ACTIVE\"}"
        );
    }

    /**
     * 관리자 수동 포인트 지급/회수.
     *
     * <p>양수: 지급(보너스), 음수: 회수. 0은 400 BAD_REQUEST.
     * PointsHistory에 INSERT-ONLY 원장으로 기록되며, user_points 잔액도 동시 갱신.
     * 회수 시 잔액 부족이면 400 에러.</p>
     *
     * <h4>기록 형식</h4>
     * <ul>
     *   <li>point_type: 양수이면 "bonus", 음수이면 "revoke"</li>
     *   <li>description: "[관리자 수동] " + request.reason()</li>
     *   <li>action_type: "ADMIN_MANUAL_ADJUST"</li>
     *   <li>reference_id: "admin_" + 현재 관리자 ID + "_" + System.currentTimeMillis()</li>
     * </ul>
     *
     * @param userId  대상 사용자 ID
     * @param request 변동량 + 사유
     * @return 처리 결과 (변동 전/후 잔액, history ID 등)
     * @throws BusinessException USER_NOT_FOUND / INVALID_INPUT (amount=0) / INSUFFICIENT_POINTS
     */
    @Transactional
    public ManualPointResponse adjustUserPoints(String userId, ManualPointAdjustRequest request) {
        // 사용자 존재 검증 (탈퇴 제외)
        findActiveUser(userId);

        int delta = request.amount();
        if (delta == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "변동량(amount)은 0이 될 수 없습니다");
        }

        // user_points 조회 — 비관적 쓰기 락으로 동시성 보호 (중복 조정 방지)
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "사용자 포인트 레코드가 없습니다: " + userId));

        int balanceBefore = userPoint.getBalance();
        java.time.LocalDate today = java.time.LocalDate.now();

        // 잔액 계산 + 도메인 메서드 호출
        if (delta > 0) {
            // 지급 — 활동 리워드 아님(isActivityReward=false) → earnedByActivity 미반영
            userPoint.addPoints(delta, today, false);
        } else {
            int toDeduct = -delta;
            if (balanceBefore < toDeduct) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "잔액 부족 — 회수 요청량=" + toDeduct + ", 현재 잔액=" + balanceBefore);
            }
            userPoint.deductPoints(toDeduct);
        }
        userPointRepository.save(userPoint);

        int balanceAfter = userPoint.getBalance();
        String pointType = (delta > 0) ? "bonus" : "revoke";
        String adminId = resolveCurrentAdminId();
        String referenceId = "admin_" + adminId + "_" + System.currentTimeMillis();
        String description = "[관리자 수동] " + request.reason();

        // PointsHistory INSERT-ONLY
        PointsHistory history = PointsHistory.builder()
                .userId(userId)
                .pointChange(delta)
                .pointAfter(balanceAfter)
                .pointType(pointType)
                .description(description)
                .referenceId(referenceId)
                .actionType("ADMIN_MANUAL_ADJUST")
                .baseAmount(null)
                .appliedMultiplier(null)
                .build();
        PointsHistory savedHistory = pointsHistoryRepository.save(history);

        log.info("[AdminUserService] 수동 포인트 조정 — userId={}, delta={}, before={}, after={}, adminId={}",
                userId, delta, balanceBefore, balanceAfter, adminId);

        // 감사 로그 — 금전 이동(포인트는 크레딧 경제의 기본 재화)이므로 필수 기록
        // PointsHistory 는 도메인 원장이고 admin_audit_logs 는 관리자 운영 감사 관점의 별도 기록이다.
        String beforeSnapshot = String.format("{\"balance\":%d}", balanceBefore);
        String afterSnapshot = String.format(
                "{\"balance\":%d,\"delta\":%d,\"pointType\":\"%s\",\"historyId\":%d}",
                balanceAfter, delta, pointType, savedHistory.getPointsHistoryId()
        );
        adminAuditService.log(
                AdminAuditService.ACTION_POINT_MANUAL,
                AdminAuditService.TARGET_USER,
                userId,
                String.format("사용자 %s 수동 포인트 %s %dP (before=%dP, after=%dP, 사유: %s)",
                        userId,
                        delta > 0 ? "지급" : "차감",
                        Math.abs(delta),
                        balanceBefore, balanceAfter,
                        request.reason()),
                beforeSnapshot,
                afterSnapshot
        );

        return new ManualPointResponse(
                userId,
                delta,
                balanceBefore,
                balanceAfter,
                pointType,
                request.reason(),
                savedHistory.getPointsHistoryId()
        );
    }

    /**
     * 관리자 수동 AI 이용권(쿠폰) 발급.
     *
     * <p>특정 사용자의 {@code user_ai_quota.purchased_ai_tokens}를 지정 수량만큼 증가시킨다.
     * 사과 보상, 마케팅 캠페인, 운영 사고 복구 등에 사용.</p>
     *
     * <p>비관적 락({@code findByUserIdWithLock})으로 동시성 보호.
     * AI 쿼터는 포인트가 아니므로 PointsHistory에 기록하지 않는다 (별도 도메인).</p>
     *
     * @param userId  대상 사용자 ID
     * @param request 발급 수량 + 사유
     * @return 발급 결과 (변동 전/후 이용권 수)
     * @throws BusinessException USER_NOT_FOUND — 사용자/쿼터 레코드 없음
     */
    @Transactional
    public GrantAiTokenResponse grantAiTokens(String userId, GrantAiTokenRequest request) {
        // 사용자 존재 검증 (탈퇴 제외)
        findActiveUser(userId);

        // user_ai_quota 비관적 락 조회 — 동시 발급 중복 방지
        UserAiQuota quota = userAiQuotaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "사용자 AI 쿼터 레코드가 없습니다: " + userId));

        int tokensBefore = quota.getPurchasedAiTokens() != null ? quota.getPurchasedAiTokens() : 0;
        quota.addPurchasedTokens(request.count());
        userAiQuotaRepository.save(quota);

        int tokensAfter = quota.getPurchasedAiTokens();
        String adminId = resolveCurrentAdminId();

        log.info("[AdminUserService] 수동 이용권 발급 — userId={}, count={}, before={}, after={}, reason={}, adminId={}",
                userId, request.count(), tokensBefore, tokensAfter, request.reason(), adminId);

        // 감사 로그 — AI 이용권(purchased_ai_tokens)은 금전 가치가 있는 자산이므로 필수 기록
        String beforeSnapshot = String.format("{\"purchasedAiTokens\":%d}", tokensBefore);
        String afterSnapshot = String.format(
                "{\"purchasedAiTokens\":%d,\"granted\":%d}", tokensAfter, request.count()
        );
        adminAuditService.log(
                AdminAuditService.ACTION_AI_TOKEN_GRANT,
                AdminAuditService.TARGET_USER,
                userId,
                String.format("사용자 %s AI 이용권 %d회 수동 발급 (before=%d, after=%d, 사유: %s)",
                        userId, request.count(),
                        tokensBefore, tokensAfter,
                        request.reason()),
                beforeSnapshot,
                afterSnapshot
        );

        return new GrantAiTokenResponse(
                userId,
                request.count(),
                tokensBefore,
                tokensAfter,
                request.reason()
        );
    }

    /**
     * 특정 사용자의 제재 이력을 최신순으로 조회한다.
     *
     * <p>user_status 테이블은 정지/복구 이력을 INSERT-ONLY 원장으로 보관한다.
     * 관리자 화면에서 제재 이력 패널에 표시할 때 사용.</p>
     *
     * @param userId 조회 대상 사용자 ID
     * @return 제재 이력 응답 DTO 목록 (createdAt DESC)
     */
    public List<SuspensionHistoryResponse> getSuspensionHistory(String userId) {
        // 사용자 존재 검증
        findActiveUser(userId);

        return adminUserStatusRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(h -> new SuspensionHistoryResponse(
                        h.getUserStatusId(),
                        h.getStatus() != null ? h.getStatus().name() : null,
                        h.getSuspendedAt(),
                        h.getSuspendedUntil(),
                        h.getSuspendReason(),
                        h.getSuspendedBy(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    /**
     * SecurityContextHolder에서 현재 관리자 ID 추출 (이력 기록용).
     *
     * <p>인증 정보가 없으면 "SYSTEM"을 반환한다.</p>
     */
    private String resolveCurrentAdminId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "SYSTEM";
        }
        return auth.getName();
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
