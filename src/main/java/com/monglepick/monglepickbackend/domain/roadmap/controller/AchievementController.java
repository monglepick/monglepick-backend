package com.monglepick.monglepickbackend.domain.roadmap.controller;

import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.roadmap.service.AchievementService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

/**
 * 업적/스탬프 랠리 컨트롤러 — 사용자 달성 업적 목록 조회 및 도장깨기 진행 현황 REST API.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET /api/v1/users/me/achievements         — 내 업적 달성 목록 조회 (카테고리 필터 지원)</li>
 *   <li>GET /api/v1/users/me/stamp-rally          — 내 도장깨기(Stamp Rally) 진행 현황 조회</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>두 엔드포인트 모두 JWT Bearer 토큰이 필요하다. 본인 데이터만 조회 가능하다.</p>
 *
 * <h3>응답 형태</h3>
 * <p>open-in-view=false 환경이므로 LazyInitializationException 방지를 위해
 * 서비스 레이어 트랜잭션 내에서 엔티티를 조회하고 DTO로 변환하여 반환한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>최초 생성 — UserAchievement 달성 목록 단순 반환</li>
 *   <li>v2 — AchievementResponse에 category/achieved/progress/maxProgress/achievedAt 필드 추가.
 *       category 쿼리 파라미터 지원. stamp-rally 엔드포인트 신규 추가.</li>
 * </ul>
 */
@Tag(name = "업적/스탬프랠리", description = "사용자 달성 업적·배지 목록 조회 및 도장깨기 진행 현황 조회")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Slf4j
public class AchievementController extends BaseController {

    /** 업적 서비스 — 달성 목록 조회 및 업적 부여 비즈니스 로직 */
    private final AchievementService achievementService;

    /** 코스 진행 현황 레포지토리 — stamp-rally 엔드포인트에서 직접 조회 */
    private final UserCourseProgressRepository userCourseProgressRepository;

    // ────────────────────────────────────────────────────────────────
    // GET /api/v1/users/me/achievements
    // ────────────────────────────────────────────────────────────────

    /**
     * 현재 로그인한 사용자의 업적 목록을 조회한다 (카테고리 필터 지원).
     *
     * <h4>동작</h4>
     * <ol>
     *   <li>category 파라미터가 있으면 해당 카테고리 업적 유형만, 없으면 전체 활성 업적 유형을 조회한다.</li>
     *   <li>각 업적 유형에 대해 사용자의 달성 여부를 확인한다.</li>
     *   <li>달성 시 achieved=true, progress=maxProgress, achievedAt(ISO 문자열)을 설정한다.</li>
     *   <li>미달성 시 achieved=false, progress=0, achievedAt=null을 설정한다.</li>
     * </ol>
     *
     * <h4>카테고리 값</h4>
     * <ul>
     *   <li>VIEWING    — 시청 관련 업적 (예: 5개 장르 탐험)</li>
     *   <li>SOCIAL     — 소셜/커뮤니티 활동 업적 (예: 리뷰 10개 달성)</li>
     *   <li>COLLECTION — 수집/저장 관련 업적</li>
     *   <li>CHALLENGE  — 도전과제 업적 (예: 코스 완주, 퀴즈 만점)</li>
     *   <li>파라미터 생략 — 전체 조회</li>
     * </ul>
     *
     * @param principal JWT 인증 정보
     * @param category  필터링할 카테고리 (선택, 생략 시 전체 조회)
     * @return 200 OK — 업적 응답 DTO 목록
     */
    @Operation(
            summary = "내 업적 달성 목록 조회",
            description = "로그인한 사용자의 업적/배지 목록을 반환합니다. " +
                    "category 파라미터로 VIEWING/SOCIAL/COLLECTION/CHALLENGE 카테고리별 필터링이 가능합니다. " +
                    "각 업적에 달성 여부(achieved), 진행률(progress/maxProgress), 달성 시각(achievedAt)을 포함합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업적 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/achievements")
    public ResponseEntity<List<AchievementResponse>> getAchievements(
            Principal principal,
            @Parameter(description = "업적 카테고리 필터 (VIEWING/SOCIAL/COLLECTION/CHALLENGE, 생략 시 전체)")
            @RequestParam(required = false) String category
    ) {
        String userId = resolveUserId(principal);
        log.debug("업적 목록 조회: userId={}, category={}", userId, category);

        // 서비스에서 업적 유형 전체(또는 카테고리 필터) + 달성 여부 + 진행률을 조합하여 반환
        List<AchievementResponse> responses = achievementService.getAchievementsWithProgress(userId, category);

        log.debug("업적 목록 조회 완료: userId={}, category={}, 건수={}", userId, category, responses.size());
        return ResponseEntity.ok(responses);
    }

    // ────────────────────────────────────────────────────────────────
    // GET /api/v1/users/me/stamp-rally
    // ────────────────────────────────────────────────────────────────

    /**
     * 현재 로그인한 사용자의 도장깨기(Stamp Rally) 진행 현황을 조회한다.
     *
     * <h4>동작</h4>
     * <ol>
     *   <li>{@code UserCourseProgressRepository.findByUserId(userId)}로 진행 중/완료 코스 목록을 조회한다.</li>
     *   <li>각 {@code UserCourseProgress}를 {@link StampRallyResponse}로 변환한다.</li>
     *   <li>movies, completedMovies는 현재 빈 목록으로 반환한다
     *       (추후 RoadmapCourse 엔티티와 연동하여 실제 영화 목록으로 교체 예정).</li>
     * </ol>
     *
     * <h4>미진행 코스</h4>
     * <p>아직 시작하지 않은 코스는 {@code user_course_progress} 레코드가 없으므로 결과에 포함되지 않는다.
     * 전체 코스 목록은 {@code GET /api/v1/roadmap/courses}에서 조회한다.</p>
     *
     * @param principal JWT 인증 정보
     * @return 200 OK — 도장깨기 진행 현황 DTO 목록 (진행 중 + 완료 코스)
     */
    @Operation(
            summary = "내 도장깨기 진행 현황 조회",
            description = "로그인한 사용자가 진행 중이거나 완료한 도장깨기(Stamp Rally) 코스 목록을 반환합니다. " +
                    "각 코스의 ID, 진행률, 완주 보상 포인트(기본 100P)를 포함합니다. " +
                    "movies/completedMovies 필드는 추후 RoadmapCourse 연동 시 실제 영화 ID 목록으로 제공될 예정입니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "도장깨기 진행 현황 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/stamp-rally")
    public ResponseEntity<List<StampRallyResponse>> getStampRally(Principal principal) {
        String userId = resolveUserId(principal);
        log.debug("도장깨기 진행 현황 조회: userId={}", userId);

        // UserCourseProgress 전체 목록 조회 (진행 중 + 완료 코스)
        List<UserCourseProgress> progressList = userCourseProgressRepository.findByUserId(userId);

        // UserCourseProgress → StampRallyResponse 변환
        List<StampRallyResponse> responses = progressList.stream()
                .map(progress -> new StampRallyResponse(
                        progress.getCourseId(),                             // id: courseId를 식별자로 사용
                        progress.getCourseId(),                             // name: courseId를 표시명으로 임시 사용
                                                                            //       (추후 RoadmapCourse.title 연동 예정)
                        "도장깨기 코스 진행 현황",                           // description: 고정 문자열 (추후 코스 설명 연동)
                        100,                                                // reward: 완주 보상 기본 100P
                        Collections.emptyList(),                            // movies: 미구현 (추후 RoadmapCourse 연동)
                        Collections.emptyList()                             // completedMovies: 미구현
                ))
                .toList();

        log.debug("도장깨기 진행 현황 조회 완료: userId={}, 진행 코스 수={}", userId, responses.size());
        return ResponseEntity.ok(responses);
    }

    // ────────────────────────────────────────────────────────────────
    // 내부 응답 DTO (컨트롤러 전용)
    // ────────────────────────────────────────────────────────────────

    /**
     * 업적 유형별 달성 여부 + 진행률 응답 DTO.
     *
     * <p>프론트엔드가 업적 화면을 렌더링하는 데 필요한 모든 정보를 포함한다.
     * 달성 여부(achieved), 진행률(progress/maxProgress), 달성 시각(achievedAt)을
     * 업적 유형 메타 정보와 함께 제공한다.</p>
     *
     * <h4>필드 설명</h4>
     * <ul>
     *   <li>{@code achievementTypeId} — 업적 유형 고유 ID</li>
     *   <li>{@code achievementCode}   — 업적 코드 (예: "course_complete")</li>
     *   <li>{@code achievementName}   — 업적 표시명 (예: "코스 완주")</li>
     *   <li>{@code description}       — 업적 설명</li>
     *   <li>{@code category}          — 카테고리 (VIEWING/SOCIAL/COLLECTION/CHALLENGE/null)</li>
     *   <li>{@code iconUrl}           — 업적 아이콘 URL (nullable)</li>
     *   <li>{@code rewardPoints}      — 달성 시 지급 포인트</li>
     *   <li>{@code requiredCount}     — 달성 조건 횟수 (maxProgress 역할)</li>
     *   <li>{@code achieved}          — 달성 여부 (true/false)</li>
     *   <li>{@code progress}          — 현재 진행 횟수 (달성 시 maxProgress, 미달성 시 0)</li>
     *   <li>{@code maxProgress}       — 달성에 필요한 총 횟수 (requiredCount 또는 1)</li>
     *   <li>{@code achievedAt}        — 달성 시각 ISO 문자열 (미달성 시 null)</li>
     * </ul>
     *
     * <h4>진행률 설계 참고</h4>
     * <p>현재 업적별 중간 진행률 추적은 미구현 상태이다.
     * 달성 여부만 이진(0 또는 maxProgress)으로 반환한다.
     * 추후 UserActivityProgress 연동으로 세밀한 진행률 표시가 가능하다.</p>
     */
    public record AchievementResponse(
            Long achievementTypeId,
            String achievementCode,
            String achievementName,
            String description,
            String category,       // VIEWING, SOCIAL, COLLECTION, CHALLENGE, null(기타)
            String iconUrl,
            Integer rewardPoints,
            Integer requiredCount, // maxProgress 역할 — 달성 조건 횟수 (없으면 1)
            boolean achieved,      // 달성 여부
            int progress,          // 현재 진행 횟수 (달성=maxProgress, 미달성=0)
            int maxProgress,       // 달성에 필요한 총 횟수
            String achievedAt      // ISO 날짜 문자열 (미달성 시 null)
    ) {}

    /**
     * 도장깨기(Stamp Rally) 코스 진행 현황 응답 DTO.
     *
     * <p>프론트엔드 stamp-rally 화면 렌더링에 필요한 코스 진행 정보를 담는다.</p>
     *
     * <h4>필드 설명</h4>
     * <ul>
     *   <li>{@code id}               — 코스 ID (roadmap_courses.course_id)</li>
     *   <li>{@code name}             — 코스 표시명 (현재 courseId로 임시 대체, 추후 RoadmapCourse.title 연동)</li>
     *   <li>{@code description}      — 코스 설명 (현재 고정 문자열, 추후 연동)</li>
     *   <li>{@code reward}           — 완주 보상 포인트 (기본 100P)</li>
     *   <li>{@code movies}           — 코스 내 영화 ID 목록 (현재 빈 목록, 추후 RoadmapCourse 연동)</li>
     *   <li>{@code completedMovies}  — 인증 완료된 영화 ID 목록 (현재 빈 목록, 추후 연동)</li>
     * </ul>
     */
    public record StampRallyResponse(
            String id,                       // courseId
            String name,                     // 코스 표시명 (임시: courseId)
            String description,              // 코스 설명
            int reward,                      // 완주 보상 포인트 (기본 100)
            List<String> movies,             // 코스 내 영화 ID 목록 (미구현: 빈 목록)
            List<String> completedMovies     // 인증 완료된 영화 ID 목록 (미구현: 빈 목록)
    ) {}
}
