package com.monglepick.monglepickbackend.domain.user.controller;

import com.monglepick.monglepickbackend.domain.user.dto.UserResponse;
import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import com.monglepick.monglepickbackend.domain.watchhistory.entity.UserWishlist;
import com.monglepick.monglepickbackend.domain.watchhistory.entity.WatchHistory;
import com.monglepick.monglepickbackend.domain.user.service.UserService;
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
 *
 * <p>API 목록:</p>
 * <ul>
 *   <li>GET /api/v1/users/me/profile - 프로필 조회</li>
 *   <li>GET /api/v1/users/me/watch-history - 시청 이력 조회</li>
 *   <li>GET /api/v1/users/me/wishlist - 위시리스트 조회</li>
 *   <li>POST /api/v1/users/me/wishlist/{movieId} - 위시리스트 추가</li>
 *   <li>DELETE /api/v1/users/me/wishlist/{movieId} - 위시리스트 제거</li>
 *   <li>GET /api/v1/users/me/preferences - 선호도 조회</li>
 * </ul>
 */
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
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(
            @AuthenticationPrincipal Long userId) {

        UserResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * 시청 이력 조회 API
     *
     * <p>대용량 테이블이므로 페이징이 필수입니다.
     * 최신 시청 순으로 정렬됩니다.</p>
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @param pageable 페이징 정보 (기본: 20건, 시청일 역순)
     * @return 200 OK + 페이지 단위의 시청 이력
     */
    @GetMapping("/watch-history")
    public ResponseEntity<Page<WatchHistory>> getWatchHistory(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "watchedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<WatchHistory> history = userService.getWatchHistory(userId, pageable);
        return ResponseEntity.ok(history);
    }

    /**
     * 위시리스트 조회 API
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @param pageable 페이징 정보 (기본: 20건, 추가일 역순)
     * @return 200 OK + 페이지 단위의 위시리스트
     */
    @GetMapping("/wishlist")
    public ResponseEntity<Page<UserWishlist>> getWishlist(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "addedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<UserWishlist> wishlist = userService.getWishlist(userId, pageable);
        return ResponseEntity.ok(wishlist);
    }

    /**
     * 위시리스트 추가 API
     *
     * <p>보고싶은 영화 목록에 영화를 추가합니다.
     * 이미 추가된 영화는 중복 추가할 수 없습니다.</p>
     *
     * @param movieId 추가할 영화 ID
     * @param userId JWT에서 추출한 사용자 ID
     * @return 201 Created
     */
    @PostMapping("/wishlist/{movieId}")
    public ResponseEntity<Void> addToWishlist(
            @PathVariable Long movieId,
            @AuthenticationPrincipal Long userId) {

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
    @DeleteMapping("/wishlist/{movieId}")
    public ResponseEntity<Void> removeFromWishlist(
            @PathVariable Long movieId,
            @AuthenticationPrincipal Long userId) {

        log.info("위시리스트 제거 요청 - userId: {}, movieId: {}", userId, movieId);
        userService.removeFromWishlist(userId, movieId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 선호도 조회 API
     *
     * <p>사용자의 영화 선호도(장르, 분위기, 플랫폼 등)를 반환합니다.
     * 선호도가 설정되지 않은 경우 204 No Content를 반환합니다.</p>
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 선호도 정보 또는 204 No Content
     */
    @GetMapping("/preferences")
    public ResponseEntity<UserPreference> getPreferences(
            @AuthenticationPrincipal Long userId) {

        return userService.getPreferences(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
