package com.monglepick.monglepickbackend.domain.reward.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PointsHistory 엔티티 단위 테스트.
 *
 * <p>설계서 v2.3 §4.2 + §13.4 기준으로 다음을 검증한다:
 * <ul>
 *   <li>INSERT-ONLY 정책 — @PreUpdate/@PreRemove 예외 발생</li>
 *   <li>빌더 — 신규 컬럼(actionType, baseAmount, appliedMultiplier) 포함 정상 생성</li>
 *   <li>point_after 체인 — 변동량과 잔액 스냅샷 관계</li>
 * </ul>
 */
class PointsHistoryTest {

    @Test
    @DisplayName("@PreUpdate — UPDATE 시도 시 UnsupportedOperationException 발생")
    void preventUpdate() {
        PointsHistory history = PointsHistory.builder()
                .userId("test_user")
                .pointChange(100)
                .pointAfter(1100)
                .pointType("earn")
                .build();

        assertThrows(UnsupportedOperationException.class,
                history::preventUpdate,
                "INSERT-ONLY 테이블이므로 UPDATE 차단");
    }

    @Test
    @DisplayName("@PreRemove — DELETE 시도 시 UnsupportedOperationException 발생")
    void preventDelete() {
        PointsHistory history = PointsHistory.builder()
                .userId("test_user")
                .pointChange(-50)
                .pointAfter(950)
                .pointType("spend")
                .build();

        assertThrows(UnsupportedOperationException.class,
                history::preventDelete,
                "INSERT-ONLY 테이블이므로 DELETE 차단");
    }

    @Test
    @DisplayName("빌더 — 신규 컬럼(actionType, baseAmount, appliedMultiplier) 포함 생성")
    void builder_withNewColumns() {
        PointsHistory history = PointsHistory.builder()
                .userId("test_user")
                .pointChange(22)
                .pointAfter(622)
                .pointType("earn")
                .description("REVIEW_CREATE 리워드")
                .referenceId("movie_m_123")
                .actionType("REVIEW_CREATE")
                .baseAmount(20)
                .appliedMultiplier(new BigDecimal("1.10"))
                .build();

        assertEquals("test_user", history.getUserId());
        assertEquals(22, history.getPointChange());
        assertEquals(622, history.getPointAfter());
        assertEquals("earn", history.getPointType());
        assertEquals("REVIEW_CREATE", history.getActionType());
        assertEquals(20, history.getBaseAmount());
        assertEquals(new BigDecimal("1.10"), history.getAppliedMultiplier());
        assertEquals("movie_m_123", history.getReferenceId());
    }

    @Test
    @DisplayName("빌더 — 비리워드 변동(AI 차감) 시 actionType/baseAmount/multiplier null")
    void builder_nonRewardChange() {
        PointsHistory history = PointsHistory.builder()
                .userId("test_user")
                .pointChange(-10)
                .pointAfter(990)
                .pointType("spend")
                .description("AI 추천 사용")
                .referenceId("session_abc123")
                .build();

        assertNull(history.getActionType(), "비리워드 변동이므로 actionType null");
        assertNull(history.getBaseAmount(), "비리워드 변동이므로 baseAmount null");
        assertNull(history.getAppliedMultiplier(), "비리워드 변동이므로 multiplier null");
    }

    @Test
    @DisplayName("point_after 체인 — 연속 레코드의 정합성 검증")
    void pointAfterChain() {
        // 시뮬레이션: 잔액 1000 → +100 → -50
        PointsHistory h1 = PointsHistory.builder()
                .pointChange(100).pointAfter(1100).pointType("earn").userId("u").build();
        PointsHistory h2 = PointsHistory.builder()
                .pointChange(-50).pointAfter(1050).pointType("spend").userId("u").build();

        // 체인 검증: h1.point_after + h2.point_change = h2.point_after
        assertEquals(h1.getPointAfter() + h2.getPointChange(), h2.getPointAfter(),
                "point_after 체인: 1100 + (-50) = 1050");
    }
}
