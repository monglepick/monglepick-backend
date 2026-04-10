package com.monglepick.monglepickbackend.domain.reward.controller;

import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.PurchaseResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItemsResponse;
import com.monglepick.monglepickbackend.domain.reward.service.PointShopService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 포인트 상점 컨트롤러 — AI 이용권 구매 REST API 엔드포인트.
 *
 * <p>포인트를 사용하여 AI 추천 이용권({@code purchased_ai_tokens})을 구매한다.
 * 구매된 이용권은 등급 무료 쿼터와 구독 보너스를 소진한 이후 자동 소비된다
 * (QuotaService 4단계 모델 3단계 — PURCHASED, 일일 한도 우회 가능).</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/point/shop/items}           — 상점 아이템 목록 + 현재 잔액</li>
 *   <li>{@code POST /api/v1/point/shop/ai-tokens}       — AI 이용권 팩 구매 (1/5/20/50회)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 JWT Bearer 토큰이 필요하다.
 * {@link BaseController#resolveUserId(Principal)}를 통해 userId를 추출한다.</p>
 *
 * <h3>상품 정보 (설계서 v3.2 — 단가 10P/회 = 100원/회 통일)</h3>
 * <table border="1">
 *   <tr><th>packType</th><th>가격</th><th>지급</th></tr>
 *   <tr><td>AI_TOKEN_1</td><td>10P</td><td>1회</td></tr>
 *   <tr><td>AI_TOKEN_5</td><td>50P</td><td>5회</td></tr>
 *   <tr><td>AI_TOKEN_20</td><td>200P</td><td>20회</td></tr>
 *   <tr><td>AI_TOKEN_50</td><td>500P</td><td>50회</td></tr>
 * </table>
 *
 * <p>v3.2 변경: AI_DAILY_EXTEND 엔드포인트 폐지. PURCHASED 토큰 자체가
 * QuotaService 3-소스 모델에서 일일 무료 한도를 우회하므로 별도 상품 불필요.</p>
 *
 * @see PointShopService
 * @see com.monglepick.monglepickbackend.domain.reward.service.QuotaService
 */
@Tag(name = "포인트 상점", description = "포인트로 AI 이용권 구매")
@RestController
@RequestMapping("/api/v1/point/shop")
@Slf4j
@RequiredArgsConstructor
public class PointShopController extends BaseController {

    /** 포인트 상점 서비스 — 아이템 목록 조회·구매 비즈니스 로직 */
    private final PointShopService pointShopService;

    /**
     * 포인트 상점 아이템 목록을 조회한다.
     *
     * <p>현재 사용자의 포인트 잔액, AI 이용권 잔여 횟수, 구매 가능한 상품 목록을 반환한다.
     * 클라이언트 상점 화면 진입 시 최초 1회 호출하여 UI를 구성한다.</p>
     *
     * @param principal JWT 인증 정보 (userId 추출용)
     * @return 200 OK + ShopItemsResponse (잔액, 이용권 잔여 횟수, 상품 목록 3종)
     */
    @Operation(
            summary = "포인트 상점 아이템 목록 조회",
            description = "현재 보유 포인트 잔액, AI 이용권 잔여 횟수, 구매 가능한 상품 목록(3종)을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "포인트 레코드 없음 (P002)")
    })
    @GetMapping("/items")
    public ResponseEntity<ShopItemsResponse> getShopItems(Principal principal) {
        // JWT Principal에서 userId 추출 (null이면 UNAUTHORIZED 예외)
        String userId = resolveUserId(principal);
        log.debug("포인트 상점 아이템 목록 조회: userId={}", userId);

        ShopItemsResponse response = pointShopService.getShopItems(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * AI 이용권 팩을 구매한다 (AI_TOKEN_1 / AI_TOKEN_5 / AI_TOKEN_20 / AI_TOKEN_50).
     *
     * <p>packType에 따라 포인트를 차감하고 AI 이용권을 지급한다.
     * 잔액이 부족하면 402 Payment Required 응답이 반환된다.
     * 구매된 이용권은 등급 일일 무료 한도를 우회하여 사용 가능하다.</p>
     *
     * <h3>packType별 상품 (설계서 v3.2 — 단가 10P/회 통일)</h3>
     * <ul>
     *   <li>{@code AI_TOKEN_1}  — 10P 차감, 1회 지급</li>
     *   <li>{@code AI_TOKEN_5}  — 50P 차감, 5회 지급</li>
     *   <li>{@code AI_TOKEN_20} — 200P 차감, 20회 지급</li>
     *   <li>{@code AI_TOKEN_50} — 500P 차감, 50회 지급</li>
     * </ul>
     *
     * @param principal JWT 인증 정보 (userId 추출용)
     * @param packType  구매할 팩 유형 ("AI_TOKEN_1", "AI_TOKEN_5", "AI_TOKEN_20", "AI_TOKEN_50")
     * @return 200 OK + PurchaseResponse (차감 포인트, 추가 토큰, 잔여 잔액, 전체 토큰 잔여 횟수)
     */
    @Operation(
            summary = "AI 이용권 팩 구매",
            description = "포인트를 소비하여 AI 이용권을 구매합니다. "
                    + "packType=AI_TOKEN_1(10P→1회), AI_TOKEN_5(50P→5회), AI_TOKEN_20(200P→20회), AI_TOKEN_50(500P→50회).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구매 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 packType (G002)"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "402", description = "포인트 잔액 부족 (P001)"),
            @ApiResponse(responseCode = "404", description = "포인트 레코드 없음 (P002)")
    })
    @PostMapping("/ai-tokens")
    public ResponseEntity<PurchaseResponse> purchaseAiTokens(
            Principal principal,
            @Parameter(description = "구매할 팩 유형 (AI_TOKEN_1, AI_TOKEN_5, AI_TOKEN_20, AI_TOKEN_50)", required = true)
            @RequestParam String packType
    ) {
        // JWT Principal에서 userId 추출
        String userId = resolveUserId(principal);
        log.info("AI 이용권 팩 구매 요청: userId={}, packType={}", userId, packType);

        PurchaseResponse response = pointShopService.purchaseAiTokens(userId, packType);
        return ResponseEntity.ok(response);
    }
}
