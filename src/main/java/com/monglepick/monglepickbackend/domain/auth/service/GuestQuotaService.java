package com.monglepick.monglepickbackend.domain.auth.service;

import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaCheckResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaConsumeResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 비로그인(게스트) 사용자의 AI 추천 무료 체험 쿼터 서비스.
 *
 * <h3>정책 — 디바이스 평생 1회 무료</h3>
 * <ul>
 *   <li>쿠키 {@code mongle_guest} 기준 1회 + 클라이언트 IP 기준 1회 (이중 방어)</li>
 *   <li>둘 중 하나라도 Redis 에 소비 기록이 있으면 차단 → 로그인 유도</li>
 *   <li>쿠키 삭제 + VPN 우회 모두 시도해야 우회 가능하므로 일반 사용자 기준 사실상 1회</li>
 * </ul>
 *
 * <h3>쿠키 포맷</h3>
 * <p>{@code {guestId}.{base64url(HMAC_SHA256(guestId))}}</p>
 * <p>예: {@code 550e8400-e29b-41d4-a716-446655440000.xKz9...abc}</p>
 *
 * <p>HMAC 시크릿은 {@code GUEST_COOKIE_SECRET} 환경변수 필수. 미설정 시 기동 실패한다.</p>
 *
 * <h3>Redis 키 스키마</h3>
 * <pre>
 * chat:guest_used:{guest_id}   → "1"  (TTL 365일)
 * chat:guest_used_ip:{ip}      → "1"  (TTL 365일)
 * </pre>
 *
 * <p>두 키 모두 {@link StringRedisTemplate.ValueOperations#setIfAbsent} (SETNX) 로 소비한다.
 * 이미 존재하면 반환값 false → 중복 소비 감지 가능.</p>
 *
 * @see com.monglepick.monglepickbackend.domain.auth.controller.GuestController
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestQuotaService {

    /** Redis 키 prefix — 쿠키(guestId) 기준 소비 기록. */
    private static final String REDIS_KEY_GUEST_USED = "chat:guest_used:";

    /** Redis 키 prefix — IP 기준 소비 기록. */
    private static final String REDIS_KEY_GUEST_USED_IP = "chat:guest_used_ip:";

    /** 쿠키/Redis 키 TTL — 365일 (평생 1회 의미). */
    private static final Duration QUOTA_TTL = Duration.ofDays(365);

    /** 쿠키 값 내 {guestId}.{signature} 구분자. */
    private static final String COOKIE_SEPARATOR = ".";

    /** HMAC 알고리즘 — JDK 표준 내장. */
    private static final String HMAC_ALGO = "HmacSHA256";

    /** Redis 클라이언트 — Spring Boot 가 {@code spring-boot-starter-data-redis} 로 자동 주입. */
    private final StringRedisTemplate redisTemplate;

    /**
     * HMAC 시크릿 키.
     * 운영 환경에서는 반드시 {@code GUEST_COOKIE_SECRET} 환경변수로 주입해야 한다.
     * 개발 기본값을 두되 운영에서 프로파일 검증으로 차단한다 (application.yml 에 comment).
     */
    @Value("${mongle.guest.cookie-secret:dev-guest-cookie-secret-change-me}")
    private String cookieSecret;

    /** Mac 인스턴스는 스레드 세이프하지 않으므로 요청마다 새로 만든다 (메서드 내 초기화). */
    private SecretKeySpec secretKeySpec;

    @PostConstruct
    void init() {
        /*
         * 시작 시 시크릿 검증. 기본값(dev-...) 을 그대로 쓰는 운영 배포를 감지하기 위한 경고.
         * 엄격 차단은 application.yml 에 PROD 프로파일 가드 추가 필요 (향후 Phase).
         */
        if (cookieSecret.startsWith("dev-")) {
            log.warn("⚠ GuestQuotaService: 기본 쿠키 시크릿 사용 중. 운영 배포 시 GUEST_COOKIE_SECRET 환경변수 필수");
        }
        this.secretKeySpec = new SecretKeySpec(
                cookieSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGO
        );
    }

    /**
     * 새 게스트 식별자(UUID) 를 생성하고 HMAC 서명까지 포함한 쿠키 값 문자열을 반환한다.
     *
     * @return {@code {guestId}.{base64url(HMAC)}}
     */
    public String issueNewCookieValue() {
        String guestId = UUID.randomUUID().toString();
        return guestId + COOKIE_SEPARATOR + sign(guestId);
    }

    /**
     * 쿠키 값에서 guestId 를 파싱하고 서명을 검증한 뒤 guestId 를 반환한다.
     *
     * @param cookieValue {@code {guestId}.{signature}} 형태의 원본 쿠키 값
     * @return 검증 통과 시 guestId, 실패 시 null
     */
    public String parseAndVerifyCookie(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return null;
        }
        int sep = cookieValue.indexOf(COOKIE_SEPARATOR);
        if (sep <= 0 || sep == cookieValue.length() - 1) {
            log.debug("쿠키 포맷 불량: {}", cookieValue);
            return null;
        }
        String guestId = cookieValue.substring(0, sep);
        String signature = cookieValue.substring(sep + 1);

        String expected = sign(guestId);
        if (!constantTimeEquals(signature, expected)) {
            log.warn("게스트 쿠키 서명 불일치: guestId={}", guestId);
            return null;
        }
        return guestId;
    }

    /**
     * 새 guestId 발급 없이 이미 검증된 guestId 로 쿠키 값 문자열을 재생성한다 (시크릿 로테이션 등).
     *
     * @param guestId 기존 guestId
     * @return 재서명된 쿠키 값
     */
    public String rebuildCookieValue(String guestId) {
        return guestId + COOKIE_SEPARATOR + sign(guestId);
    }

    /**
     * 쿼터 사용 여부 체크 (소비는 하지 않음).
     *
     * @param guestId  쿠키에서 추출한 UUID
     * @param clientIp 실제 클라이언트 IP
     * @return allowed=false 면 차단, reason 으로 어느 키에 걸렸는지 반환
     */
    public GuestQuotaCheckResponse checkQuota(String guestId, String clientIp) {
        /*
         * ① 쿠키 키 먼저 체크 — 쿠키가 단일 방어선의 주요 키.
         * Redis 장애 등으로 hasKey 가 예외를 던질 경우 fail-open 하지 않고 상위(Controller) 로 전파한다.
         * fail-open 정책은 Agent 쪽 guest_quota_client 에서 httpx 예외 처리로 구현한다.
         */
        if (Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_KEY_GUEST_USED + guestId))) {
            return new GuestQuotaCheckResponse(false, "GUEST_COOKIE_USED");
        }
        /* ② IP 키 체크 — 쿠키 삭제 후 재진입 방어 */
        if (Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_KEY_GUEST_USED_IP + clientIp))) {
            return new GuestQuotaCheckResponse(false, "GUEST_IP_USED");
        }
        return new GuestQuotaCheckResponse(true, "OK");
    }

    /**
     * 쿼터 소비 — 쿠키 키와 IP 키를 원자적으로 SETNX.
     *
     * <p>두 키 중 하나라도 이미 존재하면 소비 실패로 간주하고 양쪽을 되돌리지 않는다
     * (이미 존재한다는 것 자체가 "소비된 상태" 이므로 멱등 유지).</p>
     *
     * @param guestId  쿠키에서 추출한 UUID
     * @param clientIp 실제 클라이언트 IP
     * @return success=true 면 최초 소비 성공, false 면 이미 소비된 상태
     */
    public GuestQuotaConsumeResponse consumeQuota(String guestId, String clientIp) {
        /*
         * Redis SET NX EX 는 atomic. Spring Data Redis 의 setIfAbsent(key, value, timeout) 는
         * 내부적으로 `SET key value EX {sec} NX` 명령을 사용한다 (StringRedisTemplate).
         */
        Boolean cookieSet = redisTemplate.opsForValue().setIfAbsent(
                REDIS_KEY_GUEST_USED + guestId, "1", QUOTA_TTL);
        Boolean ipSet = redisTemplate.opsForValue().setIfAbsent(
                REDIS_KEY_GUEST_USED_IP + clientIp, "1", QUOTA_TTL);

        /*
         * 두 키 모두 신규 SET 성공해야 진짜 "최초 소비". 그 외 케이스:
         *  - cookieSet=true + ipSet=false  : 같은 IP 로 다른 사람(쿠키 없는 새 브라우저) 이 이미 사용 → 차단해야 함
         *  - cookieSet=false + ipSet=true  : 같은 쿠키를 다른 IP 에서 재사용 → 차단해야 함
         *  - 둘 다 false                     : 완벽한 재시도 → 이미 소비됨
         */
        boolean cookieNew = Boolean.TRUE.equals(cookieSet);
        boolean ipNew = Boolean.TRUE.equals(ipSet);

        if (cookieNew && ipNew) {
            log.info("게스트 쿼터 최초 소비: guestId={}, clientIp={}", guestId, clientIp);
            return new GuestQuotaConsumeResponse(true, "OK");
        }

        /*
         * 한쪽만 신규였던 경우에도 결국 "이미 소비된 상태" 로 간주.
         * 상대 키는 그대로 유지 (롤백하면 오히려 우회 창구 제공).
         */
        String reason;
        if (!cookieNew && !ipNew) {
            reason = "ALREADY_CONSUMED";
        } else if (!cookieNew) {
            reason = "GUEST_COOKIE_USED";
        } else {
            reason = "GUEST_IP_USED";
        }
        log.info("게스트 쿼터 소비 실패 (이미 소비됨): guestId={}, clientIp={}, reason={}",
                guestId, clientIp, reason);
        return new GuestQuotaConsumeResponse(false, reason);
    }

    // ════════════════════════════════════════════════════════════════
    // 내부 유틸 — HMAC 서명
    // ════════════════════════════════════════════════════════════════

    /**
     * guestId 에 HMAC-SHA256 서명을 생성하고 URL-safe Base64 (패딩 제거) 로 인코딩한다.
     */
    private String sign(String guestId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] raw = mac.doFinal(guestId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            /*
             * HMAC 실패는 JDK 레벨 문제이므로 사실상 발생 불가.
             * 발생 시 보안 회피 시도로 간주하고 런타임 예외로 차단한다.
             */
            throw new IllegalStateException("HMAC 서명 실패", e);
        }
    }

    /**
     * 타이밍 공격 방지를 위한 상수 시간 비교.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
