package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.CourseResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.CreateCourseRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.MovieSearchResult;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.UpdateCourseRequest;
import com.monglepick.monglepickbackend.admin.service.AdminRoadmapCourseService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 도장깨기(RoadmapCourse) 템플릿 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 도장깨기 템플릿" 메뉴의 5개 엔드포인트를 제공한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/roadmap/courses          — 코스 목록 (페이징, 활성/비활성 모두)</li>
 *   <li>GET    /api/v1/admin/roadmap/courses/{id}     — 코스 단건 조회</li>
 *   <li>POST   /api/v1/admin/roadmap/courses          — 코스 신규 등록</li>
 *   <li>PUT    /api/v1/admin/roadmap/courses/{id}     — 코스 수정 (course_id 제외)</li>
 *   <li>PATCH  /api/v1/admin/roadmap/courses/{id}/active — 활성/비활성 토글</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 *
 * <h3>비활성화 정책</h3>
 * <p>물리 삭제(DELETE) EP는 의도적으로 제공하지 않는다. 사용자 진행 기록
 * ({@code user_course_progress})이 course_id slug로 코스를 참조하므로 삭제 시
 * 정합성이 깨진다. 더 이상 사용하지 않는 코스는 PATCH로 비활성화만 한다.</p>
 */
@Tag(name = "관리자 — 도장깨기 템플릿", description = "RoadmapCourse 마스터 등록/수정/활성화 토글")
@RestController
@RequestMapping("/api/v1/admin/roadmap/courses")
@RequiredArgsConstructor
@Slf4j
public class AdminRoadmapCourseController {

    /** 도장깨기 코스 마스터 관리 서비스 */
    private final AdminRoadmapCourseService adminRoadmapCourseService;

    // ─────────────────────────────────────────────
    // 영화 검색 (도장깨기 템플릿 영화 추가용)
    // ─────────────────────────────────────────────

    /**
     * 영화 제목으로 영화를 검색한다 (도장깨기 템플릿 영화 추가 전용).
     *
     * <p>관리자가 코스 템플릿 생성·수정 시 영화 ID를 직접 입력하는 대신
     * 제목으로 검색하여 선택하도록 지원하는 자동완성 API.</p>
     *
     * @param keyword 검색 키워드 (한국어 또는 영어 제목)
     * @param size    반환 건수 (기본 10, 최대 30)
     * @return 200 OK + 영화 검색 결과 목록 (movieId, title, releaseYear, director, posterPath)
     *
     * @deprecated 2026-04-14: 현재 RoadmapCourseTab UI는 영화 ID를 텍스트로 직접 입력받는
     * 방식이라 이 검색 엔드포인트를 호출하지 않음. 향후 "제목 검색 후 선택" UI 구현 시에는
     * Recommend `/api/v1/search/movies` (ES 우선 + MySQL LIKE fallback) 호출로 일원화할 것.
     * 이 엔드포인트는 기동 호환성 위해 잔존.
     */
    @Deprecated
    @Operation(
            summary = "[Deprecated] 영화 검색 (도장깨기 템플릿용)",
            description = "제목 키워드로 영화를 검색합니다. " +
                    "2026-04-14부터 Deprecated. 미사용 엔드포인트. " +
                    "신규 UI 구현 시 Recommend `/api/v1/search/movies` 사용 권장."
    )
    @GetMapping("/movies/search")
    public ResponseEntity<ApiResponse<List<MovieSearchResult>>> searchMovies(
            @Parameter(description = "검색 키워드 (한국어 또는 영어 제목)", required = true, example = "인셉션")
            @RequestParam String keyword,
            @Parameter(description = "반환 건수 (최대 30)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        // keyword가 null 또는 공백이면 빈 목록 즉시 반환 (불필요한 DB 쿼리 방지)
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        log.debug("[관리자] 도장깨기 영화 검색 — keyword={}, size={}", keyword, size);
        return ResponseEntity.ok(ApiResponse.ok(adminRoadmapCourseService.searchMovies(keyword, size)));
    }

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 코스 목록 조회 (페이징, 활성/비활성 모두).
     *
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 200 OK + 페이징된 코스 목록
     */
    @Operation(
            summary = "코스 목록 조회",
            description = "활성/비활성 모든 코스를 페이징으로 조회. createdAt DESC 정렬."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> getCourses(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CourseResponse> result = adminRoadmapCourseService.getCourses(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 코스 단건 조회.
     *
     * @param id roadmap_course_id (BIGINT PK)
     * @return 200 OK + 코스 응답
     */
    @Operation(summary = "코스 단건 조회", description = "roadmap_course_id로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminRoadmapCourseService.getCourse(id)));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 신규 코스 등록.
     *
     * <p>course_id는 UNIQUE — 중복 시 409.</p>
     */
    @Operation(
            summary = "코스 신규 등록",
            description = "신규 도장깨기 코스 등록. course_id는 UNIQUE이므로 중복 시 409."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CreateCourseRequest request
    ) {
        log.info("[관리자] 코스 등록 요청 — courseId={}, title={}, movieCount={}",
                request.courseId(), request.title(),
                request.movieIds() != null ? request.movieIds().size() : 0);
        CourseResponse created = adminRoadmapCourseService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /**
     * 코스 수정 (course_id 제외).
     *
     * <p>course_id는 사용자 진행 기록과 연결된 식별자이므로 변경 불가.
     * 표시명/설명/테마/영화목록/난이도/퀴즈활성화만 수정 가능하다.</p>
     */
    @Operation(
            summary = "코스 수정",
            description = "course_id 제외 모든 필드 수정 가능. 코스 슬러그는 사용자 진행 기록 연결 키이므로 불변."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request
    ) {
        log.info("[관리자] 코스 수정 요청 — id={}, title={}", id, request.title());
        CourseResponse updated = adminRoadmapCourseService.updateCourse(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * 활성/비활성 토글.
     *
     * <p>비활성화는 hard delete가 아니다. 사용자 진행 기록은 보존되며,
     * 사용자 측 코스 목록에서만 숨겨진다.</p>
     */
    @Operation(
            summary = "코스 활성/비활성 토글",
            description = "is_active 값을 변경. 비활성화 시 사용자 측 코스 목록에서 숨김. 진행 기록은 보존."
    )
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<CourseResponse>> updateActiveStatus(
            @PathVariable Long id,
            @RequestBody UpdateActiveRequest request
    ) {
        log.info("[관리자] 코스 활성 상태 변경 요청 — id={}, isActive={}", id, request.isActive());
        CourseResponse updated = adminRoadmapCourseService.updateActiveStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }
}
