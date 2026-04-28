package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
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

    /**
     * 특정 itemName 의 활성 아이템 1건 조회 (2026-04-28 신규).
     *
     * <p>{@link com.monglepick.monglepickbackend.domain.reward.service.GradeTitleService} 가
     * 등급 자동 칭호 지급 시 칭호 PointItem 을 이름으로 매핑하기 위해 사용한다.
     * itemName 은 PointItemInitializer 가 멱등 INSERT 하는 시드의 고유 키 역할.</p>
     *
     * @param itemName 정확한 상품명 (예: "칭호 - 알갱이")
     * @return 활성 상태의 단일 매칭 (없거나 중복이면 empty/첫 건)
     */
    Optional<PointItem> findFirstByItemNameAndIsActiveTrue(String itemName);

    /**
     * 특정 {@link PointItemType} 의 활성 상품 단건 조회 (2026-04-28 신규 — DB 기반 상점 전환).
     *
     * <p>{@link com.monglepick.monglepickbackend.domain.reward.service.PointShopService#purchaseAiTokens}
     * 에서 packType 문자열을 PointItemType 으로 변환한 뒤 이 메서드로 DB 상의 상품 정보를 가져온다.
     * admin 이 비활성화(is_active=false)한 상품은 Optional.empty() 로 반환되어 구매가 차단된다.</p>
     *
     * <p>동일 itemType 의 활성 상품이 여러 건인 경우(비정상 상태) 첫 번째 건만 반환한다.
     * 정상적으로는 PointItemInitializer 멱등 INSERT 정책에 의해 각 AI_TOKEN_* 타입은 1건만 존재한다.</p>
     *
     * @param itemType 조회할 아이템 타입 (예: {@link PointItemType#AI_TOKEN_5})
     * @return 활성 상태의 상품 (없거나 비활성이면 Optional.empty())
     */
    Optional<PointItem> findFirstByItemTypeAndIsActiveTrue(PointItemType itemType);

    /**
     * 지정한 {@link PointItemType} 집합에 속하고 활성 상태인 상품을 가격 오름차순으로 전체 조회
     * (2026-04-28 신규 — DB 기반 상점 전환).
     *
     * <p>{@link com.monglepick.monglepickbackend.domain.reward.service.PointShopService#getShopItems}
     * 에서 AI 이용권 4종(AI_TOKEN_1/5/20/50)만을 가격 오름차순으로 가져와 상점 목록을 구성한다.
     * admin 이 특정 상품을 비활성화하면 해당 상품은 목록에서 제외되어 클라이언트에 노출되지 않는다.
     * 모든 AI 이용권이 비활성화된 경우 빈 리스트가 반환된다.</p>
     *
     * @param itemTypes 조회할 타입 집합 (예: {@code List.of(AI_TOKEN_1, AI_TOKEN_5, AI_TOKEN_20, AI_TOKEN_50)})
     * @return 조건을 만족하는 활성 상품 목록 (가격 오름차순, 없으면 빈 리스트)
     */
    List<PointItem> findByItemTypeInAndIsActiveTrueOrderByItemPriceAsc(Collection<PointItemType> itemTypes);
}
