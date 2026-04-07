package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리워드 정책 변경 이력 엔티티 — reward_policy_history 테이블 매핑.
 *
 * <p>관리자가 {@link RewardPolicy}의 포인트/한도 등을 변경할 때마다
 * 변경 전후 스냅샷과 변경 사유를 INSERT-ONLY 원장으로 기록한다.</p>
 *
 * <h3>INSERT-ONLY 정책</h3>
 * <p>{@link #onPreUpdate()}와 {@link #onPreRemove()}에서 수정/삭제를 원천 차단한다.
 * 감사 이력은 절대 변경되어서는 안 된다. 오류가 있으면 보정 레코드를 추가하는 방식을 사용한다.</p>
 *
 * <h3>스냅샷 형식</h3>
 * <p>{@code before_value} / {@code after_value}는 JSON 문자열로 저장한다.
 * 변경된 필드만 포함해도 되고, 전체 정책 스냅샷을 포함해도 된다.
 * 일관성을 위해 서비스 레이어에서 {@code ObjectMapper}로 직렬화 후 저장한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07 v3.3: 신규 생성. 설계서 §10.1 기반.</li>
 * </ul>
 *
 * @see RewardPolicy
 * @see com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyHistoryRepository
 */
@Entity
@Table(name = "reward_policy_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 기본 생성자 — 외부 직접 생성 금지 */
@AllArgsConstructor
@Builder
public class RewardPolicyHistory extends BaseAuditEntity {

    /**
     * 이력 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reward_policy_history_id")
    private Long rewardPolicyHistoryId;

    /**
     * 변경 대상 정책 ID (BIGINT, NOT NULL).
     *
     * <p>{@code reward_policy.policy_id}를 논리적으로 참조한다.
     * FK 미선언 — 정책 삭제 시 이력이 고아(orphan) 상태가 되어도 보존한다.</p>
     */
    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    /**
     * 변경한 관리자의 userId (VARCHAR(50), nullable).
     *
     * <p>시스템 자동 변경(배치, 마이그레이션)의 경우 null 또는 "SYSTEM"으로 저장한다.</p>
     */
    @Column(name = "changed_by", length = 50)
    private String changedBy;

    /**
     * 변경 사유 (TEXT, nullable).
     *
     * <p>관리자 페이지에서 변경 시 입력하는 자유 형식 메모.
     * 예: "v3.3 경제 타당성 분석 반영 — PLATINUM daily_earn_cap 2000→500 하향"</p>
     */
    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    /**
     * 변경 전 정책 스냅샷 (JSON, nullable).
     *
     * <p>변경 전 {@link RewardPolicy}의 주요 필드를 JSON으로 직렬화한 문자열.
     * 신규 생성(INSERT) 시에는 null.</p>
     *
     * <p>예시:
     * <pre>{@code {"pointsAmount": 20, "dailyLimit": 5, "pointType": "earn"}}</pre>
     * </p>
     */
    @Column(name = "before_value", columnDefinition = "JSON")
    private String beforeValue;

    /**
     * 변경 후 정책 스냅샷 (JSON, NOT NULL).
     *
     * <p>변경 후 {@link RewardPolicy}의 주요 필드를 JSON으로 직렬화한 문자열.</p>
     *
     * <p>예시:
     * <pre>{@code {"pointsAmount": 10, "dailyLimit": 3, "pointType": "earn"}}</pre>
     * </p>
     */
    @Column(name = "after_value", columnDefinition = "JSON")
    private String afterValue;

    // ──────────────────────────────────────────────
    // INSERT-ONLY 강제 (감사 이력 보호)
    // ──────────────────────────────────────────────

    /**
     * UPDATE 차단 — INSERT-ONLY 원장 보호.
     *
     * <p>JPA dirty checking이 UPDATE를 시도하면 예외를 발생시킨다.
     * 이력 레코드는 생성 후 절대 수정할 수 없다.</p>
     *
     * @throws UnsupportedOperationException 항상
     */
    @PreUpdate
    protected void onPreUpdate() {
        throw new UnsupportedOperationException(
                "reward_policy_history는 INSERT-ONLY입니다. UPDATE가 금지되어 있습니다. " +
                "오류 정정이 필요하면 보정 레코드를 추가하세요. policyId=" + policyId);
    }

    /**
     * DELETE 차단 — INSERT-ONLY 원장 보호.
     *
     * <p>JPA가 DELETE를 시도하면 예외를 발생시킨다.
     * 이력 레코드는 생성 후 절대 삭제할 수 없다.</p>
     *
     * @throws UnsupportedOperationException 항상
     */
    @PreRemove
    protected void onPreRemove() {
        throw new UnsupportedOperationException(
                "reward_policy_history는 INSERT-ONLY입니다. DELETE가 금지되어 있습니다. " +
                "policyId=" + policyId);
    }
}
