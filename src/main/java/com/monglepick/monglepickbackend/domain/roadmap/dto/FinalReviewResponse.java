package com.monglepick.monglepickbackend.domain.roadmap.dto;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseFinalMovie;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;

import java.time.LocalDateTime;

/**
 * 도장깨기 최종 감상평 응답 DTO.
 *
 * <p>감상평 제출(POST) 및 조회(GET) 응답에서 공통으로 사용된다.
 * 코스 진행 상태(courseStatus)를 함께 반환하여 프론트엔드가
 * COMPLETED 탭 이동 여부를 결정할 수 있도록 한다.</p>
 *
 * @param courseId        코스 슬러그
 * @param userId          작성자 사용자 ID
 * @param finalReviewText 최종 감상평 본문
 * @param isCompleted     감상평 제출 완료 여부
 * @param completeAt      감상평 완료 시각 (제출 전: null)
 * @param courseStatus    코스 진행 상태 (FINAL_REVIEW_PENDING → COMPLETED)
 * @param rewardGranted   완주 리워드 지급 여부
 * @param completedAt     코스 완주 시각 (완료 전: null)
 */
public record FinalReviewResponse(
        String courseId,
        String userId,
        String finalReviewText,
        boolean isCompleted,
        LocalDateTime completeAt,
        CourseProgressStatus courseStatus,
        boolean rewardGranted,
        LocalDateTime completedAt
) {
    public static FinalReviewResponse from(CourseFinalMovie finalReview, UserCourseProgress progress) {
        return new FinalReviewResponse(
                finalReview.getCourseId(),
                finalReview.getUserId(),
                finalReview.getFinalReviewText(),
                finalReview.isCompleted(),
                finalReview.getCompleteAt(),
                progress.getStatus(),
                progress.isRewardGranted(),
                progress.getCompletedAt()
        );
    }

    /** 감상평이 없는 경우 진행 상태만 반환 (GET 조회 시 미작성 상태) */
    public static FinalReviewResponse notSubmitted(String courseId, String userId, UserCourseProgress progress) {
        return new FinalReviewResponse(
                courseId,
                userId,
                null,
                false,
                null,
                progress.getStatus(),
                progress.isRewardGranted(),
                progress.getCompletedAt()
        );
    }
}
