package com.monglepick.monglepickbackend.domain.payment.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 구독 상품 리포지토리 — subscription_plans 테이블 데이터 접근.
 *
 * <p>구독 상품 목록 조회 및 상품 코드 기반 검색을 제공한다.
 * 운영팀의 상품 관리(등록/수정/비활성화)와 사용자의 상품 목록 조회에 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByIsActiveTrueOrderByPriceAsc()} — 활성 상품 목록 (가격 오름차순, UI 표시용)</li>
 *   <li>{@link #findByPlanCode(String)} — 상품 코드로 조회 (결제 처리 시 상품 검증)</li>
 * </ul>
 *
 * @see SubscriptionPlan 구독 상품 엔티티
 */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    /**
     * 활성화된 구독 상품 목록을 가격 오름차순으로 조회한다.
     *
     * <p>사용자에게 구독 상품 목록을 보여줄 때 사용한다.
     * {@code is_active = TRUE}인 상품만 반환하며,
     * 비활성화된 상품은 신규 구독 불가하므로 제외된다.</p>
     *
     * @return 활성 구독 상품 목록 (가격 오름차순 정렬)
     */
    List<SubscriptionPlan> findByIsActiveTrueOrderByPriceAsc();

    /**
     * 상품 코드로 구독 상품을 조회한다.
     *
     * <p>결제 요청 시 클라이언트가 전달한 planCode가 유효한지 검증하거나,
     * 특정 상품의 상세 정보를 조회할 때 사용한다.
     * planCode는 UNIQUE 제약이 있으므로 최대 1건만 반환된다.</p>
     *
     * @param planCode 상품 코드 (예: "monthly_basic", "yearly_premium")
     * @return 구독 상품 (존재하지 않으면 empty)
     */
    Optional<SubscriptionPlan> findByPlanCode(String planCode);
}
