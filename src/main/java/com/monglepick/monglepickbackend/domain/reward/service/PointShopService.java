package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.PurchaseResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItem;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItemsResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포인트 상점 서비스 — AI 이용권 아이템 목록 조회·구매 비즈니스 로직.
 *
 * <p>사용자가 보유 포인트를 소비하여 AI 추천 이용권({@code purchased_ai_tokens})을 구매한다.
 * 구매된 이용권은 {@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService}의
 * 4단계 모델 3단계(PURCHASED)에서 소비된다.</p>
 *
 * <h3>지원 상품 (packType)</h3>
 * <table border="1">
 *   <tr><th>packType</th><th>차감 포인트</th><th>지급 토큰</th><th>단가</th></tr>
 *   <tr><td>AI_TOKEN_5</td><td>200P</td><td>5회</td><td>40P/회</td></tr>
 *   <tr><td>AI_TOKEN_20</td><td>700P</td><td>20회</td><td>35P/회 (번들 할인)</td></tr>
 *   <tr><td>AI_DAILY_EXTEND</td><td>100P</td><td>5회</td><td>20P/회 (일일 한도 우회 전용)</td></tr>
 * </table>
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>packType 유효성 검증 → 차감 포인트·지급 토큰 결정</li>
 *   <li>{@link PointService#deductPoint} 호출 — 비관적 락 + 잔액 검증 + 이력 기록</li>
 *   <li>{@link UserPoint#addPurchasedTokens(int)} 호출 — purchased_ai_tokens 증가</li>
 *   <li>구매 결과 응답 반환</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 목록 조회 기본값</li>
 *   <li>{@link #purchaseAiTokens} / {@link #purchaseAiDailyExtend}: 개별 {@code @Transactional} 오버라이드</li>
 * </ul>
 *
 * <h3>동시성</h3>
 * <p>포인트 차감은 {@link PointService#deductPoint}가 비관적 락(PESSIMISTIC_WRITE)으로 처리하므로
 * 이 서비스에서는 별도 락 처리가 불필요하다. 단, 토큰 증가({@link UserPoint#addPurchasedTokens})는
 * 동일 트랜잭션 안에서 수행하여 포인트 차감·토큰 지급의 원자성을 보장한다.</p>
 *
 * @see PointService
 * @see com.monglepick.monglepickbackend.domain.reward.controller.PointShopController
 * @see com.monglepick.monglepickbackend.domain.reward.entity.UserPoint
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointShopService {

    // ──────────────────────────────────────────────
    // 상품 상수 (packType 식별자 → 포인트 비용 / 지급 토큰 수)
    // ──────────────────────────────────────────────

    /** AI 이용권 5회 팩 — packType 식별자 */
    private static final String PACK_AI_TOKEN_5 = "AI_TOKEN_5";

    /** AI 이용권 20회 팩 — packType 식별자 (번들 할인) */
    private static final String PACK_AI_TOKEN_20 = "AI_TOKEN_20";

    /** 일일 한도 우회 5회 팩 — packType 식별자 */
    private static final String PACK_AI_DAILY_EXTEND = "AI_DAILY_EXTEND";

    /** AI 이용권 5회 팩 가격 (포인트) */
    private static final int COST_AI_TOKEN_5 = 200;

    /** AI 이용권 20회 팩 가격 (포인트, 번들 할인 35P/회) */
    private static final int COST_AI_TOKEN_20 = 700;

    /** 일일 한도 우회 5회 팩 가격 (포인트, 20P/회) */
    private static final int COST_AI_DAILY_EXTEND = 100;

    /** AI 이용권 5회 팩 지급 횟수 */
    private static final int AMOUNT_AI_TOKEN_5 = 5;

    /** AI 이용권 20회 팩 지급 횟수 */
    private static final int AMOUNT_AI_TOKEN_20 = 20;

    /** 일일 한도 우회 팩 지급 횟수 */
    private static final int AMOUNT_AI_DAILY_EXTEND = 5;

    // ──────────────────────────────────────────────
    // 의존성
    // ──────────────────────────────────────────────

    /** 포인트 서비스 — 잔액 차감·이력 기록 (비관적 락 포함) */
    private final PointService pointService;

    /**
     * 사용자 포인트 리포지토리 — 잔액 조회용.
     *
     * <p>v3.3: purchased_ai_tokens는 UserAiQuota로 이동하였으므로
     * 이 리포지토리는 잔액({@code balance}) 조회 목적으로만 사용한다.</p>
     */
    private final UserPointRepository userPointRepository;

    /**
     * 사용자 AI 쿼터 리포지토리 — purchased_ai_tokens 조회·갱신 (v3.3 신규).
     *
     * <p>v3.3: AI 이용권 토큰({@code purchased_ai_tokens})이 {@code user_points}에서
     * {@code user_ai_quota} 테이블로 분리되었다.
     * 이용권 구매 시 {@link UserAiQuota#addPurchasedTokens(int)}를 통해 갱신한다.</p>
     */
    private final UserAiQuotaRepository userAiQuotaRepository;

    // ──────────────────────────────────────────────
    // 읽기 메서드 (클래스 레벨 readOnly 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 포인트 상점 아이템 목록과 현재 사용자 잔액을 반환한다.
     *
     * <p>클라이언트가 상점 화면 진입 시 호출한다.
     * 현재 잔액·AI 이용권 보유량을 함께 반환하여 구매 가능 여부를 즉시 표시할 수 있게 한다.</p>
     *
     * @param userId 사용자 ID
     * @return 현재 잔액, AI 이용권 잔여 횟수, 전체 상품 목록
     * @throws BusinessException 포인트 레코드가 없는 경우 {@code POINT_NOT_FOUND}
     */
    public ShopItemsResponse getShopItems(String userId) {
        // 포인트 레코드 조회 (락 없이 읽기) — 잔액 표시용
        UserPoint userPoint = userPointRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("포인트 레코드 없음 (상점 조회): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // v3.3: purchased_ai_tokens는 UserAiQuota에서 조회한다
        // 쿼터 레코드가 없으면 0으로 표시 (초기화 전 상태 — 정상적으로는 발생하지 않음)
        int purchasedAiTokens = userAiQuotaRepository.findByUserId(userId)
                .map(UserAiQuota::getPurchasedAiTokens)
                .orElse(0);

        // 상품 목록 구성 (정적 카탈로그 — DB 조회 불필요)
        List<ShopItem> items = buildShopItems();

        log.debug("포인트 상점 아이템 조회: userId={}, balance={}, aiTokens={}",
                userId, userPoint.getBalance(), purchasedAiTokens);

        return new ShopItemsResponse(
                userPoint.getBalance(),
                purchasedAiTokens,
                items
        );
    }

    // ──────────────────────────────────────────────
    // 쓰기 메서드 (개별 @Transactional 오버라이드)
    // ──────────────────────────────────────────────

    /**
     * AI 이용권 팩을 구매한다 (AI_TOKEN_5 / AI_TOKEN_20).
     *
     * <p>packType에 따라 포인트를 차감하고 {@code purchased_ai_tokens}에 이용권을 추가한다.
     * 포인트 차감은 {@link PointService#deductPoint}를 통해 비관적 락·이력 기록·잔액 검증을 수행한다.</p>
     *
     * <h3>지원 packType</h3>
     * <ul>
     *   <li>{@code AI_TOKEN_5}  — 200P 차감, 5회 지급</li>
     *   <li>{@code AI_TOKEN_20} — 700P 차감, 20회 지급 (번들 할인)</li>
     * </ul>
     *
     * @param userId   구매자 사용자 ID
     * @param packType 구매할 팩 유형 ("AI_TOKEN_5" 또는 "AI_TOKEN_20")
     * @return 구매 결과 (차감 포인트, 추가 토큰, 잔여 잔액, 전체 토큰 잔여 횟수)
     * @throws BusinessException 잘못된 packType인 경우 {@code INVALID_INPUT}
     * @throws BusinessException 포인트 레코드가 없는 경우 {@code POINT_NOT_FOUND}
     * @throws com.monglepick.monglepickbackend.global.exception.InsufficientPointException 잔액 부족 시
     */
    @Transactional
    public PurchaseResponse purchaseAiTokens(String userId, String packType) {
        // 1. packType 유효성 검증 및 비용·지급량 결정
        int cost;
        int tokenAmount;

        if (PACK_AI_TOKEN_5.equals(packType)) {
            cost = COST_AI_TOKEN_5;
            tokenAmount = AMOUNT_AI_TOKEN_5;
        } else if (PACK_AI_TOKEN_20.equals(packType)) {
            cost = COST_AI_TOKEN_20;
            tokenAmount = AMOUNT_AI_TOKEN_20;
        } else {
            log.warn("잘못된 AI 이용권 팩 유형: userId={}, packType={}", userId, packType);
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 팩 유형입니다. 허용값: AI_TOKEN_5, AI_TOKEN_20");
        }

        log.info("AI 이용권 구매 시작: userId={}, packType={}, cost={}P, tokenAmount={}",
                userId, packType, cost, tokenAmount);

        return executeTokenPurchase(userId, cost, tokenAmount,
                "AI 이용권 구매 (" + packType + ")", packType);
    }

    /**
     * 일일 한도 우회 AI 이용권을 구매한다 (AI_DAILY_EXTEND).
     *
     * <p>일일 무료 AI 사용 횟수를 모두 소진한 사용자가 당일 추가 사용을 원할 때 구매한다.
     * 지급된 토큰은 {@code purchased_ai_tokens}에 추가되며,
     * QuotaService의 4단계 모델 3단계(PURCHASED)에서 소비된다 (일일 한도 우회 가능).</p>
     *
     * <p>비용: 100P 차감, 5회 지급.</p>
     *
     * @param userId 구매자 사용자 ID
     * @return 구매 결과 (차감 포인트, 추가 토큰, 잔여 잔액, 전체 토큰 잔여 횟수)
     * @throws BusinessException 포인트 레코드가 없는 경우 {@code POINT_NOT_FOUND}
     * @throws com.monglepick.monglepickbackend.global.exception.InsufficientPointException 잔액 부족 시
     */
    @Transactional
    public PurchaseResponse purchaseAiDailyExtend(String userId) {
        log.info("일일 한도 우회 AI 이용권 구매 시작: userId={}, cost={}P, tokenAmount={}",
                userId, COST_AI_DAILY_EXTEND, AMOUNT_AI_DAILY_EXTEND);

        return executeTokenPurchase(userId, COST_AI_DAILY_EXTEND, AMOUNT_AI_DAILY_EXTEND,
                "AI 이용권 구매 (일일 한도 우회)", PACK_AI_DAILY_EXTEND);
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ──────────────────────────────────────────────

    /**
     * 포인트 차감 + AI 이용권 토큰 추가를 원자적으로 수행하는 공통 메서드.
     *
     * <p>동일 트랜잭션 내에서 포인트 차감과 토큰 지급을 수행하여 정합성을 보장한다.
     * 포인트 차감 실패(잔액 부족 등) 시 토큰도 지급되지 않는다.</p>
     *
     * @param userId      사용자 ID
     * @param cost        차감할 포인트
     * @param tokenAmount 지급할 AI 이용권 횟수
     * @param description 포인트 이력 설명 (PointsHistory.description에 기록)
     * @param sessionId   포인트 이력 참조 ID (PointsHistory.referenceId에 기록)
     * @return 구매 결과 DTO
     */
    private PurchaseResponse executeTokenPurchase(String userId, int cost, int tokenAmount,
                                                   String description, String sessionId) {
        // 2. 포인트 차감 (비관적 락 + 잔액 검증 + 이력 기록은 PointService가 담당)
        //    잔액 부족 시 InsufficientPointException 발생 → 트랜잭션 롤백
        pointService.deductPoint(userId, cost, "SHOP_" + sessionId + "_" + userId, description);

        // 3. 포인트 차감 후 잔액 조회 (비관적 락으로 최신값 보장)
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("포인트 차감 후 레코드 재조회 실패 (데이터 정합성 오류): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });
        int remainingBalance = userPoint.getBalance();

        // 4. v3.3: purchased_ai_tokens 증가는 UserAiQuota에서 처리한다.
        //    비관적 락으로 user_ai_quota 행을 잠근 뒤 토큰을 추가한다.
        //    쿼터 레코드가 없으면 신규 생성 후 저장 (정합성 복구 — 정상적으로는 initializePoint에서 생성됨).
        UserAiQuota userAiQuota = userAiQuotaRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    log.warn("AI 쿼터 레코드 없음 — 신규 생성 후 저장 (데이터 정합성 복구): userId={}", userId);
                    UserAiQuota newQuota = UserAiQuota.builder()
                            .userId(userId)
                            .dailyAiUsed(0)
                            .monthlyCouponUsed(0)
                            .purchasedAiTokens(0)
                            .freeDailyGranted(0)
                            .build();
                    return userAiQuotaRepository.save(newQuota);
                });

        // 토큰 추가 (도메인 메서드 — count <= 0 방어 검증 포함)
        userAiQuota.addPurchasedTokens(tokenAmount);
        int totalTokens = userAiQuota.getPurchasedAiTokens();

        log.info("AI 이용권 구매 완료: userId={}, 차감={}P, 추가={}회, 잔액={}P, 총 이용권={}회",
                userId, cost, tokenAmount, remainingBalance, totalTokens);

        return new PurchaseResponse(cost, tokenAmount, remainingBalance, totalTokens);
    }

    /**
     * 정적 상품 카탈로그 목록을 반환한다.
     *
     * <p>현재 상품 정의는 코드에 고정되어 있다.
     * 추후 DB 기반 동적 관리가 필요하면 별도 테이블(point_shop_items)로 이관한다.</p>
     *
     * @return 상점 아이템 목록 (3종)
     */
    private List<ShopItem> buildShopItems() {
        return List.of(
                // AI 이용권 5회 팩 — 기본 팩
                new ShopItem(
                        PACK_AI_TOKEN_5,
                        "AI 이용권 5회",
                        COST_AI_TOKEN_5,
                        AMOUNT_AI_TOKEN_5,
                        "AI 추천을 5회 추가로 사용할 수 있습니다. (40P/회)"
                ),
                // AI 이용권 20회 팩 — 번들 할인
                new ShopItem(
                        PACK_AI_TOKEN_20,
                        "AI 이용권 20회",
                        COST_AI_TOKEN_20,
                        AMOUNT_AI_TOKEN_20,
                        "AI 추천을 20회 추가로 사용할 수 있습니다. 번들 할인 적용 (35P/회)"
                ),
                // 일일 한도 우회 5회 팩 — 당일 무료 횟수 소진 후 사용
                new ShopItem(
                        PACK_AI_DAILY_EXTEND,
                        "일일 한도 우회 5회",
                        COST_AI_DAILY_EXTEND,
                        AMOUNT_AI_DAILY_EXTEND,
                        "오늘 무료 AI 추천 횟수를 다 쓴 경우 5회를 추가로 사용할 수 있습니다. (20P/회)"
                )
        );
    }
}
