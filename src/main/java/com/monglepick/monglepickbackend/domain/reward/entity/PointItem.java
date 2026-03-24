package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 상품 엔티티 — point_items 테이블 매핑.
 *
 * <p>포인트로 교환 가능한 상품(아이템) 정보를 저장한다.
 * 사용자가 보유 포인트를 사용하여 구매할 수 있는 아이템 목록을 관리한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code itemName} — 상품명 (필수)</li>
 *   <li>{@code itemDescription} — 상품 설명 (TEXT)</li>
 *   <li>{@code itemPrice} — 필요 포인트 (필수)</li>
 *   <li>{@code itemCategory} — 상품 카테고리 (기본값: "general")</li>
 *   <li>{@code isActive} — 판매 활성 여부 (기본값: true)</li>
 * </ul>
 */
@Entity
@Table(name = "point_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointItem extends BaseTimeEntity {

    /** 포인트 상품 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_item_id")
    private Long pointItemId;

    /** 상품명 (필수, 최대 200자) */
    @Column(name = "item_name", length = 200, nullable = false)
    private String itemName;

    /** 상품 설명 (TEXT 타입, 선택) */
    @Column(name = "item_description", columnDefinition = "TEXT")
    private String itemDescription;

    /**
     * 필요 포인트 (NOT NULL).
     * 이 상품을 교환하는 데 필요한 포인트.
     */
    @Column(name = "item_price", nullable = false)
    private Integer itemPrice;

    /**
     * 상품 카테고리 (최대 50자).
     * 기본값: "general".
     * 예: "general"(일반), "coupon"(쿠폰), "avatar"(아바타)
     */
    @Column(name = "item_category", length = 50)
    @Builder.Default
    private String itemCategory = "general";

    /**
     * 판매 활성 여부.
     * 기본값: true.
     * false로 설정하면 사용자에게 노출되지 않는다.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
