package com.monglepick.monglepickbackend.domain.user.controller;

import com.monglepick.monglepickbackend.domain.community.dto.PostResponse;
import com.monglepick.monglepickbackend.domain.community.service.PostService;
import com.monglepick.monglepickbackend.domain.user.dto.UpdateProfileRequest;
import com.monglepick.monglepickbackend.domain.user.dto.UserResponse;
import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import com.monglepick.monglepickbackend.domain.userwatchhistory.dto.UserWatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.wishlist.dto.WishlistResponse;
import com.monglepick.monglepickbackend.domain.user.service.UserService;
import com.monglepick.monglepickbackend.global.constants.AppConstants;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.monglepick.monglepickbackend.domain.community.service.ImageService;
import java.util.List;
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
    private final PostService postService;
        private final ImageService imageService;

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
     * 프로필 수정 API
     *
     * <p>닉네임, 프로필 이미지 URL 중 변경할 필드만 보내면 됩니다.
     * null인 필드는 수정하지 않습니다.</p>
     *
     * @param userId  JWT에서 추출한 사용자 ID
     * @param request 수정 요청 (nickname, profileImageUrl)
     * @return 200 OK + 수정된 프로필 정보
     */
    @Operation(summary = "프로필 수정 (JSON)", description = "닉네임, 프로필 이미지 URL, 비밀번호 변경. JSON 바디로 전달. null 필드는 수정하지 않음.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 수정 성공"),
            @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치"),
            @ApiResponse(responseCode = "403", description = "소셜 로그인 사용자는 비밀번호 변경 불가"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PatchMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> updateProfileJson(
            @AuthenticationPrincipal String userId,
            @org.springframework.web.bind.annotation.RequestBody UpdateProfileRequest request) {

        log.info("프로필 수정 요청 (JSON) - userId: {}", userId);

        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "프로필 수정 (multipart)", description = "프로필 이미지 업로드 포함 수정. multipart/form-data 로 파일 전송 시 사용")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 또는 파일 형식"),
            @ApiResponse(responseCode = "403", description = "소셜 로그인 사용자는 비밀번호 변경 불가"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PatchMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateProfileMultipart(
            @AuthenticationPrincipal String userId,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            @RequestPart(value = "nickname", required = false) String nickname,
            @RequestPart(value = "profileImageUrl", required = false) String profileImageUrl,
            @RequestPart(value = "currentPassword", required = false) String currentPassword,
            @RequestPart(value = "newPassword", required = false) String newPassword) {

        log.info("프로필 수정 요청 (multipart) - userId: {}", userId);

        // multipart 로 파일 업로드가 있으면 ImageService에 위임하여 업로드 후 URL을 profileImageUrl에 대입
        if (profileImage != null && !profileImage.isEmpty()) {
            List<String> urls = imageService.uploadImages(List.of(profileImage), userId);
            if (urls != null && !urls.isEmpty()) {
                profileImageUrl = urls.get(0);
            }
        }

        // UpdateProfileRequest 는 record 이므로 새로 생성하여 서비스에 전달
        UpdateProfileRequest request = new UpdateProfileRequest(nickname, profileImageUrl, currentPassword, newPassword);

        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 시청 이력 조회 API (마이페이지 통합 경로)
     *
     * <p>로그인한 사용자의 시청 이력을 최신순으로 페이징 조회한다.
     * 본 엔드포인트는 마이페이지 화면에서 프로필과 함께 렌더링되는 통합 경로이며,
     * 독립 경로 {@code GET /api/v1/watch-history} 는 별도의 {@code UserWatchHistoryController} 가 제공한다.
     * 두 경로 모두 같은 {@code user_watch_history} 테이블을 조회한다.</p>
     *
     * @param userId   JWT 에서 추출한 사용자 ID
     * @param pageable 페이징 정보 (기본: 20 건, watchedAt 역순)
     * @return 200 OK + 페이지 단위의 시청 이력
     */
    @Operation(
            summary = "시청 이력 조회 (마이페이지)",
            description = "사용자의 시청 이력을 최신순으로 페이징 조회. " +
                    "독립 경로 /api/v1/watch-history 와 동일한 데이터를 반환한다."
    )
    @ApiResponse(responseCode = "200", description = "시청 이력 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/watch-history")
    public ResponseEntity<Page<UserWatchHistoryResponse>> getWatchHistory(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20, sort = "watchedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<UserWatchHistoryResponse> history = userService.getWatchHistory(userId, pageable);
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
     * 내가 쓴 게시글 목록 조회 API (마이페이지용)
     */
    @Operation(summary = "내가 쓴 게시글 목록 조회", description = "JWT 기준 본인이 작성한 PUBLISHED 게시글 목록을 페이징 조회합니다.")
    @ApiResponse(responseCode = "200", description = "내 게시글 목록 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/posts")
    public ResponseEntity<Page<PostResponse>> getMyPosts(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        int safeSize = Math.min(pageable.getPageSize(), AppConstants.MAX_PAGE_SIZE);
        Pageable safePage = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), safeSize, pageable.getSort());

        return ResponseEntity.ok(postService.getMyPosts(userId, safePage));
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
