package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.reward.config.QuotaProperties;
import com.monglepick.monglepickbackend.domain.reward.dto.QuotaDto.GradeQuota;
import com.monglepick.monglepickbackend.domain.reward.dto.QuotaDto.QuotaCheckResult;
import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 등급별 쿼터(이용 한도) 서비스 — AI 추천 사용 허용 여부 및 소스 결정.
 *
 * <h3>v3.3 변경 (2026-04-07)</h3>
 * <p>AI 쿼터 카운터 조회/갱신을 {@code user_points} → {@code user_ai_quota}로 이전.
 * {@link UserAiQuotaRepository}를 단독으로 주입받으며,
 * {@code PointsHistory.description LIKE '%AI 추천%'} 안티패턴을 완전히 폐기한다.
 * {@code UserPointRepository}는 등급(Grade) 조회 목적으로만 유지한다.</p>
 *
 * <h3>AI 요청 처리 순서 (v3.2 3-소스 모델 유지)</h3>
 * <ol>
 *   <li><b>소스 1 — GRADE_FREE</b>: {@code user_ai_quota.daily_ai_used < grade.daily_ai_limit}
 *       → 무료 허용. {@code daily_ai_used++}. monthly 카운터 미반영.</li>
 *   <li><b>소스 2 — SUB_BONUS</b>: 활성 구독의 {@code remaining_ai_bonus > 0}
 *       → 구독 보너스 풀 1회 차감. 월이 바뀌었으면 먼저 lazy reset. monthly 카운터 미반영.</li>
 *   <li><b>소스 3 — PURCHASED</b>: {@code purchased_ai_tokens > 0}
 *       AND {@code monthly_coupon_used < grade.monthly_ai_limit}
 *       → 이용권 1회 차감 + monthly_coupon_used 증가. 일일 한도 우회, 쿠폰 월한도 적용.</li>
 *   <li><b>BLOCKED</b>: 모든 소스 소진 또는 쿠폰 월한도 초과 → 차단 + 안내 메시지.</li>
 * </ol>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>v3.0: 3-소스 모델 도입 (GRADE_FREE/SUB_BONUS/PURCHASED)</li>
 *   <li>v3.2: DIAMOND 등급 추가, PURCHASED 쿠폰 월한도 검사, 6등급 gradeQuotas 맵</li>
 *   <li>v3.3: AI 카운터 source를 user_points → user_ai_quota로 이전.
 *       UserAiQuotaRepository 주입. UserPointRepository는 Grade 조회용으로 축소.</li>
 * </ul>
 *
 * @see QuotaCheckResult
 * @see GradeQuota
 * @see UserAiQuota
 */
@Service
@Slf4j
public class QuotaService {

    /** UserPoint 리포지토리 — Grade FK 조회용 (v3.3: AI 카운터 조회 제거) */
    private final UserPointRepository userPointRepository;

    /** UserAiQuota 리포지토리 — AI 카운터(daily_ai_used, purchased_ai_tokens 등) 조회/갱신 (v3.3 신규) */
    private final UserAiQuotaRepository userAiQuotaRepository;

    /** UserSubscription 리포지토리 — 활성 구독의 remaining_ai_bonus 조회 및 갱신 */
    private final UserSubscriptionRepository userSubscriptionRepository;

    /**
     * 등급별 쿼터 설정 맵.
     *
     * <p>키: 등급코드(NORMAL/BRONZE/SILVER/GOLD/PLATINUM/DIAMOND), 값: 해당 등급의 쿼터 설정.
     * 생성자에서 {@code application.yml}의 {@code app.quota.*} 값으로 초기화된다.</p>
     */
    private final Map<String, GradeQuota> gradeQuotas;

    /**
     * 쿼터 서비스 생성자 (v3.3).
     *
     * <p>v3.3: {@link UserAiQuotaRepository} 추가. 6등급 gradeQuotas 맵 구성.</p>
     *
     * @param userPointRepository        UserPoint 리포지토리 (Grade 조회용)
     * @param userAiQuotaRepository      UserAiQuota 리포지토리 (AI 카운터 조회/갱신용)
     * @param userSubscriptionRepository UserSubscription 리포지토리 (구독 보너스 풀)
     * @param props                      등급별 쿼터 설정 (@ConfigurationProperties 바인딩)
     */
    public QuotaService(UserPointRepository userPointRepository,
                        UserAiQuotaRepository userAiQuotaRepository,
                        UserSubscriptionRepository userSubscriptionRepository,
                        QuotaProperties props) {
        this.userPointRepository = userPointRepository;
        this.userAiQuotaRepository = userAiQuotaRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;

        // NORMAL 등급: QuotaProperties에 normal()이 있으면 사용, 없으면 기본값
        GradeQuota normalQuota = props.normal() != null
                ? toGradeQuota(props.normal())
                : new GradeQuota(3, 200);   // 알갱이(NORMAL) 기본값: 일일 3회, 200자

        // DIAMOND 등급: yml 설정이 있으면 사용, 없으면 무제한 기본값
        GradeQuota diamondQuota = props.diamond() != null
                ? toGradeQuota(props.diamond())
                : new GradeQuota(-1, -1);   // 몽아일체(DIAMOND) 기본값: 무제한

        // BRONZE/SILVER/GOLD/PLATINUM: yml 미설정 시 NPE 방지 — null guard 적용
        GradeQuota bronzeQuota   = props.bronze()   != null ? toGradeQuota(props.bronze())   : new GradeQuota(5, 400);
        GradeQuota silverQuota   = props.silver()   != null ? toGradeQuota(props.silver())   : new GradeQuota(7, 500);
        GradeQuota goldQuota     = props.gold()     != null ? toGradeQuota(props.gold())     : new GradeQuota(10, 800);
        GradeQuota platinumQuota = props.platinum() != null ? toGradeQuota(props.platinum()) : new GradeQuota(15, 3000);

        this.gradeQuotas = Map.of(
                "NORMAL",   normalQuota,
                "BRONZE",   bronzeQuota,
                "SILVER",   silverQuota,
                "GOLD",     goldQuota,
                "PLATINUM", platinumQuota,
                "DIAMOND",  diamondQuota
        );

        log.info("v3.3 등급별 쿼터 설정 로드 완료 (3-소스 모델, 6등급, UserAiQuota 분리): " +
                        "NORMAL={}, BRONZE={}, SILVER={}, GOLD={}, PLATINUM={}, DIAMOND={}",
                gradeQuotas.get("NORMAL"), gradeQuotas.get("BRONZE"),
                gradeQuotas.get("SILVER"), gradeQuotas.get("GOLD"),
                gradeQuotas.get("PLATINUM"), gradeQuotas.get("DIAMOND"));
    }

    /**
     * QuotaProperties.GradeConfig → GradeQuota DTO 변환 헬퍼.
     */
    private static GradeQuota toGradeQuota(QuotaProperties.GradeConfig config) {
        return new GradeQuota(config.dailyLimit(), config.maxInputLength());
    }

    // ──────────────────────────────────────────────
    // 쿼터 확인 (v3.3 — UserAiQuota 기반)
    // ──────────────────────────────────────────────

    /**
     * v3.3 AI 추천 쿼터를 3-소스 모델로 확인한다.
     *
     * <p>소스 1(GRADE_FREE) → 소스 2(SUB_BONUS) → 소스 3(PURCHASED) 순서로 확인하여
     * 최초 가능한 소스에서 1회 차감하고 {@link QuotaCheckResult}를 반환한다.</p>
     *
     * <h4>v3.3 변경점</h4>
     * <ul>
     *   <li>daily_ai_used, purchased_ai_tokens 조회/갱신: user_points → user_ai_quota</li>
     *   <li>소스 1 차감(daily_ai_used++): 이 메서드 내 {@code userAiQuota.incrementDailyAiUsed()} 직접 처리
     *       (기존 PointService.incrementAiUsage() 의존 제거)</li>
     *   <li>소스 3 차감: {@code userAiQuota.consumePurchasedToken()} 호출</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param grade  사용자 등급 문자열 (NORMAL/BRONZE/SILVER/GOLD/PLATINUM/DIAMOND).
     *               null이거나 알 수 없는 등급이면 NORMAL로 fallback
     * @return 쿼터 확인 결과 (source, 잔여 횟수, 최대 입력 길이 등 포함)
     */
    @Transactional
    public QuotaCheckResult checkQuota(String userId, String grade) {
        log.debug("v3.3 쿼터 확인 시작: userId={}, grade={}", userId, grade);

        // 1. 등급별 쿼터 설정 조회 (알 수 없는 등급이면 NORMAL fallback)
        String gradeKey = (grade != null) ? grade.toUpperCase() : "NORMAL";
        GradeQuota quota = gradeQuotas.getOrDefault(gradeKey, gradeQuotas.get("NORMAL"));

        LocalDate today = LocalDate.now();

        // ── v3.3: AI 카운터는 user_ai_quota에서 읽는다 ───────────────────────────
        // user_ai_quota 레코드가 없으면 회원가입 초기화 누락 — 신규 생성 후 진행 (정합성 복구)
        UserAiQuota aiQuota = userAiQuotaRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    log.warn("user_ai_quota 레코드 없음 — 신규 생성 (정합성 복구): userId={}", userId);
                    return userAiQuotaRepository.save(
                            UserAiQuota.builder().userId(userId).build()
                    );
                });

        // lazy reset: 날짜가 바뀌었으면 daily_ai_used, free_daily_granted 초기화
        aiQuota.resetDailyIfNeeded(today);

        int dailyUsed = aiQuota.getDailyAiUsed();
        int purchasedRemaining = aiQuota.getPurchasedAiTokens();

        // ── 소스 1: GRADE_FREE ────────────────────────────────────────────────
        // DIAMOND(-1)이면 dailyLimit 검사 스킵 (무제한)
        boolean gradeUnlimited = (quota.dailyLimit() == -1);
        if (gradeUnlimited || dailyUsed < quota.dailyLimit()) {
            // 일일 무료 한도 내 사용 가능 → daily_ai_used 즉시 증가
            aiQuota.incrementDailyAiUsed();
            int subBonus = getSubBonusRemaining(userId, today);
            log.debug("쿼터 확인 완료 - GRADE_FREE: userId={}, dailyUsedAfter={}, dailyLimit={}",
                    userId, aiQuota.getDailyAiUsed(), quota.dailyLimit());
            return new QuotaCheckResult(
                    true,
                    "GRADE_FREE",
                    aiQuota.getDailyAiUsed(), // 증가 후 값
                    quota.dailyLimit(),
                    subBonus,
                    purchasedRemaining,
                    quota.maxInputLength(),
                    ""
            );
        }

        log.debug("grade 일일 한도 소진: userId={}, dailyUsed={}, dailyLimit={} → 구독 보너스 확인",
                userId, dailyUsed, quota.dailyLimit());

        // ── 소스 2: SUB_BONUS ─────────────────────────────────────────────────
        // 활성 구독 조회 → 월 리셋 적용 → 보너스 1회 차감 (user_ai_quota 변경 없음)
        Optional<UserSubscription> subOpt = userSubscriptionRepository
                .findByUserIdAndStatusFetchPlan(userId, UserSubscription.Status.ACTIVE);

        if (subOpt.isPresent()) {
            UserSubscription sub = subOpt.get();
            int monthlyAiBonus = sub.getPlan().getMonthlyAiBonus();

            // lazy reset: 월이 바뀌었으면 remaining_ai_bonus를 plan.monthlyAiBonus로 리셋
            sub.resetAiBonusIfNeeded(today, monthlyAiBonus);

            if (sub.consumeAiBonus()) {
                // 구독 보너스 풀에서 1회 차감 성공 (JPA dirty checking으로 자동 반영)
                int remaining = sub.getRemainingAiBonus();
                log.debug("쿼터 확인 완료 - SUB_BONUS: userId={}, subRemainingAfter={}", userId, remaining);
                return new QuotaCheckResult(
                        true,
                        "SUB_BONUS",
                        dailyUsed,
                        quota.dailyLimit(),
                        remaining,
                        purchasedRemaining,
                        quota.maxInputLength(),
                        ""
                );
            }
            log.debug("구독 보너스 소진: userId={} → 구매 토큰 확인", userId);
        }

        // ── 소스 3: PURCHASED ─────────────────────────────────────────────────
        // v3.2: purchased_ai_tokens > 0 AND monthly_coupon_used < grade.monthly_ai_limit
        // v3.3: aiQuota에서 직접 조회/갱신
        if (purchasedRemaining > 0) {
            // 월 리셋: 새달이면 monthly_coupon_used 초기화
            aiQuota.resetMonthlyIfNeeded(today);

            int monthlyCouponUsed = aiQuota.getMonthlyCouponUsed();
            int monthlyLimit = getMonthlyLimitForGrade(userId);
            boolean monthlyLimitExceeded = (monthlyLimit != -1 && monthlyCouponUsed >= monthlyLimit);

            if (!monthlyLimitExceeded) {
                boolean consumed = aiQuota.consumePurchasedToken(); // purchased_ai_tokens--, monthly_coupon_used++
                if (consumed) {
                    int subBonus = subOpt.isPresent() ? subOpt.get().getRemainingAiBonus() : -1;
                    log.debug("쿼터 확인 완료 - PURCHASED: userId={}, purchasedAfter={}, monthlyCouponAfter={}",
                            userId, aiQuota.getPurchasedAiTokens(), aiQuota.getMonthlyCouponUsed());
                    return new QuotaCheckResult(
                            true,
                            "PURCHASED",
                            dailyUsed,
                            quota.dailyLimit(),
                            subBonus,
                            aiQuota.getPurchasedAiTokens(), // 차감 후 잔여
                            quota.maxInputLength(),
                            ""
                    );
                }
            } else {
                log.info("이용권 쿠폰 월한도 초과 - PURCHASED 불가: userId={}, monthlyCouponUsed={}, monthlyLimit={}",
                        userId, monthlyCouponUsed, monthlyLimit);
            }
        }

        // ── BLOCKED: 모든 소스 소진 ───────────────────────────────────────────
        int finalSubBonus = subOpt.isPresent() ? subOpt.get().getRemainingAiBonus() : -1;
        String blockedMsg = buildBlockedMessage(finalSubBonus, purchasedRemaining, quota.dailyLimit());

        log.info("AI 쿼터 모두 소진 - BLOCKED: userId={}, subBonusRemaining={}, purchasedRemaining={}",
                userId, finalSubBonus, purchasedRemaining);

        return new QuotaCheckResult(
                false,
                "BLOCKED",
                dailyUsed,
                quota.dailyLimit(),
                finalSubBonus,
                purchasedRemaining,
                quota.maxInputLength(),
                blockedMsg
        );
    }

    // ──────────────────────────────────────────────
    // 등급별 최대 입력 길이 조회
    // ──────────────────────────────────────────────

    /**
     * 등급별 최대 입력 글자 수를 반환한다.
     *
     * @param grade 사용자 등급. null이거나 알 수 없는 등급이면 NORMAL 기본값(200) 반환
     * @return 최대 입력 글자 수 (-1이면 무제한)
     */
    public int getMaxInputLength(String grade) {
        return gradeQuotas.getOrDefault(
                grade != null ? grade.toUpperCase() : "NORMAL",
                gradeQuotas.get("NORMAL")
        ).maxInputLength();
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ──────────────────────────────────────────────

    /**
     * 활성 구독의 AI 보너스 잔여 횟수를 조회한다 (GRADE_FREE 응답에 정보 제공용).
     *
     * @param userId 사용자 ID
     * @param today  오늘 날짜 (lazy reset 판정용)
     * @return 구독 보너스 잔여 횟수 (-1이면 구독 없음, 0이면 소진, 양수이면 잔여)
     */
    private int getSubBonusRemaining(String userId, LocalDate today) {
        return userSubscriptionRepository
                .findByUserIdAndStatusFetchPlan(userId, UserSubscription.Status.ACTIVE)
                .map(sub -> {
                    int bonus = sub.getPlan().getMonthlyAiBonus();
                    boolean newMonth = sub.getAiBonusReset() == null
                            || sub.getAiBonusReset().getYear() != today.getYear()
                            || sub.getAiBonusReset().getMonth() != today.getMonth();
                    return newMonth ? bonus : sub.getRemainingAiBonus();
                })
                .orElse(-1); // 구독 없음
    }

    /**
     * userId로 UserPoint → Grade 엔티티를 조회하여 월간 쿠폰 한도를 반환한다.
     *
     * <p>PURCHASED 소스 사용 가능 여부 판단에 사용.
     * UserPoint가 없거나 Grade가 null이면 NORMAL 기본값 10 반환.
     * -1이면 무제한(DIAMOND).</p>
     *
     * @param userId 사용자 ID
     * @return 월간 쿠폰 한도 (-1이면 무제한, 양수이면 해당 횟수)
     */
    private int getMonthlyLimitForGrade(String userId) {
        return userPointRepository.findByUserId(userId)
                .map(UserPoint::getGrade)
                .map(Grade::getMonthlyAiLimit)
                .orElse(10); // NORMAL 기본값
    }

    /**
     * BLOCKED 상태의 안내 메시지를 생성한다.
     *
     * @param subBonusRemaining 구독 보너스 잔여 (-1이면 구독 없음)
     * @param purchasedRemaining 구매 토큰 잔여
     * @param dailyLimit        일일 한도
     * @return 사용자 안내 메시지
     */
    private String buildBlockedMessage(int subBonusRemaining, int purchasedRemaining, int dailyLimit) {
        if (subBonusRemaining == -1) {
            return String.format(
                    "오늘 AI 추천 한도(%d회)를 모두 사용했습니다. " +
                            "구독 서비스에 가입하면 매월 최대 700회의 추가 AI 추천을 이용할 수 있습니다.",
                    dailyLimit
            );
        } else {
            return "오늘 AI 추천 한도와 이번 달 구독 보너스를 모두 사용했습니다. " +
                    "포인트 상점에서 AI 이용권을 구매하거나, 내일 다시 이용해주세요.";
        }
    }
}
