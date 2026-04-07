package com.monglepick.monglepickbackend.domain.search.dto;

import java.util.List;

/**
 * 이상형 월드컵 매치 선택 응답 DTO.
 *
 * <p>매치 선택 후의 세션 상태와 다음 라운드 매치 목록을 포함한다.</p>
 *
 * <h3>응답 해석 가이드</h3>
 * <ul>
 *   <li>{@code gameCompleted=true} — 토너먼트 완료. {@code winnerMovieId}로 최종 우승 영화 표시.</li>
 *   <li>{@code roundCompleted=true, gameCompleted=false} — 현재 라운드 완료, 다음 라운드 진입.
 *       {@code nextMatches}에 다음 라운드 매치 목록이 담겨 있다.</li>
 *   <li>{@code roundCompleted=false} — 현재 라운드 진행 중.
 *       {@code nextMatches}는 비어 있다.</li>
 * </ul>
 *
 * <h3>Frontend 호환 필드 (v2)</h3>
 * <ul>
 *   <li>{@code isFinished}  — {@code gameCompleted}의 별칭. Frontend {@code result.isFinished} 호환용.</li>
 *   <li>{@code finalWinner} — {@code winnerMovieId}의 별칭. Frontend {@code result.finalWinner} 호환용.</li>
 *   <li>{@code nextMatch}   — {@code nextMatches}의 첫 번째 원소. Frontend {@code result.nextMatch} 호환용.
 *       nextMatches가 비어 있으면 null.</li>
 * </ul>
 *
 * @param sessionId      진행 중인 세션 ID
 * @param currentRound   현재 라운드 번호 (라운드 전환 후의 값)
 * @param roundCompleted 현재 라운드의 모든 매치가 완료됐는지 여부
 * @param gameCompleted  토너먼트 전체 완료 여부 (최종 우승 영화 결정)
 * @param isFinished     gameCompleted 별칭 — Frontend {@code result.isFinished} 호환용
 * @param winnerMovieId  최종 우승 영화 ID (gameCompleted=true일 때만 설정)
 * @param finalWinner    winnerMovieId 별칭 — Frontend {@code result.finalWinner} 호환용
 * @param nextMatches    다음 라운드 매치 목록 (roundCompleted=true이고 gameCompleted=false일 때)
 * @param nextMatch      nextMatches의 첫 번째 원소 — Frontend {@code result.nextMatch} 호환용 (없으면 null)
 */
public record WorldcupPickResponse(
        Long sessionId,
        int currentRound,
        boolean roundCompleted,
        boolean gameCompleted,
        boolean isFinished,         // gameCompleted 별칭 — Frontend result.isFinished 호환용
        String winnerMovieId,
        String finalWinner,         // winnerMovieId 별칭 — Frontend result.finalWinner 호환용
        List<WorldcupMatchDto> nextMatches,
        WorldcupMatchDto nextMatch  // nextMatches 첫 번째 원소 — Frontend result.nextMatch 호환용
) {

    /**
     * 게임 미완료 상태 응답을 생성하는 정적 팩토리 메서드.
     *
     * <p>라운드 미완료(현재 라운드 계속 진행) 시 사용한다.
     * alias 필드들을 자동으로 채워준다.</p>
     *
     * @param sessionId    세션 ID
     * @param currentRound 현재 라운드 번호
     * @return 라운드 미완료 응답 DTO
     */
    public static WorldcupPickResponse inProgress(Long sessionId, int currentRound) {
        return new WorldcupPickResponse(
                sessionId,
                currentRound,
                false,          // roundCompleted
                false,          // gameCompleted
                false,          // isFinished (alias)
                null,           // winnerMovieId
                null,           // finalWinner (alias)
                List.of(),      // nextMatches
                null            // nextMatch (alias)
        );
    }

    /**
     * 라운드 완료(다음 라운드 진입) 응답을 생성하는 정적 팩토리 메서드.
     *
     * <p>alias 필드들을 자동으로 채워준다.
     * nextMatch는 nextMatches의 첫 번째 원소로 설정된다.</p>
     *
     * @param sessionId   세션 ID
     * @param nextRound   다음 라운드 번호
     * @param nextMatches 다음 라운드 매치 목록
     * @return 라운드 완료 응답 DTO
     */
    public static WorldcupPickResponse roundComplete(Long sessionId, int nextRound,
                                                     List<WorldcupMatchDto> nextMatches) {
        // nextMatches가 비어 있지 않으면 첫 번째 원소를 nextMatch로 설정
        WorldcupMatchDto firstMatch = (nextMatches == null || nextMatches.isEmpty())
                ? null
                : nextMatches.get(0);

        return new WorldcupPickResponse(
                sessionId,
                nextRound,
                true,           // roundCompleted
                false,          // gameCompleted
                false,          // isFinished (alias)
                null,           // winnerMovieId
                null,           // finalWinner (alias)
                nextMatches,
                firstMatch      // nextMatch (alias — 첫 번째 원소)
        );
    }

    /**
     * 게임 완료 응답을 생성하는 정적 팩토리 메서드.
     *
     * <p>alias 필드들을 자동으로 채워준다.
     * isFinished=true, finalWinner=winnerMovieId로 설정된다.</p>
     *
     * @param sessionId     세션 ID
     * @param winnerMovieId 최종 우승 영화 ID
     * @return 게임 완료 응답 DTO
     */
    public static WorldcupPickResponse gameComplete(Long sessionId, String winnerMovieId) {
        return new WorldcupPickResponse(
                sessionId,
                1,              // currentRound — 결승 완료
                true,           // roundCompleted
                true,           // gameCompleted
                true,           // isFinished (alias)
                winnerMovieId,
                winnerMovieId,  // finalWinner (alias)
                List.of(),      // nextMatches
                null            // nextMatch (alias)
        );
    }
}
