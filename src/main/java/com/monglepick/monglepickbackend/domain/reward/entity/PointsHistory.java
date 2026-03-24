package com.monglepick.monglepickbackend.domain.reward.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 포인트 변동 이력 엔티티 — points_history 테이블 매핑.
 *
 * <p>포인트의 모든 변동(획득, 사용, 만료, 보너스)을 기록한다.
 * 각 레코드는 변동량, 변동 후 잔액, 변동 사유를 포함한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID</li>
 *   <li>{@code pointChange} — 변동량 (양수: 획득, 음수: 사용/만료)</li>
 *   <li>{@code pointAfter} — 변동 후 잔액</li>
 *   <li>{@code pointType} — 변동 유형 (earn, spend, expire, bonus)</li>
 *   <li>{@code description} — 변동 사유 설명</li>
 *   <li>{@code referenceId} — 외부 참조 ID (주문번호 등)</li>
 * </ul>
 *
 * <h3>타임스탬프</h3>
 * <p>created_at만 존재하며 updated_at은 없다 (이력 불변).
 * BaseTimeEntity를 상속하지 않고 {@code @CreationTimestamp}를 직접 사용한다.</p>
 */
@Entity
@Table(name = "points_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointsHistory {

    /** 포인트 이력 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_history_id")
    private Long pointHistoryId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 포인트 변동량 (NOT NULL).
     * 양수: 획득/보너스, 음수: 사용/만료.
     */
    @Column(name = "point_change", nullable = false)
    private Integer pointChange;

    /**
     * 변동 후 포인트 잔액 (NOT NULL).
     * 변동 적용 후의 최종 잔액을 기록한다.
     */
    @Column(name = "point_after", nullable = false)
    private Integer pointAfter;

    /**
     * 포인트 변동 유형 (VARCHAR(50), NOT NULL).
     * earn: 획득, spend: 사용, expire: 만료, bonus: 보너스.
     */
    @Column(name = "point_type", length = 50, nullable = false)
    private String pointType;

    /**
     * 변동 사유 설명 (최대 300자).
     * 예: "출석 체크 보상", "AI 추천 사용", "이벤트 보너스"
     */
    @Column(name = "description", length = 300)
    private String description;

    /**
     * 참조 ID (최대 100자).
     * 변동의 원인이 된 외부 리소스 ID (주문번호, 세션ID 등).
     */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /**
     * 레코드 생성 시각.
     * INSERT 시 자동 설정되며 이후 변경되지 않는다.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
