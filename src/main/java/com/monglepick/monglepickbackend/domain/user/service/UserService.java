package com.monglepick.monglepickbackend.domain.user.service;

import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.user.dto.UpdateProfileRequest;
import com.monglepick.monglepickbackend.domain.user.dto.UserResponse;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.domain.user.mapper.UserPreferenceMapper;
import com.monglepick.monglepickbackend.domain.userwatchhistory.dto.UserWatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.userwatchhistory.repository.UserWatchHistoryRepository;
import com.monglepick.monglepickbackend.domain.wishlist.dto.WishlistResponse;
import com.monglepick.monglepickbackend.domain.wishlist.entity.UserWishlist;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.wishlist.repository.UserWishlistRepository;
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
 * <p>사용자 프로필, 시청 이력, 위시리스트, 선호도 등 마이페이지에 필요한 비즈니스 로직을 처리합니다.</p>
 *
 * <h3>시청 이력 도메인 분리 (2026-04-08 재도입)</h3>
 * <p>{@code user_watch_history} 테이블은 실 유저의 "봤어요" 행동 기록 전용이며,
 * Kaggle MovieLens 26M 시드인 {@code kaggle_watch_history} 와 완전히 분리되어 있다.
 * 추천 학습의 단일 진실 원본은 여전히 {@code reviews} 이며, 본 도메인은 유저 대면
 * UX(시청 이력 탭, 재관람 카운트)를 담당한다.</p>
 *
 * <h3>JPA / MyBatis 혼용</h3>
 * <p>UserMapper(MyBatis)와 UserWatchHistoryRepository(JPA)가 같은 클래스에 공존한다.
 * 윤형주 도메인의 JpaRepository 유지 원칙(설계서 §15)에 따른 정상적인 하이브리드 구성이며,
 * 1차 캐시 충돌 방지를 위해 동일 트랜잭션 내에서 같은 엔티티를 두 경로로 동시에 조회하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /** 사용자 CRUD — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;
    /** 사용자 선호도 — MyBatis Mapper */
    private final UserPreferenceMapper userPreferenceMapper;
    /** 위시리스트 — 윤형주 도메인 JpaRepository 유지 */
    private final UserWishlistRepository userWishlistRepository;
    /** 시청 이력 — 윤형주 도메인 JpaRepository 유지 (Kaggle 시드와 분리된 user_watch_history 테이블) */
    private final UserWatchHistoryRepository userWatchHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    /** 활동 리워드 서비스 — 위시리스트 추가(WISHLIST_ADD) 리워드 지급 위임 */
    private final RewardService rewardService;

    /**
     * 사용자 프로필 정보를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자 정보 응답 DTO
     * @throws BusinessException 사용자를 찾을 수 없는 경우
     */
    public UserResponse getProfile(String userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
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
        /*
         * JPA/MyBatis 하이브리드 (§15): MyBatis는 dirty checking을 지원하지 않으므로
         * 도메인 메서드로 in-memory 변경한 뒤 반드시 userMapper.update(user)를 명시 호출해야 한다.
         */
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userMapper.existsByNickname(request.nickname())) {
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

        // MyBatis는 dirty checking이 없으므로 변경사항을 명시적으로 UPDATE 호출
        userMapper.update(user);

        return UserResponse.from(user);
    }

    /**
     * 사용자의 시청 이력을 페이징으로 조회합니다.
     *
     * <p>마이페이지 통합 경로({@code GET /api/v1/users/me/watch-history})에서 호출된다.
     * 독립 경로({@code /api/v1/watch-history})는 별도의 {@code UserWatchHistoryController} 가 처리하며,
     * 두 경로 모두 같은 {@code user_watch_history} 테이블을 조회한다.</p>
     *
     * <p>본 메서드는 Kaggle MovieLens 시드({@code kaggle_watch_history})와 완전히 분리되어 있다.</p>
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보 (정렬은 호출 측에서 watchedAt DESC 권장)
     * @return 페이지 단위의 시청 이력
     */
    public Page<UserWatchHistoryResponse> getWatchHistory(String userId, Pageable pageable) {
        log.debug("시청 이력 조회 - userId: {}, page: {}", userId, pageable.getPageNumber());
        return userWatchHistoryRepository.findByUserId(userId, pageable)
                .map(UserWatchHistoryResponse::from);
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
        return userWishlistRepository.findByUserId(userId, pageable)
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
        if (userWishlistRepository.existsByUserIdAndMovieId(userId, movieId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_WISHLIST);
        }

        // 사용자 존재 검증은 JWT 인증 단계에서 이미 처리됨 — String userId 그대로 사용
        // (JPA/MyBatis 하이브리드 §15.4)

        UserWishlist wishlist = UserWishlist.builder()
                .userId(userId)
                .movieId(movieId)
                .build();

        userWishlistRepository.save(wishlist);
        log.info("위시리스트 추가 - userId: {}, movieId: {}", userId, movieId);

        /*
         * 위시리스트 추가 활동 리워드 지급 (WISHLIST_ADD).
         * RewardService는 REQUIRES_NEW 트랜잭션 + 내부 try-catch로 동작하므로
         * 리워드 실패가 위시리스트 저장 트랜잭션에 영향을 주지 않는다.
         * referenceId: "movie_{movieId}" — 동일 영화 중복 지급 방지 키로 사용.
         */
        rewardService.grantReward(userId, "WISHLIST_ADD", "movie_" + movieId, 0);
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
                .findByUserIdAndMovieId(userId, movieId)
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
        return Optional.ofNullable(userPreferenceMapper.findByUserId(userId));
    }
}
