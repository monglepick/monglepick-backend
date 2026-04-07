package com.monglepick.monglepickbackend.domain.playlist.controller;

import com.monglepick.monglepickbackend.domain.playlist.dto.PlaylistDto;
import com.monglepick.monglepickbackend.domain.playlist.service.PlaylistService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 플레이리스트 REST API 컨트롤러.
 *
 * <p>사용자가 생성한 영화 플레이리스트의 CRUD, 영화 추가/제거, 좋아요 토글 엔드포인트를 제공한다.
 * 모든 엔드포인트는 JWT 인증이 필요하다 (SecurityConfig에서 {@code anyRequest().authenticated()} 적용).</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <ul>
 *   <li>GET    /api/v1/playlists                              — 내 플레이리스트 목록</li>
 *   <li>POST   /api/v1/playlists                              — 플레이리스트 생성</li>
 *   <li>GET    /api/v1/playlists/{playlistId}                 — 상세 조회</li>
 *   <li>PUT    /api/v1/playlists/{playlistId}                 — 수정</li>
 *   <li>DELETE /api/v1/playlists/{playlistId}                 — 삭제</li>
 *   <li>POST   /api/v1/playlists/{playlistId}/movies          — 영화 추가</li>
 *   <li>DELETE /api/v1/playlists/{playlistId}/movies/{movieId}— 영화 제거</li>
 *   <li>POST   /api/v1/playlists/{playlistId}/like            — 좋아요</li>
 *   <li>DELETE /api/v1/playlists/{playlistId}/like            — 좋아요 취소</li>
 * </ul>
 */
@Tag(name = "플레이리스트", description = "영화 플레이리스트 생성·조회·수정·삭제 및 좋아요 토글 API")
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
@Slf4j
public class PlaylistController extends BaseController {

    /** 플레이리스트 비즈니스 로직 서비스 */
    private final PlaylistService playlistService;

    // ─────────────────────────────────────────────
    // 목록 / 상세 조회
    // ─────────────────────────────────────────────

    /**
     * 내 플레이리스트 목록을 페이징 조회한다.
     *
     * <p>JWT에서 추출한 userId 기준으로 본인 소유 플레이리스트만 반환한다 (공개/비공개 모두).
     * 기본 정렬은 최신 생성 순(createdAt DESC)이다.</p>
     *
     * @param page      페이지 번호 (0-based, 기본값 0)
     * @param size      페이지 크기 (기본값 20, 최대 100)
     * @param principal JWT 인증 정보
     * @return 플레이리스트 요약 응답 DTO 페이지
     */
    @Operation(
            summary = "내 플레이리스트 목록 조회",
            description = "로그인한 사용자의 플레이리스트 목록을 최신 순으로 페이징 조회합니다. " +
                    "공개/비공개 플레이리스트 모두 반환됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PlaylistDto.PlaylistResponse>>> getMyPlaylists(
            @Parameter(description = "페이지 번호 (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,

            Principal principal
    ) {
        String userId = resolveUserId(principal);

        // 페이지 크기 상한 제한 (DoS 방지)
        int limitedSize = limitPageSize(size);

        // 최신 생성 순(createdAt DESC) 페이징
        Pageable pageable = PageRequest.of(page, limitedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        log.debug("내 플레이리스트 목록 조회 요청: userId={}, page={}, size={}", userId, page, limitedSize);

        Page<PlaylistDto.PlaylistResponse> result = playlistService.getMyPlaylists(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 플레이리스트 상세 정보를 조회한다 (아이템 목록 포함).
     *
     * <p>공개 플레이리스트는 본인이 아니어도 조회 가능하다.
     * 비공개 플레이리스트는 소유자만 조회 가능하다 (타인 접근 시 403).</p>
     *
     * @param playlistId 조회할 플레이리스트 ID (URL 경로 파라미터)
     * @param principal  JWT 인증 정보
     * @return 플레이리스트 상세 응답 DTO (아이템 목록 포함)
     */
    @Operation(
            summary = "플레이리스트 상세 조회",
            description = "플레이리스트 기본 정보와 포함된 영화 목록을 반환합니다. " +
                    "비공개 플레이리스트는 소유자만 접근 가능합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비공개 플레이리스트 접근 거부"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음")
    })
    @GetMapping("/{playlistId}")
    public ResponseEntity<ApiResponse<PlaylistDto.PlaylistDetailResponse>> getPlaylistDetail(
            @Parameter(description = "조회할 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.debug("플레이리스트 상세 조회 요청: playlistId={}, userId={}", playlistId, userId);

        PlaylistDto.PlaylistDetailResponse result = playlistService.getPlaylistDetail(playlistId, userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ─────────────────────────────────────────────
    // 생성 / 수정 / 삭제
    // ─────────────────────────────────────────────

    /**
     * 새 플레이리스트를 생성한다.
     *
     * <p>기본값으로 비공개(isPublic=false)로 생성된다.
     * 생성 성공 시 201 Created와 함께 생성된 playlistId를 반환한다.</p>
     *
     * @param request   플레이리스트 생성 요청 DTO (playlistName 필수)
     * @param principal JWT 인증 정보
     * @return 생성된 playlistId
     */
    @Operation(
            summary = "플레이리스트 생성",
            description = "새 플레이리스트를 생성합니다. 기본값으로 비공개(isPublic=false)로 생성됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (playlistName 누락 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PlaylistDto.CreateResponse>> createPlaylist(
            @RequestBody @Valid PlaylistDto.CreateRequest request,
            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 생성 요청: userId={}, name={}", userId, request.playlistName());

        PlaylistDto.CreateResponse result = playlistService.createPlaylist(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    /**
     * 플레이리스트 정보를 수정한다.
     *
     * <p>소유자 본인만 수정 가능하다.
     * null로 전달한 필드는 기존 값을 유지한다 (부분 수정 지원).</p>
     *
     * @param playlistId 수정할 플레이리스트 ID
     * @param request    수정 요청 DTO (변경할 필드만 전달)
     * @param principal  JWT 인증 정보
     * @return 204 No Content
     */
    @Operation(
            summary = "플레이리스트 수정",
            description = "플레이리스트 이름, 설명, 공개 여부를 수정합니다. " +
                    "null로 전달한 필드는 기존 값을 유지합니다 (부분 수정 지원).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음")
    })
    @PutMapping("/{playlistId}")
    public ResponseEntity<Void> updatePlaylist(
            @Parameter(description = "수정할 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            @RequestBody @Valid PlaylistDto.UpdateRequest request,
            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 수정 요청: playlistId={}, userId={}", playlistId, userId);

        playlistService.updatePlaylist(playlistId, userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 플레이리스트를 삭제한다.
     *
     * <p>소유자 본인만 삭제 가능하다.
     * 연관된 아이템은 DB CASCADE로 함께 삭제된다.</p>
     *
     * @param playlistId 삭제할 플레이리스트 ID
     * @param principal  JWT 인증 정보
     * @return 204 No Content
     */
    @Operation(
            summary = "플레이리스트 삭제",
            description = "플레이리스트와 연관된 영화 아이템을 모두 삭제합니다. 소유자만 삭제 가능합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음")
    })
    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(
            @Parameter(description = "삭제할 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 삭제 요청: playlistId={}, userId={}", playlistId, userId);

        playlistService.deletePlaylist(playlistId, userId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────
    // 영화 추가 / 제거
    // ─────────────────────────────────────────────

    /**
     * 플레이리스트에 영화를 추가한다.
     *
     * <p>소유자 본인만 추가 가능하다.
     * 이미 추가된 영화는 409 Conflict를 반환한다.</p>
     *
     * @param playlistId 영화를 추가할 플레이리스트 ID
     * @param request    영화 추가 요청 DTO (movieId 필수)
     * @param principal  JWT 인증 정보
     * @return 201 Created
     */
    @Operation(
            summary = "플레이리스트 영화 추가",
            description = "플레이리스트에 영화를 추가합니다. 소유자만 추가 가능하며, 중복 추가는 불가합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 추가된 영화")
    })
    @PostMapping("/{playlistId}/movies")
    public ResponseEntity<Void> addMovie(
            @Parameter(description = "영화를 추가할 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            @RequestBody @Valid PlaylistDto.AddMovieRequest request,
            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 영화 추가 요청: playlistId={}, userId={}, movieId={}",
                playlistId, userId, request.movieId());

        playlistService.addMovie(playlistId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 플레이리스트에서 영화를 제거한다.
     *
     * <p>소유자 본인만 제거 가능하다.
     * 플레이리스트에 없는 영화를 제거하면 404를 반환한다.</p>
     *
     * @param playlistId 영화를 제거할 플레이리스트 ID
     * @param movieId    제거할 영화 ID (URL 경로 파라미터)
     * @param principal  JWT 인증 정보
     * @return 204 No Content
     */
    @Operation(
            summary = "플레이리스트 영화 제거",
            description = "플레이리스트에서 특정 영화를 제거합니다. 소유자만 제거 가능합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제거 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음 또는 영화 없음")
    })
    @DeleteMapping("/{playlistId}/movies/{movieId}")
    public ResponseEntity<Void> removeMovie(
            @Parameter(description = "영화를 제거할 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            @Parameter(description = "제거할 영화 ID", required = true, example = "tmdb_12345")
            @PathVariable String movieId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 영화 제거 요청: playlistId={}, movieId={}, userId={}",
                playlistId, movieId, userId);

        playlistService.removeMovie(playlistId, movieId, userId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────
    // 좋아요 / 좋아요 취소
    // ─────────────────────────────────────────────

    /**
     * 플레이리스트에 좋아요를 누른다.
     *
     * <p>공개 플레이리스트에만 좋아요 가능하다 (비공개이면 403).
     * 이미 좋아요를 누른 경우 409 Conflict를 반환한다.</p>
     *
     * @param playlistId 좋아요를 누를 플레이리스트 ID
     * @param principal  JWT 인증 정보
     * @return 201 Created
     */
    @Operation(
            summary = "플레이리스트 좋아요",
            description = "공개 플레이리스트에 좋아요를 누릅니다. 이미 좋아요를 누른 경우 409를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "좋아요 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비공개 플레이리스트"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 좋아요")
    })
    @PostMapping("/{playlistId}/like")
    public ResponseEntity<Void> likePlaylist(
            @Parameter(description = "좋아요를 누를 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 좋아요 요청: playlistId={}, userId={}", playlistId, userId);

        playlistService.likePlaylist(playlistId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 플레이리스트 좋아요를 취소한다.
     *
     * <p>좋아요 기록이 없으면 404를 반환한다.</p>
     *
     * @param playlistId 좋아요를 취소할 플레이리스트 ID
     * @param principal  JWT 인증 정보
     * @return 204 No Content
     */
    @Operation(
            summary = "플레이리스트 좋아요 취소",
            description = "플레이리스트 좋아요를 취소합니다. 좋아요 기록이 없으면 404를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "좋아요 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "플레이리스트 없음 또는 좋아요 기록 없음")
    })
    @DeleteMapping("/{playlistId}/like")
    public ResponseEntity<Void> unlikePlaylist(
            @Parameter(description = "좋아요를 취소할 플레이리스트 ID", required = true, example = "1")
            @PathVariable Long playlistId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("플레이리스트 좋아요 취소 요청: playlistId={}, userId={}", playlistId, userId);

        playlistService.unlikePlaylist(playlistId, userId);
        return ResponseEntity.noContent().build();
    }
}
