package com.monglepick.monglepickbackend.domain.payment.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
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
 * 포인트팩 가격 마스터 엔티티 — point_pack_prices 테이블 매핑.
 *
 * <p>포인트팩 결제 시 클라이언트가 보낸 금액(amount)과 포인트(pointsAmount)를
 * 서버에서 검증할 가격표이다. 구독은 subscription_plans로 검증하며,
 * 포인트팩은 이 테이블로 검증한다.</p>
 *
 * <p><b>보안 필수</b>: 이 테이블 없이는 클라이언트가 {@code {amount:1000, pointsAmount:999999}}를
 * 전송하여 무제한 포인트를 획득할 수 있다.</p>
 *
 * <p>설계서 v2.3 §13.1 참조.</p>
 */
@Entity
@Table(name = "point_pack_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointPackPrice extends BaseAuditEntity {

    /** 포인트팩 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pack_id")
    private Long packId;

    /** 상품명 (UI 표시용, NOT NULL) */
    @Column(name = "pack_name", length = 100, nullable = false)
    private String packName;

    /** 결제 금액 (KRW, NOT NULL) */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** 지급 포인트 (NOT NULL) */
    @Column(name = "points_amount", nullable = false)
    private Integer pointsAmount;

    /** 판매 활성 여부 (DEFAULT TRUE) */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 정렬 순서 */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
