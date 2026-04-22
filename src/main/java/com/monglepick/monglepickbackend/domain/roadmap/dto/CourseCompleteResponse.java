package com.monglepick.monglepickbackend.domain.roadmap.dto;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 도장깨기 영화 완료 처리 응답 DTO.
 *
 * <p>기존 CourseProgressResponse 필드에 AI 리뷰 검증 결과를 추가한다.
 * 프론트엔드는 reviewStatus 값으로 "인증완료" / "재인증 필요" UI 분기를 결정한다.</p>
 *
 * @param courseId          코스 슬러그
 * @param totalMovies       코스 내 총 영화 수
 * @param verifiedMovies    현재까지 인증 완료한 영화 수
 * @param progressPercent   진행률 % (0.00 ~ 100.00)
 * @param status            코스 진행 상태 (IN_PROGRESS / COMPLETED)
 * @param rewardGranted     완주 리워드 지급 여부
 * @param completedAt       코스 완주 시각 (nullable)
 * @param deadlineAt        코스 완주 데드라인 (nullable)
 * @param reviewStatus      AI 판정 결과 (AUTO_VERIFIED / NEEDS_REVIEW / AUTO_REJECTED / PENDING)
 * @param rationale         AI 판정 근거 한 줄 요약 (nullable)
 * @param similarityScore   영화 줄거리 ↔ 리뷰 유사도 (0.0~1.0, nullable)
 * @param agentAvailable    AI 에이전트 호출 가능 여부 (false이면 PENDING 상태로만 등록)
 */
public record CourseCompleteResponse(
        String courseId,
        int totalMovies,
        int verifiedMovies,
        BigDecimal progressPercent,
        CourseProgressStatus status,
        boolean rewardGranted,
        LocalDateTime completedAt,
        LocalDateTime deadlineAt,
        String reviewStatus,
        String rationale,
        Float similarityScore,
        boolean agentAvailable
) {
    public static CourseCompleteResponse from(UserCourseProgress progress,
                                               String reviewStatus,
                                               String rationale,
                                               Float similarityScore,
                                               boolean agentAvailable) {
        return new CourseCompleteResponse(
                progress.getCourseId(),
                progress.getTotalMovies(),
                progress.getVerifiedMovies(),
                progress.getProgressPercent(),
                progress.getStatus(),
                progress.isRewardGranted(),
                progress.getCompletedAt(),
                progress.getDeadlineAt(),
                reviewStatus,
                rationale,
                similarityScore,
                agentAvailable
        );
    }
}
