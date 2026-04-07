package com.monglepick.monglepickbackend.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.HistoryResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.PolicyResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRewardPolicyDto.UpdateRequest;
import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicy;
import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicyHistory;
import com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 관리자 리워드 정책(RewardPolicy) 마스터 관리 서비스.
 *
 * <p>활동별 포인트 정책의 등록·수정·활성화 토글 비즈니스 로직을 담당한다.
 * 모든 변경 작업은 RewardPolicyHistory 에 INSERT-ONLY 원장으로 자동 기록된다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>정책 목록 페이징 조회 (활성/비활성 모두)</li>
 *   <li>정책 단건 조회</li>
 *   <li>신규 정책 등록 (action_type UNIQUE) + 이력 INSERT</li>
 *   <li>정책 메타 수정 (actionType 제외) + before/after 이력 INSERT</li>
 *   <li>활성/비활성 토글 + 이력 INSERT</li>
 *   <li>특정 정책의 변경 이력 조회 (최신순)</li>
 * </ol>
 *
 * <h3>변경 이력 자동 기록</h3>
 * <p>모든 update 작업 시 변경 전/후 정책 스냅샷을 Jackson으로 JSON 직렬화하여
 * RewardPolicyHistory 에 저장한다. {@code @PreUpdate}/{@code @PreRemove} 차단 덕분에
 * 이력 레코드는 사후 변경/삭제 불가.</p>
 *
 * <h3>운영 주의사항</h3>
 * <p>actionType은 {@code RewardService.grantReward()}에서 활동 코드로 정책을 조회하는 키이다.
 * 변경하면 모든 호출처를 동시에 수정해야 하므로 신규 등록 시에만 입력 받는다.
 * 폐지된 정책은 hard delete 대신 isActive=false 토글 사용을 권장한다 (이력 보존).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRewardPolicyService {

    private final RewardPolicyRepository rewardPolicyRepository;
    private final RewardPolicyHistoryRepository historyRepository;

    /** Jackson ObjectMapper — Spring Boot 자동 등록 빈 주입 */
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    public Page<PolicyResponse> getPolicies(Pageable pageable) {
        return rewardPolicyRepository.findAll(pageable).map(this::toResponse);
    }

    public PolicyResponse getPolicy(Long id) {
        return toResponse(findOrThrow(id));
    }

    public List<HistoryResponse> getPolicyHistory(Long policyId) {
        // 정책 존재 검증
        findOrThrow(policyId);
        return historyRepository.findByPolicyIdOrderByCreatedAtDesc(policyId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    @Transactional
    public PolicyResponse createPolicy(CreateRequest request) {
        if (rewardPolicyRepository.existsByActionType(request.actionType())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REWARD_POLICY);
        }

        RewardPolicy entity = RewardPolicy.builder()
                .actionType(request.actionType())
                .activityName(request.activityName())
                .actionCategory(request.actionCategory())
                .pointsAmount(request.pointsAmount())
                .pointType(request.pointType() != null ? request.pointType() : "earn")
                .dailyLimit(request.dailyLimit() != null ? request.dailyLimit() : 0)
                .maxCount(request.maxCount() != null ? request.maxCount() : 0)
                .cooldownSeconds(request.cooldownSeconds() != null ? request.cooldownSeconds() : 0)
                .minContentLength(request.minContentLength() != null ? request.minContentLength() : 0)
                .limitType(request.limitType())
                .thresholdCount(request.thresholdCount() != null ? request.thresholdCount() : 0)
                .thresholdTarget(request.thresholdTarget())
                .parentActionType(request.parentActionType())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .description(request.description())
                .build();

        RewardPolicy saved = rewardPolicyRepository.save(entity);

        // 이력 INSERT — 신규 등록은 beforeValue=null
        recordHistory(saved.getPolicyId(), null, saved,
                request.changeReason() != null ? request.changeReason() : "신규 정책 등록");

        log.info("[관리자] 리워드 정책 등록 — id={}, actionType={}, points={}",
                saved.getPolicyId(), saved.getActionType(), saved.getPointsAmount());
        return toResponse(saved);
    }

    @Transactional
    public PolicyResponse updatePolicy(Long id, UpdateRequest request) {
        RewardPolicy entity = findOrThrow(id);

        // before 스냅샷 (변경 전 상태)
        RewardPolicy before = cloneSnapshot(entity);

        // 도메인 메서드로 변경 (null이면 기존 값 유지)
        entity.updatePolicy(
                request.pointsAmount(),
                request.dailyLimit(),
                request.maxCount(),
                request.cooldownSeconds(),
                request.minContentLength(),
                request.description()
        );

        recordHistory(entity.getPolicyId(), before, entity,
                request.changeReason() != null ? request.changeReason() : "정책 메타 수정");

        log.info("[관리자] 리워드 정책 수정 — id={}, actionType={}", id, entity.getActionType());
        return toResponse(entity);
    }

    @Transactional
    public PolicyResponse updateActive(Long id, UpdateActiveRequest request) {
        RewardPolicy entity = findOrThrow(id);
        RewardPolicy before = cloneSnapshot(entity);

        boolean newActive = Boolean.TRUE.equals(request.isActive());
        entity.updateActiveStatus(newActive);

        recordHistory(entity.getPolicyId(), before, entity,
                request.changeReason() != null
                        ? request.changeReason()
                        : (newActive ? "정책 활성화" : "정책 비활성화"));

        log.info("[관리자] 리워드 정책 활성 토글 — id={}, isActive={}", id, newActive);
        return toResponse(entity);
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    private RewardPolicy findOrThrow(Long id) {
        return rewardPolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REWARD_POLICY_NOT_FOUND,
                        "리워드 정책 ID " + id + "를 찾을 수 없습니다"));
    }

    /**
     * 변경 전 상태를 메모리 스냅샷으로 복제 (DB 저장 안 함).
     *
     * <p>도메인 메서드 호출 후 dirty checking이 적용되기 전에 before 값을 보존하기 위해
     * 새 인스턴스로 필드 값을 복사한다.</p>
     */
    private RewardPolicy cloneSnapshot(RewardPolicy original) {
        return RewardPolicy.builder()
                .policyId(original.getPolicyId())
                .actionType(original.getActionType())
                .activityName(original.getActivityName())
                .actionCategory(original.getActionCategory())
                .pointsAmount(original.getPointsAmount())
                .pointType(original.getPointType())
                .dailyLimit(original.getDailyLimit())
                .maxCount(original.getMaxCount())
                .cooldownSeconds(original.getCooldownSeconds())
                .minContentLength(original.getMinContentLength())
                .limitType(original.getLimitType())
                .thresholdCount(original.getThresholdCount())
                .thresholdTarget(original.getThresholdTarget())
                .parentActionType(original.getParentActionType())
                .isActive(original.getIsActive())
                .description(original.getDescription())
                .build();
    }

    /**
     * 변경 이력을 INSERT-ONLY 원장에 기록한다.
     *
     * <p>before/after를 Jackson으로 JSON 직렬화한다.
     * 신규 등록(before=null) 시에는 beforeValue=null로 저장.</p>
     */
    private void recordHistory(Long policyId, RewardPolicy before, RewardPolicy after, String reason) {
        try {
            String beforeJson = (before != null) ? objectMapper.writeValueAsString(snapshotMap(before)) : null;
            String afterJson = objectMapper.writeValueAsString(snapshotMap(after));

            RewardPolicyHistory history = RewardPolicyHistory.builder()
                    .policyId(policyId)
                    .changedBy(resolveCurrentAdminId())
                    .changeReason(reason)
                    .beforeValue(beforeJson)
                    .afterValue(afterJson)
                    .build();

            historyRepository.save(history);
        } catch (JsonProcessingException e) {
            log.error("[관리자] 리워드 정책 변경 이력 직렬화 실패 — policyId={}, error={}",
                    policyId, e.getMessage(), e);
            // 이력 기록 실패는 정책 저장을 막지 않는다 (best effort)
        }
    }

    /**
     * 정책 엔티티를 핵심 필드만 추린 Map으로 변환 (이력 직렬화용).
     *
     * <p>전체 엔티티를 직렬화하지 않는 이유: BaseAuditEntity 상속 필드와 hibernate proxy
     * 처리 복잡성 회피.</p>
     */
    private Map<String, Object> snapshotMap(RewardPolicy p) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("actionType", p.getActionType());
        map.put("activityName", p.getActivityName());
        map.put("actionCategory", p.getActionCategory());
        map.put("pointsAmount", p.getPointsAmount());
        map.put("pointType", p.getPointType());
        map.put("dailyLimit", p.getDailyLimit());
        map.put("maxCount", p.getMaxCount());
        map.put("cooldownSeconds", p.getCooldownSeconds());
        map.put("minContentLength", p.getMinContentLength());
        map.put("limitType", p.getLimitType());
        map.put("thresholdCount", p.getThresholdCount());
        map.put("thresholdTarget", p.getThresholdTarget());
        map.put("parentActionType", p.getParentActionType());
        map.put("isActive", p.getIsActive());
        map.put("description", p.getDescription());
        return map;
    }

    /** SecurityContextHolder에서 현재 관리자 ID 추출 */
    private String resolveCurrentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "SYSTEM";
        }
        return auth.getName();
    }

    private PolicyResponse toResponse(RewardPolicy entity) {
        return new PolicyResponse(
                entity.getPolicyId(),
                entity.getActionType(),
                entity.getActivityName(),
                entity.getActionCategory(),
                entity.getPointsAmount(),
                entity.getPointType(),
                entity.getDailyLimit(),
                entity.getMaxCount(),
                entity.getCooldownSeconds(),
                entity.getMinContentLength(),
                entity.getLimitType(),
                entity.getThresholdCount(),
                entity.getThresholdTarget(),
                entity.getParentActionType(),
                entity.getIsActive(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private HistoryResponse toHistoryResponse(RewardPolicyHistory entity) {
        return new HistoryResponse(
                entity.getRewardPolicyHistoryId(),
                entity.getPolicyId(),
                entity.getChangedBy(),
                entity.getChangeReason(),
                entity.getBeforeValue(),
                entity.getAfterValue(),
                entity.getCreatedAt()
        );
    }
}
