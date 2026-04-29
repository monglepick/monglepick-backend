package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 퀴즈 엔티티 — quizzes 테이블 매핑.
 *
 * <p>AI가 생성한 영화 관련 퀴즈를 저장한다.
 * 관리자 검수(PENDING → APPROVED/REJECTED) 후 출제(PUBLISHED) 상태로 전환되는
 * 5단계 파이프라인(AI생성 → 관리자검수 → 참여 → 리워드)의 시작점이다.</p>
 *
 * <h3>상태 전이</h3>
 * <pre>
 * PENDING → APPROVED → PUBLISHED (출제 예정일 도래 후 공개)
 *         → REJECTED             (검수 탈락)
 * </pre>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code movieId} — 퀴즈 대상 영화 ID (VARCHAR(50), movies.movie_id 참조)</li>
 *   <li>{@code question} — 퀴즈 문제 (TEXT, 필수)</li>
 *   <li>{@code explanation} — 정답 해설 (TEXT, nullable)</li>
 *   <li>{@code correctAnswer} — 정답 문자열 (VARCHAR(500))</li>
 *   <li>{@code options} — 선택지 배열 JSON (예: ["A지문", "B지문", "C지문", "D지문"])</li>
 *   <li>{@code rewardPoint} — 정답 시 지급할 보상 포인트 (기본값: 10)</li>
 *   <li>{@code status} — 퀴즈 상태 (QuizStatus enum)</li>
 *   <li>{@code quizDate} — 출제 예정일 (DATE)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — 퀴즈 파이프라인 5테이블 중 메인 테이블</li>
 * </ul>
 */
@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리 */
public class Quiz extends BaseAuditEntity {

    /**
     * 퀴즈 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 서로게이트 PK로, 자동 증가한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_id")
    private Long quizId;

    /**
     * 퀴즈 대상 영화 ID (VARCHAR(50), nullable).
     * movies.movie_id를 논리적으로 참조한다.
     * null이면 특정 영화에 속하지 않는 일반 퀴즈임을 의미한다.
     */
    @Column(name = "movie_id", length = 50)
    private String movieId;

    /**
     * 퀴즈 문제 텍스트 (TEXT, 필수).
     * AI가 생성하거나 관리자가 직접 입력한 문제 내용이다.
     */
    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    /**
     * 정답 해설 (TEXT, nullable).
     * 정답을 선택한 후 사용자에게 보여줄 해설이다.
     * null이면 해설 없이 정답만 공개된다.
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    /**
     * 정답 문자열 (VARCHAR(500), nullable).
     * 예: "A지문 내용" 또는 단순 "A" 등 정답을 특정하는 문자열이다.
     */
    @Column(name = "correct_answer", length = 500)
    private String correctAnswer;

    /**
     * 선택지 배열 (JSON, nullable).
     * 객관식 선택지를 JSON 배열 문자열로 저장한다.
     * 예: ["영화 A", "영화 B", "영화 C", "영화 D"]
     * 주관식 퀴즈의 경우 null이 될 수 있다.
     */
    @Column(name = "options", columnDefinition = "json")
    private String options;

    /**
     * 보상 포인트 (INT, 기본값: 10).
     * 사용자가 퀴즈를 정답으로 풀었을 때 지급되는 포인트 양이다.
     */
    @Column(name = "reward_point")
    @Builder.Default
    private Integer rewardPoint = 10;

    /**
     * 퀴즈 상태 (QuizStatus enum, 기본값: PENDING).
     * 상태 전이: PENDING → APPROVED → PUBLISHED 또는 REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private QuizStatus status = QuizStatus.PENDING;

    /**
     * 출제 예정일 (DATE, nullable).
     * 특정 날짜에 퀴즈를 출제하도록 스케줄링하는 용도이다.
     * null이면 즉시 출제 가능 상태이다.
     */
    @Column(name = "quiz_date")
    private LocalDate quizDate;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 퀴즈 상태를 APPROVED로 변경한다.
     * 관리자가 검수를 통과시킬 때 호출한다.
     */
    public void approve() {
        this.status = QuizStatus.APPROVED;
    }

    /**
     * 퀴즈 상태를 REJECTED로 변경한다.
     * 관리자가 검수에서 탈락시킬 때 호출한다.
     */
    public void reject() {
        this.status = QuizStatus.REJECTED;
    }

    /**
     * 퀴즈 상태를 PUBLISHED로 변경한다.
     *
     * @deprecated <b>도메인 invariant 위반 위험</b>. 본 메서드는 status 만 PUBLISHED 로 변경하고
     * {@code quiz_date} 는 건드리지 않아, 호출자가 별도로 quiz_date 를 채우지 않으면 사용자 화면
     * {@code GET /api/v1/quizzes/today} (조건: quiz_date = 오늘 AND status = PUBLISHED) 에 영원히
     * 노출되지 않는 disconnect 를 만든다. 2026-04-29 운영 회귀의 근본 원인.
     *
     * <p>대신 {@link #publishOn(LocalDate)} 을 사용하여 status 와 quiz_date 를 한 번에 atomic
     * 하게 세팅하라. 호환을 위해 본 메서드는 남겨두지만 신규 호출처 추가 금지.</p>
     */
    @Deprecated
    public void publish() {
        this.status = QuizStatus.PUBLISHED;
    }

    /**
     * 퀴즈를 특정 날짜로 출제(PUBLISH) — 2026-04-29 신규.
     *
     * <p>{@code QuizPublishScheduler} 가 매일 00:00 KST 에 호출하여 status 와
     * quiz_date 를 한 번의 영속성 컨텍스트 변경으로 atomic 하게 세팅한다.
     * 운영자가 수동으로 같은 날짜에 출제할 때도 사용 가능하다.</p>
     *
     * @param date 출제 예정일 (필수, null 불가)
     */
    public void publishOn(LocalDate date) {
        this.status = QuizStatus.PUBLISHED;
        this.quizDate = date;
    }

    /**
     * 관리자 퀴즈 본문/메타 정보를 수정한다.
     *
     * <p>상태(status)는 본 메서드에서 변경하지 않는다.
     * 상태 전이는 {@link #approve()}, {@link #reject()}, {@link #publish()}로 별도 처리한다.</p>
     *
     * @param movieId       대상 영화 ID (nullable)
     * @param question      문제 본문
     * @param explanation   해설 (nullable)
     * @param correctAnswer 정답 문자열
     * @param options       선택지 JSON 문자열 (nullable — 주관식)
     * @param rewardPoint   보상 포인트
     * @param quizDate      출제 예정일 (nullable)
     */
    public void updateInfo(String movieId, String question, String explanation,
                           String correctAnswer, String options, Integer rewardPoint,
                           LocalDate quizDate) {
        this.movieId = movieId;
        this.question = question;
        this.explanation = explanation;
        this.correctAnswer = correctAnswer;
        this.options = options;
        this.rewardPoint = rewardPoint != null ? rewardPoint : this.rewardPoint;
        this.quizDate = quizDate;
    }

    /**
     * 퀴즈 상태 열거형.
     *
     * <p>퀴즈 파이프라인의 진행 단계를 나타낸다.</p>
     * <ul>
     *   <li>{@code PENDING} — AI 생성 후 관리자 검수 대기 중 (초기값)</li>
     *   <li>{@code APPROVED} — 관리자 검수 통과, 출제 대기 중</li>
     *   <li>{@code REJECTED} — 관리자 검수 탈락, 출제 불가</li>
     *   <li>{@code PUBLISHED} — 실제 출제되어 사용자에게 노출 중</li>
     * </ul>
     */
    public enum QuizStatus {
        /** 관리자 검수 대기 중 (AI 생성 직후 초기 상태) */
        PENDING,
        /** 관리자 검수 통과, 출제 예약 완료 */
        APPROVED,
        /** 관리자 검수 탈락, 출제 불가 처리 */
        REJECTED,
        /** 실제 출제 중, 사용자 참여 가능 */
        PUBLISHED
    }
}
