package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserAchievement;
import com.monglepick.monglepickbackend.domain.roadmap.repository.AchievementTypeRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserAchievementRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    /** 사용자 레포지토리 — UserAchievement FK용 User 엔티티 조회 */
    private final UserRepository userRepository;

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

        // ② User 엔티티 조회 — UserAchievement FK 연결에 필요
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "업적 처리 중 사용자를 찾을 수 없음: userId=" + userId));

        // ③ 중복 달성 여부 확인 — (user, achievementType, achievementKey) UNIQUE 제약에 대응
        boolean alreadyAchieved = userAchievementRepo
                .findByUserAndAchievementTypeAndAchievementKey(user, type, achievementKey)
                .isPresent();
        if (alreadyAchieved) {
            log.debug("이미 달성한 업적 (건너뜀): userId={}, achievementCode={}, achievementKey={}",
                    userId, achievementCode, achievementKey);
            return;
        }

        // ④ UserAchievement INSERT — 달성 기록 저장
        UserAchievement achievement = UserAchievement.builder()
                .user(user)
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "업적 조회 중 사용자를 찾을 수 없음: userId=" + userId));
        return userAchievementRepo.findAllByUser(user);
    }
}
