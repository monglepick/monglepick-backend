package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.domain.reward.constants.UserItemStatus;
import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;

/**
 * 사용자 보유 아이템 엔티티 — user_items 테이블 매핑 (2026-04-14 신규, C 방향).
 *
 * <p>유저가 포인트로 교환한 AI 이용권·아바타·배지·응모권·힌트 등을 보유 상태로 추적한다.
 * AI 이용권은 수량 카운터({@link UserAiQuota#getPurchasedAiTokens()})로 별도 관리되므로
 * 이 테이블에는 기록되지 않는다. 즉 이 테이블은 <b>인벤토리 아이템 전용</b>이다.</p>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li><b>userId는 VARCHAR(50) FK</b> — 설계서 §15.4 Phase 1 원칙. {@code @ManyToOne User} 대신
 *       userId 문자열만 저장. 데이터 격리 유지 + 도메인 침범 방지.</li>
 *   <li><b>pointItem은 {@link ManyToOne}</b> — 아이템 마스터(카테고리·이미지·유효기간)를 정규화된 경로로
 *       참조. FetchType.LAZY로 N+1 방지.</li>
 *   <li><b>status는 ENUM STRING 저장</b> — DB 가독성 + 확장성. ORDINAL 저장 시 enum 값 추가에 취약.</li>
 *   <li><b>중복 허용</b> — 동일 아이템을 여러 번 교환한 경우 각각 별도 레코드로 저장하여
 *       "5회 응모권 구매 = 응모 5회 대기"를 자연스럽게 표현. 카테고리당 하나만 착용 가능(EQUIPPED)이라는 제약은
 *       애플리케이션 레이어에서 검증한다.</li>
 * </ul>
 *
 * <h3>상태 전이</h3>
 * <p>{@link UserItemStatus} javadoc 참조.</p>
 *
 * <h3>인덱스</h3>
 * <ul>
 *   <li>{@code idx_user_items_user_status} — 유저별 활성 아이템 조회 (보유 목록 API 대부분의 쿼리)</li>
 *   <li>{@code idx_user_items_user_equipped} — 유저별 착용 아이템 조회 (프로필 렌더링 시)</li>
 *   <li>{@code idx_user_items_expires} — 만료 배치({@code @Scheduled})에서 expires_at &lt; now AND status=ACTIVE|EQUIPPED 스캔</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_items",
        indexes = {
                @Index(name = "idx_user_items_user_status", columnList = "user_id, status"),
                @Index(name = "idx_user_items_user_equipped", columnList = "user_id, status, point_item_id"),
                @Index(name = "idx_user_items_expires", columnList = "status, expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserItem extends BaseTimeEntity {

    /** PK — BIGINT AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_item_id")
    private Long userItemId;

    /**
     * 소유자 유저 ID (VARCHAR(50) FK users.user_id).
     *
     * <p>Phase 1 원칙에 따라 @ManyToOne User 연관 대신 문자열 FK만 보관한다.</p>
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 구매한 원본 아이템 (FK point_items.point_item_id).
     *
     * <p>카테고리·이미지·유효기간 기본값은 여기서 읽는다 (정규화).
     * LAZY 로딩으로 목록 조회 시 N+1 방지, 필요 시 JOIN FETCH로 가져온다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_item_id", nullable = false)
    private PointItem pointItem;

    /** 획득 시각 — 교환 완료 시점. NOT NULL. */
    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    /**
     * 만료 시각 — NULL이면 무기한 (영구 아바타 등).
     *
     * <p>PointItem.durationDays를 읽어 acquired_at + durationDays로 계산된다.
     * 만료 배치가 이 값을 기준으로 status=EXPIRED 전환 + 착용 해제를 수행한다.</p>
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 사용 완료 시각 — 힌트/응모권 등 1회성 아이템에만 설정. NULL=미사용. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 착용 시작 시각 — 착용 중(EQUIPPED)일 때만 값이 있음. 해제 시 NULL 리셋. */
    @Column(name = "equipped_at")
    private LocalDateTime equippedAt;

    /** 현재 상태 — ENUM STRING 저장 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private UserItemStatus status = UserItemStatus.ACTIVE;

    /**
     * 획득 출처 — 감사/분석용. NULL 가능.
     *
     * <p>예: "EXCHANGE" (포인트 교환), "REWARD" (업적 보상), "ADMIN" (운영자 수동 지급)</p>
     */
    @Column(name = "source", length = 30)
    private String source;

    /**
     * 잔여 수량 (힌트형 아이템용). 기본 1.
     *
     * <p>대부분 아이템은 1회성이라 1로 고정되나, "힌트 3개 묶음" 같은 아이템이 생기면
     * 이 값이 0이 될 때 status=USED로 전환된다.</p>
     */
    @Column(name = "remaining_quantity", nullable = false, columnDefinition = "INT DEFAULT 1")
    @Builder.Default
    private Integer remainingQuantity = 1;

    // ──────────────────────────────────────────────
    // 도메인 메서드 — 상태 전이 캡슐화
    // ──────────────────────────────────────────────

    /**
     * 착용 상태로 전환 — 아바타/배지에만 의미 있음.
     *
     * <p>ACTIVE 상태에서만 호출 가능. 이미 EQUIPPED면 no-op.</p>
     *
     * @throws IllegalStateException 만료되었거나 사용 완료된 아이템인 경우
     */
    public void equip() {
        if (status == UserItemStatus.EQUIPPED) {
            return;
        }
        if (status != UserItemStatus.ACTIVE) {
            throw new IllegalStateException("착용 가능한 상태가 아닙니다: status=" + status);
        }
        this.status = UserItemStatus.EQUIPPED;
        this.equippedAt = LocalDateTime.now();
    }

    /**
     * 착용 해제 — 다시 ACTIVE로 전환.
     *
     * <p>EQUIPPED 상태에서만 호출 가능. 이미 ACTIVE면 no-op.</p>
     */
    public void unequip() {
        if (status == UserItemStatus.ACTIVE) {
            return;
        }
        if (status != UserItemStatus.EQUIPPED) {
            throw new IllegalStateException("착용 해제 가능한 상태가 아닙니다: status=" + status);
        }
        this.status = UserItemStatus.ACTIVE;
        this.equippedAt = null;
    }

    /**
     * 1회 사용 — 힌트/응모권 소비.
     *
     * <p>remainingQuantity를 1 감소시키고, 0이 되면 status=USED로 전환한다.
     * 0 미만으로 내려가는 것을 방어한다.</p>
     *
     * @throws IllegalStateException 이미 USED/EXPIRED 상태이거나 잔여 수량 0인 경우
     */
    public void consumeOne() {
        if (status != UserItemStatus.ACTIVE) {
            throw new IllegalStateException("사용 가능한 상태가 아닙니다: status=" + status);
        }
        if (remainingQuantity == null || remainingQuantity <= 0) {
            throw new IllegalStateException("잔여 수량이 없습니다: userItemId=" + userItemId);
        }
        this.remainingQuantity = this.remainingQuantity - 1;
        if (this.remainingQuantity <= 0) {
            this.status = UserItemStatus.USED;
            this.usedAt = LocalDateTime.now();
        }
    }

    /**
     * 만료 처리 — 배치에서 호출. status=EXPIRED로 전환 + 착용 해제.
     */
    public void markExpired() {
        this.status = UserItemStatus.EXPIRED;
        this.equippedAt = null;
    }

    /**
     * 만료 여부 판단 — 현재 시각 기준.
     *
     * @param now 기준 시각
     * @return expires_at이 설정되어 있고 지났으면 true
     */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
