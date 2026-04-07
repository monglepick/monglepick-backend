package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 퀴즈 검수 엔티티 — quiz_reviews 테이블 매핑.
 *
 * <p>관리자가 AI 생성 퀴즈({@link Quiz})를 검수(승인/반려)한 이력을 저장한다.
 * 하나의 퀴즈에 대해 여러 차례 검수가 이루어질 수 있으므로(재검수 시나리오)
 * Quiz와 N:1 관계를 유지한다.</p>
 *
 * <h3>파이프라인 역할</h3>
 * <ol>
 *   <li>AI가 퀴즈를 생성하면 Quiz.status = PENDING</li>
 *   <li>관리자가 검수 → 이 테이블에 검수 결과 레코드 INSERT</li>
 *   <li>승인(APPROVED) 시 Quiz.status → APPROVED로 갱신</li>
 *   <li>반려(REJECTED) 시 Quiz.status → REJECTED로 갱신</li>
 * </ol>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code quiz} — 검수 대상 퀴즈 (FK → quizzes.quiz_id, LAZY)</li>
 *   <li>{@code adminId} — 검수 담당 관리자 ID (Long FK → admin.admin_id, JPA/MyBatis 하이브리드 §15.4)</li>
 *   <li>{@code reviewStatus} — 검수 결과 (ReviewStatus enum, 기본값: PENDING)</li>
 *   <li>{@code reviewerComment} — 검수 의견 (TEXT, nullable)</li>
 *   <li>{@code reviewedAt} — 검수 완료 시각 (DATETIME, nullable)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — 퀴즈 파이프라인 5테이블 중 검수 이력 테이블</li>
 * </ul>
 */
@Entity
@Table(name = "quiz_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리 */
public class QuizReview extends BaseAuditEntity {

    /**
     * 검수 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_review_id")
    private Long quizReviewId;

    /**
     * 검수 대상 퀴즈 (FK → quizzes.quiz_id, LAZY, 필수).
     * LAZY 로딩으로 퀴즈 목록 조회 시 N+1 문제를 방지한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /**
     * 검수 담당 관리자 ID — admin.admin_id를 Long으로 직접 참조한다.
     *
     * <p>자동화 검수나 배정 전 상태이면 null이 될 수 있다.
     * admin 테이블의 쓰기 소유는 김민규(MyBatis user 도메인)이므로 JPA @ManyToOne 매핑을
     * 두지 않고 Long FK로만 보관한다 (설계서 §15.4). Quiz 참조는 같은 roadmap 도메인이므로
     * @ManyToOne 유지한다.</p>
     */
    @Column(name = "admin_id")
    private Long adminId;

    /**
     * 검수 결과 상태 (ReviewStatus enum, 기본값: PENDING).
     * 검수가 완료되면 APPROVED 또는 REJECTED로 변경된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20, nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    /**
     * 검수 의견 (TEXT, nullable).
     * 관리자가 승인/반려 사유를 작성하는 자유 텍스트 필드이다.
     * 반려 시 필수 작성을 서비스 레이어에서 강제하는 것을 권장한다.
     */
    @Column(name = "reviewer_comment", columnDefinition = "TEXT")
    private String reviewerComment;

    /**
     * 검수 완료 시각 (DATETIME, nullable).
     * 검수가 실제로 완료(APPROVED/REJECTED)된 시각을 기록한다.
     * BaseAuditEntity의 created_at(레코드 생성 시각)과 별도로 관리된다.
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 퀴즈를 승인 처리한다.
     * reviewStatus를 APPROVED로 변경하고 reviewedAt을 현재 시각으로 설정한다.
     *
     * @param comment 승인 의견 (nullable)
     */
    public void approve(String comment) {
        this.reviewStatus = ReviewStatus.APPROVED;
        this.reviewerComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * 퀴즈를 반려 처리한다.
     * reviewStatus를 REJECTED로 변경하고 reviewedAt을 현재 시각으로 설정한다.
     *
     * @param comment 반려 사유 (관리자 UI에서 필수 입력 권장)
     */
    public void reject(String comment) {
        this.reviewStatus = ReviewStatus.REJECTED;
        this.reviewerComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * 검수 결과 상태 열거형.
     *
     * <ul>
     *   <li>{@code PENDING} — 검수 배정 후 결과 대기 중 (초기값)</li>
     *   <li>{@code APPROVED} — 검수 통과, Quiz.status → APPROVED로 연동</li>
     *   <li>{@code REJECTED} — 검수 탈락, Quiz.status → REJECTED로 연동</li>
     * </ul>
     */
    public enum ReviewStatus {
        /** 검수 결과 대기 중 */
        PENDING,
        /** 승인 완료 */
        APPROVED,
        /** 반려 처리 */
        REJECTED
    }
}
