package com.monglepick.monglepickbackend.global.dto;

/**
 * 인스타그램 스타일 좋아요 토글 공통 응답 DTO.
 *
 * <p>리뷰·게시글·댓글 등 모든 좋아요 토글 API에서 공통으로 사용하는 응답 객체.
 * 단일 POST 호출로 liked/unliked 상태를 전환하며, 토글 후 현재 상태와 전체 카운트를 반환한다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>
 *   POST /api/v1/posts/1/like
 *   → { "liked": true,  "likeCount": 42 }  // 처음 누른 경우 (좋아요 등록)
 *   → { "liked": false, "likeCount": 41 }  // 다시 누른 경우 (좋아요 취소)
 * </pre>
 *
 * @param liked     토글 후 현재 좋아요 상태 (true=좋아요됨, false=취소됨)
 * @param likeCount 토글 후 해당 게시물의 전체 좋아요 수
 */
public record LikeToggleResponse(
        /** 토글 후 현재 좋아요 상태 (true=좋아요됨, false=취소됨) */
        boolean liked,
        /** 토글 후 해당 게시물의 전체 좋아요 수 */
        long likeCount
) {

    /**
     * 정적 팩토리 메서드 — 서비스 레이어에서 간결하게 생성할 수 있도록 제공.
     *
     * @param liked     토글 결과 좋아요 여부
     * @param likeCount 현재 전체 좋아요 수
     * @return LikeToggleResponse 인스턴스
     */
    public static LikeToggleResponse of(boolean liked, long likeCount) {
        return new LikeToggleResponse(liked, likeCount);
    }
}
