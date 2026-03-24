package com.monglepick.monglepickbackend.domain.payment.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 결제 주문 리포지토리 — payment_orders 테이블 데이터 접근.
 *
 * <p>결제 주문의 생성, 조회, 상태 변경을 지원한다.
 * Toss Payments 결제 플로우에서 주문 생성(PENDING) → 결제 확인(COMPLETED/FAILED) →
 * 환불(REFUNDED) 전 과정에 걸쳐 사용된다.</p>
 *
 * <h3>PK 특이사항</h3>
 * <p>payment_orders의 PK인 {@code order_id}는 VARCHAR(50)이며,
 * AUTO_INCREMENT가 아닌 UUID를 직접 생성하여 사용한다.
 * 따라서 JpaRepository의 제네릭 ID 타입은 {@code String}이다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByOrderId(String)} — 주문 UUID로 조회 (PG 결제 확인 콜백에서 사용)</li>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc(String, Pageable)} — 사용자 결제 이력 (페이징)</li>
 * </ul>
 *
 * @see PaymentOrder 결제 주문 엔티티
 */
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    /**
     * 주문 UUID로 결제 주문을 조회한다.
     *
     * <p>Toss Payments 결제 확인(confirm) 콜백에서 orderId로 주문을 찾아
     * 상태를 COMPLETED 또는 FAILED로 변경할 때 사용한다.
     * PK 조회이므로 {@code findById()}와 동일하지만, 명시적인 메서드명을 제공한다.</p>
     *
     * @param orderId 주문 UUID (PG에 전달된 주문 번호)
     * @return 결제 주문 (존재하지 않으면 empty)
     */
    Optional<PaymentOrder> findByOrderId(String orderId);

    /**
     * 사용자의 결제 이력을 최신순으로 페이징 조회한다.
     *
     * <p>마이페이지의 결제 내역 화면에서 사용한다.
     * 모든 상태(PENDING, COMPLETED, FAILED, REFUNDED)의 주문이 포함되며,
     * 주문 생성 시각 기준 최신순으로 정렬된다.</p>
     *
     * <p>페이징 파라미터 예시:</p>
     * <pre>
     * Pageable pageable = PageRequest.of(0, 20);  // 첫 페이지, 20건
     * </pre>
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 정보 (페이지 번호, 페이지 크기)
     * @return 결제 주문 페이지 (최신순 정렬)
     */
    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
