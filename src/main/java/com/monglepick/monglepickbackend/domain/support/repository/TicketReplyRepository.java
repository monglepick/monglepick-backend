package com.monglepick.monglepickbackend.domain.support.repository;

import com.monglepick.monglepickbackend.domain.support.entity.TicketReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 티켓 답변 도메인 레포지토리 (사용자용).
 *
 * <p>{@link TicketReply} 엔티티에 대한 데이터 접근 계층으로, 사용자 상세 조회 시
 * 본인 티켓에 달린 답변 목록을 시간순으로 가져오는 데 사용된다.</p>
 *
 * <p>admin 계층의 {@code AdminTicketReplyRepository} 와는 별도로 도메인 계층에서
 * 독립 정의하여 계층 경계를 유지한다(admin 패키지 import 금지).</p>
 */
public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {

    /**
     * 특정 티켓의 답변 목록을 생성일시 오름차순으로 조회한다.
     *
     * <p>오름차순으로 조회하여 대화 흐름이 자연스럽게 표시되도록 한다
     * (오래된 답변 → 최신 답변).</p>
     *
     * @param ticketId 조회 대상 티켓의 PK
     * @return 답변 목록 (오래된 것부터, 없으면 빈 리스트)
     */
    List<TicketReply> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
