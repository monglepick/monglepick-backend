package com.monglepick.monglepickbackend.domain.wishlist.repository;

import com.monglepick.monglepickbackend.domain.wishlist.entity.UserWishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 위시리스트 JPA 리포지토리
 *
 * <p>사용자의 보고싶은 영화 목록의 CRUD를 제공합니다.</p>
 */
public interface UserWishlistRepository extends JpaRepository<UserWishlist, Long> {
    /** 사용자별 위시리스트 조회 */
    Page<UserWishlist> findByUserId(String userId, Pageable pageable);

    /** 중복 추가 방지를 위한 존재 여부 확인 */
    boolean existsByUserIdAndMovieId(String userId, String movieId);

    /** 삭제를 위한 위시리스트 항목 조회 */
    Optional<UserWishlist> findByUserIdAndMovieId(String userId, String movieId);
}
