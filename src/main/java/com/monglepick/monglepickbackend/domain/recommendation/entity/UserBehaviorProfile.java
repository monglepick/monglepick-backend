package com.monglepick.monglepickbackend.domain.recommendation.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 행동 프로필 엔티티 — user_behavior_profile 테이블 매핑.
 *
 * <p>AI 추천 에이전트의 협업 필터링(CF)·콘텐츠 기반 필터링(CBF) 가중치 동적 결정에 활용된다.
 * BehaviorProfileScheduler가 매일 새벽 3시에 최근 활동 데이터를 집계하여 갱신한다.</p>
 *
 * <h3>주요 활용처</h3>
 * <ul>
 *   <li>{@code tasteConsistency} — 높을수록 취향이 편향적 → CBF 가중치 상향</li>
 *   <li>{@code recommendationAcceptanceRate} — 높을수록 추천 수용성 높음 → CF 비중 상향</li>
 *   <li>{@code activityLevel} — dormant 유저에 콜드스타트 전략 적용</li>
 * </ul>
 *
 * <h3>DDL 진실 원본</h3>
 * <p>JPA {@code ddl-auto=update}로 관리되므로 이 엔티티가 스키마의 진실 원본이다.</p>
 *
 * <p>BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리</p>
 */
@Entity
@Table(name = "user_behavior_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 기본 생성자 (외부 직접 생성 방지)
@AllArgsConstructor
@Builder
public class UserBehaviorProfile extends BaseAuditEntity {

    /**
     * 사용자 ID (PK, VARCHAR(50)).
     * users.user_id를 참조하는 논리 FK이며, 1 유저당 1 레코드를 보장한다.
     */
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    /**
     * 장르 친화도 (JSON).
     * 최근 100건 시청 이력을 기반으로 정규화된 장르별 선호 강도를 저장한다.
     * 예: {"액션": 0.8, "로맨스": 0.6, "SF": 0.4}
     */
    @Column(name = "genre_affinity", columnDefinition = "JSON")
    private String genreAffinity;

    /**
     * 무드 친화도 (JSON).
     * 시청 이력 및 이벤트 로그에서 추론된 무드 태그별 선호 강도를 저장한다.
     * 예: {"감동적인": 0.9, "긴장감넘치는": 0.4, "유쾌한": 0.7}
     */
    @Column(name = "mood_affinity", columnDefinition = "JSON")
    private String moodAffinity;

    /**
     * 선호 감독 Top10 친화도 (JSON).
     * 시청한 영화의 감독 빈도를 기반으로 정규화된 선호 강도를 저장한다.
     * 예: {"봉준호": 0.9, "크리스토퍼 놀란": 0.7}
     */
    @Column(name = "director_affinity", columnDefinition = "JSON")
    private String directorAffinity;

    /**
     * 취향 일관성 지수 (0.0 ~ 1.0).
     * 장르 빈도 분포의 Shannon Entropy를 역산한 값으로,
     * 1.0에 가까울수록 특정 장르에 강하게 편향되어 있음을 의미한다.
     * CBF(콘텐츠 기반 필터링) 가중치 결정에 활용된다.
     * 계산식: 1.0 - (entropy / max_entropy)
     */
    @Column(name = "taste_consistency")
    private Double tasteConsistency;

    /**
     * 추천 수용률 (0.0 ~ 1.0).
     * AI 추천 영화 중 클릭 또는 시청으로 이어진 비율이다.
     * 계산식: (clicked_count + watched_count) / total_recommended_count
     */
    @Column(name = "recommendation_acceptance_rate")
    private Double recommendationAcceptanceRate;

    /**
     * 평균 세션 내 탐색 깊이.
     * 한 세션에서 평균적으로 클릭한 추천 카드 수를 의미하며,
     * 값이 클수록 탐색적 성향(탐험형 유저)임을 나타낸다.
     */
    @Column(name = "avg_exploration_depth")
    private Double avgExplorationDepth;

    /**
     * 활동 수준 (VARCHAR(20)).
     * 최근 30일 이벤트 수를 기준으로 분류된다.
     * <ul>
     *   <li>dormant  — 0~5건 (비활성)</li>
     *   <li>casual   — 6~20건 (일반)</li>
     *   <li>active   — 21~100건 (활성)</li>
     *   <li>power    — 101건 이상 (파워 유저)</li>
     * </ul>
     */
    @Column(name = "activity_level", length = 20)
    private String activityLevel;

    /**
     * 프로필 마지막 갱신 시각.
     * BehaviorProfileScheduler가 프로필을 재계산할 때마다 갱신된다.
     */
    @Column(name = "profile_updated_at")
    private LocalDateTime profileUpdatedAt;

    // ========== 도메인 메서드 ==========

    /**
     * 행동 프로필 전체를 갱신한다.
     *
     * <p>BehaviorProfileScheduler의 배치 처리에서 호출된다.
     * setter 대신 도메인 메서드를 통해 불변성을 최대한 유지하고,
     * profileUpdatedAt을 현재 시각으로 자동 설정한다.</p>
     *
     * @param genreAffinity                장르 친화도 JSON 문자열
     * @param moodAffinity                 무드 친화도 JSON 문자열
     * @param directorAffinity             감독 친화도 JSON 문자열
     * @param tasteConsistency             취향 일관성 지수 (0.0~1.0)
     * @param recommendationAcceptanceRate 추천 수용률 (0.0~1.0)
     * @param avgExplorationDepth          평균 탐색 깊이
     * @param activityLevel                활동 수준 (dormant/casual/active/power)
     */
    public void updateProfile(
            String genreAffinity,
            String moodAffinity,
            String directorAffinity,
            Double tasteConsistency,
            Double recommendationAcceptanceRate,
            Double avgExplorationDepth,
            String activityLevel
    ) {
        this.genreAffinity = genreAffinity;
        this.moodAffinity = moodAffinity;
        this.directorAffinity = directorAffinity;
        this.tasteConsistency = tasteConsistency;
        this.recommendationAcceptanceRate = recommendationAcceptanceRate;
        this.avgExplorationDepth = avgExplorationDepth;
        this.activityLevel = activityLevel;
        this.profileUpdatedAt = LocalDateTime.now();
    }
}
