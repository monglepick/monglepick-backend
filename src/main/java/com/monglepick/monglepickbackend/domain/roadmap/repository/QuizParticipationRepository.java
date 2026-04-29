package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizParticipation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 퀴즈 참여 리포지토리 — quiz_participations 테이블 접근.
 *
 * <p>일반/데일리 퀴즈에 사용자가 답변을 제출한 참여 이력을 관리한다.
 * (quiz_id, user_id) UNIQUE 제약으로 동일 퀴즈 중복 참여를 DB 레벨에서도 차단한다.</p>
 *
 * <h3>주요 역할</h3>
 * <ul>
 *   <li>정답 제출 전 중복 참여 여부 확인 ({@link #findByQuiz_QuizIdAndUserId})</li>
 *   <li>이미 정답을 맞춘 경우 리워드 중복 지급 방지 ({@link #existsByQuiz_QuizIdAndUserIdAndIsCorrect})</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07: 신규 생성 — QuizService.submitAnswer() 구현에서 사용</li>
 * </ul>
 */
public interface QuizParticipationRepository extends JpaRepository<QuizParticipation, Long> {

    /**
     * 특정 사용자의 특정 퀴즈 참여 기록을 조회한다.
     *
     * <p>정답 제출 요청 시 이미 참여한 기록이 있는지 확인하여
     * 중복 제출 처리 방식(재제출 허용 or 차단)을 결정할 때 사용한다.</p>
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 참여 기록 (없으면 빈 Optional)
     */
    Optional<QuizParticipation> findByQuiz_QuizIdAndUserId(Long quizId, String userId);

    /**
     * 특정 사용자가 특정 퀴즈에서 정답을 맞춘 참여 기록이 있는지 확인한다.
     *
     * <p>리워드는 최초 정답 1회만 지급한다.
     * 이미 isCorrect=true 참여 기록이 있으면 grantReward 호출을 건너뛴다.</p>
     *
     * @param quizId    퀴즈 ID
     * @param userId    사용자 ID (VARCHAR(50))
     * @param isCorrect 정답 여부 (true 고정으로 사용)
     * @return 해당 조건의 참여 기록이 있으면 true
     */
    boolean existsByQuiz_QuizIdAndUserIdAndIsCorrect(Long quizId, String userId, Boolean isCorrect);

    // ============================================================
    // 사용자별 응시 통계 — 2026-04-29 신규 (GET /api/v1/quizzes/me/stats)
    // ============================================================

    /**
     * 사용자별 응시 통계 집계 — 총응시/정답수/총획득포인트/마지막응시 시각.
     *
     * <p>QuizPage 상단 "내 응시 현황" 카드가 호출하는 단일 쿼리.
     * 4개 영역을 한 번의 쿼리로 묶어 N+1 / 라운드트립을 줄인다.</p>
     *
     * <h3>집계 정의</h3>
     * <ul>
     *   <li>총응시 = COUNT(*)</li>
     *   <li>정답수 = SUM(CASE WHEN isCorrect=true THEN 1 ELSE 0 END)</li>
     *   <li>총획득포인트 = SUM(CASE WHEN isCorrect=true THEN quiz.rewardPoint ELSE 0 END)
     *       — 단순 집계라 "최초 정답만 지급" 정책과 미세하게 다를 수 있다 (재제출 시 동일 row UPDATE
     *       이므로 같은 quiz 가 두 번 카운트되지는 않는다).</li>
     *   <li>마지막응시시각 = MAX(submittedAt)</li>
     * </ul>
     *
     * <p>응시가 0건이면 모든 값이 0/null 로 반환되므로 호출자가 정답률 계산 시 0/0 NaN 을
     * 방어해야 한다.</p>
     *
     * <h3>반환 타입 주의</h3>
     * <p>Spring Boot 4 / Hibernate 7 환경에서는 멀티 SELECT JPQL 의 결과를 반드시
     * {@code List<Object[]>} 로 받아야 한다. 메서드 반환 타입을 {@code Object[]} 로
     * 선언하면 Hibernate 가 Tuple 결과를 한번 더 배열로 감싸 단일 row 가
     * {@code [[0L, null, null, null]]} 형태(size=1, element=Object[]) 로 매핑되어
     * Service 레이어의 {@code (Number) row[0]} 캐스팅이 ClassCastException 을 던진다.
     * 운영 GET /api/v1/quizzes/me/stats 가 500 으로 회귀했던 직접 원인이다 (2026-04-29).</p>
     *
     * @param userId 통계 대상 사용자 ID
     * @return 단일 row 의 컬럼 배열을 담은 List ({@code [totalAttempts, correctCount, totalEarnedPoints, lastAttemptedAt]})
     */
    @Query(
            "SELECT " +
            "  COUNT(p), " +
            "  SUM(CASE WHEN p.isCorrect = true THEN 1 ELSE 0 END), " +
            "  SUM(CASE WHEN p.isCorrect = true THEN p.quiz.rewardPoint ELSE 0 END), " +
            "  MAX(p.submittedAt) " +
            "FROM QuizParticipation p " +
            "WHERE p.userId = :userId"
    )
    List<Object[]> aggregateMyStats(@Param("userId") String userId);

    /**
     * 사용자별 응시 이력 페이징 조회 — 2026-04-29 신규.
     *
     * <p>QuizPage 의 "내 응시 이력" 리스트가 호출. {@code JOIN FETCH p.quiz} 로
     * Lazy 매핑된 Quiz 까지 한 번에 로드하여 N+1 쿼리를 차단한다.</p>
     *
     * <h3>정렬</h3>
     * <p>JPQL 에 {@code ORDER BY p.submittedAt DESC} 하드코딩 — 가장 최근 응시가 먼저.
     * 미제출 record (submittedAt=null) 는 NULLS LAST 로 밀려난다 (대부분 DB 기본).</p>
     *
     * <h3>countQuery 분리</h3>
     * <p>Page 의 totalElements 계산은 JOIN FETCH 가 필요 없으므로 별도 countQuery 를
     * 명시하여 카운트 쿼리에서 fetch join 으로 인한 중복 카운트와 성능 저하를 방지한다.</p>
     *
     * @param userId   응시 이력 조회 대상 사용자 ID
     * @param pageable 페이지 정보 (size 만 적용, sort 는 무시 — 위 ORDER BY 고정)
     * @return Quiz fetch 된 응시 이력 Page
     */
    @Query(
            value = "SELECT p FROM QuizParticipation p " +
                    "JOIN FETCH p.quiz " +
                    "WHERE p.userId = :userId " +
                    "ORDER BY p.submittedAt DESC",
            countQuery = "SELECT COUNT(p) FROM QuizParticipation p WHERE p.userId = :userId"
    )
    Page<QuizParticipation> findMyHistory(@Param("userId") String userId, Pageable pageable);

    /**
     * 특정 사용자가 정답 처리한 퀴즈 참여 수를 집계한다.
     *
     * <p>업적 진행률 소급 계산에서 quiz_perfect / quiz_count_* 계열의
     * 기존 데이터 반영에 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @param isCorrect 정답 여부 (true 고정으로 사용)
     * @return 해당 조건의 참여 수
     */
    long countByUserIdAndIsCorrect(String userId, Boolean isCorrect);

    // ══════════════════════════════════════════════
    // AI 서비스 통계 V2 — 퀴즈 에이전트 KPI/추이 (2026-04-29)
    // ══════════════════════════════════════════════

    /**
     * 정답 수 (전체 정답률 분자).
     *
     * @return isCorrect=true 응시 수
     */
    @Query("SELECT COUNT(p) FROM QuizParticipation p WHERE p.isCorrect = true")
    long countCorrect();

    /**
     * 지정 기간 내 응시 수 (일별 추이/Summary 카드).
     *
     * <p>submittedAt 기준 — 미제출(null)은 제외된다.</p>
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 기간 내 응시 수
     */
    @Query("""
        SELECT COUNT(p) FROM QuizParticipation p
        WHERE p.submittedAt >= :start AND p.submittedAt < :end
        """)
    long countBySubmittedAtBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );
}
