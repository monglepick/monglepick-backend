package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 퀴즈 리포지토리 — quizzes 테이블 접근.
 *
 * <p>영화별 PUBLISHED 퀴즈 조회, 날짜별 퀴즈 조회, 상태별 전체 퀴즈 조회를 지원한다.</p>
 *
 * <h3>사용 목적별 메서드</h3>
 * <ul>
 *   <li>{@link #findByMovieIdAndStatus} — 영화 상세 페이지에서 해당 영화의 출제 퀴즈 목록 표시</li>
 *   <li>{@link #findByStatus}           — 관리자 검수 대기 목록 또는 전체 PUBLISHED 퀴즈 조회</li>
 *   <li>{@link #findByQuizDateAndStatus} — 오늘 날짜 기준 데일리 퀴즈 조회</li>
 * </ul>
 *
 * <h3>설계 참조</h3>
 * <p>docs/리워드_결제_설계서.md §14 — 퀴즈 파이프라인 (AI생성 → 관리자검수 → 참여 → 리워드)</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07: 신규 생성 — QuizService 구현을 위해 필요한 쿼리 메서드 추가</li>
 * </ul>
 */
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * 특정 영화의 특정 상태 퀴즈 목록을 조회한다.
     *
     * <p>영화 상세 페이지에서 해당 영화에 대한 PUBLISHED 퀴즈 목록을 표시할 때 사용한다.
     * movieId가 null인 일반 퀴즈는 조회되지 않는다.</p>
     *
     * @param movieId 퀴즈 대상 영화 ID (VARCHAR(50))
     * @param status  조회할 퀴즈 상태 (보통 PUBLISHED)
     * @return 해당 영화의 지정 상태 퀴즈 목록 (createdAt ASC 정렬은 서비스 레이어에서 처리)
     */
    List<Quiz> findByMovieIdAndStatus(String movieId, Quiz.QuizStatus status);

    /**
     * 특정 상태의 전체 퀴즈 목록을 조회한다.
     *
     * <p>PUBLISHED 상태 퀴즈 전체 조회나 관리자 PENDING 검수 목록 조회에 사용한다.</p>
     *
     * @param status 조회할 퀴즈 상태
     * @return 해당 상태의 퀴즈 목록
     */
    List<Quiz> findByStatus(Quiz.QuizStatus status);

    /**
     * 특정 날짜 및 상태의 퀴즈 목록을 조회한다.
     *
     * <p>오늘의 퀴즈(데일리 퀴즈) 기능에서 quizDate = 오늘, status = PUBLISHED 조건으로
     * 당일 출제된 퀴즈만 필터링할 때 사용한다.</p>
     *
     * @param quizDate 퀴즈 출제 예정일 (DATE)
     * @param status   조회할 퀴즈 상태 (보통 PUBLISHED)
     * @return 해당 날짜의 지정 상태 퀴즈 목록
     */
    List<Quiz> findByQuizDateAndStatus(LocalDate quizDate, Quiz.QuizStatus status);
}
