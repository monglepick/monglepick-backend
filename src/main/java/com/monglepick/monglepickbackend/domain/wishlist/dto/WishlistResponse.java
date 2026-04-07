package com.monglepick.monglepickbackend.domain.wishlist.dto;

import com.monglepick.monglepickbackend.domain.wishlist.entity.UserWishlist;

import java.time.LocalDateTime;

/**
 * 위시리스트 응답 DTO.
 *
 * <p>UserWishlist 엔티티 대신 API 응답에 사용하여
 * 엔티티 내부 구조 노출과 Jackson 직렬화 문제를 방지한다.</p>
 *
 * @param wishlistId 위시리스트 항목 ID
 * @param movieId    영화 ID
 * @param createdAt  추가 일시
 */
public record WishlistResponse(
        Long wishlistId,
        String movieId,
        LocalDateTime createdAt
) {
    /** 엔티티 → DTO 변환 팩토리 메서드 */
    public static WishlistResponse from(UserWishlist entity) {
        return new WishlistResponse(
                entity.getWishlistId(),
                entity.getMovieId(),
                entity.getCreatedAt()
        );
    }
}
