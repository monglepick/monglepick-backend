package com.monglepick.monglepickbackend.domain.community.dto;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.playlist.dto.PlaylistDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 응답 DTO
 *
 * @param id           게시글 ID
 * @param title        제목
 * @param content      본문
 * @param category     카테고리
 * @param author       작성자 닉네임
 * @param authorId     작성자 ID (본인 글 여부 확인용)
 * @param viewCount    조회수
 * @param likeCount    좋아요 수
 * @param commentCount 댓글 수
 * @param status       게시글 상태 (DRAFT / PUBLISHED)
 * @param createdAt    작성 시각
 * @param playlistId   연결된 플레이리스트 ID (PLAYLIST_SHARE 전용, 나머지 null)
 * @param playlistInfo 플레이리스트 상세 정보 (PLAYLIST_SHARE 전용, 나머지 null)
 * @param rewardPoints 리워드 지급 포인트 (미지급 시 0, 조회 응답 시 null)
 * @param imageUrls    첨부 이미지 URL 목록
 *                     로컬: http://localhost:8080/images/userId/파일명.jpg
 *                     서버: http://210.109.15.187/images/userId/파일명.jpg
 *                     추후 S3 전환 시 URL만 변경, 구조 동일
 */
public record PostResponse(
        Long id,
        String title,
        String content,
        String category,
        String author,
        String authorId,        // ✅ 추가
        int viewCount,
        int likeCount,
        int commentCount,
        String status,
        LocalDateTime createdAt,
        Long playlistId,
        PlaylistDto.SharedPlaylistInfo playlistInfo,
        Integer rewardPoints,
        List<String> imageUrls  // ✅ 추가
) {
    public static PostResponse from(Post post) {
        return from(post, null);
    }

    public static PostResponse from(Post post, Integer rewardPoints) {
        String nickname = post.getNickname() != null ? post.getNickname() : "알 수 없음";

        PlaylistDto.SharedPlaylistInfo playlistInfo = null;
        if (post.getCategory() == Post.Category.PLAYLIST_SHARE && post.getPlaylistId() != null) {
            playlistInfo = new PlaylistDto.SharedPlaylistInfo(
                    post.getPlaylistId(),
                    post.getPlaylistName(),
                    post.getPlaylistDescription(),
                    post.getPlaylistCoverImageUrl(),
                    post.getPlaylistLikeCount(),
                    post.getPlaylistMovieCount()
            );
        }

        // imageUrls: DB에 콤마 구분 문자열로 저장 → List<String>으로 변환
        // 예: "http://localhost:8080/images/userId/a.jpg,http://localhost:8080/images/userId/b.jpg"
        // 추후 S3 전환 시 URL 형식만 바뀌고 이 코드는 그대로 유지
        List<String> imageUrlList = (post.getImageUrls() != null && !post.getImageUrls().isBlank())
                ? List.of(post.getImageUrls().split(","))
                : List.of();

        return new PostResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory().name(),
                nickname,
                post.getUserId(),       // ✅ authorId
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getStatus().name(),
                post.getCreatedAt(),
                post.getPlaylistId(),
                playlistInfo,
                rewardPoints,
                imageUrlList            // ✅ imageUrls
        );
    }
}