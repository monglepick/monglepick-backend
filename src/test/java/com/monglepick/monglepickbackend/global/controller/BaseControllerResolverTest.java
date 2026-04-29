package com.monglepick.monglepickbackend.global.controller;

import com.monglepick.monglepickbackend.global.constants.AppConstants;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BaseController#resolveUserIdWithServiceKey} 회귀 테스트.
 *
 * <p>핵심 시나리오는 AI Agent 가 ServiceKey 인증으로 호출할 때
 * 컨트롤러가 {@code requestUserId} 인자를 명시하지 않아도
 * {@link AppConstants#HEADER_USER_ID X-User-Id} 헤더를 자동으로 읽어 식별해야 한다는 점.</p>
 *
 * <p>2026-04-29 회귀: 이전에는 {@code resolveUserIdWithServiceKey(principal, null)} 로 호출하면
 * ServiceKey 경로에서 무조건 INVALID_INPUT 을 던졌고, 그 결과 고객센터 챗봇의
 * {@code lookup_my_point_history} tool 이 항상 HTTP 400 으로 실패했다.</p>
 */
class BaseControllerResolverTest {

    /**
     * 테스트용 BaseController 노출 서브클래스 — protected 메서드 호출을 위한 thin wrapper.
     * BaseController 자체에는 다른 의존성이 없어 안전하게 직접 인스턴스화할 수 있다.
     */
    static class TestableBaseController extends BaseController {
        public String exposeResolve(Principal principal, String requestUserId) {
            return resolveUserIdWithServiceKey(principal, requestUserId);
        }
    }

    private final TestableBaseController controller = new TestableBaseController();

    @AfterEach
    void clearRequestContext() {
        // 테스트 간 RequestContextHolder 누수 방지 — 각 케이스마다 격리.
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 현재 스레드에 X-User-Id 헤더가 담긴 가짜 HTTP 요청 컨텍스트를 바인딩한다.
     */
    private void bindRequestWithUserIdHeader(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader(AppConstants.HEADER_USER_ID, headerValue);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Nested
    @DisplayName("ServiceKey 인증 경로 (principal = SERVICE_PRINCIPAL)")
    class ServiceKeyPath {

        private final Principal servicePrincipal = () -> AppConstants.SERVICE_PRINCIPAL;

        @Test
        @DisplayName("명시 requestUserId 가 있으면 그 값을 사용한다 (헤더 무시)")
        void explicitRequestUserId_takesPriority() {
            bindRequestWithUserIdHeader("user-from-header");

            String resolved = controller.exposeResolve(servicePrincipal, "user-from-arg");

            assertEquals("user-from-arg", resolved);
        }

        @Test
        @DisplayName("requestUserId 가 null 이면 X-User-Id 헤더를 읽어 식별한다 — 핵심 회귀 케이스")
        void nullRequestUserId_fallsBackToHeader() {
            bindRequestWithUserIdHeader("user-from-header");

            String resolved = controller.exposeResolve(servicePrincipal, null);

            assertEquals("user-from-header", resolved);
        }

        @Test
        @DisplayName("requestUserId 가 공백이어도 헤더로 폴백한다")
        void blankRequestUserId_fallsBackToHeader() {
            bindRequestWithUserIdHeader("user-from-header");

            String resolved = controller.exposeResolve(servicePrincipal, "   ");

            assertEquals("user-from-header", resolved);
        }

        @Test
        @DisplayName("requestUserId / 헤더 모두 없으면 INVALID_INPUT 예외")
        void noUserIdAnywhere_throwsInvalidInput() {
            bindRequestWithUserIdHeader(null);  // 헤더 없는 요청 컨텍스트

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> controller.exposeResolve(servicePrincipal, null)
            );
            assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
        }

        @Test
        @DisplayName("요청 컨텍스트가 바인딩되지 않은 비-요청 스레드에서도 안전하게 INVALID_INPUT 으로 처리")
        void noRequestContext_throwsInvalidInput() {
            // bindRequestWithUserIdHeader 미호출 — RequestContextHolder 비어 있음.
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> controller.exposeResolve(servicePrincipal, null)
            );
            assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("JWT 인증 경로 (principal != SERVICE_PRINCIPAL)")
    class JwtPath {

        @Test
        @DisplayName("JWT principal 의 이름을 그대로 userId 로 반환한다 — X-User-Id 헤더는 무시")
        void jwtPrincipal_returnsPrincipalName() {
            bindRequestWithUserIdHeader("attacker-user");  // BOLA 시도 시뮬레이션
            Principal jwtPrincipal = () -> "real-user-id";

            String resolved = controller.exposeResolve(jwtPrincipal, null);

            assertEquals("real-user-id", resolved);
        }

        @Test
        @DisplayName("principal 이 null 이면 UNAUTHORIZED 예외")
        void nullPrincipal_throwsUnauthorized() {
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> controller.exposeResolve(null, null)
            );
            assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        }
    }
}
