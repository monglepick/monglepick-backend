package com.monglepick.monglepickbackend.domain.review.dto;

/**
 * 리뷰 투표 응답 DTO.
 *
 * <p>특정 리뷰의 "도움이 됐어요 / 도움이 안 됐어요" 집계 결과와
 * 현재 요청 사용자의 투표 상태를 함께 반환한다.</p>
 *
 * <h3>필드 설명</h3>
 * <ul>
 *   <li>{@code helpfulCount}   — "도움이 됐어요" 투표 수 (helpful = true)</li>
 *   <li>{@code unhelpfulCount} — "도움이 안 됐어요" 투표 수 (helpful = false)</li>
 *   <li>{@code myVote}         — 현재 사용자의 투표 값 (null=미투표, true=도움됨, false=도움안됨)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 투표 처리 후 즉시 최신 집계 반환
 * ReviewVoteResponse.of(helpfulCount, unhelpfulCount, true)
 *
 * // 미투표 상태로 현황 조회 (GET 엔드포인트)
 * ReviewVoteResponse.of(helpfulCount, unhelpfulCount, null)
 * }</pre>
 *
 * @param helpfulCount   "도움이 됐어요" 투표 수
 * @param unhelpfulCount "도움이 안 됐어요" 투표 수
 * @param myVote         현재 사용자의 투표 (null=미투표, true=도움됨, false=도움안됨)
 */
public record ReviewVoteResponse(
        long helpfulCount,
        long unhelpfulCount,
        Boolean myVote
) {

    /**
     * 정적 팩토리 메서드 — 집계 결과와 현재 사용자 투표값으로 응답 객체를 생성한다.
     *
     * @param helpfulCount   "도움이 됐어요" 투표 수
     * @param unhelpfulCount "도움이 안 됐어요" 투표 수
     * @param myVote         현재 사용자의 투표 (null 허용)
     * @return ReviewVoteResponse 인스턴스
     */
    public static ReviewVoteResponse of(long helpfulCount, long unhelpfulCount, Boolean myVote) {
        return new ReviewVoteResponse(helpfulCount, unhelpfulCount, myVote);
    }
}
