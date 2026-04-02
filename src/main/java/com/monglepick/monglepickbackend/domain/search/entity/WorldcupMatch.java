package com.monglepick.monglepickbackend.domain.search.entity;

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

import java.time.LocalDateTime;

/**
 * 이상형 월드컵 개별 매치 엔티티 — worldcup_match 테이블 매핑.
 *
 * <p>월드컵 세션({@link WorldcupSession}) 내의 각 대결(매치)을 저장한다.
 * 한 라운드는 roundSize/2개의 매치로 구성되며, 각 매치에서 사용자가 두 영화 중
 * 하나를 선택하면 승자(winnerMovieId)가 기록된다.</p>
 *
 * <h3>매치 구성 예시 (16강)</h3>
 * <pre>
 * roundNumber=16, matchOrder=0 → movieA vs movieB → 승자 선택
 * roundNumber=16, matchOrder=1 → movieC vs movieD → 승자 선택
 * ...총 8경기 완료 시 8강(roundNumber=8) 매치 자동 생성
 * </pre>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(session_id, round_number, match_order) — 동일 세션 내 라운드+순서 중복 불가</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code session}        — 소속 세션 (FK → worldcup_session.session_id)</li>
 *   <li>{@code roundNumber}    — 라운드 번호 (16강=16, 8강=8, 4강=4, 결승=2, 3위=1)</li>
 *   <li>{@code matchOrder}     — 라운드 내 순서 (0-based)</li>
 *   <li>{@code movieAId}       — 대결 영화 A의 ID</li>
 *   <li>{@code movieBId}       — 대결 영화 B의 ID</li>
 *   <li>{@code winnerMovieId}  — 사용자가 선택한 승자 영화 ID (선택 전 null)</li>
 *   <li>{@code selectedAt}     — 선택 완료 시각 (선택 전 null)</li>
 * </ul>
 */
@Entity
@Table(
        name = "worldcup_match",
        uniqueConstraints = {
                /* 동일 세션 내에서 라운드+순서 조합은 유일해야 한다 */
                @UniqueConstraint(
                        name = "uk_session_round_order",
                        columnNames = {"session_id", "round_number", "match_order"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WorldcupMatch extends BaseAuditEntity {

    /**
     * 매치 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long matchId;

    /**
     * 소속 세션 (LAZY, NOT NULL).
     * worldcup_match.session_id → worldcup_session.session_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private WorldcupSession session;

    /**
     * 라운드 번호 (NOT NULL).
     * 토너먼트 강수를 직접 사용한다.
     * 예: 16강=16, 8강=8, 4강=4, 결승=2, 최종 우승 결정=1.
     */
    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    /**
     * 라운드 내 매치 순서 (0-based, NOT NULL).
     * 동일 라운드 내에서 이 매치의 순서를 나타낸다.
     * 예: 16강 첫 번째 매치=0, 두 번째=1, ..., 여덟 번째=7.
     */
    @Column(name = "match_order", nullable = false)
    private Integer matchOrder;

    /**
     * 대결 영화 A의 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 논리적으로 참조한다.
     */
    @Column(name = "movie_a_id", length = 50, nullable = false)
    private String movieAId;

    /**
     * 대결 영화 B의 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 논리적으로 참조한다.
     */
    @Column(name = "movie_b_id", length = 50, nullable = false)
    private String movieBId;

    /**
     * 사용자가 선택한 승자 영화 ID (VARCHAR(50), nullable).
     * 사용자가 movieA 또는 movieB 중 하나를 선택하면 설정된다.
     * 선택 전에는 null이다.
     */
    @Column(name = "winner_movie_id", length = 50)
    private String winnerMovieId;

    /**
     * 선택 완료 시각 (nullable).
     * 사용자가 승자를 선택하는 순간 현재 시각으로 설정된다.
     * 선택 전에는 null이다.
     */
    @Column(name = "selected_at")
    private LocalDateTime selectedAt;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 이 매치의 승자를 기록한다.
     *
     * <p>사용자가 movieA 또는 movieB 중 하나를 선택하면 호출된다.
     * winnerMovieId와 selectedAt을 동시에 설정하여 원자적으로 처리한다.</p>
     *
     * @param winnerMovieId 사용자가 선택한 승자 영화 ID (movieAId 또는 movieBId)
     */
    public void selectWinner(String winnerMovieId) {
        this.winnerMovieId = winnerMovieId;
        this.selectedAt = LocalDateTime.now();
    }
}
