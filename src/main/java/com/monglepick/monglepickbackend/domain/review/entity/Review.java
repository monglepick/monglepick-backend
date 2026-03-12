package com.monglepick.monglepickbackend.domain.review.entity;

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
 * 영화 리뷰 엔티티
 *
 * <p>MySQL reviews 테이블과 매핑됩니다.
 * 사용자가 영화에 대해 작성하는 평점과 리뷰를 저장합니다.</p>
 *
 * <p>한 사용자가 같은 영화에 대해 중복 리뷰를 작성할 수 없습니다.
 * (서비스 레이어에서 검증)</p>
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    /** 리뷰 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 리뷰 작성자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 리뷰 대상 영화 ID (movies 테이블의 ID 또는 TMDB ID) */
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    /** 평점 (1.0 ~ 5.0, 0.5 단위) */
    @Column(nullable = false)
    private Double rating;

    /** 리뷰 본문 (TEXT 타입) */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 리뷰 작성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Review(User user, Long movieId, Double rating, String content) {
        this.user = user;
        this.movieId = movieId;
        this.rating = rating;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** 리뷰 내용 및 평점 수정 */
    public void update(Double rating, String content) {
        this.rating = rating;
        this.content = content;
    }
}
