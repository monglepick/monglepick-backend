package com.monglepick.monglepickbackend.domain.search.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 이상형 월드컵 시작 요청 DTO.
 *
 * <p>클라이언트가 월드컵 세션을 시작할 때 전송하는 요청 바디이다.
 * candidateMovieIds 목록은 roundSize와 동일한 크기여야 한다.</p>
 *
 * @param genreFilter       장르 필터 (nullable — null이면 전체 장르)
 * @param roundSize         토너먼트 크기 (최소 2 — 실제로는 16/32/64 권장)
 * @param candidateMovieIds 후보 영화 ID 목록 (roundSize 크기)
 */
public record WorldcupStartRequest(
        String genreFilter,

        @NotNull(message = "라운드 크기는 필수입니다")
        @Min(value = 2, message = "라운드 크기는 2 이상이어야 합니다")
        Integer roundSize,

        @NotEmpty(message = "후보 영화 목록은 비어있을 수 없습니다")
        List<String> candidateMovieIds
) {}
