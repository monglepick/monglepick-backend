package com.monglepick.monglepickbackend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 3.0 설정.
 *
 * <p>API 문서를 자동 생성하고 Swagger UI를 제공한다.</p>
 * <p>접속: http://localhost:8080/swagger-ui/index.html</p>
 *
 * <h3>인증 스키마</h3>
 * <ul>
 *   <li><b>BearerAuth</b>: JWT Bearer 토큰 (사용자 API)</li>
 *   <li><b>ServiceKeyAuth</b>: X-Service-Key 헤더 (AI Agent 내부 통신)</li>
 * </ul>
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI 3.0 명세 빈 등록.
     *
     * <p>API 정보, 인증 스키마를 설정한다.
     * 전역 기본 인증은 BearerAuth(JWT)로 설정되어 있으며,
     * 개별 엔드포인트에서 @SecurityRequirement로 오버라이드 가능하다.</p>
     *
     * @return OpenAPI 명세 객체
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("몽글픽 백엔드 API")
                        .description("AI 영화 추천 서비스 몽글픽의 REST API 명세서.\n\n" +
                                "## 인증 방식\n" +
                                "- **JWT Bearer**: 사용자 API (Authorization: Bearer {token})\n" +
                                "- **X-Service-Key**: AI Agent 내부 통신\n\n" +
                                "## 🔧 개발 환경 간편 테스트 (DEV_AUTH_ENABLED=true 일 때)\n" +
                                "### 방법 1: 마스터키 (가장 간편)\n" +
                                "1. 상단 🔓 **Authorize** 버튼 클릭\n" +
                                "2. **DevMasterKeyAuth** 필드에 마스터키 입력 (기본: `monglepick-dev-master-2026`)\n" +
                                "3. **Authorize** 클릭 → 끝! 모든 API 인증 통과\n\n" +
                                "### 방법 2: JWT 토큰 발급 (특정 userId/role 필요 시)\n" +
                                "1. **[🔧 Dev Auth] GET /api/v1/dev/token** 호출\n" +
                                "2. 응답의 `access_token` → **BearerAuth** 필드에 붙여넣기")
                        .version("v1.0.0")
                        .contact(new Contact().name("몽글픽 팀")))
                /* 전역 기본 인증: DevMasterKey 또는 JWT Bearer (Swagger UI에서 둘 중 하나만 입력하면 됨) */
                .addSecurityItem(new SecurityRequirement()
                        .addList("DevMasterKeyAuth")
                        .addList("BearerAuth"))
                .components(new Components()
                        /* JWT Bearer 인증 스키마 */
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization"))
                        /* AI Agent 서비스 키 인증 스키마 */
                        .addSecuritySchemes("ServiceKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Service-Key"))
                        /* 개발 전용 마스터키 인증 스키마 (DEV_AUTH_ENABLED=true 일 때 활성화) */
                        .addSecuritySchemes("DevMasterKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Dev-Master-Key")
                                .description("개발 전용 마스터키 (기본값: monglepick-dev-master-2026)")));
    }
}
