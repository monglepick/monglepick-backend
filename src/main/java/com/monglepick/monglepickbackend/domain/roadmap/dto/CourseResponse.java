package com.monglepick.monglepickbackend.domain.roadmap.dto;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;

import java.time.LocalDateTime;
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
     * @param id              courseId와 동일 — 프론트엔드 course.id 호환용 alias
     * @param title           코스 제목
     * @param description     코스 설명
     * @param theme           테마 (예: "감독별", "장르별")
     * @param movieCount      코스 내 영화 수
     * @param difficulty      난이도 (beginner / intermediate / advanced)
     * @param quizEnabled     퀴즈 활성화 여부
     * @param progressPercent 현재 사용자의 진행률 % (비로그인 또는 미시작 시 0.0)
     * @param started         현재 사용자가 코스를 시작했는지 여부 (progressPercent > 0 이면 true)
     * @param deadlineDays    코스 완주 데드라인 (일 수, null이면 제한 없음)
     */
    public record CourseListResponse(
            Long roadmapCourseId,
            String courseId,
            String id,
            String title,
            String description,
            String theme,
            int movieCount,
            String difficulty,
            boolean quizEnabled,
            double progressPercent,
            boolean started,
            Integer deadlineDays
    ) {
        /**
         * RoadmapCourse 엔티티로부터 목록 응답 DTO를 생성한다.
         *
         * @param course          코스 엔티티
         * @param progressPercent 현재 사용자 진행률 (미시작/비로그인 시 0.0)
         * @return CourseListResponse
         */
        public static CourseListResponse from(RoadmapCourse course, double progressPercent, boolean started) {
            return new CourseListResponse(
                    course.getRoadmapCourseId(),
                    course.getCourseId(),
                    /* id = courseId alias (프론트엔드 course.id 호환) */
                    course.getCourseId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getTheme(),
                    course.getMovieCount() != null ? course.getMovieCount() : 0,
                    /* Difficulty enum → 소문자 문자열 변환 (beginner/intermediate/advanced) */
                    course.getDifficulty() != null ? course.getDifficulty().name() : "beginner",
                    course.getQuizEnabled() != null && course.getQuizEnabled(),
                    progressPercent,
                    started,
                    course.getDeadlineDays()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 코스 내 영화 정보 (상세 조회 전용)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 코스 상세 조회에서 반환하는 영화 정보 DTO.
     *
     * @param movieId     영화 고유 ID
     * @param title       한국어 제목
     * @param posterPath  TMDB 포스터 경로 (예: /abcdef.jpg)
     * @param releaseYear 개봉 연도
     * @param director    감독명
     * @param rating      평균 평점
     */
    public record MovieInfo(
            String movieId,
            String title,
            String posterPath,
            Integer releaseYear,
            String director,
            Double rating
    ) {
        public static MovieInfo from(Movie movie) {
            return new MovieInfo(
                    movie.getMovieId(),
                    movie.getTitle(),
                    movie.getPosterPath(),
                    movie.getReleaseYear(),
                    movie.getDirector(),
                    movie.getRating()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 코스 상세 조회 응답 (movies 포함)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 코스 상세 조회 응답 DTO.
     *
     * <p>GET /api/v1/roadmap/courses/{courseId} 응답에 사용한다.
     * 영화 상세 목록(movies), 코스 시작 여부(started), 진행률(progressPercent),
     * 완료된 영화 ID 목록(completedMovieIds)을 함께 반환한다.</p>
     *
     * <p>{@code id} 필드는 프론트엔드의 {@code course.id} 참조와의 호환성을 위해
     * {@code courseId}의 별칭(alias)으로 제공된다.</p>
     *
     * @param roadmapCourseId   DB PK (BIGINT)
     * @param courseId          코스 슬러그 (예: "nolan-filmography")
     * @param id                courseId와 동일 — 프론트엔드 호환용 alias
     * @param title             코스 제목
     * @param description       코스 설명
     * @param theme             테마
     * @param movieCount        코스 내 영화 수
     * @param movies            코스에 포함된 영화 상세 목록 (제목/포스터/감독 포함)
     * @param difficulty        난이도 문자열
     * @param quizEnabled       퀴즈 활성화 여부
     * @param started           현재 사용자가 이미 코스를 시작했는지 여부
     * @param progressPercent   현재 사용자의 진행률 % (미시작 시 0.0)
     * @param completedMovieIds 현재 사용자가 AI 검증 완료(AUTO_VERIFIED/ADMIN_APPROVED) 처리한 영화 ID 목록
     * @param pendingMovieIds   AI 검증 대기(PENDING) 또는 관리자 검토 중(NEEDS_REVIEW) 영화 ID 목록 — "⏳ AI 검증 중" 버튼 표시용
     * @param rejectedMovies    관리자가 반려(ADMIN_REJECTED) 처리한 영화 목록 (movieId + rejectionReason) — "시청 재인증" 버튼 및 사유 표시용
     * @param deadlineDays      코스 완주 데드라인 (일 수, null이면 제한 없음)
     * @param deadlineAt        현재 사용자의 완주 데드라인 시각 (미시작/데드라인 없으면 null)
     */
    public record CourseDetailResponse(
            Long roadmapCourseId,
            String courseId,
            String id,
            String title,
            String description,
            String theme,
            int movieCount,
            List<MovieInfo> movies,
            String difficulty,
            boolean quizEnabled,
            boolean started,
            double progressPercent,
            List<String> completedMovieIds,
            List<String> pendingMovieIds,
            List<RejectedMovieInfo> rejectedMovies,
            Integer deadlineDays,
            LocalDateTime deadlineAt
    ) {
        public static CourseDetailResponse from(RoadmapCourse course,
                                                List<MovieInfo> movies,
                                                boolean started,
                                                double progressPercent,
                                                List<String> completedMovieIds,
                                                List<String> pendingMovieIds,
                                                List<RejectedMovieInfo> rejectedMovies,
                                                LocalDateTime deadlineAt) {
            return new CourseDetailResponse(
                    course.getRoadmapCourseId(),
                    course.getCourseId(),
                    course.getCourseId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getTheme(),
                    course.getMovieCount() != null ? course.getMovieCount() : 0,
                    movies,
                    course.getDifficulty() != null ? course.getDifficulty().name() : "beginner",
                    course.getQuizEnabled() != null && course.getQuizEnabled(),
                    started,
                    progressPercent,
                    completedMovieIds,
                    pendingMovieIds,
                    rejectedMovies,
                    course.getDeadlineDays(),
                    deadlineAt
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 관리자 반려 영화 정보 (상세 조회 전용)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 관리자가 반려한 영화 정보 DTO.
     *
     * @param movieId         반려된 영화 ID
     * @param rejectionReason 관리자/AI 판정 사유 (nullable)
     */
    public record RejectedMovieInfo(
            String movieId,
            String rejectionReason
    ) {}

    // ─────────────────────────────────────────────────────────────────
    // 코스 시작 응답
    // ─────────────────────────────────────────────────────────────────

    /**
     * 코스 시작 응답 DTO.
     *
     * <p>POST /api/v1/roadmap/courses/{courseId}/start 응답에 사용한다.
     * 이미 시작한 코스의 경우에도 동일 응답을 반환하여 멱등성을 보장한다.</p>
     *
     * @param courseId   시작(또는 이미 시작된) 코스 슬러그
     * @param status     진행 상태 문자열 ("IN_PROGRESS" 고정)
     * @param deadlineAt 완주 데드라인 시각 (코스에 deadline_days가 설정된 경우, 없으면 null)
     */
    public record CourseStartResponse(
            String courseId,
            String status,
            LocalDateTime deadlineAt
    ) {}
}
