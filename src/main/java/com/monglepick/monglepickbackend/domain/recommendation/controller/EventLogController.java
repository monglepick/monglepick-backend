package com.monglepick.monglepickbackend.domain.recommendation.controller;

import com.monglepick.monglepickbackend.domain.recommendation.dto.EventLogRequest;
import com.monglepick.monglepickbackend.domain.recommendation.service.EventLogService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 이벤트 로그 컨트롤러 — 사용자 행동 이벤트 기록 REST API.
 *
 * <p>클라이언트(monglepick-client) 및 AI Agent(monglepick-agent)가
 * 사용자의 클릭·조회·스킵·검색 등 행동 이벤트를 백엔드에 기록한다.
 * 수집된 이벤트는 추천 알고리즘 개선과 사용자 행동 분석에 활용된다.</p>
 *
 * <h3>인증</h3>
 * <ul>
 *   <li>클라이언트 호출: JWT Bearer 토큰</li>
 *   <li>Agent 내부 호출: X-Service-Key 헤더 + Body의 {@code userId} 필드</li>
 * </ul>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code POST /api/v1/events}       — 단건 이벤트 저장</li>
 *   <li>{@code POST /api/v1/events/batch} — 배치 이벤트 저장 (최대 100건 권장)</li>
 * </ul>
 */
@Tag(name = "이벤트 로깅", description = "사용자 행동 이벤트(클릭·조회·스킵·검색 등) 기록 API")
@RestController
@RequestMapping("/api/v1/events")
@Slf4j
@RequiredArgsConstructor
public class EventLogController extends BaseController {

    /** 이벤트 로그 서비스 (단건/배치 저장 비즈니스 로직) */
    private final EventLogService eventLogService;

    /**
     * 단건 이벤트를 기록한다.
     *
     * <p>영화 클릭, 상세 조회, 예고편 재생, 평가 등
     * 실시간으로 발생하는 단일 행동 이벤트를 즉시 저장한다.</p>
     *
     * <h3>요청 예시</h3>
     * <pre>{@code
     * POST /api/v1/events
     * Authorization: Bearer {accessToken}
     *
     * {
     *   "eventType": "click",
     *   "movieId": "tt0111161",
     *   "recommendScore": 0.92,
     *   "metadata": "{\"source\":\"chat\",\"session_id\":\"abc-123\",\"position\":1}"
     * }
     * }</pre>
     *
     * @param principal 인증된 사용자 정보 (JWT 또는 ServiceKey)
     * @param request   이벤트 상세 정보
     * @return 201 Created (응답 바디 없음)
     */
    @Operation(
            summary = "단건 이벤트 저장",
            description = "사용자 행동 이벤트 1건을 기록한다. " +
                    "eventType은 필수이며 click·view·hover·search·trailer_play·skip·rate·recommend 등을 사용한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "이벤트 저장 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 (eventType 누락 또는 형식 오류)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (JWT 없음 또는 만료)")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public ResponseEntity<Void> logEvent(
            Principal principal,
            @RequestBody @Valid EventLogRequest request) {

        // JWT → principal.getName(), ServiceKey → resolveUserIdWithServiceKey 내부에서 처리
        // 이벤트 로그는 클라이언트 단독 호출이 대부분이므로 JWT userId를 우선 사용한다.
        // Agent 호출 시에도 동일 엔드포인트를 사용하며, ServiceKey 인증이면 body에 userId 포함이 필요하다.
        // 현재 EventLogRequest에는 userId 필드가 없으므로 JWT 전용으로 처리하고,
        // Agent 내부 호출이 필요해질 경우 별도 Internal 엔드포인트로 분리한다.
        String userId = resolveUserId(principal);

        log.info("POST /api/v1/events — userId={}, eventType={}, movieId={}",
                userId, request.eventType(), request.movieId());

        eventLogService.logEvent(userId, request);

        // 저장 성공: 201 Created, 응답 바디 없음
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 여러 이벤트를 배치로 기록한다.
     *
     * <p>스크롤, 호버 등 빈도가 높은 이벤트를 클라이언트에서 모아
     * 한 번에 전송하여 HTTP 오버헤드를 줄인다. 최대 100건 권장.</p>
     *
     * <h3>요청 예시</h3>
     * <pre>{@code
     * POST /api/v1/events/batch
     * Authorization: Bearer {accessToken}
     *
     * [
     *   {"eventType": "hover", "movieId": "tt0111161"},
     *   {"eventType": "view",  "movieId": "tt0068646", "recommendScore": 0.88},
     *   {"eventType": "skip",  "movieId": "tt0071562"}
     * ]
     * }</pre>
     *
     * @param principal 인증된 사용자 정보 (JWT 또는 ServiceKey)
     * @param requests  이벤트 목록 (비어 있으면 no-op)
     * @return 201 Created (응답 바디 없음)
     */
    @Operation(
            summary = "배치 이벤트 저장",
            description = "사용자 행동 이벤트 여러 건을 한 번에 기록한다. " +
                    "스크롤·호버 등 빈도 높은 이벤트를 모아서 전송할 때 사용한다. 최대 100건 권장."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "배치 저장 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 (항목 중 eventType 누락)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (JWT 없음 또는 만료)")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/batch")
    public ResponseEntity<Void> logEventsBatch(
            Principal principal,
            @RequestBody @Valid List<EventLogRequest> requests) {

        String userId = resolveUserId(principal);

        log.info("POST /api/v1/events/batch — userId={}, count={}", userId, requests.size());

        eventLogService.logEventsBatch(userId, requests);

        // 저장 성공: 201 Created, 응답 바디 없음
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
