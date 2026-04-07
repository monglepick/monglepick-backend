package com.monglepick.monglepickbackend.domain.roadmap.dto;

import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;

import java.util.List;

/**
 * 도장깨기 코스 관련 응답 DTO 모음.
 *
 * <p>목록 조회, 상세 조회, 코스 시작 응답을 각각의 record로 분리한다.
 * 목록용에는 영화 ID 목록을 포함하지 않아 응답 크기를 줄이고,
 * 상세용에는 영화 ID 목록과 시작 여부, 진행률을 함께 반환한다.</p>
 *
 * <h3>record 구성</h3>
 * <ul>
 *   <li>{@link CourseListResponse}   — GET /courses 목록 조회용 (movieIds 미포함)</li>
 *   <li>{@link CourseDetailResponse} — GET /courses/{id} 상세 조회용 (movieIds 포함)</li>
 *   <li>{@link CourseStartResponse}  — POST /courses/{id}/start 시작 응답용</li>
 * </ul>
 */
public class CourseResponse {

    /** 직접 인스턴스화 금지 — 중첩 record 전용 네임스페이스 클래스 */
    private CourseResponse() {}

    // ─────────────────────────────────────────────────────────────────
    // 코스 목록 조회 응답 (movieIds 미포함)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 코스 목록 조회 응답 DTO.
     *
     * <p>GET /api/v1/roadmap/courses 응답에 사용한다.
     * 목록에서는 영화 ID 배열을 제외하여 응답 크기를 최소화한다.
     * 로그인 사용자의 진행률을 함께 반환하여 프론트엔드에서
     * 프로그레스바를 렌더링할 수 있다.</p>
     *
     * @param roadmapCourseId DB PK (BIGINT)
     * @param courseId        코스 슬러그 (예: "nolan-filmography")
     * @param title           코스 제목
     * @param description     코스 설명
     * @param theme           테마 (예: "감독별", "장르별")
     * @param movieCount      코스 내 영화 수
     * @param difficulty      난이도 (beginner / intermediate / advanced)
     * @param quizEnabled     퀴즈 활성화 여부
     * @param progressPercent 현재 사용자의 진행률 % (비로그인 또는 미시작 시 0.0)
     */
    public record CourseListResponse(
            Long roadmapCourseId,
            String courseId,
            String title,
            String description,
            String theme,
            int movieCount,
            String difficulty,
            boolean quizEnabled,
            double progressPercent
    ) {
        /**
         * RoadmapCourse 엔티티로부터 목록 응답 DTO를 생성한다.
         *
         * @param course          코스 엔티티
         * @param progressPercent 현재 사용자 진행률 (미시작/비로그인 시 0.0)
         * @return CourseListResponse
         */
        public static CourseListResponse from(RoadmapCourse course, double progressPercent) {
            return new CourseListResponse(
                    course.getRoadmapCourseId(),
                    course.getCourseId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getTheme(),
                    course.getMovieCount() != null ? course.getMovieCount() : 0,
                    /* Difficulty enum → 소문자 문자열 변환 (beginner/intermediate/advanced) */
                    course.getDifficulty() != null ? course.getDifficulty().name() : "beginner",
                    course.getQuizEnabled() != null && course.getQuizEnabled(),
                    progressPercent
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 코스 상세 조회 응답 (movieIds 포함)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 코스 상세 조회 응답 DTO.
     *
     * <p>GET /api/v1/roadmap/courses/{courseId} 응답에 사용한다.
     * 영화 ID 목록(movieIds)과 코스 시작 여부(started), 진행률(progressPercent)을 함께 반환한다.</p>
     *
     * <p>{@code id} 필드는 프론트엔드의 {@code course.id} 참조와의 호환성을 위해
     * {@code courseId}의 별칭(alias)으로 제공된다.</p>
     *
     * @param roadmapCourseId DB PK (BIGINT)
     * @param courseId        코스 슬러그 (예: "nolan-filmography")
     * @param id              courseId와 동일 — 프론트엔드 호환용 alias
     * @param title           코스 제목
     * @param description     코스 설명
     * @param theme           테마
     * @param movieCount      코스 내 영화 수
     * @param movieIds        코스에 포함된 영화 ID 목록 (순서 = 시청 권장 순서)
     * @param difficulty      난이도 문자열
     * @param quizEnabled     퀴즈 활성화 여부
     * @param started         현재 사용자가 이미 코스를 시작했는지 여부
     * @param progressPercent 현재 사용자의 진행률 % (미시작 시 0.0)
     */
    public record CourseDetailResponse(
            Long roadmapCourseId,
            String courseId,
            String id,
            String title,
            String description,
            String theme,
            int movieCount,
            List<String> movieIds,
            String difficulty,
            boolean quizEnabled,
            boolean started,
            double progressPercent
    ) {
        /**
         * RoadmapCourse 엔티티와 진행 정보로부터 상세 응답 DTO를 생성한다.
         *
         * @param course          코스 엔티티
         * @param movieIds        파싱된 영화 ID 목록
         * @param started         코스 시작 여부
         * @param progressPercent 현재 진행률 %
         * @return CourseDetailResponse
         */
        public static CourseDetailResponse from(RoadmapCourse course,
                                                List<String> movieIds,
                                                boolean started,
                                                double progressPercent) {
            return new CourseDetailResponse(
                    course.getRoadmapCourseId(),
                    course.getCourseId(),
                    /* id = courseId alias (프론트엔드 course.id 호환) */
                    course.getCourseId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getTheme(),
                    course.getMovieCount() != null ? course.getMovieCount() : 0,
                    movieIds,
                    course.getDifficulty() != null ? course.getDifficulty().name() : "beginner",
                    course.getQuizEnabled() != null && course.getQuizEnabled(),
                    started,
                    progressPercent
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 코스 시작 응답
    // ─────────────────────────────────────────────────────────────────

    /**
     * 코스 시작 응답 DTO.
     *
     * <p>POST /api/v1/roadmap/courses/{courseId}/start 응답에 사용한다.
     * 이미 시작한 코스의 경우에도 동일 응답을 반환하여 멱등성을 보장한다.</p>
     *
     * @param courseId 시작(또는 이미 시작된) 코스 슬러그
     * @param status   진행 상태 문자열 ("IN_PROGRESS" 고정)
     */
    public record CourseStartResponse(
            String courseId,
            String status
    ) {}
}
