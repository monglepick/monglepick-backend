package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
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

import java.time.LocalDateTime;

/**
 * 퀴즈 시도 엔티티 — quiz_attempts 테이블 매핑.
 *
 * <p>도장깨기 코스에서 사용자가 영화 관련 퀴즈에 도전한 기록을 저장한다.
 * 각 시도마다 문제, 사용자 답변, 정답, 정오 여부, 점수를 기록한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseAuditEntity 상속 추가 (created_at/updated_at/created_by/updated_by 자동 관리)</li>
 *   <li>2026-03-24: PK 필드명 id → quizAttemptId 로 변경, @Column(name = "quiz_attempt_id") 추가</li>
 *   <li>2026-03-24: @CreationTimestamp import 및 attemptedAt의 @CreationTimestamp 제거 — 도메인 타임스탬프로 유지</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 퀴즈 도전자 ID (String FK → users.user_id, JPA/MyBatis 하이브리드 §15.4)</li>
 *   <li>{@code courseId} — 코스 식별자 (roadmap_courses.course_id와 매핑)</li>
 *   <li>{@code movie} — 퀴즈 대상 영화 (FK → movies.movie_id)</li>
 *   <li>{@code question} — 퀴즈 문제 (TEXT)</li>
 *   <li>{@code userAnswer} — 사용자 답변 (TEXT)</li>
 *   <li>{@code correctAnswer} — 정답 (TEXT)</li>
 *   <li>{@code isCorrect} — 정답 여부 (필수)</li>
 *   <li>{@code score} — 획득 점수 (기본값: 0)</li>
 *   <li>{@code attemptedAt} — 퀴즈 시도 시각 (도메인 고유 타임스탬프, BaseAuditEntity의 created_at과 별도)</li>
 * </ul>
 */
@Entity
@Table(name = "quiz_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 컬럼 자동 관리 */
public class QuizAttempt extends BaseAuditEntity {

    /**
     * 퀴즈 시도 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 기존 필드명 'id'에서 'quizAttemptId'로 변경하여 엔티티 식별 명확화.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_attempt_id")
    private Long quizAttemptId;

    /**
     * 퀴즈 도전 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). Movie 참조는 backend가 movies 테이블의
     * DDL 마스터이므로 그대로 @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 코스 식별자.
     * roadmap_courses.course_id 값과 매핑되는 문자열.
     * DDL상 VARCHAR(50)이며 FK 제약은 없지만 논리적으로 연결된다.
     */
    @Column(name = "course_id", length = 50, nullable = false)
    private String courseId;

    /**
     * 퀴즈 대상 영화.
     * quiz_attempts.movie_id → movies.movie_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    /** 퀴즈 문제 (TEXT, 필수) */
    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    /** 사용자가 입력한 답변 (TEXT, 필수) */
    @Column(name = "user_answer", columnDefinition = "TEXT", nullable = false)
    private String userAnswer;

    /** 정답 (TEXT, 필수) */
    @Column(name = "correct_answer", columnDefinition = "TEXT", nullable = false)
    private String correctAnswer;

    /** 정답 여부 (필수) */
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    /** 획득 점수 (기본값: 0) */
    @Column(name = "score")
    @Builder.Default
    private Integer score = 0;

    /**
     * 퀴즈 시도 시각 (도메인 고유 타임스탬프).
     * BaseAuditEntity의 created_at과는 별도로, 퀴즈 시도 시점을 기록하는 도메인 필드.
     * 서비스 레이어에서 직접 설정한다.
     */
    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;
}
