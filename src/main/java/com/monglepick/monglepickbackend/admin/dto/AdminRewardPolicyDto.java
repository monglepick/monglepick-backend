package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 리워드 정책(RewardPolicy) 마스터 관리 DTO 모음.
 *
 * <p>활동별 포인트 정책 마스터 데이터의 등록·수정·활성화 토글 화면에서 사용한다.
 * 변경 시 RewardPolicyHistory에 INSERT-ONLY 원장으로 변경 이력이 자동 기록된다.</p>
 *
 * <h3>변경 불가 필드</h3>
 * <ul>
 *   <li>{@code actionType} — 시스템 식별 코드, 신규 등록 시에만 입력</li>
 * </ul>
 *
 * <h3>action_category 값</h3>
 * <p>CONTENT / ENGAGEMENT / MILESTONE / ATTENDANCE</p>
 *
 * <h3>point_type 값</h3>
 * <p>earn (등급 배율 적용) / bonus (고정 포인트, 마일스톤)</p>
 */
public class AdminRewardPolicyDto {

    /** 신규 정책 등록 요청 */
    public record CreateRequest(
            @NotBlank(message = "활동 코드(actionType)는 필수입니다.")
            @Size(max = 50, message = "활동 코드는 50자 이하여야 합니다.")
            String actionType,

            @NotBlank(message = "활동 표시명은 필수입니다.")
            @Size(max = 100, message = "활동 표시명은 100자 이하여야 합니다.")
            String activityName,

            @NotBlank(message = "활동 카테고리는 필수입니다.")
            @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
            String actionCategory,

            @NotNull(message = "지급 포인트는 필수입니다.")
            @PositiveOrZero(message = "지급 포인트는 0 이상이어야 합니다.")
            Integer pointsAmount,

            @Size(max = 50, message = "포인트 유형은 50자 이하여야 합니다.")
            String pointType,

            @PositiveOrZero(message = "일일 한도는 0 이상이어야 합니다.")
            Integer dailyLimit,

            @PositiveOrZero(message = "평생 한도는 0 이상이어야 합니다.")
            Integer maxCount,

            @PositiveOrZero(message = "쿨다운(초)은 0 이상이어야 합니다.")
            Integer cooldownSeconds,

            @PositiveOrZero(message = "최소 콘텐츠 길이는 0 이상이어야 합니다.")
            Integer minContentLength,

            @Size(max = 20, message = "limit_type은 20자 이하여야 합니다.")
            String limitType,

            @PositiveOrZero(message = "달성 기준 횟수는 0 이상이어야 합니다.")
            Integer thresholdCount,

            @Size(max = 30, message = "thresholdTarget은 30자 이하여야 합니다.")
            String thresholdTarget,

            @Size(max = 50, message = "parentActionType은 50자 이하여야 합니다.")
            String parentActionType,

            Boolean isActive,

            @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
            String description,

            String changeReason
    ) {}

    /** 정책 메타 수정 요청 (actionType 제외) */
    public record UpdateRequest(
            @PositiveOrZero(message = "지급 포인트는 0 이상이어야 합니다.")
            Integer pointsAmount,

            @PositiveOrZero(message = "일일 한도는 0 이상이어야 합니다.")
            Integer dailyLimit,

            @PositiveOrZero(message = "평생 한도는 0 이상이어야 합니다.")
            Integer maxCount,

            @PositiveOrZero(message = "쿨다운(초)은 0 이상이어야 합니다.")
            Integer cooldownSeconds,

            @PositiveOrZero(message = "최소 콘텐츠 길이는 0 이상이어야 합니다.")
            Integer minContentLength,

            @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
            String description,

            String changeReason
    ) {}

    /** 활성/비활성 토글 요청 */
    public record UpdateActiveRequest(
            Boolean isActive,
            String changeReason
    ) {}

    /** 정책 단건 응답 */
    public record PolicyResponse(
            Long policyId,
            String actionType,
            String activityName,
            String actionCategory,
            Integer pointsAmount,
            String pointType,
            Integer dailyLimit,
            Integer maxCount,
            Integer cooldownSeconds,
            Integer minContentLength,
            String limitType,
            Integer thresholdCount,
            String thresholdTarget,
            String parentActionType,
            Boolean isActive,
            String description,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** 변경 이력 응답 (INSERT-ONLY 원장) */
    public record HistoryResponse(
            Long historyId,
            Long policyId,
            String changedBy,
            String changeReason,
            String beforeValue,
            String afterValue,
            LocalDateTime createdAt
    ) {}
}
