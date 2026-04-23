package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 설정 관리 API DTO 모음.
 *
 * <p>관리자 페이지 "설정" 탭의 하위 기능인 약관/정책, 배너, 감사 로그, 관리자 계정 관리에서
 * 사용되는 Request/Response DTO를 중첩 record 클래스로 정의한다.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>약관: {@link TermsResponse}, {@link TermsCreateRequest}, {@link TermsUpdateRequest}</li>
 *   <li>배너: {@link BannerResponse}, {@link BannerCreateRequest}, {@link BannerUpdateRequest}</li>
 *   <li>감사 로그: {@link AuditLogResponse}</li>
 *   <li>관리자 계정: {@link AdminAccountResponse}, {@link AdminRoleUpdateRequest}</li>
 * </ul>
 */
public class SettingsDto {

    // ======================== 약관/정책 DTO ========================

    /**
     * 약관 단건 응답 DTO.
     *
     * @param id         약관 고유 ID
     * @param title      약관 제목
     * @param content    약관 전문
     * @param type       약관 유형 코드 (TERMS_OF_SERVICE, PRIVACY_POLICY 등)
     * @param version    버전 문자열 ("1.0", "2.0" 등)
     * @param isRequired 필수 동의 여부
     * @param isActive   활성화 여부
     * @param createdAt  등록 일시
     * @param updatedAt  최종 수정 일시
     */
    public record TermsResponse(
            Long id,
            String title,
            String content,
            String type,
            String version,
            Boolean isRequired,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * 약관 등록 요청 DTO.
     *
     * @param title      약관 제목 (필수, 최대 200자)
     * @param content    약관 전문 (필수)
     * @param type       약관 유형 코드 (필수, 최대 50자)
     * @param version    버전 문자열 (최대 20자)
     * @param isRequired 필수 동의 여부 (필수)
     */
    public record TermsCreateRequest(
            @NotBlank(message = "약관 제목은 필수입니다.")
            @Size(max = 200, message = "약관 제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "약관 전문은 필수입니다.")
            String content,

            @NotBlank(message = "약관 유형은 필수입니다.")
            @Size(max = 50, message = "약관 유형 코드는 최대 50자입니다.")
            String type,

            @Size(max = 20, message = "버전 문자열은 최대 20자입니다.")
            String version,

            @NotNull(message = "필수 동의 여부는 필수입니다.")
            Boolean isRequired
    ) {}

    /**
     * 약관 수정 요청 DTO.
     *
     * @param title      새 약관 제목 (필수, 최대 200자)
     * @param content    새 약관 전문 (필수)
     * @param type       새 약관 유형 코드 (필수, 최대 50자)
     * @param version    새 버전 문자열 (최대 20자)
     * @param isRequired 새 필수 동의 여부 (필수)
     */
    public record TermsUpdateRequest(
            @NotBlank(message = "약관 제목은 필수입니다.")
            @Size(max = 200, message = "약관 제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "약관 전문은 필수입니다.")
            String content,

            @NotBlank(message = "약관 유형은 필수입니다.")
            @Size(max = 50, message = "약관 유형 코드는 최대 50자입니다.")
            String type,

            @Size(max = 20, message = "버전 문자열은 최대 20자입니다.")
            String version,

            @NotNull(message = "필수 동의 여부는 필수입니다.")
            Boolean isRequired
    ) {}

    // ======================== 배너 DTO ========================

    /**
     * 배너 단건 응답 DTO.
     *
     * @param id        배너 고유 ID
     * @param title     배너 제목
     * @param imageUrl  배너 이미지 URL
     * @param linkUrl   클릭 시 이동 URL (nullable)
     * @param position  노출 위치 코드 (MAIN, SUB, POPUP)
     * @param sortOrder 정렬 순서 (낮을수록 우선)
     * @param isActive  활성화 여부
     * @param startDate 게시 시작 일시 (nullable)
     * @param endDate   게시 종료 일시 (nullable)
     * @param createdAt 등록 일시
     */
    public record BannerResponse(
            Long id,
            String title,
            String imageUrl,
            String linkUrl,
            String position,
            Integer sortOrder,
            Boolean isActive,
            LocalDateTime startDate,
            LocalDateTime endDate,
            LocalDateTime createdAt
    ) {}

    /**
     * 배너 등록 요청 DTO.
     *
     * @param title     배너 제목 (필수, 최대 200자)
     * @param imageUrl  배너 이미지 URL (필수, 최대 500자)
     * @param linkUrl   클릭 시 이동 URL (최대 500자, nullable)
     * @param position  노출 위치 코드 (최대 50자)
     * @param sortOrder 정렬 순서
     * @param startDate 게시 시작 일시 (nullable)
     * @param endDate   게시 종료 일시 (nullable)
     */
    public record BannerCreateRequest(
            @NotBlank(message = "배너 제목은 필수입니다.")
            @Size(max = 200, message = "배너 제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "배너 이미지 URL은 필수입니다.")
            @Size(max = 500, message = "이미지 URL은 최대 500자입니다.")
            String imageUrl,

            @Size(max = 500, message = "링크 URL은 최대 500자입니다.")
            String linkUrl,

            @Size(max = 50, message = "위치 코드는 최대 50자입니다.")
            String position,

            Integer sortOrder,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {}

    /**
     * 배너 수정 요청 DTO.
     *
     * @param title     새 배너 제목 (필수, 최대 200자)
     * @param imageUrl  새 이미지 URL (필수, 최대 500자)
     * @param linkUrl   새 링크 URL (최대 500자, nullable)
     * @param position  새 노출 위치 코드 (최대 50자)
     * @param sortOrder 새 정렬 순서
     * @param isActive  새 활성화 여부
     * @param startDate 새 게시 시작 일시 (nullable)
     * @param endDate   새 게시 종료 일시 (nullable)
     */
    public record BannerUpdateRequest(
            @NotBlank(message = "배너 제목은 필수입니다.")
            @Size(max = 200, message = "배너 제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "배너 이미지 URL은 필수입니다.")
            @Size(max = 500, message = "이미지 URL은 최대 500자입니다.")
            String imageUrl,

            @Size(max = 500, message = "링크 URL은 최대 500자입니다.")
            String linkUrl,

            @Size(max = 50, message = "위치 코드는 최대 50자입니다.")
            String position,

            Integer sortOrder,

            @NotNull(message = "활성화 여부는 필수입니다.")
            Boolean isActive,

            LocalDateTime startDate,
            LocalDateTime endDate
    ) {}

    // ======================== 감사 로그 DTO ========================

    /**
     * 감사 로그 단건 응답 DTO.
     *
     * <p>2026-04-09 P2-⑲ 확장: {@code beforeData} / {@code afterData} 필드 추가.
     * 엔티티에는 JSON 컬럼으로 존재했으나 기존 DTO 가 이를 노출하지 않아 Frontend 에서
     * 변경 전/후 스냅샷을 볼 수 없었다. JSON Diff 뷰어 지원을 위해 필드를 추가한다.</p>
     *
     * @param id          감사 로그 고유 ID
     * @param adminId     행위를 수행한 관리자 레코드 ID (시스템 자동 처리 시 null)
     * @param actionType  행위 유형 코드 (USER_SUSPEND, POINT_MANUAL 등)
     * @param targetType  대상 엔티티 유형 (USER, POST, PAYMENT 등)
     * @param targetId    대상 엔티티 식별자
     * @param description 행위에 대한 사람이 읽을 수 있는 설명
     * @param ipAddress   관리자 클라이언트 IP 주소
     * @param beforeData  변경 전 데이터 스냅샷 (JSON 문자열, nullable). UPDATE/DELETE 에 사용
     * @param afterData   변경 후 데이터 스냅샷 (JSON 문자열, nullable). INSERT/UPDATE 에 사용
     * @param createdAt   로그 기록 일시
     */
    public record AuditLogResponse(
            Long id,
            Long adminId,
            String actionType,
            String targetType,
            String targetId,
            String description,
            String ipAddress,
            String beforeData,
            String afterData,
            LocalDateTime createdAt
    ) {}

    // ======================== 관리자 계정 DTO ========================

    /**
     * 관리자 계정 단건 응답 DTO.
     *
     * <p>2026-04-14 확장: {@code email}, {@code nickname} 필드 추가.
     * 기존에는 admin 테이블 컬럼만 매핑하여 운영자가 관리자 계정 탭에서
     * "누가 관리자인지" 사람이 읽을 수 있는 정보를 볼 수 없었다. 프론트엔드가
     * 기대하는 필드(admin.email, admin.nickname)와도 일치시킨다.</p>
     *
     * <p>{@code email}/{@code nickname}은 {@code users} 테이블에서 조회하여 채운다.
     * users 레코드가 없거나 탈퇴한 경우 null 이 들어올 수 있다.</p>
     *
     * @param adminId     관리자 레코드 고유 ID
     * @param userId      사용자 ID (users 테이블 FK)
     * @param email       사용자 이메일 — users.email 조인 (nullable)
     * @param nickname    사용자 닉네임 — users.nickname 조인 (nullable)
     * @param adminRole   관리자 역할 (ADMIN, SUPER_ADMIN 등)
     * @param isActive    관리자 권한 활성 여부
     * @param lastLoginAt 관리자 테이블 기준 마지막 로그인 일시 (nullable)
     * @param createdAt   관리자 계정 등록 일시
     */
    public record AdminAccountResponse(
            Long adminId,
            String userId,
            String email,
            String nickname,
            String adminRole,
            Boolean isActive,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {}

    /**
     * 관리자 역할 수정 요청 DTO.
     *
     * @param adminRole 새 관리자 역할 (필수, 최대 50자). 예: "ADMIN", "SUPER_ADMIN"
     */
    public record AdminRoleUpdateRequest(
            @NotBlank(message = "관리자 역할은 필수입니다.")
            @Size(max = 50, message = "관리자 역할 코드는 최대 50자입니다.")
            String adminRole
    ) {}

    /**
     * 관리자 계정 신규 등록 요청 DTO — 2026-04-14 신규.
     *
     * <p>SUPER_ADMIN 이 기존 일반 사용자를 관리자로 승격시킬 때 사용한다.
     * email 또는 userId 중 하나는 반드시 전달해야 한다. 둘 다 제공되면 userId 가 우선한다.</p>
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>userId 또는 email 로 대상 User 엔티티 조회 (없으면 G002).</li>
     *   <li>이미 admin 레코드가 있는지 확인 (중복이면 G001).</li>
     *   <li>users.user_role 을 ADMIN 으로 승격 + admin 테이블에 신규 레코드 INSERT.</li>
     *   <li>admin_audit_logs 에 ADMIN_ACCOUNT_CREATE 이벤트 기록.</li>
     * </ol>
     *
     * @param userId    사용자 ID (VARCHAR 50, nullable — email 과 상호 배타)
     * @param email     사용자 이메일 (nullable — userId 와 상호 배타)
     * @param adminRole 부여할 관리자 역할 코드 (필수, {@link com.monglepick.monglepickbackend.global.constants.AdminRole} 허용값)
     */
    public record AdminAccountCreateRequest(
            @Size(max = 50, message = "userId 는 최대 50자입니다.")
            String userId,

            @Size(max = 200, message = "email 은 최대 200자입니다.")
            String email,

            @NotBlank(message = "관리자 역할은 필수입니다.")
            @Size(max = 50, message = "관리자 역할 코드는 최대 50자입니다.")
            String adminRole
    ) {}

    // ======================== CSV 내보내기 감사 로그 DTO ========================

    /**
     * CSV 내보내기 이벤트 기록 요청 DTO — 2026-04-09 신규 추가.
     *
     * <p>관리자가 브라우저에서 CSV 다운로드를 완료한 직후, 프론트엔드가 이 DTO 로
     * {@code POST /api/v1/admin/audit-logs/csv-export} 를 호출하여
     * {@code admin_audit_logs} 테이블에 해당 이벤트를 기록한다.</p>
     *
     * <h3>필드 설명</h3>
     * <ul>
     *   <li>{@code source}     — 내보낸 데이터 소스의 논리 식별자 (예: "recommendation_logs",
     *       "users", "payments"). 프론트엔드 호출 측에서 명시적으로 지정한다.</li>
     *   <li>{@code filename}   — 실제 다운로드된 파일명 (예: "recommendation_logs_7d_2026-04-09.csv")</li>
     *   <li>{@code rowCount}   — 내보낸 행 수. 개인정보 유출 영향도 판단의 근거가 된다.</li>
     *   <li>{@code filterInfo} — 내보내기 시점의 필터 조건을 사람이 읽을 수 있는 간단한
     *       문자열로 직렬화한 값. 예: "period=7d, status=COMPLETED". nullable.</li>
     * </ul>
     *
     * <h3>왜 파일 내용은 저장하지 않는가</h3>
     * <p>파일 원본을 서버에 저장하면 저장 공간/개인정보 재유출 리스크가 커진다. 감사 목적
     * 상으로는 "누가 언제 어떤 소스에서 몇 건을 내보냈는가" 메타데이터만으로 충분하며,
     * 실제 데이터는 소스 테이블에서 언제든 재조회 가능하다.</p>
     */
    public record CsvExportLogRequest(
            @NotBlank(message = "내보내기 소스는 필수입니다.")
            @Size(max = 100, message = "소스 코드는 최대 100자입니다.")
            String source,

            @Size(max = 200, message = "파일명은 최대 200자입니다.")
            String filename,

            @NotNull(message = "행 수는 필수입니다.")
            Integer rowCount,

            @Size(max = 500, message = "필터 정보는 최대 500자입니다.")
            String filterInfo
    ) {}

    /**
     * Agent 가 실행한 관리 작업의 감사 로그 등록 요청 DTO (2026-04-23 Step 6a 신규).
     *
     * <p>관리자 AI 어시스턴트(monglepick-agent) 가 Tier 2/3 쓰기 tool 을 실행한 뒤 Backend
     * 로 callback 하여 이 엔드포인트에 POST 한다. actor prefix 는 `AdminAuditService.log()`
     * 내부에서 `SecurityContext.auth.getName()` 로 자동 삽입되므로, Agent 는 JWT forwarding
     * 으로 해당 관리자 identity 를 유지한 상태에서 호출해야 한다.</p>
     *
     * <h3>필드 규칙</h3>
     * <ul>
     *   <li>{@code actionType} — 기본값 {@code AGENT_EXECUTED}. 호출자가 더 세부 타입을
     *       (예: {@code AGENT_FAQ_CREATE}) 지정해도 허용. 최대 50자.</li>
     *   <li>{@code targetType}/{@code targetId} — 영향받는 리소스 (nullable). Tier 1 읽기에는
     *       보통 null, Tier 2/3 쓰기에는 {@code TARGET_USER}/{@code TARGET_PAYMENT} 등.</li>
     *   <li>{@code description} — 필수. "[tool=faq_create] 관리자 프롬프트: '...'" 형식 권장.</li>
     *   <li>{@code beforeData}/{@code afterData} — 구조적 저장용 JSON 문자열 (nullable).
     *       Agent 는 Tier 3 실행 전후 리소스 스냅샷을 여기에 넣는다.</li>
     * </ul>
     */
    public record AgentAuditLogRequest(
            @Size(max = 50, message = "actionType 은 최대 50자입니다.")
            String actionType,

            @Size(max = 50, message = "targetType 은 최대 50자입니다.")
            String targetType,

            @Size(max = 100, message = "targetId 는 최대 100자입니다.")
            String targetId,

            @NotBlank(message = "description 은 필수입니다.")
            @Size(max = 2000, message = "description 은 최대 2000자입니다.")
            String description,

            String beforeData,

            String afterData
    ) {}
}
