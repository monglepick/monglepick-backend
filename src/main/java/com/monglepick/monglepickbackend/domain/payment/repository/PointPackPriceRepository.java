package com.monglepick.monglepickbackend.domain.payment.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.PointPackPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 포인트팩 가격 마스터 리포지토리 — point_pack_prices 테이블 접근 계층.
 *
 * <p>PaymentService.createOrder()에서 POINT_PACK 주문 생성 시
 * 클라이언트가 보낸 금액/포인트가 서버 가격표와 일치하는지 검증한다.</p>
 *
 * <p>v3.2: 컬럼명 amount → price 변경에 따라 findByAmount... → findByPrice... 로 변경.</p>
 */
public interface PointPackPriceRepository extends JpaRepository<PointPackPrice, Long> {

    /**
     * 결제 금액(price) + 지급 포인트(pointsAmount) 조합이 활성 가격표에 존재하는지 검증한다.
     *
     * <p>클라이언트 변조 방지: 요청의 (price, pointsAmount)가 서버 가격표와 정확히 일치해야만 주문 생성을 허용.
     * v3.2: 컬럼명 amount → price 변경 반영.</p>
     *
     * @param price        결제 금액 (KRW, 1P=10원)
     * @param pointsAmount 지급 포인트
     * @return 일치하는 활성 가격 행 (없으면 Optional.empty → INVALID_POINT_PACK 에러)
     */
    Optional<PointPackPrice> findByPriceAndPointsAmountAndIsActiveTrue(Integer price, Integer pointsAmount);

    /**
     * 활성 포인트팩 전체 목록을 정렬 순서로 조회한다 (클라이언트 상점 표시용).
     */
    List<PointPackPrice> findByIsActiveTrueOrderBySortOrderAsc();

    /**
     * 관리자 — 동일한 (price, pointsAmount) 조합 중복 등록 검증.
     *
     * <p>활성/비활성 무관하게 중복 가격 조합은 차단한다.</p>
     */
    boolean existsByPriceAndPointsAmount(Integer price, Integer pointsAmount);
}
