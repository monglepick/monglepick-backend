package com.monglepick.monglepickbackend.domain.userwatchhistory.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 실 유저 시청 이력 엔티티
 *
 * <p>MySQL {@code user_watch_history} 테이블과 매핑됩니다.
 * 몽글픽 서비스 사용자가 영화를 시청했음을 기록하는 운영 도메인 테이블입니다.</p>
 *
 * <h3>Kaggle 시드(`kaggle_watch_history`) 와의 분리</h3>
 * <ul>
 *   <li>{@code user_watch_history} — 본 엔티티. 실 유저의 시청 행동 기록 (운영 R/W).</li>
 *   <li>{@code kaggle_watch_history} — Kaggle MovieLens 26M 시드 (Agent CF 학습 전용 read-only).
 *       Backend 가 관리하지 않으며 본 엔티티와 무관함.</li>
 * </ul>
 *
 * <h3>"봤다 = 리뷰" 원칙과의 관계 (설계서 §15)</h3>
 * <p>추천 학습의 단일 진실 원본은 여전히 {@code reviews} 테이블이다.
 * 본 테이블은 <b>유저 대면 UX</b>(마이페이지 시청 이력 탭, "봤어요" 원터치 체크,
 * 완주율/재관람 카운트)를 위해 별도로 운영된다. {@code reviews} 와 목적이 다르므로 중복이 아니다.</p>
 *
 * <h3>중복 허용 정책</h3>
 * <p>같은 사용자가 동일 영화를 여러 번 시청한 경우 매번 새 레코드로 저장된다.
 * 재관람 횟수 추적을 위한 의도적 설계이며, UNIQUE 제약을 두지 않는다.</p>
 *
 * <h3>Phase 1 하이브리드 원칙 (설계서 §15.4)</h3>
 * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA {@code @ManyToOne User} 매핑을 두지 않고
 * {@code String userId} FK 컬럼으로 직접 보관한다. 1차 캐시 충돌 방지 목적.</p>
 */
@Entity
@Table(
        name = "user_watch_history",
        indexes = {
                @Index(name = "idx_uwh_user", columnList = "user_id"),
                @Index(name = "idx_uwh_user_watched_at", columnList = "user_id, watched_at"),
                @Index(name = "idx_uwh_movie", columnList = "movie_id"),
                @Index(name = "idx_uwh_user_movie", columnList = "user_id, movie_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWatchHistory extends BaseAuditEntity {

    /** 시청 이력 항목 PK (BIGINT AUTO_INCREMENT, 컬럼명: user_watch_history_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_watch_history_id")
    private Long userWatchHistoryId;

    /**
     * 시청한 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>Phase 1 원칙: users 테이블의 쓰기 소유가 김민규(MyBatis) 도메인이므로
     * JPA {@code @ManyToOne User} 매핑 대신 String FK 로만 보관한다 (설계서 §15.4).</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 시청한 영화 ID (movies 테이블의 movie_id VARCHAR(50) 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /**
     * 시청 일시.
     *
     * <p>클라이언트가 명시적으로 전달하지 않으면 서비스 계층에서 현재 시각으로 채운다.
     * BaseAuditEntity 의 created_at 과는 별개이며, 사용자가 과거 시점의 시청을 기록하는 케이스를 지원한다.</p>
     */
    @Column(name = "watched_at", nullable = false)
    private LocalDateTime watchedAt;

    /**
     * 사용자 평점 (1.0 ~ 5.0).
     *
     * <p>nullable — 평점 미부여 허용. 평점은 본 테이블의 부수 정보이며,
     * 정식 평가는 {@code reviews} 테이블에 별도로 작성된다.</p>
     */
    @Column
    private Double rating;

    /**
     * 시청 경로 (어디서 영화를 시청하게 되었는지).
     *
     * <p>예: {@code "recommendation"}(AI 추천), {@code "search"}(검색),
     * {@code "wishlist"}(찜목록), {@code "home"}(홈 인기영화),
     * {@code "match"}(무비매치), {@code "direct"}(직접 접근).</p>
     */
    @Column(name = "watch_source", length = 50)
    private String watchSource;

    /**
     * 실제 시청 시간 (초 단위).
     *
     * <p>클라이언트가 측정하여 전달한다. null 이면 미측정.
     * 영화 runtime 과 비교해 {@code completionStatus} 판정에 활용 가능.</p>
     */
    @Column(name = "watch_duration_seconds")
    private Integer watchDurationSeconds;

    /**
     * 시청 완료 상태.
     *
     * <p>{@code "COMPLETED"}(완주), {@code "ABANDONED"}(중도 포기),
     * {@code "IN_PROGRESS"}(시청 중).</p>
     */
    @Column(name = "completion_status", length = 30)
    private String completionStatus;

    /**
     * 빌더 생성자.
     *
     * <p>{@code watchedAt} 은 호출 측에서 null 인 경우 {@code LocalDateTime.now()} 로 채워서 전달한다.
     * 본 엔티티는 watchedAt 의 NULL 을 허용하지 않으므로 빌더 호출 전 검증이 필요하다.</p>
     */
    @Builder
    public UserWatchHistory(String userId, String movieId, LocalDateTime watchedAt,
                            Double rating, String watchSource,
                            Integer watchDurationSeconds, String completionStatus) {
        this.userId = userId;
        this.movieId = movieId;
        this.watchedAt = watchedAt;
        this.rating = rating;
        this.watchSource = watchSource;
        this.watchDurationSeconds = watchDurationSeconds;
        this.completionStatus = completionStatus;
    }
}
