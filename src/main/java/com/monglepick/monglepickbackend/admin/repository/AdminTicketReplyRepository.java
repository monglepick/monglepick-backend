package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.TicketReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 관리자 전용 티켓 답변 리포지토리.
 *
 * <p>티켓 상세 조회 시 답변 목록을 시간순으로 가져오는 데 사용한다.</p>
 */
public interface AdminTicketReplyRepository extends JpaRepository<TicketReply, Long> {

    /**
     * 특정 티켓의 답변 목록을 생성일시 오름차순으로 조회한다.
     *
     * @param ticketId 티켓 PK
     * @return 답변 목록 (오래된 것부터)
     */
    List<TicketReply> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
