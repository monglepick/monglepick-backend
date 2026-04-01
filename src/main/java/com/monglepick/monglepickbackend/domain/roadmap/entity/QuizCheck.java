package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
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

import java.time.LocalDateTime;

/**
 * 퀴즈 관리자 검수 엔티티 — quiz_checks 테이블 매핑.
 *
 * <p>AI가 자동 생성한 퀴즈({@code quizzes})를 관리자가 검수·승인·반려하는 이력을 기록한다.
 * 퀴즈 파이프라인 5단계 중 "관리자 검수" 단계에 해당한다.</p>
 *
 * <h3>퀴즈 파이프라인 (5단계)</h3>
 * <ol>
 *   <li>AI 생성 — AI Agent가 영화 기반 퀴즈 자동 생성</li>
 *   <li><b>관리자 검수</b> — 이 엔티티가 관리하는 단계 (PENDING → APPROVED / REJECTED)</li>
 *   <li>사용자 참여 — quiz_participations 에 참여 기록</li>
 *   <li>정답 제출 — quiz_attempts 에 시도 기록</li>
 *   <li>리워드 지급 — quiz_rewards 에 보상 기록</li>
 * </ol>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code quizId}     — 검수 대상 퀴즈 ID (FK → quizzes.quiz_id)</li>
 *   <li>{@code adminId}    — 검수를 수행한 관리자 ID</li>
 *   <li>{@code status}     — 검수 상태 (PENDING / APPROVED / REJECTED)</li>
 *   <li>{@code comment}    — 반려 사유 또는 검수 메모 (nullable)</li>
 *   <li>{@code checkedAt}  — 검수 완료 시각 (nullable, PENDING 상태에서는 null)</li>
 * </ul>
 *
 * <h3>상태 전이</h3>
 * <pre>
 * PENDING → APPROVED  (퀴즈 서비스 노출 허용)
 *         → REJECTED  (퀴즈 비활성화, comment 필수)
 * </pre>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>검수 이력이 중요하므로 소프트 삭제만 허용하며, 물리 삭제는 금지한다.</li>
 *   <li>quizId 1건당 여러 검수 이력이 쌓일 수 있다 (재검수 허용).</li>
 * </ul>
 */
@Entity
@Table(name = "quiz_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class QuizCheck extends BaseAuditEntity {

    /**
     * 퀴즈 검수 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;

    /**
     * 검수 대상 퀴즈 ID (BIGINT, NOT NULL).
     * quizzes.quiz_id를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    /**
     * 검수를 수행한 관리자 ID (VARCHAR(50), NOT NULL).
     * admin 테이블의 admin_id를 참조한다.
     */
    @Column(name = "admin_id", length = 50, nullable = false)
    private String adminId;

    /**
     * 검수 상태 (VARCHAR(20), NOT NULL).
     * 기본값: "PENDING".
     * <ul>
     *   <li>"PENDING"  — 검수 대기 (초기 상태)</li>
     *   <li>"APPROVED" — 승인 완료 (퀴즈 서비스 노출 허용)</li>
     *   <li>"REJECTED" — 반려 (퀴즈 비활성화, comment 필드에 사유 기록)</li>
     * </ul>
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 검수 메모 또는 반려 사유 (TEXT, nullable).
     * REJECTED 상태일 때 반드시 사유를 기록해야 한다 (서비스 레이어에서 검증).
     * APPROVED 상태에서도 선택적으로 메모를 남길 수 있다.
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /**
     * 검수 완료 시각 (nullable).
     * PENDING 상태에서는 null이며, APPROVED 또는 REJECTED 처리 시 현재 시각으로 설정된다.
     * BaseAuditEntity의 updated_at과 별도로 도메인 의미의 검수 완료 시점을 명시적으로 기록한다.
     */
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (상태 전이)
    // ─────────────────────────────────────────────

    /**
     * 퀴즈를 승인 처리한다 (PENDING → APPROVED).
     *
     * <p>checkedAt을 현재 시각으로 설정한다.
     * 이미 APPROVED/REJECTED 상태에서 호출 시 덮어쓰기가 발생하므로
     * 서비스 레이어에서 상태 전이 가능 여부를 사전 검증해야 한다.</p>
     *
     * @param memo 선택적 승인 메모 (null 허용)
     */
    public void approve(String memo) {
        this.status = "APPROVED";
        this.comment = memo;
        this.checkedAt = LocalDateTime.now();
    }

    /**
     * 퀴즈를 반려 처리한다 (PENDING → REJECTED).
     *
     * <p>반려 사유(reason)는 필수이며, checkedAt을 현재 시각으로 설정한다.</p>
     *
     * @param reason 반려 사유 (필수, 서비스 레이어에서 @NotBlank 검증)
     */
    public void reject(String reason) {
        this.status = "REJECTED";
        this.comment = reason;
        this.checkedAt = LocalDateTime.now();
    }

    /**
     * 해당 검수가 승인 상태인지 여부를 반환한다.
     *
     * @return true이면 APPROVED, 그 외 false
     */
    public boolean isApproved() {
        return "APPROVED".equals(this.status);
    }

    /**
     * 해당 검수가 검수 대기 상태인지 여부를 반환한다.
     *
     * @return true이면 PENDING, 그 외 false
     */
    public boolean isPending() {
        return "PENDING".equals(this.status);
    }
}
