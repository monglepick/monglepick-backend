package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.RewardResult;
import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicy;
import com.monglepick.monglepickbackend.domain.reward.entity.UserActivityProgress;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserActivityProgressRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 활동 리워드 서비스 — 사용자 활동에 따른 포인트 지급/회수 핵심 로직.
 *
 * <h3>역할</h3>
 * <ul>
 *   <li>리뷰 작성, 게시글 작성, 댓글, AI 추천 사용, 위시리스트 추가 등
 *       활동 발생 시 호출되어 {@code reward_policy} 기반으로 포인트를 자동 지급</li>
 *   <li>일일 한도 / 평생 횟수 / 쿨다운 / 최소 길이 검사 후 조건 충족 시에만 지급</li>
 *   <li>threshold 기반 마일스톤(누적/일일/연속) 달성 시 연쇄 보너스 지급</li>
 *   <li>콘텐츠 삭제 등 리워드 회수 시 points_history 이력 기반 정확한 금액 차감</li>
 * </ul>
 *
 * <h3>트랜잭션 전략 (REQUIRED — 2026-04-14 변경)</h3>
 * <p>기존에는 {@code Propagation.REQUIRES_NEW}로 호출자 트랜잭션과 완전히 분리했으나,
 * 회원가입 시 {@code AuthService.signup()}의 {@code userMapper.insert(user)} +
 * {@code pointService.initializePoint(userId, 0)}이 아직 커밋되지 않은 상태에서
 * {@code REQUIRES_NEW}로 새 트랜잭션을 열면 방금 INSERT한 user_points row가 보이지 않아
 * {@code POINT_NOT_FOUND}로 실패하고 catch 가 삼키는 버그가 있었다.</p>
 * <p>따라서 {@code Propagation.REQUIRED}(Spring 기본값)로 변경하여 호출자 트랜잭션에
 * 참여하도록 한다. 리워드 지급 실패 시 본 기능도 함께 롤백되는 편이 데이터 정합성 측면에서
 * 안전하다 (가입 보너스/리뷰 작성 리워드 등은 본 기능과 원자적으로 처리돼야 함).</p>
 *
 * <h3>동시성 처리</h3>
 * <p>{@code UserActivityProgress}를 SELECT FOR UPDATE로 잠금하여
 * 동일 사용자의 동시 활동 요청(예: 리뷰 빠른 연속 제출)에서 한도 초과 지급을 방지한다.</p>
 *
 * <h3>예외 처리 전략 (2026-04-14 좁힘)</h3>
 * <p>기존에는 {@code catch (Exception e)}로 모든 예외를 삼켜 리워드 지급 실패를
 * 본 기능에 숨겼으나, 이 때문에 포인트가 안 들어오는 버그가 조용히 발생했다.
 * 이제 {@link BusinessException}(정책 미스/포인트 레코드 없음 등 도메인 예외)은 rethrow 하여
 * 호출자가 인지·롤백하도록 하고, 그 외 기술적 예외(DB 일시 장애 등)만 warn 로그 후 EMPTY 반환한다.</p>
 *
 * <h3>등급 배율 적용</h3>
 * <ul>
 *   <li>{@code point_type='earn'} — {@code floor(policy.pointsAmount × grade.rewardMultiplier)}</li>
 *   <li>{@code point_type='bonus'} — {@code policy.pointsAmount} (배율 미적용, 고정 포인트)</li>
 * </ul>
 *
 * <h3>설계 참조</h3>
 * <p>설계서 v2.4 §5.2 — 활동 리워드 서비스 상세 구현 가이드</p>
 *
 * @see RewardPolicy  활동 정책 정의 (points_amount, daily_limit, max_count, threshold 등)
 * @see UserActivityProgress  활동 카운터 캐시 (total_count, daily_count, rewarded_* 등)
 * @see PointService  포인트 잔액 변경 위임
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    /** 리워드 정책 리포지토리 — action_type으로 정책 조회 */
    private final RewardPolicyRepository rewardPolicyRepository;

    /** 유저 활동 진행률 리포지토리 — SELECT FOR UPDATE 잠금 조회 포함 */
    private final UserActivityProgressRepository userActivityProgressRepository;

    /** 사용자 포인트 리포지토리 — 일일 상한 검사 시 등급 정보 참조 */
    private final UserPointRepository userPointRepository;

    /** 포인트 이력 리포지토리 — revokeReward 시 원본 지급액 조회 */
    private final PointsHistoryRepository pointsHistoryRepository;

    /** 포인트 서비스 — 실제 잔액 변경(earnPoint / deductPoint) 위임 */
    private final PointService pointService;

    // ────────────────────────────────────────────────────────────────
    // public 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 활동 발생 시 리워드를 지급한다 (표준 경로 — policy.pointsAmount 사용).
     *
     * <p>ReviewService, CommunityService 등 각 도메인 서비스가 활동 완료 후
     * {@code @Async} 없이 직접 호출한다. {@code REQUIRES_NEW}로 별도 트랜잭션에서
     * 수행되므로 본 기능의 트랜잭션 커밋/롤백에 영향을 주지 않는다.</p>
     *
     * <h4>지급 흐름</h4>
     * <ol>
     *   <li>reward_policy 조회 (is_active=true). 없으면 즉시 반환.</li>
     *   <li>user_activity_progress 조회 (SELECT FOR UPDATE). 없으면 신규 생성.</li>
     *   <li>lazy reset — 날짜가 바뀌었으면 일일 카운터 초기화.</li>
     *   <li>일일 상한(dailyCapUsed) 검사 — 등급별 daily_earn_cap 초과 시 미지급.</li>
     *   <li>canGrant() 검사 — daily_limit / max_count / cooldown / min_content_length.</li>
     *   <li>등급 배율 적용 (earn=배율 적용, bonus=고정).</li>
     *   <li>amount &gt; 0이면 pointService.earnPoint() 호출.</li>
     *   <li>progress 카운터 갱신.</li>
     *   <li>threshold 달성 정책 연쇄 확인.</li>
     * </ol>
     *
     * <h4>호출 예시</h4>
     * <pre>{@code
     * // ReviewService.createReview() 내에서
     * rewardService.grantReward(userId, "REVIEW_CREATE", "movie_" + movieId, review.getContent().length());
     * }</pre>
     *
     * @param userId        사용자 ID (VARCHAR(50))
     * @param actionType    활동 유형 코드 (reward_policy.action_type, 예: "REVIEW_CREATE")
     * @param referenceId   참조 ID — 중복 지급 방지 키 (예: "movie_123", "post_456")
     * @param contentLength 콘텐츠 길이 — min_content_length 검사에 사용 (없으면 0 전달)
     */
    @Transactional
    public RewardResult grantReward(String userId, String actionType, String referenceId, int contentLength) {
        try {
            log.debug("리워드 지급 시작: userId={}, actionType={}, referenceId={}, contentLength={}",
                    userId, actionType, referenceId, contentLength);

            // ① reward_policy 조회 — is_active=true인 정책만 반환
            Optional<RewardPolicy> policyOpt = rewardPolicyRepository.findByActionTypeAndIsActiveTrue(actionType);
            if (policyOpt.isEmpty()) {
                /* 정책 없음 또는 비활성 → 지급 없이 종료
                 * warn 레벨로 승격 — 운영 로그에서 actionType 오타/시드 누락을 즉시 감지하기 위함.
                 * (기존 debug 레벨 때문에 SIGNUP_BONUS 등 정책 미스가 조용히 묻혔다) */
                log.warn("리워드 정책 없음 또는 비활성: actionType={}", actionType);
                return RewardResult.EMPTY;
            }
            RewardPolicy policy = policyOpt.get();

            /* ② user_activity_progress 조회 (SELECT FOR UPDATE — 행 잠금으로 동시 요청 직렬화) */
            UserActivityProgress progress = userActivityProgressRepository
                    .findByUserIdAndActionTypeForUpdate(userId, actionType)
                    .orElseGet(() -> createAndSaveProgress(userId, actionType));

            /* ③ lazy reset — 날짜가 바뀌었으면 daily_count, rewarded_today_count를 0으로 초기화 */
            LocalDate today = LocalDate.now();
            progress.lazyResetIfNeeded(today);

            /* ④ 일일 활동 리워드 상한(daily_earn_cap) 검사
             *    등급별 하루 총 획득 가능 포인트를 초과하면 해당 일 추가 리워드 차단.
             *    daily_earn_cap=0이면 무제한 (상한 미적용). */
            if (!checkDailyEarnCap(userId, policy)) {
                log.debug("일일 활동 리워드 상한 초과 → 지급 차단: userId={}, actionType={}", userId, actionType);
                /* 상한 초과 시에도 활동 카운터는 증가시킨다 (실제 활동 기록 보존) */
                progress.incrementTotalCount();
                progress.incrementDailyCount();
                progress.updateLastActionAt();
                return RewardResult.EMPTY;
            }

            /* ⑤ canGrant() 검사 — daily_limit / max_count / cooldown / min_content_length */
            boolean eligible = canGrant(policy, progress, contentLength);
            if (!eligible) {
                log.debug("리워드 지급 조건 미충족: userId={}, actionType={}", userId, actionType);
                /* 조건 미충족 시에도 활동 자체는 카운팅 (마일스톤 threshold 판정용) */
                progress.incrementTotalCount();
                progress.incrementDailyCount();
                progress.updateLastActionAt();
                /* threshold 달성 여부는 여전히 확인 (활동은 발생했으므로) */
                checkThresholdRewards(userId, actionType, progress);
                return RewardResult.EMPTY;
            }

            /* ⑥ 등급 배율 적용
             *    point_type='earn' → amount = floor(pointsAmount × grade.rewardMultiplier)
             *    point_type='bonus' → amount = pointsAmount (배율 미적용, 고정 포인트) */
            int amount = resolveAmount(userId, policy);

            /* ⑦ 포인트 지급 — amount > 0인 경우에만 earnPoint 호출
             *    amount = 0이면 AI_CHAT_USE 등 카운팅 전용 활동 → earnPoint 스킵 */
            String previousGrade = null;
            String currentGrade = null;
            if (amount > 0) {
                var earnResult = pointService.earnPoint(
                        userId,
                        amount,
                        policy.getPointType(),            /* earn 또는 bonus */
                        policy.getActivityName() + " 리워드",
                        referenceId,
                        actionType,
                        true                               /* isActivityReward=true → earned_by_activity 반영 */
                );
                previousGrade = earnResult.previousGrade();
                currentGrade = earnResult.grade();
                log.info("리워드 지급 완료: userId={}, actionType={}, amount={}P, referenceId={}",
                        userId, actionType, amount, referenceId);
            } else {
                log.debug("0P 카운팅 전용 활동 (earnPoint 스킵): userId={}, actionType={}", userId, actionType);
            }

            /* ⑧ progress 카운터 갱신
             *    totalCount / dailyCount — 리워드 지급 여부 무관, 항상 증가
             *    rewardedTodayCount / rewardedTotalCount — 실제 포인트 지급 시에만 증가 */
            progress.incrementTotalCount();
            progress.incrementDailyCount();
            progress.updateLastActionAt();
            if (amount > 0) {
                progress.incrementRewardedTodayCount();
                progress.incrementRewardedTotalCount();
            }

            /* ⑨ threshold 달성 정책 연쇄 확인
             *    parent_action_type = actionType인 자식 정책들의 달성 여부를 확인하고
             *    달성 시 보너스 지급 (재귀 호출). 자식 정책의 parent=NULL이므로 무한 재귀 불가. */
            checkThresholdRewards(userId, actionType, progress);

            /* ⑩ 등급 승격 보너스 — earnPoint 결과에서 등급 변경을 감지하여 GRADE_UP_* 지급.
             *    GRADE_UP_* 정책의 max_count=1이므로 중복 지급 불가. */
            if (previousGrade != null && currentGrade != null
                    && !currentGrade.equals(previousGrade)) {
                String gradeUpAction = "GRADE_UP_" + currentGrade;
                log.info("등급 승격 감지 → 보너스 지급 시도: userId={}, {} → {}, actionType={}",
                        userId, previousGrade, currentGrade, gradeUpAction);
                grantReward(userId, gradeUpAction, "grade_" + currentGrade + "_" + userId, 0);
            }

            /* ⑪ 지급 결과 반환 — 호출자가 API 응답에 포함할 수 있도록 */
            return RewardResult.of(amount, policy.getActivityName());

        } catch (BusinessException be) {
            /* 도메인 예외(POINT_NOT_FOUND 등)는 rethrow — 본 기능과 함께 롤백시켜 데이터 정합성 보존.
             * REQUIRED 전파로 변경되었으므로 호출자 트랜잭션에 롤백 마킹이 전파된다. */
            log.warn("리워드 지급 도메인 예외 — 본 트랜잭션과 함께 롤백: userId={}, actionType={}, code={}",
                    userId, actionType, be.getErrorCode(), be);
            throw be;
        } catch (Exception e) {
            /* 기술적 예외(DB 일시 장애 등)는 본 기능에 전파하지 않는다 — warn 로그 후 EMPTY 반환 */
            log.warn("리워드 지급 중 기술 예외 발생 (본 기능에 영향 없음): userId={}, actionType={}, error={}",
                    userId, actionType, e.getMessage(), e);
            return RewardResult.EMPTY;
        }
    }

    /**
     * 가변 금액 리워드를 지급한다 (외부 지정 amount 사용).
     *
     * <p>{@link #grantReward(String, String, String, int)}와 동일한 흐름이나,
     * {@code policy.pointsAmount} 대신 호출자가 전달한 {@code amount}를 사용한다.
     * 도장깨기 코스 완료(COURSE_COMPLETE), 업적 달성(ACHIEVEMENT_UNLOCK) 등
     * 활동별로 포인트가 다른 가변 리워드에 사용한다.</p>
     *
     * <h4>호출 예시</h4>
     * <pre>{@code
     * // RoadmapService.completeCourse() 내에서 (코스별로 포인트가 다름)
     * rewardService.grantRewardWithAmount(userId, "COURSE_COMPLETE",
     *     "course_" + courseId, course.getRewardPoints());
     * }</pre>
     *
     * @param userId     사용자 ID
     * @param actionType 활동 유형 코드 (reward_policy.action_type)
     * @param referenceId 참조 ID
     * @param amount     지급할 포인트 (외부 지정, policy.pointsAmount 무시)
     */
    @Transactional
    public RewardResult grantRewardWithAmount(String userId, String actionType, String referenceId, int amount) {
        try {
            log.debug("가변 금액 리워드 지급 시작: userId={}, actionType={}, referenceId={}, amount={}",
                    userId, actionType, referenceId, amount);

            /* ① reward_policy 조회 — 활성 정책 없으면 종료 */
            Optional<RewardPolicy> policyOpt = rewardPolicyRepository.findByActionTypeAndIsActiveTrue(actionType);
            if (policyOpt.isEmpty()) {
                log.warn("리워드 정책 없음 또는 비활성: actionType={}", actionType);
                return RewardResult.EMPTY;
            }
            RewardPolicy policy = policyOpt.get();

            /* ② user_activity_progress 조회 (SELECT FOR UPDATE) */
            UserActivityProgress progress = userActivityProgressRepository
                    .findByUserIdAndActionTypeForUpdate(userId, actionType)
                    .orElseGet(() -> createAndSaveProgress(userId, actionType));

            /* ③ lazy reset */
            LocalDate today = LocalDate.now();
            progress.lazyResetIfNeeded(today);

            /* ④ 일일 상한 검사 */
            if (!checkDailyEarnCap(userId, policy)) {
                log.debug("일일 활동 리워드 상한 초과 → 지급 차단: userId={}, actionType={}", userId, actionType);
                progress.incrementTotalCount();
                progress.incrementDailyCount();
                progress.updateLastActionAt();
                return RewardResult.EMPTY;
            }

            /* ⑤ canGrant() 검사 (contentLength=0 — 가변 금액 리워드는 길이 검사 생략) */
            boolean eligible = canGrant(policy, progress, 0);
            if (!eligible) {
                log.debug("리워드 지급 조건 미충족: userId={}, actionType={}", userId, actionType);
                progress.incrementTotalCount();
                progress.incrementDailyCount();
                progress.updateLastActionAt();
                checkThresholdRewards(userId, actionType, progress);
                return RewardResult.EMPTY;
            }

            /* ⑥ 외부 지정 amount 사용 (policy.pointsAmount 무시)
             *    단, point_type='earn'이어도 여기서는 배율을 적용하지 않는다.
             *    호출자(RoadmapService 등)가 이미 최종 금액을 전달하기 때문. */
            if (amount > 0) {
                pointService.earnPoint(
                        userId,
                        amount,
                        policy.getPointType(),
                        policy.getActivityName() + " 리워드",
                        referenceId,
                        actionType,
                        true
                );
                log.info("가변 금액 리워드 지급 완료: userId={}, actionType={}, amount={}P, referenceId={}",
                        userId, actionType, amount, referenceId);
            }

            /* ⑦ progress 갱신 */
            progress.incrementTotalCount();
            progress.incrementDailyCount();
            progress.updateLastActionAt();
            if (amount > 0) {
                progress.incrementRewardedTodayCount();
                progress.incrementRewardedTotalCount();
            }

            /* ⑧ threshold 달성 연쇄 확인 */
            checkThresholdRewards(userId, actionType, progress);

            /* ⑨ 지급 결과 반환 */
            return RewardResult.of(amount, policy.getActivityName());

        } catch (BusinessException be) {
            /* 도메인 예외는 rethrow — 본 기능과 함께 롤백 (REQUIRED 전파) */
            log.warn("가변 금액 리워드 지급 도메인 예외 — 본 트랜잭션과 함께 롤백: userId={}, actionType={}, code={}",
                    userId, actionType, be.getErrorCode(), be);
            throw be;
        } catch (Exception e) {
            log.warn("가변 금액 리워드 지급 중 기술 예외 발생: userId={}, actionType={}, error={}",
                    userId, actionType, e.getMessage(), e);
            return RewardResult.EMPTY;
        }
    }

    /**
     * 리워드를 회수한다 (콘텐츠 삭제, 어뷰징 제재 등).
     *
     * <p>points_history에서 원본 지급 이력을 조회하여 정확한 point_change 금액으로
     * deductPoint를 호출한다. 이력이 없으면 회수 생략 (이미 회수되었거나 미지급 상태).</p>
     *
     * <h4>회수 흐름</h4>
     * <ol>
     *   <li>PointsHistoryRepository에서 (userId, actionType, referenceId) 조합으로 원본 이력 조회.</li>
     *   <li>이력의 point_change (양수값)를 회수 금액으로 사용.</li>
     *   <li>pointService.deductPoint()로 잔액 차감
     *       (sessionId 자리에 referenceId + "_revoke"를 전달하여 추적 가능하게 함).</li>
     *   <li>progress.decrementTotalCount(), decrementRewardedTotalCount() 호출.</li>
     * </ol>
     *
     * <h4>호출 예시</h4>
     * <pre>{@code
     * // ReviewService.deleteReview() 내에서
     * rewardService.revokeReward(userId, "REVIEW_CREATE", "movie_" + movieId);
     * }</pre>
     *
     * @param userId      사용자 ID
     * @param actionType  원본 리워드의 활동 유형 코드
     * @param referenceId 원본 리워드의 참조 ID (회수 대상 식별 키)
     */
    @Transactional
    public void revokeReward(String userId, String actionType, String referenceId) {
        try {
            log.debug("리워드 회수 시작: userId={}, actionType={}, referenceId={}",
                    userId, actionType, referenceId);

            /* ① points_history에서 원본 지급 이력 조회
             *    (userId + actionType + referenceId) UNIQUE 인덱스로 정확한 1건 조회.
             *    policy 기준 대신 이력 기반으로 조회하여 등급 배율이 반영된 실제 지급액을 회수. */
            Optional<PointsHistory> historyOpt =
                    pointsHistoryRepository.findByUserIdAndActionTypeAndReferenceId(userId, actionType, referenceId);

            if (historyOpt.isEmpty()) {
                /* 원본 이력 없음 — 이미 회수되었거나 미지급 상태 → 생략 */
                log.debug("회수 대상 이력 없음 (이미 회수 또는 미지급): userId={}, actionType={}, referenceId={}",
                        userId, actionType, referenceId);
                return;
            }

            PointsHistory original = historyOpt.get();
            /* point_change는 지급 시 양수 값 — 회수할 금액 */
            int revokeAmount = original.getPointChange();

            if (revokeAmount <= 0) {
                /* 원본이 이미 차감 이력이거나 0P 카운팅 전용 → 회수 불필요 */
                log.debug("회수 불필요 (원본 point_change={} ≤ 0): userId={}, actionType={}",
                        revokeAmount, userId, actionType);
                return;
            }

            /* ② deductPoint 호출로 잔액 차감
             *    sessionId 자리에 referenceId + "_revoke"를 전달하여 회수 추적 가능.
             *    deductPoint는 별도 points_history에 point_type='spend' 레코드를 INSERT하므로
             *    원장 연속성이 유지된다. */
            pointService.deductPoint(
                    userId,
                    revokeAmount,
                    referenceId + "_revoke",     /* sessionId 자리 — 회수 식별용 */
                    actionType + " 리워드 회수"
            );
            log.info("리워드 회수 완료: userId={}, actionType={}, revokedAmount={}P, referenceId={}",
                    userId, actionType, revokeAmount, referenceId);

            /* ③ progress 카운터 감소
             *    totalCount: 실제 활동 횟수 감소 (마일스톤 재판정 시 참고값 보정)
             *    rewardedTotalCount: 지급 횟수 감소 (향후 재지급 한도 복원) */
            userActivityProgressRepository.findByUserIdAndActionType(userId, actionType)
                    .ifPresent(progress -> {
                        progress.decrementTotalCount();
                        progress.decrementRewardedTotalCount();
                        log.debug("progress 카운터 감소: userId={}, actionType={}, totalCount={}, rewardedTotal={}",
                                userId, actionType, progress.getTotalCount(), progress.getRewardedTotalCount());
                    });

        } catch (Exception e) {
            log.warn("리워드 회수 중 예외 발생: userId={}, actionType={}, error={}",
                    userId, actionType, e.getMessage(), e);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 리워드 지급 가능 여부를 판정한다.
     *
     * <p>다음 4가지 조건을 순서대로 검사하며, 하나라도 실패하면 {@code false} 반환.
     * 모두 통과해야 지급 가능.</p>
     *
     * <h4>검사 항목</h4>
     * <ol>
     *   <li><b>일일 한도</b>: hasDailyLimit()이 true이면
     *       {@code progress.rewardedTodayCount < policy.dailyLimit} 확인</li>
     *   <li><b>평생 횟수 한도</b>: hasMaxCount()이 true이면
     *       {@code progress.rewardedTotalCount < policy.maxCount} 확인</li>
     *   <li><b>쿨다운</b>: hasCooldown()이 true이면
     *       {@code lastActionAt + cooldownSeconds < now} 확인
     *       (lastActionAt=null이면 첫 활동 → 통과)</li>
     *   <li><b>최소 콘텐츠 길이</b>: hasMinContentLength()이 true이면
     *       {@code contentLength >= policy.minContentLength} 확인</li>
     * </ol>
     *
     * @param policy        적용할 리워드 정책
     * @param progress      현재 사용자의 활동 진행률 (lazy reset 완료 상태)
     * @param contentLength 콘텐츠 길이 (길이 검사 없는 활동은 0 전달)
     * @return 지급 가능하면 {@code true}, 한 가지라도 조건 미충족이면 {@code false}
     */
    private boolean canGrant(RewardPolicy policy, UserActivityProgress progress, int contentLength) {

        /* 검사 1: 일일 한도 — daily_limit > 0이면 오늘 지급 횟수를 한도와 비교 */
        if (policy.hasDailyLimit()) {
            if (progress.getRewardedTodayCount() >= policy.getDailyLimit()) {
                log.debug("일일 한도 초과: rewardedToday={}, dailyLimit={}",
                        progress.getRewardedTodayCount(), policy.getDailyLimit());
                return false;
            }
        }

        /* 검사 2: 평생 횟수 한도 — max_count > 0이면 누적 지급 횟수를 한도와 비교 */
        if (policy.hasMaxCount()) {
            if (progress.getRewardedTotalCount() >= policy.getMaxCount()) {
                log.debug("평생 횟수 한도 초과: rewardedTotal={}, maxCount={}",
                        progress.getRewardedTotalCount(), policy.getMaxCount());
                return false;
            }
        }

        /* 검사 3: 쿨다운 — cooldown_seconds > 0이면 마지막 활동 시각 + 쿨다운 < 현재 확인
         *          lastActionAt = null이면 첫 활동 → 쿨다운 패스 */
        if (policy.hasCooldown()) {
            LocalDateTime lastAction = progress.getLastActionAt();
            if (lastAction != null) {
                LocalDateTime cooldownExpiry = lastAction.plusSeconds(policy.getCooldownSeconds());
                if (LocalDateTime.now().isBefore(cooldownExpiry)) {
                    log.debug("쿨다운 미경과: lastActionAt={}, cooldownSeconds={}, expiry={}",
                            lastAction, policy.getCooldownSeconds(), cooldownExpiry);
                    return false;
                }
            }
        }

        /* 검사 4: 최소 콘텐츠 길이 — min_content_length > 0이면 전달받은 길이와 비교 */
        if (policy.hasMinContentLength()) {
            if (contentLength < policy.getMinContentLength()) {
                log.debug("콘텐츠 길이 부족: contentLength={}, minRequired={}",
                        contentLength, policy.getMinContentLength());
                return false;
            }
        }

        return true;
    }

    /**
     * threshold 기반 달성 정책을 순차적으로 확인하고 달성 시 보너스를 지급한다.
     *
     * <p>{@code parent_action_type = actionType}인 모든 활성 달성 정책을 조회하여
     * 각각의 threshold_target에 따라 달성 여부를 판정한다.</p>
     *
     * <h4>threshold_target별 판정 로직</h4>
     * <ul>
     *   <li>{@code TOTAL}: {@code progress.totalCount >= policy.thresholdCount}
     *       — 누적 달성. max_count=1이므로 최초 1회만 지급.</li>
     *   <li>{@code DAILY}: {@code progress.dailyCount == policy.thresholdCount}
     *       — 정확히 도달한 시점에만 지급 (매일 반복 가능).</li>
     *   <li>{@code STREAK}: {@code progress.currentStreak > 0
     *       && progress.currentStreak % policy.thresholdCount == 0}
     *       — streak이 threshold의 배수가 될 때마다 지급 (7일, 14일, 21일…).</li>
     * </ul>
     *
     * <h4>재귀 안전성</h4>
     * <p>달성 정책의 {@code parentActionType}은 NULL이므로
     * {@code findByParentActionTypeAndIsActiveTrue(null)}이 빈 리스트를 반환하여
     * 무한 재귀가 발생하지 않는다.</p>
     *
     * @param userId      사용자 ID
     * @param actionType  방금 발생한 활동 유형 코드 (부모 활동)
     * @param progress    현재 사용자의 활동 진행률 (카운터 갱신 완료 상태)
     */
    private void checkThresholdRewards(String userId, String actionType, UserActivityProgress progress) {
        /* parent_action_type = actionType인 달성 정책 목록 조회
         * 달성 정책 자체는 parent=NULL이므로 재귀 호출 시 이 목록이 빈 리스트 → 종료 */
        List<RewardPolicy> thresholdPolicies =
                rewardPolicyRepository.findByParentActionTypeAndIsActiveTrue(actionType);

        if (thresholdPolicies.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now();

        for (RewardPolicy thresholdPolicy : thresholdPolicies) {
            /* threshold_target이 없거나 threshold_count가 0이면 일반 활동 → 스킵 */
            if (!thresholdPolicy.isThresholdBased()) {
                continue;
            }

            String target = thresholdPolicy.getThresholdTarget();
            int threshold = thresholdPolicy.getThresholdCount();
            boolean achieved = false;
            String refId = null;

            /* threshold_target별 달성 판정 및 referenceId 생성 */
            switch (target) {
                case "TOTAL":
                    /* 누적 달성 — progress.totalCount가 threshold 이상이면 달성.
                     * max_count=1이므로 canGrant()에서 중복 지급이 자동 차단된다. */
                    if (progress.getTotalCount() >= threshold) {
                        achieved = true;
                        refId = actionType + "_total_" + threshold;
                    }
                    break;

                case "DAILY":
                    /* 일일 달성 — 정확히 threshold와 일치하는 시점에만 지급.
                     * ">="이 아닌 "=="을 사용하여 해당 횟수를 넘어선 뒤에는 중복 지급 안 됨. */
                    if (progress.getDailyCount() == threshold) {
                        achieved = true;
                        refId = actionType + "_daily_" + today;
                    }
                    break;

                case "STREAK":
                    /* 연속 달성 — current_streak가 threshold의 배수가 될 때마다 지급.
                     * currentStreak=0이면 끊긴 상태이므로 제외. */
                    if (progress.getCurrentStreak() > 0
                            && progress.getCurrentStreak() % threshold == 0) {
                        achieved = true;
                        refId = actionType + "_streak_" + today;
                    }
                    break;

                default:
                    log.warn("알 수 없는 threshold_target: {}, actionType={}", target, thresholdPolicy.getActionType());
                    break;
            }

            if (achieved && refId != null) {
                log.debug("threshold 달성 → 보너스 지급 시도: userId={}, thresholdActionType={}, refId={}",
                        userId, thresholdPolicy.getActionType(), refId);
                /* 달성 보너스 지급 — REQUIRES_NEW로 별도 트랜잭션에서 수행
                 * contentLength=0: threshold 달성 보너스는 길이 검사 생략 */
                grantReward(userId, thresholdPolicy.getActionType(), refId, 0);
            }
        }
    }

    /**
     * 등급 배율을 적용하여 최종 지급 포인트를 계산한다.
     *
     * <p>point_type='earn'이면 {@code floor(policy.pointsAmount × grade.rewardMultiplier)},
     * point_type='bonus'이면 {@code policy.pointsAmount} (배율 미적용).</p>
     *
     * <p>UserPoint 조회 실패(미초기화 사용자 등) 또는 grade가 null이면 배율 1.0을 적용한다.</p>
     *
     * @param userId 사용자 ID
     * @param policy 적용할 리워드 정책
     * @return 최종 지급 포인트 (배율 및 floor 처리 완료)
     */
    private int resolveAmount(String userId, RewardPolicy policy) {
        int base = policy.getPointsAmount();

        /* bonus 타입은 등급 배율 미적용 — 고정 포인트 반환 */
        if (!"earn".equals(policy.getPointType())) {
            return base;
        }

        /* earn 타입 — 사용자 등급 배율 적용
         * UserPoint → Grade → rewardMultiplier 순으로 접근.
         * 조회 실패 또는 null이면 기본 배율 1.0 사용. */
        try {
            Optional<UserPoint> userPointOpt = userPointRepository.findByUserId(userId);
            if (userPointOpt.isEmpty()) {
                log.debug("UserPoint 없음 → 기본 배율 1.0 적용: userId={}", userId);
                return base;
            }
            Grade grade = userPointOpt.get().getGrade();
            if (grade == null) {
                log.debug("등급 정보 없음 → 기본 배율 1.0 적용: userId={}", userId);
                return base;
            }
            BigDecimal multiplier = grade.getRewardMultiplier();
            if (multiplier == null || multiplier.compareTo(BigDecimal.ZERO) <= 0) {
                return base;
            }
            /* floor(base × multiplier) — BigDecimal 정밀도 유지 후 int 변환 */
            int result = BigDecimal.valueOf(base)
                    .multiply(multiplier)
                    .setScale(0, java.math.RoundingMode.FLOOR)
                    .intValue();
            log.debug("등급 배율 적용: base={}P × {} = {}P (userId={}, grade={})",
                    base, multiplier, result, userId, grade.getGradeCode());
            return result;
        } catch (Exception e) {
            /* 배율 조회 실패 시 기본값 사용 — 본 기능에 영향 없음 */
            log.warn("등급 배율 조회 실패 → 기본 배율 1.0 적용: userId={}, error={}", userId, e.getMessage());
            return base;
        }
    }

    /**
     * 일일 활동 리워드 상한(daily_earn_cap)을 검사한다.
     *
     * <p>등급별 하루 활동 리워드 총 획득 가능 포인트를 초과하면 {@code false}를 반환하여
     * 극단적 어뷰징을 방지한다. daily_earn_cap=0이면 무제한(상한 미적용).</p>
     *
     * <p>bonus 타입 정책(마일스톤 등)은 상한 검사를 생략한다 (고정 포인트이므로
     * daily_earn_cap에 포함시키지 않음). earn 타입만 상한 적용.</p>
     *
     * <h4>2026-04-13 버그 수정</h4>
     * <p>기존 코드는 UserPoint를 조회만 하고 resetDailyIfNeeded()를 호출하지 않아,
     * 날짜가 바뀐 후 어제의 dailyCapUsed 값(예: 100)이 그대로 남아
     * capUsed >= cap 조건에 걸려 하루 종일 모든 earn 리워드가 차단되는 치명적 버그가 있었다.
     * earnPoint()에서 resetDailyIfNeeded()가 호출되지만, 이 메서드가 false를 반환하면
     * earnPoint()에 도달하지 못하므로 리셋이 영영 일어나지 않았다.</p>
     *
     * @param userId 사용자 ID
     * @param policy 적용할 리워드 정책 (point_type으로 earn/bonus 구분)
     * @return 지급 가능하면 {@code true}, 상한 초과이면 {@code false}
     */
    private boolean checkDailyEarnCap(String userId, RewardPolicy policy) {
        /* bonus 타입은 일일 상한 검사 생략 */
        if (!"earn".equals(policy.getPointType())) {
            return true;
        }

        try {
            Optional<UserPoint> userPointOpt = userPointRepository.findByUserId(userId);
            if (userPointOpt.isEmpty()) {
                /* UserPoint 없음 → 상한 검사 생략 (초기화 전 사용자) */
                return true;
            }
            UserPoint userPoint = userPointOpt.get();
            Grade grade = userPoint.getGrade();

            /* grade 없거나 dailyEarnCap=0이면 무제한 */
            if (grade == null || grade.getDailyEarnCap() == null || grade.getDailyEarnCap() <= 0) {
                return true;
            }

            /* ★ 2026-04-13 수정: 날짜 변경 시 dailyCapUsed를 0으로 리셋한 뒤 비교.
             *    기존에는 resetDailyIfNeeded()를 호출하지 않아 어제 값으로 비교했다.
             *    이 메서드는 읽기 전용 검사이므로 리셋한 엔티티 상태는 JPA dirty check에 의해
             *    트랜잭션 종료 시 자동으로 반영된다 (REQUIRES_NEW 범위 내). */
            userPoint.resetDailyIfNeeded(LocalDate.now());

            int capUsed = userPoint.getDailyCapUsed();
            int cap = grade.getDailyEarnCap();

            if (capUsed >= cap) {
                log.debug("일일 활동 리워드 상한 도달: userId={}, capUsed={}, cap={}, grade={}",
                        userId, capUsed, cap, grade.getGradeCode());
                return false;
            }
        } catch (Exception e) {
            /* 상한 조회 실패 시 통과 처리 — 지급 차단보다 어뷰징 가능성이 낮음 */
            log.warn("일일 상한 검사 중 예외 (통과 처리): userId={}, error={}", userId, e.getMessage());
        }
        return true;
    }

    /**
     * 사용자의 활동 진행률 레코드를 신규 생성하고 저장한다.
     *
     * <p>해당 (userId, actionType) 조합의 레코드가 없을 때 호출된다.
     * 최초 활동 시 1회만 실행되며, 이후에는 기존 레코드를 재사용한다.</p>
     *
     * @param userId     사용자 ID
     * @param actionType 활동 유형 코드
     * @return 저장된 {@link UserActivityProgress} (모든 카운터 0, lastDailyReset=오늘)
     */
    private UserActivityProgress createAndSaveProgress(String userId, String actionType) {
        UserActivityProgress newProgress = UserActivityProgress.builder()
                .userId(userId)
                .actionType(actionType)
                /* 나머지 필드는 @Builder.Default로 0/null/오늘 초기값 설정됨 */
                .build();
        UserActivityProgress saved = userActivityProgressRepository.save(newProgress);
        log.debug("UserActivityProgress 신규 생성: userId={}, actionType={}", userId, actionType);
        return saved;
    }
}
