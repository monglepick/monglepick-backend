package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 전용 결제 주문 리포지토리.
 *
 * <p>관리자 페이지 "결제/포인트" 탭에서 결제 주문 목록을 상태별·전체로 조회하기 위한
 * 쿼리 메서드를 제공한다. 도메인 레이어의 {@code PaymentOrderRepository}는 사용자용
 * 조회(findByUserIdOrderByCreatedAtDesc)에 특화되어 있으므로, 관리자용 전체 목록 조회는
 * 별도 리포지토리로 분리한다.</p>
 *
 * <h3>주요 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findByStatusOrderByCreatedAtDesc} — 특정 상태의 주문 목록 최신순 조회</li>
 *   <li>{@link #findAllByOrderByCreatedAtDesc} — 전체 주문 목록 최신순 조회 (필터 없음)</li>
 * </ul>
 *
 * <h3>주문 상태 유효값</h3>
 * <ul>
 *   <li>{@code PENDING} — 결제 대기</li>
 *   <li>{@code COMPLETED} — 결제 완료</li>
 *   <li>{@code FAILED} — 결제 실패</li>
 *   <li>{@code REFUNDED} — 환불 완료</li>
 *   <li>{@code COMPENSATION_FAILED} — 보상 취소 실패 (관리자 수동 조치 필요)</li>
 * </ul>
 */
public interface AdminPaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    /**
     * 특정 상태의 결제 주문 목록을 최신순으로 페이징 조회한다.
     *
     * <p>관리자 결제 내역 탭에서 상태 필터(예: "환불만 보기", "실패만 보기")를
     * 적용할 때 사용한다.</p>
     *
     * @param status   주문 상태 enum
     * @param pageable 페이지 정보
     * @return 해당 상태의 주문 페이지
     */
    Page<PaymentOrder> findByStatusOrderByCreatedAtDesc(PaymentOrder.OrderStatus status, Pageable pageable);

    /**
     * 전체 결제 주문 목록을 최신순으로 페이징 조회한다.
     *
     * <p>상태 필터 없이 전체 결제 내역을 표시할 때 사용한다.</p>
     *
     * @param pageable 페이지 정보
     * @return 전체 주문 페이지
     */
    Page<PaymentOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 관리자 결제 내역 탭의 복합 필터 검색 (2026-04-14 추가).
     *
     * <p>상태(status) / 주문 유형(orderType) / 사용자 ID 를 선택적으로 조합해 검색한다.
     * null 인 파라미터는 WHERE 조건에서 자동 제외된다.</p>
     *
     * @param status     주문 상태 enum (nullable)
     * @param orderType  주문 유형 enum (nullable)
     * @param userId     사용자 ID (nullable)
     * @param pageable   페이지 정보
     * @return 조건에 매칭되는 결제 주문 페이지 (생성일시 내림차순)
     */
    @Query(
            value = "SELECT p FROM PaymentOrder p " +
                    "WHERE (:status IS NULL OR p.status = :status) " +
                    "  AND (:orderType IS NULL OR p.orderType = :orderType) " +
                    "  AND (:userId IS NULL OR p.userId = :userId) " +
                    "ORDER BY p.createdAt DESC",
            countQuery = "SELECT COUNT(p) FROM PaymentOrder p " +
                    "WHERE (:status IS NULL OR p.status = :status) " +
                    "  AND (:orderType IS NULL OR p.orderType = :orderType) " +
                    "  AND (:userId IS NULL OR p.userId = :userId)"
    )
    Page<PaymentOrder> searchByFilters(
            @Param("status") PaymentOrder.OrderStatus status,
            @Param("orderType") PaymentOrder.OrderType orderType,
            @Param("userId") String userId,
            Pageable pageable
    );
}
