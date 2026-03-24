package com.monglepick.monglepickbackend.domain.payment.controller;

import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionPlanResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionStatusResponse;
import com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 구독 API 컨트롤러 — 구독 상품 조회, 구독 상태 조회, 구독 취소.
 *
 * <p>클라이언트(monglepick-client)가 호출하는 구독 REST API 3개를 제공한다.</p>
 *
 * <h3>API 엔드포인트</h3>
 * <table>
 *   <tr><th>메서드</th><th>경로</th><th>설명</th></tr>
 *   <tr><td>GET</td><td>/api/v1/subscription/plans</td><td>활성 구독 상품 목록</td></tr>
 *   <tr><td>GET</td><td>/api/v1/subscription/status</td><td>내 구독 상태 조회</td></tr>
 *   <tr><td>POST</td><td>/api/v1/subscription/cancel</td><td>구독 취소</td></tr>
 * </table>
 *
 * <h3>구독 플로우</h3>
 * <pre>
 * 1. GET  /plans   → 구독 상품 목록 표시
 * 2. POST /payment/orders (PaymentController) → 구독 결제 주문 생성
 * 3. POST /payment/confirm → 결제 승인 + 구독 활성화 + 포인트 지급
 * 4. GET  /status  → 활성 구독 확인
 * 5. POST /cancel  → 구독 취소 (만료일까지 혜택 유지)
 * </pre>
 *
 * <h3>인증</h3>
 * <p>현재는 userId를 요청 Param으로 직접 받지만,
 * 향후 JWT {@code @AuthenticationPrincipal}로 교체할 예정이다.</p>
 *
 * @see SubscriptionService 구독 비즈니스 로직
 */
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
     * <h4>응답 예시</h4>
     * <pre>{@code
     * [
     *   {
     *     "planId": 1,
     *     "planCode": "monthly_basic",
     *     "name": "월간 기본",
     *     "periodType": "MONTHLY",
     *     "price": 3900,
     *     "pointsPerPeriod": 3000,
     *     "description": "매월 3,000P 지급"
     *   },
     *   ...
     * ]
     * }</pre>
     *
     * @return 200 OK + 활성 구독 상품 목록
     */
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
     * <p>클라이언트의 "내 구독" 화면에서 사용한다.
     * 활성 구독이 있으면 상세 정보를 반환하고,
     * 없으면 {@code hasActiveSubscription=false}를 반환한다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * GET /api/v1/subscription/status?userId=user_abc123
     * }</pre>
     *
     * <h4>응답 예시 (활성 구독 있음)</h4>
     * <pre>{@code
     * {
     *   "hasActiveSubscription": true,
     *   "planName": "월간 기본",
     *   "status": "ACTIVE",
     *   "startedAt": "2026-03-01T10:00:00",
     *   "expiresAt": "2026-04-01T10:00:00",
     *   "autoRenew": true
     * }
     * }</pre>
     *
     * @param userId 사용자 ID (필수)
     * @return 200 OK + SubscriptionStatusResponse
     */
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getStatus(
            @RequestParam String userId) {
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
     * <p>사용자의 활성 구독을 취소한다.
     * 취소 후에도 만료일까지 서비스 이용이 가능하며,
     * 자동 갱신이 중지된다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * POST /api/v1/subscription/cancel?userId=user_abc123
     * }</pre>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * {
     *   "message": "구독이 취소되었습니다. 만료일까지 기존 포인트를 사용할 수 있습니다."
     * }
     * }</pre>
     *
     * @param userId 사용자 ID (필수)
     * @return 200 OK + 취소 안내 메시지
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelSubscription(
            @RequestParam String userId) {
        log.info("구독 취소 API 호출: userId={}", userId);

        subscriptionService.cancelSubscription(userId);

        return ResponseEntity.ok(Map.of(
                "message", "구독이 취소되었습니다. 만료일까지 기존 포인트를 사용할 수 있습니다."
        ));
    }
}
