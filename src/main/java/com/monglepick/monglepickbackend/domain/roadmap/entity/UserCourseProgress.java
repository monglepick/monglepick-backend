package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 사용자 코스 진행 현황 엔티티 — user_course_progress 테이블 매핑.
 *
 * <p>사용자가 특정 도장깨기 코스({@link RoadmapCourse})를 진행할 때의
 * 인증 완료 영화 수, 진행률, 완주 여부 등을 추적한다.
 * 코스 내 모든 영화가 인증되면 {@code COMPLETED} 상태로 전환되어 리워드가 지급된다.</p>
 *
 * <h3>진행률 계산</h3>
 * <pre>
 * progressPercent = (verifiedMovies / totalMovies) × 100.00
 * 소수점 둘째 자리까지 저장 (DECIMAL(5,2))
 * </pre>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(user_id, course_id) — 동일 사용자가 동일 코스를 중복 시작 불가</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId}           — 진행 중인 사용자 ID</li>
 *   <li>{@code courseId}         — 진행 중인 코스 ID (roadmap_courses.course_id 논리적 참조)</li>
 *   <li>{@code totalMovies}      — 코스 내 총 영화 수 (코스 시작 시 설정, 불변)</li>
 *   <li>{@code verifiedMovies}   — 현재까지 인증 완료한 영화 수</li>
 *   <li>{@code progressPercent}  — 진행률 % (DECIMAL(5,2), 0.00~100.00)</li>
 *   <li>{@code status}           — 진행 상태 ({@link CourseProgressStatus})</li>
 *   <li>{@code startedAt}        — 코스 시작 시각</li>
 *   <li>{@code completedAt}      — 코스 완주 시각 (nullable)</li>
 *   <li>{@code rewardGranted}    — 완주 리워드 지급 여부 (중복 지급 방지)</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_course_progress",
        uniqueConstraints = {
                /* 동일 사용자는 동일 코스를 한 번만 진행할 수 있다 */
                @UniqueConstraint(
                        name = "uk_user_course",
                        columnNames = {"user_id", "course_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserCourseProgress extends BaseAuditEntity {

    /**
     * 코스 진행 현황 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "progress_id")
    private Long progressId;

    /**
     * 진행 중인 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 진행 중인 코스 ID (VARCHAR(100), NOT NULL).
     * roadmap_courses.course_id(slug 형태)를 논리적으로 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: 논리적 참조).
     * 예: "nolan-filmography", "korean-thrillers"
     */
    @Column(name = "course_id", length = 100, nullable = false)
    private String courseId;

    /**
     * 코스 내 총 영화 수 (NOT NULL).
     * 코스 시작 시 {@link RoadmapCourse#getMovieCount()}에서 가져와 고정한다.
     * 이후 코스 영화 목록이 변경되어도 이 값은 유지된다 (스냅샷).
     */
    @Column(name = "total_movies", nullable = false)
    private Integer totalMovies;

    /**
     * 인증 완료한 영화 수 (NOT NULL, 기본값 0).
     * {@link #verify()} 호출마다 1씩 증가한다.
     */
    @Column(name = "verified_movies", nullable = false)
    @Builder.Default
    private Integer verifiedMovies = 0;

    /**
     * 코스 진행률 % (DECIMAL(5,2), 기본값 0.00).
     * verifiedMovies / totalMovies × 100.00 으로 계산.
     * 소수점 둘째 자리까지 저장한다 (예: 66.67%).
     */
    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal progressPercent = BigDecimal.ZERO;

    /**
     * 코스 진행 상태 ({@link CourseProgressStatus}, NOT NULL).
     * 기본값: IN_PROGRESS.
     * 모든 영화 인증 완료 시 COMPLETED로 전환.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CourseProgressStatus status = CourseProgressStatus.IN_PROGRESS;

    /**
     * 코스 시작 시각 (NOT NULL).
     * 진행 레코드 최초 생성 시 현재 시각으로 설정된다.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * 코스 완주 시각 (nullable).
     * 모든 영화 인증 완료 시(status=COMPLETED) 설정된다.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 완주 리워드 지급 완료 여부 (기본값 false).
     * COURSE_COMPLETE 리워드 지급 성공 후 true로 전환된다.
     * 중복 지급 방지를 위한 멱등성 플래그.
     */
    @Column(name = "reward_granted", nullable = false)
    @Builder.Default
    private boolean rewardGranted = false;

    /**
     * 코스 완주 데드라인 시각 (nullable).
     *
     * <p>코스 시작 시 {@link RoadmapCourse#getDeadlineDays()}가 설정된 경우
     * {@code startedAt + deadlineDays 일}로 계산되어 저장된다.
     * null이면 데드라인 없음 (무기한 진행 가능).</p>
     */
    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 영화 1개 인증 완료를 반영한다.
     *
     * <p>verifiedMovies를 1 증가시키고 progressPercent를 재계산한다.
     * 서비스 레이어에서 CourseVerification.isVerified=true 처리 후 이 메서드를 호출한다.</p>
     *
     * <h4>진행률 계산 공식</h4>
     * <pre>
     * progressPercent = floor(verifiedMovies / totalMovies × 100, 2자리)
     * </pre>
     */
    public void verify() {
        this.verifiedMovies++;
        // totalMovies가 0인 경우 방어 처리
        if (this.totalMovies > 0) {
            this.progressPercent = BigDecimal.valueOf(this.verifiedMovies)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(this.totalMovies), 2, RoundingMode.HALF_UP);
        }
    }

    /**
     * 코스를 완주 처리한다.
     *
     * <p>status를 COMPLETED로 전환하고 완주 시각을 기록한다.
     * progressPercent를 100.00으로 고정하여 UI에서 100%로 표시되게 한다.
     * 서비스 레이어에서 verifiedMovies == totalMovies 판정 후 호출한다.</p>
     *
     * @param now 완주 시각 (일반적으로 LocalDateTime.now())
     */
    public void complete(LocalDateTime now) {
        this.status = CourseProgressStatus.COMPLETED;
        this.completedAt = now;
        this.progressPercent = new BigDecimal("100.00");
    }

    /**
     * 완주 리워드 지급 완료를 표시한다.
     *
     * <p>RewardService.grantRewardWithAmount() 호출 성공 후 중복 지급 방지를 위해 설정한다.</p>
     */
    public void markRewardGranted() {
        this.rewardGranted = true;
    }
}
