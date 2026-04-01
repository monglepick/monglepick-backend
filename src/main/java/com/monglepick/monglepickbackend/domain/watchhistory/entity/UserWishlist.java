package com.monglepick.monglepickbackend.domain.watchhistory.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 위시리스트 엔티티
 *
 * <p>MySQL user_wishlist 테이블과 매핑됩니다.
 * 사용자가 나중에 볼 영화를 저장하는 보고싶은 목록입니다.</p>
 *
 * <p>한 사용자가 같은 영화를 중복으로 추가할 수 없습니다.</p>
 */
@Entity
@Table(
        name = "user_wishlist",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wishlist_user_movie", columnNames = {"user_id", "movie_id"}
        ),
        indexes = @Index(name = "idx_wishlist_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — PK 필드명: id → wishlistId로 변경 (DDL 컬럼명 wishlist_id 매핑)
 * — addedAt 수동 필드 및 @PrePersist 제거됨 (created_at으로 대체)
 */
public class UserWishlist extends BaseAuditEntity {

    /** 위시리스트 항목 고유 식별자 (PK, BIGINT AUTO_INCREMENT, 컬럼명: wishlist_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wishlist_id")
    private Long wishlistId;

    /** 위시리스트 소유 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 위시리스트에 추가된 영화 ID (movies 테이블의 movie_id VARCHAR(50) 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /* addedAt 수동 필드 제거됨 — BaseTimeEntity의 created_at이 위시리스트 추가 시각 역할을 대체 */

    @Builder
    public UserWishlist(User user, String movieId) {
        this.user = user;
        this.movieId = movieId;
    }
}
