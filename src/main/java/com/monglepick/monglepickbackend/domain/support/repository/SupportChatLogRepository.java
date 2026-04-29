package com.monglepick.monglepickbackend.domain.support.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고객센터 챗봇 사용 로그 Repository (read-only 사용).
 *
 * <p>Agent (Python) 가 직접 SQL INSERT 로 데이터를 적재하므로 Backend 는 조회 전용.</p>
 *
 * <p>제공 쿼리:</p>
 * <ul>
 *   <li>페이징 검색 (의도 / 1:1 유도 / 사용자 / 키워드 / 기간 필터)</li>
 *   <li>의도별 분포 집계 (intent_kind 기준 GROUP BY)</li>
 *   <li>1:1 유도 비율 집계 (시간대별)</li>
 *   <li>자주 묻는 질문 키워드 (user_message TOP-N)</li>
 *   <li>session_id 단위 트레이스 (단일 세션 모든 턴)</li>
 * </ul>
 */
public interface SupportChatLogRepository extends JpaRepository<SupportChatLog, Long> {

    /**
     * session_id 단위로 모든 턴을 시간순 조회 — 세션 트레이스 화면용.
     */
    List<SupportChatLog> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 페이징 검색 — 모든 필터는 NULL 허용 (해당 필터 미적용).
     *
     * @param intentKind 의도 필터 (faq/personal_data/policy/redirect/smalltalk/complaint/unknown)
     * @param needsHuman 1:1 유도 여부 필터 (NULL=무시)
     * @param userId     특정 사용자 필터
     * @param keyword    user_message LIKE 키워드 (NULL=무시)
     * @param from       기간 시작 (포함)
     * @param to         기간 종료 (포함)
     */
    @Query("""
        SELECT l FROM SupportChatLog l
        WHERE (:intentKind IS NULL OR l.intentKind = :intentKind)
          AND (:needsHuman IS NULL OR l.needsHuman = :needsHuman)
          AND (:userId IS NULL OR l.userId = :userId)
          AND (:keyword IS NULL OR l.userMessage LIKE CONCAT('%', :keyword, '%'))
          AND (:from IS NULL OR l.createdAt >= :from)
          AND (:to IS NULL OR l.createdAt < :to)
        ORDER BY l.createdAt DESC
        """)
    Page<SupportChatLog> searchLogs(
            @Param("intentKind") String intentKind,
            @Param("needsHuman") Boolean needsHuman,
            @Param("userId") String userId,
            @Param("keyword") String keyword,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    /**
     * 의도별 건수 집계 (시간 범위 내).
     * 결과: [intent_kind, count] 형태의 Object[] 배열 리스트.
     */
    @Query("""
        SELECT l.intentKind AS intentKind, COUNT(l) AS cnt
        FROM SupportChatLog l
        WHERE (:from IS NULL OR l.createdAt >= :from)
          AND (:to IS NULL OR l.createdAt < :to)
        GROUP BY l.intentKind
        ORDER BY cnt DESC
        """)
    List<Object[]> countByIntent(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * 일자별 총 건수 + 1:1 유도 건수 — 시계열 차트용.
     * 결과: [date(YYYY-MM-DD), total, needs_human_count] Object[].
     *
     * <p>JPA Native Query 로 DATE() 함수 사용 (HQL 미지원).</p>
     */
    @Query(value = """
        SELECT DATE(created_at) AS d,
               COUNT(*) AS total,
               SUM(CASE WHEN needs_human = 1 THEN 1 ELSE 0 END) AS needs_human_cnt
        FROM support_chat_log
        WHERE (:from IS NULL OR created_at >= :from)
          AND (:to IS NULL OR created_at < :to)
        GROUP BY DATE(created_at)
        ORDER BY d ASC
        """, nativeQuery = true)
    List<Object[]> dailyCounts(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * 가장 많이 들어온 user_message 상위 N건 (정확 일치 GROUP BY).
     * 키워드 군집 / 자주 묻는 질문 파악용.
     */
    @Query(value = """
        SELECT user_message, COUNT(*) AS cnt
        FROM support_chat_log
        WHERE (:from IS NULL OR created_at >= :from)
          AND (:to IS NULL OR created_at < :to)
        GROUP BY user_message
        ORDER BY cnt DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topUserMessages(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") int limit
    );

    /**
     * 1:1 유도된 로그 비율 — 분자/분모.
     */
    @Query("""
        SELECT
            SUM(CASE WHEN l.needsHuman = true THEN 1 ELSE 0 END) AS needsHuman,
            COUNT(l) AS total
        FROM SupportChatLog l
        WHERE (:from IS NULL OR l.createdAt >= :from)
          AND (:to IS NULL OR l.createdAt < :to)
        """)
    Object[] needsHumanRatio(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
