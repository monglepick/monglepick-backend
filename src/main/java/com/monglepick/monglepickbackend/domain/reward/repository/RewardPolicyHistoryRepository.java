package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicyHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리워드 정책 변경 이력 리포지토리 — reward_policy_history 테이블 접근 계층.
 *
 * <p>INSERT-ONLY 원장이므로 조회 메서드만 정의한다.
 * save()는 JpaRepository 기본 제공이며, UPDATE/DELETE는
 * {@link RewardPolicyHistory} 엔티티의 {@code @PreUpdate} / {@code @PreRemove}에서 차단된다.</p>
 *
 * @see RewardPolicyHistory
 */
public interface RewardPolicyHistoryRepository extends JpaRepository<RewardPolicyHistory, Long> {

    /**
     * 특정 정책의 변경 이력을 최신순으로 조회한다.
     *
     * <p>관리자 페이지 "정책 변경 이력" 탭에서 사용한다.</p>
     *
     * @param policyId reward_policy.policy_id
     * @return 변경 이력 목록 (최신 순)
     */
    List<RewardPolicyHistory> findByPolicyIdOrderByCreatedAtDesc(Long policyId);

    /**
     * 정책 ID 무관하게 **모든 정책**의 변경 이력을 복합 필터로 조회한다 — 2026-04-09 P2-⑰ 신규.
     *
     * <p>기존의 {@link #findByPolicyIdOrderByCreatedAtDesc} 는 개별 정책 단위로만 이력을
     *조회할 수 있어서 "모든 정책의 변경 이력을 한 화면에서" 운영 감사하는 것이 불가능했다.
     * 본 메서드는 정책 ID / 변경 관리자(changedBy) / 시간 범위(from~to) 를 모두 optional
     * 파라미터로 받아 복합 조건 조회를 지원한다.</p>
     *
     * <h3>파라미터 규칙</h3>
     * <ul>
     *   <li>모든 파라미터는 {@code null} 허용 — null 이면 해당 조건을 무시한다.</li>
     *   <li>{@code policyId}: 정확 일치 — 특정 정책의 이력만 필터링 (기존 메서드와 중첩되지만 페이징 지원 버전)</li>
     *   <li>{@code changedBy}: 정확 일치 (소문자 무시 안 함) — "SYSTEM" 또는 관리자 userId</li>
     *   <li>{@code fromDate}: 이 시각 이상 (inclusive)</li>
     *   <li>{@code toDate}: 이 시각 미만 (exclusive)</li>
     * </ul>
     *
     * <h3>정렬</h3>
     * <p>JPQL 에 {@code ORDER BY h.createdAt DESC} 하드코딩 — 이력 조회는 항상 최신순이다.
     * Pageable 의 sort 파라미터는 무시된다.</p>
     *
     * @param policyId  특정 정책 ID (nullable)
     * @param changedBy 변경 관리자 userId (nullable)
     * @param fromDate  시작 시각 inclusive (nullable)
     * @param toDate    종료 시각 exclusive (nullable)
     * @param pageable  페이지 정보
     * @return 필터링된 이력 페이지 (createdAt DESC)
     */
    @Query(
        "SELECT h FROM RewardPolicyHistory h WHERE " +
        "(:policyId  IS NULL OR h.policyId  = :policyId) AND " +
        "(:changedBy IS NULL OR h.changedBy = :changedBy) AND " +
        "(:fromDate  IS NULL OR h.createdAt >= :fromDate) AND " +
        "(:toDate    IS NULL OR h.createdAt <  :toDate) " +
        "ORDER BY h.createdAt DESC"
    )
    Page<RewardPolicyHistory> searchAllByFilters(
            @Param("policyId") Long policyId,
            @Param("changedBy") String changedBy,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
