package com.monglepick.monglepickbackend.domain.userwatchhistory.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 시청 기록 추가 요청 DTO.
 *
 * <p>POST /api/v1/watch-history 요청 바디에 사용된다.
 * {@code movieId} 만 필수이며, 나머지 5 필드는 모두 선택이다.</p>
 *
 * <h3>필드 가이드</h3>
 * <ul>
 *   <li>{@code movieId} — movies.movie_id VARCHAR(50). 필수.</li>
 *   <li>{@code watchedAt} — 시청 일시. null 이면 서비스 계층에서 현재 시각으로 채운다.</li>
 *   <li>{@code rating} — 1.0 ~ 5.0. null 이면 평점 미부여. 정식 평가는 별도 reviews 도메인에서 진행.</li>
 *   <li>{@code watchSource} — recommendation/search/wishlist/home/match/direct 등. null 허용.</li>
 *   <li>{@code watchDurationSeconds} — 실제 시청 초. 클라이언트 측정값. null 허용.</li>
 *   <li>{@code completionStatus} — COMPLETED/ABANDONED/IN_PROGRESS. null 허용.</li>
 * </ul>
 *
 * @param movieId              시청한 영화 ID (필수)
 * @param watchedAt            시청 일시 (nullable, 미입력 시 서버 시각)
 * @param rating               사용자 평점 1.0 ~ 5.0 (nullable)
 * @param watchSource          시청 경로 (nullable)
 * @param watchDurationSeconds 시청 시간 초 (nullable)
 * @param completionStatus     시청 완료 상태 (nullable)
 */
public record UserWatchHistoryRequest(

        @NotBlank(message = "영화 ID는 필수입니다")
        @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다")
        String movieId,

        LocalDateTime watchedAt,

        @DecimalMin(value = "1.0", message = "평점은 1.0 이상이어야 합니다")
        @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다")
        Double rating,

        @Size(max = 50, message = "시청 경로는 50자 이하여야 합니다")
        String watchSource,

        Integer watchDurationSeconds,

        @Size(max = 30, message = "완료 상태는 30자 이하여야 합니다")
        String completionStatus
) {
}
