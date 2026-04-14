package com.monglepick.monglepickbackend.domain.roadmap.entity;

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
 * 사용자 업적/뱃지 달성 이력 엔티티 — user_achievements 테이블 매핑.
 *
 * <p>도장깨기 코스 완주, 퀴즈 만점, 리뷰 작성 등 다양한 활동에 대한
 * 업적/뱃지 달성 기록을 저장한다.</p>
 *
 * <h3>2테이블 분리 구조</h3>
 * <ul>
 *   <li>{@link AchievementType} — 업적 유형 마스터 (achievement_types 테이블)</li>
 *   <li>{@link UserAchievement} — 사용자별 달성 기록 (user_achievements 테이블, 이 클래스)</li>
 * </ul>
 *
 * <p>같은 사용자+업적유형+키 조합의 중복 달성은 불가하다
 * (user_id, achievement_type_id, achievement_key UNIQUE 제약).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId}          — 업적 달성 사용자 ID (String FK → users.user_id, JPA/MyBatis 하이브리드 §15.4)</li>
 *   <li>{@code achievementType} — 업적 유형 참조 (FK → achievement_types.achievement_type_id)</li>
 *   <li>{@code achievementKey}  — 업적 식별 키 (예: 코스 ID, 수치 등)</li>
 *   <li>{@code achievedAt}      — 업적 달성 시각 (도메인 고유 필드, 유지)</li>
 *   <li>{@code metadata}        — 추가 메타데이터 (JSON)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → userAchievementId (컬럼명: user_achievement_id)</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>achievedAt 필드는 도메인 고유 타임스탬프이므로 유지</li>
 *   <li>[분리] achievementType VARCHAR(50) → @ManyToOne FK (achievement_types 테이블 참조)</li>
 *   <li>[분리] UNIQUE 제약: (user_id, achievement_type, achievement_key)
 *              → (user_id, achievement_type_id, achievement_key)</li>
 * </ul>
 */
@Entity(name = "RoadmapUserAchievement")
@Table(name = "user_achievements", uniqueConstraints = {
        /*
         * 같은 사용자가 같은 업적 유형+키를 중복 달성하지 못하도록 UNIQUE 제약.
         * achievement_type VARCHAR → achievement_type_id FK로 변경됨에 따라
         * 제약 컬럼도 achievement_type_id로 갱신.
         */
        @UniqueConstraint(name = "uk_user_achievement", columnNames = {"user_id", "achievement_type_id", "achievement_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성을 위한 protected 기본 생성자 */
@AllArgsConstructor
@Builder
public class UserAchievement extends BaseAuditEntity {

    /**
     * 업적 달성 기록 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_achievement_id")
    private Long userAchievementId;

    /**
     * 업적 달성 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). AchievementType은 같은 roadmap 도메인이므로
     * @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 업적 유형 코드 — 레거시 VARCHAR 컬럼 호환용 (DB NOT NULL 제약 유지).
     *
     * <p>achievement_type_id FK 전환 이전에 존재하던 컬럼이 DB에 NOT NULL로 남아 있어
     * INSERT 시 값을 채워줘야 한다. {@link AchievementType#getAchievementCode()}와 동일한 값을 넣는다.</p>
     */
    @Column(name = "achievement_type", length = 50, nullable = false)
    private String achievementTypeCode;

    /**
     * 업적 유형 참조 (LAZY) — achievement_types 마스터 테이블 FK.
     *
     * <p>기존 VARCHAR(50) 문자열 컬럼에서 @ManyToOne FK 참조로 변경.
     * 업적 유형의 표시명·보상·아이콘 등 메타 정보는 {@link AchievementType}에서 관리한다.</p>
     *
     * <p>변경 전: {@code @Column(name = "achievement_type", length = 50)}</p>
     * <p>변경 후: {@code @ManyToOne + @JoinColumn(name = "achievement_type_id")}</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_type_id", nullable = false)
    private AchievementType achievementType;

    /**
     * 업적 식별 키 (최대 100자, 필수).
     *
     * <p>같은 유형 내에서 구체적인 업적 항목을 구분하는 보조 식별자이다.
     * 유형이 단일 달성형이면 "default"를 사용한다.</p>
     *
     * <p>예: 코스 ID("nolan-filmography"), 수치("10"), 장르("horror"), "default"</p>
     */
    @Column(name = "achievement_key", length = 100, nullable = false)
    private String achievementKey;

    /**
     * 업적 달성 시각 (도메인 고유 타임스탬프).
     *
     * <p>{@code created_at}(레코드 DB 삽입 시각)과 별개로,
     * 비즈니스 관점에서의 실제 업적 달성 시점을 기록한다.
     * 서비스 레이어에서 {@code LocalDateTime.now()}로 명시적으로 설정해야 한다.</p>
     */
    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;

    /**
     * 추가 메타데이터 (JSON 객체, 선택).
     *
     * <p>업적과 관련된 부가 정보를 자유 형식으로 저장한다.
     * 예: {"score": 100, "time_taken": "2h 30m", "movies_watched": 12}</p>
     */
    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
