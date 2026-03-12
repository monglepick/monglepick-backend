package com.monglepick.monglepickbackend.domain.watchhistory.repository;

import com.monglepick.monglepickbackend.domain.watchhistory.entity.WatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시청 이력 JPA 리포지토리
 *
 * <p>대용량 테이블(26M+ 행)이므로 반드시 페이징을 사용해야 합니다.</p>
 */
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    /** 사용자별 시청 이력 조회 (페이징 필수) */
    Page<WatchHistory> findByUserId(Long userId, Pageable pageable);
}
