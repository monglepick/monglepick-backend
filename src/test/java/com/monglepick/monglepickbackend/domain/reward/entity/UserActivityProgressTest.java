package com.monglepick.monglepickbackend.domain.reward.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserActivityProgress 엔티티 단위 테스트.
 *
 * <p>설계서 v2.3 §4.4 기준으로 다음을 검증한다:
 * <ul>
 *   <li>lazy reset — 날짜 변경 시 dailyCount, rewardedTodayCount 리셋</li>
 *   <li>카운터 증가/감소 — 경계값(0 미만 방지)</li>
 *   <li>streak 갱신 — 연속/끊김/최고 기록</li>
 * </ul>
 */
class UserActivityProgressTest {

    private UserActivityProgress createDefault() {
        return UserActivityProgress.builder()
                .userId("test_user")
                .actionType("REVIEW_CREATE")
                .totalCount(10)
                .dailyCount(3)
                .currentStreak(5)
                .maxStreak(7)
                .lastStreakDate(LocalDate.now().minusDays(1))
                .rewardedTodayCount(2)
                .rewardedTotalCount(8)
                .lastDailyReset(LocalDate.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // lazy reset 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("lazyResetIfNeeded")
    class LazyResetTest {

        @Test
        @DisplayName("날짜 변경 → dailyCount, rewardedTodayCount 리셋")
        void lazyReset_nextDay() {
            UserActivityProgress p = createDefault();
            assertEquals(3, p.getDailyCount());
            assertEquals(2, p.getRewardedTodayCount());

            p.lazyResetIfNeeded(LocalDate.now().plusDays(1));

            assertEquals(0, p.getDailyCount(), "일일 카운트 리셋");
            assertEquals(0, p.getRewardedTodayCount(), "일일 리워드 카운트 리셋");
            assertEquals(10, p.getTotalCount(), "누적 카운트는 리셋 안 함");
            assertEquals(8, p.getRewardedTotalCount(), "누적 리워드 카운트 리셋 안 함");
        }

        @Test
        @DisplayName("같은 날 → 리셋 안 함")
        void lazyReset_sameDay() {
            UserActivityProgress p = createDefault();
            p.lazyResetIfNeeded(LocalDate.now());

            assertEquals(3, p.getDailyCount(), "같은 날이므로 유지");
            assertEquals(2, p.getRewardedTodayCount(), "같은 날이므로 유지");
        }

        @Test
        @DisplayName("streak은 lazyReset에서 건드리지 않음")
        void lazyReset_streakUntouched() {
            UserActivityProgress p = createDefault();
            int streakBefore = p.getCurrentStreak();

            p.lazyResetIfNeeded(LocalDate.now().plusDays(1));

            assertEquals(streakBefore, p.getCurrentStreak(),
                    "streak은 AttendanceService에서만 갱신, lazyReset 무관");
        }
    }

    // ──────────────────────────────────────────────
    // 카운터 증가/감소 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("카운터 증가/감소")
    class CounterTest {

        @Test
        @DisplayName("incrementTotalCount, incrementDailyCount")
        void increment() {
            UserActivityProgress p = createDefault();
            p.incrementTotalCount();
            p.incrementDailyCount();

            assertEquals(11, p.getTotalCount());
            assertEquals(4, p.getDailyCount());
        }

        @Test
        @DisplayName("incrementRewardedTodayCount, incrementRewardedTotalCount")
        void incrementRewarded() {
            UserActivityProgress p = createDefault();
            p.incrementRewardedTodayCount();
            p.incrementRewardedTotalCount();

            assertEquals(3, p.getRewardedTodayCount());
            assertEquals(9, p.getRewardedTotalCount());
        }

        @Test
        @DisplayName("decrementTotalCount — 0 미만 방지")
        void decrementTotalCount_floor() {
            UserActivityProgress p = UserActivityProgress.builder()
                    .userId("u").actionType("A").totalCount(0).build();

            p.decrementTotalCount();

            assertEquals(0, p.getTotalCount(), "0 미만으로 내려가지 않음");
        }

        @Test
        @DisplayName("decrementRewardedTotalCount — 0 미만 방지")
        void decrementRewardedTotalCount_floor() {
            UserActivityProgress p = UserActivityProgress.builder()
                    .userId("u").actionType("A").rewardedTotalCount(0).build();

            p.decrementRewardedTotalCount();

            assertEquals(0, p.getRewardedTotalCount(), "0 미만으로 내려가지 않음");
        }

        @Test
        @DisplayName("decrementTotalCount — 양수에서 감소")
        void decrementTotalCount_positive() {
            UserActivityProgress p = createDefault(); // totalCount=10
            p.decrementTotalCount();

            assertEquals(9, p.getTotalCount());
        }
    }

    // ──────────────────────────────────────────────
    // streak 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("updateStreak — 연속 기록")
    class StreakTest {

        @Test
        @DisplayName("어제 활동 → streak 이어감 (연속)")
        void updateStreak_consecutive() {
            UserActivityProgress p = createDefault(); // streak=5, lastStreakDate=어제

            p.updateStreak(LocalDate.now());

            assertEquals(6, p.getCurrentStreak(), "5→6 연속");
            assertEquals(LocalDate.now(), p.getLastStreakDate());
        }

        @Test
        @DisplayName("어제 미활동 → streak 1로 리셋")
        void updateStreak_broken() {
            UserActivityProgress p = UserActivityProgress.builder()
                    .userId("u").actionType("A")
                    .currentStreak(10)
                    .maxStreak(10)
                    .lastStreakDate(LocalDate.now().minusDays(3)) // 3일 전
                    .build();

            p.updateStreak(LocalDate.now());

            assertEquals(1, p.getCurrentStreak(), "streak 끊김 → 1로 리셋");
            assertEquals(10, p.getMaxStreak(), "역대 최고는 유지");
        }

        @Test
        @DisplayName("maxStreak 갱신 — currentStreak이 기존 max 초과")
        void updateStreak_newMax() {
            UserActivityProgress p = UserActivityProgress.builder()
                    .userId("u").actionType("A")
                    .currentStreak(7)
                    .maxStreak(7)
                    .lastStreakDate(LocalDate.now().minusDays(1))
                    .build();

            p.updateStreak(LocalDate.now());

            assertEquals(8, p.getCurrentStreak());
            assertEquals(8, p.getMaxStreak(), "새 최고 기록 갱신");
        }

        @Test
        @DisplayName("resetStreak — 강제 리셋 시 maxStreak 유지")
        void resetStreak() {
            UserActivityProgress p = createDefault(); // streak=5, maxStreak=7

            p.resetStreak();

            assertEquals(0, p.getCurrentStreak(), "streak 0으로 리셋");
            assertEquals(7, p.getMaxStreak(), "역대 최고는 유지");
        }

        @Test
        @DisplayName("첫 활동(lastStreakDate=null) → streak=1")
        void updateStreak_firstActivity() {
            UserActivityProgress p = UserActivityProgress.builder()
                    .userId("u").actionType("A")
                    .currentStreak(0)
                    .maxStreak(0)
                    .lastStreakDate(null)
                    .build();

            p.updateStreak(LocalDate.now());

            assertEquals(1, p.getCurrentStreak(), "첫 활동 → streak 1");
            assertEquals(1, p.getMaxStreak(), "첫 활동 → maxStreak 1");
        }
    }
}
