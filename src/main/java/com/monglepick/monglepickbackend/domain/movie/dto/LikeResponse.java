package com.monglepick.monglepickbackend.domain.movie.dto;

/**
 * 영화 좋아요 응답 DTO.
 *
 * <p>toggleLike, isLiked 등 좋아요 관련 API의 공통 응답 형식이다.
 * 클라이언트는 이 DTO 하나로 현재 좋아요 상태와 전체 좋아요 수를 한 번에 받는다.</p>
 *
 * <h3>사용 시나리오</h3>
 * <ul>
 *   <li>POST /api/v1/movies/{movieId}/like — 토글 후 변경된 상태 반환</li>
 *   <li>GET  /api/v1/movies/{movieId}/like — 현재 사용자의 좋아요 상태 조회</li>
 *   <li>GET  /api/v1/movies/{movieId}/like/count — 공개 좋아요 수 조회 (liked=false 고정)</li>
 * </ul>
 *
 * @param liked     현재 사용자의 활성 좋아요 여부 (true=좋아요 활성, false=취소 또는 미로그인)
 * @param likeCount 해당 영화의 전체 활성 좋아요 수
 */
public record LikeResponse(
        boolean liked,
        long likeCount
) {

    /**
     * 좋아요 상태와 카운트로 LikeResponse를 생성하는 팩토리 메서드.
     *
     * @param liked     현재 사용자의 좋아요 활성 여부
     * @param likeCount 해당 영화의 전체 활성 좋아요 수
     * @return LikeResponse 인스턴스
     */
    public static LikeResponse of(boolean liked, long likeCount) {
        return new LikeResponse(liked, likeCount);
    }
}
