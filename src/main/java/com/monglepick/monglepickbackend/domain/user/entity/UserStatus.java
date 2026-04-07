package com.monglepick.monglepickbackend.domain.user.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 계정 정지/해제 상태 관리 엔티티 — user_status 테이블
 *
 * <p>엑셀 설계 첫 번째 시트 45번 테이블 기준 (담당: 김민규).
 * 관리자가 특정 사용자를 정지하거나 해제할 때 이력을 기록합니다.</p>
 *
 * <p>계정 상태 변경 이력 전체를 보관합니다 (INSERT-ONLY 권장).
 * 현재 정지 여부는 최신 레코드의 status 컬럼으로 판단합니다.</p>
 */
@Entity
@Table(
        name = "user_status",
        indexes = {
                @Index(name = "idx_user_status_user",   columnList = "user_id"),
                @Index(name = "idx_user_status_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStatus extends BaseAuditEntity {

    /** 상태 레코드 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_status_id")
    private Long userStatusId;

    /** 대상 사용자 ID (users.user_id 참조) */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 계정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    /** 정지 시작 일시 (ACTIVE인 경우 null) */
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /** 정지 해제 예정 일시 (영구 정지인 경우 null) */
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    /** 정지 사유 */
    @Column(name = "suspend_reason", length = 500)
    private String suspendReason;

    /** 정지/해제 처리 관리자 ID (users.user_id 참조) */
    @Column(name = "suspended_by", length = 50)
    private String suspendedBy;

    @Builder
    public UserStatus(String userId, AccountStatus status, LocalDateTime suspendedAt,
                      LocalDateTime suspendedUntil, String suspendReason, String suspendedBy) {
        this.userId = userId;
        this.status = status;
        this.suspendedAt = suspendedAt;
        this.suspendedUntil = suspendedUntil;
        this.suspendReason = suspendReason;
        this.suspendedBy = suspendedBy;
    }

    /**
     * 계정 상태 열거형
     *
     * <ul>
     *   <li>ACTIVE — 정상 활성 계정</li>
     *   <li>SUSPENDED — 정지된 계정</li>
     * </ul>
     */
    public enum AccountStatus {
        ACTIVE, SUSPENDED
    }
}
