package com.monglepick.monglepickbackend.domain.auth.service;

import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaCheckResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.GuestDto.GuestQuotaConsumeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GuestQuotaService 단위 테스트 (2026-04-22, Phase 3).
 *
 * <p>HMAC 쿠키 서명/검증 + Redis SETNX 기반 평생 1회 쿼터 로직 검증.
 * Redis 는 Mock 처리하여 DB 의존성 없이 서비스 로직만 검증한다.</p>
 *
 * <h3>테스트 그룹</h3>
 * <ul>
 *   <li>{@link CookieSignatureTest} — issueNewCookieValue/parseAndVerifyCookie 라운드트립 + 서명 조작 방어</li>
 *   <li>{@link CheckQuotaTest} — 쿠키/IP 키 조합별 허용/차단 판정</li>
 *   <li>{@link ConsumeQuotaTest} — SETNX 원자성 + 멱등성 검증</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GuestQuotaServiceTest {

    /** 테스트용 HMAC 시크릿 (PostConstruct 후 SecretKeySpec 재초기화). */
    private static final String TEST_SECRET = "test-secret-for-unit-tests-only-do-not-use-in-production";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private GuestQuotaService guestQuotaService;

    @BeforeEach
    void setUp() {
        guestQuotaService = new GuestQuotaService(redisTemplate);
        // @Value 로 주입되는 cookieSecret 을 ReflectionTestUtils 로 세팅.
        // 이후 @PostConstruct init() 을 수동 호출해 SecretKeySpec 을 초기화한다.
        ReflectionTestUtils.setField(guestQuotaService, "cookieSecret", TEST_SECRET);
        ReflectionTestUtils.invokeMethod(guestQuotaService, "init");
    }

    // ════════════════════════════════════════════════════════════════
    // 1. 쿠키 서명/검증
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("쿠키 서명/검증 — issueNewCookieValue + parseAndVerifyCookie")
    class CookieSignatureTest {

        @Test
        @DisplayName("발급한 쿠키는 스스로 파싱/검증 통과한다 (라운드트립)")
        void roundtrip() {
            String cookieValue = guestQuotaService.issueNewCookieValue();

            // 포맷: {guestId}.{signature}
            assertThat(cookieValue).contains(".");
            String[] parts = cookieValue.split("\\.", 2);
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isNotBlank();
            assertThat(parts[1]).isNotBlank();

            // parseAndVerify 가 guestId 를 그대로 복원
            String verified = guestQuotaService.parseAndVerifyCookie(cookieValue);
            assertThat(verified).isEqualTo(parts[0]);
        }

        @Test
        @DisplayName("서명이 조작된 쿠키는 null 반환 (위조 방어)")
        void tamperedSignatureRejected() {
            String cookieValue = guestQuotaService.issueNewCookieValue();
            String[] parts = cookieValue.split("\\.", 2);
            /* 서명 마지막 문자 바꿔 검증 실패 유도 */
            String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1) + "X";

            assertThat(guestQuotaService.parseAndVerifyCookie(tampered)).isNull();
        }

        @Test
        @DisplayName("guestId 가 바뀌면 서명 불일치로 null 반환")
        void guestIdMismatchRejected() {
            String cookieValue = guestQuotaService.issueNewCookieValue();
            String sig = cookieValue.split("\\.", 2)[1];
            /* guestId 만 교체하면 같은 시크릿으로도 서명이 달라져 검증 실패해야 한다 */
            String swapped = "00000000-0000-0000-0000-000000000000." + sig;

            assertThat(guestQuotaService.parseAndVerifyCookie(swapped)).isNull();
        }

        @Test
        @DisplayName("null / 빈 값 / 구분자 없음은 모두 null 반환 (포맷 방어)")
        void malformedInputsReturnNull() {
            assertThat(guestQuotaService.parseAndVerifyCookie(null)).isNull();
            assertThat(guestQuotaService.parseAndVerifyCookie("")).isNull();
            assertThat(guestQuotaService.parseAndVerifyCookie("   ")).isNull();
            assertThat(guestQuotaService.parseAndVerifyCookie("no-separator")).isNull();
            assertThat(guestQuotaService.parseAndVerifyCookie(".only-sig")).isNull();
            assertThat(guestQuotaService.parseAndVerifyCookie("only-id.")).isNull();
        }

        @Test
        @DisplayName("rebuildCookieValue 는 동일 guestId 에 대해 동일 결과를 반환 (결정적)")
        void rebuildIsDeterministic() {
            String guestId = "sample-guest-uuid-1234";
            String first = guestQuotaService.rebuildCookieValue(guestId);
            String second = guestQuotaService.rebuildCookieValue(guestId);

            assertThat(first).isEqualTo(second);
            assertThat(guestQuotaService.parseAndVerifyCookie(first)).isEqualTo(guestId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 2. 쿼터 체크
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkQuota — 쿠키/IP 키 조합별 판정")
    class CheckQuotaTest {

        @Test
        @DisplayName("양쪽 키 모두 없으면 allowed=true, reason=OK")
        void bothKeysAbsentAllowsRequest() {
            when(redisTemplate.hasKey(anyString())).thenReturn(false);

            GuestQuotaCheckResponse resp = guestQuotaService.checkQuota("guest-1", "1.2.3.4");

            assertThat(resp.allowed()).isTrue();
            assertThat(resp.reason()).isEqualTo("OK");
        }

        @Test
        @DisplayName("쿠키 키가 이미 존재하면 GUEST_COOKIE_USED 로 차단")
        void cookieKeyHitBlocks() {
            when(redisTemplate.hasKey("chat:guest_used:guest-1")).thenReturn(true);

            GuestQuotaCheckResponse resp = guestQuotaService.checkQuota("guest-1", "1.2.3.4");

            assertThat(resp.allowed()).isFalse();
            assertThat(resp.reason()).isEqualTo("GUEST_COOKIE_USED");
            /* IP 체크까지 갈 필요 없이 바로 반환 */
            verify(redisTemplate, never()).hasKey("chat:guest_used_ip:1.2.3.4");
        }

        @Test
        @DisplayName("쿠키 키 없음 + IP 키만 존재하면 GUEST_IP_USED 로 차단")
        void ipKeyOnlyHitBlocks() {
            when(redisTemplate.hasKey("chat:guest_used:guest-1")).thenReturn(false);
            when(redisTemplate.hasKey("chat:guest_used_ip:1.2.3.4")).thenReturn(true);

            GuestQuotaCheckResponse resp = guestQuotaService.checkQuota("guest-1", "1.2.3.4");

            assertThat(resp.allowed()).isFalse();
            assertThat(resp.reason()).isEqualTo("GUEST_IP_USED");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 3. 쿼터 소비
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("consumeQuota — SETNX 원자성 + 멱등성")
    class ConsumeQuotaTest {

        @BeforeEach
        void stubValueOps() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("양쪽 키 모두 신규 SET 성공 시 success=true, reason=OK")
        void firstTimeConsumeSucceeds() {
            when(valueOperations.setIfAbsent(eq("chat:guest_used:guest-1"), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(valueOperations.setIfAbsent(eq("chat:guest_used_ip:1.2.3.4"), eq("1"), any(Duration.class)))
                    .thenReturn(true);

            GuestQuotaConsumeResponse resp = guestQuotaService.consumeQuota("guest-1", "1.2.3.4");

            assertThat(resp.success()).isTrue();
            assertThat(resp.reason()).isEqualTo("OK");
        }

        @Test
        @DisplayName("양쪽 키 모두 이미 존재하면 success=false, reason=ALREADY_CONSUMED")
        void duplicateConsumeFailsMarkedAlreadyConsumed() {
            when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                    .thenReturn(false);

            GuestQuotaConsumeResponse resp = guestQuotaService.consumeQuota("guest-1", "1.2.3.4");

            assertThat(resp.success()).isFalse();
            assertThat(resp.reason()).isEqualTo("ALREADY_CONSUMED");
        }

        @Test
        @DisplayName("쿠키만 기존 존재, IP 는 신규 → reason=GUEST_COOKIE_USED")
        void partialHitCookieReused() {
            when(valueOperations.setIfAbsent(eq("chat:guest_used:guest-1"), eq("1"), any(Duration.class)))
                    .thenReturn(false);
            when(valueOperations.setIfAbsent(eq("chat:guest_used_ip:1.2.3.4"), eq("1"), any(Duration.class)))
                    .thenReturn(true);

            GuestQuotaConsumeResponse resp = guestQuotaService.consumeQuota("guest-1", "1.2.3.4");

            assertThat(resp.success()).isFalse();
            assertThat(resp.reason()).isEqualTo("GUEST_COOKIE_USED");
        }

        @Test
        @DisplayName("IP 만 기존 존재, 쿠키는 신규 → reason=GUEST_IP_USED")
        void partialHitIpReused() {
            when(valueOperations.setIfAbsent(eq("chat:guest_used:guest-1"), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(valueOperations.setIfAbsent(eq("chat:guest_used_ip:1.2.3.4"), eq("1"), any(Duration.class)))
                    .thenReturn(false);

            GuestQuotaConsumeResponse resp = guestQuotaService.consumeQuota("guest-1", "1.2.3.4");

            assertThat(resp.success()).isFalse();
            assertThat(resp.reason()).isEqualTo("GUEST_IP_USED");
        }
    }
}
