package com.monglepick.monglepickbackend.domain.playlist.mapper;

import com.monglepick.monglepickbackend.domain.playlist.entity.Playlist;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 플레이리스트 MyBatis Mapper.
 *
 * <p>playlist, playlist_item 테이블의 CRUD를 담당한다.
 * 사용자 큐레이션 플레이리스트 생성/조회/수정/삭제 및 아이템 관리를 지원한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/playlist/PlaylistMapper.xml}</p>
 */
@Mapper
public interface PlaylistMapper {

    // ── Playlist CRUD ──

    /** PK로 플레이리스트 조회 */
    Playlist findById(@Param("playlistId") Long playlistId);

    /** 사용자의 플레이리스트 목록 조회 */
    List<Playlist> findByUserId(@Param("userId") String userId);

    /** 공개 플레이리스트 목록 조회 (페이징) */
    List<Playlist> findPublicPlaylists(@Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 공개 플레이리스트 총 건수 */
    long countPublicPlaylists();

    /** 플레이리스트 생성 (INSERT) */
    void insertPlaylist(Playlist playlist);

    /** 플레이리스트 수정 (UPDATE) */
    void updatePlaylist(Playlist playlist);

    /** 플레이리스트 삭제 */
    void deletePlaylist(@Param("playlistId") Long playlistId);

    // ── PlaylistItem CRUD ──

    /** 플레이리스트 내 아이템 목록 조회 (정렬 순서대로) */
    List<PlaylistItem> findItemsByPlaylistId(@Param("playlistId") Long playlistId);

    /** 플레이리스트에 아이템 추가 (INSERT) */
    void insertItem(PlaylistItem item);

    /** 플레이리스트에서 아이템 삭제 */
    void deleteItem(@Param("playlistItemId") Long playlistItemId);

    /** 플레이리스트 내 영화 중복 확인 */
    boolean existsItem(@Param("playlistId") Long playlistId,
                       @Param("movieId") String movieId);
}
