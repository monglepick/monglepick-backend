package com.monglepick.monglepickbackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Paths;

/**
 * Spring MVC 웹 설정.
 *
 * <p>Spring MVC의 추가적인 웹 설정을 정의하는 구성 클래스이다.
 * {@link WebMvcConfigurer} 인터페이스를 구현하여 필요한 설정만
 * 오버라이드할 수 있다.</p>
 *
 * <h3>CORS 설정 관련 주의사항</h3>
 * <p>CORS 설정은 {@link com.monglepick.monglepickbackend.global.security.SecurityConfig}의
 * {@code corsConfigurationSource()} 빈에서 처리한다.
 * {@code addCorsMappings()} 메서드를 여기서 오버라이드하면
 * Spring Security의 CORS 설정과 <b>충돌</b>할 수 있으므로
 * 의도적으로 비워두었다.</p>
 *
 * <p>Spring Security가 활성화된 환경에서는 Security 필터가
 * DispatcherServlet보다 먼저 실행되므로, CORS Preflight(OPTIONS) 요청이
 * SecurityFilterChain에서 먼저 처리된다.
 * 따라서 CORS 설정은 Security 레이어에서 관리하는 것이 올바르다.</p>
 *
 * <h3>확장 포인트</h3>
 * <p>향후 아래 기능이 필요할 때 이 클래스에 추가한다:</p>
 * <ul>
 *   <li>{@code addInterceptors()} — 로깅, 인증, 요청 추적 인터셉터</li>
 *   <li>{@code configureMessageConverters()} — 커스텀 HTTP 메시지 컨버터</li>
 *   <li>{@code addResourceHandlers()} — 정적 리소스 핸들러 (이미지 업로드 등)</li>
 *   <li>{@code addFormatters()} — 커스텀 타입 변환기 (날짜 포맷 등)</li>
 *   <li>{@code addArgumentResolvers()} — 커스텀 메서드 파라미터 리졸버
 *       (예: {@code @CurrentUser} 어노테이션 처리)</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.global.security.SecurityConfig#corsConfigurationSource()
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /*
     * ── CORS 설정 ──
     *
     * addCorsMappings()를 오버라이드하지 않는다.
     * SecurityConfig.corsConfigurationSource()에서 CORS 정책을 관리하며,
     * 여기서 중복 설정 시 Spring Security와 Spring MVC 간 CORS 처리 충돌이 발생한다.
     *
     * 참고: Spring Security가 비활성화된 환경에서는 아래와 같이 설정할 수 있다:
     *
     * @Override
     * public void addCorsMappings(CorsRegistry registry) {
     *     registry.addMapping("/**")
     *             .allowedOrigins("http://localhost:3000", "http://localhost:5173")
     *             .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
     *             .allowedHeaders("*")
     *             .allowCredentials(true)
     *             .maxAge(3600);
     * }
     */
    /**
     * 파일 저장 경로
     * 로컬: ./uploads (기본값)
     * 서버 배포 시 .env에서 UPLOAD_DIR=/home/ubuntu/data 로 오버라이드
     */
    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 정적 파일(이미지) 서빙 설정
     *
     * 【로컬 개발】
     *   /images/** 요청 → uploadDir 폴더에서 파일 서빙
     *   예: GET http://localhost:8080/images/userId/abc.jpg
     *       → ./uploads/userId/abc.jpg 반환
     *
     * 【서버 배포】
     *   이 설정은 로컬에서만 사용됨
     *   서버에서는 NGINX가 /images/** 를 /home/ubuntu/data/ 로 직접 서빙
     *   NGINX 설정 예시:
     *     location /images/ {
     *         alias /home/ubuntu/data/;
     *     }
     *
     * 【추후 S3/Object Storage 전환 시】
     *   이 메서드 불필요 — S3 URL을 직접 반환하므로 로컬 서빙 필요 없음
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }

    /*
     * ── 인터셉터 등록 예시 (향후 추가) ──
     *
     * @Override
     * public void addInterceptors(InterceptorRegistry registry) {
     *     // 요청 로깅 인터셉터
     *     registry.addInterceptor(new RequestLoggingInterceptor())
     *             .addPathPatterns("/api/**");
     *
     *     // API 요청 추적 인터셉터 (X-Request-Id 생성)
     *     registry.addInterceptor(new RequestTraceInterceptor())
     *             .addPathPatterns("/api/**");
     * }
     */

    /*
     * ── 커스텀 Argument Resolver 예시 (향후 추가) ──
     *
     * JWT 인증 구현 후 @CurrentUser 어노테이션으로
     * 컨트롤러 메서드 파라미터에 현재 사용자 정보를 주입할 때 사용:
     *
     * @Override
     * public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
     *     resolvers.add(new CurrentUserArgumentResolver());
     * }
     */
}
