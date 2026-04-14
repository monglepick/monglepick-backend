package com.monglepick.monglepickbackend.domain.roadmap.dto;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 코스 진행 현황 응답 DTO.
 *
 * <p>영화 인증 처리 후 또는 진행 현황 조회 시 클라이언트에 반환하는 DTO이다.
 * progressPercent를 통해 프로그레스바를 렌더링하고,
 * completed=true 시 완주 축하 화면을 표시한다.</p>
 *
 * @param courseId        코스 ID (slug 형태, 예: "nolan-filmography")
 * @param totalMovies     코스 내 총 영화 수
 * @param verifiedMovies  인증 완료한 영화 수
 * @param progressPercent 진행률 % (0.00~100.00)
 * @param status          진행 상태 ({@link CourseProgressStatus})
 * @param completed       완주 여부 (status=COMPLETED과 동일하나 편의상 제공)
 * @param completedAt     완주 시각 (completed=false이면 null)
 * @param deadlineAt      완주 데드라인 시각 (null이면 데드라인 없음)
 */
public record CourseProgressResponse(
        String courseId,
        int totalMovies,
        int verifiedMovies,
        BigDecimal progressPercent,
        CourseProgressStatus status,
        boolean completed,
        LocalDateTime completedAt,
        LocalDateTime deadlineAt
) {
    /**
     * UserCourseProgress 엔티티로부터 DTO를 생성한다.
     *
     * @param progress UserCourseProgress 엔티티
     * @return CourseProgressResponse
     */
    public static CourseProgressResponse from(UserCourseProgress progress) {
        return new CourseProgressResponse(
                progress.getCourseId(),
                progress.getTotalMovies(),
                progress.getVerifiedMovies(),
                progress.getProgressPercent(),
                progress.getStatus(),
                progress.getStatus() == CourseProgressStatus.COMPLETED,
                progress.getCompletedAt(),
                progress.getDeadlineAt()
        );
    }
}
