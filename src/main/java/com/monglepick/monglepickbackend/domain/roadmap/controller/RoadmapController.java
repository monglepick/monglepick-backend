package com.monglepick.monglepickbackend.domain.roadmap.controller;

import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseCompleteResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseProgressResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseDetailResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseListResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseStartResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseReviewResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import com.monglepick.monglepickbackend.domain.roadmap.service.RoadmapService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 도장깨기(로드맵) 컨트롤러 — 코스 영화 인증 및 진행 현황 조회 REST API.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>POST /api/v1/roadmap/courses/{courseId}/verify — 영화 인증 처리 (JWT 필수)</li>
 *   <li>GET  /api/v1/roadmap/courses/progress          — 전체 코스 진행 현황 조회 (JWT 필수)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 JWT Bearer 토큰이 필요하다.</p>
 *
 * <h3>설계 참고</h3>
 * <p>{@code totalMovies}와 {@code rewardPoints}는 신규 코스 시작 시에만 의미가 있다.
 * 기존 진행 레코드가 있는 경우 서비스 레이어에서 기존 값을 유지한다.</p>
 */
@Tag(name = "도장깨기", description = "코스 영화 인증 처리 및 진행 현황 조회")
@RestController
@RequestMapping("/api/v1/roadmap/courses")
@RequiredArgsConstructor
@Slf4j
public class RoadmapController extends BaseController {

    /** 로드맵 서비스 — 영화 인증 및 진행 현황 비즈니스 로직 */
    private final RoadmapService roadmapService;

    // ────────────────────────────────────────────────────────────────
    // 신규 엔드포인트 — 코스 목록/상세/시작/영화완료
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스 목록을 조회한다.
     *
     * <p>theme 쿼리 파라미터가 없으면 전체 코스를 반환한다.
     * 로그인 사용자의 경우 각 코스에 진행률(progressPercent)이 포함된다.
     * 비로그인 사용자(principal=null)는 progressPercent=0.0으로 반환한다.</p>
     *
     * @param theme     테마 필터 (선택, 예: "감독별", "장르별")
     * @param principal JWT 인증 정보 (선택 — 비로그인 허용)
     * @return 200 OK — 코스 목록 DTO 리스트
     */
    @Operation(
            summary = "코스 목록 조회",
            description = "도장깨기 코스 목록을 반환합니다. " +
                    "theme 파라미터로 테마 필터링이 가능하며, " +
                    "로그인 사용자는 각 코스의 진행률도 함께 확인할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "코스 목록 조회 성공")
    })
    @GetMapping("")
    public ResponseEntity<List<CourseListResponse>> getCourses(
            @Parameter(description = "테마 필터 (미입력 시 전체 조회)", example = "감독별")
            @RequestParam(required = false) String theme,

            Principal principal
    ) {
        /* 비로그인 허용 — principal이 null이면 userId=null 전달 */
        String userId = (principal != null) ? principal.getName() : null;
        log.debug("코스 목록 조회: theme={}, userId={}", theme, userId);

        List<CourseListResponse> responses = roadmapService.getCourses(theme, userId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 코스 상세 정보를 조회한다.
     *
     * <p>courseId(slug)로 특정 코스의 상세 정보를 반환한다.
     * 영화 ID 목록(movieIds), 시작 여부(started), 진행률(progressPercent)이 포함된다.
     * 비로그인 사용자는 started=false, progressPercent=0.0으로 반환한다.</p>
     *
     * @param courseId  코스 슬러그 (예: "nolan-filmography")
     * @param principal JWT 인증 정보 (선택 — 비로그인 허용)
     * @return 200 OK — 코스 상세 DTO
     */
    @Operation(
            summary = "코스 상세 조회",
            description = "코스 ID(slug)로 상세 정보를 조회합니다. " +
                    "영화 ID 목록, 난이도, 시작 여부, 진행률이 포함됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "코스 상세 조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스")
    })
    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(
            @Parameter(description = "코스 슬러그", required = true, example = "nolan-filmography")
            @PathVariable String courseId,

            Principal principal
    ) {
        /* 비로그인 허용 — principal이 null이면 userId=null 전달 */
        String userId = (principal != null) ? principal.getName() : null;
        log.debug("코스 상세 조회: courseId={}, userId={}", courseId, userId);

        CourseDetailResponse response = roadmapService.getCourseDetail(courseId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 코스를 시작한다 (진행 레코드 생성).
     *
     * <p>최초 시작 시 UserCourseProgress 레코드를 생성한다.
     * 이미 시작한 코스의 경우 멱등 응답을 반환한다 (201 대신 200 반환).
     * 코스 시작은 JWT 인증이 필수이다.</p>
     *
     * @param courseId  코스 슬러그
     * @param principal JWT 인증 정보 (필수)
     * @return 201 Created — 코스 시작 응답 DTO (이미 시작한 경우 200 OK)
     */
    @Operation(
            summary = "코스 시작",
            description = "도장깨기 코스를 시작합니다. " +
                    "최초 시작 시 진행 레코드가 생성되며, " +
                    "이미 시작한 코스의 경우 멱등 응답(200)을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "코스 시작 성공 (신규 진행 레코드 생성)"),
            @ApiResponse(responseCode = "200", description = "이미 시작한 코스 (멱등 응답)"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스")
    })
    @PostMapping("/{courseId}/start")
    public ResponseEntity<CourseStartResponse> startCourse(
            @Parameter(description = "코스 슬러그", required = true, example = "nolan-filmography")
            @PathVariable String courseId,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("코스 시작 요청: userId={}, courseId={}", userId, courseId);

        CourseStartResponse response = roadmapService.startCourse(courseId, userId);

        /* 이미 시작한 코스(멱등)는 200, 신규 시작은 201 반환 */
        HttpStatus status = "IN_PROGRESS".equals(response.status())
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 코스 내 특정 영화를 완료 처리한다.
     *
     * <p>verifiedMovies를 1 증가시키고 진행률을 재계산한다.
     * 모든 영화 완료 시 코스 완주 처리와 리워드 지급이 자동으로 이뤄진다.
     * JWT 인증이 필수이다.</p>
     *
     * @param courseId  코스 슬러그
     * @param movieId   완료 처리할 영화 ID
     * @param principal JWT 인증 정보 (필수)
     * @return 200 OK — 업데이트된 코스 진행 현황 DTO
     */
    @Operation(
            summary = "영화 완료 처리",
            description = "코스 내 특정 영화를 완료 처리합니다. " +
                    "인증 완료 영화 수(verifiedMovies)가 1 증가하며, " +
                    "모든 영화 완료 시 코스 완주 리워드가 자동 지급됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "영화 완료 처리 성공"),
            @ApiResponse(responseCode = "400", description = "이미 완주한 코스"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스")
    })
    @PostMapping("/{courseId}/movies/{movieId}/complete")
    public ResponseEntity<CourseCompleteResponse> completeMovie(
            @Parameter(description = "코스 슬러그", required = true, example = "nolan-filmography")
            @PathVariable String courseId,

            @Parameter(description = "완료 처리할 영화 ID", required = true, example = "12345")
            @PathVariable String movieId,

            @RequestBody(required = false) Map<String, String> body,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        String reviewText = (body != null) ? body.get("review") : null;
        log.info("영화 완료 요청: userId={}, courseId={}, movieId={}, hasReview={}", userId, courseId, movieId, reviewText != null);

        // Backend 가 Agent 를 직접 호출하여 AI 판정까지 한 번에 반환.
        // (이전 3-step 플로우는 AI 우회 취약점으로 2026-04-22 제거됨)
        CourseCompleteResponse response = roadmapService.completeMovie(courseId, movieId, userId, reviewText);
        return ResponseEntity.ok(response);
    }

    /**
     * 시청인증 버튼 진입 시 해당 영화에 대해 사용자가 작성한 리뷰를 조회한다.
     *
     * <p>인증 기록이 있으면 리뷰 본문과 verified=true를 반환한다.
     * 아직 인증하지 않은 영화이면 verified=false, reviewText=null을 반환한다.</p>
     *
     * @param courseId  코스 슬러그
     * @param movieId   영화 ID
     * @param principal JWT 인증 정보 (필수)
     * @return 200 OK — 시청 리뷰 응답 DTO
     */
    @Operation(
            summary = "시청 리뷰 조회",
            description = "코스 내 특정 영화에 대해 작성한 시청 리뷰를 반환합니다. " +
                    "인증 기록이 없으면 verified=false로 반환됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리뷰 조회 성공 (미인증이면 verified=false)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/{courseId}/movies/{movieId}/review")
    public ResponseEntity<CourseReviewResponse> getMovieReview(
            @PathVariable String courseId,
            @PathVariable String movieId,
            Principal principal
    ) {
        String userId = resolveUserId(principal);
        CourseReviewResponse response = roadmapService.getMovieReview(courseId, movieId, userId);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────────────────────
    // 기존 엔드포인트 — 영화 인증 / 전체 진행 현황
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스 내 영화를 인증 처리한다.
     *
     * <p>최초 인증 시 코스 진행 레코드를 신규 생성한다.
     * 인증 후 완주 판정이 이뤄지며, 완주 시 리워드가 자동 지급된다.</p>
     *
     * @param courseId     인증할 코스 ID (URL 경로 파라미터, 예: "nolan-filmography")
     * @param totalMovies  코스 내 총 영화 수 (신규 시작 시 필요, 기존 진행 레코드 있으면 무시됨, 기본값 0)
     * @param rewardPoints 완주 시 지급 포인트 (코스별 설정값, 0이면 포인트 미지급, 기본값 0)
     * @param principal    JWT 인증 정보
     * @return 200 OK — 업데이트된 코스 진행 현황 DTO
     */
    @Operation(
            summary = "코스 영화 인증",
            description = "지정된 코스에서 영화 1편을 인증합니다. " +
                    "최초 인증 시 코스 진행 레코드가 생성되며, " +
                    "모든 영화 인증 완료 시 완주 리워드가 자동 지급됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 완료 — 진행 현황 반환"),
            @ApiResponse(responseCode = "400", description = "이미 완주한 코스 또는 유효하지 않은 요청"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/{courseId}/verify")
    public ResponseEntity<CourseProgressResponse> verifyMovie(
            @Parameter(description = "코스 ID (slug 형태)", required = true, example = "nolan-filmography")
            @PathVariable String courseId,

            @Parameter(description = "코스 내 총 영화 수 (신규 시작 시 설정)", example = "10")
            @RequestParam(required = false, defaultValue = "0") int totalMovies,

            @Parameter(description = "완주 시 지급 포인트 (0이면 미지급)", example = "100")
            @RequestParam(required = false, defaultValue = "0") int rewardPoints,

            Principal principal
    ) {
        String userId = resolveUserId(principal);
        log.info("영화 인증 요청: userId={}, courseId={}, totalMovies={}, rewardPoints={}",
                userId, courseId, totalMovies, rewardPoints);

        CourseProgressResponse response = roadmapService.verifyMovie(
                userId, courseId, totalMovies, rewardPoints
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 사용자의 전체 코스 진행 현황 목록을 조회한다.
     *
     * <p>진행 중(IN_PROGRESS)과 완료(COMPLETED) 코스를 모두 반환한다.
     * 마이페이지 도장깨기 탭에서 사용한다.</p>
     *
     * @param principal JWT 인증 정보
     * @return 200 OK — 전체 코스 진행 현황 DTO 목록
     */
    @Operation(
            summary = "전체 코스 진행 현황 조회",
            description = "로그인한 사용자의 진행 중/완료 코스 진행 현황 전체 목록을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "진행 현황 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/progress")
    public ResponseEntity<List<CourseProgressResponse>> getCourseProgress(Principal principal) {
        String userId = resolveUserId(principal);
        log.debug("코스 진행 현황 조회: userId={}", userId);

        List<UserCourseProgress> progressList = roadmapService.getCourseProgress(userId);
        List<CourseProgressResponse> responses = progressList.stream()
                .map(CourseProgressResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
