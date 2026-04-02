package com.monglepick.monglepickbackend.domain.roadmap.controller;

import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseProgressResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
