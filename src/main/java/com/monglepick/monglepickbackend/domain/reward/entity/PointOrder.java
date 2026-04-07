package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 아이템 구매 내역 엔티티 — point_orders 테이블 매핑.
 *
 * <p>사용자가 보유 포인트로 포인트 상점(point_items)의 아이템을 구매한 이력을 저장한다.
 * AI 이용권, 영화티켓응모권 등 포인트 소비처에서 발생하는 거래를 기록하며,
 * 포인트 차감(PointsHistory)과 함께 원자적으로 처리된다.</p>
 *
 * <p>Excel DB 설계서 Table 25 기준으로 생성되었다.</p>
 *
 * <h3>상태 (status)</h3>
 * <ul>
 *   <li>{@code PENDING} — 처리 중 (포인트 차감 전)</li>
 *   <li>{@code COMPLETED} — 구매 완료 (포인트 차감 성공)</li>
 *   <li>{@code CANCELLED} — 취소됨 (포인트 차감 실패 또는 사용자 취소)</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 구매 사용자 ID (users.user_id 참조)</li>
 *   <li>{@code pointItem} — 구매한 포인트 아이템 (point_items FK, LAZY)</li>
 *   <li>{@code usedPoint} — 실제 사용(차감)된 포인트 수</li>
 *   <li>{@code status} — 구매 상태 (PENDING / COMPLETED / CANCELLED)</li>
 *   <li>{@code itemCount} — 구매 수량 (기본값: 1)</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_point_orders_user_id} — 사용자별 구매 이력 조회</li>
 *   <li>{@code idx_point_orders_status} — 상태별 집계 조회</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 25 기준으로 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "point_orders",
        indexes = {
                /* 사용자별 포인트 구매 이력 조회 (마이페이지 이용 내역) */
                @Index(name = "idx_point_orders_user_id", columnList = "user_id"),
                /* 상태별 집계 (COMPLETED 건수로 소비 통계 산출) */
                @Index(name = "idx_point_orders_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointOrder extends BaseAuditEntity {

    /**
     * 포인트 주문 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_order_id")
    private Long pointOrderId;

    /**
     * 구매 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 논리적으로 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 구매한 포인트 아이템 (LAZY, NOT NULL).
     * point_items.point_item_id를 FK로 참조한다.
     * AI 이용권, 영화티켓응모권 등 포인트 상점 상품과 연결된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_item_id", nullable = false)
    private PointItem pointItem;

    /**
     * 실제 사용(차감)된 포인트 수 (NOT NULL).
     * 구매 시점의 아이템 가격 × 수량으로 계산된 총 차감 포인트.
     * 가격 변동 이력 보존을 위해 별도 컬럼으로 저장한다.
     */
    @Column(name = "used_point", nullable = false)
    private Integer usedPoint;

    /**
     * 구매 상태 (VARCHAR(20), NOT NULL).
     * 허용 값: PENDING(처리중), COMPLETED(완료), CANCELLED(취소)
     * 기본값: PENDING — 포인트 차감 트랜잭션 시작 시 PENDING으로 생성.
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 구매 수량 (기본값: 1).
     * 동일 아이템을 여러 개 한 번에 구매하는 경우를 지원한다.
     * usedPoint = 아이템단가 × itemCount 로 계산된다.
     */
    @Column(name = "item_count", nullable = false)
    @Builder.Default
    private Integer itemCount = 1;

    /* created_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */
}
