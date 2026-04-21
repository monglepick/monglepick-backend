package com.monglepick.monglepickbackend.domain.search.dto;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 이상형 월드컵 시작 요청 DTO.
 *
 * <p>클라이언트가 월드컵 세션을 시작할 때 전송하는 요청 바디이다.</p>
 *
 * <h3>후보 산정 정책</h3>
 * <ul>
 *   <li>{@code sourceType=CATEGORY} 이면 {@code categoryId} 기준으로 활성 후보 풀에서 선택한다.</li>
 *   <li>{@code sourceType=GENRE} 이면 {@code selectedGenres}를 모두 만족하는 영화 중
 *       {@code vote_count >= 100} 조건을 충족하는 후보에서 선택한다.</li>
 * </ul>
 *
 * @param sourceType     시작 방식 (CATEGORY / GENRE)
 * @param categoryId     월드컵 카테고리 ID (CATEGORY일 때 필수)
 * @param selectedGenres 사용자가 고른 장르 목록 (GENRE일 때 필수, 다중 선택)
 * @param roundSize      토너먼트 크기 (8/16/32/64 중 하나)
 */
public record WorldcupStartRequest(
        @NotNull(message = "월드컵 시작 방식은 필수입니다")
        WorldcupSourceType sourceType,

        Long categoryId,

        List<String> selectedGenres,

        @NotNull(message = "라운드 크기는 필수입니다")
        @Min(value = 2, message = "라운드 크기는 2 이상이어야 합니다")
        Integer roundSize
) {}
