package com.monglepick.monglepickbackend.domain.reward.controller;

import com.monglepick.monglepickbackend.domain.reward.dto.AiQuotaStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.BalanceResponse;
import com.monglepick.monglepickbackend.domain.reward.service.QuotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PointController.getAiQuotaStatus 단위 테스트.
 *
 * <p>실제 Spring 컨텍스트 없이 Mockito 만으로 검증한다.
 * SecurityConfig / FilterChain 의존 없이 서비스 호출 흐름만 확인.</p>
 */
@ExtendWith(MockitoExtension.class)
class PointControllerAiQuotaTest {

    @Mock
    private PointService pointService;

    @Mock
    private QuotaService quotaService;

    /* PointController 는 BaseController 상속이므로 직접 인스턴스 생성 불가 —
       @InjectMocks 가 @Mock 을 주입해 준다. 나머지 필드(attendanceService 등)는
       MockitoExtension 이 null 로 남겨두며, 이 테스트에서 호출하지 않으므로 문제없다. */
    @InjectMocks
    private PointController pointController;

    @Test
    @DisplayName("getAiQuotaStatus — JWT principal 에서 userId 추출 후 QuotaService 위임")
    void getAiQuotaStatus_delegatesToQuotaService() {
        // given
        String userId = "test-user-01";
        Principal principal = () -> userId;

        BalanceResponse balanceResponse = new BalanceResponse(500, "BRONZE", 800);
        when(pointService.getBalance(userId)).thenReturn(balanceResponse);

        OffsetDateTime resetAt = LocalDate.now().plusDays(1)
                .atTime(LocalTime.MIDNIGHT)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();

        AiQuotaStatusResponse expected = new AiQuotaStatusResponse(
                2, 5, -1, 0, 3, 30, resetAt);
        when(quotaService.getAiQuotaStatus(userId, "BRONZE")).thenReturn(expected);

        // when
        ResponseEntity<AiQuotaStatusResponse> response = pointController.getAiQuotaStatus(principal);

        // then
        assertEquals(200, response.getStatusCode().value());
        AiQuotaStatusResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.dailyAiUsed());
        assertEquals(5, body.dailyAiLimit());
        assertEquals(-1, body.remainingAiBonus());   // 구독 없음
        assertEquals(0, body.purchasedAiTokens());
        assertEquals(3, body.monthlyCouponUsed());
        assertEquals(30, body.monthlyCouponLimit());
        assertNotNull(body.resetAt());

        // 서비스 호출 순서 검증
        verify(pointService).getBalance(userId);
        verify(quotaService).getAiQuotaStatus(userId, "BRONZE");
    }

    @Test
    @DisplayName("getAiQuotaStatus — principal null 이면 UNAUTHORIZED 예외 발생")
    void getAiQuotaStatus_nullPrincipal_throwsUnauthorized() {
        // BaseController.resolveUserId() 가 null principal 에 대해 BusinessException 을 던진다
        assertThrows(
                com.monglepick.monglepickbackend.global.exception.BusinessException.class,
                () -> pointController.getAiQuotaStatus(null)
        );
        verifyNoInteractions(pointService, quotaService);
    }
}
