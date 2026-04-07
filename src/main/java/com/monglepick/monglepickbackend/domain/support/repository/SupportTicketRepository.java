package com.monglepick.monglepickbackend.domain.support.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상담 티켓 JPA 레포지토리.
 *
 * <p>{@link SupportTicket} 엔티티에 대한 데이터 접근 계층.
 * 사용자별 티켓 목록 페이징 조회를 제공한다.</p>
 */
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /**
     * 특정 사용자의 상담 티켓 목록을 페이징하여 조회한다.
     *
     * <p>SupportTicket은 String userId를 직접 보관하므로 (JPA/MyBatis 하이브리드 §15.4),
     * Spring Data JPA derived query는 단순 {@code findByUserId}로 작성한다.</p>
     *
     * <p>사용 예: 마이페이지 "내 문의 내역" 탭에서 최신순 페이징 조회.</p>
     *
     * <pre>{@code
     * // 최신순 1페이지 (10개)
     * Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
     * Page<SupportTicket> tickets = ticketRepository.findByUserId(userId, pageable);
     * }</pre>
     *
     * @param userId   조회할 사용자 ID (VARCHAR 50)
     * @param pageable 페이징/정렬 조건
     * @return 해당 사용자의 티켓 페이지 (없으면 빈 Page)
     */
    Page<SupportTicket> findByUserId(String userId, Pageable pageable);
}
