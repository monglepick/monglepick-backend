package com.monglepick.monglepickbackend.domain.recommendation.dto;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;

/**
 * 추천 피드백 응답 DTO.
 *
 * <p>피드백 제출(POST) 결과를 클라이언트에 반환하는 응답 바디이다.
 * 저장된 피드백의 ID, 유형, 코멘트를 포함한다.</p>
 *
 * <h3>응답 예시 (JSON)</h3>
 * <pre>{@code
 * {
 *   "feedbackId": 42,
 *   "feedbackType": "like",
 *   "comment": "추천이 정확해서 좋았어요!"
 * }
 * }</pre>
 *
 * @param feedbackId   저장된 피드백의 고유 ID
 * @param feedbackType 피드백 유형 문자열 (like/dislike/watched/not_interested)
 * @param comment      피드백 코멘트 (없으면 null)
 */
public record RecommendationFeedbackResponse(

        /** 저장된 피드백 고유 ID (recommendation_feedback_id) */
        Long feedbackId,

        /** 피드백 유형 문자열 (EnumType.STRING 매핑 기준: like/dislike/watched/not_interested) */
        String feedbackType,

        /**
         * 별점 (1~5, null 가능).
         * QA #172 (2026-04-23): Frontend 별점 UI 복원 및 재방문 시 표시를 위해 응답에 포함.
         */
        Integer rating,

        /** 피드백 코멘트 (선택 입력, 없으면 null) */
        String comment

) {
    /**
     * RecommendationFeedback 엔티티로부터 응답 DTO를 생성한다.
     *
     * <p>FeedbackType enum을 소문자 문자열로 변환하여 클라이언트에 반환한다.
     * EnumType.STRING으로 저장된 값과 동일한 형태(like, dislike 등)로 직렬화된다.</p>
     *
     * @param entity 저장된 추천 피드백 엔티티
     * @return 응답 DTO
     */
    public static RecommendationFeedbackResponse from(RecommendationFeedback entity) {
        return new RecommendationFeedbackResponse(
                entity.getRecommendationFeedbackId(),
                entity.getFeedbackType().name(),   // enum → 소문자 문자열 (like, dislike 등)
                entity.getRating(),
                entity.getComment()
        );
    }
}
