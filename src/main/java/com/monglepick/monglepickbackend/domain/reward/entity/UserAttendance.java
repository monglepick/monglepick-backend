package com.monglepick.monglepickbackend.domain.reward.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 출석 체크 엔티티 — user_attendance 테이블 매핑.
 *
 * <p>사용자의 일일 출석 체크 기록을 저장한다.
 * 동일 사용자 + 동일 날짜에 중복 출석이 불가하다 (UNIQUE(user_id, check_date)).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID</li>
 *   <li>{@code checkDate} — 출석 체크 날짜 (DATE)</li>
 *   <li>{@code streakCount} — 연속 출석일 수 (기본값: 1)</li>
 * </ul>
 *
 * <h3>타임스탬프</h3>
 * <p>created_at만 존재하며 updated_at은 없다.
 * BaseTimeEntity를 상속하지 않고 {@code @CreationTimestamp}를 직접 사용한다.</p>
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
public class UserAttendance {

    /** 출석 기록 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

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

    /**
     * 레코드 생성 시각.
     * INSERT 시 자동 설정되며 이후 변경되지 않는다.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
