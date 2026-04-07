package com.monglepick.monglepickbackend.domain.reward.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserPoint 엔티티 단위 테스트.
 *
 * <p>설계서 v3.3 §4.1 기준으로 다음을 검증한다:
 * <ul>
 *   <li>정합성 공식: balance = total_earned - total_spent</li>
 *   <li>addPoints(isActivityReward) — earned_by_activity, daily_cap_used 분기</li>
 *   <li>lazy reset (일일 — dailyEarned/dailyCapUsed만. AI 쿼터는 UserAiQuota로 분리됨)</li>
 *   <li>deductPoints — 잔액 부족 예외</li>
 *   <li>getGradeCode — NORMAL fallback</li>
 * </ul>
 *
 * <p>v3.3 변경: AI 쿼터 관련 테스트(incrementAiUsage, resetMonthlyIfNeeded,
 * dailyAiUsed 등)는 {@code UserAiQuotaTest}로 이동하였다.</p>
 */
class UserPointTest {

    /** 테스트용 UserPoint 생성 헬퍼 */
    private UserPoint createDefault() {
        return UserPoint.builder()
                .userId("test_user")
                .balance(1000)
                .totalEarned(1000)
                .totalSpent(0)
                .dailyEarned(0)
                .dailyReset(LocalDate.now())
                .earnedByActivity(500)
                .dailyCapUsed(0)
                .build();
    }

    // ──────────────────────────────────────────────
    // addPoints 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("addPoints — 포인트 획득")
    class AddPointsTest {

        @Test
        @DisplayName("활동 리워드(isActivityReward=true) → earnedByActivity, dailyCapUsed 증가")
        void addPoints_activityReward() {
            UserPoint up = createDefault();
            int beforeEarnedByActivity = up.getEarnedByActivity(); // 500
            int beforeDailyCapUsed = up.getDailyCapUsed(); // 0

            up.addPoints(100, LocalDate.now(), true);

            assertEquals(1100, up.getBalance(), "balance += 100");
            assertEquals(1100, up.getTotalEarned(), "totalEarned += 100");
            assertEquals(beforeEarnedByActivity + 100, up.getEarnedByActivity(),
                    "활동 리워드이므로 earnedByActivity += 100");
            assertEquals(beforeDailyCapUsed + 100, up.getDailyCapUsed(),
                    "활동 리워드이므로 dailyCapUsed += 100");
        }

        @Test
        @DisplayName("결제 충전(isActivityReward=false) → earnedByActivity, dailyCapUsed 변동 없음")
        void addPoints_paymentCharge() {
            UserPoint up = createDefault();
            int beforeEarnedByActivity = up.getEarnedByActivity(); // 500
            int beforeDailyCapUsed = up.getDailyCapUsed(); // 0

            up.addPoints(500, LocalDate.now(), false);

            assertEquals(1500, up.getBalance(), "balance += 500");
            assertEquals(1500, up.getTotalEarned(), "totalEarned += 500");
            assertEquals(beforeEarnedByActivity, up.getEarnedByActivity(),
                    "결제 충전이므로 earnedByActivity 변동 없음");
            assertEquals(beforeDailyCapUsed, up.getDailyCapUsed(),
                    "결제 충전이므로 dailyCapUsed 변동 없음");
        }

        @Test
        @DisplayName("하위 호환 오버로드(2-파라미터) → isActivityReward=false 기본값")
        void addPoints_backwardCompatible() {
            UserPoint up = createDefault();
            int beforeEarnedByActivity = up.getEarnedByActivity();

            up.addPoints(200, LocalDate.now());

            assertEquals(1200, up.getBalance());
            assertEquals(beforeEarnedByActivity, up.getEarnedByActivity(),
                    "하위 호환 오버로드는 isActivityReward=false");
        }

        @Test
        @DisplayName("정합성 공식: balance = total_earned - total_spent")
        void addPoints_consistencyFormula() {
            UserPoint up = createDefault();
            up.addPoints(300, LocalDate.now(), true);
            up.deductPoints(150);

            assertEquals(up.getTotalEarned() - up.getTotalSpent(), up.getBalance(),
                    "balance = total_earned - total_spent 항상 성립");
        }
    }

    // ──────────────────────────────────────────────
    // deductPoints 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("deductPoints — 포인트 차감")
    class DeductPointsTest {

        @Test
        @DisplayName("잔액 충분 → 정상 차감")
        void deductPoints_success() {
            UserPoint up = createDefault(); // balance=1000
            up.deductPoints(400);

            assertEquals(600, up.getBalance());
            assertEquals(400, up.getTotalSpent());
        }

        @Test
        @DisplayName("잔액 부족 → IllegalArgumentException")
        void deductPoints_insufficientBalance() {
            UserPoint up = createDefault(); // balance=1000

            assertThrows(IllegalArgumentException.class, () -> up.deductPoints(1500),
                    "잔액 1000 < 필요 1500 → 예외 발생");
        }

        @Test
        @DisplayName("잔액 정확히 일치 → 정상 차감 (0원 잔액)")
        void deductPoints_exactBalance() {
            UserPoint up = createDefault(); // balance=1000
            up.deductPoints(1000);

            assertEquals(0, up.getBalance());
            assertEquals(1000, up.getTotalSpent());
        }
    }

    // ──────────────────────────────────────────────
    // lazy reset 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Lazy Reset")
    class LazyResetTest {

        @Test
        @DisplayName("일일 리셋 — 날짜 변경 시 dailyEarned, dailyCapUsed 리셋 (v3.3: dailyAiUsed는 UserAiQuota로 분리)")
        void resetDailyIfNeeded_nextDay() {
            UserPoint up = createDefault();
            up.addPoints(50, LocalDate.now(), true); // dailyEarned=50, dailyCapUsed=50

            assertEquals(50, up.getDailyEarned());
            assertEquals(50, up.getDailyCapUsed());

            // 다음 날로 리셋
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            up.resetDailyIfNeeded(tomorrow);

            assertEquals(0, up.getDailyEarned(), "일일 획득 리셋");
            assertEquals(0, up.getDailyCapUsed(), "일일 cap 리셋");
            assertEquals(tomorrow, up.getDailyReset(), "리셋 기준일 갱신");
        }

        @Test
        @DisplayName("일일 리셋 — 같은 날이면 리셋 안 함")
        void resetDailyIfNeeded_sameDay() {
            UserPoint up = createDefault();
            up.addPoints(50, LocalDate.now(), true);

            up.resetDailyIfNeeded(LocalDate.now());

            assertEquals(50, up.getDailyEarned(), "같은 날이므로 리셋 안 함");
        }

        // v3.3: 월간 AI 리셋(resetMonthlyIfNeeded) 테스트는 UserAiQuotaTest로 이동
    }

    // ──────────────────────────────────────────────
    // getGradeCode 테스트
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("getGradeCode — 등급 코드 조회")
    class GradeCodeTest {

        @Test
        @DisplayName("grade=null → NORMAL fallback")
        void getGradeCode_null() {
            UserPoint up = UserPoint.builder()
                    .userId("test_user")
                    .grade(null) // grade 미설정
                    .build();

            assertEquals("NORMAL", up.getGradeCode(),
                    "grade가 null이면 NORMAL 반환 (설계서 v2.3 기본 등급)");
        }

        @Test
        @DisplayName("grade 설정됨 → 해당 등급 코드 반환")
        void getGradeCode_withGrade() {
            Grade silver = Grade.builder()
                    .gradeCode("SILVER")
                    .gradeName("실버")
                    .minPoints(2000)
                    .rewardMultiplier(new BigDecimal("1.30"))
                    .dailyEarnCap(1200)
                    .build();
            UserPoint up = UserPoint.builder()
                    .userId("test_user")
                    .grade(silver)
                    .build();

            assertEquals("SILVER", up.getGradeCode());
        }
    }

    // v3.3: AI 사용 횟수 테스트(incrementAiUsage/getDailyAiUsed/getMonthlyAiUsed)는
    //        UserAiQuotaTest로 이동하였다.
}
