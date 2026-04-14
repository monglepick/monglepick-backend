package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 등급 마스터 리포지토리 — grades 테이블 접근 계층.
 *
 * <p>등급 코드(gradeCode) 기반 단건 조회와 누적 포인트 기반 등급 결정,
 * 활성 등급 목록 조회 기능을 제공한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByGradeCode(String)} — BRONZE/SILVER/GOLD/PLATINUM 코드로 단건 조회</li>
 *   <li>{@link #findTopByMinPointsLessThanEqualAndIsActiveTrueOrderByMinPointsDesc(Integer)}
 *       — 누적 포인트 기준 해당 등급 결정 (내림차순 첫 번째)</li>
 *   <li>{@link #findAllByIsActiveTrueOrderBySortOrderAsc()} — 활성 등급 전체 목록 (정렬 순)</li>
 * </ul>
 *
 * @see Grade
 */
public interface GradeRepository extends JpaRepository<Grade, Long> {

    /**
     * 등급 코드로 Grade를 조회한다.
     *
     * <p>코드는 대문자로 저장되므로 호출 전 {@code toUpperCase()} 처리를 권장한다.
     * 예: {@code gradeRepository.findByGradeCode("BRONZE")}</p>
     *
     * @param gradeCode 등급 코드 (BRONZE, SILVER, GOLD, PLATINUM)
     * @return 해당 등급 (존재하지 않으면 Optional.empty())
     */
    Optional<Grade> findByGradeCode(String gradeCode);

    /**
     * 누적 포인트 기준으로 사용자의 현재 등급을 결정한다.
     *
     * <p>조건: {@code min_points <= totalEarned AND is_active = true}
     * 정렬: {@code min_points DESC} → 가장 높은 min_points를 가진 등급이 첫 번째.
     * 예: totalEarned=1500이면 BRONZE(0)와 SILVER(1000) 중 SILVER가 반환된다.</p>
     *
     * <h4>동작 원리</h4>
     * <pre>
     * totalEarned=500  → min_points <= 500 AND active → BRONZE(0) 만족 → BRONZE 반환
     * totalEarned=1500 → min_points <= 1500 → BRONZE(0), SILVER(1000) 만족 → SILVER 반환
     * totalEarned=6000 → BRONZE, SILVER, GOLD(5000) 만족 → GOLD 반환
     * </pre>
     *
     * @param totalEarned 사용자의 누적 획득 포인트
     * @return 해당 등급 (활성 등급이 하나도 없으면 Optional.empty())
     */
    Optional<Grade> findTopByMinPointsLessThanEqualAndIsActiveTrueOrderByMinPointsDesc(Integer totalEarned);

    /**
     * 활성화된 모든 등급을 정렬 순서(sort_order ASC) 기준으로 반환한다.
     *
     * <p>관리자 페이지 등급 목록 표시, 등급 선택 드롭다운 등에 사용된다.
     * BRONZE → SILVER → GOLD → PLATINUM 순서로 반환된다.</p>
     *
     * @return 활성 등급 목록 (sort_order 오름차순)
     */
    List<Grade> findAllByIsActiveTrueOrderBySortOrderAsc();

    /**
     * {@code subscription_plan_type} 컬럼 값으로 활성 등급을 단건 조회한다.
     *
     * <p>2026-04-14 신설 — 구독 결제 완료 시 {@code SubscriptionService.applyGuaranteedGradeForSubscription()}
     * 에서 사용한다. 기존에는 {@code findAll().stream().filter(...)} 로 전 등급을 fetch 한 뒤 stream 으로
     * 필터링했는데, DB 쿼리 한 번으로 단건 조회가 가능하므로 의도와 성능 측면에서 모두 개선된다.</p>
     *
     * <h4>매핑 규칙</h4>
     * <ul>
     *   <li>'basic'   → SILVER (팝콘)</li>
     *   <li>'premium' → PLATINUM (몽글팝콘)</li>
     *   <li>그 외      → 빈 결과</li>
     * </ul>
     *
     * <p>설계상 {@code subscription_plan_type} 컬럼은 등급당 최대 1행이지만, 운영 상 정합성이
     * 깨질 수 있으므로 호출 측에서 단건 처리(첫 결과 사용 등)를 보장해야 한다.</p>
     *
     * @param subscriptionPlanType 구독 플랜 타입 ("basic" / "premium")
     * @return 매칭되는 활성 등급 (없으면 Optional.empty())
     */
    Optional<Grade> findFirstBySubscriptionPlanTypeAndIsActiveTrue(String subscriptionPlanType);
}
