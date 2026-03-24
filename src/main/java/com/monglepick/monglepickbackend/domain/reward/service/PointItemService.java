package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.ExchangeResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.PointItemResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포인트 아이템 서비스 — 아이템 조회 및 포인트 교환 비즈니스 로직.
 *
 * <p>사용자가 보유 포인트로 아이템을 교환(구매)할 수 있는 "포인트 상점" 기능을 제공한다.
 * 아이템 목록 조회는 읽기 전용이며, 아이템 교환은 PointService를 통해
 * 비관적 락 기반 포인트 차감을 수행한다.</p>
 *
 * <h3>아이템 교환 흐름</h3>
 * <ol>
 *   <li>대상 아이템 조회 (활성 상태 확인)</li>
 *   <li>PointService.deductPoint() 호출 — 비관적 락으로 잔액 검증 + 차감</li>
 *   <li>교환 결과 반환 (잔액, 아이템명)</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 기본 읽기 전용</li>
 *   <li>{@link #exchangeItem}: 개별 {@code @Transactional} — 쓰기 트랜잭션 (포인트 차감)</li>
 * </ul>
 *
 * @see PointItemRepository
 * @see PointService#deductPoint(String, int, String, String)
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
     * <p>클라이언트에서 카테고리별 필터링을 적용할 때 사용된다.
     * 예: category="ai"로 조회하면 AI 추천 이용권 관련 아이템만 반환한다.</p>
     *
     * @param category 아이템 카테고리 (예: "general", "coupon", "avatar", "ai")
     * @return 해당 카테고리의 활성 아이템 목록 (가격 오름차순, 없으면 빈 리스트)
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
     * <p>처리 순서:</p>
     * <ol>
     *   <li>대상 아이템 조회 + 활성 상태 확인</li>
     *   <li>PointService.deductPoint() 호출 — 비관적 락으로 잔액 검증 + 차감</li>
     *   <li>교환 결과 반환</li>
     * </ol>
     *
     * <h4>예외 상황</h4>
     * <ul>
     *   <li>아이템 미존재 또는 비활성 → {@link BusinessException}(ITEM_NOT_FOUND, 404)</li>
     *   <li>포인트 잔액 부족 → {@link com.monglepick.monglepickbackend.global.exception.InsufficientPointException}
     *       (PointService에서 발생)</li>
     *   <li>포인트 레코드 없음 → {@link BusinessException}(POINT_NOT_FOUND, 404)
     *       (PointService에서 발생)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param itemId 교환 대상 포인트 아이템 ID
     * @return 교환 결과 (성공 여부, 잔액, 아이템명)
     * @throws BusinessException 아이템 미존재/비활성 또는 포인트 관련 예외
     */
    @Transactional
    public ExchangeResponse exchangeItem(String userId, Long itemId) {
        log.info("아이템 교환 시작: userId={}, itemId={}", userId, itemId);

        // 1. 대상 아이템 조회 (활성 상태 확인 포함)
        PointItem item = itemRepository.findByPointItemIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> {
                    log.warn("아이템 교환 실패 (아이템 미존재/비활성): userId={}, itemId={}", userId, itemId);
                    return new BusinessException(ErrorCode.ITEM_NOT_FOUND);
                });

        // 2. 포인트 차감 (PointService가 비관적 락 + 잔액 검증 처리)
        DeductResponse deductResult = pointService.deductPoint(
                userId,
                item.getItemPrice(),
                "item-" + itemId,
                "아이템 교환: " + item.getItemName()
        );

        log.info("아이템 교환 완료: userId={}, itemId={}, itemName={}, price={}P, balanceAfter={}",
                userId, itemId, item.getItemName(), item.getItemPrice(), deductResult.balanceAfter());

        // 3. 교환 결과 반환
        return new ExchangeResponse(true, deductResult.balanceAfter(), item.getItemName());
    }

    // ──────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────

    /**
     * PointItem 엔티티를 PointItemResponse DTO로 변환한다.
     *
     * <p>엔티티의 내부 필드명을 클라이언트 친화적인 DTO 필드명으로 매핑한다.</p>
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
