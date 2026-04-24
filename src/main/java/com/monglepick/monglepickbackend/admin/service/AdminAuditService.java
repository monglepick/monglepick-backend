package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.repository.AdminAuditLogRepository;
import com.monglepick.monglepickbackend.domain.admin.entity.AdminAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 감사 로그 기록 전용 서비스 — 2026-04-09 신규 추가.
 *
 * <p>관리자 페이지의 중요 쓰기 액션(결제 환불, 수동 포인트, 사용자 정지, 이용권 발급 등)에서
 * 호출하여 {@link AdminAuditLog} 테이블에 변경 이력을 기록한다.</p>
 *
 * <h3>도입 배경 (P1-1)</h3>
 * <p>기존에는 {@link AdminAuditLog} 엔티티·{@link AdminAuditLogRepository}·조회 API
 * (AdminSettingsService)는 이미 존재했지만 <b>저장 경로(write-path)가 단 한 군데도
 * 구현되어 있지 않았다</b>. {@code AdminUserService.java:245}, {@code SubscriptionService.java:366}
 * 등에 "향후 AdminAuditLog 연동 시 이 위치에 INSERT 호출을 추가한다"는 TODO 주석만 남아
 * 있던 상태였다. 따라서 관리자 감사 로그 조회 UI는 동작해도 영구히 빈 결과만 노출되어
 * 환불·포인트 수동 지급·사용자 제재 등 민감 액션의 추적이 불가능했다.</p>
 *
 * <p>이 서비스는 모든 중요 액션에서 공통으로 호출할 수 있는 단일 write-path를 제공하여
 * 보일러플레이트를 최소화하고 감사 기록 품질을 일정하게 유지한다.</p>
 *
 * <h3>트랜잭션 전략 — REQUIRES_NEW</h3>
 * <p>감사 로그 쓰기는 반드시 <b>별도 트랜잭션</b>({@link Propagation#REQUIRES_NEW})에서
 * 수행한다. 그 이유는 다음과 같다.</p>
 * <ul>
 *   <li>감사 로그 쓰기가 어떤 이유로든 실패(컬럼 크기 초과, 락 대기 초과 등)할 경우,
 *       기본 전파 방식({@code REQUIRED})에서는 상위 업무 트랜잭션을 {@code rollback-only}
 *       상태로 마킹하여 정상적인 업무 처리(환불·포인트 변동 등)까지 함께 롤백시킨다.
 *       이는 "감사 때문에 업무가 깨지는" 본말전도이므로 반드시 분리해야 한다.</li>
 *   <li>반대로 업무 트랜잭션이 실패하는 경우에는 어차피 호출자가 예외를 전파하여
 *       이 메서드 자체가 호출되지 않으므로, "일어나지 않은 일"이 로그에 남지 않는다.</li>
 * </ul>
 *
 * <p>또한 감사 로그 저장 로직 자체를 {@code try/catch}로 감싸서 쓰기 실패 시
 * {@code log.warn} 만 남기고 예외를 삼킨다. 업무 트랜잭션은 이미 REQUIRES_NEW 로
 * 격리되어 있으므로 이 catch는 방어선을 한 번 더 두는 것에 가깝다.</p>
 *
 * <h3>adminId 처리 정책</h3>
 * <p>{@link AdminAuditLog#getAdminId()} 는 {@code BIGINT} 타입이지만 현재 인증 컨텍스트
 * ({@link SecurityContextHolder})는 {@code auth.getName()} 으로 사용자 식별 문자열만
 * 제공한다(보통 user_id VARCHAR). 별도 {@code admin} 테이블과의 매핑 조회를 추가하면
 * 감사 로그 한 건당 SELECT 1회가 더해지고 매핑이 없는 환경(시드 계정 등)에서 NPE
 * 위험이 발생한다. 따라서 본 서비스는 다음 타협을 적용한다.</p>
 * <ul>
 *   <li>{@code adminId} (Long) 컬럼은 항상 {@code null} 로 저장.</li>
 *   <li>실제 수행자 식별자는 {@code description} 필드 앞에 {@code [user_abc123]} 형식
 *       prefix 로 기록하여 조회 시 육안으로 즉시 판별 가능.</li>
 *   <li>추후 admin 테이블과의 FK 매핑이 필요해지면 이 서비스만 수정하면 된다
 *       (호출 측 변경 불필요).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    /** 감사 로그 JPA 리포지토리 */
    private final AdminAuditLogRepository adminAuditLogRepository;

    /** {@code description} 최대 길이 — TEXT 컬럼이지만 운영 가독성을 위해 상한을 둔다. */
    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    // ──────────────────────────────────────────────────────────
    // actionType 상수 — 호출 측이 오타를 내지 않도록 상수로 제공한다.
    // AdminAuditLog 엔티티의 Javadoc §actionType 예시와 일관되게 명명한다.
    // ──────────────────────────────────────────────────────────

    /** 결제 주문 환불 */
    public static final String ACTION_PAYMENT_REFUND       = "PAYMENT_REFUND";
    /** 결제 주문 보상 복구 (COMPENSATION_FAILED → COMPLETED) */
    public static final String ACTION_PAYMENT_COMPENSATE   = "PAYMENT_COMPENSATE";
    /** 구독 관리자 강제 취소 */
    public static final String ACTION_SUBSCRIPTION_CANCEL  = "SUBSCRIPTION_CANCEL";
    /** 구독 관리자 연장 */
    public static final String ACTION_SUBSCRIPTION_EXTEND  = "SUBSCRIPTION_EXTEND";
    /** 포인트 수동 지급/차감 */
    public static final String ACTION_POINT_MANUAL         = "POINT_MANUAL";
    /** AI 이용권 수동 발급 */
    public static final String ACTION_AI_TOKEN_GRANT       = "AI_TOKEN_GRANT";
    /** 사용자 계정 정지 */
    public static final String ACTION_USER_SUSPEND         = "USER_SUSPEND";
    /** 사용자 계정 정지 해제 */
    public static final String ACTION_USER_UNSUSPEND       = "USER_UNSUSPEND";
    /** 사용자 역할 변경 (USER ↔ ADMIN) */
    public static final String ACTION_USER_ROLE_UPDATE     = "USER_ROLE_UPDATE";
    /**
     * CSV 내보내기 이벤트.
     *
     * <p>관리자가 통계/로그/마스터 데이터를 CSV 파일로 다운로드한 이벤트를 기록한다.
     * CSV 내보내기는 클라이언트(브라우저)에서 발생하므로 프론트엔드가 다운로드 직후
     * 전용 엔드포인트({@code POST /api/v1/admin/audit-logs/csv-export})를 명시적으로
     * 호출하여 이 로그를 남긴다.</p>
     *
     * <p>이 로그는 개인정보/매출 데이터 유출 추적, GDPR 감사 대응, 내부 통제 목적으로
     * 사용된다. 파일 내용 자체는 저장하지 않고 메타데이터(소스, 행 수, 필터)만 남긴다.</p>
     */
    public static final String ACTION_CSV_EXPORT           = "CSV_EXPORT";
    /**
     * 관리자 로그인 성공 (2026-04-14 추가).
     *
     * <p>관리자 전용 로그인(AdminLoginFilter: POST /api/v1/admin/auth/login) 또는
     * 일반 로그인(LoginFilter: POST /api/v1/auth/login) 로 ROLE_ADMIN 사용자가
     * 성공적으로 인증을 마친 직후 LoginSuccessHandler 가 본 액션을 기록한다.
     * "누가 언제 어떤 관리자 엔드포인트를 사용했는가"의 출발점을 추적하는 용도.</p>
     */
    public static final String ACTION_ADMIN_LOGIN          = "ADMIN_LOGIN";
    /**
     * 관리자 계정 생성 (2026-04-14 추가).
     *
     * <p>SUPER_ADMIN 이 기존 일반 사용자를 관리자로 승격시키고 {@code admin}
     * 테이블에 신규 레코드를 INSERT 한 이벤트를 기록한다.</p>
     */
    public static final String ACTION_ADMIN_ACCOUNT_CREATE = "ADMIN_ACCOUNT_CREATE";
    /**
     * 관리자 역할 변경 (2026-04-14 추가).
     *
     * <p>기존 {@link #ACTION_USER_ROLE_UPDATE} 는 users.user_role 컬럼의
     * USER ↔ ADMIN 전환을 기록한다. 본 액션은 admin.admin_role 컬럼의
     * 세부 역할 변경(SUPER_ADMIN ↔ MODERATOR 등)을 별도로 구분하기 위해
     * 분리 기록한다.</p>
     */
    public static final String ACTION_ADMIN_ROLE_UPDATE    = "ADMIN_ROLE_UPDATE";
    /**
     * 관리자 AI 에이전트가 실행한 작업 (2026-04-23 Step 6a 추가).
     *
     * <p>monglepick-agent 의 `tool_executor` 가 Tier 2/3 쓰기 tool 을 실행한 직후 Backend
     * 감사 EP `POST /api/v1/admin/audit-logs/agent` 로 callback 하여 이 actionType 으로
     * 기록된다. Backend EP 가 발행하는 기존 도메인별 actionType(POINT_MANUAL 등)과
     * **중복 기록**되는 점이 중요하다 — 한 줄은 Backend 내부 로직이, 한 줄은 에이전트
     * 트리거가 남겨 "어느 에이전트 턴이 어떤 쓰기를 유발했는지" 양방향 추적(설계서 §5.1,
     * §7.1)이 가능하다. description 에는 tool_name + 관리자 발화 원문이 담긴다.</p>
     */
    public static final String ACTION_AGENT_EXECUTED       = "AGENT_EXECUTED";
    /**
     * 고객센터 FAQ 등록 (2026-04-24 추가).
     *
     * <p>관리자가 새 FAQ 를 등록한 이벤트. targetId = faqId(Long → String),
     * targetType = {@link #TARGET_SUPPORT_FAQ}.</p>
     */
    public static final String ACTION_SUPPORT_FAQ_CREATE   = "SUPPORT_FAQ_CREATE";
    /**
     * 고객센터 FAQ 수정 (2026-04-24 추가).
     *
     * <p>질문/답변/카테고리/키워드/공개여부/순서 변경을 포함한 모든 FAQ 갱신 이벤트.
     * targetId = faqId(Long → String), targetType = {@link #TARGET_SUPPORT_FAQ}.</p>
     */
    public static final String ACTION_SUPPORT_FAQ_UPDATE   = "SUPPORT_FAQ_UPDATE";
    /**
     * 고객센터 FAQ 삭제 (2026-04-24 추가).
     *
     * <p>관리자가 FAQ 를 삭제한 이벤트. ES 인덱스에서도 동시에 제거된다.
     * targetId = faqId(Long → String), targetType = {@link #TARGET_SUPPORT_FAQ}.</p>
     */
    public static final String ACTION_SUPPORT_FAQ_DELETE   = "SUPPORT_FAQ_DELETE";

    // ──────────────────────────────────────────────────────────
    // targetType 상수
    // ──────────────────────────────────────────────────────────

    /** 사용자 엔티티 */
    public static final String TARGET_USER         = "USER";
    /** 결제 주문 엔티티 */
    public static final String TARGET_PAYMENT      = "PAYMENT";
    /** 구독 엔티티 */
    public static final String TARGET_SUBSCRIPTION = "SUBSCRIPTION";
    /**
     * 내보낸 데이터 소스(논리 식별자) — 실제 엔티티가 아니라 "recommendation_logs",
     * "users", "payments" 같은 소스 코드 문자열.
     */
    public static final String TARGET_EXPORT_SOURCE = "EXPORT_SOURCE";
    /**
     * 관리자 계정 엔티티 (2026-04-14 추가).
     *
     * <p>admin 테이블의 adminId/userId 를 targetId 로 기록한다.
     * 로그인·계정 생성·역할 변경 등 admin 레코드 자체를 대상으로 하는 이벤트에 사용.</p>
     */
    public static final String TARGET_ADMIN         = "ADMIN";
    /**
     * 고객센터 FAQ 엔티티 (2026-04-24 추가).
     *
     * <p>support_faq 테이블의 faqId 를 targetId 로 기록한다.
     * FAQ 등록/수정/삭제 이벤트에 사용.</p>
     */
    public static final String TARGET_SUPPORT_FAQ   = "SUPPORT_FAQ";

    // ──────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────

    /**
     * 감사 로그 저장 — 간단 버전 (before/after 스냅샷 없음).
     *
     * @param actionType  행위 유형 (위 상수 사용 권장)
     * @param targetType  대상 엔티티 유형 (위 상수 사용 권장, nullable)
     * @param targetId    대상 엔티티 식별자 (VARCHAR 100 상한, nullable)
     * @param description 사람이 읽을 수 있는 설명 (최대 2000자, 앞쪽에 actor prefix 자동 추가)
     */
    public void log(String actionType, String targetType, String targetId, String description) {
        log(actionType, targetType, targetId, description, null, null);
    }

    /**
     * 감사 로그 저장 — 상세 버전 (변경 전/후 스냅샷 포함).
     *
     * <p>{@link Propagation#REQUIRES_NEW} 로 별도 트랜잭션에서 수행하여 업무 트랜잭션과
     * 완전히 격리한다. 저장 실패 시 경고 로그만 남기고 예외는 삼킨다 — 감사 기록 실패가
     * 업무 처리를 깨뜨리지 않도록 보장한다.</p>
     *
     * @param actionType  행위 유형 (위 상수 사용 권장, 필수)
     * @param targetType  대상 엔티티 유형 (nullable)
     * @param targetId    대상 엔티티 식별자 (nullable)
     * @param description 사람이 읽을 수 있는 설명 (앞쪽에 actor prefix 자동 추가)
     * @param beforeData  변경 전 스냅샷 JSON 문자열 (nullable)
     * @param afterData   변경 후 스냅샷 JSON 문자열 (nullable)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String actionType,
            String targetType,
            String targetId,
            String description,
            String beforeData,
            String afterData
    ) {
        try {
            // actor(수행자) 식별자를 description 앞쪽에 prefix 로 부착한다.
            // adminId Long 컬럼은 현재 인증 컨텍스트가 문자열만 제공하므로 NULL 로 둔다.
            String actor = resolveCurrentActor();
            String fullDescription = formatDescription(actor, description);

            AdminAuditLog entry = AdminAuditLog.builder()
                    .adminId(null)                   // TODO: admin 테이블 매핑 도입 시 여기에 연결
                    .actionType(actionType)
                    .targetType(targetType)
                    .targetId(truncateTargetId(targetId))
                    .description(fullDescription)
                    .ipAddress(null)                 // TODO: HttpServletRequest 연동 시 기록
                    .beforeData(beforeData)
                    .afterData(afterData)
                    .build();

            adminAuditLogRepository.save(entry);

            log.debug("[감사 로그] 저장 완료 — actor={}, actionType={}, targetType={}, targetId={}",
                    actor, actionType, targetType, targetId);
        } catch (Exception e) {
            // 감사 로그 실패는 업무 트랜잭션에 영향을 주지 않는다 — 경고만 남기고 삼킨다.
            // REQUIRES_NEW 로 격리되어 있어도 catch 를 한 번 더 두어 호출자 쪽에 예외가
            // 전파될 가능성을 원천 차단한다.
            log.warn("[감사 로그] 저장 실패 — actionType={}, targetType={}, targetId={}, err={}",
                    actionType, targetType, targetId, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────────────────

    /**
     * 현재 인증된 사용자 식별자를 반환한다.
     *
     * <p>{@link SecurityContextHolder} 의 {@code Authentication.getName()} 이
     * JWT filter 에서 세팅한 user_id(VARCHAR)를 반환한다고 가정한다.
     * 미인증/익명 컨텍스트(예: 시스템 스케줄러)에서는 "SYSTEM" 을 반환한다.</p>
     */
    private String resolveCurrentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return "SYSTEM";
            }
            return auth.getName();
        } catch (Exception e) {
            // SecurityContext 접근 실패 — 매우 드문 케이스이므로 기본값 반환
            return "UNKNOWN";
        }
    }

    /**
     * actor prefix 와 설명을 병합한 최종 description 문자열을 생성한다.
     *
     * <p>출력 예시: {@code "[user_admin001] 사용자 user_abc 정지 (사유: 비속어 반복)"}</p>
     */
    private String formatDescription(String actor, String rawDescription) {
        String safeDescription = rawDescription != null ? rawDescription : "";
        String merged = "[" + actor + "] " + safeDescription;

        // 설명 길이 상한 — TEXT 컬럼이지만 UI 렌더링/스크롤 편의상 2000자 제한
        if (merged.length() > DESCRIPTION_MAX_LENGTH) {
            return merged.substring(0, DESCRIPTION_MAX_LENGTH - 3) + "...";
        }
        return merged;
    }

    /**
     * targetId 가 VARCHAR(100) 컬럼 상한을 넘지 않도록 절단한다.
     * 일반적으로 UUID/user_id 모두 100자 이내지만 예기치 못한 긴 값이 들어와도 DB 예외가
     * 발생하지 않도록 방어한다.
     */
    private String truncateTargetId(String targetId) {
        if (targetId == null) return null;
        return targetId.length() > 100 ? targetId.substring(0, 100) : targetId;
    }
}
