package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity로 변경 — created_at/updated_at에 더해 created_by/updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도장깨기 코스 엔티티 — roadmap_courses 테이블 매핑.
 *
 * <p>AI Agent가 생성한 영화 도장깨기(로드맵) 코스를 저장한다.
 * 각 코스는 테마별로 구성된 영화 목록과 난이도, 퀴즈 활성화 여부를 포함한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code courseId} — 코스 고유 식별자 (UNIQUE, 예: "nolan-filmography")</li>
 *   <li>{@code title} — 코스 제목 (예: "크리스토퍼 놀란 필모그래피 정복")</li>
 *   <li>{@code theme} — 테마/카테고리 (예: "감독별", "장르별", "시대별")</li>
 *   <li>{@code movieIds} — 코스에 포함된 영화 ID 목록 (JSON 배열, 필수)</li>
 *   <li>{@code movieCount} — 코스 내 영화 수 (필수)</li>
 *   <li>{@code difficulty} — 난이도 (beginner, intermediate, advanced)</li>
 *   <li>{@code quizEnabled} — 퀴즈 활성화 여부</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → roadmapCourseId (컬럼명: roadmap_course_id)</li>
 *   <li>BaseTimeEntity → BaseAuditEntity로 변경 (created_by/updated_by 추가)</li>
 *   <li>수동 createdBy 필드 제거 — BaseAuditEntity에서 상속 (AuditorAware 자동 주입)</li>
 * </ul>
 */
@Entity
@Table(name = "roadmap_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RoadmapCourse extends BaseAuditEntity {

    /**
     * 코스 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: id → roadmapCourseId (엔티티 PK 네이밍 통일)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "roadmap_course_id")
    private Long roadmapCourseId;

    /**
     * 코스 고유 식별자 (UNIQUE).
     * 사람이 읽을 수 있는 slug 형태의 ID.
     * 예: "nolan-filmography", "90s-classics", "korean-thrillers"
     */
    @Column(name = "course_id", length = 50, nullable = false, unique = true)
    private String courseId;

    /** 코스 제목 (필수, 최대 300자) */
    @Column(name = "title", length = 300, nullable = false)
    private String title;

    /** 코스 설명 (선택, TEXT 타입) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 테마/카테고리 (최대 100자).
     * 예: "감독별", "장르별", "시대별", "국가별"
     */
    @Column(name = "theme", length = 100)
    private String theme;

    /**
     * 코스에 포함된 영화 ID 목록 (JSON 배열, 필수).
     * 예: ["12345", "67890", "11111"]
     * 순서가 곧 시청 순서를 의미한다.
     */
    @Column(name = "movie_ids", columnDefinition = "json", nullable = false)
    private String movieIds;

    /** 코스 내 영화 수 (필수) */
    @Column(name = "movie_count", nullable = false)
    private Integer movieCount;

    /**
     * 난이도.
     * ENUM('beginner', 'intermediate', 'advanced') 중 하나.
     * 기본값: beginner
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    @Builder.Default
    private Difficulty difficulty = Difficulty.beginner;

    /**
     * 퀴즈 활성화 여부.
     * true이면 코스 내 영화별 퀴즈가 제공된다.
     * 기본값: false
     */
    @Column(name = "quiz_enabled")
    @Builder.Default
    private Boolean quizEnabled = false;

    /**
     * 코스 활성화 여부 (관리자 비활성화 토글용, 기본값 true).
     *
     * <p>false이면 사용자 측 {@code GET /api/v1/roadmap/courses}에서 노출되지 않으며,
     * 신규 시작도 불가하다. 기존에 진행 중인 사용자의 진행 기록은 보존된다.</p>
     *
     * <p>JPA {@code ddl-auto=update}로 자동 컬럼 추가됨.</p>
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 코스 완주 데드라인 (일 수, nullable).
     *
     * <p>null이면 데드라인 없음 (무기한 진행 가능).
     * 양수이면 코스 시작 후 N일 이내에 완주해야 한다.
     * 예: 30 → 시작 후 30일 이내 완주.
     * 사용자별 실제 마감 시각은 {@code UserCourseProgress.deadlineAt}에 저장된다.</p>
     */
    @Column(name = "deadline_days")
    private Integer deadlineDays;

    /* createdBy 수동 필드 제거 — BaseAuditEntity에서 @CreatedBy로 자동 관리 */
    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (관리자 CRUD 전용)
    // ─────────────────────────────────────────────

    /**
     * 코스 메타 정보를 수정한다 (관리자 전용).
     *
     * <p>코스 슬러그(course_id)는 사용자 진행 기록과 연결된 식별자이므로 변경 불가.
     * 표시명/설명/테마/영화목록/난이도/퀴즈활성화만 수정 가능하다.</p>
     *
     * @param title       변경할 제목
     * @param description 변경할 설명
     * @param theme       변경할 테마
     * @param movieIds    변경할 영화 ID JSON 배열 문자열
     * @param movieCount  변경할 영화 수
     * @param difficulty  변경할 난이도
     * @param quizEnabled 변경할 퀴즈 활성화 여부
     */
    public void updateInfo(String title, String description, String theme,
                           String movieIds, Integer movieCount,
                           Difficulty difficulty, Boolean quizEnabled,
                           Integer deadlineDays) {
        this.title = title;
        this.description = description;
        this.theme = theme;
        this.movieIds = movieIds;
        this.movieCount = movieCount;
        this.difficulty = difficulty;
        this.quizEnabled = quizEnabled;
        this.deadlineDays = deadlineDays;
    }

    /**
     * 코스 활성/비활성 상태를 변경한다 (관리자 토글용).
     *
     * @param active true=활성, false=비활성
     */
    public void updateActiveStatus(boolean active) {
        this.isActive = active;
    }

    /**
     * 코스 난이도 열거형.
     *
     * <p>MySQL ENUM('beginner','intermediate','advanced')에 매핑된다.</p>
     */
    public enum Difficulty {
        /** 초급 — 입문자를 위한 대중적인 영화 위주 */
        beginner,
        /** 중급 — 어느 정도 영화 경험이 있는 사용자용 */
        intermediate,
        /** 고급 — 영화 마니아를 위한 깊이 있는 코스 */
        advanced
    }
}
