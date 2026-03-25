package com.monglepick.monglepickbackend.domain.payment.controller;

import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionPlanResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionStatusResponse;
import com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 구독 API 컨트롤러 — 구독 상품 조회, 구독 상태 조회, 구독 취소.
 *
 * <p>클라이언트(monglepick-client)가 호출하는 구독 REST API 3개를 제공한다.</p>
 *
 * @see SubscriptionService 구독 비즈니스 로직
 */
@Tag(name = "구독", description = "구독 상품, 현황, 취소")
@RestController
@RequestMapping("/api/v1/subscription")
@Slf4j
@RequiredArgsConstructor
public class SubscriptionController {

    /** 구독 서비스 */
    private final SubscriptionService subscriptionService;

    // ──────────────────────────────────────────────
    // GET /api/v1/subscription/plans — 구독 상품 목록
    // ──────────────────────────────────────────────

    /**
     * 활성 구독 상품 목록을 조회한다.
     *
     * <p>클라이언트의 "구독 상품 선택" 화면에서 사용한다.
     * 활성 상품만 가격 오름차순으로 반환한다.</p>
     *
     * @return 200 OK + 활성 구독 상품 목록
     */
    @Operation(summary = "구독 상품 목록 조회", description = "활성 구독 상품 목록을 가격 오름차순으로 반환 (비로그인 허용)")
    @ApiResponse(responseCode = "200", description = "상품 목록 조회 성공")
    @SecurityRequirement(name = "")
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanResponse>> getPlans() {
        log.debug("구독 상품 목록 조회 API 호출");

        List<SubscriptionPlanResponse> plans = subscriptionService.getActivePlans();
        return ResponseEntity.ok(plans);
    }

    // ──────────────────────────────────────────────
    // GET /api/v1/subscription/status — 구독 상태 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자의 구독 상태를 조회한다.
     *
     * @return 200 OK + SubscriptionStatusResponse
     */
    @Operation(summary = "내 구독 상태 조회", description = "활성 구독 여부 및 상세 정보 (만료일, 자동갱신 등)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 상태 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getStatus(Principal principal) {
        /* C-6: principal null 체크 추가 (NPE 방지) */
        String userId = resolveUserId(principal);
        log.debug("구독 상태 조회 API 호출: userId={}", userId);

        SubscriptionStatusResponse status = subscriptionService.getStatus(userId);
        return ResponseEntity.ok(status);
    }

    // ──────────────────────────────────────────────
    // POST /api/v1/subscription/cancel — 구독 취소
    // ──────────────────────────────────────────────

    /**
     * 구독을 취소한다.
     *
     * <p>취소 후에도 만료일까지 서비스 이용이 가능하며, 자동 갱신이 중지된다.</p>
     *
     * @return 200 OK + 취소 안내 메시지
     */
    @Operation(summary = "구독 취소", description = "활성 구독 취소. 만료일까지 기존 혜택 유지, 자동갱신 중지")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 취소 성공"),
            @ApiResponse(responseCode = "404", description = "활성 구독 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelSubscription(Principal principal) {
        /* C-6: principal null 체크 추가 (NPE 방지) */
        String userId = resolveUserId(principal);
        log.info("구독 취소 API 호출: userId={}", userId);

        subscriptionService.cancelSubscription(userId);

        return ResponseEntity.ok(Map.of(
                "message", "구독이 취소되었습니다. 만료일까지 기존 포인트를 사용할 수 있습니다."
        ));
    }

    // ──────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────

    /**
     * Principal에서 userId를 안전하게 추출한다.
     * null인 경우 UNAUTHORIZED 예외를 던진다 (NPE 방지).
     *
     * <p>C-6 버그 수정: principal 직접 접근 시 NPE 발생 가능성 제거</p>
     */
    private String resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getName();
    }
}
