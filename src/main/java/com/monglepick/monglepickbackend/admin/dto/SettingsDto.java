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
     * @param id          감사 로그 고유 ID
     * @param adminId     행위를 수행한 관리자 레코드 ID (시스템 자동 처리 시 null)
     * @param actionType  행위 유형 코드 (USER_SUSPEND, POINT_MANUAL 등)
     * @param targetType  대상 엔티티 유형 (USER, POST, PAYMENT 등)
     * @param targetId    대상 엔티티 식별자
     * @param description 행위에 대한 사람이 읽을 수 있는 설명
     * @param ipAddress   관리자 클라이언트 IP 주소
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
            LocalDateTime createdAt
    ) {}

    // ======================== 관리자 계정 DTO ========================

    /**
     * 관리자 계정 단건 응답 DTO.
     *
     * @param adminId     관리자 레코드 고유 ID
     * @param userId      사용자 ID (users 테이블 FK)
     * @param adminRole   관리자 역할 (ADMIN, SUPER_ADMIN 등)
     * @param isActive    관리자 권한 활성 여부
     * @param lastLoginAt 마지막 로그인 일시 (nullable)
     * @param createdAt   관리자 계정 등록 일시
     */
    public record AdminAccountResponse(
            Long adminId,
            String userId,
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
}
