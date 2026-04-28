package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType.Dispense;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.PurchaseResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItem;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItemsResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
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
 * 구매된 이용권은 {@link QuotaService}의 4단계 모델 3단계(PURCHASED)에서 소비된다.</p>
 *
 * <h3>지원 상품 (packType) — 기본값 (DB 미수정 시), 단가 10P/회 = 100원/회 통일</h3>
 * <table border="1">
 *   <tr><th>packType</th><th>차감 포인트 (기본)</th><th>지급 토큰</th><th>단가</th><th>유효기간</th></tr>
 *   <tr><td>AI_TOKEN_1</td><td>10P</td><td>1회</td><td>10P/회</td><td>30일</td></tr>
 *   <tr><td>AI_TOKEN_5</td><td>50P</td><td>5회</td><td>10P/회</td><td>30일</td></tr>
 *   <tr><td>AI_TOKEN_20</td><td>200P</td><td>20회</td><td>10P/회</td><td>30일</td></tr>
 *   <tr><td>AI_TOKEN_50</td><td>500P</td><td>50회</td><td>10P/회</td><td>60일</td></tr>
 * </table>
 *
 * <p>v3.2 변경: AI_DAILY_EXTEND 폐지. PURCHASED 토큰 자체가 일일 무료 한도를 우회하므로
 * 별도 "일일 한도 우회" 상품이 불필요하다.</p>
 *
 * <p>v4 (2026-04-28): 하드코딩 카탈로그 → DB 기반 전환.
 * admin 의 비활성화/가격 수정이 {@code point_items} 테이블을 통해 즉시 반영된다.
 * {@link PointItemRepository#findFirstByItemTypeAndIsActiveTrue} 로 활성 상품 단건 조회,
 * {@link PointItemRepository#findByItemTypeInAndIsActiveTrueOrderByItemPriceAsc} 로 목록 조회.
 * 비활성화된 상품은 상점 목록에서 제외되고 구매 시도 시 {@link ErrorCode#ITEM_NOT_FOUND} 를 반환한다.</p>
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>packType 유효성 검증 (PointItemType.fromCodeOrUnknown + dispense=AI_TOKEN 검사)</li>
 *   <li>DB 에서 해당 packType 의 활성 상품 조회 → 비활성 시 ITEM_NOT_FOUND</li>
 *   <li>{@link PointService#deductPoint} 호출 — 비관적 락 + 잔액 검증 + 이력 기록</li>
 *   <li>{@link UserAiQuota#addPurchasedTokens(int)} 호출 — purchased_ai_tokens 증가 (v3.3)</li>
 *   <li>구매 결과 응답 반환</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 목록 조회 기본값</li>
 *   <li>{@link #purchaseAiTokens}: 개별 {@code @Transactional} 오버라이드</li>
 * </ul>
 *
 * <h3>동시성</h3>
 * <p>포인트 차감은 {@link PointService#deductPoint}가 비관적 락(PESSIMISTIC_WRITE)으로 처리하므로
 * 이 서비스에서는 별도 락 처리가 불필요하다. 단, 토큰 증가({@link UserAiQuota#addPurchasedTokens})는
 * 동일 트랜잭션 안에서 수행하여 포인트 차감·토큰 지급의 원자성을 보장한다.</p>
 *
 * @see PointService
 * @see com.monglepick.monglepickbackend.domain.reward.controller.PointShopController
 * @see com.monglepick.monglepickbackend.domain.reward.entity.UserPoint
 * @see PointItemRepository
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointShopService {

    // ──────────────────────────────────────────────
    // 지원 AI 이용권 타입 목록 — DB 조회 시 IN 조건으로 사용.
    // PointItemType.Dispense.AI_TOKEN 에 해당하는 4종을 명시한다.
    // 추후 새 AI 이용권 팩이 추가될 경우 이 목록을 확장한다.
    // ──────────────────────────────────────────────

    /**
     * AI 이용권에 해당하는 PointItemType 목록.
     *
     * <p>상점 목록 조회({@link #getShopItems})에서 DB IN 조건에 사용되며,
     * 이 목록에 포함되지 않은 타입은 상점에 노출되지 않는다.
     * v4: 하드코딩 상수(PACK_AI_TOKEN_x/COST_x/AMOUNT_x) 대신 이 단일 목록이 진실 원본.</p>
     */
    private static final List<PointItemType> AI_TOKEN_TYPES = List.of(
            PointItemType.AI_TOKEN_1,
            PointItemType.AI_TOKEN_5,
            PointItemType.AI_TOKEN_20,
            PointItemType.AI_TOKEN_50
    );

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

    /**
     * 포인트 아이템 리포지토리 — point_items 테이블에서 활성 AI 이용권 상품 조회 (v4 신규).
     *
     * <p>admin 이 비활성화하거나 가격/이름을 수정한 내용이 이 리포지토리를 통해 즉시 반영된다.
     * 기존 하드코딩 상수(PACK_x/COST_x/AMOUNT_x) 를 대체한다.</p>
     */
    private final PointItemRepository pointItemRepository;

    // ──────────────────────────────────────────────
    // 읽기 메서드 (클래스 레벨 readOnly 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 포인트 상점 아이템 목록과 현재 사용자 잔액을 반환한다.
     *
     * <p>클라이언트가 상점 화면 진입 시 호출한다.
     * 현재 잔액·AI 이용권 보유량을 함께 반환하여 구매 가능 여부를 즉시 표시할 수 있게 한다.</p>
     *
     * <p>v4 (2026-04-28): DB({@code point_items}) 에서 활성 AI 이용권 상품을 조회한다.
     * admin 이 비활성화한 상품은 목록에서 제외된다.
     * 모든 AI 이용권이 비활성화된 경우 빈 리스트가 반환되며, 클라이언트는 "판매 중인 상품이 없습니다" 를 표시해야 한다.</p>
     *
     * @param userId 사용자 ID
     * @return 현재 잔액, AI 이용권 잔여 횟수, 전체 상품 목록 (admin 비활성화 상품 제외)
     * @throws BusinessException 포인트 레코드가 없는 경우 {@code POINT_NOT_FOUND}
     */
    public ShopItemsResponse getShopItems(String userId) {
        // 1. 포인트 레코드 조회 (락 없이 읽기) — 잔액 표시용
        UserPoint userPoint = userPointRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("포인트 레코드 없음 (상점 조회): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 2. v3.3: purchased_ai_tokens는 UserAiQuota에서 조회한다.
        //    쿼터 레코드가 없으면 0으로 표시 (초기화 전 상태 — 정상적으로는 발생하지 않음)
        int purchasedAiTokens = userAiQuotaRepository.findByUserId(userId)
                .map(UserAiQuota::getPurchasedAiTokens)
                .orElse(0);

        // 3. v4: point_items DB에서 활성 AI 이용권 상품을 가격 오름차순으로 조회한다.
        //    admin이 비활성화한 상품은 자동으로 제외된다.
        //    결과가 비어있으면 빈 리스트를 그대로 반환 (admin이 모두 비활성화한 상태).
        List<PointItem> activeItems = pointItemRepository
                .findByItemTypeInAndIsActiveTrueOrderByItemPriceAsc(AI_TOKEN_TYPES);

        // 4. PointItem 엔티티 → ShopItem DTO 변환.
        //    itemId는 packType 문자열(AI_TOKEN_5 등)을 그대로 사용하여 클라이언트 호환성 유지.
        //    resolveAmount()로 NULL 방어 — amount 컬럼이 NULL이면 PointItemType 기본값 사용.
        List<ShopItem> items = activeItems.stream()
                .map(item -> new ShopItem(
                        item.getItemType().name(),     // itemId: "AI_TOKEN_1", "AI_TOKEN_5" 등 — 클라이언트 packType과 동일
                        item.getItemName(),            // admin이 수정한 이름이 즉시 반영됨
                        item.getItemPrice(),           // admin이 수정한 가격이 즉시 반영됨
                        item.resolveAmount(),          // amount 컬럼 NULL-safe 조회
                        item.getItemDescription()      // admin이 수정한 설명이 즉시 반영됨
                ))
                .toList();

        log.debug("포인트 상점 아이템 조회: userId={}, balance={}, aiTokens={}, 활성상품={}건",
                userId, userPoint.getBalance(), purchasedAiTokens, items.size());

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
     * AI 이용권 팩을 구매한다 (AI_TOKEN_1 / AI_TOKEN_5 / AI_TOKEN_20 / AI_TOKEN_50).
     *
     * <p>packType에 따라 DB 에서 활성 상품 정보를 조회하여 포인트를 차감하고
     * {@code purchased_ai_tokens}에 이용권을 추가한다.
     * 포인트 차감은 {@link PointService#deductPoint}를 통해 비관적 락·이력 기록·잔액 검증을 수행한다.</p>
     *
     * <p>구매된 이용권은 QuotaService 3-소스 모델 3단계(PURCHASED)에서 소비되며,
     * 등급 일일 무료 한도를 우회하여 사용할 수 있다.</p>
     *
     * <p>v4 (2026-04-28): 하드코딩 if-else 제거 → DB 조회 기반으로 전환.
     * admin 이 비활성화하면 구매 시 {@link ErrorCode#ITEM_NOT_FOUND} 가 반환된다.
     * admin 이 가격을 변경하면 변경된 값이 즉시 차감 포인트로 사용된다.</p>
     *
     * <h3>지원 packType (기본값 — DB 미수정 시)</h3>
     * <ul>
     *   <li>{@code AI_TOKEN_1}  — 10P 차감, 1회 지급</li>
     *   <li>{@code AI_TOKEN_5}  — 50P 차감, 5회 지급</li>
     *   <li>{@code AI_TOKEN_20} — 200P 차감, 20회 지급</li>
     *   <li>{@code AI_TOKEN_50} — 500P 차감, 50회 지급</li>
     * </ul>
     *
     * @param userId   구매자 사용자 ID
     * @param packType 구매할 팩 유형 ("AI_TOKEN_1", "AI_TOKEN_5", "AI_TOKEN_20", "AI_TOKEN_50")
     * @return 구매 결과 (차감 포인트, 추가 토큰, 잔여 잔액, 전체 토큰 잔여 횟수)
     * @throws BusinessException 잘못된 packType인 경우 {@code INVALID_INPUT}
     * @throws BusinessException admin이 해당 상품을 비활성화했거나 시드 누락인 경우 {@code ITEM_NOT_FOUND}
     * @throws BusinessException 포인트 레코드가 없는 경우 {@code POINT_NOT_FOUND}
     * @throws com.monglepick.monglepickbackend.global.exception.InsufficientPointException 잔액 부족 시
     */
    @Transactional
    public PurchaseResponse purchaseAiTokens(String userId, String packType) {

        // 1. packType 문자열 → PointItemType 변환 (null/공백/미지원 시 UNKNOWN 반환)
        PointItemType resolvedType = PointItemType.fromCodeOrUnknown(packType);

        // 2. AI 이용권 타입인지 검사.
        //    UNKNOWN 이거나 Dispense 가 AI_TOKEN 이 아니면 지원하지 않는 팩 유형 오류.
        if (resolvedType == PointItemType.UNKNOWN || resolvedType.getDispense() != Dispense.AI_TOKEN) {
            log.warn("잘못된 AI 이용권 팩 유형: userId={}, packType={}", userId, packType);
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 팩 유형입니다. 허용값: AI_TOKEN_1, AI_TOKEN_5, AI_TOKEN_20, AI_TOKEN_50");
        }

        // 3. DB에서 해당 타입의 활성 상품 조회.
        //    admin 이 비활성화(is_active=false)하거나 시드가 누락된 경우 ITEM_NOT_FOUND.
        PointItem item = pointItemRepository.findFirstByItemTypeAndIsActiveTrue(resolvedType)
                .orElseThrow(() -> {
                    log.warn("AI 이용권 구매 불가 — 비활성화되었거나 시드 누락: userId={}, packType={}",
                            userId, packType);
                    return new BusinessException(ErrorCode.ITEM_NOT_FOUND,
                            "현재 판매되지 않는 상품입니다");
                });

        // 4. DB에서 읽어온 가격·수량 추출.
        //    admin 이 변경한 가격이 즉시 반영된다 (하드코딩 상수 제거로 인한 핵심 개선).
        //    resolveAmount(): amount 컬럼이 NULL 이면 PointItemType 기본값 사용.
        int cost = item.getItemPrice();
        int tokenAmount = item.resolveAmount();

        // 5. 가격/수량 방어 검사 — 0 이하면 시드 데이터 오류로 차단한다.
        //    정상적으로는 PointItemInitializer 가 올바른 값으로 시드를 INSERT 한다.
        if (cost <= 0 || tokenAmount <= 0) {
            log.error("AI 이용권 시드 데이터 오류 — 가격/수량이 0 이하: itemId={}, cost={}, tokenAmount={}",
                    item.getPointItemId(), cost, tokenAmount);
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND,
                    "상품 데이터가 올바르지 않습니다. 관리자에게 문의하세요");
        }

        log.info("AI 이용권 구매 시작: userId={}, packType={}, cost={}P, tokenAmount={} (DB 기반)",
                userId, packType, cost, tokenAmount);

        // 6. 공통 구매 처리 — 포인트 차감 + 토큰 지급 원자 수행
        return executeTokenPurchase(userId, cost, tokenAmount,
                "AI 이용권 구매 (" + packType + ")", packType);
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
     * @param cost        차감할 포인트 (DB item_price 값)
     * @param tokenAmount 지급할 AI 이용권 횟수 (DB amount 또는 PointItemType 기본값)
     * @param description 포인트 이력 설명 (PointsHistory.description에 기록)
     * @param sessionId   포인트 이력 참조 ID (PointsHistory.referenceId에 기록)
     * @return 구매 결과 DTO
     */
    private PurchaseResponse executeTokenPurchase(String userId, int cost, int tokenAmount,
                                                   String description, String sessionId) {
        // 포인트 차감 (비관적 락 + 잔액 검증 + 이력 기록은 PointService가 담당)
        // 잔액 부족 시 InsufficientPointException 발생 → 트랜잭션 롤백
        pointService.deductPoint(userId, cost, "SHOP_" + sessionId + "_" + userId, description);

        // 포인트 차감 후 잔액 조회 (비관적 락으로 최신값 보장)
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("포인트 차감 후 레코드 재조회 실패 (데이터 정합성 오류): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });
        int remainingBalance = userPoint.getBalance();

        // v3.3: purchased_ai_tokens 증가는 UserAiQuota에서 처리한다.
        //       비관적 락으로 user_ai_quota 행을 잠근 뒤 토큰을 추가한다.
        //       쿼터 레코드가 없으면 신규 생성 후 저장 (정합성 복구 — 정상적으로는 initializePoint에서 생성됨).
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
}
