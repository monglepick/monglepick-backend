package com.monglepick.monglepickbackend.domain.search.dto;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupMatch;

/**
 * 이상형 월드컵 매치 정보 DTO.
 *
 * <p>클라이언트에게 전달할 매치 단건 정보를 담는다.
 * winnerMovieId가 null이면 아직 선택하지 않은 매치이다.</p>
 *
 * <h3>Frontend 호환 필드 (v2)</h3>
 * <ul>
 *   <li>{@code movie1Id} — {@code movieAId}의 별칭. Frontend가 {@code match.movie1Id}로 접근할 수 있다.</li>
 *   <li>{@code movie2Id} — {@code movieBId}의 별칭. Frontend가 {@code match.movie2Id}로 접근할 수 있다.</li>
 * </ul>
 *
 * @param matchId       매치 고유 ID
 * @param roundNumber   라운드 번호 (16강=16, 8강=8, ...)
 * @param matchOrder    라운드 내 순서 (0-based)
 * @param movieAId      대결 영화 A ID
 * @param movieBId      대결 영화 B ID
 * @param movie1Id      movieAId 별칭 — Frontend {@code match.movie1Id} 호환용
 * @param movie2Id      movieBId 별칭 — Frontend {@code match.movie2Id} 호환용
 * @param winnerMovieId 선택된 승자 영화 ID (선택 전 null)
 */
public record WorldcupMatchDto(
        Long matchId,
        int roundNumber,
        int matchOrder,
        String movieAId,
        String movieBId,
        String movie1Id,  // movieAId 별칭 — Frontend match.movie1Id 호환용
        String movie2Id,  // movieBId 별칭 — Frontend match.movie2Id 호환용
        String winnerMovieId
) {
    /**
     * WorldcupMatch 엔티티로부터 DTO를 생성한다.
     *
     * <p>movie1Id와 movie2Id는 각각 movieAId, movieBId와 동일한 값으로 설정된다.
     * Frontend가 어떤 필드명으로 접근하더라도 동일한 영화 ID를 얻을 수 있다.</p>
     *
     * @param match WorldcupMatch 엔티티
     * @return WorldcupMatchDto
     */
    public static WorldcupMatchDto from(WorldcupMatch match) {
        return new WorldcupMatchDto(
                match.getMatchId(),
                match.getRoundNumber(),
                match.getMatchOrder(),
                match.getMovieAId(),
                match.getMovieBId(),
                match.getMovieAId(),  // movie1Id = movieAId 별칭
                match.getMovieBId(),  // movie2Id = movieBId 별칭
                match.getWinnerMovieId()
        );
    }
}
