package com.monglepick.monglepickbackend.domain.community.dto;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.playlist.dto.PlaylistDto;

import java.time.LocalDateTime;

/**
 * 게시글 응답 DTO
 *
 * @param id           게시글 ID
 * @param title        제목
 * @param content      본문
 * @param category     카테고리
 * @param author       작성자 닉네임
 * @param viewCount    조회수
 * @param likeCount    좋아요 수
 * @param commentCount 댓글 수
 * @param status       게시글 상태 (DRAFT / PUBLISHED)
 * @param createdAt    작성 시각
 * @param playlistId   연결된 플레이리스트 ID (PLAYLIST_SHARE 전용, 나머지 null)
 * @param playlistInfo 플레이리스트 상세 정보 (PLAYLIST_SHARE 전용, 나머지 null)
 */
public record PostResponse(
        Long id,
        String title,
        String content,
        String category,
        String author,
        int viewCount,
        int likeCount,
        int commentCount,
        String status,
        LocalDateTime createdAt,
        Long playlistId,
        PlaylistDto.SharedPlaylistInfo playlistInfo
) {
    /**
     * Post 엔티티를 PostResponse로 변환하는 팩토리 메서드.
     *
     * <p>{@link Post#getNickname()}은 MyBatis PostMapper의 JOIN users 쿼리 결과로 채워진다.
     * JOIN 없이 로드된 Post 객체에서는 null이 될 수 있으며 이 경우 "알 수 없음"으로 표시한다.</p>
     *
     * <p>PLAYLIST_SHARE 카테고리일 때는 {@code playlistInfo} 필드에 플레이리스트 상세 정보가
     * 담긴다 (findPlaylistSharePostsWithDetail 쿼리 결과에서 @Transient 필드로 채워진 경우).</p>
     */
    public static PostResponse from(Post post) {
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

        return new PostResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory().name(),
                nickname,
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getStatus().name(),
                post.getCreatedAt(),
                post.getPlaylistId(),
                playlistInfo
        );
    }
}
