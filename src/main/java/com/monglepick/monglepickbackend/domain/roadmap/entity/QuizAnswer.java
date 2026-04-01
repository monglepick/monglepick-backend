package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 퀴즈 선택지 엔티티 — quiz_answers 테이블 매핑.
 *
 * <p>퀴즈({@link Quiz})에 속하는 개별 선택지(보기)를 저장한다.
 * 퀴즈 하나에 여러 선택지가 존재하며(1:N), 그 중 하나 이상이 정답일 수 있다.
 * {@code sortOrder} 필드를 통해 선택지의 표시 순서를 제어한다.</p>
 *
 * <p>Quiz 엔티티의 {@code options} JSON 필드와는 별도로 관리되는 정규화된 선택지 테이블이다.
 * 관리자 검수 UI나 정답 채점 로직에서 이 테이블을 우선적으로 사용한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code quiz} — 소속 퀴즈 (FK → quizzes.quiz_id, LAZY)</li>
 *   <li>{@code answerText} — 선택지 내용 (VARCHAR(500), 필수)</li>
 *   <li>{@code isCorrect} — 정답 여부 (기본값: false)</li>
 *   <li>{@code sortOrder} — 선택지 표시 순서 (기본값: 0, 낮을수록 먼저 표시)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — 퀴즈 파이프라인 5테이블 중 선택지 테이블</li>
 * </ul>
 */
@Entity
@Table(name = "quiz_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리 */
public class QuizAnswer extends BaseAuditEntity {

    /**
     * 퀴즈 선택지 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_answer_id")
    private Long quizAnswerId;

    /**
     * 소속 퀴즈 (FK → quizzes.quiz_id, LAZY).
     * LAZY 로딩으로 N+1 문제를 방지한다.
     * 퀴즈 삭제 시 선택지도 함께 삭제되어야 하므로
     * 서비스 레이어에서 cascading을 명시적으로 처리한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /**
     * 선택지 내용 (VARCHAR(500), 필수).
     * 사용자에게 표시될 보기 텍스트이다.
     * 예: "크리스토퍼 놀란", "스티븐 스필버그"
     */
    @Column(name = "answer_text", length = 500, nullable = false)
    private String answerText;

    /**
     * 정답 여부 (기본값: false).
     * true이면 이 선택지가 정답임을 의미한다.
     * 퀴즈 하나에 정답 선택지가 반드시 하나 이상 존재해야 한다.
     */
    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;

    /**
     * 선택지 표시 순서 (INT, 기본값: 0).
     * 낮은 값일수록 먼저 표시된다.
     * 보통 0(A), 1(B), 2(C), 3(D) 순서로 설정한다.
     */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 선택지 내용을 수정한다.
     * 관리자가 검수 과정에서 선택지 텍스트를 수정할 때 사용한다.
     *
     * @param answerText 수정할 선택지 내용
     */
    public void updateAnswerText(String answerText) {
        this.answerText = answerText;
    }

    /**
     * 정답 여부를 변경한다.
     * 관리자가 검수 과정에서 정답을 재지정할 때 사용한다.
     *
     * @param isCorrect 정답 여부
     */
    public void updateIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
}
