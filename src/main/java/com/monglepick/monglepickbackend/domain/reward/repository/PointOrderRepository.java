package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.PointOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 포인트 아이템 구매 내역 레포지토리 — point_orders 테이블 CRUD.
 *
 * <p>사용자가 포인트 상점에서 구매한 아이템(AI 이용권 등) 이력을 조회한다.
 * 구매 이력 조회, 관리자 통계 집계에 활용된다.</p>
 */
@Repository
public interface PointOrderRepository extends JpaRepository<PointOrder, Long> {

    /**
     * 사용자 ID로 포인트 구매 이력을 최신순으로 조회한다.
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 사용자의 포인트 구매 이력 페이지
     */
    Page<PointOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 사용자 ID와 상태로 포인트 구매 이력을 조회한다.
     *
     * @param userId 사용자 ID
     * @param status 구매 상태 (COMPLETED, CANCELLED, PENDING)
     * @param pageable 페이징 정보
     * @return 필터링된 포인트 구매 이력 페이지
     */
    Page<PointOrder> findByUserIdAndStatusOrderByCreatedAtDesc(
            String userId, String status, Pageable pageable);
}
