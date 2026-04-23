package com.monglepick.monglepickbackend.domain.chat.repository;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅 추천 칩 Repository.
 *
 * <p>chat_suggestions 테이블의 조회/쓰기를 담당한다.
 * 활성 칩 목록(Public용)과 관리자 필터 페이징(Admin용) 두 가지 주요 쿼리를 제공한다.</p>
 */
public interface ChatSuggestionRepository extends JpaRepository<ChatSuggestion, Long> {

    /**
     * 현재 시각 기준으로 노출 가능한 활성 추천 칩 전체를 조회한다.
     *
     * <p>활성 조건:
     * <ul>
     *   <li>{@code is_active = true}</li>
     *   <li>{@code start_at IS NULL OR start_at <= now} (시작 전이 아님)</li>
     *   <li>{@code end_at IS NULL OR end_at >= now} (종료 후가 아님)</li>
     * </ul>
     * 서비스 레이어에서 셔플 후 limit 개만 추출한다.</p>
     *
     * @param now 현재 시각 (서비스 레이어에서 LocalDateTime.now() 전달)
     * @return 노출 가능한 추천 칩 목록 (정렬: display_order 오름차순)
     */
    @Query("""
            SELECT s FROM ChatSuggestion s
            WHERE s.isActive = true
              AND (s.startAt IS NULL OR s.startAt <= :now)
              AND (s.endAt   IS NULL OR s.endAt   >= :now)
            ORDER BY s.displayOrder ASC
            """)
    List<ChatSuggestion> findActiveAt(@Param("now") LocalDateTime now);

    /**
     * 클릭 수를 원자적으로 1 증가시킨다 (DB UPDATE, 동시성 안전).
     *
     * <p>@Modifying + JPQL UPDATE 로 처리하여 엔티티를 메모리로 로드하지 않고
     * 단일 쿼리로 click_count = click_count + 1 을 실행한다.</p>
     *
     * @param id 증가 대상 추천 칩 ID
     */
    @Modifying
    @Query("UPDATE ChatSuggestion s SET s.clickCount = s.clickCount + 1 WHERE s.suggestionId = :id")
    void incrementClickCount(@Param("id") Long id);

    /**
     * 관리자용 — 다중 조건 필터링 + 페이지네이션.
     *
     * <p>isActive / fromDate / toDate 는 모두 옵셔널이며,
     * null 전달 시 해당 조건을 무시한다 (AdminAuditLogRepository 패턴 동일).</p>
     *
     * @param isActive  활성 여부 필터 (null이면 전체)
     * @param fromDate  생성일 시작 (inclusive, null이면 하한 없음)
     * @param toDate    생성일 종료 (exclusive, null이면 상한 없음)
     * @param pageable  페이지 정보
     * @return 필터링된 추천 칩 페이지 (생성일 내림차순)
     */
    @Query("""
            SELECT s FROM ChatSuggestion s
            WHERE (:isActive IS NULL OR s.isActive = :isActive)
              AND (:fromDate IS NULL OR s.createdAt >= :fromDate)
              AND (:toDate   IS NULL OR s.createdAt  < :toDate)
            ORDER BY s.createdAt DESC
            """)
    Page<ChatSuggestion> findAdminFiltered(
            @Param("isActive")  Boolean isActive,
            @Param("fromDate")  LocalDateTime fromDate,
            @Param("toDate")    LocalDateTime toDate,
            Pageable pageable
    );
}
