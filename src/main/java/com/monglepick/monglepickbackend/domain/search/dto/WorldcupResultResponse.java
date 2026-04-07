package com.monglepick.monglepickbackend.domain.search.dto;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupResult;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSession;

import java.time.format.DateTimeFormatter;

/**
 * 이상형 월드컵 결과 응답 DTO.
 *
 * <p>GET /api/v1/worldcup/result/{sessionId} 응답으로 반환된다.
 * Frontend가 결과 화면에서 우승 영화 정보를 표시할 때 사용한다.</p>
 *
 * <h3>Frontend 호환</h3>
 * <ul>
 *   <li>{@code gameId} — Frontend {@code result.gameId} 호환용 (sessionId와 동일 값)</li>
 *   <li>{@code winner} — 우승 영화 상세 정보 (title, posterPath, releaseYear 포함)</li>
 *   <li>{@code rankings} — 현재는 빈 배열 반환 (향후 확장 가능)</li>
 * </ul>
 *
 * @param gameId        세션 ID (gameId 별칭으로 Frontend 호환)
 * @param sessionId     세션 ID
 * @param winnerMovieId 우승 영화 ID
 * @param winner        우승 영화 상세 정보 (MovieInfo 중첩 레코드)
 * @param completedAt   완료 시각 (ISO 8601 형식 문자열)
 */
public record WorldcupResultResponse(
        Long gameId,
        Long sessionId,
        String winnerMovieId,
        MovieInfo winner,
        String completedAt
) {

    /** ISO 8601 날짜/시간 포맷터 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 우승 영화 기본 정보 중첩 레코드.
     *
     * <p>Frontend 결과 화면의 winner 객체 구조를 맞춘다.
     * title, posterPath, releaseYear로 포스터와 제목을 표시할 수 있다.</p>
     *
     * @param movieId     영화 ID
     * @param title       영화 제목 (한국어)
     * @param posterPath  TMDB 포스터 경로 (예: /abcdef.jpg)
     * @param releaseYear 개봉 연도
     */
    public record MovieInfo(
            String movieId,
            String title,
            String posterPath,
            Integer releaseYear
    ) {
        /**
         * Movie 엔티티로부터 MovieInfo를 생성한다.
         *
         * @param movie Movie 엔티티
         * @return MovieInfo
         */
        public static MovieInfo from(Movie movie) {
            return new MovieInfo(
                    movie.getMovieId(),
                    movie.getTitle(),
                    movie.getPosterPath(),
                    movie.getReleaseYear()
            );
        }
    }

    /**
     * WorldcupSession + Movie 엔티티로부터 결과 응답을 생성한다.
     *
     * <p>WorldcupResult 엔티티가 없어도 세션 정보만으로 응답을 구성할 수 있다.
     * winner 영화를 조회할 수 없으면 winnerMovieId만 반환하고 winner는 null로 설정한다.</p>
     *
     * @param session      완료된 WorldcupSession 엔티티
     * @param winnerMovie  우승 영화 엔티티 (null 허용 — 조회 실패 시)
     * @return WorldcupResultResponse
     */
    public static WorldcupResultResponse from(WorldcupSession session, Movie winnerMovie) {
        // 우승 영화 정보 구성 (null 안전 처리)
        MovieInfo movieInfo = (winnerMovie != null) ? MovieInfo.from(winnerMovie) : null;

        // 완료 시각 포맷 (completedAt이 null이면 빈 문자열)
        String completedAt = (session.getCompletedAt() != null)
                ? session.getCompletedAt().format(FORMATTER)
                : "";

        return new WorldcupResultResponse(
                session.getSessionId(),  // gameId = sessionId 별칭
                session.getSessionId(),
                session.getWinnerMovieId(),
                movieInfo,
                completedAt
        );
    }
}
