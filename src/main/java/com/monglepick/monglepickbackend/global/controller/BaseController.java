package com.monglepick.monglepickbackend.global.controller;

import com.monglepick.monglepickbackend.global.constants.AppConstants;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;

/**
 * 컨트롤러 공통 기능 추상 클래스.
 *
 * <p>JWT/ServiceKey 인증에서 userId 추출, 페이지 크기 제한 등
 * 여러 컨트롤러에서 중복되는 로직을 중앙에서 관리한다.</p>
 *
 * <h3>사용법</h3>
 * <pre>{@code
 * @RestController
 * public class MyController extends BaseController {
 *     public ResponseEntity<?> myEndpoint(Principal principal) {
 *         String userId = resolveUserId(principal);
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.controller.PointController
 * @see com.monglepick.monglepickbackend.domain.payment.controller.PaymentController
 * @see com.monglepick.monglepickbackend.domain.payment.controller.SubscriptionController
 */
public abstract class BaseController {

    /**
     * Principal에서 userId를 안전하게 추출한다 (JWT 전용).
     *
     * <p>null이거나 getName()이 null이면 UNAUTHORIZED 예외를 던진다.</p>
     *
     * @param principal 인증된 사용자 정보
     * @return 사용자 ID
     * @throws BusinessException 인증 정보가 없는 경우
     */
    protected String resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getName();
    }

    /**
     * 공개 API에서 옵셔널 JWT 처리 — 인증이 없으면 null 반환 (예외 없음).
     *
     * <p>permitAll 엔드포인트가 로그인 사용자에게 추가 정보(solved 여부 등)를
     * 내려줄 때 사용한다. 비로그인이면 null을 반환해 기능을 건너뛴다.</p>
     *
     * @param principal 인증된 사용자 정보 (비로그인이면 null)
     * @return 사용자 ID, 또는 인증 정보가 없으면 null
     */
    protected String resolveUserIdSilently(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        return principal.getName();
    }

    /**
     * Principal에서 userId를 추출한다 (ServiceKey + JWT 혼합 인증).
     *
     * <p>ServiceKey 인증인 경우 다음 우선순위로 대상 사용자 ID 를 결정한다.</p>
     * <ol>
     *   <li>컨트롤러가 명시적으로 넘긴 {@code requestUserId} (쿼리 파라미터/바디 필드 등).</li>
     *   <li>현재 HTTP 요청의 {@link AppConstants#HEADER_USER_ID X-User-Id} 헤더.
     *       AI Agent 의 {@code support_tools/_base.call_backend_get} 가 사용하는
     *       표준 채널이며, 모든 ServiceKey 컨트롤러가 일일이 plumbing 하지 않아도
     *       BaseController 단계에서 자동으로 인식된다.</li>
     *   <li>둘 다 없으면 INVALID_INPUT 예외.</li>
     * </ol>
     *
     * <p>JWT 인증인 경우 토큰에서 추출된 userId(=principal.getName())를 그대로 사용한다.</p>
     *
     * @param principal     인증된 사용자 정보
     * @param requestUserId 컨트롤러에서 명시적으로 전달한 userId (없으면 null 가능)
     * @return 확인된 사용자 ID
     * @throws BusinessException 인증 정보가 없거나, ServiceKey 인데 userId 가 어디에도 없는 경우
     */
    protected String resolveUserIdWithServiceKey(Principal principal, String requestUserId) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String principalName = principal.getName();

        // ServiceKey 인증: 명시 인자 → X-User-Id 헤더 → 예외 순서로 폴백
        if (AppConstants.SERVICE_PRINCIPAL.equals(principalName)) {
            String resolved = (requestUserId != null && !requestUserId.isBlank())
                    ? requestUserId
                    : readUserIdHeaderFromCurrentRequest();
            if (resolved == null || resolved.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "서비스 호출 시 userId는 필수입니다");
            }
            return resolved;
        }

        // JWT 인증: 토큰에서 추출한 userId 사용
        return principalName;
    }

    /**
     * 현재 스레드에 바인딩된 HTTP 요청에서 {@code X-User-Id} 헤더 값을 읽는다.
     *
     * <p>{@link RequestContextHolder} 가 ServletRequestAttributes 를 가지지 못하는
     * 비-요청 컨텍스트(스케줄러, 비동기 워커 등)에서는 안전하게 {@code null} 을 반환한다.</p>
     *
     * @return 헤더 값 (없거나 비-요청 컨텍스트면 null)
     */
    private String readUserIdHeaderFromCurrentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr)) {
            return null;
        }
        HttpServletRequest request = attr.getRequest();
        String value = request.getHeader(AppConstants.HEADER_USER_ID);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 페이지 크기를 상한값으로 제한한다 (대량 조회 DoS 방지).
     *
     * @param size 요청된 페이지 크기
     * @return 제한된 페이지 크기 (최대 {@link AppConstants#MAX_PAGE_SIZE})
     */
    protected int limitPageSize(int size) {
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }
}
