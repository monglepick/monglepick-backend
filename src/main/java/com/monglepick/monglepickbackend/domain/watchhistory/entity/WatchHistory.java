package com.monglepick.monglepickbackend.domain.watchhistory.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
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
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — PK 필드명: id → watchHistoryId로 변경 (DDL 컬럼명 watch_history_id 매핑)
 * — watchedAt은 도메인 고유 필드이므로 유지 (시청 일시)
 */
public class WatchHistory extends BaseAuditEntity {

    /** 시청 이력 고유 식별자 (PK, BIGINT AUTO_INCREMENT, 컬럼명: watch_history_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watch_history_id")
    private Long watchHistoryId;

    /** 시청한 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 시청한 영화 ID (movies 테이블의 movie_id VARCHAR(50) 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /** 시청 일시 */
    @Column(name = "watched_at", nullable = false)
    private LocalDateTime watchedAt;

    /** 사용자 평점 (1.0 ~ 5.0, null이면 평점 미부여) */
    @Column
    private Double rating;

    /**
     * 시청 경로 (Phase 2).
     * 사용자가 어떤 경로로 이 영화를 시청하게 되었는지 기록한다.
     * 예: "recommendation"(AI 추천), "search"(검색), "wishlist"(찜목록),
     *     "home"(홈 인기영화), "match"(무비매치), "direct"(직접 접근)
     */
    @Column(name = "watch_source", length = 50)
    private String watchSource;

    /**
     * 실제 시청 시간 — 초 단위 (Phase 2).
     * 클라이언트에서 측정하여 전달한다. null이면 미측정.
     * 영화 runtime과 비교하여 completion_status 판정에 활용 가능.
     */
    @Column(name = "watch_duration_seconds")
    private Integer watchDurationSeconds;

    /**
     * 시청 완료 상태 (Phase 2).
     * COMPLETED(완료), ABANDONED(중도 포기), IN_PROGRESS(시청 중)
     */
    @Column(name = "completion_status", length = 30)
    private String completionStatus;

    @Builder
    public WatchHistory(User user, String movieId, LocalDateTime watchedAt, Double rating,
                        String watchSource, Integer watchDurationSeconds, String completionStatus) {
        this.user = user;
        this.movieId = movieId;
        this.watchedAt = watchedAt;
        this.rating = rating;
        this.watchSource = watchSource;
        this.watchDurationSeconds = watchDurationSeconds;
        this.completionStatus = completionStatus;
    }
}
