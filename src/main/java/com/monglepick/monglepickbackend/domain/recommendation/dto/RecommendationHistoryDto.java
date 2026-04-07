package com.monglepick.monglepickbackend.domain.recommendation.dto;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;

import java.time.LocalDateTime;

/**
 * 추천 이력 도메인 DTO 모음 (record 기반).
 *
 * <p>클라이언트 추천 이력 탭({@code GET /api/v1/recommendations})에서 사용하는
 * 요청·응답 DTO를 하나의 파일에 Inner Record로 묶어 관리한다.</p>
 *
 * <h3>찜/봤어요 상태 처리 전략</h3>
 * <p>RecommendationLog 엔티티에는 wishlist_yn, watched_yn 컬럼이 없다.
 * 대신 {@link com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact}의
 * {@code wishlisted}, {@code watched} 필드를 활용하여 상태를 조회하고,
 * 토글 시에도 해당 Impact 레코드를 업서트한다.</p>
 *
 * <h3>포함된 DTO</h3>
 * <ul>
 *   <li>{@link RecommendationHistoryResponse} — 추천 이력 목록 항목 응답</li>
 *   <li>{@link WishlistToggleResponse} — 찜 토글 응답</li>
 *   <li>{@link WatchedToggleResponse} — 봤어요 토글 응답</li>
 * </ul>
 */
public class RecommendationHistoryDto {

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 추천 이력 목록 항목 응답 DTO.
     *
     * <p>{@code GET /api/v1/recommendations} 페이징 응답의 content 항목으로 사용된다.
     * RecommendationLog 엔티티와 연관 Impact 상태를 조합하여 생성한다.</p>
     *
     * <h3>응답 예시 (JSON)</h3>
     * <pre>{@code
     * {
     *   "recommendationLogId": 42,
     *   "movieId": "tmdb_12345",
     *   "title": "인터스텔라",
     *   "posterPath": "/path/to/poster.jpg",
     *   "genres": "[\"SF\",\"드라마\"]",
     *   "score": 0.92,
     *   "reason": "우주 탐험과 감동적인 부자 관계를 좋아하시는 분께 추천합니다.",
     *   "recommendedAt": "2026-04-06T10:30:00",
     *   "wishlisted": false,
     *   "watched": false
     * }
     * }</pre>
     *
     * @param recommendationLogId 추천 로그 고유 ID
     * @param movieId             추천된 영화 ID
     * @param title               영화 한국어 제목
     * @param posterPath          TMDB 포스터 이미지 경로 (null 가능)
     * @param genres              장르 목록 JSON 문자열 (null 가능)
     * @param score               최종 추천 점수
     * @param reason              AI가 생성한 추천 이유 텍스트
     * @param recommendedAt       추천 발생 시각 (RecommendationLog.createdAt)
     * @param wishlisted          찜 여부 (RecommendationImpact.wishlisted 기반)
     * @param watched             봤어요 여부 (RecommendationImpact.watched 기반)
     */
    public record RecommendationHistoryResponse(

            /** 추천 로그 고유 ID — 찜/봤어요 토글 API의 경로 파라미터로 사용 */
            Long recommendationLogId,

            /** 추천된 영화 ID */
            String movieId,

            /** 영화 한국어 제목 */
            String title,

            /** TMDB 포스터 이미지 경로 (없으면 null) */
            String posterPath,

            /** 장르 목록 JSON 문자열 (예: ["SF","드라마"], 없으면 null) */
            String genres,

            /** 최종 추천 점수 (0.0~1.0 범위) */
            Float score,

            /** AI가 생성한 추천 이유 텍스트 */
            String reason,

            /** 추천 발생 시각 */
            LocalDateTime recommendedAt,

            /** 찜 여부 (RecommendationImpact.wishlisted, 임팩트 없으면 false) */
            boolean wishlisted,

            /** 봤어요 여부 (RecommendationImpact.watched, 임팩트 없으면 false) */
            boolean watched

    ) {
        /**
         * RecommendationLog 엔티티와 Impact 상태 값으로 응답 DTO를 생성한다.
         *
         * <p>Impact 정보는 RecommendationImpactRepository에서 별도로 조회하여
         * 서비스 레이어에서 이 메서드에 전달한다.
         * Impact 레코드가 없으면 wishlisted=false, watched=false로 처리한다.</p>
         *
         * @param log        추천 로그 엔티티 (movie JOIN FETCH 필수)
         * @param wishlisted 찜 여부 (Impact 없으면 false)
         * @param watched    봤어요 여부 (Impact 없으면 false)
         * @return 추천 이력 응답 DTO
         */
        public static RecommendationHistoryResponse from(
                RecommendationLog log,
                boolean wishlisted,
                boolean watched
        ) {
            return new RecommendationHistoryResponse(
                    log.getRecommendationLogId(),
                    log.getMovie().getMovieId(),
                    log.getMovie().getTitle(),
                    log.getMovie().getPosterPath(),
                    log.getMovie().getGenres(),
                    log.getScore(),
                    log.getReason(),
                    log.getCreatedAt(),     // BaseAuditEntity.createdAt → 추천 발생 시각
                    wishlisted,
                    watched
            );
        }
    }

    /**
     * 찜 토글 응답 DTO.
     *
     * <p>{@code POST /api/v1/recommendations/{recommendationLogId}/wishlist} 응답에 사용된다.
     * 토글 후 현재 찜 상태를 반환한다.</p>
     *
     * @param wishlisted 토글 후 찜 여부 (true: 찜 추가됨, false: 찜 취소됨)
     */
    public record WishlistToggleResponse(

            /** 토글 후 찜 여부 */
            boolean wishlisted

    ) {}

    /**
     * 봤어요 토글 응답 DTO.
     *
     * <p>{@code POST /api/v1/recommendations/{recommendationLogId}/watched} 응답에 사용된다.
     * 토글 후 현재 봤어요 상태를 반환한다.</p>
     *
     * @param watched 토글 후 봤어요 여부 (true: 봤어요 추가됨, false: 봤어요 취소됨)
     */
    public record WatchedToggleResponse(

            /** 토글 후 봤어요 여부 */
            boolean watched

    ) {}
}
