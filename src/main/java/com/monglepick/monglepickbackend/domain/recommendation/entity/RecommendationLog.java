package com.monglepick.monglepickbackend.domain.recommendation.entity;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 로그 엔티티 — recommendation_log 테이블 매핑.
 *
 * <p>AI Agent가 사용자에게 추천한 영화의 상세 기록을 저장한다.
 * 추천 이유, 점수 상세(CF/CBF/Hybrid), 장르/무드 매치율 등을 포함하여
 * 추천 품질 분석 및 피드백 수집에 활용된다.</p>
 *
 * <h3>점수 상세 (ScoreDetail)</h3>
 * <ul>
 *   <li>{@code score} — 최종 추천 점수 (필수)</li>
 *   <li>{@code cfScore} — Collaborative Filtering 점수</li>
 *   <li>{@code cbfScore} — Content-Based Filtering 점수</li>
 *   <li>{@code hybridScore} — CF + CBF 하이브리드 합산 점수</li>
 *   <li>{@code genreMatch} — 장르 일치율 (0.0~1.0)</li>
 *   <li>{@code moodMatch} — 무드 매치율 (0.0~1.0)</li>
 *   <li>{@code rankPosition} — 추천 목록 내 순위 (1부터 시작)</li>
 * </ul>
 */
@Entity
@Table(
        name = "recommendation_log",
        indexes = {
                // 사용자별 추천 이력 조회 시 사용 (user_id + 최신순 정렬)
                @Index(name = "idx_recommendation_user", columnList = "user_id"),
                // 기간별 추천 통계 집계 시 사용
                @Index(name = "idx_recommendation_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — PK 필드명: id → recommendationLogId로 변경 (DDL 컬럼명 recommendation_log_id 매핑)
 * — 수동 @CreationTimestamp created_at 필드 제거됨
 */
public class RecommendationLog extends BaseAuditEntity {

    /** 추천 로그 고유 ID (PK, BIGINT AUTO_INCREMENT, 컬럼명: recommendation_log_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_log_id")
    private Long recommendationLogId;

    /**
     * 추천 대상 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). Movie 참조는 backend가 movies 테이블의
     * DDL 마스터이므로 그대로 @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 추천이 발생한 채팅 세션 ID (UUID v4).
     * 같은 세션에서 여러 영화가 추천될 수 있다.
     */
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    /**
     * 추천된 영화.
     * recommendation_log.movie_id → movies.movie_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    /** 추천 이유 설명 (AI가 생성한 자연어 텍스트, 필수) */
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    /** 최종 추천 점수 (필수) */
    @Column(name = "score", nullable = false)
    private Float score;

    /** Collaborative Filtering 점수 (유사 사용자 기반) */
    @Column(name = "cf_score")
    private Float cfScore;

    /** Content-Based Filtering 점수 (콘텐츠 유사도 기반) */
    @Column(name = "cbf_score")
    private Float cbfScore;

    /** 하이브리드 합산 점수 (CF + CBF 가중 합산) */
    @Column(name = "hybrid_score")
    private Float hybridScore;

    /** 장르 일치율 (0.0~1.0, 사용자 선호 장르와의 매치 비율) */
    @Column(name = "genre_match")
    private Float genreMatch;

    /** 무드 매치율 (0.0~1.0, 사용자 감정/무드 태그와의 매치 비율) */
    @Column(name = "mood_match")
    private Float moodMatch;

    /** 추천 목록 내 순위 (1부터 시작, 상위일수록 작은 값) */
    @Column(name = "rank_position")
    private Integer rankPosition;

    /**
     * 사용자 추천 의도 요약 (TEXT, nullable).
     * Intent-First 아키텍처에서 LLM이 추출한 user_intent 자연어 요약.
     * 추천 품질 분석 및 A/B 테스트에 활용된다.
     */
    @Column(name = "user_intent", columnDefinition = "TEXT")
    private String userIntent;

    /**
     * 추천 응답 소요 시간 (ms, nullable).
     * context_loader → response_formatter 전체 파이프라인 처리 시간.
     * 성능 모니터링 및 병목 분석에 활용된다.
     */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /**
     * 사용 LLM/알고리즘 버전 (VARCHAR(50), nullable).
     * 예: "exaone-4.0-32b", "solar-pro", "hybrid-v2".
     * 모델 버전별 추천 품질 비교에 활용된다.
     */
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    /**
     * 사용자 클릭 여부 (기본값: false).
     * 추천된 영화를 사용자가 실제로 클릭/상세 조회했는지 기록.
     * CTR(Click-Through Rate) 분석 및 추천 품질 평가에 활용된다.
     */
    @Column(name = "clicked", nullable = false)
    @Builder.Default
    private Boolean clicked = false;

    /* created_at은 BaseAuditEntity(→BaseTimeEntity)에서 자동 관리 — 수동 @CreationTimestamp 필드 제거됨 */
}
