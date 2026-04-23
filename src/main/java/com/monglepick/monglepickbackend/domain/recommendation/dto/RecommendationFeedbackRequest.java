package com.monglepick.monglepickbackend.domain.recommendation.dto;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 피드백 제출 요청 DTO.
 *
 * <p>사용자가 AI 추천 결과에 대해 피드백을 제출할 때 사용하는 요청 바디이다.
 * feedbackType 은 필수, comment 와 rating 은 선택이다.</p>
 *
 * <h3>사용 예시 (JSON)</h3>
 * <pre>{@code
 * {
 *   "feedbackType": "like",
 *   "rating": 5,
 *   "comment": "추천이 정확해서 좋았어요!"
 * }
 * }</pre>
 *
 * @param feedbackType 피드백 유형 (like/dislike/watched/not_interested, 필수)
 * @param rating       별점 1~5 (선택). 마이픽 추천 카드 별점 UI 를 위한 원시값.
 * @param comment      피드백 코멘트 (선택, 자유 텍스트)
 */
public record RecommendationFeedbackRequest(

        /**
         * 피드백 유형.
         * like(좋아요), dislike(싫어요), watched(시청함), not_interested(관심없음) 중 하나.
         */
        @NotNull(message = "피드백 유형은 필수입니다")
        RecommendationFeedback.FeedbackType feedbackType,

        /**
         * 별점 (선택, 1~5 정수).
         *
         * <p>QA #172 (2026-04-23) 근본 해결: Frontend 에서 별점을 feedbackType 으로 축약하지 않고
         * 원시값 그대로 전달할 수 있도록 추가. null 이면 별점을 남기지 않는 케이스
         * (like/dislike 버튼 클릭 등). 값이 있으면 1~5 범위를 강제 검증한다.</p>
         */
        @Min(value = 1, message = "별점은 1 이상이어야 합니다")
        @Max(value = 5, message = "별점은 5 이하여야 합니다")
        Integer rating,

        /**
         * 피드백 코멘트 (선택).
         * 사용자가 자유롭게 의견을 남길 수 있는 텍스트 필드.
         */
        String comment

) {}
