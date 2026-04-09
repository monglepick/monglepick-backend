package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * 관리자 전용 퀴즈 리포지토리.
 *
 * <p>관리자 페이지 "AI 운영 → 퀴즈" 탭에서 상태별·전체 퀴즈 목록을 페이징 조회하기 위한
 * 쿼리 메서드를 제공한다. 도메인 레이어의 {@code QuizRepository}는 리스트 기반 조회에
 * 특화되어 있으므로 페이징 검색은 별도 리포지토리로 분리한다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByStatusOrderByCreatedAtDesc} — 상태별 퀴즈 최신순 페이징 (레거시)</li>
 *   <li>{@link #findAllByOrderByCreatedAtDesc} — 전체 퀴즈 최신순 페이징 (레거시)</li>
 *   <li>{@link #searchByFilters} — 복합 필터 검색 (2026-04-09 P1-⑫ 신규)</li>
 * </ul>
 */
public interface AdminQuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * 특정 상태의 퀴즈 목록을 최신순으로 페이징 조회한다.
     *
     * @param status   퀴즈 상태 (PENDING / APPROVED / REJECTED / PUBLISHED)
     * @param pageable 페이지 정보
     * @return 해당 상태의 퀴즈 페이지
     */
    Page<Quiz> findByStatusOrderByCreatedAtDesc(Quiz.QuizStatus status, Pageable pageable);

    /**
     * 전체 퀴즈 목록을 최신순으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 전체 퀴즈 페이지
     */
    Page<Quiz> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 퀴즈 복합 필터 검색 — 2026-04-09 P1-⑫ 신규.
     *
     * <p>기존의 {@link #findByStatusOrderByCreatedAtDesc} 는 상태 한 가지 필터만
     * 지원하여 "특정 영화의 퀴즈", "본문 키워드 포함", "특정 기간 출제 예정" 같은
     * 실제 운영 검색이 불가능했다. 이 메서드는 status / movieId / keyword / 시간 범위
     * 를 모두 optional 파라미터로 받아 DB 전체를 대상으로 한 번의 쿼리로 처리한다.</p>
     *
     * <h3>이전 MVP 와의 관계</h3>
     * <p>2026-04-09 초에 Frontend `QuizManagementTab.jsx` 에 클라이언트 측 필터
     * (`filteredQuizzes`)가 MVP 로 추가되었으나, 현재 페이지(10건) 내에서만 작동하는
     * 제약이 있었다. 본 Repository 확장으로 Frontend 는 Backend 에 파라미터만 전달하면
     * 전역 검색이 가능해지며, 클라이언트 필터 로직은 단계적으로 제거된다.</p>
     *
     * <h3>파라미터 규칙</h3>
     * <ul>
     *   <li>모든 파라미터는 {@code null} 허용 — null 이면 해당 조건 무시.</li>
     *   <li>{@code status}: 정확 일치 (enum 타입)</li>
     *   <li>{@code movieId}: 부분 일치 (대소문자 무시) — 기존 클라이언트 필터와 동일 패턴</li>
     *   <li>{@code keyword}: question/explanation/correctAnswer 중 어느 하나에 부분 일치 (OR 조건, 대소문자 무시)</li>
     *   <li>{@code fromDate}: quizDate 이상 (inclusive)</li>
     *   <li>{@code toDate}: quizDate 이하 (inclusive) — 날짜 필드라 reviewed inclusive 가 직관적</li>
     * </ul>
     *
     * <h3>정렬</h3>
     * <p>JPQL 에 {@code ORDER BY q.createdAt DESC} 를 하드코딩. Pageable sort 파라미터는
     * 무시된다. 퀴즈 이력은 항상 최신 생성순이 기본.</p>
     *
     * <h3>성능</h3>
     * <p>{@code quiz} 테이블 규모가 수천~수만 건으로 제한적이라 LIKE 부분 일치로도 수용 가능.
     * 추후 규모가 커지면 {@code quiz_date}/{@code movie_id}/{@code status} 인덱스 추가 고려.</p>
     *
     * @param status   퀴즈 상태 (nullable)
     * @param movieId  영화 ID 부분 일치 키워드 (nullable)
     * @param keyword  문제/해설/정답 본문 키워드 (nullable)
     * @param fromDate quizDate 시작 (inclusive, nullable)
     * @param toDate   quizDate 종료 (inclusive, nullable)
     * @param pageable 페이지 정보
     * @return 필터링된 퀴즈 페이지 (createdAt DESC)
     */
    @Query(
        "SELECT q FROM Quiz q WHERE " +
        "(:status   IS NULL OR q.status = :status) AND " +
        "(:movieId  IS NULL OR LOWER(q.movieId) LIKE LOWER(CONCAT('%', :movieId, '%'))) AND " +
        "(:keyword  IS NULL OR LOWER(q.question)      LIKE LOWER(CONCAT('%', :keyword, '%')) " +
        "                   OR LOWER(q.explanation)   LIKE LOWER(CONCAT('%', :keyword, '%')) " +
        "                   OR LOWER(q.correctAnswer) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
        "(:fromDate IS NULL OR q.quizDate >= :fromDate) AND " +
        "(:toDate   IS NULL OR q.quizDate <= :toDate) " +
        "ORDER BY q.createdAt DESC"
    )
    Page<Quiz> searchByFilters(
            @Param("status") Quiz.QuizStatus status,
            @Param("movieId") String movieId,
            @Param("keyword") String keyword,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
