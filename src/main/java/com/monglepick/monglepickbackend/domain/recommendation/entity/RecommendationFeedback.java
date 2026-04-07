package com.monglepick.monglepickbackend.domain.recommendation.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 피드백 엔티티 — recommendation_feedback 테이블 매핑.
 *
 * <p>사용자가 AI 추천 결과에 대해 남긴 피드백을 저장한다.
 * 추천 품질 개선 및 사용자 선호도 학습에 활용된다.
 * 한 사용자는 하나의 추천에 대해 하나의 피드백만 남길 수 있다
 * (user_id, recommendation_id UNIQUE 제약).</p>
 *
 * <h3>피드백 유형 (FeedbackType)</h3>
 * <ul>
 *   <li>{@code like} — 좋아요 (추천이 마음에 들었음)</li>
 *   <li>{@code dislike} — 싫어요 (추천이 마음에 들지 않았음)</li>
 *   <li>{@code watched} — 시청함 (추천을 보고 실제로 시청)</li>
 *   <li>{@code not_interested} — 관심 없음 (해당 영화에 관심이 없음)</li>
 * </ul>
 */
@Entity
@Table(name = "recommendation_feedback", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_user_rec", columnNames = {"user_id", "recommendation_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — PK 필드명: id → recommendationFeedbackId로 변경 (DDL 컬럼명 recommendation_feedback_id 매핑)
 * — 수동 @CreationTimestamp created_at 필드 제거됨
 */
public class RecommendationFeedback extends BaseAuditEntity {

    /** 피드백 고유 ID (PK, BIGINT AUTO_INCREMENT, 컬럼명: recommendation_feedback_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_feedback_id")
    private Long recommendationFeedbackId;

    /**
     * 피드백을 남긴 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). RecommendationLog 참조는 같은 recommendation
     * 도메인이므로 @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 피드백 대상 추천 로그.
     * recommendation_feedback.recommendation_id → recommendation_log.id FK (ON DELETE CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private RecommendationLog recommendationLog;

    /**
     * 피드백 유형.
     * ENUM('like', 'dislike', 'watched', 'not_interested') 중 하나.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    /** 피드백 코멘트 (선택, 자유 텍스트) */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /* created_at은 BaseAuditEntity(→BaseTimeEntity)에서 자동 관리 — 수동 @CreationTimestamp 필드 제거됨 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 기존 피드백의 유형과 코멘트를 갱신한다 (UPSERT 처리용).
     *
     * <p>동일 사용자가 같은 추천에 대해 피드백을 재제출하면
     * 새 레코드를 생성하는 대신 이 메서드로 기존 값을 덮어쓴다.
     * (user_id, recommendation_id) UNIQUE 제약을 애플리케이션 레벨에서 처리한다.</p>
     *
     * @param feedbackType 새 피드백 유형
     * @param comment      새 피드백 코멘트 (null 허용)
     */
    public void update(FeedbackType feedbackType, String comment) {
        this.feedbackType = feedbackType;
        this.comment = comment;
    }

    /**
     * 추천 피드백 유형 열거형.
     *
     * <p>MySQL ENUM('like','dislike','watched','not_interested')에 매핑된다.</p>
     */
    public enum FeedbackType {
        /** 좋아요 — 추천이 마음에 들었음 */
        like,
        /** 싫어요 — 추천이 마음에 들지 않았음 */
        dislike,
        /** 시청함 — 추천을 보고 실제로 시청 */
        watched,
        /** 관심 없음 — 해당 영화에 관심이 없음 */
        not_interested
    }
}
