package com.monglepick.monglepickbackend.domain.reward.controller;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.BalanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.CheckRequest;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.CheckResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductRequest;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnRequest;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.ExchangeResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.HistoryResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.PointItemResponse;
import com.monglepick.monglepickbackend.domain.reward.service.AttendanceService;
import com.monglepick.monglepickbackend.domain.reward.service.PointItemService;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.ServiceKeyAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 포인트 컨트롤러 — 포인트 시스템 REST API 엔드포인트.
 *
 * <p>AI Agent(monglepick-agent) 내부 호출과 클라이언트(monglepick-client) 호출을
 * 모두 처리한다. 모든 응답은 DTO를 직접 반환한다 (ApiResponse 래퍼 미사용).</p>
 *
 * <h3>인증</h3>
 * <ul>
 *   <li>Agent 호출 (check/deduct/earn): X-Service-Key 헤더</li>
 *   <li>클라이언트 호출: JWT Bearer 토큰</li>
 * </ul>
 */
@Tag(name = "포인트", description = "잔액 조회, 적립/차감, 출석 체크, 아이템 교환")
@RestController
@RequestMapping("/api/v1/point")
@Slf4j
@RequiredArgsConstructor
public class PointController {

    /** 포인트 서비스 (잔액 확인/차감/획득/이력 비즈니스 로직) */
    private final PointService pointService;

    /** 출석 체크 서비스 (출석/연속 출석/보너스 포인트 지급) */
    private final AttendanceService attendanceService;

    /** 포인트 아이템 서비스 (아이템 조회/교환) */
    private final PointItemService pointItemService;

    /**
     * 인증 정보에서 userId를 추출한다.
     *
     * <p>JWT 인증: principal.getName() = userId (JwtAuthenticationFilter가 설정)
     * ServiceKey 인증: principal.getName() = "service" → requestUserId 파라미터 사용</p>
     *
     * @param principal     인증된 사용자 정보
     * @param requestUserId 요청에 포함된 userId (Agent 호출 시 사용, nullable)
     * @return 확인된 사용자 ID
     * @throws BusinessException 인증 정보가 없거나, ServiceKey 호출인데 userId가 누락된 경우
     */
    private String resolveUserId(Principal principal, String requestUserId) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String principalName = principal.getName();

        // ServiceKey 인증: Agent가 요청 파라미터로 userId를 전달
        if (ServiceKeyAuthFilter.SERVICE_PRINCIPAL.equals(principalName)) {
            if (requestUserId == null || requestUserId.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "서비스 호출 시 userId는 필수입니다");
            }
            return requestUserId;
        }

        // JWT 인증: 토큰에서 추출한 userId 사용
        return principalName;
    }

    // ──────────────────────────────────────────────
    // Agent 내부 호출 엔드포인트
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 확인 (Agent → Backend).
     *
     * <p>AI Agent가 추천 실행 전에 사용자의 포인트 잔액이 충분한지 확인한다.</p>
     *
     * @param request 잔액 확인 요청 (userId, cost)
     * @return 200 OK + CheckResponse (잔액 충분 여부, 등급별 글자 수 제한 포함)
     */
    @Operation(summary = "포인트 잔액 확인", description = "AI Agent가 추천 실행 전 사용자 포인트 충분 여부 확인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 (allowed=true/false)"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @SecurityRequirement(name = "ServiceKeyAuth")
    @PostMapping("/check")
    public ResponseEntity<CheckResponse> checkPoint(
            Principal principal,
            @Valid @RequestBody CheckRequest request) {
        // ServiceKey(Agent) → request.userId() 사용, JWT → principal에서 추출 (BOLA 방지)
        String userId = resolveUserId(principal, request.userId());
        log.debug("POST /api/v1/point/check — userId={}, cost={}", userId, request.cost());

        CheckResponse response = pointService.checkPoint(userId, request.cost());
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 차감 (Agent → Backend).
     *
     * <p>AI Agent가 추천 완료 후 포인트를 차감한다. 비관적 락으로 동시성을 보장한다.</p>
     *
     * @param request 차감 요청 (userId, amount, sessionId, description)
     * @return 200 OK + DeductResponse (차감 후 잔액, 이력 ID)
     */
    @Operation(summary = "포인트 차감", description = "AI Agent가 추천 완료 후 포인트 차감. 비관적 락으로 동시성 보장")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "차감 성공"),
            @ApiResponse(responseCode = "402", description = "포인트 부족")
    })
    @SecurityRequirement(name = "ServiceKeyAuth")
    @PostMapping("/deduct")
    public ResponseEntity<DeductResponse> deductPoint(
            Principal principal,
            @Valid @RequestBody DeductRequest request) {
        // ServiceKey(Agent) → request.userId() 사용, JWT → principal에서 추출 (BOLA 방지)
        String userId = resolveUserId(principal, request.userId());
        log.info("POST /api/v1/point/deduct — userId={}, amount={}, sessionId={}",
                userId, request.amount(), request.sessionId());

        DeductResponse response = pointService.deductPoint(
                userId,
                request.amount(),
                request.sessionId(),
                request.description()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 획득 (내부 서비스 호출).
     *
     * <p>출석 체크, 퀴즈 보상, 이벤트 보너스 등 내부 서비스에서
     * 포인트를 지급할 때 호출한다. 등급 재계산이 자동으로 수행된다.</p>
     *
     * @param request 획득 요청 (userId, amount, pointType, description, referenceId)
     * @return 200 OK + EarnResponse (획득 후 잔액, 등급)
     */
    @Operation(summary = "포인트 획득", description = "내부 서비스에서 포인트 지급 (출석/퀴즈/이벤트 보상)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "포인트 지급 성공"),
            @ApiResponse(responseCode = "404", description = "포인트 레코드 없음")
    })
    @SecurityRequirement(name = "ServiceKeyAuth")
    @PostMapping("/earn")
    public ResponseEntity<EarnResponse> earnPoint(
            Principal principal,
            @Valid @RequestBody EarnRequest request) {
        // ServiceKey(Agent) → request.userId() 사용, JWT → principal에서 추출 (BOLA 방지)
        String userId = resolveUserId(principal, request.userId());
        log.info("POST /api/v1/point/earn — userId={}, amount={}, type={}",
                userId, request.amount(), request.pointType());

        EarnResponse response = pointService.earnPoint(
                userId,
                request.amount(),
                request.pointType(),
                request.description(),
                request.referenceId()
        );
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 내부 + 클라이언트 겸용 엔드포인트
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 조회 (Agent + 클라이언트 겸용).
     *
     * <p>사용자의 현재 포인트 잔액, 등급, 누적 획득 포인트를 조회한다.</p>
     *
     * @param userId 사용자 ID
     * @return 200 OK + BalanceResponse (잔액, 등급, 누적 획득)
     */
    @Operation(summary = "포인트 잔액 조회", description = "현재 포인트 잔액, 등급, 누적 획득 포인트 조회")
    @ApiResponse(responseCode = "200", description = "잔액 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            Principal principal,
            @RequestParam(required = false) String userId) {
        String resolvedUserId = resolveUserId(principal, userId);
        log.debug("GET /api/v1/point/balance — userId={}", resolvedUserId);

        BalanceResponse response = pointService.getBalance(resolvedUserId);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 클라이언트 전용 엔드포인트
    // ──────────────────────────────────────────────

    /**
     * 포인트 변동 이력 조회 (클라이언트 전용).
     *
     * <p>사용자의 포인트 변동 내역을 최신순으로 페이징 조회한다.</p>
     *
     * @param page 페이지 번호 (0-based, 기본값 0)
     * @param size 페이지 크기 (기본값 20)
     * @return 200 OK + Page of HistoryResponse (변동 이력 페이지)
     */
    @Operation(summary = "포인트 변동 이력 조회", description = "포인트 변동 내역을 최신순 페이징 조회 (최대 100건)")
    @ApiResponse(responseCode = "200", description = "이력 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/history")
    public ResponseEntity<Page<HistoryResponse>> getHistory(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = resolveUserId(principal, null);
        // 페이지 크기 상한 100으로 제한 (대량 조회 방지)
        int safeSize = Math.min(size, 100);
        log.debug("GET /api/v1/point/history — userId={}, page={}, size={}", userId, page, safeSize);

        Page<HistoryResponse> response = pointService.getHistory(userId, PageRequest.of(page, safeSize));
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 출석 체크 엔드포인트 (클라이언트 전용)
    // ──────────────────────────────────────────────

    /**
     * 출석 체크 수행 (클라이언트 전용).
     *
     * <p>사용자의 오늘 출석을 기록하고, 연속 출석일(streak)에 따른
     * 보너스 포인트를 지급한다.</p>
     *
     * @return 200 OK + AttendanceResponse (출석일, 연속일수, 획득 포인트, 잔액)
     */
    @Operation(summary = "출석 체크", description = "오늘 출석 기록 + 연속 출석 보너스 포인트 지급")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "출석 체크 성공"),
            @ApiResponse(responseCode = "409", description = "오늘 이미 출석 완료")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceResponse> checkIn(Principal principal) {
        String userId = resolveUserId(principal, null);
        log.info("POST /api/v1/point/attendance — userId={}", userId);

        AttendanceResponse response = attendanceService.checkIn(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 출석 현황 조회 (클라이언트 전용).
     *
     * <p>사용자의 현재 연속 출석일, 총 출석일, 오늘 출석 여부,
     * 이번 달 출석 날짜 목록을 조회한다.</p>
     *
     * @return 200 OK + AttendanceStatusResponse (연속일수, 총일수, 오늘출석여부, 월간날짜목록)
     */
    @Operation(summary = "출석 현황 조회", description = "연속 출석일, 총 출석일, 오늘 출석 여부, 월간 출석 날짜")
    @ApiResponse(responseCode = "200", description = "현황 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/attendance/status")
    public ResponseEntity<AttendanceStatusResponse> getAttendanceStatus(Principal principal) {
        String userId = resolveUserId(principal, null);
        log.debug("GET /api/v1/point/attendance/status — userId={}", userId);

        AttendanceStatusResponse response = attendanceService.getStatus(userId);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 포인트 아이템 엔드포인트 (클라이언트 전용)
    // ──────────────────────────────────────────────

    /**
     * 포인트 아이템 목록 조회 (클라이언트 전용).
     *
     * <p>활성 상태인 포인트 아이템 목록을 조회한다.</p>
     *
     * @param category 아이템 카테고리 (선택, 예: "general", "coupon", "avatar", "ai")
     * @return 200 OK + 아이템 목록 (가격 오름차순, 없으면 빈 리스트)
     */
    @Operation(summary = "포인트 아이템 목록", description = "활성 포인트 아이템 목록 조회. category 파라미터로 필터링 가능")
    @ApiResponse(responseCode = "200", description = "아이템 목록 조회 성공")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/items")
    public ResponseEntity<List<PointItemResponse>> getItems(
            @RequestParam(required = false) String category) {
        log.debug("GET /api/v1/point/items — category={}", category);

        if (category != null) {
            return ResponseEntity.ok(pointItemService.getItemsByCategory(category));
        }
        return ResponseEntity.ok(pointItemService.getActiveItems());
    }

    /**
     * 포인트 아이템 교환 (클라이언트 전용).
     *
     * <p>사용자의 보유 포인트로 지정된 아이템을 교환(구매)한다.</p>
     *
     * @param itemId 교환 대상 아이템 ID
     * @return 200 OK + ExchangeResponse (성공 여부, 잔액, 아이템명)
     */
    @Operation(summary = "포인트 아이템 교환", description = "보유 포인트로 아이템 교환(구매). 포인트 부족 시 에러 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "아이템 교환 성공"),
            @ApiResponse(responseCode = "402", description = "포인트 부족"),
            @ApiResponse(responseCode = "404", description = "아이템 미존재 또는 비활성")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/items/{itemId}/exchange")
    public ResponseEntity<ExchangeResponse> exchangeItem(
            Principal principal,
            @PathVariable Long itemId) {
        String userId = resolveUserId(principal, null);
        log.info("POST /api/v1/point/items/{}/exchange — userId={}", itemId, userId);

        ExchangeResponse response = pointItemService.exchangeItem(userId, itemId);
        return ResponseEntity.ok(response);
    }
}
