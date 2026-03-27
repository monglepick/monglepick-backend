package com.monglepick.monglepickbackend.global.config;

import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * 개발 전용 JWT 토큰 발급 컨트롤러.
 *
 * <p>Swagger UI에서 API 테스트 시 JWT 인증 토큰을 간편하게 발급받을 수 있는 엔드포인트를 제공한다.
 * {@code DEV_AUTH_ENABLED=true} 환경변수가 설정되어야만 활성화된다.</p>
 *
 * <h3>사용 방법</h3>
 * <ol>
 *   <li>GET /api/v1/dev/token 호출 (userId, role 파라미터 지정)</li>
 *   <li>응답의 access_token 값을 복사</li>
 *   <li>Swagger UI 상단 "Authorize" 버튼 클릭</li>
 *   <li>BearerAuth 필드에 토큰 붙여넣기</li>
 *   <li>이후 모든 API 호출에 자동으로 JWT 인증 적용</li>
 * </ol>
 *
 * <p><b>⚠ 주의:</b> 운영 환경에서는 반드시 DEV_AUTH_ENABLED=false(기본값)로 유지해야 한다.
 * 이 컨트롤러는 {@code @ConditionalOnProperty}로 보호되어 환경변수가 true가 아니면
 * 빈 자체가 등록되지 않는다.</p>
 *
 * @see JwtTokenProvider
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev.auth.enabled", havingValue = "true")
@Tag(name = "🔧 Dev Auth", description = "개발 전용 JWT 토큰 발급 (DEV_AUTH_ENABLED=true 일 때만 활성화)")
public class DevAuthController {

    /** JWT 토큰 생성을 위한 프로바이더 */
    private final JwtTokenProvider jwtTokenProvider;

    /** 허용 가능한 role 화이트리스트 (임의 권한 토큰 발급 방지) */
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    /**
     * 테스트용 JWT Access Token을 발급한다.
     *
     * <p>Swagger UI에서 API 테스트 시 간편하게 인증 토큰을 얻을 수 있다.
     * 발급된 토큰의 만료 시간은 application.yml의 access-token-expiry 설정을 따른다 (기본 1시간).</p>
     *
     * <p><b>사용 시나리오:</b></p>
     * <ul>
     *   <li>일반 사용자 테스트: {@code ?userId=test-user-1&role=USER}</li>
     *   <li>관리자 테스트: {@code ?userId=admin-1&role=ADMIN}</li>
     *   <li>특정 사용자 ID로 테스트: {@code ?userId=USR_abc123&role=USER}</li>
     * </ul>
     *
     * @param userId 토큰에 포함할 사용자 ID (기본값: "dev-test-user")
     * @param role   토큰에 포함할 사용자 역할 (기본값: "ADMIN")
     * @return access_token, token_type, expires_in, user_id, role 정보를 포함한 JSON
     */
    @GetMapping("/token")
    @SecurityRequirements  // Swagger UI에서 인증 불필요 표시
    @Operation(
            summary = "테스트용 JWT 토큰 발급",
            description = """
                    Swagger UI API 테스트용 JWT Access Token을 발급합니다.

                    **사용법:**
                    1. 이 엔드포인트를 호출하여 토큰을 발급받습니다
                    2. 응답의 `access_token` 값을 복사합니다
                    3. Swagger UI 상단 🔓 **Authorize** 버튼을 클릭합니다
                    4. **BearerAuth** 필드에 토큰을 붙여넣고 **Authorize** 클릭
                    5. 이후 모든 API 호출에 JWT 인증이 자동 적용됩니다

                    ⚠ **개발 환경 전용** — 운영 환경에서는 비활성화됩니다.""",
            responses = {
                    @ApiResponse(responseCode = "200", description = "토큰 발급 성공")
            }
    )
    public ResponseEntity<Map<String, Object>> generateDevToken(
            @Parameter(description = "사용자 ID (JWT subject)", example = "dev-test-user")
            @RequestParam(defaultValue = "dev-test-user") String userId,

            @Parameter(description = "사용자 역할 (USER / ADMIN)", example = "ADMIN")
            @RequestParam(defaultValue = "ADMIN") String role
    ) {
        // role 화이트리스트 검증 (임의 권한 토큰 발급 방지)
        String upperRole = role.toUpperCase();
        if (!ALLOWED_ROLES.contains(upperRole)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "허용되지 않는 role: " + role,
                    "allowed_roles", ALLOWED_ROLES
            ));
        }

        // JWT Access Token 생성 (JwtTokenProvider의 기존 로직 활용)
        String accessToken = jwtTokenProvider.generateAccessToken(userId, upperRole);

        log.info("[Dev Auth] JWT 토큰 발급 — userId: {}, role: {}", userId, role);

        return ResponseEntity.ok(Map.of(
                "access_token", accessToken,
                "token_type", "Bearer",
                "expires_in", "application.yml의 access-token-expiry 설정값 (기본 1시간)",
                "user_id", userId,
                "role", role,
                "usage", "Swagger UI 상단 Authorize 버튼 → BearerAuth에 access_token 값 붙여넣기"
        ));
    }

    /**
     * 개발 인증 기능의 활성화 상태를 확인한다.
     *
     * <p>이 엔드포인트가 응답하면 Dev Auth가 활성화된 상태이다.
     * 비활성화 상태에서는 이 컨트롤러 자체가 빈으로 등록되지 않으므로 404를 반환한다.</p>
     *
     * @return Dev Auth 상태 정보
     */
    @GetMapping("/status")
    @SecurityRequirements  // Swagger UI에서 인증 불필요 표시
    @Operation(
            summary = "Dev Auth 활성화 상태 확인",
            description = "Dev Auth 기능이 활성화되어 있는지 확인합니다. 응답이 오면 활성화된 상태입니다."
    )
    public ResponseEntity<Map<String, Object>> devAuthStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", true,
                "message", "⚠ Dev Auth가 활성화되어 있습니다. 운영 환경에서는 DEV_AUTH_ENABLED=false로 설정하세요.",
                "endpoints", Map.of(
                        "token", "GET /api/v1/dev/token?userId={id}&role={role}",
                        "status", "GET /api/v1/dev/status"
                )
        ));
    }
}
