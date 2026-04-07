package com.monglepick.monglepickbackend.domain.payment.config;

import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan.PeriodType;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 구독 상품 초기 데이터 적재기 — subscription_plans 테이블 시드 데이터 삽입.
 *
 * <p>애플리케이션 시작 시 {@code subscription_plans} 테이블에 4개 구독 상품이 없으면 INSERT한다.
 * 이미 존재하는 상품(planCode 기준)은 건너뛰어 멱등(idempotent) 동작을 보장한다.</p>
 *
 * <h3>v3.0 AI 3-소스 모델 — 구독 상품 설계 원칙</h3>
 * <p>구독은 포인트 대량 지급 방식이 아닌 {@code monthly_ai_bonus} 를 통해
 * AI 쿼터를 직접 증가시킨다. 이로써 구독 가치가 명확해진다.</p>
 * <ul>
 *   <li>구독 가입 → {@code UserSubscription.initAiBonus(monthlyAiBonus)} 호출</li>
 *   <li>매월 갱신 → {@code UserSubscription.resetAiBonusIfNeeded(today, monthlyAiBonus)} 호출 (lazy reset)</li>
 *   <li>AI 요청 시 grade 일일 한도 초과 → {@code UserSubscription.consumeAiBonus()} 호출</li>
 * </ul>
 *
 * <h3>v3.0 시드 데이터 (4개 상품)</h3>
 * <table border="1">
 *   <tr><th>planCode</th><th>이름</th><th>주기</th><th>가격</th><th>AI보너스/월</th><th>포인트/주기</th><th>원/AI회</th></tr>
 *   <tr><td>monthly_basic</td><td>월간 Basic</td><td>MONTHLY</td><td>4,900원</td><td>100회</td><td>200P</td><td>49원</td></tr>
 *   <tr><td>monthly_premium</td><td>월간 Premium</td><td>MONTHLY</td><td>9,900원</td><td>500회</td><td>500P</td><td>19.8원</td></tr>
 *   <tr><td>yearly_basic</td><td>연간 Basic</td><td>YEARLY</td><td>49,000원</td><td>150회/월</td><td>300P/월</td><td>27.2원</td></tr>
 *   <tr><td>yearly_premium</td><td>연간 Premium</td><td>YEARLY</td><td>99,000원</td><td>700회/월</td><td>800P/월</td><td>11.8원</td></tr>
 * </table>
 *
 * <h3>구독 vs AI 이용권 구매 가격 비교 (구독 유도 설계)</h3>
 * <ul>
 *   <li>AI 이용권 5회(80P=800원) → 160원/회 — 구독보다 3배 이상 비쌈</li>
 *   <li>monthly_basic → 49원/회 — 이용권 대비 70% 저렴</li>
 *   <li>monthly_premium → 19.8원/회 — 이용권 대비 88% 저렴</li>
 *   <li>→ 포인트 구매 AI 이용권은 구독 유도 수단, 구독이 AI 이용의 주요 경로</li>
 * </ul>
 *
 * <h3>연간 할인율</h3>
 * <ul>
 *   <li>yearly_basic: monthly_basic×12=58,800원 대비 49,000원 → 약 17% 할인</li>
 *   <li>yearly_premium: monthly_premium×12=118,800원 대비 99,000원 → 약 17% 할인</li>
 * </ul>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(3)} 지정 — GradeInitializer(기본순서), RewardPolicyInitializer(@Order(2)) 이후 실행.</p>
 *
 * <h3>멱등 전략</h3>
 * <p>각 planCode에 대해 {@link SubscriptionPlanRepository#findByPlanCode(String)}로 존재 여부 확인 후
 * 없는 경우에만 INSERT한다. ddl-auto=update 환경에서 재시작 시마다 안전하게 동작한다.</p>
 *
 * @see SubscriptionPlan 구독 상품 엔티티
 * @see SubscriptionPlanRepository
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3) // GradeInitializer, RewardPolicyInitializer 이후 실행
public class SubscriptionPlanInitializer implements ApplicationRunner {

    /** 구독 상품 리포지토리 — subscription_plans 테이블 접근 */
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    /**
     * 애플리케이션 시작 시 구독 상품 초기 데이터를 적재한다.
     *
     * <p>4개 구독 상품을 planCode 기준으로 확인하여 없는 경우에만 INSERT한다.
     * 이미 존재하는 상품은 건너뛰어 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("구독 상품 초기화 시작 — subscription_plans 테이블 시드 데이터 확인 (v3.0)");

        List<SubscriptionPlan> plans = buildDefaultPlans();

        int insertedCount = 0;
        int skippedCount = 0;

        for (SubscriptionPlan plan : plans) {
            // planCode 기준 존재 여부 확인 (멱등)
            if (subscriptionPlanRepository.findByPlanCode(plan.getPlanCode()).isPresent()) {
                log.debug("구독 상품 이미 존재 (건너뜀): planCode={}", plan.getPlanCode());
                skippedCount++;
                continue;
            }
            subscriptionPlanRepository.save(plan);
            insertedCount++;
            log.info("구독 상품 INSERT: planCode={}, name={}, price={}, monthlyAiBonus={}, pointsPerPeriod={}",
                    plan.getPlanCode(), plan.getName(), plan.getPrice(),
                    plan.getMonthlyAiBonus(), plan.getPointsPerPeriod());
        }

        if (insertedCount == 0) {
            log.info("구독 상품 초기화 완료 — 모든 상품이 이미 존재함 (INSERT 없음, 건너뜀={}개)", skippedCount);
        } else {
            log.info("구독 상품 초기화 완료 — {}개 상품 INSERT 완료, {}개 건너뜀", insertedCount, skippedCount);
        }
    }

    /**
     * v3.2 기본 구독 상품 4개를 SubscriptionPlan 엔티티 리스트로 생성한다.
     *
     * <p>엑셀 DB설계 v4_t2_09_15 Table 49 (subscription_plans) 기준으로 전면 재조정.
     * 가격 인하 + AI 보너스 현실화로 수지타산 개선. 1P=10원 포인트팩과 환산 일관성 유지.</p>
     *
     * <h4>v3.2 수지 구조 (엑셀 Table 49 기준)</h4>
     * <ul>
     *   <li>monthly_basic  : 2,900원 / AI 30회/월 / 300P → 단가 96.7원/회</li>
     *   <li>monthly_premium: 5,900원 / AI 60회/월 / 600P → 단가 98.3원/회</li>
     *   <li>yearly_basic   : 29,000원/년 / AI 34회/월(연400회) / 340P/월 → 단가 71.1원/회</li>
     *   <li>yearly_premium : 59,000원/년 / AI 67회/월(연800회) / 670P/월 → 단가 73.4원/회</li>
     * </ul>
     * <p>AI 이용권(200P=2,000원, 400원/회) 대비 구독이 4배 이상 저렴 → 구독 유도 구조 유지.</p>
     *
     * @return 초기화할 SubscriptionPlan 엔티티 목록 (가격 오름차순)
     */
    private List<SubscriptionPlan> buildDefaultPlans() {
        return List.of(

                // ── 월간 Basic ────────────────────────────────────────────
                // 가격: 2,900원/월 (v3.2: 4,900 → 2,900원 인하, 접근성 강화)
                // AI 보너스: 월 30회 (v3.2: 100 → 30회, 수지 개선)
                // 포인트: 300P/월 (v3.2: 200 → 300P 상향)
                // 단가: 2,900원 ÷ 30회 = 96.7원/회 (vs AI 이용권 400원/회 → 4배 이상 저렴 ✓)
                SubscriptionPlan.builder()
                        .planCode("monthly_basic")
                        .name("월간 Basic")
                        .periodType(PeriodType.MONTHLY)
                        .price(2_900)                   // v3.2: 2,900원/월 (엑셀 Table 49)
                        .monthlyAiBonus(30)             // v3.2: 매월 AI 보너스 30회 (수지 개선)
                        .pointsPerPeriod(300)           // v3.2: 매월 300P 지급 (1P=10원 기준 3,000원 상당)
                        .description("월간 Basic 구독 — 매월 300 포인트 지급 (AI 추천 30회).")
                        .isActive(true)
                        .build(),

                // ── 월간 Premium ──────────────────────────────────────────
                // 가격: 5,900원/월 (v3.2: 9,900 → 5,900원 인하, 접근성 강화)
                // AI 보너스: 월 60회 (v3.2: 500 → 60회, 수지 개선)
                // 포인트: 600P/월 (v3.2: 500 → 600P 상향)
                // 단가: 5,900원 ÷ 60회 = 98.3원/회 (vs AI 이용권 400원/회 → 4배 이상 저렴 ✓)
                SubscriptionPlan.builder()
                        .planCode("monthly_premium")
                        .name("월간 Premium")
                        .periodType(PeriodType.MONTHLY)
                        .price(5_900)                   // v3.2: 5,900원/월 (엑셀 Table 49)
                        .monthlyAiBonus(60)             // v3.2: 매월 AI 보너스 60회 (수지 개선)
                        .pointsPerPeriod(600)           // v3.2: 매월 600P 지급 (1P=10원 기준 6,000원 상당)
                        .description("월간 Premium 구독 — 매월 600 포인트 지급 (AI 추천 60회).")
                        .isActive(true)
                        .build(),

                // ── 연간 Basic ────────────────────────────────────────────
                // 가격: 29,000원/년 (v3.2: 49,000 → 29,000원 인하)
                // monthly_basic×12=34,800원 대비 약 17% 할인
                // AI 보너스: 월 34회 (연 400회 ÷ 12 ≈ 33.3 → 34회)
                // 포인트: 340P/월 (연간 총 4,080P ≈ "약 4,000 포인트")
                // 단가: 29,000원 ÷ (34회×12=408회) = 71.1원/회
                SubscriptionPlan.builder()
                        .planCode("yearly_basic")
                        .name("연간 Basic")
                        .periodType(PeriodType.YEARLY)
                        .price(29_000)                  // v3.2: 29,000원/년 (엑셀 Table 49)
                        .monthlyAiBonus(34)             // v3.2: 매월 34회 (연 약400회, 34×12=408회)
                        .pointsPerPeriod(340)           // v3.2: 매월 340P (연간 4,080P ≈ "약 4,000포인트")
                        .description("연간 Basic 구독 — 연간 약 4,000 포인트 지급 (AI 추천 약400회). monthly_basic 대비 약 17% 할인.")
                        .isActive(true)
                        .build(),

                // ── 연간 Premium ──────────────────────────────────────────
                // 가격: 59,000원/년 (v3.2: 99,000 → 59,000원 인하)
                // monthly_premium×12=70,800원 대비 약 17% 할인
                // AI 보너스: 월 67회 (연 800회 ÷ 12 ≈ 66.7 → 67회)
                // 포인트: 670P/월 (연간 총 8,040P ≈ "약 8,000 포인트")
                // 단가: 59,000원 ÷ (67회×12=804회) = 73.4원/회
                SubscriptionPlan.builder()
                        .planCode("yearly_premium")
                        .name("연간 Premium")
                        .periodType(PeriodType.YEARLY)
                        .price(59_000)                  // v3.2: 59,000원/년 (엑셀 Table 49)
                        .monthlyAiBonus(67)             // v3.2: 매월 67회 (연 약800회, 67×12=804회)
                        .pointsPerPeriod(670)           // v3.2: 매월 670P (연간 8,040P ≈ "약 8,000포인트")
                        .description("연간 Premium 구독 — 연간 약 8,000 포인트 지급 (AI 추천 약800회). monthly_premium 대비 약 17% 할인.")
                        .isActive(true)
                        .build()
        );
    }
}
