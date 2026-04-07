package com.monglepick.monglepickbackend.domain.recommendation.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 임팩트 엔티티 — recommendation_impact 테이블 매핑.
 *
 * <p>AI Agent가 추천한 영화에 대해 사용자가 취한 후속 행동(클릭·상세조회·위시리스트·시청·평점)을
 * 순차적으로 기록한다. 각 이벤트는 해당 플래그를 true로 갱신하는 방식으로 추적되며,
 * 추천 품질 지표(CTR, CVR, 위시리스트율 등) 산출의 원본 데이터로 활용된다.</p>
 *
 * <h3>행동 단계(Funnel)</h3>
 * <ol>
 *   <li>클릭 ({@code clicked}) — 추천 카드 클릭 + {@code timeToClickSeconds} 기록</li>
 *   <li>상세 조회 ({@code detailViewed}) — 영화 상세 페이지 진입</li>
 *   <li>위시리스트 ({@code wishlisted}) — 위시리스트 추가</li>
 *   <li>시청 완료 ({@code watched}) — 시청 이력 기록 완료</li>
 *   <li>평점 ({@code rated}) — 리뷰/별점 제출 완료</li>
 * </ol>
 *
 * <h3>유니크 제약</h3>
 * <p>(user_id, movie_id, recommendation_log_id) 조합이 유니크하므로
 * 동일 세션 내 같은 영화에 대한 임팩트는 단 하나의 레코드로 관리된다.</p>
 */
@Entity
@Table(
        name = "recommendation_impact",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_impact_user_movie_rec",
                columnNames = {"user_id", "movie_id", "recommendation_log_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 기본 생성자 (외부 직접 생성 방지)
@AllArgsConstructor
@Builder
public class RecommendationImpact extends BaseAuditEntity {

    /**
     * 임팩트 고유 ID (PK, BIGINT AUTO_INCREMENT, 컬럼명: impact_id).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "impact_id")
    private Long impactId;

    /**
     * 행동 주체 사용자 ID.
     * users.user_id를 참조하는 VARCHAR(50) 논리 FK.
     * 잦은 단순 조회를 위해 FK 객체 대신 ID 값을 직접 보관한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 추천된 영화 ID.
     * movies.movie_id를 참조하는 VARCHAR(50) 논리 FK.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /**
     * 연관 추천 로그 (물리 FK: recommendation_log_id).
     * 이 임팩트가 어느 추천 세션에서 발생한 것인지 역추적하는 데 사용된다.
     * LAZY 로딩으로 불필요한 JOIN을 방지한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_log_id")
    private RecommendationLog recommendationLog;

    /**
     * 추천 목록 내 순위 (1부터 시작).
     * 상위 순위 영화의 CTR이 높은지 분석하는 포지션 바이어스 측정에 활용된다.
     */
    @Column(name = "recommendation_position")
    private Integer recommendationPosition;

    /**
     * 추천 카드 클릭 여부 (기본값: false).
     * {@link #markClicked(int)} 호출 시 true로 전환되며, 클릭 소요 시간도 함께 기록된다.
     */
    @Column(name = "clicked")
    @Builder.Default
    private Boolean clicked = false;

    /**
     * 영화 상세 페이지 조회 여부 (기본값: false).
     * 카드 클릭 후 상세 페이지까지 진입했는지 판별한다.
     */
    @Column(name = "detail_viewed")
    @Builder.Default
    private Boolean detailViewed = false;

    /**
     * 위시리스트 추가 여부 (기본값: false).
     * 관심 영화 저장률(Save Rate) 계산의 원본 데이터다.
     */
    @Column(name = "wishlisted")
    @Builder.Default
    private Boolean wishlisted = false;

    /**
     * 시청 완료 여부 (기본값: false).
     * 실제 시청으로의 전환율(View Rate)을 측정한다.
     */
    @Column(name = "watched")
    @Builder.Default
    private Boolean watched = false;

    /**
     * 평점 부여 여부 (기본값: false).
     * 시청 후 리뷰 작성률(Rating Rate)을 측정한다.
     */
    @Column(name = "rated")
    @Builder.Default
    private Boolean rated = false;

    /**
     * 추천 후 첫 클릭까지 걸린 시간 (초, nullable).
     * 값이 작을수록 추천 카드가 사용자의 즉각적인 관심을 끌었음을 의미한다.
     * {@link #markClicked(int)}에서만 설정된다.
     */
    @Column(name = "time_to_click_seconds")
    private Integer timeToClickSeconds;

    // ========== 도메인 메서드 ==========

    /**
     * 카드 클릭 이벤트를 기록한다.
     *
     * <p>clicked 플래그를 true로 설정하고, 추천 노출 후 클릭까지
     * 소요된 시간(초)을 함께 저장한다. 이미 클릭된 레코드에 재호출해도
     * 시간은 최신 값으로 덮어쓰여지지 않도록 호출 측에서 중복 방지를 권장한다.</p>
     *
     * @param timeToClickSec 추천 노출 후 첫 클릭까지 소요된 시간 (초, 0 이상)
     */
    public void markClicked(int timeToClickSec) {
        this.clicked = true;
        this.timeToClickSeconds = timeToClickSec;
    }

    /**
     * 영화 상세 페이지 조회 이벤트를 기록한다.
     *
     * <p>detailViewed 플래그를 true로 설정한다.
     * 클릭 후 상세 페이지 진입 여부를 측정하며, 멱등 호출이 보장된다.</p>
     */
    public void markDetailViewed() {
        this.detailViewed = true;
    }

    /**
     * 위시리스트 추가 이벤트를 기록한다.
     *
     * <p>wishlisted 플래그를 true로 설정한다. 위시리스트 삭제 시에는
     * 별도 취소 이벤트를 지원하지 않으며, 한 번 기록된 관심 의사는 유지된다.</p>
     */
    public void markWishlisted() {
        this.wishlisted = true;
    }

    /**
     * 시청 완료 이벤트를 기록한다.
     *
     * <p>watched 플래그를 true로 설정한다.
     * 추천 이력 화면의 "봤어요" 토글 또는 review 작성 시점에 호출된다 — "리뷰 작성 = 시청
     * 완료 확인"이 단일 진실 원본이다 (watch_history 도메인 폐기, 2026-04-08).</p>
     */
    public void markWatched() {
        this.watched = true;
    }

    /**
     * 평점/리뷰 작성 이벤트를 기록한다.
     *
     * <p>rated 플래그를 true로 설정한다.
     * review 도메인의 INSERT 이벤트와 연동하여 호출된다.</p>
     */
    public void markRated() {
        this.rated = true;
    }

    /**
     * 위시리스트 추가를 취소한다 (찜 토글 취소용).
     *
     * <p>wishlisted 플래그를 false로 되돌린다.
     * {@code POST /api/v1/recommendations/{id}/wishlist} 토글 API에서
     * 현재 wishlisted=true 상태일 때 호출하여 찜을 취소한다.</p>
     *
     * <p>설계 주의: RecommendationImpact는 기본적으로 INSERT-ONLY 이벤트 로그이나,
     * 클라이언트 UX 요구사항(찜 토글)을 위해 취소 메서드를 제한적으로 허용한다.</p>
     */
    public void cancelWishlisted() {
        this.wishlisted = false;
    }

    /**
     * 봤어요를 취소한다 (봤어요 토글 취소용).
     *
     * <p>watched 플래그를 false로 되돌린다.
     * {@code POST /api/v1/recommendations/{id}/watched} 토글 API에서
     * 현재 watched=true 상태일 때 호출하여 봤어요를 취소한다.</p>
     */
    public void cancelWatched() {
        this.watched = false;
    }
}
