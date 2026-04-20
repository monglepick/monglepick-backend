package com.monglepick.monglepickbackend.domain.playlist.service;

import com.monglepick.monglepickbackend.domain.playlist.dto.PlaylistDto;
import com.monglepick.monglepickbackend.domain.playlist.entity.Playlist;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistItem;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistLike;
import com.monglepick.monglepickbackend.domain.playlist.entity.PlaylistScrap;
import com.monglepick.monglepickbackend.domain.playlist.mapper.PlaylistMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 플레이리스트 비즈니스 로직 서비스.
 *
 * <p>사용자가 생성한 영화 플레이리스트의 CRUD, 영화 추가/제거, 좋아요 토글을 담당한다.
 * 클래스 레벨 {@code @Transactional(readOnly = true)}로 기본 읽기 전용을 설정하고,
 * 쓰기 작업 메서드에는 {@code @Transactional}을 개별 오버라이드한다.</p>
 *
 * <h3>소유권 검증 정책</h3>
 * <ul>
 *   <li>수정/삭제: 플레이리스트 소유자만 가능 — PL002(403)</li>
 *   <li>상세 조회: 공개 플레이리스트는 누구나, 비공개는 소유자만 — PL002(403)</li>
 *   <li>좋아요: 공개 플레이리스트만 가능 — PL007(403)</li>
 * </ul>
 *
 * <h3>주요 에러코드</h3>
 * <ul>
 *   <li>PL001 — 플레이리스트 없음</li>
 *   <li>PL002 — 접근 권한 없음 (소유자 아님)</li>
 *   <li>PL003 — 영화 중복 추가</li>
 *   <li>PL004 — 제거할 영화 없음</li>
 *   <li>PL005 — 좋아요 중복</li>
 *   <li>PL006 — 취소할 좋아요 없음</li>
 *   <li>PL007 — 비공개 플레이리스트</li>
 * </ul>
 *
 * <h3>JPA/MyBatis 하이브리드 (§15)</h3>
 * <p>Playlist/PlaylistItem/PlaylistLike {@code @Entity}는 DDL 정의 전용이며,
 * 데이터 R/W는 100% {@link PlaylistMapper}로 처리한다. 도메인 메서드({@code update},
 * {@code incrementLikeCount} 등)로 in-memory 변경 후 반드시 명시적 update 호출이 필요하다.
 * 단 like_count 증감은 원자적 DB UPDATE({@code incrementLikeCount}/{@code decrementLikeCount})를
 * 사용하여 동시 요청의 race condition을 방지한다.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 레벨: 기본 읽기 전용 트랜잭션
public class PlaylistService {

    /** 플레이리스트 통합 Mapper — playlist/playlist_item/playlist_likes 세 테이블 모두 담당 */
    private final PlaylistMapper playlistMapper;

    // ─────────────────────────────────────────────
    // 조회 (readOnly = true 상속)
    // ─────────────────────────────────────────────

    /**
     * 내 플레이리스트 목록을 페이징 조회한다.
     *
     * <p>본인이 생성한 플레이리스트만 반환한다 (공개/비공개 모두 포함).
     * Spring Pageable의 offset/pageSize를 추출하여 SQL LIMIT으로 전달하고,
     * 별도 count 쿼리로 PageImpl을 조립한다. Pageable.Sort는 MyBatis에서는 지원하지 않으며
     * XML 내부에서 created_at DESC로 고정 정렬한다.</p>
     *
     * @param userId   JWT에서 추출한 사용자 ID
     * @param pageable 페이지 번호·크기 정보 (Sort는 무시되고 created_at DESC 고정)
     * @return 플레이리스트 요약 응답 DTO 페이지
     */
    public Page<PlaylistDto.PlaylistResponse> getMyPlaylists(String userId, Pageable pageable) {
        log.debug("내 플레이리스트 목록 조회: userId={}, page={}", userId, pageable.getPageNumber());

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<Playlist> playlists = playlistMapper.findByUserId(userId, offset, limit);
        long total = playlistMapper.countByUserId(userId);

        List<PlaylistDto.PlaylistResponse> content = playlists.stream()
                .map(PlaylistDto.PlaylistResponse::from)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 플레이리스트 상세 정보를 조회한다 (아이템 목록 포함).
     *
     * <p>공개 플레이리스트는 누구나 조회 가능하다.
     * 비공개 플레이리스트는 소유자만 조회 가능하며, 타인이 접근하면 PL002(403)를 던진다.</p>
     *
     * @param playlistId 조회할 플레이리스트 ID
     * @param userId     JWT에서 추출한 요청자 사용자 ID
     * @return 플레이리스트 상세 응답 DTO (아이템 목록 포함)
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL002 — 비공개 플레이리스트에 소유자가 아닌 사용자가 접근할 때
     */
    public PlaylistDto.PlaylistDetailResponse getPlaylistDetail(Long playlistId, String userId) {
        log.debug("플레이리스트 상세 조회: playlistId={}, userId={}", playlistId, userId);

        // 플레이리스트 존재 확인 (MyBatis, null → PL001)
        Playlist playlist = playlistMapper.findById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND,
                    "플레이리스트를 찾을 수 없습니다: playlistId=" + playlistId);
        }

        // 비공개 플레이리스트는 소유자만 접근 가능
        if (!playlist.getIsPublic() && !playlist.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_ACCESS_DENIED,
                    "비공개 플레이리스트에 접근할 권한이 없습니다");
        }

        // 아이템 목록 조회 (sort_order 오름차순)
        List<PlaylistDto.PlaylistItemResponse> itemDtos =
                playlistMapper.findItemsByPlaylistId(playlistId)
                        .stream()
                        .map(PlaylistDto.PlaylistItemResponse::from)
                        .toList();

        return PlaylistDto.PlaylistDetailResponse.from(playlist, itemDtos);
    }

    // ─────────────────────────────────────────────
    // 쓰기 (개별 @Transactional 오버라이드)
    // ─────────────────────────────────────────────

    /**
     * 새 플레이리스트를 생성한다.
     *
     * <p>isPublic 기본값은 false(비공개)이며, 생성자(userId)는 JWT에서 추출한 값을 사용한다.</p>
     *
     * @param userId  JWT에서 추출한 사용자 ID
     * @param request 생성 요청 DTO (playlistName 필수)
     * @return 생성된 플레이리스트 ID를 담은 응답 DTO
     */
    @Transactional
    public PlaylistDto.CreateResponse createPlaylist(String userId, PlaylistDto.CreateRequest request) {
        log.info("플레이리스트 생성: userId={}, name={}", userId, request.playlistName());

        Playlist playlist = Playlist.builder()
                .userId(userId)
                .playlistName(request.playlistName())
                .description(request.description())
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .coverImageUrl(request.coverImageUrl())
                .likeCount(0)
                .build();

        // MyBatis insert — useGeneratedKeys로 playlistId 자동 세팅
        playlistMapper.insertPlaylist(playlist);
        log.info("플레이리스트 생성 완료: playlistId={}", playlist.getPlaylistId());

        return new PlaylistDto.CreateResponse(playlist.getPlaylistId());
    }

    /**
     * 플레이리스트 정보를 수정한다 (null-safe 패치).
     *
     * <p>소유자 본인만 수정할 수 있다.
     * null로 전달된 필드는 기존 값을 유지한다 (부분 수정 지원).</p>
     *
     * @param playlistId 수정할 플레이리스트 ID
     * @param userId     JWT에서 추출한 사용자 ID (소유권 검증)
     * @param request    수정 요청 DTO (변경할 필드만 전달, null 허용)
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL002 — 소유자가 아닌 사용자가 수정 시도 시
     */
    @Transactional
    public void updatePlaylist(Long playlistId, String userId, PlaylistDto.UpdateRequest request) {
        log.info("플레이리스트 수정: playlistId={}, userId={}", playlistId, userId);

        // 소유자 검증 포함 조회
        Playlist playlist = findOwnedPlaylist(playlistId, userId);

        // 가져온(복사한) 플레이리스트는 공개로 전환 불가
        if (Boolean.TRUE.equals(playlist.getIsImported()) && Boolean.TRUE.equals(request.isPublic())) {
            throw new BusinessException(ErrorCode.PLAYLIST_IMPORTED_CANNOT_SHARE,
                    "가져온 플레이리스트는 공개할 수 없습니다: playlistId=" + playlistId);
        }

        // 도메인 메서드로 null-safe 수정 후 MyBatis UPDATE 명시 호출 (dirty checking 미지원)
        playlist.update(request.playlistName(), request.description(), request.isPublic());
        playlistMapper.updatePlaylist(playlist);

        log.info("플레이리스트 수정 완료: playlistId={}", playlistId);
    }

    /**
     * 플레이리스트를 삭제한다.
     *
     * <p>소유자 본인만 삭제 가능하다. 연관된 아이템(playlist_item)은
     * DB의 ON DELETE CASCADE로 자동 삭제된다.</p>
     *
     * @param playlistId 삭제할 플레이리스트 ID
     * @param userId     JWT에서 추출한 사용자 ID (소유권 검증)
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL002 — 소유자가 아닌 사용자가 삭제 시도 시
     */
    @Transactional
    public void deletePlaylist(Long playlistId, String userId) {
        log.info("플레이리스트 삭제: playlistId={}, userId={}", playlistId, userId);

        // 소유자 검증 포함 조회 후 PK로 삭제
        Playlist playlist = findOwnedPlaylist(playlistId, userId);
        playlistMapper.deletePlaylist(playlist.getPlaylistId());

        log.info("플레이리스트 삭제 완료: playlistId={}", playlistId);
    }

    /**
     * 플레이리스트에 영화를 추가한다.
     *
     * <p>소유자 본인만 추가 가능하다.
     * 동일 플레이리스트에 같은 영화를 중복 추가하면 PL003(409)을 던진다.
     * sort_order는 기존 마지막 순서에 자동으로 추가된다 (현재 아이템 수 기준).</p>
     *
     * @param playlistId 영화를 추가할 플레이리스트 ID
     * @param userId     JWT에서 추출한 사용자 ID (소유권 검증)
     * @param request    영화 추가 요청 DTO (movieId 필수)
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL002 — 소유자가 아닌 사용자가 추가 시도 시
     * @throws BusinessException PL003 — 이미 추가된 영화를 중복 추가 시도 시
     */
    @Transactional
    public void addMovie(Long playlistId, String userId, PlaylistDto.AddMovieRequest request) {
        log.info("플레이리스트 영화 추가: playlistId={}, userId={}, movieId={}",
                playlistId, userId, request.movieId());

        // 소유자 검증 포함 조회
        findOwnedPlaylist(playlistId, userId);

        // 중복 추가 방지 (UNIQUE(playlist_id, movie_id) 제약 전 애플리케이션 레벨 확인)
        if (playlistMapper.existsItem(playlistId, request.movieId())) {
            throw new BusinessException(ErrorCode.PLAYLIST_ITEM_DUPLICATE,
                    "이미 플레이리스트에 추가된 영화입니다: movieId=" + request.movieId());
        }

        // 현재 아이템 수를 sort_order 기준으로 사용 (마지막에 추가)
        int currentItemCount = playlistMapper.findItemsByPlaylistId(playlistId).size();

        PlaylistItem item = PlaylistItem.builder()
                .playlistId(playlistId)
                .movieId(request.movieId())
                .sortOrder(currentItemCount) // 0-based: 첫 번째 아이템은 0, 두 번째는 1, ...
                .addedAt(LocalDateTime.now())
                .build();

        playlistMapper.insertItem(item);
        log.info("플레이리스트 영화 추가 완료: playlistItemId={}", item.getPlaylistItemId());
    }

    /**
     * 플레이리스트에서 영화를 제거한다.
     *
     * <p>소유자 본인만 제거 가능하다.
     * 존재하지 않는 영화를 제거하려 하면 PL004(404)를 던진다.</p>
     *
     * @param playlistId 영화를 제거할 플레이리스트 ID
     * @param movieId    제거할 영화 ID
     * @param userId     JWT에서 추출한 사용자 ID (소유권 검증)
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL002 — 소유자가 아닌 사용자가 제거 시도 시
     * @throws BusinessException PL004 — 해당 플레이리스트에 지정 영화가 없을 때
     */
    @Transactional
    public void removeMovie(Long playlistId, String movieId, String userId) {
        log.info("플레이리스트 영화 제거: playlistId={}, movieId={}, userId={}",
                playlistId, movieId, userId);

        // 소유자 검증 포함 조회
        findOwnedPlaylist(playlistId, userId);

        // 제거할 아이템 조회 (MyBatis, null → PL004)
        PlaylistItem item = playlistMapper.findItemByPlaylistIdAndMovieId(playlistId, movieId);
        if (item == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_ITEM_NOT_FOUND,
                    "플레이리스트에 해당 영화가 없습니다: movieId=" + movieId);
        }

        int deletedOrder = item.getSortOrder() != null ? item.getSortOrder() : 0;
        playlistMapper.deleteItem(item.getPlaylistItemId());

        // 삭제된 아이템보다 뒤에 있는 항목들의 sortOrder를 1씩 당겨 hole 제거
        playlistMapper.shiftSortOrderDown(playlistId, deletedOrder);
        log.info("플레이리스트 영화 제거 완료: playlistItemId={}, 재정렬 완료", item.getPlaylistItemId());
    }

    /**
     * 플레이리스트에 좋아요를 누른다.
     *
     * <p>공개 플레이리스트에만 좋아요 가능하다 (비공개이면 PL007).
     * 이미 좋아요를 누른 경우 PL005(409)를 던진다.
     * 성공 시 Playlist.likeCount를 원자적으로 1 증가시킨다 (DB 레벨 UPDATE).</p>
     *
     * @param playlistId 좋아요를 누를 플레이리스트 ID
     * @param userId     JWT에서 추출한 사용자 ID
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL005 — 이미 좋아요를 누른 경우
     * @throws BusinessException PL007 — 비공개 플레이리스트에 좋아요 시도 시
     */
    @Transactional
    public void likePlaylist(Long playlistId, String userId) {
        log.info("플레이리스트 좋아요: playlistId={}, userId={}", playlistId, userId);

        // 플레이리스트 존재 확인 (MyBatis, null → PL001)
        Playlist playlist = playlistMapper.findById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND,
                    "플레이리스트를 찾을 수 없습니다: playlistId=" + playlistId);
        }

        // 비공개 플레이리스트는 좋아요 불가
        if (!playlist.getIsPublic()) {
            throw new BusinessException(ErrorCode.PLAYLIST_PRIVATE,
                    "비공개 플레이리스트에는 좋아요를 누를 수 없습니다");
        }

        // 중복 좋아요 방지
        if (playlistMapper.existsLikeByPlaylistIdAndUserId(playlistId, userId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_LIKE_DUPLICATE,
                    "이미 좋아요를 누른 플레이리스트입니다");
        }

        // 좋아요 레코드 생성
        PlaylistLike like = PlaylistLike.builder()
                .playlistId(playlistId)
                .userId(userId)
                .build();
        playlistMapper.insertLike(like);

        // likeCount 원자적 증가 — DB 레벨 UPDATE로 동시 요청 race condition 방지
        playlistMapper.incrementLikeCount(playlistId);
        log.info("플레이리스트 좋아요 완료: playlistId={}", playlistId);
    }

    /**
     * 플레이리스트 좋아요를 취소한다.
     *
     * <p>좋아요 기록이 없는 경우 PL006(404)를 던진다.
     * 성공 시 Playlist.likeCount를 원자적으로 1 감소시킨다 (DB 레벨 UPDATE, 0 미만 방지).</p>
     *
     * @param playlistId 좋아요를 취소할 플레이리스트 ID
     * @param userId     JWT에서 추출한 사용자 ID
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL006 — 좋아요 기록이 없을 때
     */
    @Transactional
    public void unlikePlaylist(Long playlistId, String userId) {
        log.info("플레이리스트 좋아요 취소: playlistId={}, userId={}", playlistId, userId);

        // 플레이리스트 존재 확인 (likeCount 감소를 위해 필요)
        Playlist playlist = playlistMapper.findById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND,
                    "플레이리스트를 찾을 수 없습니다: playlistId=" + playlistId);
        }

        // 좋아요 레코드 조회
        PlaylistLike like = playlistMapper.findLikeByPlaylistIdAndUserId(playlistId, userId);
        if (like == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_LIKE_NOT_FOUND,
                    "좋아요 기록이 없습니다");
        }

        playlistMapper.deleteLike(like.getPlaylistLikeId());

        // likeCount 원자적 감소 — DB 레벨 UPDATE로 동시 요청 race condition 방지
        playlistMapper.decrementLikeCount(playlistId);
        log.info("플레이리스트 좋아요 취소 완료: playlistId={}", playlistId);
    }

    // ─────────────────────────────────────────────
    // 플레이리스트 가져오기 (복사)
    // ─────────────────────────────────────────────

    /**
     * 커뮤니티에 공유된 플레이리스트를 내 플레이리스트로 복사한다.
     *
     * <h3>비즈니스 규칙</h3>
     * <ul>
     *   <li>대상 플레이리스트가 없으면 PL001(404)</li>
     *   <li>비공개 플레이리스트는 가져올 수 없음 — PL007(403)</li>
     *   <li>본인 플레이리스트는 가져올 수 없음 — PL010(400)</li>
     *   <li>이미 가져온 플레이리스트를 중복 요청 시 PL008(409)</li>
     * </ul>
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>원본 플레이리스트 조회 및 검증</li>
     *   <li>새 플레이리스트 생성 (이름 + " (가져옴)", 비공개)</li>
     *   <li>아이템 일괄 복사 (INSERT SELECT)</li>
     *   <li>playlist_scrap 레코드 기록 (중복 방지용)</li>
     * </ol>
     *
     * @param playlistId 가져올 원본 플레이리스트 ID
     * @param userId     요청자 사용자 ID (JWT)
     * @return 새로 생성된 내 플레이리스트 ID
     */
    @Transactional
    public PlaylistDto.ImportResponse importPlaylist(Long playlistId, String userId) {
        log.info("플레이리스트 가져오기: playlistId={}, userId={}", playlistId, userId);

        // 1) 원본 플레이리스트 존재 확인
        Playlist source = playlistMapper.findById(playlistId);
        if (source == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND,
                    "플레이리스트를 찾을 수 없습니다: playlistId=" + playlistId);
        }

        // 2) 비공개 플레이리스트 차단
        if (!Boolean.TRUE.equals(source.getIsPublic())) {
            throw new BusinessException(ErrorCode.PLAYLIST_PRIVATE,
                    "비공개 플레이리스트는 가져올 수 없습니다");
        }

        // 3) 본인 플레이리스트 차단
        if (source.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_SELF_IMPORT,
                    "본인의 플레이리스트는 가져올 수 없습니다");
        }

        // 4) 중복 가져오기 차단
        if (playlistMapper.existsScrap(userId, playlistId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_SCRAP_DUPLICATE,
                    "이미 가져온 플레이리스트입니다");
        }

        // 5) 새 플레이리스트 생성 (비공개, 이름에 "(가져옴)" 접미사, isImported=true)
        Playlist newPlaylist = Playlist.builder()
                .userId(userId)
                .playlistName(source.getPlaylistName() + " (가져옴)")
                .description(source.getDescription())
                .isPublic(false)
                .coverImageUrl(source.getCoverImageUrl())
                .likeCount(0)
                .isImported(true)
                .build();
        playlistMapper.insertPlaylist(newPlaylist);

        // 6) 아이템 일괄 복사 (INSERT SELECT)
        playlistMapper.copyItems(playlistId, newPlaylist.getPlaylistId());

        // 7) 스크랩 기록 (중복 방지 UNIQUE 제약)
        PlaylistScrap scrap = PlaylistScrap.builder()
                .userId(userId)
                .playlistId(playlistId)
                .build();
        playlistMapper.insertScrap(scrap);

        log.info("플레이리스트 가져오기 완료: newPlaylistId={}", newPlaylist.getPlaylistId());
        return new PlaylistDto.ImportResponse(newPlaylist.getPlaylistId());
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ─────────────────────────────────────────────

    /**
     * 플레이리스트를 조회하고 소유권을 검증한다.
     *
     * <p>playlistId로 단건 조회 후 userId 일치 여부를 확인한다.
     * 플레이리스트가 없으면 PL001, 소유자가 다르면 PL002를 던진다.</p>
     *
     * @param playlistId 플레이리스트 ID
     * @param userId     소유자 사용자 ID
     * @return 소유권이 확인된 Playlist 엔티티
     * @throws BusinessException PL001 — 플레이리스트가 존재하지 않을 때
     * @throws BusinessException PL002 — 소유자가 아닌 경우
     */
    private Playlist findOwnedPlaylist(Long playlistId, String userId) {
        // 1차: PK로 존재 확인 (없으면 PL001)
        Playlist playlist = playlistMapper.findById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND,
                    "플레이리스트를 찾을 수 없습니다: playlistId=" + playlistId);
        }

        // 2차: 소유자 검증 (다른 사용자의 플레이리스트이면 PL002)
        if (!playlist.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_ACCESS_DENIED,
                    "플레이리스트에 접근할 권한이 없습니다");
        }

        return playlist;
    }
}
