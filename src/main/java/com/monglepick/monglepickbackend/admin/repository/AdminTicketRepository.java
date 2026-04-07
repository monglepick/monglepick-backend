package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportTicket;
import com.monglepick.monglepickbackend.domain.support.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 상담 티켓 리포지토리.
 *
 * <p>관리자 페이지 "고객센터 → 티켓" 탭에서 전체 티켓 조회/필터 조회를 위한 쿼리 메서드를 제공한다.
 * SupportTicket 은 users 테이블을 String FK 로 직접 보관하므로(JPA/MyBatis 하이브리드 §15.4)
 * JOIN FETCH 가 불필요하다 — 평범한 페이징 쿼리만 사용한다.</p>
 */
public interface AdminTicketRepository extends JpaRepository<SupportTicket, Long> {

    /**
     * 전체 티켓을 최신순으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 티켓 페이지
     */
    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 상태의 티켓을 최신순으로 페이징 조회한다.
     *
     * @param status   티켓 상태
     * @param pageable 페이지 정보
     * @return 해당 상태의 티켓 페이지
     */
    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(
            TicketStatus status,
            Pageable pageable
    );

    /**
     * 상태별 티켓 수 카운트 (통계용).
     *
     * @param status 티켓 상태
     * @return 해당 상태의 티켓 수
     */
    long countByStatus(TicketStatus status);
}
