package com.monglepick.monglepickbackend.domain.playlist.mapper;

import com.monglepick.monglepickbackend.domain.playlist.entity.Playlist;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistItem;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 플레이리스트 MyBatis Mapper.
 *
 * <p>playlist, playlist_item, playlist_likes 세 테이블의 CRUD + 원자적 카운터 업데이트를
 * 통합하여 담당한다. 사용자 큐레이션 플레이리스트의 생성/조회/수정/삭제, 아이템 관리,
 * 좋아요 토글을 모두 지원한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/playlist/PlaylistMapper.xml}</p>
 *
 * <p><b>JPA/MyBatis 하이브리드 (§15)</b>: Playlist/PlaylistItem/PlaylistLike {@code @Entity}는
 * DDL 정의 전용으로만 사용되며 데이터 R/W는 이 Mapper로 100% 처리한다.
 * JpaRepository는 작성/사용하지 않는다.</p>
 */
@Mapper
public interface PlaylistMapper {

    // ═══ Playlist 조회 ═══

    /** PK로 플레이리스트 조회 (없으면 null) */
    Playlist findById(@Param("playlistId") Long playlistId);

    /**
     * 사용자의 플레이리스트 목록 조회 (페이징, 최신순).
     *
     * <p>offset/limit은 Spring Pageable에서 추출해 Service에서 전달한다.
     * 전체 건수는 {@link #countByUserId}로 별도 조회해 PageImpl로 조립한다.</p>
     */
    List<Playlist> findByUserId(@Param("userId") String userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /** 사용자의 플레이리스트 총 건수 — 페이지 조립용 */
    long countByUserId(@Param("userId") String userId);

    /** 공개 플레이리스트 목록 조회 (페이징) */
    List<Playlist> findPublicPlaylists(@Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 공개 플레이리스트 총 건수 */
    long countPublicPlaylists();

    // ═══ Playlist 쓰기 ═══

    /** 플레이리스트 생성 (INSERT) — useGeneratedKeys로 playlistId 자동 세팅 */
    void insertPlaylist(Playlist playlist);

    /** 플레이리스트 수정 (UPDATE) — 이름/설명/공개여부/커버이미지 */
    void updatePlaylist(Playlist playlist);

    /** 플레이리스트 삭제 (playlist_item은 ON DELETE CASCADE) */
    void deletePlaylist(@Param("playlistId") Long playlistId);

    /** 좋아요 수를 원자적으로 1 증가 (race condition 방지) */
    void incrementLikeCount(@Param("playlistId") Long playlistId);

    /** 좋아요 수를 원자적으로 1 감소 (0 미만 방지 CASE 구문) */
    void decrementLikeCount(@Param("playlistId") Long playlistId);

    // ═══ PlaylistItem ═══

    /** 플레이리스트 내 아이템 목록 조회 (sort_order 오름차순) */
    List<PlaylistItem> findItemsByPlaylistId(@Param("playlistId") Long playlistId);

    /** 플레이리스트에 아이템 추가 (INSERT) */
    void insertItem(PlaylistItem item);

    /** 플레이리스트에서 아이템 삭제 */
    void deleteItem(@Param("playlistItemId") Long playlistItemId);

    /** 플레이리스트 내 영화 중복 확인 — UNIQUE 제약 전 사전 검증 */
    boolean existsItem(@Param("playlistId") Long playlistId,
                       @Param("movieId") String movieId);

    /** 특정 영화의 PlaylistItem 단건 조회 (삭제 대상 식별용, 없으면 null) */
    PlaylistItem findItemByPlaylistIdAndMovieId(@Param("playlistId") Long playlistId,
                                                 @Param("movieId") String movieId);

    /**
     * 삭제된 아이템보다 뒤에 있는 아이템들의 sort_order를 1씩 당긴다 (hole 제거).
     *
     * <p>예: sort_order [0,1,2,3]에서 1 삭제 → [0,2,3] → 이 메서드로 [0,1,2] 정규화.</p>
     */
    void shiftSortOrderDown(@Param("playlistId") Long playlistId,
                             @Param("deletedSortOrder") int deletedSortOrder);

    // ═══ PlaylistLike ═══

    /** 사용자가 특정 플레이리스트에 이미 좋아요를 눌렀는지 확인 */
    boolean existsLikeByPlaylistIdAndUserId(@Param("playlistId") Long playlistId,
                                             @Param("userId") String userId);

    /** 좋아요 레코드 단건 조회 (취소 대상 식별용, 없으면 null) */
    PlaylistLike findLikeByPlaylistIdAndUserId(@Param("playlistId") Long playlistId,
                                                @Param("userId") String userId);

    /** 좋아요 레코드 생성 (INSERT) */
    void insertLike(PlaylistLike like);

    /** 좋아요 레코드 삭제 */
    void deleteLike(@Param("playlistLikeId") Long playlistLikeId);
}
