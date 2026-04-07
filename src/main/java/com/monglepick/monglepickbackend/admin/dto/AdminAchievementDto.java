package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 업적(AchievementType) 마스터 관리 DTO 모음.
 *
 * <p>업적 등록·수정·비활성화 화면에서 사용하는 요청/응답 record를 모두 정의한다.
 * AchievementType 엔티티의 가공된 표현이며, 관리자만 사용한다.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>{@link CreateAchievementRequest} — 신규 업적 등록</li>
 *   <li>{@link UpdateAchievementRequest} — 기존 업적 수정 (achievement_code 제외)</li>
 *   <li>{@link UpdateActiveRequest}      — 활성/비활성 토글</li>
 *   <li>{@link AchievementResponse}      — 업적 단일 항목 응답</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>{@code achievementCode}는 신규 등록 시에만 입력 받고, 수정 시에는 변경 불가
 *       (서비스 로직에서 이 코드로 달성 판정을 하므로 변경하면 기존 달성 기록과의 연결이 끊김).</li>
 *   <li>{@code rewardPoints}는 0 이상 정수만 허용 (음수 금지).</li>
 *   <li>{@code requiredCount}는 nullable — null이면 1회 달성 업적으로 취급.</li>
 *   <li>비활성화는 별도 토글 EP({@link UpdateActiveRequest})로 분리 — 활성/비활성만 빠르게 변경 가능.</li>
 * </ul>
 */
public class AdminAchievementDto {

    // ─────────────────────────────────────────────
    // 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 신규 업적 등록 요청 DTO.
     *
     * <p>achievement_code는 UNIQUE 제약이 있으므로 서비스 레이어에서
     * existsByAchievementCode 로 사전 검증한다.</p>
     *
     * @param achievementCode 업적 코드 (영문 소문자+언더스코어, 50자 이내, UNIQUE, 필수)
     * @param achievementName 표시명 (한국어, 100자 이내, 필수)
     * @param description     설명 (TEXT, 선택)
     * @param requiredCount   달성 조건 횟수 (nullable, null=1회)
     * @param rewardPoints    달성 시 지급 포인트 (0 이상, 기본 0)
     * @param iconUrl         아이콘 URL (500자 이내, 선택)
     * @param category        카테고리 (VIEWING/SOCIAL/COLLECTION/CHALLENGE/null)
     */
    public record CreateAchievementRequest(
            @NotBlank(message = "업적 코드는 필수입니다.")
            @Size(max = 50, message = "업적 코드는 50자 이하여야 합니다.")
            String achievementCode,

            @NotBlank(message = "업적 표시명은 필수입니다.")
            @Size(max = 100, message = "업적 표시명은 100자 이하여야 합니다.")
            String achievementName,

            String description,

            @PositiveOrZero(message = "달성 조건 횟수는 0 이상이어야 합니다.")
            Integer requiredCount,

            @PositiveOrZero(message = "보상 포인트는 0 이상이어야 합니다.")
            Integer rewardPoints,

            @Size(max = 500, message = "아이콘 URL은 500자 이하여야 합니다.")
            String iconUrl,

            @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
            String category
    ) {}

    /**
     * 기존 업적 수정 요청 DTO (achievement_code 제외).
     *
     * <p>업적 코드는 사용자 달성 기록과 연결된 식별자이므로 수정 불가.
     * 표시명/설명/조건/보상/아이콘/카테고리만 수정 가능하다.</p>
     */
    public record UpdateAchievementRequest(
            @NotBlank(message = "업적 표시명은 필수입니다.")
            @Size(max = 100, message = "업적 표시명은 100자 이하여야 합니다.")
            String achievementName,

            String description,

            @PositiveOrZero(message = "달성 조건 횟수는 0 이상이어야 합니다.")
            Integer requiredCount,

            @PositiveOrZero(message = "보상 포인트는 0 이상이어야 합니다.")
            Integer rewardPoints,

            @Size(max = 500, message = "아이콘 URL은 500자 이하여야 합니다.")
            String iconUrl,

            @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
            String category
    ) {}

    /**
     * 활성/비활성 토글 요청 DTO.
     *
     * <p>{@code isActive=false}이면 신규 달성 기록 생성이 차단된다.
     * 기존 달성 기록은 보존된다.</p>
     */
    public record UpdateActiveRequest(
            Boolean isActive
    ) {}

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 업적 단일 항목 응답 DTO.
     *
     * <p>관리자 화면 테이블 한 행에 표시되는 모든 컬럼을 포함한다.</p>
     *
     * @param achievementTypeId 업적 유형 PK
     * @param achievementCode   업적 코드 (UNIQUE)
     * @param achievementName   표시명
     * @param description       설명 (nullable)
     * @param requiredCount     달성 조건 횟수 (nullable)
     * @param rewardPoints      달성 시 지급 포인트
     * @param iconUrl           아이콘 URL (nullable)
     * @param category          카테고리 (nullable)
     * @param isActive          활성화 여부
     * @param createdAt         생성 시각
     * @param updatedAt         수정 시각
     */
    public record AchievementResponse(
            Long achievementTypeId,
            String achievementCode,
            String achievementName,
            String description,
            Integer requiredCount,
            Integer rewardPoints,
            String iconUrl,
            String category,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
