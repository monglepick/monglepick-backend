package com.monglepick.monglepickbackend.domain.user.service;

import com.monglepick.monglepickbackend.domain.user.dto.UpdateProfileRequest;
import com.monglepick.monglepickbackend.domain.user.dto.UserResponse;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import com.monglepick.monglepickbackend.domain.watchhistory.dto.WatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.watchhistory.dto.WishlistResponse;
import com.monglepick.monglepickbackend.domain.watchhistory.entity.UserWishlist;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.user.repository.UserPreferenceRepository;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.domain.watchhistory.repository.UserWishlistRepository;
import com.monglepick.monglepickbackend.domain.watchhistory.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 마이페이지 서비스
 *
 * <p>사용자 프로필, 시청 이력, 위시리스트, 선호도 등
 * 마이페이지에 필요한 비즈니스 로직을 처리합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserWishlistRepository userWishlistRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 사용자 프로필 정보를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자 정보 응답 DTO
     * @throws BusinessException 사용자를 찾을 수 없는 경우
     */
    public UserResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * 프로필(닉네임, 프로필 이미지 URL)을 수정합니다.
     *
     * <p>null인 필드는 수정하지 않습니다 (Partial Update).
     * 닉네임 변경 시 다른 사용자와 중복 여부를 검증합니다.</p>
     *
     * @param userId  사용자 ID
     * @param request 수정 요청 DTO
     * @return 수정된 사용자 프로필
     * @throws BusinessException 사용자를 찾을 수 없거나 닉네임이 이미 사용 중인 경우
     */
    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
            }
            user.updateNickname(request.nickname());
        }

        if (request.profileImageUrl() != null) {
            user.updateProfileImage(request.profileImageUrl());
        }

        if (request.newPassword() != null) {
            if (user.getProvider() != User.Provider.LOCAL) {
                throw new BusinessException(ErrorCode.SOCIAL_USER_CANNOT_CHANGE_PASSWORD);
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
            }
            user.updatePassword(passwordEncoder.encode(request.newPassword()));
            log.info("비밀번호 변경 완료 - userId: {}", userId);
        }

        return UserResponse.from(user);
    }

    /**
     * 사용자의 시청 이력을 페이징으로 조회합니다.
     *
     * <p>대용량 테이블(26M+ 행)이므로 반드시 페이징을 사용합니다.
     * 최신순 정렬을 위해 Pageable에서 Sort를 지정해야 합니다.</p>
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 페이지 단위의 시청 이력
     */
    public Page<WatchHistoryResponse> getWatchHistory(String userId, Pageable pageable) {
        log.debug("시청 이력 조회 - userId: {}, page: {}", userId, pageable.getPageNumber());
        return watchHistoryRepository.findByUser_UserId(userId, pageable)
                .map(WatchHistoryResponse::from);
    }

    /**
     * 사용자의 위시리스트를 페이징으로 조회합니다.
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 페이지 단위의 위시리스트
     */
    public Page<WishlistResponse> getWishlist(String userId, Pageable pageable) {
        log.debug("위시리스트 조회 - userId: {}", userId);
        return userWishlistRepository.findByUser_UserId(userId, pageable)
                .map(WishlistResponse::from);
    }

    /**
     * 위시리스트에 영화를 추가합니다.
     *
     * <p>이미 추가된 영화는 중복 추가할 수 없습니다.</p>
     *
     * @param userId 사용자 ID
     * @param movieId 추가할 영화 ID
     * @throws BusinessException 이미 위시리스트에 존재하는 경우
     */
    @Transactional
    public void addToWishlist(String userId, String movieId) {
        // 중복 확인
        if (userWishlistRepository.existsByUser_UserIdAndMovieId(userId, movieId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_WISHLIST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserWishlist wishlist = UserWishlist.builder()
                .user(user)
                .movieId(movieId)
                .build();

        userWishlistRepository.save(wishlist);
        log.info("위시리스트 추가 - userId: {}, movieId: {}", userId, movieId);
    }

    /**
     * 위시리스트에서 영화를 제거합니다.
     *
     * @param userId 사용자 ID
     * @param movieId 제거할 영화 ID
     * @throws BusinessException 위시리스트 항목을 찾을 수 없는 경우
     */
    @Transactional
    public void removeFromWishlist(String userId, String movieId) {
        UserWishlist wishlist = userWishlistRepository
                .findByUser_UserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISHLIST_NOT_FOUND));

        userWishlistRepository.delete(wishlist);
        log.info("위시리스트 제거 - userId: {}, movieId: {}", userId, movieId);
    }

    /**
     * 사용자의 선호도 정보를 조회합니다.
     *
     * <p>선호도가 아직 설정되지 않은 경우 빈 Optional을 반환합니다.</p>
     *
     * @param userId 사용자 ID
     * @return 사용자 선호도 Optional
     */
    public Optional<UserPreference> getPreferences(String userId) {
        return userPreferenceRepository.findByUser_UserId(userId);
    }
}
