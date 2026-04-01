package com.monglepick.monglepickbackend.domain.content.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비속어·유해 콘텐츠 검출 로그 엔티티 — toxicity_logs 테이블 매핑.
 *
 * <p>게시글·댓글·리뷰·채팅 등 다양한 콘텐츠 유형에서 AI가 감지한
 * 유해 표현(비속어, 혐오 발언, 성적 콘텐츠 등)을 기록한다.
 * 관리자 페이지 "콘텐츠 관리 > 혐오표현 관리" 탭에서 조회 및 조치한다.</p>
 *
 * <h3>v5 변경 이력</h3>
 * <ul>
 *   <li>v4: 채팅 전용 구조 (sessionId, inputText, toxicityType) — toxicity_log 테이블</li>
 *   <li>v5: 커뮤니티/리뷰/채팅 통합 구조 (contentType, contentId, detectedWords, severity)
 *       — 테이블명 toxicity_log → toxicity_logs 변경, 컬럼 전면 재설계</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code contentType}   — 감지된 콘텐츠 유형 (POST/COMMENT/REVIEW/CHAT)</li>
 *   <li>{@code contentId}     — 감지된 콘텐츠 레코드 ID (해당 테이블의 PK)</li>
 *   <li>{@code userId}        — 콘텐츠 작성자 사용자 ID (익명이면 nullable)</li>
 *   <li>{@code detectedWords} — AI가 감지한 유해 단어 목록 (JSON 배열)</li>
 *   <li>{@code toxicityScore} — AI 유해성 점수 (0.0~1.0)</li>
 *   <li>{@code severity}      — 심각도 (LOW/MEDIUM/HIGH/CRITICAL, 기본값: "LOW")</li>
 *   <li>{@code actionTaken}   — 취해진 조치 (NONE/WARN/BLIND/DELETE, nullable)</li>
 *   <li>{@code processedAt}   — 조치 처리 시각 (nullable, 미처리 시 null)</li>
 * </ul>
 *
 * <h3>심각도 기준 (severity)</h3>
 * <ul>
 *   <li>{@code LOW}      — 경미한 비속어 (toxicityScore 0.0~0.3)</li>
 *   <li>{@code MEDIUM}   — 중간 수준 (toxicityScore 0.3~0.6)</li>
 *   <li>{@code HIGH}     — 심각한 혐오·폭력 (toxicityScore 0.6~0.8)</li>
 *   <li>{@code CRITICAL} — 즉시 조치 필요 (toxicityScore 0.8~1.0)</li>
 * </ul>
 *
 * <h3>조치 유형 (actionTaken)</h3>
 * <ul>
 *   <li>{@code NONE}   — 로그만 기록, 조치 없음</li>
 *   <li>{@code WARN}   — 사용자 경고 발송</li>
 *   <li>{@code BLIND}  — 콘텐츠 블라인드 처리</li>
 *   <li>{@code DELETE} — 콘텐츠 삭제</li>
 * </ul>
 *
 * <h3>인덱스 전략</h3>
 * <ul>
 *   <li>idx_toxicity_content  — contentType+contentId 복합 인덱스 (특정 콘텐츠 로그 조회)</li>
 *   <li>idx_toxicity_severity — severity 단일 인덱스 (관리자 심각도별 필터 조회)</li>
 * </ul>
 */
@Entity
@Table(
        name = "toxicity_logs",
        indexes = {
                /* 특정 콘텐츠(타입+ID)에 대한 감지 로그 조회 최적화 */
                @Index(name = "idx_toxicity_content", columnList = "content_type, content_id"),
                /* 관리자 심각도별 필터 조회 최적화 */
                @Index(name = "idx_toxicity_severity", columnList = "severity")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class ToxicityLog extends BaseAuditEntity {

    /**
     * 유해성 로그 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "toxicity_log_id")
    private Long toxicityLogId;

    /**
     * 감지된 콘텐츠 유형 (VARCHAR(30), NOT NULL).
     * <ul>
     *   <li>"POST"    — 커뮤니티 게시글</li>
     *   <li>"COMMENT" — 게시글 댓글</li>
     *   <li>"REVIEW"  — 영화 리뷰</li>
     *   <li>"CHAT"    — AI 채팅 입력</li>
     * </ul>
     */
    @Column(name = "content_type", length = 30, nullable = false)
    private String contentType;

    /**
     * 감지된 콘텐츠 레코드 ID (BIGINT, NOT NULL).
     * contentType에 따라 각 테이블의 PK를 참조한다.
     * <ul>
     *   <li>POST    → posts.id</li>
     *   <li>COMMENT → post_comment.post_comment_id</li>
     *   <li>REVIEW  → reviews.review_id</li>
     *   <li>CHAT    → 해당 채팅 메시지 순번 (세션 내 식별자)</li>
     * </ul>
     * FK는 @Column으로만 선언 (다형성 참조로 단일 FK 제약 불가).
     */
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    /**
     * 콘텐츠 작성자 사용자 ID (VARCHAR(50), nullable).
     * 익명 접근이거나 시스템 자동 감지인 경우 null일 수 있다.
     */
    @Column(name = "user_id", length = 50)
    private String userId;

    /**
     * AI가 감지한 유해 단어 목록 (JSON 배열, nullable).
     * 예: ["씨발", "개새끼"]
     * AI 분석이 단어 단위 추출을 지원하지 않으면 null로 저장된다.
     */
    @Column(name = "detected_words", columnDefinition = "JSON")
    private String detectedWords;

    /**
     * AI 유해성 점수 (nullable, 0.0~1.0).
     * 0.0: 안전, 1.0: 매우 유해.
     * AI 분석 실패 시 null이 저장될 수 있다.
     */
    @Column(name = "toxicity_score")
    private Float toxicityScore;

    /**
     * 심각도 레벨 (VARCHAR(20), 기본값: "LOW").
     * toxicityScore 구간에 따라 자동 분류된다:
     * <ul>
     *   <li>"LOW"      — 0.0~0.3 (경미)</li>
     *   <li>"MEDIUM"   — 0.3~0.6 (중간)</li>
     *   <li>"HIGH"     — 0.6~0.8 (심각)</li>
     *   <li>"CRITICAL" — 0.8~1.0 (즉시 조치)</li>
     * </ul>
     */
    @Column(name = "severity", length = 20)
    @Builder.Default
    private String severity = "LOW";

    /**
     * 취해진 조치 (VARCHAR(30), nullable).
     * 미처리 상태에서는 null이며, 관리자 조치 후 값이 설정된다.
     * <ul>
     *   <li>"NONE"   — 로그만 기록, 별도 조치 없음</li>
     *   <li>"WARN"   — 작성자에게 경고 알림 발송</li>
     *   <li>"BLIND"  — 콘텐츠 블라인드(숨김) 처리</li>
     *   <li>"DELETE" — 콘텐츠 완전 삭제</li>
     * </ul>
     */
    @Column(name = "action_taken", length = 30)
    private String actionTaken;

    /**
     * 조치 처리 시각 (nullable).
     * 미처리 상태(actionTaken=null)에서는 null이며,
     * 관리자가 조치를 완료할 때 현재 시각이 기록된다.
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 관리자 조치를 기록한다.
     *
     * <p>actionTaken 값을 설정하고 processedAt을 현재 시각으로 기록한다.
     * 유효한 값: "NONE", "WARN", "BLIND", "DELETE"</p>
     *
     * @param action 취할 조치 코드 (서비스 레이어에서 유효값 검증 필요)
     */
    public void processAction(String action) {
        this.actionTaken = action;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 해당 로그가 아직 미처리 상태인지 반환한다.
     *
     * @return true이면 미처리(actionTaken=null), false이면 조치 완료
     */
    public boolean isPending() {
        return this.actionTaken == null;
    }

    /**
     * 즉시 조치가 필요한 CRITICAL 심각도인지 반환한다.
     *
     * @return true이면 CRITICAL 심각도
     */
    public boolean isCritical() {
        return "CRITICAL".equals(this.severity);
    }
}
