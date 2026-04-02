package com.monglepick.monglepickbackend.domain.reward.entity;

/*
 * ★ BaseAuditEntity를 상속하지 않는다.
 * 이유: BaseAuditEntity를 상속하면 updated_at/updated_by가 자동 포함되어
 *       INSERT-ONLY 정책(@PreUpdate 예외)과 충돌한다.
 * 대신 created_at/created_by만 직접 선언한다.
 * 설계서 v2.3 §4.2 참조.
 */
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 포인트 변동 이력 엔티티 — points_history 테이블 매핑 (INSERT-ONLY 원장).
 *
 * <p>포인트의 모든 변동(획득, 사용, 보너스, 만료, 환불, 회수)을 기록한다.
 * <b>INSERT만 허용하며 UPDATE/DELETE는 금지</b>한다.
 * 잘못된 지급은 {@code point_type='refund'} 또는 {@code 'revoke'}인 새 레코드를
 * INSERT하여 역전 처리한다.</p>
 *
 * <h3>INSERT-ONLY 정책 강제</h3>
 * <ul>
 *   <li>{@code @PreUpdate} — UPDATE 시도 시 {@link UnsupportedOperationException} 발생</li>
 *   <li>{@code @PreRemove} — DELETE 시도 시 {@link UnsupportedOperationException} 발생</li>
 *   <li>BaseAuditEntity를 상속하지 않음 — updated_at/updated_by 컬럼 제거</li>
 *   <li>created_at/created_by만 직접 선언 ({@code @CreationTimestamp}, {@code @CreatedBy})</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseAuditEntity 상속 추가, PK 필드명 변경</li>
 *   <li>2026-04-02: 설계서 v2.3 §4.2 기준 전면 수정 —
 *       BaseAuditEntity 상속 제거(INSERT-ONLY 정합성),
 *       action_type/base_amount/applied_multiplier 3개 컬럼 추가,
 *       uk_history_user_action_ref UNIQUE 인덱스 추가,
 *       @PreUpdate/@PreRemove INSERT-ONLY 강제,
 *       point_type에 refund/revoke 추가</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code pointChange} — 변동량 (양수: 획득, 음수: 사용/환불/회수)</li>
 *   <li>{@code pointAfter} — 변동 직후 잔액 스냅샷 (point_after 체인 검증용)</li>
 *   <li>{@code pointType} — earn/spend/bonus/expire/refund/revoke</li>
 *   <li>{@code actionType} — 활동 유형 코드 (reward_policy.action_type, nullable)</li>
 *   <li>{@code baseAmount} — 배율 적용 전 원래 포인트 (nullable)</li>
 *   <li>{@code appliedMultiplier} — 적용된 등급 배율 (nullable)</li>
 * </ul>
 *
 * <h3>인덱스</h3>
 * <ul>
 *   <li>{@code idx_history_user_created} — (user_id, created_at): 사용자별 이력 조회</li>
 *   <li>{@code idx_history_user_type} — (user_id, point_type): 유형별 집계</li>
 *   <li>{@code uk_history_user_action_ref} — (user_id, action_type, reference_id): <b>UNIQUE</b> 중복 지급 방지</li>
 *   <li>{@code idx_history_user_action_type} — (user_id, action_type, created_at): 활동별 이력 조회</li>
 * </ul>
 *
 * <h3>point_after 체인 검증</h3>
 * <p>연속된 두 레코드에서: {@code record[n].point_after + record[n+1].point_change = record[n+1].point_after}
 * 이 항상 성립해야 한다. 정합성 배치에서 검증.</p>
 */
@Entity
@Table(
        name = "points_history",
        uniqueConstraints = {
                /* ★ 중복 지급 방지 최종 안전장치 — DB 레벨에서 동일 (user, action, reference) 조합 차단.
                 * MySQL UNIQUE 인덱스는 NULL을 중복 허용하므로 action_type=NULL인 기존 데이터에 영향 없음. */
                @UniqueConstraint(
                        name = "uk_history_user_action_ref",
                        columnNames = {"user_id", "action_type", "reference_id"}
                )
        },
        indexes = {
                @Index(name = "idx_history_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_history_user_type", columnList = "user_id, point_type"),
                @Index(name = "idx_history_user_action_type", columnList = "user_id, action_type, created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointsHistory {

    /**
     * 포인트 이력 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "points_history_id")
    private Long pointsHistoryId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 포인트 변동량 (NOT NULL).
     * 양수: 획득/보너스, 음수: 사용/만료/환불/회수.
     */
    @Column(name = "point_change", nullable = false)
    private Integer pointChange;

    /**
     * 변동 후 포인트 잔액 스냅샷 (NOT NULL).
     * 변동 적용 후의 최종 잔액을 기록한다.
     * point_after 체인: record[n].point_after + record[n+1].point_change = record[n+1].point_after
     */
    @Column(name = "point_after", nullable = false)
    private Integer pointAfter;

    /**
     * 포인트 변동 유형 (VARCHAR(50), NOT NULL).
     * <ul>
     *   <li>{@code earn} — 활동 리워드 획득 (등급 배율 적용 대상)</li>
     *   <li>{@code spend} — AI 추천 사용, 아이템 교환 등 소비</li>
     *   <li>{@code bonus} — 일회성/마일스톤 보너스 (배율 미적용)</li>
     *   <li>{@code expire} — 포인트 만료</li>
     *   <li>{@code refund} — 결제 환불로 인한 포인트 회수</li>
     *   <li>{@code revoke} — 콘텐츠 삭제로 인한 리워드 회수 (§6.3.2)</li>
     * </ul>
     */
    @Column(name = "point_type", length = 50, nullable = false)
    private String pointType;

    /**
     * 변동 사유 설명 (최대 300자).
     * 예: "출석 체크 보상", "AI 추천 사용", "REVIEW_CREATE 리워드"
     */
    @Column(name = "description", length = 300)
    private String description;

    /**
     * 참조 ID (최대 100자).
     * 변동의 원인이 된 외부 리소스 ID.
     * <p>reference_id 생성 규칙 (설계서 §6.3.1):
     * <ul>
     *   <li>REVIEW_CREATE → "movie_{movieId}" (같은 영화 리뷰 1회만)</li>
     *   <li>POST_REWARD → "post_{postId}"</li>
     *   <li>COMMENT_CREATE → "comment_{commentId}"</li>
     *   <li>WISHLIST_ADD → "movie_{movieId}"</li>
     *   <li>WORLDCUP_COMPLETE → "session_{sessionId}"</li>
     *   <li>COURSE_COMPLETE → "course_{courseId}"</li>
     *   <li>AI 차감 → sessionId</li>
     *   <li>결제 충전 → orderId</li>
     * </ul>
     * </p>
     */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    // ──────────────────────────────────────────────
    // ★ 신규 컬럼 3개 (설계서 v2.3 §4.2)
    // ──────────────────────────────────────────────

    /**
     * 활동 유형 코드 (VARCHAR(50), nullable).
     *
     * <p>reward_policy.action_type과 논리적 참조 관계.
     * 기존 데이터(출석, AI 차감, 결제 충전 등)는 NULL로 유지.
     * MySQL UNIQUE 인덱스는 NULL을 중복 허용하므로 기존 데이터에 영향 없음.</p>
     */
    @Column(name = "action_type", length = 50)
    private String actionType;

    /**
     * 배율 적용 전 원래 포인트 (INT, nullable).
     *
     * <p>reward_policy.points_amount 값. NULL이면 비리워드 변동(AI 차감, 결제 충전 등).</p>
     * <ul>
     *   <li>point_type='earn': base_amount = policy.points_amount</li>
     *   <li>point_type='bonus': base_amount = policy.points_amount</li>
     *   <li>AI 차감/결제 충전/관리자 수동: base_amount = NULL</li>
     * </ul>
     * <p>정합성 검증: base_amount IS NOT NULL인 레코드에서
     * {@code point_change = floor(base_amount × COALESCE(applied_multiplier, 1.00))} 항상 성립.</p>
     */
    @Column(name = "base_amount")
    private Integer baseAmount;

    /**
     * 적용된 등급 배율 (DECIMAL(3,2), nullable).
     *
     * <p>리워드 지급 시점의 등급 배율. NULL이면 배율 미적용(bonus/spend).</p>
     * <ul>
     *   <li>point_type='earn': applied_multiplier = grade.reward_multiplier (예: 1.30)</li>
     *   <li>point_type='bonus': applied_multiplier = NULL (배율 미적용)</li>
     *   <li>AI 차감/결제 충전: applied_multiplier = NULL</li>
     * </ul>
     * <p>관리자 통계: 정책 원래값 기준은 SUM(base_amount), 실제 지급 기준은 SUM(point_change),
     * 배율 효과는 AVG(applied_multiplier).</p>
     */
    @Column(name = "applied_multiplier", precision = 3, scale = 2)
    private BigDecimal appliedMultiplier;

    // ──────────────────────────────────────────────
    // 감사 필드 (BaseAuditEntity 대신 직접 선언)
    // ──────────────────────────────────────────────

    /**
     * 레코드 생성 시각 (DATETIME(6), NOT NULL, 변경 불가).
     * INSERT 시 Hibernate가 자동 설정. UPDATE 시 변경되지 않음.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * 레코드 생성자 ID (VARCHAR(50)).
     * INSERT 시 AuditorAware를 통해 현재 사용자 ID가 자동 설정.
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 50)
    private String createdBy;

    // ──────────────────────────────────────────────
    // INSERT-ONLY 정책 강제 — UPDATE/DELETE 시도 시 예외 발생
    // ──────────────────────────────────────────────

    /**
     * INSERT-ONLY 정책 — UPDATE 차단.
     *
     * <p>JPA가 dirty checking으로 UPDATE를 시도하면 이 콜백이 호출되어
     * {@link UnsupportedOperationException}을 발생시킨다.
     * 잘못된 지급은 point_type='refund' 또는 'revoke'인 새 레코드를 INSERT하여 역전 처리.</p>
     */
    @PreUpdate
    protected void preventUpdate() {
        throw new UnsupportedOperationException(
                "PointsHistory는 INSERT-ONLY 테이블입니다. UPDATE가 불가합니다. "
                        + "잘못된 지급은 point_type='refund'/'revoke' 레코드를 새로 INSERT하세요.");
    }

    /**
     * INSERT-ONLY 정책 — DELETE 차단.
     *
     * <p>JPA의 remove() 호출 시 이 콜백이 호출되어
     * {@link UnsupportedOperationException}을 발생시킨다.</p>
     */
    @PreRemove
    protected void preventDelete() {
        throw new UnsupportedOperationException(
                "PointsHistory는 INSERT-ONLY 테이블입니다. DELETE가 불가합니다. "
                        + "잘못된 지급은 point_type='refund'/'revoke' 레코드를 새로 INSERT하세요.");
    }
}
