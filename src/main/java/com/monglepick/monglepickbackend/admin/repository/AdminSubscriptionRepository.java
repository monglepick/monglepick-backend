package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 관리자 전용 구독 리포지토리.
 *
 * <p>관리자 페이지 "결제/포인트 → 구독 관리" 탭에서 사용자 구독 현황을
 * 상태별·전체로 조회하기 위한 쿼리 메서드를 제공한다. plan 필드는 LAZY 로딩이므로
 * N+1 방지를 위해 JOIN FETCH 쿼리를 사용한다.</p>
 *
 * <h3>주요 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findByStatusWithPlan} — 특정 상태의 구독 목록 (plan 즉시 로딩, 페이징)</li>
 *   <li>{@link #findAllWithPlan} — 전체 구독 목록 (plan 즉시 로딩, 페이징)</li>
 *   <li>{@link #findByIdWithPlan} — 단건 조회 (plan 즉시 로딩)</li>
 * </ul>
 *
 * <h3>open-in-view=false 환경 주의</h3>
 * <p>프로젝트는 {@code spring.jpa.open-in-view=false} 설정이므로 트랜잭션 범위 밖에서
 * plan LAZY 필드에 접근하면 LazyInitializationException이 발생한다. 관리자 응답 DTO 변환에서
 * plan.planCode/plan.name 접근이 필요하므로 반드시 FETCH 쿼리를 사용한다.</p>
 */
public interface AdminSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * 특정 상태의 구독 목록을 plan과 함께 페이징 조회한다.
     *
     * <p>정렬: 생성일시 내림차순 (최신 구독이 먼저).</p>
     *
     * @param status   구독 상태 (ACTIVE / CANCELLED / EXPIRED)
     * @param pageable 페이지 정보
     * @return plan이 즉시 로딩된 구독 페이지
     */
    @Query(
            value = "SELECT s FROM UserSubscription s JOIN FETCH s.plan " +
                    "WHERE s.status = :status ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM UserSubscription s WHERE s.status = :status"
    )
    Page<UserSubscription> findByStatusWithPlan(
            @Param("status") UserSubscription.Status status,
            Pageable pageable
    );

    /**
     * 전체 구독 목록을 plan과 함께 페이징 조회한다.
     *
     * <p>상태 필터 없이 전체 구독 현황을 표시할 때 사용한다.
     * 정렬: 생성일시 내림차순.</p>
     *
     * @param pageable 페이지 정보
     * @return plan이 즉시 로딩된 전체 구독 페이지
     */
    @Query(
            value = "SELECT s FROM UserSubscription s JOIN FETCH s.plan ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM UserSubscription s"
    )
    Page<UserSubscription> findAllWithPlan(Pageable pageable);

    /**
     * 단건 구독을 plan과 함께 조회한다.
     *
     * <p>관리자 상세 응답 DTO 변환 시 LazyInitializationException 방지.</p>
     *
     * @param id 구독 레코드 ID (user_subscription_id)
     * @return plan이 즉시 로딩된 구독 (없으면 empty)
     */
    @Query("SELECT s FROM UserSubscription s JOIN FETCH s.plan WHERE s.userSubscriptionId = :id")
    Optional<UserSubscription> findByIdWithPlan(@Param("id") Long id);

    /**
     * 관리자 구독 관리 탭의 복합 필터 검색 (2026-04-14 추가, 2026-04-23 날짜 범위 확장).
     *
     * <p>상태 / 플랜 코드 / 사용자 ID / 구독 생성일 범위(fromDate~toDate)를 선택적으로
     * 조합해 검색한다. null 파라미터는 조건에서 자동 제외되며, plan 은 JOIN FETCH 로
     * 즉시 로딩된다.</p>
     *
     * <p>날짜 필터 의미론 — {@code fromDate} 는 inclusive(이상), {@code toDate} 는 exclusive(미만).
     * 프로젝트 전 Admin Repository 의 날짜 범위 규약과 일치 (AuditLog / Payment 등).</p>
     *
     * @param status    구독 상태 (nullable)
     * @param planCode  구독 플랜 코드 (예: monthly_basic, nullable)
     * @param userId    사용자 ID (nullable)
     * @param fromDate  구독 생성일 시작 inclusive (nullable)
     * @param toDate    구독 생성일 종료 exclusive (nullable)
     * @param pageable  페이지 정보
     * @return plan 이 즉시 로딩된 구독 페이지 (생성일시 내림차순)
     */
    @Query(
            value = "SELECT s FROM UserSubscription s JOIN FETCH s.plan p " +
                    "WHERE (:status IS NULL OR s.status = :status) " +
                    "  AND (:planCode IS NULL OR p.planCode = :planCode) " +
                    "  AND (:userId IS NULL OR s.userId = :userId) " +
                    "  AND (:fromDate IS NULL OR s.createdAt >= :fromDate) " +
                    "  AND (:toDate   IS NULL OR s.createdAt <  :toDate) " +
                    "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM UserSubscription s " +
                    "WHERE (:status IS NULL OR s.status = :status) " +
                    "  AND (:planCode IS NULL OR s.plan.planCode = :planCode) " +
                    "  AND (:userId IS NULL OR s.userId = :userId) " +
                    "  AND (:fromDate IS NULL OR s.createdAt >= :fromDate) " +
                    "  AND (:toDate   IS NULL OR s.createdAt <  :toDate)"
    )
    Page<UserSubscription> searchByFilters(
            @Param("status") UserSubscription.Status status,
            @Param("planCode") String planCode,
            @Param("userId") String userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
