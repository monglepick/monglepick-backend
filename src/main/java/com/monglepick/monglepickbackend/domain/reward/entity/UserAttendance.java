package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 출석 체크 엔티티 — user_attendance 테이블 매핑.
 *
 * <p>사용자의 일일 출석 체크 기록을 저장한다.
 * 동일 사용자 + 동일 날짜에 중복 출석이 불가하다 (UNIQUE(user_id, check_date)).</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseAuditEntity 상속 추가 (created_at/updated_at/created_by/updated_by 자동 관리)</li>
 *   <li>2026-03-24: PK 필드명 attendanceId → userAttendanceId 로 변경, @Column(name = "user_attendance_id") 추가</li>
 *   <li>2026-03-24: 수동 createdAt (@CreationTimestamp) 필드 제거 — BaseAuditEntity가 created_at 자동 관리</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID</li>
 *   <li>{@code checkDate} — 출석 체크 날짜 (DATE)</li>
 *   <li>{@code streakCount} — 연속 출석일 수 (기본값: 1)</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "check_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속 추가: created_at, updated_at, created_by, updated_by 컬럼 자동 관리 */
public class UserAttendance extends BaseAuditEntity {

    /**
     * 출석 기록 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 기존 필드명 'attendanceId'에서 'userAttendanceId'로 변경하여 엔티티 식별 명확화.
     * 기존 컬럼명 'attendance_id'에서 'user_attendance_id'로 변경.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_attendance_id")
    private Long userAttendanceId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 출석 체크 날짜 (DATE, NOT NULL).
     * 사용자가 출석 체크를 수행한 날짜.
     */
    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    /**
     * 연속 출석일 수.
     * 기본값: 1.
     * 전날도 출석했으면 +1, 아니면 1로 리셋.
     */
    @Column(name = "streak_count")
    @Builder.Default
    private Integer streakCount = 1;

    // ========== Excel Table 기준 추가 컬럼 (1개) ==========

    /**
     * 이 출석으로 적립된 포인트 (기본값: 0).
     * RewardService가 출석 체크 시 등급 배율 적용 후 실제 지급된 포인트를 기록한다.
     * 출석 이력 조회 시 날짜별 적립 포인트를 표시하는 데 활용된다.
     */
    @Column(name = "reward_point")
    @Builder.Default
    private Integer rewardPoint = 0;

    /* 수동 createdAt 필드 제거됨 — BaseAuditEntity가 created_at 컬럼을 자동 관리 */
}
