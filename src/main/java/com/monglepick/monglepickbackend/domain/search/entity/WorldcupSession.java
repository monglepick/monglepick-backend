package com.monglepick.monglepickbackend.domain.search.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이상형 월드컵 세션 엔티티 — worldcup_session 테이블 매핑.
 *
 * <p>사용자가 참여하는 영화 이상형 월드컵 토너먼트의 진행 세션을 저장한다.
 * 각 세션은 라운드 크기(16/32/64강), 현재 진행 라운드, 매치 순서를 추적하며
 * 최종 우승 영화 결정 시 {@code COMPLETED} 상태로 전환된다.</p>
 *
 * <h3>세션 생명주기</h3>
 * <pre>
 * 시작(IN_PROGRESS) → [매치 선택 반복] → 완료(COMPLETED) / 중단(ABANDONED)
 * </pre>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId}             — 참가 사용자 ID (VARCHAR(50))</li>
 *   <li>{@code sourceType}         — 후보 산정 방식 (CATEGORY / GENRE)</li>
 *   <li>{@code categoryId}         — CATEGORY 방식일 때 선택한 카테고리 ID</li>
 *   <li>{@code selectedGenresJson} — GENRE 방식일 때 선택한 장르 목록(JSON)</li>
 *   <li>{@code candidatePoolSize}  — 시작 시점 후보 풀 총 개수</li>
 *   <li>{@code roundSize}          — 총 라운드 크기 (16/32/64)</li>
 *   <li>{@code currentRound}       — 현재 진행 라운드 번호 (16→8→4→2→1)</li>
 *   <li>{@code currentMatchOrder}  — 현재 라운드 내 매치 순서 (0-based)</li>
 *   <li>{@code status}             — 세션 상태 ({@link WorldcupStatus})</li>
 *   <li>{@code winnerMovieId}      — 최종 우승 영화 ID (완료 시에만 설정)</li>
 *   <li>{@code startedAt}          — 세션 시작 시각</li>
 *   <li>{@code completedAt}        — 세션 완료 시각 (nullable)</li>
 *   <li>{@code rewardGranted}      — 리워드 지급 완료 여부</li>
 * </ul>
 *
 * <h3>인덱스</h3>
 * <ul>
 *   <li>{@code idx_session_user} — user_id + status 복합 인덱스 (진행 중 세션 조회 최적화)</li>
 *   <li>{@code idx_session_category} — category_id 인덱스 (카테고리별 통계 조회)</li>
 * </ul>
 */
@Entity
@Table(
        name = "worldcup_session",
        indexes = {
                /* 사용자별 진행 중 세션 조회 최적화 */
                @Index(name = "idx_session_user", columnList = "user_id, status"),
                /* 카테고리별 세션 통계 조회 최적화 */
                @Index(name = "idx_session_category", columnList = "category_id"),
                @Index(name = "idx_session_source", columnList = "source_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WorldcupSession extends BaseAuditEntity {

    /**
     * 세션 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    /**
     * 참가 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 후보 산정 방식 (CATEGORY / GENRE).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private WorldcupSourceType sourceType;

    /**
     * CATEGORY 시작 방식일 때 선택한 카테고리 ID.
     */
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * GENRE 시작 방식일 때 선택한 장르 목록 JSON.
     */
    @Column(name = "selected_genres_json", columnDefinition = "JSON")
    private String selectedGenresJson;

    /**
     * 시작 시점 후보 풀 총 개수.
     */
    @Column(name = "candidate_pool_size", nullable = false)
    private Integer candidatePoolSize;

    /**
     * 총 라운드 크기 (NOT NULL).
     * 가능한 값: 16(16강), 32(32강), 64(64강).
     * 세션 생성 시 결정되며 이후 변경 불가.
     */
    @Column(name = "round_size", nullable = false)
    private Integer roundSize;

    /**
     * 현재 진행 중인 라운드 번호 (NOT NULL).
     * 16강 시작 시 16, 8강 진입 시 8, 4강=4, 결승=2, 최종=1 순으로 감소한다.
     * 라운드가 완료되면 절반으로 줄어든다(예: 16 → 8).
     */
    @Column(name = "current_round", nullable = false)
    private Integer currentRound;

    /**
     * 현재 라운드 내 매치 순서 (0-based, NOT NULL, 기본값 0).
     * 라운드 내에서 몇 번째 매치까지 완료되었는지 추적한다.
     * 예: 16강(8경기) 중 3번째 경기가 완료되면 currentMatchOrder=3.
     */
    @Column(name = "current_match_order", nullable = false)
    @Builder.Default
    private Integer currentMatchOrder = 0;

    /**
     * 세션 상태 ({@link WorldcupStatus}, NOT NULL).
     * 기본값: IN_PROGRESS.
     * 완주 시 COMPLETED, 중단 시 ABANDONED로 전환.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WorldcupStatus status = WorldcupStatus.IN_PROGRESS;

    /**
     * 최종 우승 영화 ID (VARCHAR(50), nullable).
     * 세션 완료(COMPLETED) 시에만 설정된다.
     * movies.movie_id를 논리적으로 참조한다.
     */
    @Column(name = "winner_movie_id", length = 50)
    private String winnerMovieId;

    /**
     * 세션 시작 시각 (NOT NULL).
     * 세션 생성 시 현재 시각으로 설정된다.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * 세션 완료 시각 (nullable).
     * 세션이 COMPLETED 상태로 전환될 때 설정된다.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 리워드 지급 완료 여부 (기본값 false).
     * 세션 완료 후 WORLDCUP_COMPLETE 리워드 지급 시 true로 전환된다.
     * 중복 지급 방지를 위한 멱등성 플래그.
     */
    @Column(name = "reward_granted", nullable = false)
    @Builder.Default
    private boolean rewardGranted = false;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 세션을 완료 처리한다.
     *
     * <p>status를 COMPLETED로 전환하고 최종 우승 영화 ID와 완료 시각을 기록한다.
     * 서비스 레이어에서 마지막 라운드(round=1) 매치 선택 완료 시 호출한다.</p>
     *
     * @param winnerMovieId 최종 우승 영화 ID (movies.movie_id)
     */
    public void complete(String winnerMovieId) {
        this.status = WorldcupStatus.COMPLETED;
        this.winnerMovieId = winnerMovieId;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 세션을 중단 처리한다.
     *
     * <p>사용자가 월드컵을 완주하지 않고 이탈할 때 호출한다.
     * ABANDONED 상태의 세션은 리워드 지급 대상에서 제외된다.</p>
     */
    public void abandon() {
        this.status = WorldcupStatus.ABANDONED;
    }

    /**
     * 리워드 지급 완료를 표시한다.
     *
     * <p>RewardService.grantReward() 호출 성공 후 중복 지급 방지를 위해 설정한다.</p>
     */
    public void markRewardGranted() {
        this.rewardGranted = true;
    }

    /**
     * 현재 라운드 내 매치 순서를 1 증가시킨다.
     *
     * <p>매치가 완료될 때마다 호출하여 현재 라운드의 진행 상황을 추적한다.</p>
     */
    public void nextMatch() {
        this.currentMatchOrder++;
    }

    /**
     * 다음 라운드로 진입한다.
     *
     * <p>현재 라운드의 모든 매치가 완료되면 서비스 레이어에서 호출한다.
     * currentRound를 절반으로 줄이고 currentMatchOrder를 0으로 초기화한다.
     * 예: 16강(16) 완료 → 8강(8) 진입.</p>
     */
    public void advanceRound() {
        this.currentRound = this.currentRound / 2;
        this.currentMatchOrder = 0;
    }
}
