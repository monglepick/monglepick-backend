package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.SystemDto.ConfigItem;
import com.monglepick.monglepickbackend.admin.dto.SystemDto.ServiceHealth;
import com.monglepick.monglepickbackend.admin.dto.SystemDto.ServiceStatusResponse;
import com.monglepick.monglepickbackend.admin.dto.SystemDto.SystemConfigResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 시스템 서비스.
 * - 4개 서비스 헬스체크 집계 (Backend 자신 + Agent + Recommend + Nginx)
 * - 현재 설정값 조회 (읽기 전용)
 */
@Slf4j
@Service
public class AdminSystemService {

    /**
     * 각 서비스 헬스체크 URL.
     *
     * <p>운영(Prod) 환경에서는 backend 컨테이너 안에서 다른 컨테이너/외부 서비스를 호출해야 하므로
     * 기본값(localhost:*)을 그대로 쓰면 모두 connection refused 가 난다. 운영 docker-compose 에서
     * `ADMIN_HEALTH_*` 환경변수로 다음과 같이 오버라이드한다 (Spring relaxed binding).</p>
     *
     * <ul>
     *   <li>backend  : http://localhost:8080/actuator/health  (자기 자신 — 컨테이너 내부 actuator)</li>
     *   <li>agent    : http://monglepick-agent:8000          (compose 내부 DNS, 코드에서 /health 자동 부여)</li>
     *   <li>recommend: http://monglepick-recommend:8001      (compose 내부 DNS, 코드에서 /health 자동 부여)</li>
     *   <li>nginx    : http://10.20.0.13/health              (VM1 내부 IP. 전체 URL 그대로 사용)</li>
     * </ul>
     *
     * <p>로컬 개발은 모든 서비스가 호스트의 localhost 로 도달 가능하므로 기본값으로 동작한다.</p>
     */
    @Value("${admin.health.backend-url:http://localhost:8080/actuator/health}")
    private String backendUrl;

    @Value("${admin.health.agent-url:http://localhost:8000}")
    private String agentUrl;

    @Value("${admin.health.recommend-url:http://localhost:8001}")
    private String recommendUrl;

    @Value("${admin.health.nginx-url:http://localhost:80/health}")
    private String nginxUrl;

    /* ── JWT 설정값 (application.yml에서 읽기) ── */
    @Value("${jwt.access-expiration:1800000}")
    private long jwtAccessExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpiration;

    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize;

    private final RestClient restClient = RestClient.create();

    /**
     * 4개 서비스 헬스체크를 집계한다.
     * 각 서비스의 /health 엔드포인트에 GET 요청을 보내고 응답 시간을 측정.
     */
    public ServiceStatusResponse checkServiceStatus() {
        return new ServiceStatusResponse(
                // backend 는 기본값/오버라이드 모두 전체 URL 형태 (예: /actuator/health)
                checkHealth("Spring Boot", backendUrl),
                // agent / recommend 는 base URL + /health 조합
                checkHealth("AI Agent", agentUrl + "/health"),
                checkHealth("Recommend", recommendUrl + "/health"),
                // nginx 는 전체 URL 형태 (기본값/오버라이드 모두)
                checkHealth("Nginx", nginxUrl)
        );
    }

    /**
     * 현재 시스템 설정값을 조회한다 (읽기 전용).
     * 환경변수/설정 파일에서 직접 수정이 필요하므로 UI에서는 조회만 가능.
     */
    public SystemConfigResponse getSystemConfig() {
        Map<String, ConfigItem> configs = new LinkedHashMap<>();

        configs.put("jwtAccessExpiry", new ConfigItem(
                formatDuration(jwtAccessExpiration), "JWT Access Token 만료 시간"));
        configs.put("jwtRefreshExpiry", new ConfigItem(
                formatDuration(jwtRefreshExpiration), "JWT Refresh Token 만료 시간"));
        configs.put("fileUploadLimit", new ConfigItem(
                maxFileSize, "파일 업로드 최대 크기 (JPEG/PNG/GIF/WebP)"));
        configs.put("redisSessionTtl", new ConfigItem(
                "30일", "AI Agent 대화 세션 TTL"));
        configs.put("sseKeepalive", new ConfigItem(
                "15초", "SSE 연결 유지 간격"));
        configs.put("embeddingRateLimit", new ConfigItem(
                "100 RPM", "Upstage Solar 임베딩 API Rate Limit"));
        configs.put("cfCacheTtl", new ConfigItem(
                "24시간", "Redis 협업필터링 캐시 TTL"));

        return new SystemConfigResponse(configs);
    }

    /* ── 내부 헬퍼 ── */

    /**
     * 단일 서비스에 헬스체크 요청을 보낸다.
     * 타임아웃: 5초. 실패 시 connected=false 반환.
     */
    private ServiceHealth checkHealth(String name, String url) {
        long start = System.currentTimeMillis();
        try {
            restClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();

            long elapsed = System.currentTimeMillis() - start;
            String status = elapsed > 3000 ? "slow" : "up";

            return new ServiceHealth(name, url, true, status, elapsed, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[admin] 헬스체크 실패: {} ({}) — {}", name, url, e.getMessage());
            return new ServiceHealth(name, url, false, "down", elapsed, null);
        }
    }

    /** 밀리초를 사람 읽기 편한 형식으로 변환 */
    private String formatDuration(long millis) {
        if (millis < 60_000) return (millis / 1000) + "초";
        if (millis < 3_600_000) return (millis / 60_000) + "분";
        if (millis < 86_400_000) return (millis / 3_600_000) + "시간";
        return (millis / 86_400_000) + "일";
    }
}
