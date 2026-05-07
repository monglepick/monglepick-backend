package com.monglepick.monglepickbackend.domain.search.entity;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이상형 월드컵 결과 엔티티 — worldcup_results 테이블 매핑.
 *
 * <p>온보딩 과정에서 사용자가 참여하는 "영화 이상형 월드컵"의 결과를 저장한다.
 * 토너먼트 형식으로 영화를 비교/선택하여 사용자의 취향을 파악하고,
 * 이를 초기 선호도(UserPreference)로 반영한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseAuditEntity 상속 추가 (created_at/updated_at/created_by/updated_by 자동 관리)</li>
 *   <li>2026-03-24: PK 필드명 id → worldcupResultId 로 변경, @Column(name = "worldcup_result_id") 추가</li>
 *   <li>2026-03-24: 수동 createdAt (@CreationTimestamp) 필드 제거 — BaseAuditEntity가 created_at 자동 관리</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 참여 사용자 ID (String FK → users.user_id, JPA/MyBatis 하이브리드 §15.4)</li>
 *   <li>{@code roundSize} — 토너먼트 라운드 수 (기본값: 16강)</li>
 *   <li>{@code winnerMovie} — 최종 우승 영화 (FK → movies.movie_id, 필수)</li>
 *   <li>{@code runnerUpMovie} — 준우승 영화 (FK → movies.movie_id, 선택)</li>
 *   <li>{@code semiFinalMovieIds} — 4강 진출 영화 ID 목록 (TEXT)</li>
 *   <li>{@code selectionLog} — 각 라운드별 선택 로그 (TEXT)</li>
 *   <li>{@code genrePreferences} — 선택 결과에서 추출된 장르 선호도 (TEXT)</li>
 *   <li>{@code onboardingCompleted} — 온보딩 완료 여부 (필수)</li>
 * </ul>
 */
@Entity
@Table(name = "worldcup_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속 추가: created_at, updated_at, created_by, updated_by 컬럼 자동 관리 */
public class WorldcupResult extends BaseAuditEntity {

    /**
     * 월드컵 결과 고유 ID (BIGINT AUTO_INCREMENT PK).
     * DB 레거시 컬럼명은 id이며, Java 필드명만 worldcupResultId로 유지한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long worldcupResultId;

    /**
     * 월드컵 참여 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). Movie 참조는 backend가 movies 테이블의
     * DDL 마스터이므로 그대로 @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 토너먼트 라운드 수.
     * 기본값: 16 (16강).
     * 가능한 값: 8, 16, 32, 64 등
     */
    @Column(name = "round_size", nullable = false)
    @Builder.Default
    private Integer roundSize = 16;

    /**
     * 최종 우승 영화 (필수).
     * worldcup_results.winner_movie_id → movies.movie_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_movie_id", nullable = false)
    private Movie winnerMovie;

    /**
     * 준우승 영화 (선택).
     * worldcup_results.runner_up_movie_id → movies.movie_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "runner_up_movie_id")
    private Movie runnerUpMovie;

    /**
     * 4강 진출 영화 ID 목록 (TEXT).
     * 쉼표 구분 문자열 또는 JSON 배열 형태로 저장.
     * 예: "12345,67890,11111,22222"
     */
    @Column(name = "semi_final_movie_ids", columnDefinition = "TEXT")
    private String semiFinalMovieIds;

    /**
     * 각 라운드별 선택 로그 (TEXT).
     * 사용자가 각 매치에서 어떤 영화를 선택했는지 기록.
     * JSON 또는 구조화된 텍스트 형태.
     */
    @Column(name = "selection_log", columnDefinition = "TEXT")
    private String selectionLog;

    /**
     * 선택 결과에서 추출된 장르 선호도 (TEXT).
     * 우승/상위 진출 영화들의 장르를 분석하여 선호 장르를 도출.
     * 예: "액션:3,SF:2,드라마:1"
     */
    @Column(name = "genre_preferences", columnDefinition = "TEXT")
    private String genrePreferences;

    /**
     * 온보딩 완료 여부 (필수).
     * true이면 월드컵 결과가 UserPreference에 반영 완료되었음을 의미.
     * 기본값: false
     */
    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private Boolean onboardingCompleted = false;

    /**
     * 진행 세션 ID — worldcup_session.session_id 논리적 참조 (nullable).
     *
     * <p>신규 월드컵 세션 기반 결과는 session_id가 설정된다.
     * 레거시(온보딩 전용) 결과 또는 세션 없이 생성된 결과는 NULL이다.</p>
     */
    @Column(name = "session_id")
    private Long sessionId;

    /**
     * 리워드 지급 여부 (기본값 false).
     *
     * <p>WORLDCUP_COMPLETE / WORLDCUP_FIRST 리워드 지급 후 true로 전환된다.
     * WorldcupSession.rewardGranted 와 별도로 결과 레코드에도 기록하여
     * 결과 기반 집계 시 이중 지급 여부를 확인할 수 있다.</p>
     */
    @Column(name = "reward_granted", nullable = false)
    @Builder.Default
    private boolean rewardGranted = false;

    /**
     * 총 매치 수 (nullable, 통계용).
     *
     * <p>완료된 토너먼트에서 사용자가 선택한 전체 매치 수를 저장한다.
     * 16강 기준 = 15경기 (16+8+4+2+1 = 31 → 16/2 + ... = 15).
     * 통계 및 분석 페이지에서 활동량 지표로 활용한다.</p>
     */
    @Column(name = "total_matches")
    private Integer totalMatches;

    /* 수동 createdAt 필드 제거됨 — BaseAuditEntity가 created_at 컬럼을 자동 관리 */
}
