package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.UserInfo;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService.JwtRefreshResult;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT 토큰 관리 컨트롤러.
 *
 * <p>KMG 프로젝트의 JwtController 패턴을 적용.
 * OAuth2 쿠키→헤더 교환과 Refresh Token 갱신을 처리한다.</p>
 *
 * <h3>쿠키 보안 정책</h3>
 * <p>모든 엔드포인트에서 Refresh Token은 HttpOnly 쿠키로만 읽고/설정하며,
 * JSON body에는 포함하지 않는다 (XSS 방어). Access Token만 body로 반환한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>POST /jwt/exchange — OAuth2 쿠키 Refresh Token → 새 토큰 쌍 (쿠키+body)</li>
 *   <li>POST /jwt/refresh — 쿠키 Refresh Token → 새 토큰 쌍 (쿠키+body)</li>
 * </ul>
 */
@Tag(name = "JWT 토큰", description = "쿠키 기반 Refresh Token 교환 및 갱신")
@Slf4j
@RestController
@RequestMapping("/jwt")
@RequiredArgsConstructor
public class JwtController {

    private final JwtService jwtService;

    /**
     * Refresh Token 쿠키 설정/삭제/추출을 담당하는 단일 유틸리티.
     * 모든 인증 흐름에서 쿠키 처리를 일관되게 관리한다.
     */
    private final CookieUtil cookieUtil;

    /**
     * Access Token 갱신 응답 DTO.
     *
     * <p>Refresh Token은 쿠키로만 전달하므로 body에는 포함하지 않는다.
     * OAuth2 쿠키 교환 직후에도 로컬 로그인과 동일하게 사용자 요약 정보를 내려
     * 클라이언트 인증 상태의 user.id가 비지 않도록 한다.</p>
     *
     * @param accessToken 새로 발급된 Access Token
     * @param user        현재 사용자 요약 정보
     */
    public record RefreshResponseBody(String accessToken, UserInfo user) {
    }

    /**
     * OAuth2 쿠키→헤더 교환.
     *
     * <p>소셜 로그인 성공 후 SocialSuccessHandler가 설정한
     * HttpOnly 쿠키의 Refresh Token을 읽어 새 토큰 쌍을 발급한다.
     * 새 Refresh Token은 HttpOnly 쿠키로 갱신하고,
     * Access Token과 사용자 요약 정보를 JSON body로 반환한다.
     * 클라이언트의 /cookie 페이지에서 즉시 호출해야 한다.</p>
     *
     * @param request  HTTP 요청 (refreshToken 쿠키 포함)
     * @param response HTTP 응답 (새 refreshToken 쿠키 설정)
     * @return 200 OK + RefreshResponseBody (accessToken + user body, refreshToken은 쿠키)
     */
    @Operation(
            summary = "소셜 로그인 토큰 교환",
            description = "OAuth2 성공 후 HttpOnly 쿠키의 Refresh Token으로 새 토큰 쌍 발급. "
                    + "새 Refresh Token은 HttpOnly 쿠키로 갱신, Access Token과 사용자 정보는 body 반환"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 교환 성공"),
            @ApiResponse(responseCode = "401", description = "쿠키 없음 또는 유효하지 않은 Refresh Token")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/exchange")
    public ResponseEntity<RefreshResponseBody> exchange(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /jwt/exchange — OAuth2 쿠키 Refresh Token 교환");

        /* 1. 쿠키에서 Refresh Token 추출 */
        String refreshToken = cookieUtil.extractRefreshToken(request);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_FOUND, "refreshToken 쿠키가 없습니다.");
        }

        /* 2. 토큰 로테이션: 기존 토큰 무효화 + 새 토큰 쌍 발급 */
        JwtRefreshResult result = jwtService.refreshRotate(refreshToken);

        /* 3. 새 Refresh Token을 HttpOnly 쿠키로 갱신 */
        cookieUtil.addRefreshTokenCookie(response, result.newRefreshToken());

        /* 4. Access Token + 사용자 정보만 body로 반환 */
        return ResponseEntity.ok(new RefreshResponseBody(result.newAccessToken(), result.user()));
    }

    /**
     * Refresh Token 갱신 (토큰 로테이션).
     *
     * <p>HttpOnly 쿠키에서 기존 Refresh Token을 읽어 새 토큰 쌍으로 교환한다.
     * 기존 토큰은 DB 화이트리스트에서 삭제되어 재사용 불가능하다.
     * 새 Refresh Token은 HttpOnly 쿠키로 갱신하고,
     * Access Token과 사용자 요약 정보를 JSON body로 반환한다.</p>
     *
     * @param request  HTTP 요청 (refreshToken 쿠키 포함)
     * @param response HTTP 응답 (새 refreshToken 쿠키 설정)
     * @return 200 OK + RefreshResponseBody (accessToken + user body, refreshToken은 쿠키)
     */
    @Operation(
            summary = "Access Token 갱신 (Rotation)",
            description = "HttpOnly 쿠키의 Refresh Token으로 새 Access/Refresh Token 쌍 발급. "
                    + "기존 Refresh Token은 무효화되고 새 쿠키로 갱신됨"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @ApiResponse(responseCode = "401", description = "쿠키 없음 또는 유효하지 않은 Refresh Token")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponseBody> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /jwt/refresh — 쿠키 기반 Refresh Token 갱신");

        /* 1. 쿠키에서 Refresh Token 추출 */
        String refreshToken = cookieUtil.extractRefreshToken(request);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_FOUND, "refreshToken 쿠키가 없습니다.");
        }

        /* 2. 토큰 로테이션: 기존 토큰 무효화 + 새 토큰 쌍 발급 */
        JwtRefreshResult result = jwtService.refreshRotate(refreshToken);

        /* 3. 새 Refresh Token을 HttpOnly 쿠키로 갱신 */
        cookieUtil.addRefreshTokenCookie(response, result.newRefreshToken());

        /* 4. Access Token + 사용자 정보만 body로 반환 */
        return ResponseEntity.ok(new RefreshResponseBody(result.newAccessToken(), result.user()));
    }
}
