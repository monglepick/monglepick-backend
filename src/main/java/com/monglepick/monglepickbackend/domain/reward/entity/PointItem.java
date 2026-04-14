package com.monglepick.monglepickbackend.domain.reward.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 *   <li>2026-04-14: 카테고리별 실제 지급 로직(C 방향)을 위한 4컬럼 추가 — {@code itemType},
 *       {@code amount}, {@code durationDays}, {@code imageUrl}. 기존 {@code itemCategory}는
 *       대분류로 유지하고, {@code itemType}이 지급 로직 분기 키 역할을 한다.</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code itemName} — 상품명 (필수)</li>
 *   <li>{@code itemDescription} — 상품 설명 (TEXT)</li>
 *   <li>{@code itemPrice} — 필요 포인트 (필수)</li>
 *   <li>{@code itemCategory} — 대분류 카테고리 (기본값: "general"). 정규값은
 *       {@link com.monglepick.monglepickbackend.domain.reward.constants.PointItemCategory} 참조</li>
 *   <li>{@code itemType} — 지급 분기 키 (ENUM, v2). NULL 가능(레거시 데이터 호환).
 *       파싱 실패 시 {@link PointItemType#UNKNOWN}으로 처리된다.</li>
 *   <li>{@code amount} — 지급 수량 (예: AI_TOKEN_5 → 5). NULL이면 type의 기본값 사용.</li>
 *   <li>{@code durationDays} — 유효기간(일). NULL이면 무기한.</li>
 *   <li>{@code imageUrl} — 아바타/배지 이미지 경로 (정적 리소스 또는 CDN).</li>
 *   <li>{@code isActive} — 판매 활성 여부 (기본값: true)</li>
 * </ul>
 */
@Entity
@Table(name = "point_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseTimeEntity → BaseAuditEntity 변경: created_by, updated_by 컬럼 추가 관리 */
public class PointItem extends BaseAuditEntity {

    /** 포인트 상품 고유 ID (BIGINT AUTO_INCREMENT PK) — PK 컬럼명 변경 없음 */
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
     * 정규값: "coupon"/"avatar"/"badge"/"apply"/"hint" (소문자 통일).
     * 과거 버전 {@code "COUPON"} / {@code "ai_feature"} / {@code "profile"} /
     * {@code "roadmap"}는 PointItemInitializer 구버전 정리 로직에서 정규화되거나
     * {@code is_active=false}로 비활성화된다.
     */
    @Column(name = "item_category", length = 50)
    @Builder.Default
    private String itemCategory = "general";

    /**
     * 지급 분기 키 (v2, 2026-04-14).
     *
     * <p>{@link PointItemType} enum 값이 문자열로 저장된다.
     * PointItemService.exchangeItem()가 이 값을 읽어 UserAiQuota 적립 vs UserItem INSERT를 분기한다.
     * NULL이거나 알 수 없는 값이면 {@link PointItemType#UNKNOWN}으로 처리되어 교환 시 예외 발생.</p>
     *
     * <p>ddl-auto=update에서 기존 행의 이 컬럼은 NULL로 유지되므로, 마이그레이션 레이어
     * ({@code PointItemInitializer})가 기존 시드를 이름으로 매칭하여 itemType을 채운다.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 50)
    private PointItemType itemType;

    /**
     * 지급 수량 (v2, 2026-04-14).
     *
     * <p>의미는 {@link #itemType}에 따라 다르다:</p>
     * <ul>
     *   <li>{@code AI_TOKEN_*} — 지급할 AI 이용권 횟수 (1/5/20/50)</li>
     *   <li>{@code INVENTORY} 타입 — 발급할 user_items 레코드 수 (기본 1)</li>
     * </ul>
     *
     * <p>NULL이면 {@link PointItemType#getAmount()} 기본값이 사용된다.</p>
     */
    @Column(name = "amount")
    private Integer amount;

    /**
     * 유효기간(일) — NULL이면 무기한 (v2, 2026-04-14).
     *
     * <p>UserItem INSERT 시 {@code expires_at = acquired_at + durationDays}로 계산된다.
     * AI 이용권은 UserAiQuota에 카운터로만 기록되므로 유효기간 미사용 (원자 카운터 모델).</p>
     *
     * <p>NULL이면 {@link PointItemType#getDurationDays()} 기본값이 사용된다.</p>
     */
    @Column(name = "duration_days")
    private Integer durationDays;

    /**
     * 아이템 이미지 URL (v2, 2026-04-14).
     *
     * <p>아바타/배지는 이 경로를 프로필/댓글에서 렌더링한다.
     * 예: {@code "/avatars/mongle.png"} (정적 리소스) 또는 CDN URL.
     * NULL이면 프론트엔드가 카테고리별 기본 아이콘을 표시한다.</p>
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * 판매 활성 여부.
     * 기본값: true.
     * false로 설정하면 사용자에게 노출되지 않는다.
     */
    /** DDL 기본값 true — NULL 방지 (V5 테스트에서 발견: @Builder.Default만으로는 DB 컬럼에 NULL 저장됨) */
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 실효 지급 수량 반환 — {@link #amount}가 NULL이면 {@link #itemType} 기본값을 사용한다.
     *
     * <p>exchangeItem에서 매 호출 시 이 메서드를 쓰면 NULL 가드 반복을 피할 수 있다.</p>
     *
     * @return 지급 수량 (1 이상 보장, 알 수 없는 타입은 0)
     */
    public int resolveAmount() {
        if (amount != null && amount > 0) {
            return amount;
        }
        return itemType != null ? itemType.getAmount() : 0;
    }

    /**
     * 실효 유효기간(일) 반환 — {@link #durationDays}가 NULL이면 {@link #itemType} 기본값을 사용한다.
     *
     * @return 유효기간(일). NULL이면 무기한.
     */
    public Integer resolveDurationDays() {
        if (durationDays != null) {
            return durationDays;
        }
        return itemType != null ? itemType.getDurationDays() : null;
    }
}
