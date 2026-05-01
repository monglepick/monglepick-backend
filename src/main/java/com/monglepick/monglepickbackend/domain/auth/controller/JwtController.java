package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.UserInfo;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService.JwtRefreshResult;
import com.monglepick.monglepickbackend.domain.user.entity.User;
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
 * 사용자 OAuth2 쿠키→헤더 교환과 사용자 Refresh Token 갱신을 처리한다.
 * 관리자 Refresh Token 갱신은 {@code /api/v1/admin/auth/refresh}를 사용한다.</p>
 *
 * <h3>쿠키 보안 정책</h3>
 * <p>모든 엔드포인트에서 Refresh Token은 HttpOnly 쿠키로만 읽고/설정하며,
 * JSON body에는 포함하지 않는다 (XSS 방어). Access Token만 body로 반환한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>POST /jwt/exchange — OAuth2 쿠키 Refresh Token → 새 토큰 쌍 (쿠키+body)</li>
 *   <li>POST /jwt/refresh — 사용자 쿠키 Refresh Token → 새 토큰 쌍 (쿠키+body)</li>
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
     * <p>Refresh Token은 쿠키로만 전달하므로 body에는 accessToken만 포함한다.
     * 단순 토큰 로테이션({@code POST /jwt/refresh}) 응답에서 사용된다 — 호출 시점에는
     * 클라이언트가 이미 user 객체를 보유하므로 별도 동봉이 불필요하다.</p>
     *
     * @param accessToken 새로 발급된 Access Token
     */
    public record RefreshResponseBody(String accessToken) {
    }

    /**
     * OAuth2 쿠키 → JWT 교환 응답 DTO.
     *
     * <p>2026-04-29 회귀 픽스 — 소셜 로그인 직후 호출되는 {@code POST /jwt/exchange}
     * 응답에서 user 객체가 누락돼 있던 결과 클라이언트 {@code useAuthStore.user.id}
     * 가 비어 있는 채로 저장됐고, PointPage 등의 데이터 로더가 모두
     * {@code if (!user?.id) return;} 가드에 막혀 잔액/상점/내 아이템/이력/응모/출석/리워드
     * 진행 현황이 전혀 표시되지 않던 증상을 해소한다.</p>
     *
     * <p>응답 형태를 로컬 로그인({@code LoginSuccessHandler}) 의 {@code LoginResponse}
     * 와 동일하게 맞춰 클라이언트가 어떤 인증 경로를 거치든 동일한 user 객체를
     * 받도록 단일 진실 원본을 회복한다.</p>
     *
     * @param accessToken 새로 발급된 Access Token
     * @param user        사용자 요약 정보 (id/email/nickname/profileImage/provider/role)
     */
    public record ExchangeResponseBody(String accessToken, UserInfo user) {
    }

    /**
     * OAuth2 쿠키→헤더 교환.
     *
     * <p>소셜 로그인 성공 후 SocialSuccessHandler가 설정한
     * HttpOnly 쿠키의 Refresh Token을 읽어 새 토큰 쌍을 발급한다.
     * 새 Refresh Token은 HttpOnly 쿠키로 갱신하고,
     * Access Token만 JSON body로 반환한다.
     * 클라이언트의 /cookie 페이지에서 즉시 호출해야 한다.</p>
     *
     * @param request  HTTP 요청 (refreshToken 쿠키 포함)
     * @param response HTTP 응답 (새 refreshToken 쿠키 설정)
     * @return 200 OK + RefreshResponseBody (accessToken만 body, refreshToken은 쿠키)
     */
    @Operation(
            summary = "소셜 로그인 토큰 교환",
            description = "OAuth2 성공 후 HttpOnly 쿠키의 Refresh Token으로 새 토큰 쌍 발급. "
                    + "새 Refresh Token은 HttpOnly 쿠키로 갱신, Access Token만 body 반환"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 교환 성공"),
            @ApiResponse(responseCode = "401", description = "쿠키 없음 또는 유효하지 않은 Refresh Token")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/exchange")
    public ResponseEntity<ExchangeResponseBody> exchange(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /jwt/exchange — OAuth2 쿠키 Refresh Token 교환");

        /* 1. 쿠키에서 Refresh Token 추출 */
        String refreshToken = cookieUtil.extractRefreshToken(request);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_FOUND, "refreshToken 쿠키가 없습니다.");
        }

        /* 2. 토큰 로테이션: 기존 토큰 무효화 + 새 토큰 쌍 발급
         * 결과에는 user 엔티티도 함께 담겨 오므로 추가 DB 조회 없이 응답에 매핑 가능. */
        JwtRefreshResult result = jwtService.refreshRotate(refreshToken);

        /* 3. 새 Refresh Token을 HttpOnly 쿠키로 갱신 */
        cookieUtil.addRefreshTokenCookie(response, result.newRefreshToken());

        /* 4. user 엔티티 → UserInfo DTO 매핑 (LoginSuccessHandler 와 동일 형태)
         *    필드 순서·이름은 LoginSuccessHandler.UserInfo 및 AuthDto.UserInfo 와 일치.
         *    클라이언트(useAuthStore) 는 어떤 로그인 경로를 거쳐도 동일한 user 객체를
         *    저장하게 되어 PointPage 의 user.id 가드가 항상 만족된다. */
        User user = result.user();
        UserInfo userInfo = new UserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getProvider().name(),
                user.getUserRole().name()
        );

        /* 5. accessToken + user 동봉 응답 */
        return ResponseEntity.ok(new ExchangeResponseBody(result.newAccessToken(), userInfo));
    }

    /**
     * 사용자 Refresh Token 갱신 (토큰 로테이션).
     *
     * <p>HttpOnly 쿠키에서 기존 사용자 Refresh Token을 읽어 새 토큰 쌍으로 교환한다.
     * 기존 토큰은 DB 화이트리스트에서 삭제되어 재사용 불가능하다.
     * 새 Refresh Token은 HttpOnly 쿠키로 갱신하고,
     * Access Token만 JSON body로 반환한다. 관리자 계정은 이 엔드포인트를 사용할 수 없고
     * {@code /api/v1/admin/auth/refresh}를 사용해야 한다.</p>
     *
     * @param request  HTTP 요청 (refreshToken 쿠키 포함)
     * @param response HTTP 응답 (새 refreshToken 쿠키 설정)
     * @return 200 OK + RefreshResponseBody (accessToken만 body, refreshToken은 쿠키)
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

        /* 4. Access Token만 body로 반환 */
        return ResponseEntity.ok(new RefreshResponseBody(result.newAccessToken()));
    }
}
