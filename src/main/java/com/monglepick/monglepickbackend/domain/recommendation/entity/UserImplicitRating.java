package com.monglepick.monglepickbackend.domain.recommendation.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 암시적 평점 엔티티 — user_implicit_rating 테이블 매핑.
 *
 * <p>사용자의 명시적 평점(별점)이 아니라, 클릭·상세 조회·즐겨찾기·좋아요·시청 완료 등
 * 행동 데이터를 가중 합산하여 산출한 암시적 평점을 저장한다.</p>
 *
 * <p>CF(협업 필터링) 추천 엔진이 이 점수를 사용자-영화 상호작용 행렬로 활용하므로,
 * Cold Start 완화 및 Warm-up 단계 전환 기준 점수로도 쓰인다.</p>
 *
 * <h3>점수 범위</h3>
 * <ul>
 *   <li>최소 0.0 (부정 행동이 연속되더라도 음수로 내려가지 않음)</li>
 *   <li>최대 5.0 (행동이 아무리 많아도 별점 스케일 초과 불가)</li>
 * </ul>
 *
 * <h3>contributing_actions 포맷</h3>
 * <pre>{"click": 2, "detail_view": 1, "wishlist_add": 1, "like": 0, ...}</pre>
 *
 * <p>(userId, movieId) 쌍에 UNIQUE 제약을 걸어 사용자당 영화 1건의 누적 레코드만 유지한다.</p>
 */
@Entity
@Table(
        name = "user_implicit_rating",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_implicit_user_movie",
                columnNames = {"user_id", "movie_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserImplicitRating extends BaseAuditEntity {

    /**
     * 암시적 평점 고유 ID (PK, BIGINT AUTO_INCREMENT).
     * 컬럼명: implicit_rating_id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "implicit_rating_id")
    private Long implicitRatingId;

    /**
     * 사용자 ID (FK → users.user_id, VARCHAR(50), NOT NULL).
     * UNIQUE 제약의 첫 번째 컬럼.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 영화 ID (FK → movies.movie_id, VARCHAR(50), NOT NULL).
     * UNIQUE 제약의 두 번째 컬럼.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /**
     * 암시적 평점 (0.0 ~ 5.0, 행동 기반 가중 합산, NOT NULL).
     *
     * <p>행동별 가중치 예시: click=+0.5, like=+2.0, not_interested=-3.0.
     * {@link #addScore(double)} 호출마다 clamp(0.0, 5.0) 적용.</p>
     */
    @Column(name = "implicit_score", nullable = false)
    @Builder.Default
    private Double implicitScore = 0.0;

    /**
     * 기여 행동 카운트 JSON (JSON 컬럼, nullable).
     *
     * <p>포맷: {@code {"click": 1, "detail_view": 1, "wishlist_add": 1, ...}}.
     * 행동이 발생할 때마다 해당 키의 카운트가 1씩 증가한다.
     * {@link UserImplicitRatingService#recordAction(String, String, String)} 에서 갱신된다.</p>
     */
    @Column(name = "contributing_actions", columnDefinition = "JSON")
    private String contributingActions;

    /**
     * 마지막 행동 발생 시각 (nullable).
     *
     * <p>{@link #addScore(double)} 호출 시 {@code LocalDateTime.now()}로 자동 갱신된다.
     * 오래된 암시적 평점을 배치 감쇄(Decay) 처리할 때 기준 시각으로 활용된다.</p>
     */
    @Column(name = "last_action_at")
    private LocalDateTime lastActionAt;

    // -------------------------------------------------------------------------
    // 도메인 메서드
    // -------------------------------------------------------------------------

    /**
     * 암시적 점수에 delta를 가산한다.
     *
     * <p>가산 후 결과가 0.0 미만이면 0.0으로, 5.0 초과이면 5.0으로 클램프한다.
     * 행동 발생 시각(lastActionAt)을 현재 시각으로 갱신한다.</p>
     *
     * @param delta 가산할 점수 변화량 (음수 가능 — not_interested, skip)
     */
    public void addScore(double delta) {
        this.implicitScore = Math.max(0.0, Math.min(5.0, this.implicitScore + delta));
        this.lastActionAt = LocalDateTime.now();
    }

    /**
     * 기여 행동 JSON 문자열을 갱신한다.
     *
     * <p>서비스 계층에서 ObjectMapper로 직렬화된 JSON 문자열을 주입받아 저장한다.
     * JPA dirty checking으로 트랜잭션 커밋 시 자동 UPDATE된다.</p>
     *
     * @param json 직렬화된 contributing_actions JSON 문자열
     */
    public void updateContributingActions(String json) {
        this.contributingActions = json;
    }
}
