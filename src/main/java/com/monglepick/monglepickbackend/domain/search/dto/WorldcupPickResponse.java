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
 * @param sessionId      진행 중인 세션 ID
 * @param currentRound   현재 라운드 번호 (라운드 전환 후의 값)
 * @param roundCompleted 현재 라운드의 모든 매치가 완료됐는지 여부
 * @param gameCompleted  토너먼트 전체 완료 여부 (최종 우승 영화 결정)
 * @param winnerMovieId  최종 우승 영화 ID (gameCompleted=true일 때만 설정)
 * @param nextMatches    다음 라운드 매치 목록 (roundCompleted=true이고 gameCompleted=false일 때)
 */
public record WorldcupPickResponse(
        Long sessionId,
        int currentRound,
        boolean roundCompleted,
        boolean gameCompleted,
        String winnerMovieId,
        List<WorldcupMatchDto> nextMatches
) {}
