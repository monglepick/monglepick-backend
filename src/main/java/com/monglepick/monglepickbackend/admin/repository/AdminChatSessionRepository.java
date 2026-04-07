package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
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
}
