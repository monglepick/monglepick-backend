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
     * {@code surface} 별 활성 추천 칩 조회 (2026-04-23 추가).
     *
     * <p>{@code findActiveAt} 과 동일한 기간 조건 + surface 필터. 인덱스
     * {@code idx_suggestions_surface_active(surface, is_active, start_at, end_at)} 의
     * 1번 컬럼을 활용해 채널별 조회 성능을 유지한다.</p>
     *
     * @param surface 채널 코드 — 'user_chat' / 'admin_assistant' / 'faq_chatbot'
     * @param now     현재 시각
     * @return 해당 채널의 노출 가능한 추천 칩 목록 (display_order 오름차순)
     */
    @Query("""
            SELECT s FROM ChatSuggestion s
            WHERE s.surface = :surface
              AND s.isActive = true
              AND (s.startAt IS NULL OR s.startAt <= :now)
              AND (s.endAt   IS NULL OR s.endAt   >= :now)
            ORDER BY s.displayOrder ASC
            """)
    List<ChatSuggestion> findActiveBySurfaceAt(
            @Param("surface") String surface,
            @Param("now") LocalDateTime now
    );

    /**
     * 특정 surface 의 등록 건수 조회 — DemoInitializer 가 채널별로 시드 존재 여부를
     * 판단할 때 사용. 기존 `count()` 는 전체 테이블 기준이라 surface 별 분리 시드가
     * 불가능했던 점을 보완.
     *
     * @param surface 채널 코드
     * @return 해당 surface 의 총 레코드 수
     */
    long countBySurface(String surface);

    /**
     * 레거시 레코드 복구 — surface 가 NULL 또는 빈 문자열인 모든 행을 'user_chat' 으로
     * 일괄 업데이트한다. 2026-04-23 surface 컬럼 도입 이후 기존 DB 에 컬럼이 추가될 때
     * Hibernate 가 DEFAULT 값 없이 ALTER TABLE 을 실행하면 기존 10건 시드가 빈 문자열로
     * 남을 수 있다 (MySQL strict mode 설정에 따라). Initializer 맨 처음에 1회 호출하여
     * 3채널 분리가 제대로 동작하도록 보장한다.
     *
     * @return 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE ChatSuggestion s SET s.surface = 'user_chat' WHERE s.surface IS NULL OR s.surface = ''")
    int normalizeLegacySurface();

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
     * <p>모든 필터는 옵셔널. null 또는 빈 문자열(surface) 전달 시 해당 조건 무시.
     * 2026-04-23: surface 필터 추가 — 관리자 UI 에서 채널(유저 채팅/관리자 AI/FAQ 챗봇)
     * 별로 시각화하기 위함.</p>
     *
     * @param surface   채널 필터 (null/빈 문자열이면 전체)
     * @param isActive  활성 여부 필터 (null이면 전체)
     * @param fromDate  생성일 시작 (inclusive, null이면 하한 없음)
     * @param toDate    생성일 종료 (exclusive, null이면 상한 없음)
     * @param pageable  페이지 정보
     * @return 필터링된 추천 칩 페이지 (생성일 내림차순)
     */
    @Query("""
            SELECT s FROM ChatSuggestion s
            WHERE (:surface  IS NULL OR :surface = '' OR s.surface = :surface)
              AND (:isActive IS NULL OR s.isActive = :isActive)
              AND (:fromDate IS NULL OR s.createdAt >= :fromDate)
              AND (:toDate   IS NULL OR s.createdAt  < :toDate)
            ORDER BY s.createdAt DESC
            """)
    Page<ChatSuggestion> findAdminFiltered(
            @Param("surface")   String surface,
            @Param("isActive")  Boolean isActive,
            @Param("fromDate")  LocalDateTime fromDate,
            @Param("toDate")    LocalDateTime toDate,
            Pageable pageable
    );
}
