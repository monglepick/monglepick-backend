package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 개인화 도장깨기 코스 엔티티 — course 테이블 매핑.
 *
 * <p>AI가 사용자의 취향을 분석하여 맞춤 생성한 개인화 도장깨기 코스를 저장한다.
 * 관리자가 미리 정의한 큐레이션 코스({@link RoadmapCourse})와는 별개로,
 * 사용자별로 동적으로 생성되는 개인화 코스다.</p>
 *
 * <p>코스는 특정 테마(감독, 장르, 시대 등)에 맞는 영화 목록으로 구성되며,
 * 완료 기한(deadline)이 있을 경우 deadAt 이후에는 만료 처리된다.</p>
 *
 * <p>Excel DB 설계서 Table 18 기준으로 생성되었다.
 * 기존 roadmap_courses(RoadmapCourse)와는 별개의 테이블이며 혼동 주의.</p>
 *
 * <h3>course_theme 예시</h3>
 * <ul>
 *   <li>{@code "감독"} — 특정 감독의 필모그래피 탐험</li>
 *   <li>{@code "장르"} — 특정 장르 대표작 순례</li>
 *   <li>{@code "시대"} — 특정 시대(연도대) 명작 탐방</li>
 *   <li>{@code "배우"} — 특정 배우 출연작 여정</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 코스 소유 사용자 ID</li>
 *   <li>{@code courseTitle} — AI가 생성한 코스 제목 (최대 300자)</li>
 *   <li>{@code courseDescription} — 코스 설명 (TEXT, nullable)</li>
 *   <li>{@code courseTheme} — 코스 테마 분류 (최대 100자, nullable)</li>
 *   <li>{@code movieIds} — 코스 구성 영화 ID 배열 (JSON, NOT NULL)</li>
 *   <li>{@code movieCount} — 코스 구성 영화 수 (NOT NULL)</li>
 *   <li>{@code deadline} — 완료 제한 일수 (nullable)</li>
 *   <li>{@code deadAt} — 만료 일시 (nullable, deadline 기반으로 계산)</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_user_course_user_id} — 사용자별 코스 목록 조회</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 18 기준으로 최초 생성 (RoadmapCourse와 별개)</li>
 * </ul>
 */
@Entity
@Table(
        name = "course",
        indexes = {
                /* 사용자별 개인화 코스 목록 조회 */
                @Index(name = "idx_user_course_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserCourse extends BaseAuditEntity {

    /**
     * 코스 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    /**
     * 코스 소유 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 논리적으로 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * AI가 생성한 코스 제목 (VARCHAR(300), NOT NULL).
     * 예: "봉준호 감독 필모그래피 완주 도전", "2000년대 한국 공포영화 순례"
     */
    @Column(name = "course_title", length = 300, nullable = false)
    private String courseTitle;

    /**
     * 코스 설명 (TEXT, nullable).
     * AI가 생성한 코스 기획 의도 및 선정 기준 설명.
     */
    @Column(name = "course_description", columnDefinition = "TEXT")
    private String courseDescription;

    /**
     * 코스 테마 분류 (VARCHAR(100), nullable).
     * 코스가 어떤 테마로 기획되었는지를 나타내는 분류 코드.
     * 예: "감독", "장르", "시대", "배우"
     */
    @Column(name = "course_theme", length = 100)
    private String courseTheme;

    /**
     * 코스 구성 영화 ID 배열 (JSON, NOT NULL).
     * movies.movie_id 값들을 JSON 배열로 저장한다.
     * 예: ["movie_001", "movie_002", "movie_003"]
     * 순서가 의미를 가지며, Agent가 추천한 관람 순서를 반영한다.
     */
    @Column(name = "movie_ids", columnDefinition = "JSON", nullable = false)
    private String movieIds;

    /**
     * 코스 구성 영화 수 (NOT NULL).
     * movieIds 배열의 길이와 일치해야 한다. 빠른 조회를 위해 별도 저장.
     */
    @Column(name = "movie_count", nullable = false)
    private Integer movieCount;

    /**
     * 완료 제한 일수 (nullable).
     * 코스 생성 시점으로부터 완료해야 하는 기한(일 단위).
     * null이면 기한 없는 영구 코스.
     */
    @Column(name = "deadline")
    private Integer deadline;

    /**
     * 코스 만료 일시 (DATETIME, nullable).
     * deadline이 설정된 경우 코스 생성 시각 + deadline 일수로 계산된다.
     * null이면 만료 없음. isExpired()로 만료 여부를 확인한다.
     */
    @Column(name = "dead_at")
    private LocalDateTime deadAt;

    /* created_at, updated_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 코스 만료 여부를 반환한다.
     *
     * <p>deadAt이 설정되어 있고 현재 시각이 deadAt을 지났으면 true를 반환한다.
     * deadAt이 null이면 만료 없는 코스이므로 항상 false를 반환한다.</p>
     *
     * @return 만료된 경우 true, 아직 유효하거나 기한 없는 경우 false
     */
    public boolean isExpired() {
        if (this.deadAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(this.deadAt);
    }
}
