package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 포인트 아이템 리포지토리 — point_items 테이블 접근.
 *
 * <p>포인트로 교환 가능한 상품(아이템)의 조회 및 관리에 사용되는
 * 쿼리 메서드를 제공한다. 모든 조회는 활성 상태(is_active=true)의
 * 아이템만 대상으로 한다.</p>
 *
 * <h3>주요 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findByIsActiveTrueOrderByItemPriceAsc} — 전체 활성 아이템 목록 (가격 오름차순)</li>
 *   <li>{@link #findByPointItemIdAndIsActiveTrue} — 단건 활성 아이템 조회 (교환 시 사용)</li>
 *   <li>{@link #findByItemCategoryAndIsActiveTrueOrderByItemPriceAsc} — 카테고리별 활성 아이템 조회</li>
 * </ul>
 *
 * <h3>아이템 카테고리 예시</h3>
 * <ul>
 *   <li>{@code "general"} — 일반 상품</li>
 *   <li>{@code "coupon"} — 쿠폰</li>
 *   <li>{@code "avatar"} — 아바타/프로필 꾸미기</li>
 *   <li>{@code "ai"} — AI 추천 이용권</li>
 * </ul>
 *
 * @see PointItem
 * @see com.monglepick.monglepickbackend.domain.reward.service.PointItemService
 */
public interface PointItemRepository extends JpaRepository<PointItem, Long> {

    /**
     * 활성 상태인 전체 포인트 아이템을 가격 오름차순으로 조회한다.
     *
     * <p>클라이언트의 "포인트 상점" 화면에서 구매 가능한 아이템 목록을 표시할 때 사용된다.
     * 비활성화(is_active=false)된 아이템은 제외된다.</p>
     *
     * @return 활성 아이템 목록 (가격 오름차순, 없으면 빈 리스트)
     */
    List<PointItem> findByIsActiveTrueOrderByItemPriceAsc();

    /**
     * 특정 ID의 활성 아이템을 조회한다.
     *
     * <p>아이템 교환(구매) 시 대상 아이템이 존재하고 활성 상태인지 확인하는 용도로 사용된다.
     * 아이템이 존재하지 않거나 비활성화 상태이면 Optional.empty()를 반환한다.</p>
     *
     * @param pointItemId 포인트 아이템 ID
     * @return 활성 아이템 (없거나 비활성이면 Optional.empty())
     */
    Optional<PointItem> findByPointItemIdAndIsActiveTrue(Long pointItemId);

    /**
     * 특정 카테고리의 활성 아이템을 가격 오름차순으로 조회한다.
     *
     * <p>클라이언트에서 카테고리별 필터링을 적용할 때 사용된다.
     * 예: category="ai"로 조회하면 AI 추천 이용권 목록만 반환한다.</p>
     *
     * @param itemCategory 아이템 카테고리 (예: "general", "coupon", "avatar", "ai")
     * @return 해당 카테고리의 활성 아이템 목록 (가격 오름차순, 없으면 빈 리스트)
     */
    List<PointItem> findByItemCategoryAndIsActiveTrueOrderByItemPriceAsc(String itemCategory);
}
