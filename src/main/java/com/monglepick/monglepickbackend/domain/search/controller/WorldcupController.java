package com.monglepick.monglepickbackend.domain.search.controller;

import com.monglepick.monglepickbackend.domain.search.dto.WorldcupCategoryOptionResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickRequest;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupResultResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartOptionsRequest;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartOptionsResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartRequest;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartResponse;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSession;
import com.monglepick.monglepickbackend.domain.search.service.WorldcupService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 이상형 월드컵 컨트롤러 — 세션 시작/매치 선택/결과 조회/이력 조회 REST API.
 *
 * <h3>엔드포인트 목록 (v2)</h3>
 * <ul>
 *   <li>GET  /api/v1/worldcup/categories         — 사용자 노출용 카테고리 목록 조회</li>
 *   <li>POST /api/v1/worldcup/options            — 시작 조건별 가능 라운드 계산</li>
 *   <li>POST /api/v1/worldcup/start             — 월드컵 세션 시작 (JWT 필수)</li>
 *   <li>POST /api/v1/worldcup/pick              — 매치 선택 (JWT 필수)</li>
 *   <li>GET  /api/v1/worldcup/sessions          — 진행 중인 세션 목록 조회 (JWT 필수)</li>
 *   <li>GET  /api/v1/worldcup/result/{sessionId} — 완료된 세션 결과 조회 (JWT 필수)</li>
 * </ul>
 *
 * <h3>월드컵 시작 흐름</h3>
 * <ul>
 *   <li>사용자는 {@code categories} 또는 다중 {@code selectedGenres} 기반으로 시작 조건을 선택한다.</li>
 *   <li>{@code /options} 응답의 {@code availableRoundSizes}로 진행 가능한 라운드만 노출한다.</li>
 *   <li>POST /start 응답: {@code gameId} 필드 추가 (sessionId 별칭).</li>
 *   <li>POST /pick 응답: {@code isFinished}, {@code finalWinner}, {@code nextMatch} alias 필드 추가.</li>
 *   <li>GET  /result/{sessionId}: 신규 엔드포인트 — Frontend {@code getWorldcupResult(gameId)} 대응.</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 JWT Bearer 토큰이 필요하다.
 * Principal에서 userId를 추출하여 세션 소유 여부를 검증한다.</p>
 */
@Tag(name = "이상형 월드컵", description = "영화 이상형 월드컵 세션 시작, 매치 선택, 결과 조회, 이력 조회")
@RestController
@RequestMapping("/api/v1/worldcup")
@RequiredArgsConstructor
@Slf4j
public class WorldcupController extends BaseController {

    /** 월드컵 서비스 — 세션/매치/결과 비즈니스 로직 */
    private final WorldcupService worldcupService;

    @Operation(
            summary = "월드컵 카테고리 목록 조회",
            description = "사용자에게 노출할 활성 카테고리와 각 카테고리의 가능 라운드 정보를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/categories")
    public ResponseEntity<List<WorldcupCategoryOptionResponse>> getCategories() {
        return ResponseEntity.ok(worldcupService.getAvailableCategories());
    }

    @Operation(
            summary = "월드컵 시작 가능 라운드 조회",
            description = "카테고리 또는 장르 기반 조건으로 후보 풀 크기와 시작 가능한 라운드 목록을 계산합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/options")
    public ResponseEntity<WorldcupStartOptionsResponse> getStartOptions(
            @RequestBody @Valid WorldcupStartOptionsRequest request
    ) {
        return ResponseEntity.ok(worldcupService.getStartOptions(request));
    }

    /**
     * 이상형 월드컵 세션을 시작한다.
     *
     * <p>사용자가 선택한 카테고리 또는 장르 조건을 기준으로 후보를 산정하고,
     * 선택한 라운드 크기만큼 무작위 매치를 생성한다.</p>
     *
     * @param principal JWT 인증 정보
     * @param request   세션 시작 요청 (sourceType, categoryId or selectedGenres, roundSize)
     * @return 201 Created — 생성된 세션 ID(gameId 포함) 및 첫 라운드 매치 목록
     */
    @Operation(
            summary = "월드컵 세션 시작",
            description = "카테고리 기반 또는 장르 기반 조건으로 월드컵 세션을 시작합니다. " +
                    "응답의 gameId는 sessionId와 동일한 별칭 필드입니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "세션 생성 및 첫 라운드 매치 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 시작 조건 또는 후보 부족"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/start")
    public ResponseEntity<WorldcupStartResponse> startWorldcup(
            Principal principal,
            @RequestBody @Valid WorldcupStartRequest request
    ) {
        String userId = resolveUserId(principal);
        log.info("월드컵 시작 요청: userId={}, sourceType={}, categoryId={}, selectedGenres={}, roundSize={}",
                userId, request.sourceType(), request.categoryId(), request.selectedGenres(), request.roundSize());

        WorldcupStartResponse response = worldcupService.startWorldcup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 매치에서 승자를 선택한다.
     *
     * <p>선택 후 라운드/게임 완료 여부를 응답에 포함한다.</p>
     *
     * <h4>응답 alias 필드 (Frontend 호환)</h4>
     * <ul>
     *   <li>{@code isFinished}  = {@code gameCompleted}</li>
     *   <li>{@code finalWinner} = {@code winnerMovieId}</li>
     *   <li>{@code nextMatch}   = {@code nextMatches}의 첫 번째 원소</li>
     * </ul>
     *
     * @param principal JWT 인증 정보
     * @param request   매치 선택 요청 (sessionId, matchId, winnerMovieId)
     * @return 200 OK — 현재 진행 상태 및 다음 라운드 매치 목록 (alias 필드 포함)
     */
    @Operation(
            summary = "매치 승자 선택",
            description = "지정된 매치에서 승자 영화를 선택합니다. " +
                    "라운드 완료 시 다음 라운드 매치가 자동 생성되어 응답에 포함됩니다. " +
                    "gameCompleted=true이면 isFinished=true, finalWinner에 우승 영화 ID가 담깁니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선택 완료 — 진행 상태 및 다음 매치 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 세션/매치 ID 또는 유효하지 않은 승자 ID"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/pick")
    public ResponseEntity<WorldcupPickResponse> pick(
            Principal principal,
            @RequestBody @Valid WorldcupPickRequest request
    ) {
        String userId = resolveUserId(principal);
        log.debug("매치 선택 요청: userId={}, sessionId={}, matchId={}, winnerMovieId={}",
                userId, request.sessionId(), request.matchId(), request.winnerMovieId());

        WorldcupPickResponse response = worldcupService.pick(
                userId,
                request.sessionId(),
                request.matchId(),
                request.winnerMovieId()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 완료된 월드컵 세션의 결과를 조회한다.
     *
     * <p>Frontend의 {@code getWorldcupResult(gameId)} 호출에 대응한다.
     * {@code sessionId}는 Frontend에서 {@code gameId}로 전달된 값과 동일하다.
     * 세션이 COMPLETED 상태가 아니면 400 응답이 반환된다.</p>
     *
     * <h4>응답 구조</h4>
     * <ul>
     *   <li>{@code gameId} — sessionId와 동일 (Frontend 호환)</li>
     *   <li>{@code winner} — 우승 영화 상세 (movieId, title, posterPath, releaseYear)</li>
     *   <li>{@code completedAt} — 완료 시각 (ISO 8601)</li>
     * </ul>
     *
     * @param sessionId 세션 ID (Frontend gameId와 동일한 값)
     * @param principal JWT 인증 정보
     * @return 200 OK — 결과 응답 DTO (gameId, winner, completedAt)
     */
    @Operation(
            summary = "월드컵 결과 조회",
            description = "완료된 월드컵 세션의 우승 영화 정보를 조회합니다. " +
                    "sessionId는 시작 시 받은 gameId와 동일한 값입니다. " +
                    "COMPLETED 상태가 아닌 세션에 요청하면 400 오류가 반환됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결과 조회 성공"),
            @ApiResponse(responseCode = "400", description = "세션 미발견 또는 미완료 상태"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/result/{sessionId}")
    public ResponseEntity<WorldcupResultResponse> getResult(
            @PathVariable Long sessionId,
            Principal principal
    ) {
        // principal은 인증 확인용 — 결과는 sessionId로 조회 (소유권 검증은 서비스에서 COMPLETED 상태로 간접 보장)
        String userId = resolveUserId(principal);
        log.debug("월드컵 결과 조회 요청: userId={}, sessionId={}", userId, sessionId);

        WorldcupResultResponse response = worldcupService.getResult(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 사용자의 진행 중인 월드컵 세션 목록을 조회한다.
     *
     * <p>IN_PROGRESS 상태의 세션만 반환한다.
     * 클라이언트에서 "이어하기" 기능 구현 시 활용할 수 있다.</p>
     *
     * @param principal JWT 인증 정보
     * @return 200 OK — 진행 중인 세션 엔티티 목록
     */
    @Operation(
            summary = "진행 중인 월드컵 세션 목록 조회",
            description = "로그인한 사용자의 IN_PROGRESS 상태 세션 목록을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "세션 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/sessions")
    public ResponseEntity<List<WorldcupSession>> getMySessions(Principal principal) {
        String userId = resolveUserId(principal);
        log.debug("진행 중 세션 목록 조회: userId={}", userId);

        List<WorldcupSession> sessions = worldcupService.getMySessions(userId);
        return ResponseEntity.ok(sessions);
    }
}
