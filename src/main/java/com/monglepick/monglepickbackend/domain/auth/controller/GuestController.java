package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaCheckResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaConsumeResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaRequest;
import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestTokenResponse;
import com.monglepick.monglepickbackend.domain.auth.service.GuestQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

/**
 * 비로그인(게스트) 사용자의 AI 추천 무료 체험 쿠키 발급 / 쿼터 체크 / 소비 컨트롤러.
 *
 * <h3>엔드포인트 3종</h3>
 * <ul>
 *   <li>{@code POST /api/v1/guest/token} — Public.
 *       Client 는 앱 최초 진입 시 비로그인 상태면 한 번 호출하여 HttpOnly 쿠키 {@code mongle_guest} 를 수신.</li>
 *   <li>{@code POST /api/v1/guest/quota/check} — ServiceKey. Agent 가 채팅 시작 전에 게스트 여부 + 사용 기록을 조회.</li>
 *   <li>{@code POST /api/v1/guest/quota/consume} — ServiceKey. Agent 가 첫 추천 카드를 노출하는 시점에 원자적으로 소비.</li>
 * </ul>
 *
 * <h3>차감 시점 — Agent graph.py recommendation_ranker 기준</h3>
 * <p>로그인 유저의 {@code consumePoint} 와 동일한 위치에서 호출된다.
 * 질문만 하고 이탈하면 차감되지 않음 (UX 배려).</p>
 *
 * @see GuestQuotaService
 */
@Tag(name = "게스트 쿼터", description = "비로그인 사용자의 AI 추천 무료 체험 (쿠키+IP 평생 1회)")
@Slf4j
@RestController
@RequestMapping("/api/v1/guest")
@RequiredArgsConstructor
public class GuestController {

    /** 게스트 식별 쿠키 이름 — 서버/클라이언트 공통 상수. */
    public static final String GUEST_COOKIE_NAME = "mongle_guest";

    /** 쿠키 유효기간 — 365일 (평생 1회 정책과 동기화). */
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    private final GuestQuotaService guestQuotaService;

    /**
     * 쿠키 Secure 플래그 — HTTPS 전용 여부.
     * 개발(HTTP): false, 운영(HTTPS): true (APP_COOKIE_SECURE=true 환경변수).
     */
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    // ════════════════════════════════════════════════════════════════
    // 1. 쿠키 발급 — Public
    // ════════════════════════════════════════════════════════════════

    /**
     * 게스트 쿠키 발급/재확인.
     *
     * <p>이미 유효한 쿠키가 있으면 재발급하지 않고 기존 guestId 를 그대로 돌려준다.
     * 쿠키가 없거나 서명이 깨졌으면 새 UUID 로 발급한다.</p>
     *
     * <p>{@code used} 필드로 현재 쿼터 상태를 함께 노출하여 Client 가
     * "이미 소비된 유저" 를 UI 에서 미리 회색 처리할 수 있게 한다.</p>
     *
     * @param request  기존 쿠키 확인용
     * @param response 쿠키 세팅 대상
     * @return {@link GuestTokenResponse} — guestId + used
     */
    @Operation(
            summary = "게스트 쿠키 발급",
            description = "앱 진입 시 비로그인 상태면 1회 호출. HttpOnly 쿠키 mongle_guest 를 내려준다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "쿠키 발급 성공 (신규 또는 재확인)")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/token")
    public ResponseEntity<GuestTokenResponse> issueGuestToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        /* ① 기존 쿠키 조회 + 서명 검증 */
        String existingGuestId = extractAndVerifyExistingCookie(request);

        /* ② 검증 통과 시 재발급 없이 그대로 사용 — 새 쿠키는 set 하지 않음 (불필요한 Set-Cookie 절감) */
        String guestId;
        if (existingGuestId != null) {
            guestId = existingGuestId;
            log.debug("게스트 쿠키 재사용: guestId={}", guestId);
        } else {
            /* ③ 신규 발급 — UUID + HMAC 서명 + Set-Cookie */
            String cookieValue = guestQuotaService.issueNewCookieValue();
            guestId = cookieValue.split("\\.", 2)[0];
            addGuestCookie(response, cookieValue);
            log.info("게스트 쿠키 신규 발급: guestId={}", guestId);
        }

        /* ④ 현재 쿼터 소비 여부도 같이 반환 — Client 가 UI 에서 참고 */
        String clientIp = extractClientIp(request);
        GuestQuotaCheckResponse quota = guestQuotaService.checkQuota(guestId, clientIp);

        return ResponseEntity.ok(new GuestTokenResponse(guestId, !quota.allowed()));
    }

    // ════════════════════════════════════════════════════════════════
    // 2. 쿼터 체크 — ServiceKey (Agent 전용)
    // ════════════════════════════════════════════════════════════════

    /**
     * Agent → Backend 게스트 쿼터 체크.
     *
     * <p>소비하지 않고 조회만 한다. Agent 가 채팅 SSE 진입 초기에 호출하여
     * 차단 상태면 즉시 {@code error: GUEST_QUOTA_EXCEEDED} 이벤트를 흘려보낸다.</p>
     *
     * @param req guestId + clientIp
     * @return allowed + reason
     */
    @Operation(
            summary = "[Agent 전용] 게스트 쿼터 체크",
            description = "X-Service-Key 헤더 필수. 소비하지 않음 (조회 전용)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "체크 완료 (allowed=true/false)"),
            @ApiResponse(responseCode = "401", description = "X-Service-Key 불일치")
    })
    @PostMapping("/quota/check")
    public ResponseEntity<GuestQuotaCheckResponse> checkGuestQuota(
            @Valid @RequestBody GuestQuotaRequest req
    ) {
        log.debug("게스트 쿼터 체크 요청: guestId={}, clientIp={}", req.guestId(), req.clientIp());
        GuestQuotaCheckResponse resp = guestQuotaService.checkQuota(req.guestId(), req.clientIp());
        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. 쿼터 소비 — ServiceKey (Agent 전용)
    // ════════════════════════════════════════════════════════════════

    /**
     * Agent → Backend 게스트 쿼터 소비.
     *
     * <p>{@code recommendation_ranker} 완료 직후, 첫 {@code movie_card} yield 직전에 호출된다.
     * 로그인 유저의 {@code POST /api/v1/point/consume} 와 동일한 위치.</p>
     *
     * <p>이미 소비된 상태면 success=false + reason 을 반환하되 409 가 아닌 200 으로 응답한다
     * (Agent 쪽에서 예외 처리 복잡도 감소).</p>
     *
     * @param req guestId + clientIp
     * @return success + reason
     */
    @Operation(
            summary = "[Agent 전용] 게스트 쿼터 소비",
            description = "X-Service-Key 헤더 필수. SETNX 로 원자적 소비. 이미 소비된 상태면 success=false 반환."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "소비 처리 완료 (신규/중복 모두 200)"),
            @ApiResponse(responseCode = "401", description = "X-Service-Key 불일치")
    })
    @PostMapping("/quota/consume")
    public ResponseEntity<GuestQuotaConsumeResponse> consumeGuestQuota(
            @Valid @RequestBody GuestQuotaRequest req
    ) {
        log.debug("게스트 쿼터 소비 요청: guestId={}, clientIp={}", req.guestId(), req.clientIp());
        GuestQuotaConsumeResponse resp = guestQuotaService.consumeQuota(req.guestId(), req.clientIp());
        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════════════════════════
    // 내부 유틸
    // ════════════════════════════════════════════════════════════════

    /**
     * 요청에서 {@code mongle_guest} 쿠키를 찾아 서명 검증 후 guestId 를 반환한다.
     *
     * @return 유효한 guestId, 없거나 검증 실패 시 null
     */
    private String extractAndVerifyExistingCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> GUEST_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .map(guestQuotaService::parseAndVerifyCookie)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 응답에 게스트 HttpOnly 쿠키를 세팅한다.
     *
     * <p>Refresh Token 쿠키와 정책이 동일하지만 만료(365일) 와 이름이 다르므로
     * {@link com.monglepick.monglepickbackend.global.security.CookieUtil} 을 재사용하지 않고
     * 여기서 직접 {@link ResponseCookie} 로 생성한다.</p>
     *
     * @param response    HTTP 응답
     * @param cookieValue {@code {guestId}.{signature}} 서명된 쿠키 값
     */
    private void addGuestCookie(HttpServletResponse response, String cookieValue) {
        ResponseCookie cookie = ResponseCookie.from(GUEST_COOKIE_NAME, cookieValue)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE.getSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 클라이언트 실제 IP 추출.
     *
     * <p>Nginx 프록시 환경에서는 {@code X-Forwarded-For} 첫 항목이 원본 IP.
     * 헤더가 없으면 {@code request.getRemoteAddr()} 로 폴백.</p>
     *
     * @return 클라이언트 IP 문자열 (빈 값 방지, 최소 "unknown")
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            /* 콤마 구분 체인의 첫 항목이 실제 클라이언트 */
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : "unknown";
    }
}
