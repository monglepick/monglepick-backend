package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
