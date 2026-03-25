package com.monglepick.monglepickbackend.domain.user.controller;

import com.monglepick.monglepickbackend.domain.user.dto.UserResponse;
import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import com.monglepick.monglepickbackend.domain.watchhistory.dto.WatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.watchhistory.dto.WishlistResponse;
import com.monglepick.monglepickbackend.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 컨트롤러
 *
 * <p>사용자 프로필, 시청 이력, 위시리스트, 선호도 등
 * 마이페이지 관련 API를 제공합니다. 모든 엔드포인트는 인증이 필요합니다.</p>
 */
@Tag(name = "마이페이지", description = "프로필, 시청 이력, 위시리스트, 선호도")
@Slf4j
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 프로필 조회 API
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 사용자 프로필 정보
     */
    @Operation(summary = "프로필 조회", description = "JWT 토큰에서 추출한 사용자의 프로필 정보 반환")
    @ApiResponse(responseCode = "200", description = "프로필 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(
            @AuthenticationPrincipal String userId) {

        UserResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * 시청 이력 조회 API
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @param pageable 페이징 정보 (기본: 20건, 시청일 역순)
     * @return 200 OK + 페이지 단위의 시청 이력
     */
    @Operation(summary = "시청 이력 조회", description = "사용자의 시청 이력을 최신순으로 페이징 조회")
    @ApiResponse(responseCode = "200", description = "시청 이력 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/watch-history")
    public ResponseEntity<Page<WatchHistoryResponse>> getWatchHistory(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20, sort = "watchedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<WatchHistoryResponse> history = userService.getWatchHistory(userId, pageable);
        return ResponseEntity.ok(history);
    }

    /**
     * 위시리스트 조회 API
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @param pageable 페이징 정보 (기본: 20건, 추가일 역순)
     * @return 200 OK + 페이지 단위의 위시리스트
     */
    @Operation(summary = "위시리스트 조회", description = "보고싶은 영화 목록을 추가일 역순으로 페이징 조회")
    @ApiResponse(responseCode = "200", description = "위시리스트 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/wishlist")
    public ResponseEntity<Page<WishlistResponse>> getWishlist(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20, sort = "addedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<WishlistResponse> wishlist = userService.getWishlist(userId, pageable);
        return ResponseEntity.ok(wishlist);
    }

    /**
     * 위시리스트 추가 API
     *
     * @param movieId 추가할 영화 ID
     * @param userId JWT에서 추출한 사용자 ID
     * @return 201 Created
     */
    @Operation(summary = "위시리스트 추가", description = "보고싶은 영화 목록에 영화 추가. 중복 추가 불가")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "위시리스트 추가 성공"),
            @ApiResponse(responseCode = "409", description = "이미 위시리스트에 추가된 영화")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/wishlist/{movieId}")
    public ResponseEntity<Void> addToWishlist(
            @PathVariable String movieId,
            @AuthenticationPrincipal String userId) {

        log.info("위시리스트 추가 요청 - userId: {}, movieId: {}", userId, movieId);
        userService.addToWishlist(userId, movieId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 위시리스트 제거 API
     *
     * @param movieId 제거할 영화 ID
     * @param userId JWT에서 추출한 사용자 ID
     * @return 204 No Content
     */
    @Operation(summary = "위시리스트 제거", description = "보고싶은 영화 목록에서 영화 제거")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "위시리스트 제거 성공"),
            @ApiResponse(responseCode = "404", description = "위시리스트 항목 없음")
    })
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/wishlist/{movieId}")
    public ResponseEntity<Void> removeFromWishlist(
            @PathVariable String movieId,
            @AuthenticationPrincipal String userId) {

        log.info("위시리스트 제거 요청 - userId: {}, movieId: {}", userId, movieId);
        userService.removeFromWishlist(userId, movieId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 선호도 조회 API
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 선호도 정보 또는 204 No Content
     */
    @Operation(summary = "선호도 조회", description = "사용자의 영화 선호도 (장르, 분위기, 플랫폼 등). 미설정 시 204")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호도 조회 성공"),
            @ApiResponse(responseCode = "204", description = "선호도 미설정")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/preferences")
    public ResponseEntity<UserPreference> getPreferences(
            @AuthenticationPrincipal String userId) {

        return userService.getPreferences(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
