package com.monglepick.monglepickbackend.domain.search.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 이상형 월드컵 시작 요청 DTO.
 *
 * <p>클라이언트가 월드컵 세션을 시작할 때 전송하는 요청 바디이다.</p>
 *
 * <h3>candidateMovieIds 처리 정책 (v2 — Frontend 호환)</h3>
 * <ul>
 *   <li>{@code candidateMovieIds}가 null 또는 비어있으면 서버가 DB에서 랜덤으로 선택한다.</li>
 *   <li>{@code candidateMovieIds}가 제공되면 해당 목록을 그대로 사용한다
 *       (크기가 roundSize와 일치해야 함).</li>
 * </ul>
 *
 * <p>Frontend의 {@code startWorldcup({ round, genre })} 호출 시
 * candidateMovieIds를 전달하지 않아도 400 오류가 발생하지 않는다.</p>
 *
 * @param genreFilter       장르 필터 (nullable — null이면 전체 장르로 랜덤 선택)
 * @param roundSize         토너먼트 크기 (최소 2 — 실제로는 8/16/32 권장)
 * @param candidateMovieIds 후보 영화 ID 목록 (optional — null/empty이면 서버가 DB에서 선택)
 */
public record WorldcupStartRequest(
        String genreFilter,

        @NotNull(message = "라운드 크기는 필수입니다")
        @Min(value = 2, message = "라운드 크기는 2 이상이어야 합니다")
        Integer roundSize,

        // optional: null 또는 빈 목록이면 서버가 장르 기반 랜덤 선택
        List<String> candidateMovieIds
) {}
