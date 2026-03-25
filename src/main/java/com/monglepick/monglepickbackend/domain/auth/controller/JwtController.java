package com.monglepick.monglepickbackend.domain.auth.controller;

import com.monglepick.monglepickbackend.domain.auth.dto.JwtResponseDto;
import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT 토큰 관리 컨트롤러.
 *
 * <p>KMG 프로젝트의 JwtController 패턴을 적용.
 * OAuth2 쿠키→헤더 교환과 Refresh Token 갱신을 처리한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>POST /jwt/exchange — OAuth2 쿠키를 헤더 기반 JWT로 교환</li>
 *   <li>POST /jwt/refresh — Refresh Token으로 새 토큰 쌍 발급 (토큰 로테이션)</li>
 * </ul>
 */
@Tag(name = "JWT 토큰", description = "쿠키→헤더 교환, Refresh Token 갱신")
@Slf4j
@RestController
@RequestMapping("/jwt")
@RequiredArgsConstructor
public class JwtController {

    private final JwtService jwtService;

    /**
     * OAuth2 쿠키→헤더 교환.
     *
     * <p>소셜 로그인 성공 후 SocialSuccessHandler가 설정한
     * HttpOnly 쿠키의 Refresh Token을 JSON 기반 JWT로 교환한다.
     * 클라이언트의 /cookie 페이지에서 즉시 호출해야 한다.</p>
     *
     * @return 200 OK + JwtResponseDto (accessToken, refreshToken, userNickname)
     */
    @Operation(
            summary = "소셜 로그인 토큰 교환",
            description = "OAuth2 성공 후 HttpOnly 쿠키의 Refresh Token을 JSON 기반 JWT로 교환"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 교환 성공"),
            @ApiResponse(responseCode = "400", description = "쿠키 없음 또는 유효하지 않은 토큰")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/exchange")
    public ResponseEntity<JwtResponseDto> exchange(HttpServletRequest request, HttpServletResponse response) {
        log.info("POST /jwt/exchange — OAuth2 쿠키→헤더 교환");

        JwtResponseDto dto = jwtService.cookie2Header(request, response);
        return ResponseEntity.ok(dto);
    }

    /**
     * Refresh Token 갱신 (토큰 로테이션).
     *
     * <p>기존 Refresh Token을 새 토큰 쌍으로 교환한다.
     * 기존 토큰은 DB 화이트리스트에서 삭제되어 재사용 불가능하다.</p>
     *
     * @param request refreshToken이 포함된 요청 Body
     * @return 200 OK + JwtResponseDto (새로운 accessToken, refreshToken, userNickname)
     */
    @Operation(
            summary = "Access Token 갱신 (Rotation)",
            description = "기존 Refresh Token으로 새 Access/Refresh Token 쌍 발급. 기존 토큰은 무효화됨"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token")
    })
    @SecurityRequirement(name = "")
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponseDto> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("POST /jwt/refresh — Refresh Token 갱신");

        JwtResponseDto dto = jwtService.refreshRotate(request.refreshToken());
        return ResponseEntity.ok(dto);
    }

    /** Refresh Token 갱신 요청 DTO */
    public record RefreshRequest(
            @NotBlank(message = "refreshToken은 필수입니다")
            String refreshToken
    ) {
    }
}
