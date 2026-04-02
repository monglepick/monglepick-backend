package com.monglepick.monglepickbackend.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 이상형 월드컵 매치 선택 요청 DTO.
 *
 * <p>사용자가 특정 매치에서 승자를 선택할 때 전송하는 요청 바디이다.
 * winnerMovieId는 반드시 해당 매치의 movieAId 또는 movieBId 중 하나여야 한다.
 * 서비스 레이어에서 유효성을 검증한다.</p>
 *
 * @param sessionId     소속 세션 ID
 * @param matchId       선택할 매치 ID
 * @param winnerMovieId 사용자가 선택한 승자 영화 ID
 */
public record WorldcupPickRequest(
        @NotNull(message = "세션 ID는 필수입니다")
        Long sessionId,

        @NotNull(message = "매치 ID는 필수입니다")
        Long matchId,

        @NotBlank(message = "승자 영화 ID는 필수입니다")
        String winnerMovieId
) {}
