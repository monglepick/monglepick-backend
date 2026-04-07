package com.monglepick.monglepickbackend.domain.chat.repository;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSessionArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 채팅 세션 아카이브 JPA Repository.
 *
 * <p>Agent 내부 API(세션 저장/로드)와 Client 이력 API(목록/상세/삭제)에서 사용한다.</p>
 */
public interface ChatSessionArchiveRepository extends JpaRepository<ChatSessionArchive, Long> {

    /** 세션 UUID로 조회 (Agent 내부 호출: 세션 로드/upsert) */
    Optional<ChatSessionArchive> findBySessionId(String sessionId);

    /** 사용자별 세션 목록 조회 — 소프트 삭제 제외, 최신 메시지 순 (Client 이력 목록) */
    Page<ChatSessionArchive> findByUserIdAndIsDeletedFalseOrderByLastMessageAtDesc(
            String userId, Pageable pageable);

    /** 사용자 소유 + 미삭제 세션 조회 (Client 상세/삭제 시 권한 확인 겸용) */
    Optional<ChatSessionArchive> findByUserIdAndSessionIdAndIsDeletedFalse(
            String userId, String sessionId);

    /** 세션 존재 여부 확인 */
    boolean existsBySessionId(String sessionId);
}
