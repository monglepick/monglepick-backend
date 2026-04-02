package com.monglepick.monglepickbackend.domain.recommendation.dto;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 피드백 제출 요청 DTO.
 *
 * <p>사용자가 AI 추천 결과에 대해 피드백을 제출할 때 사용하는 요청 바디이다.
 * feedbackType은 필수이며, comment는 선택적으로 입력할 수 있다.</p>
 *
 * <h3>사용 예시 (JSON)</h3>
 * <pre>{@code
 * {
 *   "feedbackType": "like",
 *   "comment": "추천이 정확해서 좋았어요!"
 * }
 * }</pre>
 *
 * @param feedbackType 피드백 유형 (like/dislike/watched/not_interested, 필수)
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
         * 피드백 코멘트 (선택).
         * 사용자가 자유롭게 의견을 남길 수 있는 텍스트 필드.
         */
        String comment

) {}
