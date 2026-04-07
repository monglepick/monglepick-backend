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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 퀴즈 참여 엔티티 — quiz_participations 테이블 매핑.
 *
 * <p>사용자가 특정 퀴즈에 참여하여 제출한 답변과 채점 결과를 저장한다.
 * 동일 사용자가 동일 퀴즈에 중복 참여하는 것을 방지하기 위해
 * {@code (quiz_id, user_id)} 복합 유니크 제약을 적용한다.</p>
 *
 * <h3>파이프라인 역할</h3>
 * <ol>
 *   <li>PUBLISHED 상태의 퀴즈에 사용자가 답변 제출</li>
 *   <li>이 테이블에 참여 레코드 INSERT (is_correct 채점 포함)</li>
 *   <li>정답(is_correct = true) 시 QuizReward 레코드 생성 + 포인트 지급</li>
 * </ol>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code quiz} — 참여한 퀴즈 (FK → quizzes.quiz_id, LAZY)</li>
 *   <li>{@code userId} — 참여 사용자 ID (String FK → users.user_id, JPA/MyBatis 하이브리드 §15.4)</li>
 *   <li>{@code selectedOption} — 사용자가 선택한 답 (VARCHAR(500), nullable)</li>
 *   <li>{@code isCorrect} — 정답 여부 (채점 결과, nullable)</li>
 *   <li>{@code submittedAt} — 답변 제출 시각 (DATETIME)</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(quiz_id, user_id) — 동일 퀴즈에 동일 사용자의 중복 참여 불가.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — 퀴즈 파이프라인 5테이블 중 참여 이력 테이블</li>
 * </ul>
 */
@Entity
@Table(
        name = "quiz_participations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quiz_user",
                columnNames = {"quiz_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리 */
public class QuizParticipation extends BaseAuditEntity {

    /**
     * 퀴즈 참여 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_participation_id")
    private Long quizParticipationId;

    /**
     * 참여한 퀴즈 (FK → quizzes.quiz_id, LAZY, 필수).
     * LAZY 로딩으로 참여 목록 조회 시 N+1 문제를 방지한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /**
     * 참여 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>quiz_id와 함께 UNIQUE 제약을 구성하여 중복 참여를 방지한다.
     * users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). Quiz 참조는 같은 roadmap 도메인이므로
     * @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 사용자가 선택한 답 (VARCHAR(500), nullable).
     * 객관식의 경우 선택지 텍스트, 주관식의 경우 입력 텍스트가 저장된다.
     * 제출 전 임시 저장 상태이면 null일 수 있다.
     */
    @Column(name = "selected_option", length = 500)
    private String selectedOption;

    /**
     * 정답 여부 (BOOLEAN, nullable).
     * 채점 완료 후 true(정답)/false(오답)가 설정된다.
     * 채점 전(미제출) 상태이면 null이다.
     */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    /**
     * 답변 제출 시각 (DATETIME, nullable).
     * 사용자가 최종 답변을 제출한 시각이다.
     * BaseAuditEntity의 created_at(레코드 생성 시각)과 별도로 관리된다.
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 답변을 제출하고 채점 결과를 기록한다.
     * 서비스 레이어에서 채점 후 호출한다.
     *
     * @param selectedOption 사용자가 선택한 답 텍스트
     * @param isCorrect      채점 결과 (true: 정답, false: 오답)
     */
    public void submit(String selectedOption, Boolean isCorrect) {
        this.selectedOption = selectedOption;
        this.isCorrect = isCorrect;
        this.submittedAt = LocalDateTime.now();
    }
}
