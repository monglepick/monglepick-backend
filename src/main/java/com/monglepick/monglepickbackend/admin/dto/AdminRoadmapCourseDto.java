package com.monglepick.monglepickbackend.admin.dto;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 도장깨기(RoadmapCourse) 템플릿 관리 DTO 모음.
 *
 * <p>코스 등록·수정·활성화 토글 화면에서 사용하는 요청/응답 record를 모두 정의한다.
 * 코스 슬러그(course_id)는 사용자 진행 기록과 연결된 식별자이므로 신규 등록 시에만
 * 입력 받고, 수정 시에는 변경 불가하다.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>{@link CreateCourseRequest} — 신규 코스 등록</li>
 *   <li>{@link UpdateCourseRequest} — 기존 코스 수정 (course_id 제외)</li>
 *   <li>{@link UpdateActiveRequest} — 활성/비활성 토글</li>
 *   <li>{@link CourseResponse}      — 코스 단일 항목 응답</li>
 * </ul>
 *
 * <h3>movieIds 처리</h3>
 * <p>RoadmapCourse 엔티티는 {@code movie_ids}를 JSON 문자열로 저장하지만,
 * DTO는 {@code List&lt;String&gt;}으로 받아 서비스 레이어에서 Jackson으로 직렬화한다.
 * 클라이언트 호환성과 검증 편의를 위해 배열 형태를 유지한다.</p>
 *
 * <h3>난이도 값</h3>
 * <p>{@code beginner / intermediate / advanced} (소문자) — RoadmapCourse.Difficulty enum과 매칭.</p>
 */
public class AdminRoadmapCourseDto {

    // ─────────────────────────────────────────────
    // 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 신규 도장깨기 코스 등록 요청 DTO.
     *
     * <p>course_id는 UNIQUE 제약 — 중복 시 409.
     * movieIds는 최소 1개 이상이어야 한다.</p>
     */
    public record CreateCourseRequest(
            @NotBlank(message = "코스 슬러그(course_id)는 필수입니다.")
            @Size(max = 50, message = "코스 슬러그는 50자 이하여야 합니다.")
            String courseId,

            @NotBlank(message = "코스 제목은 필수입니다.")
            @Size(max = 300, message = "코스 제목은 300자 이하여야 합니다.")
            String title,

            String description,

            @Size(max = 100, message = "테마는 100자 이하여야 합니다.")
            String theme,

            @NotEmpty(message = "영화 ID 목록은 1개 이상이어야 합니다.")
            List<String> movieIds,

            /**
             * 난이도 — beginner / intermediate / advanced (소문자).
             * null 또는 빈 문자열이면 beginner로 처리.
             */
            String difficulty,

            Boolean quizEnabled
    ) {}

    /**
     * 기존 코스 수정 요청 DTO (course_id 제외).
     *
     * <p>코스 슬러그는 사용자 진행 기록과 연결되어 변경 불가.</p>
     */
    public record UpdateCourseRequest(
            @NotBlank(message = "코스 제목은 필수입니다.")
            @Size(max = 300, message = "코스 제목은 300자 이하여야 합니다.")
            String title,

            String description,

            @Size(max = 100, message = "테마는 100자 이하여야 합니다.")
            String theme,

            @NotEmpty(message = "영화 ID 목록은 1개 이상이어야 합니다.")
            List<String> movieIds,

            String difficulty,

            Boolean quizEnabled
    ) {}

    /**
     * 활성/비활성 토글 요청 DTO.
     *
     * <p>{@code isActive=false}이면 사용자 측 코스 목록에서 숨겨진다.
     * 기존 진행 기록은 보존된다.</p>
     */
    public record UpdateActiveRequest(
            Boolean isActive
    ) {}

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 영화 검색 결과 단건 DTO.
     *
     * <p>관리자가 도장깨기 템플릿 생성·수정 시 영화 제목으로 검색할 때
     * 드롭다운에 표시되는 최소 정보. 선택한 movieId를 movieIds 목록에 추가한다.</p>
     */
    public record MovieSearchResult(
            String movieId,
            String title,
            String titleEn,
            Integer releaseYear,
            String director,
            String posterPath
    ) {
        public static MovieSearchResult from(Movie movie) {
            return new MovieSearchResult(
                    movie.getMovieId(),
                    movie.getTitle(),
                    movie.getTitleEn(),
                    movie.getReleaseYear(),
                    movie.getDirector(),
                    movie.getPosterPath()
            );
        }
    }

    /**
     * 코스 단일 항목 응답 DTO.
     *
     * <p>관리자 화면 테이블 한 행에 표시되는 모든 컬럼을 포함한다.
     * movieIds는 JSON 문자열을 List&lt;String&gt;으로 파싱하여 반환한다.</p>
     */
    public record CourseResponse(
            Long roadmapCourseId,
            String courseId,
            String title,
            String description,
            String theme,
            List<String> movieIds,
            Integer movieCount,
            String difficulty,
            Boolean quizEnabled,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
