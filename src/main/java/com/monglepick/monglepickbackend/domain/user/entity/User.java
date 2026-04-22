package com.monglepick.monglepickbackend.domain.user.entity;

import com.monglepick.monglepickbackend.global.constants.UserRole;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 *
 * <p>MySQL users 테이블과 매핑됩니다.
 * DDL 기준 PK는 user_id VARCHAR(50)입니다.</p>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — 수동 createdAt/updatedAt 필드 및 @PrePersist/@PreUpdate 메서드 제거됨
 */
public class User extends BaseAuditEntity {

    /** 사용자 고유 식별자 (VARCHAR(50), DDL PK) */
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    /** 이메일 주소 (로그인 ID로 사용) */
    @Column(unique = true, length = 255)
    private String email;

    /** 닉네임 (커뮤니티 표시명) */
    @Column(unique = true, length = 50)
    private String nickname;

    /** 비밀번호 해시 (BCrypt, 소셜 로그인 시 null) */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** 프로필 이미지 URL */
    @Column(name = "profile_image", length = 500)
    private String profileImage;

    /** 로그인 제공자 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Provider provider;

    /** 소셜 제공자 고유 ID */
    @Column(name = "provider_id", length = 200)
    private String providerId;

    /** 사용자 역할 (기본값: USER) */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20)
    private UserRole userRole;

    /** 필수 약관 동의 (이용약관 + 개인정보) */
    @Column(name = "required_term")
    private Boolean requiredTerm;

    /** 선택 약관 동의 (기타 선택 사항) */
    @Column(name = "option_term")
    private Boolean optionTerm;

    /** 마케팅 수신 동의 (REQ_015: 개인정보/마케팅 동의 구분 관리) */
    @Column(name = "marketing_agreed")
    private Boolean marketingAgreed;

    /**
     * 계정 상태 (REQ_029: 잠금, 관리자: 정지/활성화).
     * ACTIVE: 정상, SUSPENDED: 정지, LOCKED: 로그인 실패 잠금
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    /** 계정 정지 시각 (관리자 사용자 관리: 정지 시 기록) */
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /**
     * 계정 정지 해제 예정 시각 (임시 정지용, nullable).
     *
     * <p>값이 있으면 해당 시각 이후 자동 복구 대상.
     * null이면 영구 정지(관리자 수동 복구 전까지 유지).</p>
     *
     * <p>JPA {@code ddl-auto=update}로 자동 컬럼 추가.</p>
     */
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    /** 계정 정지 사유 (관리자 사용자 관리: 정지 사유 기록) */
    @Column(name = "suspend_reason", length = 500)
    private String suspendReason;

    /** 소프트 삭제 여부 (회원 탈퇴 시 true, 30일 후 물리삭제) */
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /** 소프트 삭제 시각 (탈퇴 시 기록, 30일 후 물리삭제 스케줄링 기준) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 최종 로그인 시각 (관리자 대시보드 활동 추적) */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /* created_at, updated_at은 BaseAuditEntity(→BaseTimeEntity)에서 자동 관리 — 수동 필드 제거됨 */

    /** 로그인 제공자 열거형 */
    public enum Provider {
        LOCAL, NAVER, KAKAO, GOOGLE
    }

    /**
     * 사용자 계정 상태 열거형.
     * <ul>
     *   <li>ACTIVE — 정상 사용 가능</li>
     *   <li>SUSPENDED — 관리자에 의해 정지됨</li>
     *   <li>LOCKED — 로그인 5회 실패로 잠금 (REQ_029)</li>
     * </ul>
     */
    public enum UserStatus {
        ACTIVE, SUSPENDED, LOCKED
    }

    @Builder
    public User(String userId, String email, String nickname, String passwordHash,
                String profileImage, Provider provider, String providerId,
                UserRole userRole, Boolean optionTerm, Boolean requiredTerm, Boolean marketingAgreed) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.profileImage = profileImage;
        this.provider = provider != null ? provider : Provider.LOCAL;
        this.providerId = providerId;
        this.userRole = userRole != null ? userRole : UserRole.USER;
        this.optionTerm = optionTerm != null ? optionTerm : false;
        this.requiredTerm = requiredTerm != null ? requiredTerm : false;
        this.marketingAgreed = marketingAgreed != null ? marketingAgreed : false;
        this.status = UserStatus.ACTIVE;
        this.isDeleted = false;
    }

    /* @PrePersist/@PreUpdate 제거됨 — BaseTimeEntity의 @CreationTimestamp/@UpdateTimestamp로 자동 관리 */

    /** 닉네임 변경 */
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 사용자 역할 변경 (관리자 기능).
     *
     * <p>USER ↔ ADMIN 전환에 사용한다.
     * Spring Security의 권한 체계가 userRole을 기반으로 동작하므로,
     * 변경 즉시 해당 사용자의 다음 JWT 발급 시 새 역할이 반영된다.</p>
     *
     * @param newRole 새 역할 (USER, ADMIN)
     */
    public void updateUserRole(UserRole newRole) {
        this.userRole = newRole;
    }

    /** 프로필 이미지 변경 */
    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    /** 비밀번호 변경 */
    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 계정 정지 (관리자 기능 — 영구 정지).
     *
     * <p>suspendedUntil=null 로 설정되어 자동 복구 대상에서 제외된다.</p>
     */
    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedAt = LocalDateTime.now();
        this.suspendedUntil = null;
        this.suspendReason = reason;
    }

    /**
     * 계정 임시 정지 (관리자 기능 — 일정 기간 후 자동 복구).
     *
     * @param reason 정지 사유
     * @param until  정지 해제 예정 시각 (현재 시각보다 미래여야 함)
     */
    public void suspendUntil(String reason, LocalDateTime until) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedAt = LocalDateTime.now();
        this.suspendedUntil = until;
        this.suspendReason = reason;
    }

    /** 계정 정지 해제 (관리자 기능) */
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspendedUntil = null;
        this.suspendReason = null;
    }

    /** 로그인 실패 잠금 (REQ_029: 5회 오류 시) */
    public void lock() {
        this.status = UserStatus.LOCKED;
    }

    /** 회원 탈퇴 — 소프트 삭제 (30일 후 물리삭제) */
    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /** 최종 로그인 시각 갱신 */
    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
