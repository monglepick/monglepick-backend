package com.monglepick.monglepickbackend.domain.search.dto;

import java.util.List;

/**
 * 이상형 월드컵 시작 응답 DTO.
 *
 * <p>세션 생성 및 첫 라운드 매치 목록을 포함한다.
 * 클라이언트는 이 응답으로 sessionId(또는 gameId)를 저장하고
 * 첫 라운드 매치를 화면에 표시한다.</p>
 *
 * <h3>Frontend 호환 필드 (v2)</h3>
 * <ul>
 *   <li>{@code gameId} — {@code sessionId}의 별칭. Frontend가 {@code data.gameId}로 접근할 수 있다.</li>
 * </ul>
 *
 * @param sessionId    생성된 세션 ID (이후 pick API 호출 시 사용)
 * @param gameId       sessionId 별칭 — Frontend {@code data.gameId} 호환용
 * @param roundSize    총 토너먼트 크기 (예: 16)
 * @param currentRound 현재 라운드 번호 (시작 시 roundSize와 동일)
 * @param matches      현재 라운드의 매치 목록 (roundSize/2 개)
 */
public record WorldcupStartResponse(
        Long sessionId,
        Long gameId,        // sessionId 별칭 — Frontend data.gameId 호환용
        int roundSize,
        int currentRound,
        List<WorldcupMatchDto> matches
) {}
