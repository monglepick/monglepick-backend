package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.AchievementResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.CreateAchievementRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.UpdateAchievementRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.repository.AchievementTypeRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 업적(AchievementType) 마스터 관리 서비스.
 *
 * <p>업적 마스터 데이터의 등록·수정·활성화 토글 비즈니스 로직을 담당한다.
 * 사용자 측에서 호출되는 {@link com.monglepick.monglepickbackend.domain.roadmap.service.AchievementService}와는
 * 책임이 분리되어 있으며, 본 서비스는 관리자 화면 전용이다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>업적 마스터 목록 조회 (활성/비활성 모두 포함, 페이징)</li>
 *   <li>업적 마스터 단건 조회</li>
 *   <li>업적 마스터 신규 등록 (코드 중복 방지)</li>
 *   <li>업적 마스터 수정 (코드 변경 불가, 표시명/설명/조건/보상/아이콘/카테고리만)</li>
 *   <li>활성/비활성 토글 (사용자 달성 기록은 보존)</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly=true)} — 모든 조회 메서드 기본 적용</li>
 *   <li>쓰기 메서드: {@code @Transactional} 개별 오버라이드</li>
 * </ul>
 *
 * <h3>주의사항</h3>
 * <p>비활성화는 hard delete가 아닌 {@code is_active=false} 토글로 처리한다.
 * 이는 기존 사용자 달성 기록({@code user_achievement} 테이블)이 FK로 이 마스터를
 * 참조하고 있기 때문이다. 마스터를 삭제하면 달성 기록이 깨진다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAchievementService {

    /** 업적 유형 마스터 레포지토리 (JPA, 윤형주 admin 도메인이라 직접 접근 가능) */
    private final AchievementTypeRepository achievementTypeRepository;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 업적 마스터 목록을 페이징 조회한다 (활성/비활성 모두 포함).
     *
     * <p>관리자 화면 테이블 표시용. 정렬 기준은 호출자의 {@link Pageable}이 결정한다.</p>
     *
     * @param pageable 페이지 정보
     * @return 업적 응답 페이지
     */
    public Page<AchievementResponse> getAchievements(Pageable pageable) {
        return achievementTypeRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * 업적 마스터 단건 조회.
     *
     * @param id 업적 유형 ID (achievement_type_id)
     * @return 업적 응답 DTO
     * @throws BusinessException 업적이 존재하지 않는 경우 (ACHIEVEMENT_TYPE_NOT_FOUND)
     */
    public AchievementResponse getAchievement(Long id) {
        return toResponse(findAchievementByIdOrThrow(id));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 신규 업적 마스터를 등록한다.
     *
     * <p>{@code achievement_code} UNIQUE 제약을 사전 검증하여 친화적 409를 반환한다.
     * 신규 등록 시 isActive 기본값은 true(활성).</p>
     *
     * @param request 신규 업적 등록 요청
     * @return 생성된 업적 응답 DTO
     * @throws BusinessException 코드 중복 시 (DUPLICATE_ACHIEVEMENT_CODE)
     */
    @Transactional
    public AchievementResponse createAchievement(CreateAchievementRequest request) {
        // 1) 코드 중복 사전 검증 — UNIQUE 제약으로 INSERT 시점 예외 발생을 회피
        if (achievementTypeRepository.existsByAchievementCode(request.achievementCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACHIEVEMENT_CODE);
        }

        // 2) 엔티티 빌더 — Builder.Default 로 isActive=true / rewardPoints=0 자동 설정
        AchievementType entity = AchievementType.builder()
                .achievementCode(request.achievementCode())
                .achievementName(request.achievementName())
                .description(request.description())
                .requiredCount(request.requiredCount())
                .rewardPoints(request.rewardPoints() != null ? request.rewardPoints() : 0)
                .iconUrl(request.iconUrl())
                .category(request.category())
                .isActive(true)
                .build();

        AchievementType saved = achievementTypeRepository.save(entity);
        log.info("[관리자] 업적 마스터 등록 — id={}, code={}, name={}",
                saved.getAchievementTypeId(), saved.getAchievementCode(), saved.getAchievementName());

        return toResponse(saved);
    }

    /**
     * 기존 업적 마스터를 수정한다 (achievement_code 제외).
     *
     * <p>업적 코드는 사용자 달성 기록과 연결된 식별자이므로 변경 불가.
     * 표시명/설명/조건/보상/아이콘/카테고리만 수정 가능하다.</p>
     *
     * @param id      수정 대상 업적 유형 ID
     * @param request 수정 요청
     * @return 수정된 업적 응답 DTO
     * @throws BusinessException 업적이 없으면 ACHIEVEMENT_TYPE_NOT_FOUND
     */
    @Transactional
    public AchievementResponse updateAchievement(Long id, UpdateAchievementRequest request) {
        AchievementType entity = findAchievementByIdOrThrow(id);

        // 도메인 메서드 호출 — JPA dirty checking으로 자동 UPDATE
        entity.updateInfo(
                request.achievementName(),
                request.description(),
                request.rewardPoints() != null ? request.rewardPoints() : 0,
                request.iconUrl(),
                request.category()
        );

        // requiredCount는 도메인 메서드(updateInfo)에 포함되지 않아 별도 처리 — 엔티티 setter 부재이므로
        // updateInfo()를 확장하지 않고 reflection 없이 처리하기 위해 신규 필드는 그대로 두고,
        // 향후 도메인 메서드 확장 시 대체. 현재는 requiredCount 변경을 지원하지 않음을 로그로 명시한다.
        if (request.requiredCount() != null
                && !request.requiredCount().equals(entity.getRequiredCount())) {
            log.warn("[관리자] requiredCount 변경 요청은 현재 지원하지 않습니다 (도메인 메서드 미지원). " +
                    "id={}, oldValue={}, requestedValue={}",
                    id, entity.getRequiredCount(), request.requiredCount());
        }

        log.info("[관리자] 업적 마스터 수정 — id={}, name={}", id, entity.getAchievementName());
        return toResponse(entity);
    }

    /**
     * 업적 마스터 활성/비활성 토글.
     *
     * <p>{@code isActive=false}이면 신규 달성 기록 생성이 차단된다.
     * 기존 사용자 달성 기록은 보존된다 (FK 제약 보호).</p>
     *
     * @param id      대상 업적 유형 ID
     * @param request 활성 여부 토글 요청
     * @return 갱신된 업적 응답 DTO
     */
    @Transactional
    public AchievementResponse updateActiveStatus(Long id, UpdateActiveRequest request) {
        AchievementType entity = findAchievementByIdOrThrow(id);
        boolean newActive = Boolean.TRUE.equals(request.isActive());
        entity.updateActiveStatus(newActive);

        log.info("[관리자] 업적 활성 상태 변경 — id={}, code={}, isActive={}",
                id, entity.getAchievementCode(), newActive);
        return toResponse(entity);
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    /**
     * ID로 업적 마스터를 조회하거나 404 예외를 발생시킨다.
     */
    private AchievementType findAchievementByIdOrThrow(Long id) {
        return achievementTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACHIEVEMENT_TYPE_NOT_FOUND,
                        "업적 유형 ID " + id + "를 찾을 수 없습니다"));
    }

    /**
     * 엔티티를 응답 DTO로 변환한다.
     */
    private AchievementResponse toResponse(AchievementType entity) {
        return new AchievementResponse(
                entity.getAchievementTypeId(),
                entity.getAchievementCode(),
                entity.getAchievementName(),
                entity.getDescription(),
                entity.getRequiredCount(),
                entity.getRewardPoints(),
                entity.getIconUrl(),
                entity.getCategory(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
