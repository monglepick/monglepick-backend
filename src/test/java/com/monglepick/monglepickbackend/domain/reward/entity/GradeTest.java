package com.monglepick.monglepickbackend.domain.reward.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grade 엔티티 단위 테스트.
 *
 * <p>설계서 v3.1 기준으로 다음을 검증한다:
 * <ul>
 *   <li>빌더 — rewardMultiplier, dailyEarnCap, monthlyAiLimit 포함 생성</li>
 *   <li>updateQuota — 5-파라미터 업데이트, null 시 기존 값 유지
 *       (v3.0에서 freeDailyCount 제거, v3.1에서 monthlyAiLimit 복원)</li>
 *   <li>기본값 — rewardMultiplier=1.00, dailyEarnCap=0</li>
 * </ul>
 */
class GradeTest {

    @Test
    @DisplayName("빌더 — 5등급 시드 데이터 기준 GOLD 등급 생성")
    void builder_goldGrade() {
        Grade gold = Grade.builder()
                .gradeCode("GOLD")
                .gradeName("골드")
                .minPoints(5000)
                .dailyAiLimit(30)
                .monthlyAiLimit(600)
                .maxInputLength(1000)
                .rewardMultiplier(new BigDecimal("1.50"))
                .dailyEarnCap(2000)
                .sortOrder(3)
                .build();

        assertEquals("GOLD", gold.getGradeCode());
        assertEquals(5000, gold.getMinPoints());
        assertEquals(new BigDecimal("1.50"), gold.getRewardMultiplier());
        assertEquals(2000, gold.getDailyEarnCap());
        assertEquals(30, gold.getDailyAiLimit());
    }

    @Test
    @DisplayName("기본값 — rewardMultiplier=1.00, dailyEarnCap=0")
    void defaults() {
        Grade grade = Grade.builder()
                .gradeCode("TEST")
                .minPoints(0)
                .build();

        assertEquals(BigDecimal.ONE, grade.getRewardMultiplier(),
                "기본 배율 1.00");
        assertEquals(0, grade.getDailyEarnCap(),
                "기본 일일 상한 0 (무제한)");
        assertTrue(grade.getIsActive(), "기본 활성 상태");
    }

    @Test
    @DisplayName("updateQuota — 새 파라미터(rewardMultiplier, dailyEarnCap) 포함 업데이트")
    void updateQuota_withNewFields() {
        Grade grade = Grade.builder()
                .gradeCode("SILVER")
                .minPoints(2000)
                .dailyAiLimit(10)
                .monthlyAiLimit(200)
                .maxInputLength(500)
                .rewardMultiplier(new BigDecimal("1.30"))
                .dailyEarnCap(1200)
                .build();

        // 배율과 상한만 변경, 나머지 null → 기존 값 유지
        // updateQuota 시그니처: (dailyAiLimit, monthlyAiLimit, freeDailyCount, maxInputLength, rewardMultiplier, dailyEarnCap)
        grade.updateQuota(null, null, null, null,
                new BigDecimal("1.50"), 1500);

        assertEquals(10, grade.getDailyAiLimit(), "null이므로 기존 값 유지");
        assertEquals(200, grade.getMonthlyAiLimit(), "null이므로 기존 값 유지");
        assertEquals(500, grade.getMaxInputLength(), "null이므로 기존 값 유지");
        assertEquals(new BigDecimal("1.50"), grade.getRewardMultiplier(), "배율 변경됨");
        assertEquals(1500, grade.getDailyEarnCap(), "상한 변경됨");
    }

    @Test
    @DisplayName("updateQuota — 전체 필드 변경")
    void updateQuota_allFields() {
        Grade grade = Grade.builder()
                .gradeCode("NORMAL")
                .minPoints(0)
                .dailyAiLimit(3)
                .monthlyAiLimit(30)
                .maxInputLength(200)
                .rewardMultiplier(BigDecimal.ONE)
                .dailyEarnCap(500)
                .build();

        // updateQuota 시그니처: (dailyAiLimit, monthlyAiLimit, freeDailyCount, maxInputLength, rewardMultiplier, dailyEarnCap)
        grade.updateQuota(5, 80, 1, 300,
                new BigDecimal("1.10"), 800);

        assertEquals(5, grade.getDailyAiLimit());
        assertEquals(80, grade.getMonthlyAiLimit());
        assertEquals(1, grade.getFreeDailyCount());
        assertEquals(300, grade.getMaxInputLength());
        assertEquals(new BigDecimal("1.10"), grade.getRewardMultiplier());
        assertEquals(800, grade.getDailyEarnCap());
    }
}
