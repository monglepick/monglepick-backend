package com.monglepick.monglepickbackend.domain.reward.entity;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 유저 활동 진행률 엔티티 — user_activity_progress 테이블 매핑.
 *
 * <p>유저별 활동 유형(action_type)마다 1행을 유지하며,
 * 누적/일일/연속 활동 횟수와 리워드 지급 횟수를 캐싱한다.
 * {@code points_history} COUNT 쿼리 대신 이 테이블의 1행 조회로
 * 한도 검사 성능을 확보한다.</p>
 *
 * <h3>핵심 역할</h3>
 * <ul>
 *   <li><b>한도 검사 캐시</b>: rewarded_today_count < daily_limit, rewarded_total_count < max_count</li>
 *   <li><b>마일스톤 판정</b>: total_count ≥ N으로 즉시 판정 (별도 테이블 풀스캔 불필요)</li>
 *   <li><b>연속 기록</b>: current_streak으로 STREAK 보상 판정 (7일, 15일, 30일 연속 출석 등)</li>
 *   <li><b>쿨다운 검사</b>: last_action_at + cooldown_seconds < now</li>
 * </ul>
 *
 * <h3>동시성 처리</h3>
 * <p>RewardService.grantReward()에서 {@code SELECT FOR UPDATE}로 행 잠금 후 lazy reset + 카운터 갱신.
 * 비관적 락이 리워드 트랜잭션(REQUIRES_NEW) 전체를 보호한다.</p>
 *
 * <h3>lazy reset 패턴</h3>
 * <p>last_daily_reset 날짜가 오늘과 다르면 daily_count, rewarded_today_count를 0으로 리셋.
 * 잠금 상태에서 수행하므로 경쟁 조건이 방지된다.</p>
 *
 * <h3>카운터 분리 원칙 (설계서 v2.3 §4.4)</h3>
 * <ul>
 *   <li>{@code total_count} / {@code daily_count} — 실제 활동 횟수 (리워드 지급 여부 무관, 항상 증가)</li>
 *   <li>{@code rewarded_today_count} / {@code rewarded_total_count} — 리워드 지급 횟수 (한도 검사용)</li>
 *   <li>분리 이유: 리워드 한도가 변경되어도 실제 활동 횟수가 남아있으므로 소급 지급 가능</li>
 * </ul>
 *
 * <h3>인덱스</h3>
 * <ul>
 *   <li>{@code uk_progress_user_action} — (user_id, action_type) UNIQUE: 유저당 활동별 1행 보장</li>
 *   <li>{@code idx_progress_action} — (action_type): 활동별 집계</li>
 *   <li>{@code idx_progress_streak} — (action_type, current_streak): 연속 기록 랭킹</li>
 *   <li>{@code idx_progress_reset} — (last_daily_reset): 리셋 대상 검색</li>
 * </ul>
 *
 * @see RewardPolicy 활동 정책 정의
 * @see PointsHistory 포인트 변동 원장 (UNIQUE 인덱스가 최종 중복 방지)
 */
@Entity
@Table(
        name = "user_activity_progress",
        uniqueConstraints = {
                /* 유저당 활동별 정확히 1행만 존재해야 한다 */
                @UniqueConstraint(
                        name = "uk_progress_user_action",
                        columnNames = {"user_id", "action_type"}
                )
        },
        indexes = {
                @Index(name = "idx_progress_action", columnList = "action_type"),
                @Index(name = "idx_progress_streak", columnList = "action_type, current_streak"),
                @Index(name = "idx_progress_reset", columnList = "last_daily_reset")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserActivityProgress extends BaseAuditEntity {

    /**
     * 진행률 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "progress_id")
    private Long progressId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 활동 유형 코드 (VARCHAR(50), NOT NULL).
     * reward_policy.action_type과 논리적 참조 관계.
     */
    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType;

    /**
     * 전체 누적 활동 횟수 (리워드 지급 여부 무관, 항상 증가).
     * 마일스톤 달성 판정에 사용: total_count ≥ threshold_count
     */
    @Column(name = "total_count", nullable = false)
    @Builder.Default
    private Integer totalCount = 0;

    /**
     * 오늘 활동 횟수.
     * lazy reset: 날짜가 바뀌면 0으로 리셋.
     * DAILY threshold 판정에 사용: daily_count == threshold_count
     */
    @Column(name = "daily_count", nullable = false)
    @Builder.Default
    private Integer dailyCount = 0;

    /**
     * 현재 연속 일수/횟수.
     * 출석 등 연속 활동 추적. AttendanceService에서만 갱신.
     * STREAK threshold 판정: current_streak % threshold_count == 0
     */
    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private Integer currentStreak = 0;

    /**
     * 역대 최고 연속 기록.
     * current_streak이 max_streak을 넘으면 갱신.
     */
    @Column(name = "max_streak", nullable = false)
    @Builder.Default
    private Integer maxStreak = 0;

    /**
     * 마지막 연속 기록 날짜.
     * streak 끊김 판정: last_streak_date < 어제 → current_streak = 0
     */
    @Column(name = "last_streak_date")
    private LocalDate lastStreakDate;

    /**
     * 오늘 리워드 지급 횟수.
     * lazy reset: 날짜가 바뀌면 0으로 리셋.
     * 한도 검사: rewarded_today_count < daily_limit
     */
    @Column(name = "rewarded_today_count", nullable = false)
    @Builder.Default
    private Integer rewardedTodayCount = 0;

    /**
     * 누적 리워드 지급 횟수.
     * 한도 검사: rewarded_total_count < max_count
     */
    @Column(name = "rewarded_total_count", nullable = false)
    @Builder.Default
    private Integer rewardedTotalCount = 0;

    /**
     * lazy reset 기준일 (DATE, NOT NULL).
     * 날짜가 바뀌면 daily_count, rewarded_today_count를 리셋.
     */
    @Column(name = "last_daily_reset", nullable = false)
    @Builder.Default
    private LocalDate lastDailyReset = LocalDate.now();

    /**
     * 마지막 활동 시각 (DATETIME(6)).
     * 쿨다운 검사: last_action_at + cooldown_seconds < now
     */
    @Column(name = "last_action_at")
    private LocalDateTime lastActionAt;

    // ──────────────────────────────────────────────
    // 도메인 메서드
    // ──────────────────────────────────────────────

    /**
     * lazy reset — 날짜가 바뀌었으면 일일 카운터를 리셋한다.
     *
     * <p>반드시 SELECT FOR UPDATE 잠금 상태에서 호출해야 경쟁 조건이 방지된다.
     * streak은 이 메서드에서 건드리지 않는다 (AttendanceService에서만 갱신).</p>
     *
     * @param today 오늘 날짜
     */
    public void lazyResetIfNeeded(LocalDate today) {
        if (!this.lastDailyReset.equals(today)) {
            this.dailyCount = 0;
            this.rewardedTodayCount = 0;
            this.lastDailyReset = today;
        }
    }

    /**
     * 전체 누적 활동 횟수 증가.
     * 리워드 지급 여부와 무관하게 활동 발생 시마다 호출.
     */
    public void incrementTotalCount() {
        this.totalCount++;
    }

    /**
     * 오늘 활동 횟수 증가.
     * 리워드 지급 여부와 무관하게 활동 발생 시마다 호출.
     */
    public void incrementDailyCount() {
        this.dailyCount++;
    }

    /**
     * 오늘 리워드 지급 횟수 증가.
     * 실제로 포인트가 지급된 경우에만 호출 (0P 카운팅 전용은 제외).
     */
    public void incrementRewardedTodayCount() {
        this.rewardedTodayCount++;
    }

    /**
     * 누적 리워드 지급 횟수 증가.
     * 실제로 포인트가 지급된 경우에만 호출.
     */
    public void incrementRewardedTotalCount() {
        this.rewardedTotalCount++;
    }

    /**
     * 전체 누적 활동 횟수 감소 (콘텐츠 삭제 시 회수용).
     * 0 미만으로 내려가지 않도록 방어.
     */
    public void decrementTotalCount() {
        if (this.totalCount > 0) {
            this.totalCount--;
        }
    }

    /**
     * 누적 리워드 지급 횟수 감소 (콘텐츠 삭제 시 회수용).
     * 0 미만으로 내려가지 않도록 방어.
     */
    public void decrementRewardedTotalCount() {
        if (this.rewardedTotalCount > 0) {
            this.rewardedTotalCount--;
        }
    }

    /**
     * 마지막 활동 시각을 현재로 갱신한다.
     * 쿨다운 검사에 사용.
     */
    public void updateLastActionAt() {
        this.lastActionAt = LocalDateTime.now();
    }

    /**
     * 연속 기록(streak)을 갱신한다 (출석 서비스에서 호출).
     *
     * <p>어제 활동 기록이 있으면 streak++, 없으면 1로 리셋.
     * max_streak도 함께 갱신한다.</p>
     *
     * @param today 오늘 날짜
     */
    public void updateStreak(LocalDate today) {
        if (this.lastStreakDate != null && this.lastStreakDate.equals(today.minusDays(1))) {
            /* 어제 활동 → streak 이어감 */
            this.currentStreak++;
        } else {
            /* 어제 미활동 또는 첫 활동 → streak 리셋 */
            this.currentStreak = 1;
        }
        this.lastStreakDate = today;
        /* 역대 최고 갱신 */
        if (this.currentStreak > this.maxStreak) {
            this.maxStreak = this.currentStreak;
        }
    }

    /**
     * 연속 기록을 강제 리셋한다 (streak 끊김 처리).
     * max_streak은 유지한다.
     */
    public void resetStreak() {
        this.currentStreak = 0;
    }
}
