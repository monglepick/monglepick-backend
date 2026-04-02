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
     * v3.0 기본 구독 상품 4개를 SubscriptionPlan 엔티티 리스트로 생성한다.
     *
     * <p>설계서 v3.0 §4.9 시드 데이터 기준. monthly_ai_bonus 필드 포함.
     * 포인트 대량 지급 방식(구 3,000P~100,000P) 폐지, AI 쿼터 직접 부여로 전환.</p>
     *
     * @return 초기화할 SubscriptionPlan 엔티티 목록 (가격 오름차순)
     */
    private List<SubscriptionPlan> buildDefaultPlans() {
        return List.of(

                // ── 월간 Basic ────────────────────────────────────────────
                // 가격: 4,900원 / AI 보너스: 월 100회 / 포인트: 200P/월
                // NORMAL(3/일=90회/월) + 구독(100회) ≈ 190회/월 AI 가능
                // 단가: 4,900원 ÷ 100회 = 49원/회 (vs AI 이용권 160원/회)
                SubscriptionPlan.builder()
                        .planCode("monthly_basic")
                        .name("월간 Basic")
                        .periodType(PeriodType.MONTHLY)
                        .price(4_900)                   // 4,900원/월
                        .monthlyAiBonus(100)            // 매월 AI 보너스 100회 지급
                        .pointsPerPeriod(200)           // 매월 보너스 포인트 200P 지급
                        .description("월간 Basic 구독 — 매월 AI 추천 100회 보너스 + 200P 지급. 일반 사용자의 AI 한도를 약 3배로 확장.")
                        .isActive(true)
                        .build(),

                // ── 월간 Premium ──────────────────────────────────────────
                // 가격: 9,900원 / AI 보너스: 월 500회 / 포인트: 500P/월
                // NORMAL(3/일=90회/월) + 구독(500회) ≈ 590회/월 AI 가능
                // 단가: 9,900원 ÷ 500회 = 19.8원/회 (볼륨 할인 ✓)
                SubscriptionPlan.builder()
                        .planCode("monthly_premium")
                        .name("월간 Premium")
                        .periodType(PeriodType.MONTHLY)
                        .price(9_900)                   // 9,900원/월
                        .monthlyAiBonus(500)            // 매월 AI 보너스 500회 지급
                        .pointsPerPeriod(500)           // 매월 보너스 포인트 500P 지급
                        .description("월간 Premium 구독 — 매월 AI 추천 500회 보너스 + 500P 지급. AI를 집중 활용하는 영화 마니아를 위한 플랜.")
                        .isActive(true)
                        .build(),

                // ── 연간 Basic ────────────────────────────────────────────
                // 가격: 49,000원/년 (monthly_basic×12=58,800원 대비 약 17% 할인)
                // AI 보너스: 월 150회 (monthly_basic 100회보다 +50회 혜택)
                // 포인트: 월 300P (연간 총 3,600P 지급)
                // 단가: 49,000원 ÷ (150회×12월=1,800회) = 27.2원/회
                SubscriptionPlan.builder()
                        .planCode("yearly_basic")
                        .name("연간 Basic")
                        .periodType(PeriodType.YEARLY)
                        .price(49_000)                  // 49,000원/년
                        .monthlyAiBonus(150)            // 매월 AI 보너스 150회 지급 (연간 1,800회)
                        .pointsPerPeriod(300)           // 매월 보너스 포인트 300P (연간 3,600P)
                        .description("연간 Basic 구독 — 월 AI 추천 150회 보너스 + 300P/월 지급. monthly_basic 대비 약 17% 할인 + 월 50회 추가 혜택.")
                        .isActive(true)
                        .build(),

                // ── 연간 Premium ──────────────────────────────────────────
                // 가격: 99,000원/년 (monthly_premium×12=118,800원 대비 약 17% 할인)
                // AI 보너스: 월 700회 (monthly_premium 500회보다 +200회 혜택)
                // 포인트: 월 800P (연간 총 9,600P 지급)
                // 단가: 99,000원 ÷ (700회×12월=8,400회) = 11.8원/회 (최저가 ✓)
                SubscriptionPlan.builder()
                        .planCode("yearly_premium")
                        .name("연간 Premium")
                        .periodType(PeriodType.YEARLY)
                        .price(99_000)                  // 99,000원/년
                        .monthlyAiBonus(700)            // 매월 AI 보너스 700회 지급 (연간 8,400회)
                        .pointsPerPeriod(800)           // 매월 보너스 포인트 800P (연간 9,600P)
                        .description("연간 Premium 구독 — 월 AI 추천 700회 보너스 + 800P/월 지급. 최고 혜택 플랜, monthly_premium 대비 약 17% 할인 + 월 200회 추가.")
                        .isActive(true)
                        .build()
        );
    }
}
