package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.ocrevent.UserVerificationRepository;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.movie.repository.FavGenreRepository;
import com.monglepick.monglepickbackend.domain.movie.repository.FavMovieRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.roadmap.controller.AchievementController.AchievementResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserAchievement;
import com.monglepick.monglepickbackend.domain.roadmap.repository.AchievementTypeRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizParticipationRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserAchievementRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupResultRepository;
import com.monglepick.monglepickbackend.global.dto.UnlockedAchievementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 리뷰 Mapper — review_count_10 / genre_explorer 실시간 진행률 계산 */
    private final ReviewMapper reviewMapper;

    /** 커뮤니티 Mapper — post_count_* / comment_count_* / playlist_share_first 진행률 계산 */
    private final PostMapper postMapper;

    /** 코스 진행 Repository — course_complete / course_count_* 진행률 계산 */
    private final UserCourseProgressRepository userCourseProgressRepository;

    /** 퀴즈 참여 Repository — quiz_perfect / quiz_count_* 진행률 계산 */
    private final QuizParticipationRepository quizParticipationRepository;

    /** 추천 로그 Repository — recommendation_* 진행률 계산 */
    private final RecommendationLogRepository recommendationLogRepository;

    /** OCR 인증 Repository — ocr_* 진행률 계산 */
    private final UserVerificationRepository userVerificationRepository;

    /** 월드컵 결과 Repository — onboard_complete 진행률 계산 */
    private final WorldcupResultRepository worldcupResultRepository;

    /** 온보딩 선호 장르 Repository — onboard_complete 진행률 계산 */
    private final FavGenreRepository favGenreRepository;

    /** 온보딩 인생 영화 Repository — onboard_complete 진행률 계산 */
    private final FavMovieRepository favMovieRepository;

    /** 조회 API의 소급 달성 처리를 별도 트랜잭션으로 분리하기 위한 트랜잭션 매니저 */
    private final PlatformTransactionManager transactionManager;

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
    public Optional<UnlockedAchievementResponse> checkAndGrant(String userId, String achievementCode, String achievementKey) {
        // ① AchievementType 마스터 조회 — 없거나 비활성이면 처리 중단
        Optional<AchievementType> typeOpt = achievementTypeRepo.findByAchievementCode(achievementCode);
        if (typeOpt.isEmpty()) {
            log.warn("업적 코드에 해당하는 마스터 없음 (건너뜀): achievementCode={}, userId={}", achievementCode, userId);
            return Optional.empty();
        }
        AchievementType type = typeOpt.get();
        if (Boolean.FALSE.equals(type.getIsActive())) {
            log.debug("비활성 업적 코드 (건너뜀): achievementCode={}, userId={}", achievementCode, userId);
            return Optional.empty();
        }

        // ② 중복 달성 여부 확인 — (user_id, achievementType, achievementKey) UNIQUE 제약에 대응
        //    JPA/MyBatis 하이브리드 §15.4 — User 엔티티 lookup 없이 String userId 직접 사용
        boolean alreadyAchieved = userAchievementRepo
                .findByUserIdAndAchievementTypeAndAchievementKey(userId, type, achievementKey)
                .isPresent();
        if (alreadyAchieved) {
            log.debug("이미 달성한 업적 (건너뜀): userId={}, achievementCode={}, achievementKey={}",
                    userId, achievementCode, achievementKey);
            return Optional.empty();
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

        return Optional.of(UnlockedAchievementResponse.from(type));
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
     * 게시글 작성 후 CSV/관리자 등록 기반 커뮤니티 업적을 확인한다.
     *
     * <p>achievementCode가 {@code post_count_}로 시작하는 활성 업적은
     * requiredCount와 사용자의 게시글 수를 비교해 자동 달성 처리한다.
     * PLAYLIST_SHARE 게시글 작성 시에는 {@code playlist_share_first}도 함께 확인한다.</p>
     */
    @Transactional
    public List<UnlockedAchievementResponse> checkPostAchievements(String userId, Post.Category category) {
        return checkEligibleCountAchievements(userId, type -> {
            String code = normalizeAchievementCode(type.getAchievementCode());
            return code != null && (code.startsWith("post_count_")
                    || (category == Post.Category.PLAYLIST_SHARE && "playlist_share_first".equals(code)));
        });
    }

    /**
     * 댓글 작성 후 CSV/관리자 등록 기반 댓글 업적을 확인한다.
     *
     * <p>achievementCode가 {@code comment_count_}로 시작하는 활성 업적은
     * requiredCount와 사용자의 유효 댓글 수를 비교해 자동 달성 처리한다.</p>
     */
    @Transactional
    public List<UnlockedAchievementResponse> checkCommentAchievements(String userId) {
        return checkEligibleCountAchievements(userId, type -> {
            String code = normalizeAchievementCode(type.getAchievementCode());
            return code != null && code.startsWith("comment_count_");
        });
    }

    /**
     * 온보딩 완료 업적을 확인한다.
     *
     * <p>{@code onboard_complete}는 별도 개별 업적 없이 월드컵 완료, 선호 장르 선택,
     * 인생 영화 선택 3개 체크포인트가 모두 충족되었을 때 최초 1회 달성된다.</p>
     */
    @Transactional
    public Optional<UnlockedAchievementResponse> checkOnboardComplete(String userId) {
        int progress = computeOnboardCompleteProgress(userId, 3);
        if (progress >= 3) {
            return checkAndGrant(userId, "onboard_complete", "default");
        }
        return Optional.empty();
    }

    /**
     * 온보딩 3개 체크포인트의 현재 진행 상태를 조회한다.
     */
    public OnboardCompleteProgress getOnboardCompleteProgress(String userId) {
        boolean worldcupCompleted = worldcupResultRepository.countByUserId(userId) > 0;
        boolean favoriteGenresCompleted = favGenreRepository.countByUserId(userId) > 0;
        boolean favoriteMoviesCompleted = favMovieRepository.countByUserId(userId) > 0;
        int progress = 0;
        if (worldcupCompleted) {
            progress++;
        }
        if (favoriteGenresCompleted) {
            progress++;
        }
        if (favoriteMoviesCompleted) {
            progress++;
        }
        return new OnboardCompleteProgress(
                worldcupCompleted,
                favoriteGenresCompleted,
                favoriteMoviesCompleted,
                progress,
                3,
                progress >= 3
        );
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
    @Transactional
    public List<AchievementResponse> getAchievementsWithProgress(String userId, @Nullable String category) {

        // ① 카테고리 조건에 맞는 활성 업적 유형 목록 조회
        List<AchievementType> types;
        if (isAllCategory(category)) {
            types = achievementTypeRepo.findByIsActiveTrue();
            log.debug("업적 유형 전체 조회: {}건", types.size());
        } else {
            types = achievementTypeRepo.findByCategoryAndIsActiveTrue(category);
            log.debug("업적 유형 카테고리 조회: category={}, {}건", category, types.size());
        }

        // ② 사용자의 달성 이력 → achievementTypeId 맵 (최신 기록 우선)
        List<UserAchievement> userAchievements = userAchievementRepo.findAllByUserId(userId);
        Map<Long, UserAchievement> achievedMap = new LinkedHashMap<>();
        for (UserAchievement ua : userAchievements) {
            Long achievedTypeId = resolveAchievementTypeIdSafely(userId, ua);
            if (achievedTypeId == null) {
                log.warn("업적 달성 이력의 마스터 연결 누락 (조회 제외): userId={}, userAchievementId={}, legacyCode={}",
                        userId,
                        ua != null ? ua.getUserAchievementId() : null,
                        ua != null ? ua.getAchievementTypeCode() : null);
                continue;
            }
            achievedMap.merge(achievedTypeId, ua, (existing, incoming) -> {
                if (incoming.getAchievedAt() != null && (existing.getAchievedAt() == null
                        || incoming.getAchievedAt().isAfter(existing.getAchievedAt()))) {
                    return incoming;
                }
                return existing;
            });
        }

        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        List<AchievementResponse> result = new ArrayList<>();

        for (AchievementType type : types) {
            Long typeId = type.getAchievementTypeId();
            boolean achieved = achievedMap.containsKey(typeId);

            int maxProgress = resolveMaxProgress(type);

            int progress;
            LocalDateTime grantedAt = null;

            if (achieved) {
                // 이미 달성 — 진행률 = maxProgress
                progress = maxProgress;
                UserAchievement ua = achievedMap.get(typeId);
                if (ua != null && ua.getAchievedAt() != null) {
                    grantedAt = ua.getAchievedAt();
                }
            } else {
                // ③ 실시간 진행률 계산 (achievementCode별 도메인 데이터 조회)
                try {
                    progress = computeRealProgress(userId, type.getAchievementCode(), maxProgress);
                } catch (Exception e) {
                    log.error("업적 진행률 계산 실패 (해당 업적은 0으로 응답): userId={}, code={}, error={}",
                            userId, type.getAchievementCode(), e.getMessage(), e);
                    progress = 0;
                }

                // ④ 기존 데이터로 이미 달성 조건 충족 → 소급 달성 처리
                if (progress >= maxProgress) {
                    try {
                        grantedAt = autoGrantIfEligibleInNewTransaction(userId, type);
                        achieved = (grantedAt != null);
                    } catch (Exception e) {
                        log.error("업적 소급 달성 처리 실패 (목록 응답은 유지): userId={}, code={}, error={}",
                                userId, type.getAchievementCode(), e.getMessage(), e);
                    }
                }
            }

            String achievedAtStr = (achieved && grantedAt != null)
                    ? grantedAt.format(isoFormatter) : null;

            result.add(new AchievementResponse(
                    typeId,
                    type.getAchievementCode(),
                    type.getAchievementName(),
                    type.getDescription(),
                    type.getCategory(),
                    type.getIconUrl(),
                    type.getRewardPoints(),
                    maxProgress,
                    achieved,
                    achieved ? maxProgress : progress,
                    maxProgress,
                    achievedAtStr
            ));
        }

        return result;
    }

    private Long resolveAchievementTypeIdSafely(String userId, UserAchievement ua) {
        if (ua == null) {
            return null;
        }
        try {
            AchievementType type = ua.getAchievementType();
            return type != null ? type.getAchievementTypeId() : null;
        } catch (RuntimeException e) {
            log.warn("업적 달성 이력의 마스터 조회 실패 (조회 제외): userId={}, userAchievementId={}, legacyCode={}, error={}",
                    userId, ua.getUserAchievementId(), ua.getAchievementTypeCode(), e.getMessage());
            return null;
        }
    }

    /**
     * achievementCode별 현재 실제 진행 수를 반환한다.
     * 카운트 기반 업적만 계산하며, 이벤트성(quiz_perfect, course_complete)은 0 반환.
     */
    private int computeRealProgress(String userId, String achievementCode, int maxProgress) {
        if (achievementCode == null || achievementCode.isBlank()) {
            return 0;
        }
        achievementCode = normalizeAchievementCode(achievementCode);

        if (achievementCode.startsWith("post_count_") || hasNumericSuffix(achievementCode, "post_")) {
            return (int) Math.min(
                    postMapper.countByUserIdAndStatus(userId, PostStatus.PUBLISHED.name()),
                    maxProgress
            );
        }
        if (achievementCode.startsWith("review_count_")) {
            return (int) Math.min(reviewMapper.countByUserId(userId), maxProgress);
        }
        if (achievementCode.startsWith("comment_count_") || hasNumericSuffix(achievementCode, "comment_")) {
            return (int) Math.min(
                    postMapper.countCommentsByUserIdAndIsDeletedFalse(userId),
                    maxProgress
            );
        }
        if (achievementCode.startsWith("course_count_") || hasNumericSuffix(achievementCode, "course_complete_")) {
            return countCompletedCourses(userId, maxProgress);
        }
        if (achievementCode.startsWith("quiz_count_")) {
            return countCorrectQuizzes(userId, maxProgress);
        }
        if (hasNumericSuffix(achievementCode, "recommendation_")) {
            return (int) Math.min(recommendationLogRepository.countByUserId(userId), maxProgress);
        }
        if (hasNumericSuffix(achievementCode, "ocr_")) {
            return (int) Math.min(userVerificationRepository.countByUserId(userId), maxProgress);
        }

        return switch (achievementCode) {
            case "genre_explorer" -> (int) Math.min(reviewMapper.countDistinctExploredGenres(userId), maxProgress);
            case "course_complete" -> countCompletedCourses(userId, maxProgress);
            case "quiz_perfect", "quiz_first_correct" -> countCorrectQuizzes(userId, maxProgress);
            case "onboard_complete" -> computeOnboardCompleteProgress(userId, maxProgress);
            case "playlist_share_first" -> (int) Math.min(
                    postMapper.countByUserIdAndCategoryAndStatus(
                            userId,
                            Post.Category.PLAYLIST_SHARE.name(),
                            PostStatus.PUBLISHED.name()
                    ),
                    maxProgress
            );
            default -> 0;
        };
    }

    private int computeOnboardCompleteProgress(String userId, int maxProgress) {
        return Math.min(getOnboardCompleteProgress(userId).progress(), maxProgress);
    }

    private boolean isAllCategory(@Nullable String category) {
        if (category == null || category.isBlank()) {
            return true;
        }
        String normalized = category.trim();
        return "ALL".equalsIgnoreCase(normalized)
                || "전체".equals(normalized)
                || "*".equals(normalized);
    }

    private int countCompletedCourses(String userId, int maxProgress) {
        return (int) Math.min(
                userCourseProgressRepository.countByUserIdAndStatus(userId, CourseProgressStatus.COMPLETED),
                maxProgress
        );
    }

    private int countCorrectQuizzes(String userId, int maxProgress) {
        return (int) Math.min(
                quizParticipationRepository.countByUserIdAndIsCorrect(userId, true),
                maxProgress
        );
    }

    private List<UnlockedAchievementResponse> checkEligibleCountAchievements(
            String userId,
            java.util.function.Predicate<AchievementType> filter
    ) {
        List<UnlockedAchievementResponse> unlockedAchievements = new ArrayList<>();
        List<AchievementType> types = achievementTypeRepo.findByIsActiveTrue();
        for (AchievementType type : types) {
            if (!filter.test(type)) {
                continue;
            }
            int maxProgress = resolveMaxProgress(type);
            int progress;
            try {
                progress = computeRealProgress(userId, type.getAchievementCode(), maxProgress);
            } catch (Exception e) {
                log.error("업적 진행률 계산 실패 (달성 체크 건너뜀): userId={}, code={}, error={}",
                        userId, type.getAchievementCode(), e.getMessage(), e);
                continue;
            }
            if (progress >= maxProgress) {
                grantIfEligible(userId, type, "default").ifPresent(unlockedAchievements::add);
            }
        }
        return unlockedAchievements;
    }

    private int resolveMaxProgress(AchievementType type) {
        String code = normalizeAchievementCode(type.getAchievementCode());
        if ("onboard_complete".equals(code)) {
            return 3;
        }

        if (type.getRequiredCount() != null && type.getRequiredCount() > 0) {
            return type.getRequiredCount();
        }

        if (code == null) {
            return 1;
        }

        int underscore = code.lastIndexOf('_');
        if (underscore >= 0 && underscore + 1 < code.length()) {
            try {
                int parsed = Integer.parseInt(code.substring(underscore + 1));
                return Math.max(parsed, 1);
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }

        return 1;
    }

    private String normalizeAchievementCode(String achievementCode) {
        if (achievementCode == null) {
            return null;
        }
        return achievementCode.trim().toLowerCase().replace('-', '_');
    }

    private boolean hasNumericSuffix(String achievementCode, String prefix) {
        if (achievementCode == null || !achievementCode.startsWith(prefix)) {
            return false;
        }
        String suffix = achievementCode.substring(prefix.length());
        if (suffix.isBlank()) {
            return false;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public record OnboardCompleteProgress(
            boolean worldcupCompleted,
            boolean favoriteGenresCompleted,
            boolean favoriteMoviesCompleted,
            int progress,
            int maxProgress,
            boolean completed
    ) {}

    /**
     * 소급 달성 처리 — 이미 조건을 충족했지만 아직 UserAchievement가 없는 경우 INSERT + 리워드 지급.
     *
     * @return 달성 처리된 시각 (이미 달성 기록이 있거나 INSERT 성공 시), 실패 시 null
     */
    private LocalDateTime autoGrantIfEligibleInNewTransaction(String userId, AchievementType type) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> autoGrantIfEligible(userId, type));
    }

    private LocalDateTime autoGrantIfEligible(String userId, AchievementType type) {
        return grantIfEligible(userId, type, "default")
                .map(ignored -> LocalDateTime.now())
                .orElseGet(LocalDateTime::now);
    }

    private Optional<UnlockedAchievementResponse> grantIfEligible(String userId, AchievementType type, String achievementKey) {
        boolean alreadyAchieved = userAchievementRepo
                .findByUserIdAndAchievementTypeAndAchievementKey(userId, type, achievementKey)
                .isPresent();
        if (alreadyAchieved) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        UserAchievement achievement = UserAchievement.builder()
                .userId(userId)
                .achievementTypeCode(type.getAchievementCode())
                .achievementType(type)
                .achievementKey(achievementKey)
                .achievedAt(now)
                .build();
        userAchievementRepo.save(achievement);
        log.info("업적 소급 달성 처리: userId={}, code={}", userId, type.getAchievementCode());

        if (type.getRewardPoints() != null && type.getRewardPoints() > 0) {
            try {
                rewardService.grantRewardWithAmount(
                        userId,
                        "ACHIEVEMENT_UNLOCK",
                        "achievement_" + type.getAchievementCode(),
                        type.getRewardPoints()
                );
            } catch (Exception e) {
                log.warn("업적 소급 리워드 지급 실패 (업적 INSERT는 유지): userId={}, code={}, error={}",
                        userId, type.getAchievementCode(), e.getMessage());
            }
        }
        return Optional.of(UnlockedAchievementResponse.from(type));
    }
}
