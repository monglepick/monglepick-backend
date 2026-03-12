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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시청 이력 엔티티
 *
 * <p>MySQL watch_history 테이블과 매핑됩니다.
 * 사용자가 시청한 영화와 평점을 기록합니다.</p>
 *
 * <p>AI 추천 에이전트의 협업 필터링(CF)에서 이 데이터를 활용하여
 * 유사한 시청 패턴을 가진 사용자를 찾고 영화를 추천합니다.</p>
 *
 * <p>총 26,010,786행의 대용량 테이블 (Kaggle MovieLens 데이터 기반)</p>
 */
@Entity
@Table(name = "watch_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchHistory {

    /** 시청 이력 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 시청한 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 시청한 영화 ID */
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    /** 시청 일시 */
    @Column(name = "watched_at", nullable = false)
    private LocalDateTime watchedAt;

    /** 사용자 평점 (1.0 ~ 5.0, null이면 평점 미부여) */
    @Column
    private Double rating;

    @Builder
    public WatchHistory(User user, Long movieId, LocalDateTime watchedAt, Double rating) {
        this.user = user;
        this.movieId = movieId;
        this.watchedAt = watchedAt;
        this.rating = rating;
    }
}
