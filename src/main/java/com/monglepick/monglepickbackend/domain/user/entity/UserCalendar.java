package com.monglepick.monglepickbackend.domain.user.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 캘린더 엔티티 — user_calendars 테이블 매핑.
 *
 * <p>사용자의 영화 관련 일정(시사회, 개봉일 알림, 개인 메모 등)을 저장한다.
 * 시작 시간은 필수이며, 종료 시간은 선택이다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 *   <li>2026-03-31: 클래스명 {@code Calander} → {@code UserCalendar} 오타 수정.
 *       테이블명 {@code calander} → {@code user_calendars} 변경 (오타 수정 + 명칭 명확화).
 *       PK 필드명 {@code calanderId} → {@code userCalendarId},
 *       컬럼명 {@code calander_id} → {@code user_calendar_id}.</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID (users.user_id 참조)</li>
 *   <li>{@code scheduleTitle} — 일정 제목 (필수)</li>
 *   <li>{@code scheduleDescription} — 일정 설명 (선택)</li>
 *   <li>{@code startTime} — 시작 시간 (DATETIME, 필수)</li>
 *   <li>{@code endTime} — 종료 시간 (DATETIME, 선택)</li>
 * </ul>
 */
@Entity
@Table(name = "user_calendars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자 — 외부 직접 생성 금지
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 컬럼 자동 관리 */
public class UserCalendar extends BaseAuditEntity {

    /**
     * 캘린더 일정 고유 ID (BIGINT AUTO_INCREMENT PK).
     *
     * <p>기존 {@code calanderId}(오타)에서 {@code userCalendarId}로 변경하여
     * 엔티티 식별 명확화 및 오타 수정.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_calendar_id")
    private Long userCalendarId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     *
     * <p>users.user_id를 논리적으로 참조한다. 사용자 1명이 여러 일정을 가질 수 있다.</p>
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 일정 제목 (VARCHAR(200), NOT NULL).
     *
     * <p>영화 제목, 시사회명, 개인 메모 등 일정을 식별하는 짧은 제목이다.</p>
     */
    @Column(name = "schedule_title", length = 200, nullable = false)
    private String scheduleTitle;

    /**
     * 일정 설명 (TEXT, nullable).
     *
     * <p>일정에 대한 상세 메모나 설명이다. 선택 입력이며 null이 허용된다.</p>
     */
    @Column(name = "schedule_description", columnDefinition = "TEXT")
    private String scheduleDescription;

    /**
     * 일정 시작 시간 (DATETIME, NOT NULL).
     *
     * <p>LocalDateTime으로 매핑되며, 사용자가 설정한 일정 시작 일시이다.</p>
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 일정 종료 시간 (DATETIME, nullable).
     *
     * <p>종일 일정이나 시작 시간만 있는 경우 null이다.
     * 값이 있으면 해당 일시에 일정이 종료된다.</p>
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    // ──────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드 사용)
    // ──────────────────────────────────────────────

    /**
     * 일정 제목과 설명을 수정한다.
     *
     * @param title       새 일정 제목 (null이면 변경하지 않음)
     * @param description 새 일정 설명 (null 허용 — 설명 삭제 용도로도 사용)
     */
    public void updateContent(String title, String description) {
        if (title != null) this.scheduleTitle = title;
        this.scheduleDescription = description;
    }

    /**
     * 일정 시간을 수정한다.
     *
     * @param startTime 새 시작 시간 (null이면 변경하지 않음)
     * @param endTime   새 종료 시간 (null 허용 — 종료 시간 삭제 용도로도 사용)
     */
    public void updateTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null) this.startTime = startTime;
        this.endTime = endTime;
    }
}
