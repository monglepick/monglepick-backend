package com.monglepick.monglepickbackend.domain.reward.dto;

import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicy;
import com.monglepick.monglepickbackend.domain.reward.entity.UserActivityProgress;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리워드 카탈로그/진행 현황 DTO 모음.
 *
 * <p>사용자/관리자 양쪽에서 "리워드 지급 기준"(정책 카탈로그)과
 * "내가(또는 특정 유저가) 지금까지 어떤 리워드를 얼마나 받았는지"
 * (활동 진행도 + 마일스톤 진행률) 를 조회할 때 사용하는 읽기 전용 응답 DTO 집합이다.</p>
 *
 * <h3>구성</h3>
 * <ul>
 *   <li>{@link PolicyResponse} — 리워드 정책 단건 (reward_policy 테이블 1행)</li>
 *   <li>{@link ActivityProgressResponse} — 사용자의 활동별 진행도 (user_activity_progress 1행)</li>
 *   <li>{@link MilestoneProgressResponse} — threshold 기반 마일스톤 진행률 (parent 의 카운터 대비 달성률)</li>
 *   <li>{@link UserRewardStatusResponse} — 위 3종 통합 응답 (활동/마일스톤 목록 + 요약)</li>
 * </ul>
 *
 * <h3>사용 경로</h3>
 * <ul>
 *   <li>GET /api/v1/point/policies                  → List&lt;PolicyResponse&gt; (클라이언트용 카탈로그)</li>
 *   <li>GET /api/v1/point/progress                  → UserRewardStatusResponse (내 진행 현황)</li>
 *   <li>GET /api/v1/admin/users/{userId}/rewards    → UserRewardStatusResponse (관리자용 동일 구조)</li>
 * </ul>
 */
public final class RewardCatalogDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private RewardCatalogDto() {
    }

    // ──────────────────────────────────────────────
    // 1) 정책 카탈로그
    // ──────────────────────────────────────────────

    /**
     * 리워드 정책 응답 (단건) — reward_policy 테이블 1행의 공개용 투영.
     *
     * <p>클라이언트 "리워드 지급 기준" 화면에서 카테고리별 카드 그리드로 노출된다.
     * 관리자용 DTO 와 달리 created_by/updated_by 등의 감사 필드는 포함하지 않는다.</p>
     *
     * @param policyId          정책 PK
     * @param actionType        활동 코드 (예: REVIEW_CREATE, ATTENDANCE_STREAK_7)
     * @param activityName      한국어 활동 표시명 (예: "리뷰 작성")
     * @param actionCategory    CONTENT / ENGAGEMENT / MILESTONE / ATTENDANCE
     * @param pointsAmount      1회 지급 포인트 (point_type='earn' 이면 등급 배율 적용 대상)
     * @param pointType         earn | bonus (bonus 는 등급 배율 미적용)
     * @param dailyLimit        일일 최대 지급 횟수 (0 = 무제한)
     * @param maxCount          평생 최대 지급 횟수 (0 = 무제한)
     * @param cooldownSeconds   연속 실행 최소 간격(초) (0 = 없음)
     * @param minContentLength  최소 콘텐츠 길이 (0 = 검사 없음)
     * @param thresholdCount    마일스톤 임계치 (0 = 일반 활동)
     * @param thresholdTarget   TOTAL | DAILY | STREAK | null(일반 활동)
     * @param parentActionType  부모 활동 코드 (threshold 기반 마일스톤의 참조 대상)
     * @param description       관리자 메모 / 사용자 안내 문구
     */
    public record PolicyResponse(
            Long policyId,
            String actionType,
            String activityName,
            String actionCategory,
            Integer pointsAmount,
            String pointType,
            Integer dailyLimit,
            Integer maxCount,
            Integer cooldownSeconds,
            Integer minContentLength,
            Integer thresholdCount,
            String thresholdTarget,
            String parentActionType,
            String description
    ) {
        /**
         * {@link RewardPolicy} 엔티티 → PolicyResponse 변환.
         *
         * @param p 리워드 정책 엔티티 (null 이면 안 됨)
         * @return 공개용 응답 DTO
         */
        public static PolicyResponse from(RewardPolicy p) {
            return new PolicyResponse(
                    p.getPolicyId(),
                    p.getActionType(),
                    p.getActivityName(),
                    p.getActionCategory(),
                    p.getPointsAmount(),
                    p.getPointType(),
                    p.getDailyLimit(),
                    p.getMaxCount(),
                    p.getCooldownSeconds(),
                    p.getMinContentLength(),
                    p.getThresholdCount(),
                    p.getThresholdTarget(),
                    p.getParentActionType(),
                    p.getDescription()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 2) 활동 진행도 (일반 활동 — threshold 없음)
    // ──────────────────────────────────────────────

    /**
     * 사용자 활동 진행도 응답 — user_activity_progress 1행 + 정책 조인 정보.
     *
     * <p>"일반 활동"(리뷰/출석/댓글 등) 의 현재 누적/오늘/리워드 지급 현황을 표현한다.
     * 일일 한도 게이지("오늘 2/3") 와 쿨다운 잔여시간 안내에 사용된다.</p>
     *
     * @param actionType           활동 코드
     * @param activityName         한국어 활동 표시명
     * @param actionCategory       정책 카테고리 (CONTENT/ENGAGEMENT/MILESTONE/ATTENDANCE)
     * @param pointsAmount         1회 지급 포인트 (안내용)
     * @param dailyLimit           정책상 일일 한도 (0 = 무제한)
     * @param maxCount             정책상 평생 한도 (0 = 무제한)
     * @param cooldownSeconds      정책상 쿨다운 (0 = 없음)
     * @param totalCount           누적 활동 횟수 (리워드 지급 무관)
     * @param dailyCount           오늘 활동 횟수
     * @param rewardedTodayCount   오늘 리워드 지급 횟수 (일일 한도 검사 기준)
     * @param rewardedTotalCount   누적 리워드 지급 횟수 (평생 한도 검사 기준)
     * @param currentStreak        현재 연속 일수 (출석/스트릭 활동)
     * @param maxStreak            역대 최고 연속 일수
     * @param lastStreakDate       마지막 연속 기록일
     * @param lastActionAt         마지막 활동 시각 (쿨다운 기준)
     */
    public record ActivityProgressResponse(
            String actionType,
            String activityName,
            String actionCategory,
            Integer pointsAmount,
            Integer dailyLimit,
            Integer maxCount,
            Integer cooldownSeconds,
            Integer totalCount,
            Integer dailyCount,
            Integer rewardedTodayCount,
            Integer rewardedTotalCount,
            Integer currentStreak,
            Integer maxStreak,
            LocalDate lastStreakDate,
            LocalDateTime lastActionAt
    ) {
        /**
         * (RewardPolicy + UserActivityProgress) → ActivityProgressResponse 변환.
         *
         * <p>progress 가 null 이면 (활동 기록이 아직 없는 경우) 카운터는 모두 0 으로 반환한다.</p>
         *
         * @param policy   정책 엔티티 (nullable 가능 — 시드 누락 시 대비)
         * @param progress 활동 진행도 엔티티 (nullable — 아직 활동 이력 없을 때)
         * @param actionType 활동 코드 (policy 가 null 인 경우 폴백용)
         * @return 응답 DTO
         */
        public static ActivityProgressResponse of(RewardPolicy policy,
                                                  UserActivityProgress progress,
                                                  String actionType) {
            /* 정책이 있으면 정책 필드를, 없으면 actionType 만 채운다 */
            String name = policy != null ? policy.getActivityName() : actionType;
            String category = policy != null ? policy.getActionCategory() : null;
            Integer amount = policy != null ? policy.getPointsAmount() : null;
            Integer dailyLimit = policy != null ? policy.getDailyLimit() : 0;
            Integer maxCount = policy != null ? policy.getMaxCount() : 0;
            Integer cooldown = policy != null ? policy.getCooldownSeconds() : 0;

            if (progress == null) {
                /* 활동 이력 없음 — 카운터 전부 0 */
                return new ActivityProgressResponse(
                        actionType, name, category, amount,
                        dailyLimit, maxCount, cooldown,
                        0, 0, 0, 0, 0, 0, null, null
                );
            }

            return new ActivityProgressResponse(
                    actionType,
                    name,
                    category,
                    amount,
                    dailyLimit,
                    maxCount,
                    cooldown,
                    progress.getTotalCount(),
                    progress.getDailyCount(),
                    progress.getRewardedTodayCount(),
                    progress.getRewardedTotalCount(),
                    progress.getCurrentStreak(),
                    progress.getMaxStreak(),
                    progress.getLastStreakDate(),
                    progress.getLastActionAt()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 3) 마일스톤 진행률 (threshold 기반)
    // ──────────────────────────────────────────────

    /**
     * 마일스톤 진행률 응답 — threshold 기반 정책의 현재 달성률.
     *
     * <p>parent_action_type 이 참조하는 UserActivityProgress 의 카운터를 기준으로
     * "현재값 / 임계치" 를 계산한다. STREAK 타입은 current_streak 을, TOTAL 은 total_count 를,
     * DAILY 는 daily_count 를 참조한다.</p>
     *
     * <p>achieved 가 true 가 된 이후에도 행 자체는 유지한다 — UI 에서 "획득 완료" 배지 표시용.</p>
     *
     * @param actionType       마일스톤 활동 코드 (예: REVIEW_MILESTONE_5)
     * @param activityName     한국어 표시명 (예: "리뷰 5회 달성")
     * @param actionCategory   카테고리
     * @param pointsAmount     달성 시 지급 포인트
     * @param thresholdCount   임계치 (예: 5)
     * @param thresholdTarget  TOTAL / DAILY / STREAK
     * @param parentActionType 참조 대상 활동 코드 (예: REVIEW_CREATE)
     * @param currentValue     현재 값 (parent 의 해당 카운터)
     * @param progressPercent  달성률 (0 ~ 100, 정수로 반올림)
     * @param achieved         달성 여부 (currentValue >= thresholdCount)
     * @param description      정책 설명
     */
    public record MilestoneProgressResponse(
            String actionType,
            String activityName,
            String actionCategory,
            Integer pointsAmount,
            Integer thresholdCount,
            String thresholdTarget,
            String parentActionType,
            Integer currentValue,
            Integer progressPercent,
            Boolean achieved,
            String description
    ) {
        /**
         * (RewardPolicy + parent 의 current 값) → MilestoneProgressResponse 변환.
         *
         * @param policy       마일스톤 정책 (threshold_target 이 NULL 이 아님)
         * @param currentValue parent 의 해당 카운터 현재값
         * @return 응답 DTO
         */
        public static MilestoneProgressResponse of(RewardPolicy policy, int currentValue) {
            int threshold = policy.getThresholdCount() != null ? policy.getThresholdCount() : 0;
            /* 0으로 나누기 방지 — threshold=0 이면 진행률 0% */
            int percent = threshold > 0
                    ? Math.min(100, (int) Math.round(currentValue * 100.0 / threshold))
                    : 0;
            boolean achieved = threshold > 0 && currentValue >= threshold;

            return new MilestoneProgressResponse(
                    policy.getActionType(),
                    policy.getActivityName(),
                    policy.getActionCategory(),
                    policy.getPointsAmount(),
                    threshold,
                    policy.getThresholdTarget(),
                    policy.getParentActionType(),
                    currentValue,
                    percent,
                    achieved,
                    policy.getDescription()
            );
        }
    }

    // ──────────────────────────────────────────────
    // 4) 사용자 종합 리워드 현황 (활동 + 마일스톤)
    // ──────────────────────────────────────────────

    /**
     * 사용자 종합 리워드 현황 응답.
     *
     * <p>GET /api/v1/point/progress 및 GET /api/v1/admin/users/{userId}/rewards
     * 의 단일 반환 타입.</p>
     *
     * @param userId            대상 사용자 ID
     * @param totalEarned       누적 획득 포인트 (UserPoint.totalEarned)
     * @param earnedByActivity  활동으로 획득한 누적 포인트 (등급 판정 기준)
     * @param currentBalance    현재 잔액
     * @param gradeCode         현재 등급 코드
     * @param activities        활동별 진행도 목록 (일반 활동만 — threshold_target IS NULL)
     * @param milestones        마일스톤 진행률 목록 (threshold_target IS NOT NULL)
     */
    public record UserRewardStatusResponse(
            String userId,
            Integer totalEarned,
            Integer earnedByActivity,
            Integer currentBalance,
            String gradeCode,
            List<ActivityProgressResponse> activities,
            List<MilestoneProgressResponse> milestones
    ) {
    }
}
