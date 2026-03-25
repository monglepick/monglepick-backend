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
                                "- **X-Service-Key**: AI Agent 내부 통신")
                        .version("v1.0.0")
                        .contact(new Contact().name("몽글픽 팀")))
                /* 전역 기본 인증: JWT Bearer */
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
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
                                .name("X-Service-Key")));
    }
}
