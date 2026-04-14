package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.RewardCatalogDto.ActivityProgressResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.RewardCatalogDto.MilestoneProgressResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.RewardCatalogDto.PolicyResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.RewardCatalogDto.UserRewardStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicy;
import com.monglepick.monglepickbackend.domain.reward.entity.UserActivityProgress;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserActivityProgressRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 리워드 카탈로그/진행 현황 조회 전용 서비스 (읽기 전용).
 *
 * <p>사용자/관리자 양쪽에서 "리워드 지급 기준"(정책 카탈로그) 과
 * "내가(또는 특정 유저가) 지금까지 어떤 리워드를 얼마나 받았는지" 를 조회할 때 사용한다.
 * 쓰기 로직(리워드 지급, 정책 수정) 은 {@link RewardService} / {@code AdminRewardPolicyService}
 * 쪽에 있으며, 이 서비스는 순수 조회만 담당한다.</p>
 *
 * <h3>카테고리별 필터</h3>
 * <ul>
 *   <li>{@code null} — 전체 활성 정책</li>
 *   <li>{@code CONTENT} — 콘텐츠 생산 (리뷰/게시글/댓글)</li>
 *   <li>{@code ENGAGEMENT} — 참여 (찜/FAQ/티켓)</li>
 *   <li>{@code MILESTONE} — 마일스톤/업적 (첫 리뷰/등급 승급 등)</li>
 *   <li>{@code ATTENDANCE} — 출석 관련</li>
 * </ul>
 *
 * <h3>일반 활동 vs 마일스톤 분리 기준</h3>
 * <p>{@code threshold_target IS NULL} 이면 "일반 활동" (ActivityProgressResponse),
 * {@code threshold_target IS NOT NULL} 이면 "마일스톤" (MilestoneProgressResponse) 로 분류한다.
 * 마일스톤은 parent_action_type 이 참조하는 UserActivityProgress 의 카운터(TOTAL/DAILY/STREAK)
 * 대비 달성률을 계산해 반환한다.</p>
 *
 * @see RewardPolicy
 * @see UserActivityProgress
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardQueryService {

    /** 리워드 정책 리포지토리 (활성 정책 조회) */
    private final RewardPolicyRepository rewardPolicyRepository;

    /** 유저 활동 진행률 리포지토리 (사용자별 카운터 조회) */
    private final UserActivityProgressRepository progressRepository;

    /** 포인트 잔액 리포지토리 (현재 잔액/누적/등급 요약) */
    private final UserPointRepository userPointRepository;

    // ══════════════════════════════════════════════
    // 1) 정책 카탈로그 조회
    // ══════════════════════════════════════════════

    /**
     * 활성 리워드 정책 목록을 조회한다 (카테고리별 필터 가능).
     *
     * <p>공개(클라이언트) + 관리자 양쪽에서 사용하는 공통 메서드.
     * 비활성 정책(is_active=false)은 제외된다.</p>
     *
     * <p>정렬 순서: actionCategory ASC → thresholdCount ASC (0 이 먼저, 마일스톤은 임계치 오름차순)
     * → actionType ASC. 카테고리 내에서 일반 활동이 먼저, 그 다음 작은 임계치 마일스톤부터 표시된다.</p>
     *
     * @param category 카테고리 필터 (null 이면 전체)
     * @return 정책 응답 목록 (없으면 빈 리스트)
     */
    public List<PolicyResponse> listActivePolicies(String category) {
        log.debug("리워드 정책 카탈로그 조회 — category={}", category);

        /* 카테고리 지정 시 인덱스(idx_policy_category) 를 타고, 아니면 is_active 인덱스 사용 */
        List<RewardPolicy> policies = (category != null && !category.isBlank())
                ? rewardPolicyRepository.findByActionCategoryAndIsActiveTrue(category.trim())
                : rewardPolicyRepository.findByIsActiveTrue();

        return policies.stream()
                .sorted(Comparator
                        .comparing(RewardPolicy::getActionCategory, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(p -> p.getThresholdCount() != null ? p.getThresholdCount() : 0)
                        .thenComparing(RewardPolicy::getActionType))
                .map(PolicyResponse::from)
                .toList()
                ;
    }

    // ══════════════════════════════════════════════
    // 2) 사용자 종합 리워드 현황 조회 (본인 or 관리자 대행)
    // ══════════════════════════════════════════════

    /**
     * 특정 사용자의 리워드 진행 현황을 종합 조회한다.
     *
     * <p>반환 구조:</p>
     * <ol>
     *   <li>{@code activities} — 일반 활동별 카운터 (리뷰/출석/댓글 등) + 일일 한도 정보</li>
     *   <li>{@code milestones} — threshold 기반 마일스톤 진행률 (parent 카운터 대비 달성률)</li>
     * </ol>
     *
     * <p>정책이 있지만 UserActivityProgress 행이 아직 없는 경우에도 "카운터 0" 상태로 응답에 포함한다.
     * (UI 에서 "아직 시도 안 한 활동" 도 지급 기준과 함께 보여주기 위함)</p>
     *
     * @param userId 대상 사용자 ID
     * @return 종합 리워드 현황 응답
     */
    public UserRewardStatusResponse getUserRewardStatus(String userId) {
        log.debug("사용자 리워드 현황 조회 — userId={}", userId);

        /* 1. 활성 정책 전체 로드 (일반 활동 + 마일스톤 모두 포함) */
        List<RewardPolicy> policies = rewardPolicyRepository.findByIsActiveTrue();

        /* 2. 사용자의 모든 활동 진행도 로드 → (actionType → progress) 맵 구성 */
        List<UserActivityProgress> progresses = progressRepository.findAllByUserId(userId);
        Map<String, UserActivityProgress> progressByAction = new HashMap<>(progresses.size());
        for (UserActivityProgress p : progresses) {
            progressByAction.put(p.getActionType(), p);
        }

        /* 3. 정책을 "일반 활동"과 "마일스톤" 으로 분리 */
        List<ActivityProgressResponse> activities = new java.util.ArrayList<>();
        List<MilestoneProgressResponse> milestones = new java.util.ArrayList<>();

        for (RewardPolicy policy : policies) {
            boolean isMilestone = policy.isThresholdBased();
            if (isMilestone) {
                /* 마일스톤 — parent 의 카운터를 참조하여 진행률 계산 */
                int currentValue = resolveMilestoneCurrentValue(policy, progressByAction);
                milestones.add(MilestoneProgressResponse.of(policy, currentValue));
            } else {
                /* 일반 활동 — 자기 자신의 UserActivityProgress 조회 */
                UserActivityProgress progress = progressByAction.get(policy.getActionType());
                activities.add(ActivityProgressResponse.of(policy, progress, policy.getActionType()));
            }
        }

        /* 정렬: 일반 활동은 카테고리 → actionType, 마일스톤은 parent → threshold 오름차순 */
        activities.sort(Comparator
                .comparing(ActivityProgressResponse::actionCategory, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ActivityProgressResponse::actionType));
        milestones.sort(Comparator
                .comparing(MilestoneProgressResponse::parentActionType, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(m -> m.thresholdCount() != null ? m.thresholdCount() : 0));

        /* 4. UserPoint 요약 (잔액/누적/등급) — 미존재 시 0 기본값 */
        UserPoint userPoint = userPointRepository.findByUserId(userId).orElse(null);
        Integer balance = userPoint != null ? userPoint.getBalance() : 0;
        Integer totalEarned = userPoint != null ? userPoint.getTotalEarned() : 0;
        Integer earnedByActivity = userPoint != null ? userPoint.getEarnedByActivity() : 0;
        String gradeCode = userPoint != null ? userPoint.getGradeCode() : "NORMAL";

        return new UserRewardStatusResponse(
                userId,
                totalEarned,
                earnedByActivity,
                balance,
                gradeCode,
                activities,
                milestones
        );
    }

    // ══════════════════════════════════════════════
    // 내부 헬퍼
    // ══════════════════════════════════════════════

    /**
     * 마일스톤 정책의 현재값을 parent 의 카운터에서 추출한다.
     *
     * <ul>
     *   <li>{@code threshold_target=TOTAL}  → parent.totalCount</li>
     *   <li>{@code threshold_target=DAILY}  → parent.dailyCount</li>
     *   <li>{@code threshold_target=STREAK} → parent.currentStreak</li>
     * </ul>
     *
     * <p>parent 행이 없으면(= 사용자가 해당 활동을 아직 안 했으면) 0 을 반환한다.</p>
     *
     * @param milestonePolicy  마일스톤 정책 (threshold_target 이 NULL 이 아님)
     * @param progressByAction 사용자의 actionType → progress 맵
     * @return 현재값 (parent 미존재 시 0)
     */
    private int resolveMilestoneCurrentValue(RewardPolicy milestonePolicy,
                                             Map<String, UserActivityProgress> progressByAction) {
        String parentActionType = milestonePolicy.getParentActionType();
        if (parentActionType == null) {
            return 0;
        }

        UserActivityProgress parent = progressByAction.get(parentActionType);
        if (parent == null) {
            return 0;
        }

        String target = milestonePolicy.getThresholdTarget();
        if (target == null) {
            return 0;
        }

        /* target 별로 적절한 카운터 선택 */
        return switch (target) {
            case "TOTAL" -> parent.getTotalCount() != null ? parent.getTotalCount() : 0;
            case "DAILY" -> parent.getDailyCount() != null ? parent.getDailyCount() : 0;
            case "STREAK" -> parent.getCurrentStreak() != null ? parent.getCurrentStreak() : 0;
            default -> 0;
        };
    }
}
