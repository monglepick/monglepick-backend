package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemCategory;
import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.domain.reward.constants.UserItemStatus;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.ExchangeResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.PointItemResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserItemRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 포인트 아이템 서비스 — 아이템 조회 및 포인트 교환 비즈니스 로직.
 *
 * <p>사용자가 보유 포인트로 아이템을 교환(구매)할 수 있는 "포인트 상점" 기능을 제공한다.
 * 아이템 목록 조회는 읽기 전용이며, 아이템 교환은 PointService를 통해
 * 비관적 락 기반 포인트 차감을 수행한 후 <b>카테고리별 실제 지급 로직</b>을 수행한다.</p>
 *
 * <h3>v2 변경 이력 (2026-04-14 — C 방향 지급 로직 구현)</h3>
 * <p>기존에는 교환 시 포인트만 차감하고 실제 지급이 누락되어 있었다
 * (쿠폰·아바타 모두 포인트만 날아가는 상태). v2에서 {@link PointItemType}을 키로
 * 다음 분기를 수행하도록 확장했다:</p>
 * <ul>
 *   <li>{@link PointItemType.Dispense#AI_TOKEN} — {@link UserAiQuota#addPurchasedTokens}로 토큰 적립.
 *       {@link PointShopService#purchaseAiTokens}와 동일한 경로로 연결되어 QuotaService의 PURCHASED
 *       소스 소비가 정상 동작한다.</li>
 *   <li>{@link PointItemType.Dispense#INVENTORY} — {@link UserItem} 레코드 INSERT.
 *       expires_at은 {@link PointItem#resolveDurationDays()} 기반으로 계산.</li>
 *   <li>{@link PointItemType.Dispense#UNSUPPORTED} — 레거시/미정의 타입. 교환 차단({@link ErrorCode#ITEM_NOT_FOUND}).</li>
 * </ul>
 *
 * <h3>아이템 교환 흐름 (v2)</h3>
 * <ol>
 *   <li>대상 아이템 조회 (활성 상태 + itemType resolve)</li>
 *   <li>지급 방식 사전 검증 (UNSUPPORTED 조기 실패)</li>
 *   <li>{@link PointService#deductPoint} — 비관적 락으로 포인트 차감 + 이력 기록</li>
 *   <li>itemType에 따라 분기:
 *     <ul>
 *       <li>AI_TOKEN — UserAiQuota.addPurchasedTokens(amount)</li>
 *       <li>INVENTORY — UserItem 엔티티 저장</li>
 *     </ul>
 *   </li>
 *   <li>{@link ExchangeResponse} 반환 (빌더 팩토리로 카테고리별 응답 분기)</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 기본 읽기 전용</li>
 *   <li>{@link #exchangeItem}: {@code @Transactional} — 쓰기 (포인트 차감 + 지급 원자성 보장)</li>
 * </ul>
 *
 * <h3>동시성</h3>
 * <p>포인트 차감은 PointService가 비관적 락으로 처리하고, UserAiQuota도 findByUserIdWithLock으로
 * 잠가서 갱신한다. UserItem INSERT는 PK AUTO_INCREMENT이므로 락 불필요.
 * 전 과정이 동일 트랜잭션에 묶여 있어 중간 실패 시 롤백 보장.</p>
 *
 * @see PointItemRepository
 * @see UserItemRepository
 * @see UserAiQuotaRepository
 * @see PointItemType
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointItemService {

    /** 포인트 아이템 리포지토리 (아이템 조회) */
    private final PointItemRepository itemRepository;

    /** 포인트 서비스 (아이템 교환 시 포인트 차감) */
    private final PointService pointService;

    /** 사용자 AI 쿼터 리포지토리 — AI 이용권 교환 시 purchased_ai_tokens 갱신 (v2 신규) */
    private final UserAiQuotaRepository userAiQuotaRepository;

    /** 보유 아이템 리포지토리 — 인벤토리 아이템 교환 시 INSERT (v2 신규) */
    private final UserItemRepository userItemRepository;

    /** 차감 후 잔액 재조회용 (비관적 락 포함) — v2 신규 */
    private final UserPointRepository userPointRepository;

    // ──────────────────────────────────────────────
    // 아이템 목록 조회 (읽기 전용)
    // ──────────────────────────────────────────────

    /**
     * 활성 상태인 전체 포인트 아이템 목록을 조회한다.
     *
     * <p>클라이언트의 "포인트 상점" 화면에서 구매 가능한 아이템 전체 목록을 표시할 때 사용된다.
     * 비활성화(is_active=false)된 아이템은 제외되며, 가격 오름차순으로 정렬된다.</p>
     *
     * @return 활성 아이템 목록 (가격 오름차순, 없으면 빈 리스트)
     */
    public List<PointItemResponse> getActiveItems() {
        log.debug("전체 활성 아이템 목록 조회");

        List<PointItemResponse> items = itemRepository.findByIsActiveTrueOrderByItemPriceAsc()
                .stream()
                .map(this::toResponse)
                .toList();

        log.debug("활성 아이템 조회 완료: {}건", items.size());
        return items;
    }

    /**
     * 특정 카테고리의 활성 아이템 목록을 조회한다.
     *
     * <p>v2: 정규 카테고리는 소문자 5종 ({@link PointItemCategory}).</p>
     *
     * @param category 아이템 카테고리 (예: "coupon", "avatar", "badge", "apply", "hint")
     * @return 해당 카테고리의 활성 아이템 목록
     */
    public List<PointItemResponse> getItemsByCategory(String category) {
        log.debug("카테고리별 아이템 목록 조회: category={}", category);

        List<PointItemResponse> items = itemRepository
                .findByItemCategoryAndIsActiveTrueOrderByItemPriceAsc(category)
                .stream()
                .map(this::toResponse)
                .toList();

        log.debug("카테고리별 아이템 조회 완료: category={}, {}건", category, items.size());
        return items;
    }

    // ──────────────────────────────────────────────
    // 아이템 교환 (쓰기 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 포인트로 아이템을 교환(구매)한다.
     *
     * <p>v2: itemType을 읽어 카테고리별 실제 지급 로직으로 분기한다. 기존 포인트-only 차감에서
     * UserAiQuota 적립 또는 UserItem INSERT까지 원자적으로 수행하도록 확장되었다.</p>
     *
     * <h4>예외 상황</h4>
     * <ul>
     *   <li>아이템 미존재/비활성 → {@link BusinessException}(ITEM_NOT_FOUND)</li>
     *   <li>지원 불가 itemType (UNSUPPORTED) → {@link BusinessException}(ITEM_NOT_FOUND)
     *       — 레거시 데이터에 대한 안전 장치</li>
     *   <li>잔액 부족 → InsufficientPointException (PointService)</li>
     *   <li>포인트 레코드 없음 → {@link BusinessException}(POINT_NOT_FOUND)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param itemId 교환 대상 포인트 아이템 ID
     * @return 교환 결과 (AI 이용권 또는 인벤토리 변종)
     * @throws BusinessException 위 예외 상황들
     */
    @Transactional
    public ExchangeResponse exchangeItem(String userId, Long itemId) {
        log.info("아이템 교환 시작 (v2): userId={}, itemId={}", userId, itemId);

        // 1) 아이템 조회 (활성 상태 확인 포함)
        PointItem item = itemRepository.findByPointItemIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> {
                    log.warn("아이템 교환 실패 (아이템 미존재/비활성): userId={}, itemId={}", userId, itemId);
                    return new BusinessException(ErrorCode.ITEM_NOT_FOUND);
                });

        // 2) 지급 방식 사전 검증 — UNSUPPORTED는 조기 차단
        PointItemType itemType = item.getItemType() != null
                ? item.getItemType()
                : PointItemType.UNKNOWN;
        if (itemType.getDispense() == PointItemType.Dispense.UNSUPPORTED) {
            log.warn("지원하지 않는 itemType 교환 시도 (레거시 데이터 가능성): userId={}, itemId={}, itemName={}",
                    userId, itemId, item.getItemName());
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND,
                    "현재 판매되지 않는 아이템입니다. 관리자에게 문의하세요.");
        }

        // 3) 포인트 차감 — 비관적 락 + 잔액 검증 + 이력 기록
        pointService.deductPoint(
                userId,
                item.getItemPrice(),
                "item-" + itemId,
                "아이템 교환: " + item.getItemName()
        );

        // 4) 차감 후 잔액 재조회 (비관적 락으로 최신값 보장)
        int balanceAfter = userPointRepository.findByUserIdForUpdate(userId)
                .map(UserPoint::getBalance)
                .orElseThrow(() -> {
                    log.error("포인트 차감 후 레코드 재조회 실패 (정합성 오류): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 5) 카테고리별 지급 분기
        ExchangeResponse response = switch (itemType.getDispense()) {
            case AI_TOKEN -> dispenseAiToken(userId, item, itemType, balanceAfter);
            case INVENTORY -> dispenseInventoryItem(userId, item, itemType, balanceAfter);
            case UNSUPPORTED -> throw new IllegalStateException(
                    "UNSUPPORTED는 위에서 걸러졌어야 함: itemType=" + itemType);
        };

        log.info("아이템 교환 완료 (v2): userId={}, itemId={}, itemName={}, category={}, itemType={}, balance={}P",
                userId, itemId, item.getItemName(), item.getItemCategory(),
                itemType.name(), balanceAfter);

        return response;
    }

    // ──────────────────────────────────────────────
    // 카테고리별 지급 분기 (v2 신규)
    // ──────────────────────────────────────────────

    /**
     * AI 이용권 지급 — {@code UserAiQuota.purchased_ai_tokens}에 수량 적립.
     *
     * <p>{@link PointShopService#purchaseAiTokens}와 동일한 경로. 쿼터가 없으면 정합성 복구용
     * 신규 생성 후 저장. 도메인 메서드 {@link UserAiQuota#addPurchasedTokens}가 amount &le; 0 방어.</p>
     *
     * @param userId       사용자 ID
     * @param item         교환된 아이템 마스터
     * @param itemType     지급 타입 (AI_TOKEN_1/5/20/50 중 하나)
     * @param balanceAfter 차감 후 잔액 (응답 필드)
     * @return AI 이용권 응답 변종
     */
    private ExchangeResponse dispenseAiToken(String userId, PointItem item, PointItemType itemType,
                                              int balanceAfter) {
        int addedAmount = item.resolveAmount();
        if (addedAmount <= 0) {
            log.error("AI 이용권 지급량이 0 이하 — 시드 오류 가능성: itemId={}, itemType={}",
                    item.getPointItemId(), itemType);
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND,
                    "AI 이용권 지급 수량이 잘못 설정된 아이템입니다.");
        }

        UserAiQuota quota = userAiQuotaRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    log.warn("AI 쿼터 레코드 없음 — 신규 생성 (정합성 복구): userId={}", userId);
                    return userAiQuotaRepository.save(UserAiQuota.builder()
                            .userId(userId)
                            .dailyAiUsed(0)
                            .monthlyCouponUsed(0)
                            .purchasedAiTokens(0)
                            .freeDailyGranted(0)
                            .build());
                });
        quota.addPurchasedTokens(addedAmount);
        int totalTokens = quota.getPurchasedAiTokens();

        log.info("AI 이용권 적립: userId={}, itemId={}, +{}회 → 총 {}회",
                userId, item.getPointItemId(), addedAmount, totalTokens);

        return ExchangeResponse.aiToken(
                balanceAfter,
                item.getItemName(),
                itemType.name(),
                item.getItemCategory(),
                addedAmount,
                totalTokens
        );
    }

    /**
     * 인벤토리 아이템 지급 — user_items 테이블에 보유 레코드 INSERT.
     *
     * <p>expires_at은 PointItem.resolveDurationDays()로 계산한다 (NULL이면 무기한).
     * remainingQuantity는 기본 1 (amount 필드는 AI 토큰 전용이므로 무시).</p>
     *
     * @param userId       사용자 ID
     * @param item         교환된 아이템 마스터
     * @param itemType     지급 타입 (AVATAR_* / BADGE_* / APPLY_* / QUIZ_HINT)
     * @param balanceAfter 차감 후 잔액
     * @return 인벤토리 응답 변종 (userItemId 포함)
     */
    private ExchangeResponse dispenseInventoryItem(String userId, PointItem item, PointItemType itemType,
                                                    int balanceAfter) {
        LocalDateTime now = LocalDateTime.now();
        Integer durationDays = item.resolveDurationDays();
        LocalDateTime expiresAt = durationDays != null ? now.plusDays(durationDays) : null;

        UserItem entity = UserItem.builder()
                .userId(userId)
                .pointItem(item)
                .acquiredAt(now)
                .expiresAt(expiresAt)
                .status(UserItemStatus.ACTIVE)
                .source("EXCHANGE")
                .remainingQuantity(1)
                .build();

        UserItem saved = userItemRepository.save(entity);

        log.info("인벤토리 아이템 지급: userId={}, userItemId={}, itemId={}, itemType={}, expiresAt={}",
                userId, saved.getUserItemId(), item.getPointItemId(), itemType, expiresAt);

        return ExchangeResponse.inventory(
                balanceAfter,
                item.getItemName(),
                saved.getUserItemId(),
                itemType.name(),
                item.getItemCategory()
        );
    }

    // ──────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────

    /**
     * PointItem 엔티티를 PointItemResponse DTO로 변환한다.
     *
     * <p>v2에서 추가된 itemType/amount/durationDays/imageUrl 필드는 상점 목록 조회 DTO에 노출하지 않는다.
     * 클라이언트는 카테고리 레벨로만 구분하며, 세부 지급 분기는 Backend가 담당.</p>
     *
     * @param item 포인트 아이템 엔티티
     * @return DTO 변환 결과
     */
    private PointItemResponse toResponse(PointItem item) {
        return new PointItemResponse(
                item.getPointItemId(),
                item.getItemName(),
                item.getItemDescription(),
                item.getItemPrice(),
                item.getItemCategory()
        );
    }
}
