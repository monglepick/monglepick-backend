package com.monglepick.monglepickbackend.domain.search.dto;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupMatch;

/**
 * 이상형 월드컵 매치 정보 DTO.
 *
 * <p>클라이언트에게 전달할 매치 단건 정보를 담는다.
 * winnerMovieId가 null이면 아직 선택하지 않은 매치이다.</p>
 *
 * @param matchId       매치 고유 ID
 * @param roundNumber   라운드 번호 (16강=16, 8강=8, ...)
 * @param matchOrder    라운드 내 순서 (0-based)
 * @param movieAId      대결 영화 A ID
 * @param movieBId      대결 영화 B ID
 * @param winnerMovieId 선택된 승자 영화 ID (선택 전 null)
 */
public record WorldcupMatchDto(
        Long matchId,
        int roundNumber,
        int matchOrder,
        String movieAId,
        String movieBId,
        String winnerMovieId
) {
    /**
     * WorldcupMatch 엔티티로부터 DTO를 생성한다.
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
                match.getWinnerMovieId()
        );
    }
}
