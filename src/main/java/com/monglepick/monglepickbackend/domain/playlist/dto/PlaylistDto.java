package com.monglepick.monglepickbackend.domain.playlist.dto;

import com.monglepick.monglepickbackend.domain.playlist.entity.Playlist;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 플레이리스트 도메인 DTO 모음 (record 기반).
 *
 * <p>요청/응답 DTO를 하나의 파일에 Inner Record로 묶어 관리한다.
 * 엔티티로부터 DTO를 생성하는 정적 팩토리 메서드({@code from})를 각 응답 DTO에 제공한다.</p>
 *
 * <h3>요청 DTO</h3>
 * <ul>
 *   <li>{@link CreateRequest} — 플레이리스트 생성</li>
 *   <li>{@link UpdateRequest} — 플레이리스트 수정</li>
 *   <li>{@link AddMovieRequest} — 영화 추가</li>
 * </ul>
 *
 * <h3>응답 DTO</h3>
 * <ul>
 *   <li>{@link PlaylistResponse} — 목록 조회용 (요약 정보)</li>
 *   <li>{@link PlaylistDetailResponse} — 상세 조회용 (아이템 포함)</li>
 *   <li>{@link PlaylistItemResponse} — 개별 아이템 정보</li>
 * </ul>
 */
public class PlaylistDto {

    // ─────────────────────────────────────────────
    // 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 플레이리스트 생성 요청 DTO.
     *
     * <p>{@code POST /api/v1/playlists} 요청 바디에 사용된다.
     * playlistName은 필수이며, description/isPublic/coverImageUrl은 선택적으로 입력 가능하다.</p>
     *
     * <h3>예시 (JSON)</h3>
     * <pre>{@code
     * {
     *   "playlistName": "크리스마스 영화 모음",
     *   "description": "겨울밤에 보기 좋은 영화들",
     *   "isPublic": true,
     *   "coverImageUrl": "https://example.com/cover.jpg"
     * }
     * }</pre>
     *
     * @param playlistName  플레이리스트 이름 (필수, 1~200자)
     * @param description   플레이리스트 설명 (선택)
     * @param isPublic      공개 여부 (선택, 기본값: false)
     * @param coverImageUrl 커버 이미지 URL (선택, 최대 500자)
     */
    public record CreateRequest(

            /** 플레이리스트 이름 (공백 불가, 최대 200자) */
            @NotBlank(message = "플레이리스트 이름은 필수입니다")
            @Size(max = 200, message = "플레이리스트 이름은 최대 200자입니다")
            String playlistName,

            /** 플레이리스트 설명 (null 허용) */
            String description,

            /** 공개 여부 (null이면 false로 처리) */
            Boolean isPublic,

            /** 커버 이미지 URL (null 허용, 최대 500자) */
            @Size(max = 500, message = "커버 이미지 URL은 최대 500자입니다")
            String coverImageUrl

    ) {}

    /**
     * 플레이리스트 수정 요청 DTO.
     *
     * <p>{@code PUT /api/v1/playlists/{playlistId}} 요청 바디에 사용된다.
     * 모든 필드는 선택적이며, null로 전송한 필드는 기존 값을 유지하도록
     * 서비스 레이어에서 null-safe 처리한다.</p>
     *
     * <h3>예시 (JSON)</h3>
     * <pre>{@code
     * {
     *   "playlistName": "겨울 영화 모음",
     *   "description": "따뜻한 겨울밤에 보기 좋은 영화들",
     *   "isPublic": true
     * }
     * }</pre>
     *
     * @param playlistName 변경할 플레이리스트 이름 (null 허용 — 변경 안 함)
     * @param description  변경할 설명 (null 허용)
     * @param isPublic     공개 여부 변경 (null 허용)
     */
    public record UpdateRequest(

            /** 변경할 플레이리스트 이름 (null 허용, 최대 200자) */
            @Size(max = 200, message = "플레이리스트 이름은 최대 200자입니다")
            String playlistName,

            /** 변경할 설명 (null 허용) */
            String description,

            /** 변경할 공개 여부 (null 허용) */
            Boolean isPublic

    ) {}

    /**
     * 플레이리스트 영화 추가 요청 DTO.
     *
     * <p>{@code POST /api/v1/playlists/{playlistId}/movies} 요청 바디에 사용된다.</p>
     *
     * <h3>예시 (JSON)</h3>
     * <pre>{@code
     * { "movieId": "tmdb_12345" }
     * }</pre>
     *
     * @param movieId 추가할 영화 ID (필수, VARCHAR(50) 기준)
     */
    public record AddMovieRequest(

            /** 추가할 영화 ID (필수) */
            @NotNull(message = "영화 ID는 필수입니다")
            @NotBlank(message = "영화 ID는 필수입니다")
            String movieId

    ) {}

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 플레이리스트 목록 조회 응답 DTO (요약 정보).
     *
     * <p>{@code GET /api/v1/playlists} 페이징 응답의 content 항목으로 사용된다.
     * 상세 아이템 목록은 포함하지 않는다.</p>
     *
     * @param playlistId   플레이리스트 고유 ID
     * @param playlistName 플레이리스트 이름
     * @param description  플레이리스트 설명 (null 가능)
     * @param isPublic     공개 여부
     * @param likeCount    좋아요 수
     * @param coverImageUrl 커버 이미지 URL (null 가능)
     * @param createdAt    생성 시각
     */
    public record PlaylistResponse(

            /** 플레이리스트 고유 ID */
            Long playlistId,

            /** 플레이리스트 이름 */
            String playlistName,

            /** 플레이리스트 설명 (없으면 null) */
            String description,

            /** 공개 여부 */
            Boolean isPublic,

            /** 좋아요 수 */
            Integer likeCount,

            /** 커버 이미지 URL (없으면 null) */
            String coverImageUrl,

            /** 다른 사용자 플레이리스트를 가져온(복사한) 경우 true */
            Boolean isImported,

            /** 생성 시각 */
            LocalDateTime createdAt

    ) {
        /**
         * Playlist 엔티티로부터 목록 응답 DTO를 생성한다.
         *
         * @param entity Playlist 엔티티
         * @return 목록 응답 DTO
         */
        public static PlaylistResponse from(Playlist entity) {
            return new PlaylistResponse(
                    entity.getPlaylistId(),
                    entity.getPlaylistName(),
                    entity.getDescription(),
                    entity.getIsPublic(),
                    entity.getLikeCount(),
                    entity.getCoverImageUrl(),
                    Boolean.TRUE.equals(entity.getIsImported()),
                    entity.getCreatedAt()
            );
        }
    }

    /**
     * 플레이리스트 상세 조회 응답 DTO (아이템 목록 포함).
     *
     * <p>{@code GET /api/v1/playlists/{playlistId}} 응답에 사용된다.
     * 플레이리스트 기본 정보와 포함된 영화 아이템 목록을 함께 반환한다.</p>
     *
     * @param playlistId   플레이리스트 고유 ID
     * @param playlistName 플레이리스트 이름
     * @param description  플레이리스트 설명 (null 가능)
     * @param isPublic     공개 여부
     * @param likeCount    좋아요 수
     * @param coverImageUrl 커버 이미지 URL (null 가능)
     * @param items        포함된 영화 아이템 목록 (sort_order 오름차순 정렬)
     * @param createdAt    생성 시각
     */
    public record PlaylistDetailResponse(

            /** 플레이리스트 고유 ID */
            Long playlistId,

            /** 플레이리스트 이름 */
            String playlistName,

            /** 플레이리스트 설명 (없으면 null) */
            String description,

            /** 공개 여부 */
            Boolean isPublic,

            /** 좋아요 수 */
            Integer likeCount,

            /** 커버 이미지 URL (없으면 null) */
            String coverImageUrl,

            /** 포함된 영화 아이템 목록 (sort_order 오름차순) */
            List<PlaylistItemResponse> items,

            /** 다른 사용자 플레이리스트를 가져온(복사한) 경우 true */
            Boolean isImported,

            /** 생성 시각 */
            LocalDateTime createdAt

    ) {
        /**
         * Playlist 엔티티와 아이템 응답 DTO 목록으로 상세 응답 DTO를 생성한다.
         *
         * @param entity    Playlist 엔티티
         * @param itemDtos  아이템 응답 DTO 목록 (서비스 레이어에서 변환 후 전달)
         * @return 상세 응답 DTO
         */
        public static PlaylistDetailResponse from(Playlist entity, List<PlaylistItemResponse> itemDtos) {
            return new PlaylistDetailResponse(
                    entity.getPlaylistId(),
                    entity.getPlaylistName(),
                    entity.getDescription(),
                    entity.getIsPublic(),
                    entity.getLikeCount(),
                    entity.getCoverImageUrl(),
                    itemDtos,
                    Boolean.TRUE.equals(entity.getIsImported()),
                    entity.getCreatedAt()
            );
        }
    }

    /**
     * 플레이리스트 아이템(개별 영화) 응답 DTO.
     *
     * <p>{@link PlaylistDetailResponse}의 {@code items} 필드에 포함된다.</p>
     *
     * @param playlistItemId 아이템 고유 ID
     * @param movieId        영화 ID
     * @param sortOrder      정렬 순서 (낮을수록 앞쪽)
     * @param addedAt        아이템 추가 시각
     */
    public record PlaylistItemResponse(

            /** 아이템 고유 ID */
            Long playlistItemId,

            /** 영화 ID */
            String movieId,

            /** 영화 제목 */
            String title,

            /** 포스터 이미지 경로 */
            String posterPath,

            /** 평균 평점 */
            Double rating,

            /** 정렬 순서 (낮을수록 앞쪽에 표시) */
            Integer sortOrder,

            /** 아이템 추가 시각 */
            LocalDateTime addedAt

    ) {
        /**
         * PlaylistItem 엔티티로부터 아이템 응답 DTO를 생성한다.
         *
         * @param entity PlaylistItem 엔티티
         * @return 아이템 응답 DTO
         */
        public static PlaylistItemResponse from(PlaylistItem entity) {
            return new PlaylistItemResponse(
                    entity.getPlaylistItemId(),
                    entity.getMovieId(),
                    entity.getTitle(),
                    entity.getPosterPath(),
                    entity.getRating(),
                    entity.getSortOrder(),
                    entity.getAddedAt()
            );
        }
    }

    /**
     * 플레이리스트 생성 응답 DTO.
     *
     * <p>{@code POST /api/v1/playlists} 성공 시 생성된 플레이리스트 ID를 반환한다.</p>
     *
     * @param playlistId 생성된 플레이리스트 고유 ID
     */
    public record CreateResponse(

            /** 생성된 플레이리스트 고유 ID */
            Long playlistId

    ) {}

    /**
     * 커뮤니티에 공유된 플레이리스트 요약 정보 DTO.
     *
     * <p>PLAYLIST_SHARE 카테고리 게시글 응답({@code PostResponse})에 인라인으로 포함된다.
     * PostMapper의 findPlaylistSharePostsWithDetail 쿼리에서 JOIN playlist로 채워진다.</p>
     *
     * @param playlistId    플레이리스트 고유 ID
     * @param playlistName  플레이리스트 이름
     * @param description   플레이리스트 설명 (null 가능)
     * @param coverImageUrl 커버 이미지 URL (null 가능)
     * @param likeCount     플레이리스트 좋아요 수
     * @param movieCount    포함된 영화 수
     */
    public record SharedPlaylistInfo(
            Long playlistId,
            String playlistName,
            String description,
            String coverImageUrl,
            Integer likeCount,
            Integer movieCount
    ) {}

    /**
     * 플레이리스트 가져오기(복사) 응답 DTO.
     *
     * <p>{@code POST /api/v1/playlists/{playlistId}/import} 성공 시
     * 새로 생성된 내 플레이리스트 ID를 반환한다.</p>
     *
     * @param newPlaylistId 복사 생성된 내 플레이리스트 고유 ID
     */
    public record ImportResponse(
            Long newPlaylistId
    ) {}
}
