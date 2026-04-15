package com.monglepick.monglepickbackend.domain.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Agent 내부 호출용 추천 로그 배치 저장 DTO 모음 (2026-04-15 신규).
 *
 * <p>AI Agent 가 {@code recommendation_ranker} 노드 완료 후 {@code movie_card}
 * SSE 이벤트를 발행하는 시점에, 추천된 영화 N 개를 한 번에 {@code recommendation_log}
 * 테이블에 기록하기 위해 사용한다. 기존 {@link RecommendationHistoryDto} 는
 * <b>유저 대면 조회</b> 전용이므로 본 파일에 분리해 관심사 구분.</p>
 *
 * <h3>사용 경로</h3>
 * <ol>
 *   <li>Agent: {@code monglepick-agent/.../api/recommendation_client.py::save_recommendation_logs()}
 *       → movies 를 {@link Item} 리스트로 직렬화해 Backend 로 전송</li>
 *   <li>Backend: {@code POST /api/v1/recommendations/internal/batch}
 *       ({@code X-Service-Key} 인증) → {@code RecommendationLogService.saveBatch}</li>
 *   <li>반환된 {@link SaveBatchResponse#recommendationLogIds()} 를 Agent 가
 *       각 영화의 {@code recommendation_log_id} 로 매핑해 SSE {@code movie_card}
 *       payload 에 실어 보낸다 → Client "관심없음"/"좋아요" 피드백 FK 성립</li>
 * </ol>
 *
 * <h3>저장 범위 (2026-04-15 v1)</h3>
 * <ul>
 *   <li>{@code recommendation_log} 테이블의 모든 점수/메타 컬럼을 Agent 에서 직접 주입</li>
 *   <li>{@code emotion}/{@code moodTags} 는 현 Entity 에 별도 컬럼이 없으므로 본 범위에서
 *       <b>헤더 메타로만 받고 저장하지 않는다</b> (후속에 Entity 확장 시 컬럼 매핑 예정).
 *       다만 {@code userIntent} 는 엔티티 컬럼이 있으므로 Agent 가 요약 문자열로 채워 보낸다.</li>
 * </ul>
 */
public class RecommendationLogBatchDto {

    // ─────────────────────────────────────────────
    // 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 배치 저장 요청 — 단일 추천 턴에서 발행된 영화 N 개를 한 번에 INSERT 하기 위한 상위 DTO.
     *
     * @param userId          추천 대상 사용자 ID (ServiceKey 인증 시 body 신뢰)
     * @param sessionId       추천이 발생한 채팅 세션 UUID ({@code chat_session_archive.session_id} 와 동일 값)
     * @param userIntent      사용자 의도 자연어 요약 (Intent-First 아키텍처에서 LLM 이 추출). 모든 Item 에 동일하게 복사 저장
     * @param emotion         (메타) 감정 라벨 — 현재 미저장, 후속 Entity 확장 대비
     * @param moodTags        (메타) 무드 태그 목록 — 현재 미저장, 후속 Entity 확장 대비
     * @param responseTimeMs  그래프 전체 응답 소요 시간 (ms). 모든 Item 에 동일하게 복사 저장 (성능 분석용)
     * @param modelVersion    사용 LLM/알고리즘 버전 식별자 (예: "chat-v3.4")
     * @param items           추천된 영화별 점수/메타 목록 (순서 보존 필수 — 반환 ID 순서 매핑에 사용)
     */
    public record SaveBatchRequest(
            @NotBlank(message = "userId 는 필수입니다")
            String userId,

            @NotBlank(message = "sessionId 는 필수입니다")
            String sessionId,

            /** Intent-First 요약. null 이면 빈 문자열 취급 */
            String userIntent,

            /** 현재 미저장 — Entity 확장 시 활성화 */
            String emotion,

            /** 현재 미저장 — Entity 확장 시 활성화 */
            List<String> moodTags,

            /** 그래프 응답 소요 시간 (ms). null 허용 */
            Integer responseTimeMs,

            /** 모델 버전 라벨. null 허용, length <= 50 */
            String modelVersion,

            @NotEmpty(message = "items 는 1개 이상이어야 합니다")
            @Valid
            List<Item> items
    ) {

        /**
         * 배치 내 개별 영화 항목.
         *
         * @param movieId       추천된 영화 ID (movies.movie_id FK)
         * @param rankPosition  추천 목록 내 순위 (1부터 시작)
         * @param reason        AI 가 생성한 추천 이유 자연어 텍스트 (Entity `reason` NOT NULL 대응 — 빈 값이면 " ")
         * @param score         최종 추천 점수 (Entity `score` NOT NULL)
         * @param cfScore       CF 점수 (nullable)
         * @param cbfScore      CBF 점수 (nullable)
         * @param hybridScore   하이브리드 합산 점수 (nullable)
         * @param genreMatch    장르 일치율 0.0~1.0 (nullable)
         * @param moodMatch     무드 매치율 0.0~1.0 (nullable)
         */
        public record Item(
                @NotBlank(message = "movieId 는 필수입니다")
                String movieId,

                @NotNull(message = "rankPosition 은 필수입니다")
                Integer rankPosition,

                /** Entity 의 reason 컬럼이 NOT NULL 이므로 null 이면 Service 에서 " " 로 대체 */
                String reason,

                @NotNull(message = "score 는 필수입니다")
                Float score,

                Float cfScore,
                Float cbfScore,
                Float hybridScore,
                Float genreMatch,
                Float moodMatch
        ) {}
    }

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 배치 저장 응답 — 저장된 {@code recommendation_log} PK 리스트.
     *
     * <p>요청 {@link SaveBatchRequest#items()} 와 <b>순서가 동일하게 보존</b>된다.
     * Agent 는 이 순서를 믿고 각 영화에 {@code recommendation_log_id} 를 매핑한다.
     * 일부 영화의 movieId 가 {@code movies} 테이블에 존재하지 않아 skip 된 경우
     * 해당 자리는 {@code null} 로 채워진다 (length 보존).</p>
     *
     * @param recommendationLogIds 저장된 PK 리스트 (skip 된 자리는 null, 길이는 요청 items 와 동일)
     */
    public record SaveBatchResponse(List<Long> recommendationLogIds) {}
}
