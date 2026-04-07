package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연속 출석 현황 엔티티 — user_streak_attendance 테이블 매핑.
 *
 * <p>사용자의 연속 출석(streak) 달성 시점을 기록한다.
 * user_attendance 기록과 연결하여 연속 출석 보상 지급 근거 및 이력 관리에 활용된다.</p>
 *
 * <p>Excel DB 설계서 Table 29 기준으로 생성되었다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID (users.user_id 참조)</li>
 *   <li>{@code attendance} — 연결된 출석 기록 (user_attendance FK, LAZY)</li>
 *   <li>{@code streakCheckDate} — 연속 출석 일수 (달성 시점의 연속 일수)</li>
 *   <li>{@code rewardPoint} — 해당 연속 출석으로 지급된 보너스 포인트</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_streak_attendance_user_id} — 사용자별 연속 출석 이력 조회</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 29 기준으로 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_streak_attendance",
        indexes = {
                /* 사용자별 연속 출석 이력 조회 */
                @Index(name = "idx_streak_attendance_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserStreakAttendance extends BaseAuditEntity {

    /**
     * 연속 출석 이력 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_streak_id")
    private Long attendanceStreakId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 논리적으로 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 연결된 출석 기록 (LAZY, nullable).
     * user_attendance.user_attendance_id를 FK로 참조한다.
     * 연속 출석 보상이 어떤 출석 체크에서 발생했는지 추적하는 데 사용된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id")
    private UserAttendance attendance;

    /**
     * 연속 출석 일수 (NOT NULL).
     * 해당 시점까지의 연속 출석 일수를 기록한다.
     * 예: 7 → 7일 연속 출석 달성 시점의 레코드
     */
    @Column(name = "streak_check_date", nullable = false)
    private Integer streakCheckDate;

    /**
     * 연속 출석 보너스 포인트 (기본값: 0).
     * 해당 연속 출석 달성으로 지급된 추가 포인트를 기록한다.
     * RewardService가 연속 출석 보상 지급 시 이 값을 설정한다.
     */
    @Column(name = "reward_point")
    @Builder.Default
    private Integer rewardPoint = 0;

    /* created_at, updated_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */
}
