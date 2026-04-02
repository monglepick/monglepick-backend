package com.monglepick.monglepickbackend.domain.reward.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RewardPolicy 엔티티 단위 테스트.
 *
 * <p>설계서 v2.3 §4.3 기준으로 다음을 검증한다:
 * <ul>
 *   <li>빌더 — 9개 신규 컬럼 포함 생성, 기본값 검증</li>
 *   <li>헬퍼 메서드 — hasDailyLimit, hasMaxCount, isThresholdBased, hasCooldown, hasMinContentLength</li>
 *   <li>updatePolicy — 6-파라미터 업데이트</li>
 *   <li>컬럼명 — actionType (activity_type이 아닌 action_type)</li>
 * </ul>
 */
class RewardPolicyTest {

    @Nested
    @DisplayName("빌더 + 기본값")
    class BuilderTest {

        @Test
        @DisplayName("반복 활동 정책 생성 (REVIEW_CREATE)")
        void builder_repeatActivity() {
            RewardPolicy policy = RewardPolicy.builder()
                    .actionType("REVIEW_CREATE")
                    .activityName("리뷰 작성")
                    .actionCategory("CONTENT")
                    .pointsAmount(20)
                    .pointType("earn")
                    .dailyLimit(3)
                    .maxCount(0)
                    .cooldownSeconds(0)
                    .minContentLength(10)
                    .build();

            assertEquals("REVIEW_CREATE", policy.getActionType());
            assertEquals("CONTENT", policy.getActionCategory());
            assertEquals("earn", policy.getPointType());
            assertEquals(20, policy.getPointsAmount());
            assertEquals(3, policy.getDailyLimit());
            assertEquals(10, policy.getMinContentLength());
            assertNull(policy.getThresholdTarget(), "일반 활동은 threshold 없음");
            assertNull(policy.getParentActionType(), "일반 활동은 parent 없음");
        }

        @Test
        @DisplayName("마일스톤 정책 생성 (REVIEW_MILESTONE_5)")
        void builder_milestone() {
            RewardPolicy policy = RewardPolicy.builder()
                    .actionType("REVIEW_MILESTONE_5")
                    .activityName("리뷰 5회")
                    .actionCategory("MILESTONE")
                    .pointsAmount(30)
                    .pointType("bonus")
                    .dailyLimit(0)
                    .maxCount(1)
                    .thresholdCount(5)
                    .thresholdTarget("TOTAL")
                    .parentActionType("REVIEW_CREATE")
                    .limitType("ONCE")
                    .build();

            assertEquals("TOTAL", policy.getThresholdTarget());
            assertEquals(5, policy.getThresholdCount());
            assertEquals("REVIEW_CREATE", policy.getParentActionType());
            assertEquals("bonus", policy.getPointType());
            assertEquals(1, policy.getMaxCount());
        }

        @Test
        @DisplayName("기본값 검증 — pointType=earn, dailyLimit=0, maxCount=0 등")
        void builder_defaults() {
            RewardPolicy policy = RewardPolicy.builder()
                    .actionType("TEST")
                    .activityName("테스트")
                    .pointsAmount(10)
                    .build();

            assertEquals("earn", policy.getPointType(), "기본 pointType=earn");
            assertEquals(0, policy.getDailyLimit(), "기본 dailyLimit=0 (무제한)");
            assertEquals(0, policy.getMaxCount(), "기본 maxCount=0 (무제한)");
            assertEquals(0, policy.getCooldownSeconds(), "기본 cooldown=0");
            assertEquals(0, policy.getMinContentLength(), "기본 minContentLength=0");
            assertEquals(0, policy.getThresholdCount(), "기본 thresholdCount=0");
            assertEquals("CONTENT", policy.getActionCategory(), "기본 카테고리=CONTENT");
            assertTrue(policy.getIsActive(), "기본 활성 상태");
        }
    }

    @Nested
    @DisplayName("헬퍼 메서드")
    class HelperTest {

        @Test
        @DisplayName("hasDailyLimit — dailyLimit > 0일 때만 true")
        void hasDailyLimit() {
            RewardPolicy withLimit = RewardPolicy.builder()
                    .actionType("A").activityName("A").pointsAmount(10)
                    .dailyLimit(3).build();
            RewardPolicy noLimit = RewardPolicy.builder()
                    .actionType("B").activityName("B").pointsAmount(10)
                    .dailyLimit(0).build();

            assertTrue(withLimit.hasDailyLimit());
            assertFalse(noLimit.hasDailyLimit());
        }

        @Test
        @DisplayName("hasMaxCount — maxCount > 0일 때만 true")
        void hasMaxCount() {
            RewardPolicy once = RewardPolicy.builder()
                    .actionType("A").activityName("A").pointsAmount(10)
                    .maxCount(1).build();
            RewardPolicy unlimited = RewardPolicy.builder()
                    .actionType("B").activityName("B").pointsAmount(10)
                    .maxCount(0).build();

            assertTrue(once.hasMaxCount());
            assertFalse(unlimited.hasMaxCount());
        }

        @Test
        @DisplayName("isThresholdBased — thresholdTarget + thresholdCount 둘 다 있을 때")
        void isThresholdBased() {
            RewardPolicy milestone = RewardPolicy.builder()
                    .actionType("A").activityName("A").pointsAmount(10)
                    .thresholdTarget("TOTAL").thresholdCount(5).build();
            RewardPolicy normal = RewardPolicy.builder()
                    .actionType("B").activityName("B").pointsAmount(10)
                    .build();

            assertTrue(milestone.isThresholdBased());
            assertFalse(normal.isThresholdBased());
        }

        @Test
        @DisplayName("hasCooldown — cooldownSeconds > 0일 때만 true")
        void hasCooldown() {
            RewardPolicy withCooldown = RewardPolicy.builder()
                    .actionType("A").activityName("A").pointsAmount(10)
                    .cooldownSeconds(10).build();
            RewardPolicy noCooldown = RewardPolicy.builder()
                    .actionType("B").activityName("B").pointsAmount(10)
                    .cooldownSeconds(0).build();

            assertTrue(withCooldown.hasCooldown());
            assertFalse(noCooldown.hasCooldown());
        }

        @Test
        @DisplayName("hasMinContentLength — minContentLength > 0일 때만 true")
        void hasMinContentLength() {
            RewardPolicy withLength = RewardPolicy.builder()
                    .actionType("A").activityName("A").pointsAmount(10)
                    .minContentLength(10).build();
            RewardPolicy noLength = RewardPolicy.builder()
                    .actionType("B").activityName("B").pointsAmount(10)
                    .minContentLength(0).build();

            assertTrue(withLength.hasMinContentLength());
            assertFalse(noLength.hasMinContentLength());
        }
    }

    @Test
    @DisplayName("updatePolicy — 6-파라미터 부분 업데이트")
    void updatePolicy() {
        RewardPolicy policy = RewardPolicy.builder()
                .actionType("REVIEW_CREATE")
                .activityName("리뷰 작성")
                .pointsAmount(20)
                .dailyLimit(3)
                .maxCount(0)
                .cooldownSeconds(0)
                .minContentLength(10)
                .description("원본 설명")
                .build();

        // pointsAmount와 description만 변경, 나머지 null → 기존 유지
        policy.updatePolicy(30, null, null, null, null, "금액 상향 조정");

        assertEquals(30, policy.getPointsAmount(), "30P로 변경");
        assertEquals(3, policy.getDailyLimit(), "null이므로 기존 유지");
        assertEquals(0, policy.getMaxCount(), "null이므로 기존 유지");
        assertEquals("금액 상향 조정", policy.getDescription(), "설명 변경");
    }
}
