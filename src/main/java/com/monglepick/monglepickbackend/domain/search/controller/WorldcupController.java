package com.monglepick.monglepickbackend.domain.search.controller;

import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickRequest;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 이상형 월드컵 컨트롤러 — 월드컵 세션 시작/매치 선택/이력 조회 REST API.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>POST /api/v1/worldcup/start    — 월드컵 세션 시작 (JWT 필수)</li>
 *   <li>POST /api/v1/worldcup/pick     — 매치 선택 (JWT 필수)</li>
 *   <li>GET  /api/v1/worldcup/sessions — 진행 중인 세션 목록 조회 (JWT 필수)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 JWT Bearer 토큰이 필요하다.
 * Principal에서 userId를 추출하여 세션 소유 여부를 검증한다.</p>
 */
@Tag(name = "이상형 월드컵", description = "영화 이상형 월드컵 세션 시작, 매치 선택, 이력 조회")
@RestController
@RequestMapping("/api/v1/worldcup")
@RequiredArgsConstructor
@Slf4j
public class WorldcupController extends BaseController {

    /** 월드컵 서비스 — 세션/매치 비즈니스 로직 */
    private final WorldcupService worldcupService;

    /**
     * 이상형 월드컵 세션을 시작한다.
     *
     * <p>요청에 포함된 후보 영화 목록과 라운드 크기로 세션을 생성하고,
     * 첫 라운드 매치 목록을 반환한다.</p>
     *
     * @param principal JWT 인증 정보
     * @param request   세션 시작 요청 (genreFilter, roundSize, candidateMovieIds)
     * @return 201 Created — 생성된 세션 ID 및 첫 라운드 매치 목록
     */
    @Operation(
            summary = "월드컵 세션 시작",
            description = "후보 영화 목록과 라운드 크기로 월드컵 세션을 시작합니다. " +
                    "candidateMovieIds의 크기는 roundSize와 동일해야 합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "세션 생성 및 첫 라운드 매치 반환"),
            @ApiResponse(responseCode = "400", description = "후보 영화 수 불일치 또는 유효성 검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/start")
    public ResponseEntity<WorldcupStartResponse> startWorldcup(
            Principal principal,
            @RequestBody @Valid WorldcupStartRequest request
    ) {
        String userId = resolveUserId(principal);
        log.info("월드컵 시작 요청: userId={}, roundSize={}, candidateCount={}",
                userId, request.roundSize(), request.candidateMovieIds().size());

        WorldcupStartResponse response = worldcupService.startWorldcup(
                userId,
                request.genreFilter(),
                request.roundSize(),
                request.candidateMovieIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 매치에서 승자를 선택한다.
     *
     * <p>선택 후 라운드/게임 완료 여부를 응답에 포함한다.
     * gameCompleted=true이면 최종 우승 영화가 winnerMovieId에 담겨 반환된다.</p>
     *
     * @param principal JWT 인증 정보
     * @param request   매치 선택 요청 (sessionId, matchId, winnerMovieId)
     * @return 200 OK — 현재 진행 상태 및 다음 라운드 매치 목록 (있을 경우)
     */
    @Operation(
            summary = "매치 승자 선택",
            description = "지정된 매치에서 승자 영화를 선택합니다. " +
                    "라운드 완료 시 다음 라운드 매치가 자동 생성되어 응답에 포함됩니다.",
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
