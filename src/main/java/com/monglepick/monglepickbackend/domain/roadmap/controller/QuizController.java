package com.monglepick.monglepickbackend.domain.roadmap.controller;

import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.QuizResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitRequest;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitResponse;
import com.monglepick.monglepickbackend.domain.roadmap.service.QuizService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 퀴즈 컨트롤러 — 영화 퀴즈 목록 조회 및 정답 제출 REST API.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET  /api/v1/quizzes/movie/{movieId} — 영화별 PUBLISHED 퀴즈 목록 (공개)</li>
 *   <li>GET  /api/v1/quizzes/today           — 오늘 날짜 PUBLISHED 퀴즈 목록 (공개)</li>
 *   <li>POST /api/v1/quizzes/{quizId}/submit — 정답 제출 및 채점 (JWT 필수)</li>
 * </ul>
 *
 * <h3>인증 정책</h3>
 * <ul>
 *   <li>GET 엔드포인트: 비로그인 허용 (공개) — SecurityConfig의 permitAll 대상</li>
 *   <li>POST /submit: JWT Bearer 토큰 필수 — 미인증 시 401</li>
 * </ul>
 *
 * <h3>보안 주의</h3>
 * <p>GET 응답({@link QuizResponse})에는 정답({@code correctAnswer})이 포함되지 않는다.
 * 정답 확인은 POST /submit 채점 후 {@link SubmitResponse}의 {@code explanation}으로만 가능하다.</p>
 *
 * <h3>설계 참조</h3>
 * <p>docs/리워드_결제_설계서.md §14 — 퀴즈 파이프라인 + QUIZ_CORRECT 리워드 정책</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07: 신규 생성 — 퀴즈 API 3개 엔드포인트 구현</li>
 * </ul>
 */
@Tag(name = "퀴즈", description = "영화 퀴즈 목록 조회 및 정답 제출")
@RestController
@RequestMapping("/api/v1/quizzes")
@RequiredArgsConstructor
@Slf4j
public class QuizController extends BaseController {

    /** 퀴즈 서비스 — 목록 조회, 채점, 리워드 지급 비즈니스 로직 */
    private final QuizService quizService;

    // ────────────────────────────────────────────────────────────────
    // 퀴즈 목록 조회 (공개 API)
    // ────────────────────────────────────────────────────────────────

    /**
     * 특정 영화의 PUBLISHED 퀴즈 목록을 조회한다.
     *
     * <p>영화 상세 페이지 하단의 "이 영화 퀴즈" 섹션에서 사용한다.
     * 비로그인 사용자도 조회 가능하나, 정답 제출은 로그인 필요.
     * 응답에 정답({@code correctAnswer})은 포함되지 않는다.</p>
     *
     * @param movieId 퀴즈를 조회할 영화 ID (VARCHAR(50), URL 경로 파라미터)
     * @return 200 OK — PUBLISHED 퀴즈 DTO 목록 (없으면 빈 배열)
     */
    @Operation(
            summary = "영화별 퀴즈 목록 조회",
            description = "특정 영화의 출제 중(PUBLISHED) 퀴즈 목록을 반환합니다. " +
                    "비로그인 사용자도 조회 가능합니다. 정답은 포함되지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "퀴즈 목록 조회 성공 (없으면 빈 배열)")
    })
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<QuizResponse>> getMovieQuizzes(
            @Parameter(description = "퀴즈를 조회할 영화 ID", required = true, example = "tt0816692")
            @PathVariable String movieId
    ) {
        log.debug("영화별 퀴즈 목록 요청: movieId={}", movieId);

        List<QuizResponse> responses = quizService.getMovieQuizzes(movieId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 오늘 날짜(quizDate = today)의 PUBLISHED 퀴즈 목록을 조회한다.
     *
     * <p>메인 페이지 또는 퀴즈 탭의 "오늘의 퀴즈" 섹션에서 사용한다.
     * quizDate가 오늘인 퀴즈만 반환하므로 관리자가 quizDate를 지정하지 않은 퀴즈는
     * 이 엔드포인트로 조회되지 않는다. (getAllPublished 엔드포인트 별도 제공 검토 가능)</p>
     *
     * @return 200 OK — 오늘 날짜 PUBLISHED 퀴즈 DTO 목록 (없으면 빈 배열)
     */
    @Operation(
            summary = "오늘의 퀴즈 목록 조회",
            description = "오늘 날짜(quizDate = today)로 등록된 출제 중 퀴즈 목록을 반환합니다. " +
                    "비로그인 사용자도 조회 가능합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "오늘의 퀴즈 목록 조회 성공 (없으면 빈 배열)")
    })
    @GetMapping("/today")
    public ResponseEntity<List<QuizResponse>> getTodayQuizzes() {
        log.debug("오늘의 퀴즈 목록 요청");

        List<QuizResponse> responses = quizService.getTodayQuizzes();
        return ResponseEntity.ok(responses);
    }

    // ────────────────────────────────────────────────────────────────
    // 정답 제출 (JWT 필수)
    // ────────────────────────────────────────────────────────────────

    /**
     * 퀴즈 정답을 제출하고 채점 결과와 리워드 지급 여부를 반환한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>JWT에서 userId 추출 ({@link BaseController#resolveUserId})</li>
     *   <li>퀴즈 존재 및 PUBLISHED 상태 확인</li>
     *   <li>채점 — 대소문자 무시 비교</li>
     *   <li>참여 기록 저장 (동일 퀴즈 재제출 가능, 단 리워드는 최초 정답 1회)</li>
     *   <li>정답이고 최초 정답인 경우 QUIZ_CORRECT 리워드 지급</li>
     * </ol>
     *
     * <h4>응답 필드 설명</h4>
     * <ul>
     *   <li>{@code correct}     — 이번 제출의 정답 여부</li>
     *   <li>{@code explanation} — 관리자가 입력한 정답 해설 (null 가능)</li>
     *   <li>{@code rewardPoint} — 이번 제출로 실제 지급된 포인트 (최초 정답 외에는 0)</li>
     * </ul>
     *
     * @param quizId    정답을 제출할 퀴즈 ID (URL 경로 파라미터)
     * @param request   정답 제출 요청 DTO ({@code answer} 필드)
     * @param principal JWT 인증 정보 (필수)
     * @return 200 OK — 채점 결과 DTO
     * @apiNote 401 Unauthorized: 미인증 사용자 | 404 Not Found: 퀴즈 미존재 또는 PUBLISHED 아님
     */
    @Operation(
            summary = "퀴즈 정답 제출",
            description = "퀴즈에 답변을 제출하고 채점 결과를 받습니다. " +
                    "정답이고 최초 정답인 경우 QUIZ_CORRECT 리워드가 자동 지급됩니다. " +
                    "동일 퀴즈를 여러 번 제출할 수 있지만 리워드는 최초 정답 1회만 지급됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "채점 완료 (correct=true/false)"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 (answer 누락 등)"),
            @ApiResponse(responseCode = "401", description = "인증 필요 — JWT 토큰 없음"),
            @ApiResponse(responseCode = "404", description = "퀴즈가 존재하지 않거나 출제 중이 아님")
    })
    @PostMapping("/{quizId}/submit")
    public ResponseEntity<SubmitResponse> submitAnswer(
            @Parameter(description = "정답을 제출할 퀴즈 ID", required = true, example = "1")
            @PathVariable Long quizId,

            @RequestBody @Valid SubmitRequest request,

            Principal principal
    ) {
        /* JWT에서 userId 추출 — 미인증 시 BusinessException(UNAUTHORIZED) 발생 */
        String userId = resolveUserId(principal);

        log.info("퀴즈 정답 제출: userId={}, quizId={}", userId, quizId);

        SubmitResponse response = quizService.submitAnswer(userId, quizId, request);
        return ResponseEntity.ok(response);
    }
}
