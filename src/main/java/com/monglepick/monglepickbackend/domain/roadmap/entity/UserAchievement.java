package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 업적/뱃지 엔티티 — user_achievements 테이블 매핑.
 *
 * <p>도장깨기 코스 완주, 퀴즈 만점, 리뷰 작성 등 다양한 활동에 대한
 * 업적/뱃지를 기록한다. 같은 유형+키의 업적은 중복 달성이 불가하다
 * (user_id, achievement_type, achievement_key UNIQUE 제약).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code user} — 업적 달성 사용자 (FK → users.user_id)</li>
 *   <li>{@code achievementType} — 업적 유형 (예: "course_complete", "quiz_perfect", "review_count")</li>
 *   <li>{@code achievementKey} — 업적 식별 키 (예: 코스 ID, 수치 등)</li>
 *   <li>{@code achievedAt} — 업적 달성 시각 (도메인 고유 필드, 유지)</li>
 *   <li>{@code metadata} — 추가 메타데이터 (JSON)</li>
 * </ul>
 *
 * <h3>업적 유형 예시</h3>
 * <ul>
 *   <li>{@code course_complete} — 코스 완주 (key: 코스 ID)</li>
 *   <li>{@code quiz_perfect} — 퀴즈 만점 (key: 코스 ID)</li>
 *   <li>{@code review_count_10} — 리뷰 10개 작성</li>
 *   <li>{@code genre_explorer} — 장르 탐험가 (5개 이상 장르 시청)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → userAchievementId (컬럼명: user_achievement_id)</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>achievedAt 필드는 도메인 고유 타임스탬프이므로 유지</li>
 *   <li>@CreationTimestamp 제거 (achievedAt) — 도메인 필드로 전환, 별도 설정 필요</li>
 * </ul>
 */
@Entity(name = "RoadmapUserAchievement")
@Table(name = "user_achievements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_achievement", columnNames = {"user_id", "achievement_type", "achievement_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAchievement extends BaseAuditEntity {

    /**
     * 업적 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: id → userAchievementId (엔티티 PK 네이밍 통일)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_achievement_id")
    private Long userAchievementId;

    /**
     * 업적 달성 사용자.
     * user_achievements.user_id → users.user_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 업적 유형 (최대 50자).
     * 예: "course_complete", "quiz_perfect", "review_count", "genre_explorer"
     */
    @Column(name = "achievement_type", length = 50, nullable = false)
    private String achievementType;

    /**
     * 업적 식별 키 (최대 100자).
     * 같은 유형 내에서 구체적인 업적을 구분하는 키.
     * 예: 코스 ID("nolan-filmography"), 수치("10"), 장르("horror")
     */
    @Column(name = "achievement_key", length = 100, nullable = false)
    private String achievementKey;

    /**
     * 업적 달성 시각 (도메인 고유 타임스탬프).
     * created_at(레코드 생성)과 별개로, 실제 업적 달성 시점을 기록한다.
     * 서비스 레이어에서 명시적으로 설정해야 한다.
     */
    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;

    /**
     * 추가 메타데이터 (JSON 객체).
     * 업적과 관련된 부가 정보를 자유롭게 저장한다.
     * 예: {"score": 100, "time_taken": "2h 30m", "movies_watched": 12}
     */
    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
