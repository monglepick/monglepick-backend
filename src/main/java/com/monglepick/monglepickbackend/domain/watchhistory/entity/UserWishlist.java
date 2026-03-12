package com.monglepick.monglepickbackend.domain.watchhistory.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 위시리스트 엔티티
 *
 * <p>MySQL user_wishlist 테이블과 매핑됩니다.
 * 사용자가 나중에 볼 영화를 저장하는 보고싶은 목록입니다.</p>
 *
 * <p>한 사용자가 같은 영화를 중복으로 추가할 수 없습니다.</p>
 */
@Entity
@Table(name = "user_wishlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWishlist {

    /** 위시리스트 항목 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 위시리스트 소유 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 위시리스트에 추가된 영화 ID */
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    /** 위시리스트 추가 시각 */
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Builder
    public UserWishlist(User user, Long movieId) {
        this.user = user;
        this.movieId = movieId;
    }

    @PrePersist
    protected void onCreate() {
        this.addedAt = LocalDateTime.now();
    }
}
