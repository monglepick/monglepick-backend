package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.monglepick.monglepickbackend.domain.roadmap.controller.AchievementController.AchievementResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserAchievement;
import com.monglepick.monglepickbackend.domain.roadmap.repository.AchievementTypeRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserAchievementRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 업적 서비스 — 업적 달성 감지, 리워드 지급, 달성 이력 관리.
 *
 * <h3>역할</h3>
 * <ul>
 *   <li>도장깨기 완주, 퀴즈 만점, 장르 탐험 등 각 도메인 서비스가 활동 완료 후 호출</li>
 *   <li>{@code achievementCode}로 {@link AchievementType} 마스터 조회</li>
 *   <li>(user_id, achievement_type_id, achievement_key) UNIQUE 제약 기반 중복 달성 방지</li>
 *   <li>신규 달성 시 {@link UserAchievement} INSERT 후 {@link RewardService#grantRewardWithAmount} 연동</li>
 * </ul>
 *
 * <h3>호출 예시</h3>
 * <pre>{@code
 * // RoadmapService 내에서 코스 완주 후
 * achievementService.checkAndGrant(userId, "course_complete", courseId);
 *
 * // 장르 탐험 업적 달성 시
 * achievementService.checkAndGrant(userId, "genre_explorer", "default");
 * }</pre>
 *
 * @see AchievementType  업적 유형 마스터 (achievementCode, rewardPoints)
 * @see UserAchievement  사용자별 달성 이력
 * @see RewardService    포인트 지급 위임
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    /** 업적 유형 마스터 레포지토리 — achievementCode로 마스터 엔티티 조회 */
    private final AchievementTypeRepository achievementTypeRepo;

    /** 사용자 업적 달성 이력 레포지토리 — 중복 달성 확인 및 INSERT */
    private final UserAchievementRepository userAchievementRepo;

    /** 리워드 서비스 — 업적 달성 포인트 지급 위임 */
    private final RewardService rewardService;

    // (2026-04-08) JPA/MyBatis 하이브리드 §15.4 적용:
    // UserAchievement 가 String userId 직접 보관으로 변경되어 User 엔티티 lookup 불필요.
    // 사용자 존재 검증은 컨트롤러의 JWT 인증 단계에서 이미 완료된다.

    // ────────────────────────────────────────────────────────────────
    // public 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 업적 달성 여부를 확인하고, 미달성 시 업적을 부여하고 리워드를 지급한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>{@code achievementCode}로 {@link AchievementType} 마스터 조회.
     *       없거나 비활성 상태이면 즉시 반환 (warn 로그).</li>
     *   <li>User 엔티티 조회 — {@link UserAchievement}의 FK용.</li>
     *   <li>(user, achievementType, achievementKey) 기준 중복 달성 여부 확인.
     *       이미 달성된 경우 즉시 반환 (debug 로그).</li>
     *   <li>{@link UserAchievement} INSERT — achievedAt=현재 시각.</li>
     *   <li>rewardPoints > 0이면 {@link RewardService#grantRewardWithAmount} 호출
     *       (referenceId = "achievement_" + achievementCode, 동적 포인트 지급).</li>
     * </ol>
     *
     * <h4>예외 전략</h4>
     * <p>RewardService 내부 예외는 REQUIRES_NEW 트랜잭션으로 분리되어
     * 업적 INSERT 트랜잭션에 영향을 주지 않는다.
     * User 미발견 시에만 {@link BusinessException}을 던진다.</p>
     *
     * @param userId          사용자 ID (VARCHAR(50))
     * @param achievementCode 업적 코드 ({@link AchievementType#getAchievementCode()}, 예: "course_complete")
     * @param achievementKey  업적 식별 키 (단일 달성형이면 "default", 코스별이면 courseId 등)
     */
    @Transactional
    public void checkAndGrant(String userId, String achievementCode, String achievementKey) {
        // ① AchievementType 마스터 조회 — 없거나 비활성이면 처리 중단
        Optional<AchievementType> typeOpt = achievementTypeRepo.findByAchievementCode(achievementCode);
        if (typeOpt.isEmpty()) {
            log.warn("업적 코드에 해당하는 마스터 없음 (건너뜀): achievementCode={}, userId={}", achievementCode, userId);
            return;
        }
        AchievementType type = typeOpt.get();
        if (Boolean.FALSE.equals(type.getIsActive())) {
            log.debug("비활성 업적 코드 (건너뜀): achievementCode={}, userId={}", achievementCode, userId);
            return;
        }

        // ② 중복 달성 여부 확인 — (user_id, achievementType, achievementKey) UNIQUE 제약에 대응
        //    JPA/MyBatis 하이브리드 §15.4 — User 엔티티 lookup 없이 String userId 직접 사용
        boolean alreadyAchieved = userAchievementRepo
                .findByUserIdAndAchievementTypeAndAchievementKey(userId, type, achievementKey)
                .isPresent();
        if (alreadyAchieved) {
            log.debug("이미 달성한 업적 (건너뜀): userId={}, achievementCode={}, achievementKey={}",
                    userId, achievementCode, achievementKey);
            return;
        }

        // ③ UserAchievement INSERT — 달성 기록 저장
        UserAchievement achievement = UserAchievement.builder()
                .userId(userId)
                .achievementTypeCode(type.getAchievementCode()) // 레거시 NOT NULL 컬럼 호환
                .achievementType(type)
                .achievementKey(achievementKey)
                .achievedAt(LocalDateTime.now())
                .build();
        userAchievementRepo.save(achievement);
        log.info("업적 달성 기록: userId={}, achievementCode={}, achievementKey={}, rewardPoints={}",
                userId, achievementCode, achievementKey, type.getRewardPoints());

        // ⑤ rewardPoints > 0이면 ACHIEVEMENT_UNLOCK 정책으로 동적 포인트 지급
        //    grantRewardWithAmount는 REQUIRES_NEW 트랜잭션으로 실행되어
        //    포인트 지급 실패가 업적 INSERT를 롤백시키지 않는다
        if (type.getRewardPoints() != null && type.getRewardPoints() > 0) {
            rewardService.grantRewardWithAmount(
                    userId,
                    "ACHIEVEMENT_UNLOCK",
                    "achievement_" + achievementCode,
                    type.getRewardPoints()
            );
        }
    }

    /**
     * 특정 사용자의 달성 업적 목록을 전체 조회한다.
     *
     * <p>마이페이지 업적 탭, AchievementController 등에서 사용한다.
     * achievementType이 LAZY이므로 필요 시 서비스 레이어에서 명시적으로 초기화한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 달성 업적 목록 (최신순 정렬은 컨트롤러/클라이언트 담당)
     * @throws BusinessException {@link ErrorCode#USER_NOT_FOUND} 사용자가 존재하지 않는 경우
     */
    public List<UserAchievement> getAchievements(String userId) {
        // JPA/MyBatis 하이브리드 §15.4 — String userId 직접 사용 (User 엔티티 lookup 불필요)
        return userAchievementRepo.findAllByUserId(userId);
    }

    /**
     * 업적 유형 전체(또는 특정 카테고리)에 대해 사용자 달성 여부를 포함한 진행률 목록을 반환한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>category가 null이면 활성 업적 유형 전체 조회,
     *       아니면 해당 카테고리의 활성 업적 유형만 조회한다.</li>
     *   <li>사용자의 달성 이력을 조회하여 달성된 achievementTypeId Set을 생성한다.</li>
     *   <li>각 AchievementType을 {@link AchievementResponse}로 변환한다.
     *       <ul>
     *         <li>달성 시: achieved=true, progress=maxProgress, achievedAt=ISO 문자열</li>
     *         <li>미달성 시: achieved=false, progress=0, achievedAt=null</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <h4>진행률(progress) 설계 참고</h4>
     * <p>현재 업적별 중간 진행률 추적은 구현되지 않았다.
     * 달성 시 maxProgress, 미달성 시 0으로 이진 반환한다.
     * 추후 UserActivityProgress 연동으로 세밀한 진행률 표시가 가능하다.</p>
     *
     * @param userId   사용자 ID
     * @param category 필터링할 카테고리 (null이면 전체 조회)
     * @return 업적 유형별 달성 여부 + 진행률이 포함된 응답 DTO 목록
     * @throws BusinessException {@link ErrorCode#USER_NOT_FOUND} 사용자가 존재하지 않는 경우
     */
    public List<AchievementResponse> getAchievementsWithProgress(String userId, @Nullable String category) {

        // ① 카테고리 조건에 맞는 활성 업적 유형 목록 조회
        //    JPA/MyBatis 하이브리드 §15.4 — User 엔티티 lookup 제거 (JWT 인증 단계에서 검증 완료)
        List<AchievementType> types;
        if (category == null || category.isBlank()) {
            // 카테고리 파라미터 없음 → 전체 활성 업적 유형 조회
            types = achievementTypeRepo.findByIsActiveTrue();
            log.debug("업적 유형 전체 조회: {}건", types.size());
        } else {
            // 특정 카테고리만 조회
            types = achievementTypeRepo.findByCategoryAndIsActiveTrue(category);
            log.debug("업적 유형 카테고리 조회: category={}, {}건", category, types.size());
        }

        // ② 사용자의 달성 이력 조회 → achievementTypeId Set으로 변환 (O(1) 조회)
        List<UserAchievement> userAchievements = userAchievementRepo.findAllByUserId(userId);

        // achievementTypeId → UserAchievement 맵 (같은 타입을 여러 키로 달성 가능하므로 최신 기록만 보관)
        // 달성 여부는 "해당 타입을 하나라도 달성했는지"를 기준으로 판정한다
        java.util.Map<Long, UserAchievement> achievedMap = new java.util.LinkedHashMap<>();
        for (UserAchievement ua : userAchievements) {
            Long typeId = ua.getAchievementType().getAchievementTypeId();
            // 이미 있으면 더 최신 기록으로 교체 (achievedAt 기준)
            achievedMap.merge(typeId, ua, (existing, incoming) -> {
                if (incoming.getAchievedAt() != null && (existing.getAchievedAt() == null
                        || incoming.getAchievedAt().isAfter(existing.getAchievedAt()))) {
                    return incoming;
                }
                return existing;
            });
        }

        // ISO 날짜 포맷 (프론트엔드 파싱 호환)
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        // ④ AchievementType 목록을 AchievementResponse DTO로 변환
        return types.stream()
                .map(type -> {
                    Long typeId = type.getAchievementTypeId();
                    boolean achieved = achievedMap.containsKey(typeId);

                    // maxProgress: requiredCount가 null이거나 0이면 1(단일 달성형)로 처리
                    int maxProgress = (type.getRequiredCount() != null && type.getRequiredCount() > 0)
                            ? type.getRequiredCount()
                            : 1;

                    // progress: 달성 시 maxProgress, 미달성 시 0 (중간 진행률 미구현)
                    int progress = achieved ? maxProgress : 0;

                    // achievedAt: 달성 시 ISO 문자열, 미달성 시 null
                    String achievedAt = null;
                    if (achieved) {
                        UserAchievement ua = achievedMap.get(typeId);
                        if (ua.getAchievedAt() != null) {
                            achievedAt = ua.getAchievedAt().format(isoFormatter);
                        }
                    }

                    return new AchievementResponse(
                            typeId,
                            type.getAchievementCode(),
                            type.getAchievementName(),
                            type.getDescription(),
                            type.getCategory(),
                            type.getIconUrl(),
                            type.getRewardPoints(),
                            maxProgress,   // requiredCount (maxProgress 역할)
                            achieved,
                            progress,
                            maxProgress,
                            achievedAt
                    );
                })
                .toList();
    }
}
