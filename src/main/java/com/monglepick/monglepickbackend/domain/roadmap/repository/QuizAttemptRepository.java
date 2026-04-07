package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 퀴즈 시도 리포지토리 — quiz_attempts 테이블 접근.
 *
 * <p>사용자가 특정 퀴즈에 제출한 시도 이력을 관리한다.
 * 주된 용도는 리워드 중복 지급 방지 — 동일 (userId, quizId) 조합에서
 * 이미 정답을 맞춘 기록이 있으면 재지급을 건너뛴다.</p>
 *
 * <h3>QuizAttempt vs QuizParticipation 구분</h3>
 * <ul>
 *   <li>{@code QuizAttempt} — 도장깨기 코스 내 영화 퀴즈 시도 (courseId, Movie FK 포함)</li>
 *   <li>{@code QuizParticipation} — 일반/데일리 퀴즈 참여 (Quiz FK, 유니크 제약)</li>
 * </ul>
 * <p>QuizService에서는 범용 퀴즈 제출에 {@code QuizParticipationRepository}를 우선 사용하며,
 * 이 레포지토리는 도장깨기 코스 연동 또는 보조 조회 목적으로 사용한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07: 신규 생성 — QuizService 구현에서 정답 이력 중복 확인 목적</li>
 * </ul>
 */
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /**
     * 특정 사용자가 특정 코스의 특정 영화에 대해 정답을 맞춘 시도가 있는지 확인한다.
     *
     * <p>동일 (userId, courseId, movieId) 조합에서 isCorrect=true인 레코드 존재 여부를 확인하여
     * 리워드 중복 지급을 방지한다.</p>
     *
     * @param userId   사용자 ID (VARCHAR(50))
     * @param courseId 코스 ID (VARCHAR(50))
     * @param movieId  영화 ID (VARCHAR(50))
     * @return isCorrect=true인 기존 시도가 있으면 true
     */
    @Query("SELECT COUNT(a) > 0 FROM QuizAttempt a " +
           "WHERE a.userId = :userId " +
           "AND a.courseId = :courseId " +
           "AND a.movie.movieId = :movieId " +
           "AND a.isCorrect = true")
    boolean existsCorrectAttempt(
            @Param("userId") String userId,
            @Param("courseId") String courseId,
            @Param("movieId") String movieId
    );

    /**
     * 특정 사용자의 특정 코스 내 가장 최근 시도를 조회한다.
     *
     * <p>시도 횟수(attemptNumber) 계산 또는 마지막 답변 확인에 사용한다.</p>
     *
     * @param userId   사용자 ID
     * @param courseId 코스 ID
     * @param movieId  영화 ID
     * @return 가장 최근 시도 (없으면 빈 Optional)
     */
    @Query("SELECT a FROM QuizAttempt a " +
           "WHERE a.userId = :userId " +
           "AND a.courseId = :courseId " +
           "AND a.movie.movieId = :movieId " +
           "ORDER BY a.attemptedAt DESC")
    Optional<QuizAttempt> findLatestAttempt(
            @Param("userId") String userId,
            @Param("courseId") String courseId,
            @Param("movieId") String movieId
    );
}
