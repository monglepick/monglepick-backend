package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.reward.config.QuotaProperties;
import com.monglepick.monglepickbackend.domain.reward.dto.QuotaDto.GradeQuota;
import com.monglepick.monglepickbackend.domain.reward.dto.QuotaDto.QuotaCheckResult;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
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
 * <h3>v3.0 AI 3-소스 모델</h3>
 * <p>기존의 "일일 한도 + 월간 한도 + freeDaily + 10P 과금" 방식을
 * 3계층 소스 기반으로 전면 교체한다.</p>
 *
 * <h3>AI 요청 처리 순서</h3>
 * <ol>
 *   <li><b>소스 1 — GRADE_FREE</b>: {@code user_points.daily_ai_used < grade.daily_ai_limit}
 *       → 무료 허용. {@code daily_ai_used++}</li>
 *   <li><b>소스 2 — SUB_BONUS</b>: 활성 구독의 {@code remaining_ai_bonus > 0}
 *       → 구독 보너스 풀 1회 차감. 월이 바뀌었으면 먼저 lazy reset.</li>
 *   <li><b>소스 3 — PURCHASED</b>: {@code user_points.purchased_ai_tokens > 0}
 *       → 구매 토큰 1회 차감.</li>
 *   <li><b>BLOCKED</b>: 모든 소스 소진 → 차단 + 안내 메시지.</li>
 * </ol>
 *
 * <h3>v3.0 변경 사항</h3>
 * <ul>
 *   <li>제거: 월간 한도 검사 (monthlyAiUsed/monthlyLimit)</li>
 *   <li>제거: freeDaily/effectiveCost/baseCost 파라미터</li>
 *   <li>추가: 구독 보너스 풀 확인 (UserSubscriptionRepository)</li>
 *   <li>추가: 구매 토큰 확인 (UserPoint.purchasedAiTokens)</li>
 *   <li>변경: {@code checkQuota()} 파라미터에서 {@code baseCost} 제거</li>
 *   <li>변경: {@link QuotaCheckResult} — source/subBonusRemaining/purchasedRemaining 반환</li>
 * </ul>
 *
 * <h3>QuotaProperties 하위 호환</h3>
 * <p>{@code application.yml}의 {@code app.quota.*} 설정에서
 * {@code daily-limit}과 {@code max-input-length}만 사용한다.
 * {@code monthly-limit}과 {@code free-daily}는 yml에 남아있어도 무시된다.</p>
 *
 * @see QuotaCheckResult
 * @see GradeQuota
 * @see PointService#checkPoint(String, int)
 */
@Service
@Slf4j
public class QuotaService {

    /** UserPoint 리포지토리 — daily_ai_used/purchased_ai_tokens 필드 조회 및 갱신 */
    private final UserPointRepository userPointRepository;

    /** UserSubscription 리포지토리 — 활성 구독의 remaining_ai_bonus 조회 및 갱신 */
    private final UserSubscriptionRepository userSubscriptionRepository;

    /**
     * 등급별 쿼터 설정 맵.
     *
     * <p>키: 등급명(NORMAL/BRONZE/SILVER/GOLD/PLATINUM), 값: 해당 등급의 쿼터 설정.
     * 생성자에서 {@code application.yml}의 {@code app.quota.*} 값으로 초기화된다.
     * v3.0: GradeQuota 는 dailyLimit/maxInputLength 2개 필드만 보유.</p>
     */
    private final Map<String, GradeQuota> gradeQuotas;

    /**
     * 쿼터 서비스 생성자.
     *
     * <p>5등급(NORMAL/BRONZE/SILVER/GOLD/PLATINUM) 설정을 불변 맵으로 구성한다.
     * v3.0: QuotaProperties.GradeConfig에서 dailyLimit/maxInputLength만 사용.
     * monthlyLimit/freeDaily는 하위 호환을 위해 yml에 남을 수 있으나 이 생성자에서 무시한다.</p>
     *
     * @param userPointRepository        UserPoint 리포지토리
     * @param userSubscriptionRepository UserSubscription 리포지토리 (구독 보너스 풀 접근)
     * @param props                      등급별 쿼터 설정 (@ConfigurationProperties 바인딩)
     */
    public QuotaService(UserPointRepository userPointRepository,
                        UserSubscriptionRepository userSubscriptionRepository,
                        QuotaProperties props) {
        this.userPointRepository = userPointRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;

        // NORMAL 등급: QuotaProperties에 normal()이 있으면 사용, 없으면 기본값 (v3.0 §4.5)
        GradeQuota normalQuota = props.normal() != null
                ? toGradeQuota(props.normal())
                : new GradeQuota(3, 200);   // NORMAL 기본값: 일일 3회, 200자

        this.gradeQuotas = Map.of(
                "NORMAL",   normalQuota,
                "BRONZE",   toGradeQuota(props.bronze()),
                "SILVER",   toGradeQuota(props.silver()),
                "GOLD",     toGradeQuota(props.gold()),
                "PLATINUM", toGradeQuota(props.platinum())
        );

        log.info("v3.0 등급별 쿼터 설정 로드 완료 (3-소스 모델): NORMAL={}, BRONZE={}, SILVER={}, GOLD={}, PLATINUM={}",
                gradeQuotas.get("NORMAL"), gradeQuotas.get("BRONZE"),
                gradeQuotas.get("SILVER"), gradeQuotas.get("GOLD"),
                gradeQuotas.get("PLATINUM"));
    }

    /**
     * QuotaProperties.GradeConfig → GradeQuota DTO 변환 헬퍼.
     *
     * <p>v3.0: dailyLimit/maxInputLength만 추출. monthlyLimit/freeDaily 무시.</p>
     */
    private static GradeQuota toGradeQuota(QuotaProperties.GradeConfig config) {
        return new GradeQuota(config.dailyLimit(), config.maxInputLength());
    }

    // ──────────────────────────────────────────────
    // 쿼터 확인 (v3.0 3-소스 모델)
    // ──────────────────────────────────────────────

    /**
     * v3.0 AI 추천 쿼터를 3-소스 모델로 확인한다.
     *
     * <p>소스 1(GRADE_FREE) → 소스 2(SUB_BONUS) → 소스 3(PURCHASED) 순서로 확인하여
     * 최초 가능한 소스에서 1회 차감하고 {@link QuotaCheckResult}를 반환한다.</p>
     *
     * <p>이 메서드는 {@code @Transactional}이므로 소스 2/3 차감 시 JPA dirty checking으로
     * DB에 자동 반영된다. 소스 1 차감({@code daily_ai_used++})은 PointService에서
     * {@code incrementAiUsage()}를 통해 별도 처리한다.</p>
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>등급별 GradeQuota 조회 (알 수 없는 등급이면 NORMAL fallback)</li>
     *   <li>user_points에서 daily_ai_used 읽기 (lazy reset 적용)</li>
     *   <li>daily_ai_used &lt; dailyLimit → GRADE_FREE 반환 (차감은 PointService 담당)</li>
     *   <li>활성 구독 조회 → resetAiBonusIfNeeded → consumeAiBonus() → SUB_BONUS 반환</li>
     *   <li>purchased_ai_tokens &gt; 0 → consumePurchasedToken() → PURCHASED 반환</li>
     *   <li>모두 소진 → BLOCKED 반환</li>
     * </ol>
     *
     * @param userId 사용자 ID
     * @param grade  사용자 등급 문자열 (NORMAL/BRONZE/SILVER/GOLD/PLATINUM).
     *               null이거나 알 수 없는 등급이면 NORMAL로 fallback
     * @return 쿼터 확인 결과 (source, 잔여 횟수, 최대 입력 길이 등 포함)
     */
    @Transactional
    public QuotaCheckResult checkQuota(String userId, String grade) {
        log.debug("v3.0 쿼터 확인 시작: userId={}, grade={}", userId, grade);

        // 1. 등급별 쿼터 설정 조회 (알 수 없는 등급이면 NORMAL fallback)
        String gradeKey = (grade != null) ? grade.toUpperCase() : "NORMAL";
        GradeQuota quota = gradeQuotas.getOrDefault(gradeKey, gradeQuotas.get("NORMAL"));

        LocalDate today = LocalDate.now();

        // ── 소스 1: GRADE_FREE ────────────────────────────────────────────
        // user_points에서 daily_ai_used를 읽어 grade.daily_ai_limit과 비교.
        // lazy reset: dailyReset이 오늘이 아니면 0으로 간주한다 (읽기 전용 보정).
        // 실제 daily_ai_used 증가는 PointService.incrementAiUsage()에서 수행.
        int dailyUsed = 0;
        int purchasedRemaining = 0;
        Optional<UserPoint> userPointOpt = userPointRepository.findByUserId(userId);

        if (userPointOpt.isPresent()) {
            UserPoint up = userPointOpt.get();
            // 날짜가 바뀌었으면 일일 카운터를 0으로 간주 (lazy reset — 실제 DB 반영은 PointService)
            if (up.getDailyReset() != null && up.getDailyReset().equals(today)) {
                dailyUsed = up.getDailyAiUsed() != null ? up.getDailyAiUsed() : 0;
            }
            purchasedRemaining = up.getPurchasedAiTokens() != null ? up.getPurchasedAiTokens() : 0;
        }

        // PLATINUM(-1)이면 dailyLimit 검사 스킵 (무제한)
        boolean gradeUnlimited = (quota.dailyLimit() == -1);
        if (gradeUnlimited || dailyUsed < quota.dailyLimit()) {
            // GRADE_FREE: 일일 무료 한도 내 사용 가능
            // 구독/구매 토큰 잔여 횟수도 함께 조회하여 클라이언트에 현황 전달
            int subBonus = getSubBonusRemaining(userId, today);
            log.debug("쿼터 확인 완료 - GRADE_FREE: userId={}, dailyUsed={}, dailyLimit={}",
                    userId, dailyUsed, quota.dailyLimit());
            return new QuotaCheckResult(
                    true,
                    "GRADE_FREE",
                    dailyUsed,
                    quota.dailyLimit(),
                    subBonus,
                    purchasedRemaining,
                    quota.maxInputLength(),
                    ""
            );
        }

        log.debug("grade 일일 한도 소진: userId={}, dailyUsed={}, dailyLimit={} → 구독 보너스 확인",
                userId, dailyUsed, quota.dailyLimit());

        // ── 소스 2: SUB_BONUS ─────────────────────────────────────────────
        // 활성 구독 조회 → 월 리셋 적용 → 보너스 1회 차감.
        // plan 정보 필요(monthlyAiBonus)이므로 JOIN FETCH 사용.
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
                log.debug("쿼터 확인 완료 - SUB_BONUS: userId={}, remainingAfter={}", userId, remaining);
                return new QuotaCheckResult(
                        true,
                        "SUB_BONUS",
                        dailyUsed,
                        quota.dailyLimit(),
                        remaining,             // 차감 후 잔여
                        purchasedRemaining,
                        quota.maxInputLength(),
                        ""
                );
            }
            // 구독은 있으나 이번 달 보너스 소진 → 소스 3으로
            log.debug("구독 보너스 소진: userId={} → 구매 토큰 확인", userId);
        }

        // ── 소스 3: PURCHASED ─────────────────────────────────────────────
        // user_points.purchased_ai_tokens에서 1회 차감.
        if (userPointOpt.isPresent() && purchasedRemaining > 0) {
            UserPoint up = userPointOpt.get();
            boolean consumed = up.consumePurchasedToken(); // 음수 방지 내장
            if (consumed) {
                // JPA dirty checking으로 purchased_ai_tokens 감소 자동 반영
                int subBonus = subOpt.isPresent() ? subOpt.get().getRemainingAiBonus() : -1;
                log.debug("쿼터 확인 완료 - PURCHASED: userId={}, purchasedAfter={}",
                        userId, up.getPurchasedAiTokens());
                return new QuotaCheckResult(
                        true,
                        "PURCHASED",
                        dailyUsed,
                        quota.dailyLimit(),
                        subBonus,
                        up.getPurchasedAiTokens(), // 차감 후 잔여
                        quota.maxInputLength(),
                        ""
                );
            }
        }

        // ── BLOCKED: 모든 소스 소진 ───────────────────────────────────────
        // 클라이언트가 안내 메시지를 조건별로 커스터마이징할 수 있도록
        // subBonusRemaining 값으로 구독 유무를 판별한다:
        //   -1 → 구독 없음 → 구독 가입 유도
        //    0 → 구독 있으나 소진 → 이용권 구매 또는 등급 업 안내
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
     * @return 최대 입력 글자 수
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
     * <p>소스 1(GRADE_FREE)로 허용될 때 클라이언트에 구독 현황을 알려주기 위해 조회한다.
     * 실제 차감은 하지 않으며, 조회만 수행한다.</p>
     *
     * @param userId 사용자 ID
     * @param today  오늘 날짜 (lazy reset 판정용)
     * @return 구독 보너스 잔여 횟수 (-1이면 구독 없음, 0이면 소진, 양수이면 잔여)
     */
    private int getSubBonusRemaining(String userId, LocalDate today) {
        return userSubscriptionRepository
                .findByUserIdAndStatusFetchPlan(userId, UserSubscription.Status.ACTIVE)
                .map(sub -> {
                    // lazy reset 적용 후 잔여 반환 (조회 전용이므로 저장은 하지 않음)
                    int bonus = sub.getPlan().getMonthlyAiBonus();
                    boolean newMonth = sub.getAiBonusReset() == null
                            || sub.getAiBonusReset().getYear() != today.getYear()
                            || sub.getAiBonusReset().getMonth() != today.getMonth();
                    return newMonth ? bonus : sub.getRemainingAiBonus();
                })
                .orElse(-1); // 구독 없음
    }

    /**
     * BLOCKED 상태의 안내 메시지를 생성한다.
     *
     * <p>구독 유무 및 이용권 보유 여부에 따라 다른 안내 메시지를 생성한다.
     * 클라이언트가 이 메시지를 그대로 표시하거나, source/subBonusRemaining 값으로
     * 자체 메시지를 렌더링할 수 있다.</p>
     *
     * @param subBonusRemaining 구독 보너스 잔여 (-1이면 구독 없음)
     * @param purchasedRemaining 구매 토큰 잔여
     * @param dailyLimit        일일 한도
     * @return 사용자 안내 메시지
     */
    private String buildBlockedMessage(int subBonusRemaining, int purchasedRemaining, int dailyLimit) {
        if (subBonusRemaining == -1) {
            // 구독이 없는 경우 → 구독 가입 유도
            return String.format(
                    "오늘 AI 추천 한도(%d회)를 모두 사용했습니다. "
                            + "구독 서비스에 가입하면 매월 최대 700회의 추가 AI 추천을 이용할 수 있습니다.",
                    dailyLimit
            );
        } else {
            // 구독은 있으나 이번 달 보너스까지 소진
            return String.format(
                    "오늘 AI 추천 한도와 이번 달 구독 보너스를 모두 사용했습니다. "
                            + "포인트 상점에서 AI 이용권을 구매하거나, 내일 다시 이용해주세요."
            );
        }
    }
}
