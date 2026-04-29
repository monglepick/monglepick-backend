package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 관리자 전용 채팅 세션 아카이브 리포지토리.
 *
 * <p>관리자 페이지 "AI 운영 → 챗봇 대화 로그" 탭에서 모든 사용자의 채팅 세션을
 * 조회하기 위한 쿼리 메서드를 제공한다. 도메인 레이어의 {@code ChatSessionArchiveRepository}는
 * 사용자별 조회(findByUser_UserId...) 에 특화되어 있으므로 전체 세션 페이징 조회는
 * 별도로 분리한다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByIsDeletedFalseOrderByLastMessageAtDesc} — 삭제되지 않은 전체 세션 최신순 페이징</li>
 *   <li>{@link #findBySessionIdAndIsDeletedFalse} — 단건 조회 (삭제 제외)</li>
 * </ul>
 */
public interface AdminChatSessionRepository extends JpaRepository<ChatSessionArchive, Long> {

    /**
     * 삭제되지 않은 전체 채팅 세션을 마지막 메시지 시각 기준 최신순으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 전체 채팅 세션 페이지 (소프트 삭제 제외)
     */
    Page<ChatSessionArchive> findByIsDeletedFalseOrderByLastMessageAtDesc(Pageable pageable);

    /**
     * 특정 사용자(userId)의 채팅 세션을 마지막 메시지 시각 기준 최신순으로 페이징 조회한다.
     *
     * <p>관리자 페이지 "AI 운영 → 챗봇 대화 로그" 탭의 사용자 검색(UserSearchPicker)에서
     * 이메일/닉네임으로 사용자를 선택하면 추출된 userId 가 이 메서드의 파라미터로 전달된다.
     * ChatSessionArchive 는 String FK 직접 보관 방식(JPA/MyBatis 하이브리드 §15.4)이므로
     * 단순 컬럼 매칭으로 정확히 그 사용자의 세션만 가져온다.</p>
     *
     * <p>2026-04-14 신규 — 기존 컨트롤러가 받지도 않던 keyword 입력을 폐기하고
     * 사용자 단위 필터링으로 일원화하기 위해 추가.</p>
     *
     * @param userId   세션 소유자 user_id (정확 일치)
     * @param pageable 페이지 정보
     * @return 해당 사용자의 채팅 세션 페이지 (소프트 삭제 제외)
     */
    Page<ChatSessionArchive> findByUserIdAndIsDeletedFalseOrderByLastMessageAtDesc(
            String userId,
            Pageable pageable
    );

    /**
     * 특정 세션 UUID로 단건 조회한다 (소프트 삭제 제외).
     *
     * <p>관리자 상세 화면에서 메시지 전체를 조회할 때 사용한다.</p>
     *
     * @param sessionId 세션 UUID
     * @return 채팅 세션 (삭제되었거나 존재하지 않으면 empty)
     */
    Optional<ChatSessionArchive> findBySessionIdAndIsDeletedFalse(String sessionId);

    /**
     * 지정 기간 내 생성된 채팅 세션 수를 카운트한다.
     *
     * <p>관리자 대시보드 KPI(오늘 AI 채팅 요청 수)와 추이 차트(일별 AI 채팅 수)에 사용된다.
     * 소프트 삭제 여부는 무시한다 — 통계는 발생량을 측정하므로 사용자 삭제와 무관하게 카운트한다.</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 해당 범위의 채팅 세션 수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // ======================== Task 4: 챗봇 통계 집계 ========================
    //
    // AI 운영 탭의 GET /admin/ai/chat/stats 엔드포인트가 사용한다.
    // 빈 DB 환경에서도 안전하게 0/빈 리스트를 반환하도록 모든 쿼리가 COUNT/SUM 기반이다.

    /**
     * 지정 기간 내 활성 세션 수(is_active=true 이고 is_deleted=false) 를 카운트한다.
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 활성 세션 수
     */
    long countByIsActiveTrueAndIsDeletedFalseAndCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * 지정 기간 내 세션의 turn_count 합계를 조회한다 (총 대화 턴 수).
     *
     * <p>COALESCE 로 감싸 데이터가 없을 때 null 대신 0 을 반환한다.
     * 빈 DB 환경에서도 안전하게 0 을 반환하므로 Service 레이어에서 별도 null 체크가 필요 없다.</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 총 턴 수 (데이터 없으면 0)
     */
    @Query(
            "SELECT COALESCE(SUM(c.turnCount), 0) " +
            "FROM ChatSessionArchive c " +
            "WHERE c.isDeleted = false " +
            "  AND c.createdAt >= :start " +
            "  AND c.createdAt < :end"
    )
    long sumTurnCountByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 지정 기간 내 세션들의 intent_summary JSON 원문 목록을 조회한다.
     *
     * <p>통계 집계용으로 최대 1000 건까지만 가져와 서비스 레이어에서 파싱/합산한다.
     * intent_summary 는 {@code {"recommend": 3, "search": 1, ...}} 형태의 JSON 객체이므로
     * DB 수준 집계가 어렵다 (MySQL JSON 함수 의존성 회피).</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return intent_summary JSON 문자열 리스트 (null/공백 제외)
     */
    @Query(
            "SELECT c.intentSummary " +
            "FROM ChatSessionArchive c " +
            "WHERE c.isDeleted = false " +
            "  AND c.createdAt >= :start " +
            "  AND c.createdAt < :end " +
            "  AND c.intentSummary IS NOT NULL"
    )
    List<String> findIntentSummariesByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // ═══ AI 서비스 통계 분석용 집계 ═══

    /** 삭제되지 않은 전체 세션 수 */
    long countByIsDeletedFalse();

    /** 삭제되지 않은 전체 세션의 턴 수 합계 */
    @Query("SELECT COALESCE(SUM(c.turnCount), 0) FROM ChatSessionArchive c WHERE c.isDeleted = false")
    long sumAllTurnCount();

    /** 삭제되지 않은 전체 세션의 추천 영화 수 합계 */
    @Query("SELECT COALESCE(SUM(c.recommendedMovieCount), 0) FROM ChatSessionArchive c WHERE c.isDeleted = false")
    long sumAllRecommendedMovieCount();

    /** 전체 기간 intent_summary JSON 목록 (최대 5000건) */
    @Query("SELECT c.intentSummary FROM ChatSessionArchive c " +
           "WHERE c.isDeleted = false AND c.intentSummary IS NOT NULL")
    List<String> findAllIntentSummaries();

    // ══════════════════════════════════════════════
    // 관리자 통계용 집계 쿼리 (AdminStatsService 섹션 14 — 전환 퍼널)
    // ══════════════════════════════════════════════

    /**
     * 지정 기간 내 AI 채팅을 사용한 고유 사용자 수를 반환한다 (전환 퍼널 단계 3).
     *
     * <p>DISTINCT user_id 로 중복을 제거하여 실제로 채팅을 시작한 고유 사용자만 카운트한다.
     * 소프트 삭제 레코드도 포함한다(퍼널 분석은 실제 발생량 기준).</p>
     *
     * @param start 기간 시작 시각 (inclusive)
     * @param end   기간 종료 시각 (exclusive)
     * @return 해당 기간 AI 채팅 고유 사용자 수
     */
    @Query("""
            SELECT COUNT(DISTINCT c.userId)
            FROM ChatSessionArchive c
            WHERE c.createdAt >= :start AND c.createdAt < :end
            """)
    long countDistinctUserByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // ══════════════════════════════════════════════
    // AI 서비스 통계 V2 — 운영 통계용 (소프트 삭제 무시)
    //
    // 운영자 입장에서는 "사용자가 세션을 삭제했어도 발생량은 측정해야" 하므로
    // isDeleted 필터를 적용하지 않는 신규 집계 메서드를 별도로 두어
    // 기존 호출자 (사용자 목록 화면 등) 의 의미는 보존한다.
    // ══════════════════════════════════════════════

    /**
     * 전체 세션 수 (소프트 삭제 무시) — 운영 통계의 발생량 기준.
     *
     * @return 전체 세션 수 (삭제 포함)
     */
    @Query("SELECT COUNT(c) FROM ChatSessionArchive c")
    long countAllSessions();

    /**
     * 전체 세션의 turn_count 합계 (소프트 삭제 무시).
     *
     * @return 누적 턴 수 (삭제 포함)
     */
    @Query("SELECT COALESCE(SUM(c.turnCount), 0) FROM ChatSessionArchive c")
    long sumAllTurns();

    /**
     * 전체 세션의 추천 영화 수 합계 (소프트 삭제 무시).
     *
     * @return 누적 추천 영화 수 (삭제 포함)
     */
    @Query("SELECT COALESCE(SUM(c.recommendedMovieCount), 0) FROM ChatSessionArchive c")
    long sumAllRecommendedMovies();

    /**
     * 지정 기간 내 세션 수 (소프트 삭제 무시).
     *
     * @param start 시작 시각 (inclusive)
     * @param end   종료 시각 (exclusive)
     * @return 기간 내 세션 수 (삭제 포함)
     */
    @Query("""
            SELECT COUNT(c) FROM ChatSessionArchive c
            WHERE c.createdAt >= :start AND c.createdAt < :end
            """)
    long countSessionsByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 지정 기간 내 세션의 turn_count 합계 (소프트 삭제 무시).
     *
     * @param start 시작 시각
     * @param end   종료 시각
     * @return 기간 내 누적 턴 수 (삭제 포함)
     */
    @Query("""
            SELECT COALESCE(SUM(c.turnCount), 0) FROM ChatSessionArchive c
            WHERE c.createdAt >= :start AND c.createdAt < :end
            """)
    long sumTurnsByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 의도 집계 — 페이지네이션 가능한 intent_summary 조회 (소프트 삭제 무시).
     *
     * <p>{@link #findAllIntentSummaries()} 가 LIMIT 없이 전체를 fetch 하여 OOM 위험이 있던 점을
     * 보완. 호출자는 Pageable 로 size 제한을 명시한다 (예: PageRequest.of(0, 10000)).</p>
     *
     * @param pageable 페이지 정보
     * @return intent_summary JSON 페이지
     */
    @Query("SELECT c.intentSummary FROM ChatSessionArchive c WHERE c.intentSummary IS NOT NULL")
    Page<String> findIntentSummariesPaged(Pageable pageable);
}
