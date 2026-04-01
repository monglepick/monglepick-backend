package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 도장깨기 인증 엔티티 — course_verification 테이블 매핑.
 *
 * <p>사용자가 도장깨기 코스({@code roadmap_courses}) 내 개별 영화를 실제로 시청했는지
 * 인증한 기록을 저장한다. 퀴즈 정답, 이미지 인증, 리뷰 작성 등 다양한 인증 방식을 지원한다.</p>
 *
 * <h3>인증 흐름</h3>
 * <pre>
 * 영화 시청 → 인증 요청(verificationType) → AI 검증(aiConfidence) → isVerified=true → verifiedAt 기록
 * </pre>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId}           — 인증 요청 사용자 ID</li>
 *   <li>{@code courseId}         — 인증이 속한 코스 ID (roadmap_courses.course_id)</li>
 *   <li>{@code movieId}          — 인증 대상 영화 ID (movies.movie_id)</li>
 *   <li>{@code verificationType} — 인증 방식 (QUIZ / IMAGE / REVIEW)</li>
 *   <li>{@code isVerified}       — 인증 완료 여부 (기본값: false)</li>
 *   <li>{@code aiConfidence}     — AI 인증 신뢰도 점수 (0.0~1.0, nullable)</li>
 *   <li>{@code verifiedAt}       — 인증 완료 시각 (nullable)</li>
 * </ul>
 *
 * <h3>인증 방식 (verificationType)</h3>
 * <ul>
 *   <li>{@code QUIZ}   — 퀴즈 정답으로 시청 인증</li>
 *   <li>{@code IMAGE}  — 화면 캡처 이미지로 시청 인증 (AI 분석)</li>
 *   <li>{@code REVIEW} — 리뷰 작성으로 시청 인증</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(user_id, course_id, movie_id) — 동일 사용자가 동일 코스의 동일 영화를 중복 인증 불가</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>courseId는 roadmap_courses 테이블의 slug 형태 course_id(VARCHAR(50))를 참조한다.</li>
 *   <li>movieId는 movies 테이블의 movie_id(VARCHAR(50))를 참조한다.</li>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>IMAGE 인증의 경우 AI Agent가 aiConfidence를 계산하여 설정하며,
 *       임계값(예: 0.8 이상) 초과 시 서비스 레이어에서 isVerified=true로 전환한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "course_verification",
        uniqueConstraints = {
                /* 동일 사용자가 동일 코스의 동일 영화를 중복 인증 불가 */
                @UniqueConstraint(
                        name = "uk_course_verification_user_course_movie",
                        columnNames = {"user_id", "course_id", "movie_id"}
                )
        },
        indexes = {
                /* 사용자별 코스 인증 진행률 조회 최적화 */
                @Index(name = "idx_course_verification_user_course", columnList = "user_id, course_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class CourseVerification extends BaseAuditEntity {

    /**
     * 도장깨기 인증 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    /**
     * 인증 요청 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 인증이 속한 코스 ID (VARCHAR(50), NOT NULL).
     * roadmap_courses.course_id (slug 형태, 예: "nolan-filmography")를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "course_id", length = 50, nullable = false)
    private String courseId;

    /**
     * 인증 대상 영화 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 참조한다.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /**
     * 인증 방식 (VARCHAR(30), NOT NULL).
     * <ul>
     *   <li>"QUIZ"   — 퀴즈 정답으로 시청 인증</li>
     *   <li>"IMAGE"  — 화면 캡처 이미지로 시청 인증 (AI 분석)</li>
     *   <li>"REVIEW" — 리뷰 작성으로 시청 인증</li>
     * </ul>
     */
    @Column(name = "verification_type", length = 30, nullable = false)
    private String verificationType;

    /**
     * 인증 완료 여부 (기본값: false).
     * AI 또는 시스템 검증 통과 후 true로 전환된다.
     * isVerified=true가 되어야 코스 진행률에 반영된다.
     */
    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * AI 인증 신뢰도 점수 (nullable, 0.0~1.0).
     * IMAGE 인증 방식에서 AI Agent가 계산한 신뢰도 점수.
     * QUIZ/REVIEW 방식에서는 null이거나 1.0으로 설정된다.
     */
    @Column(name = "ai_confidence")
    private Float aiConfidence;

    /**
     * 인증 완료 시각 (nullable).
     * isVerified가 false인 동안은 null이며,
     * true로 전환될 때 현재 시각이 기록된다.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 인증을 완료 처리한다.
     *
     * <p>isVerified를 true로 전환하고 verifiedAt을 현재 시각으로 설정한다.
     * aiConfidence가 제공된 경우 함께 기록한다.</p>
     *
     * @param confidence AI 신뢰도 점수 (null 허용, QUIZ/REVIEW 인증 시 null 전달)
     */
    public void verify(Float confidence) {
        this.isVerified = true;
        this.aiConfidence = confidence;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * 인증을 취소(무효화)한다.
     *
     * <p>관리자가 부정 인증을 발견했을 때 사용한다.
     * verifiedAt은 그대로 두어 이력을 보존한다.</p>
     */
    public void invalidate() {
        this.isVerified = false;
    }
}
