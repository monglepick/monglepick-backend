package com.monglepick.monglepickbackend.domain.watchhistory.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 시청 기록 추가 요청 DTO.
 *
 * <p>POST /api/v1/watch-history 요청 바디에 사용됩니다.
 * movieId는 필수, watchedAt과 rating은 선택 필드입니다.</p>
 *
 * <h3>필드 설명</h3>
 * <ul>
 *   <li>{@code movieId} — 시청한 영화 ID (movies 테이블 PK, VARCHAR(50), 필수)</li>
 *   <li>{@code watchedAt} — 시청 일시. null이면 서버 수신 시각(LocalDateTime.now())으로 대체</li>
 *   <li>{@code rating} — 사용자 평점 (1.0 ~ 5.0, null이면 미부여로 처리)</li>
 * </ul>
 *
 * @param movieId   시청한 영화 ID (필수)
 * @param watchedAt 시청 일시 (nullable, 미입력 시 현재 시각)
 * @param rating    사용자 평점 1.0 ~ 5.0 (nullable)
 */
public record WatchHistoryRequest(

        /** 시청한 영화 ID — movies 테이블의 movie_id 컬럼과 매핑 (VARCHAR(50)) */
        @NotBlank(message = "영화 ID는 필수입니다")
        @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다")
        String movieId,

        /**
         * 시청 일시 — null 허용.
         * 클라이언트가 전달하지 않으면 서비스 계층에서 LocalDateTime.now()로 채웁니다.
         */
        LocalDateTime watchedAt,

        /**
         * 사용자 평점 — null 허용 (평점 미부여).
         * 범위: 1.0 이상 5.0 이하. 0.5 단위를 허용하므로 BigDecimal 대신 Double 사용.
         */
        @DecimalMin(value = "1.0", message = "평점은 1.0 이상이어야 합니다")
        @DecimalMax(value = "5.0", message = "평점은 5.0 이하여야 합니다")
        Double rating,

        /**
         * 시청 경로 (Phase 2) — null 허용.
         * 예: "recommendation", "search", "wishlist", "home", "match", "direct"
         */
        @Size(max = 50, message = "시청 경로는 50자 이하여야 합니다")
        String watchSource,

        /**
         * 시청 시간 (초 단위, Phase 2) — null이면 미측정.
         */
        Integer watchDurationSeconds,

        /**
         * 시청 완료 상태 (Phase 2) — null 허용.
         * "COMPLETED", "ABANDONED", "IN_PROGRESS"
         */
        @Size(max = 30, message = "완료 상태는 30자 이하여야 합니다")
        String completionStatus
) {
}
