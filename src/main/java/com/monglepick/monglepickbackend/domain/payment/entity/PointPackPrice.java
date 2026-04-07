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
 * <p>포인트팩 결제 시 클라이언트가 보낸 금액(price)과 포인트(pointsAmount)를
 * 서버에서 검증할 가격표이다. 구독은 subscription_plans로 검증하며,
 * 포인트팩은 이 테이블로 검증한다.</p>
 *
 * <p><b>보안 필수</b>: 이 테이블 없이는 클라이언트가 {@code {price:1000, pointsAmount:999999}}를
 * 전송하여 무제한 포인트를 획득할 수 있다.</p>
 *
 * <h3>v3.2 포인트팩 구성 (1P=10원 통일)</h3>
 * <table border="1">
 *   <tr><th>팩명</th><th>가격(원)</th><th>포인트(P)</th><th>원/P</th></tr>
 *   <tr><td>100 포인트</td><td>1,000원</td><td>100P</td><td>10원</td></tr>
 *   <tr><td>200 포인트</td><td>2,000원</td><td>200P</td><td>10원</td></tr>
 *   <tr><td>500 포인트</td><td>5,000원</td><td>500P</td><td>10원</td></tr>
 *   <tr><td>1,000 포인트</td><td>10,000원</td><td>1,000P</td><td>10원</td></tr>
 *   <tr><td>5,000 포인트</td><td>50,000원</td><td>5,000P</td><td>10원</td></tr>
 *   <tr><td>10,000 포인트</td><td>100,000원</td><td>10,000P</td><td>10원</td></tr>
 * </table>
 *
 * <p>설계서 v3.2 §13.1, 엑셀 Table 50 기준.</p>
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

    /**
     * 결제 금액 (KRW, NOT NULL) — v3.2: 컬럼명 amount → price (엑셀 Table 50 기준).
     *
     * <p>1P = 10원으로 통일. pointsAmount × 10 = price.</p>
     */
    @Column(name = "price", nullable = false)
    private Integer price;

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

    // ─────────────────────────────────────────────
    // 도메인 메서드 (관리자 CRUD 전용)
    // ─────────────────────────────────────────────

    /**
     * 포인트팩 메타 정보를 일괄 수정한다 (PK 제외).
     *
     * <p>가격(price)/지급포인트(pointsAmount) 변경은 결제 검증과 직결되므로 신중하게 사용한다.
     * 운영 중인 팩의 가격을 변경하면 진행 중인 주문에 영향을 줄 수 있다.</p>
     */
    public void updateInfo(String packName, Integer price, Integer pointsAmount,
                           Boolean isActive, Integer sortOrder) {
        this.packName = packName;
        this.price = price;
        this.pointsAmount = pointsAmount;
        this.isActive = isActive != null ? isActive : true;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    /** 활성 상태 토글 */
    public void updateActive(boolean active) {
        this.isActive = active;
    }
}
