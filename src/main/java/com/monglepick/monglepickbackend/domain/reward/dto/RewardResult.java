package com.monglepick.monglepickbackend.domain.reward.dto;

/**
 * 리워드 지급 결과 DTO.
 *
 * <p>{@code RewardService.grantReward()} 호출 후 지급 성공 여부와 지급된 포인트를
 * 호출자(ReviewService, PostService 등)에 반환한다. 호출자는 이 정보를 API 응답 DTO에
 * 포함하여 클라이언트가 리워드 획득 알림을 표시할 수 있게 한다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * RewardResult result = rewardService.grantReward(userId, "REVIEW_CREATE", ref, len);
 * // result.earned() == true이면 result.points()만큼 지급됨
 * }</pre>
 *
 * @param earned     리워드 지급 여부 (조건 미충족/비활성/예외 시 false)
 * @param points     지급된 포인트 (미지급 시 0)
 * @param policyName 정책 한국어 이름 (클라이언트 표시용, 예: "리뷰 작성")
 */
public record RewardResult(
        boolean earned,
        int points,
        String policyName
) {
    /** 리워드 미지급 (정책 없음, 조건 미충족, 예외 등) */
    public static final RewardResult EMPTY = new RewardResult(false, 0, null);

    /** 리워드 지급 성공 팩토리 메서드 */
    public static RewardResult of(int points, String policyName) {
        return new RewardResult(points > 0, points, policyName);
    }
}
