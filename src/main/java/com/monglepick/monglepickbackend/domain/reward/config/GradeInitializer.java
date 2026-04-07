package com.monglepick.monglepickbackend.domain.reward.config;

import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 등급 마스터 초기 데이터 적재기 (v3.2 — 6등급 팝콘 테마, 엑셀 Table 27 기준).
 *
 * <p>애플리케이션 시작 시 {@code grades} 테이블에 6개 등급 초기 데이터가 없으면 INSERT한다.
 * 이미 존재하는 등급 코드는 건너뛰어 멱등(idempotent) 동작을 보장한다.</p>
 *
 * <h3>v3.2 핵심 변경 사항 (2026-04-03, 엑셀 Table 27 기준)</h3>
 * <ul>
 *   <li><b>6등급 체계</b>: DIAMOND 등급 신규 추가 (earned_by_activity 20,000P 이상)</li>
 *   <li><b>팝콘 테마 한국어명</b>: 알갱이 / 강냉이 / 팝콘 / 카라멜팝콘 / 몽글팝콘 / 몽아일체</li>
 *   <li><b>min_points 전면 재조정</b>: BRONZE 1,000 / SILVER 4,000 / GOLD 6,500 / PLATINUM 10,000 / DIAMOND 20,000</li>
 *   <li><b>daily_ai_limit 재정의</b>: 일일 <b>무료</b> AI 사용 횟수 (NORMAL=3, BRONZE=5, SILVER=7, GOLD=10, PLATINUM=15, DIAMOND=-1 무제한)</li>
 *   <li><b>monthly_ai_limit 재정의</b>: AI 이용권(쿠폰) 월간 사용 한도 (NORMAL=10, BRONZE=30, SILVER=60, GOLD=80, PLATINUM=120, DIAMOND=-1 무제한)</li>
 *   <li><b>free_daily_count 복원</b>: 매일 자동 지급되는 이용권 횟수 (BRONZE=1, SILVER/GOLD=2, PLATINUM/DIAMOND=4)</li>
 *   <li><b>subscription_plan_type 신규</b>: 구독 즉시 보장 등급 연결 (SILVER='basic', PLATINUM='premium')</li>
 *   <li><b>daily_earn_cap 현실화</b>: 실제 earn 패턴 기반 상한 조정 (DIAMOND=0 무제한)</li>
 *   <li><b>reward_multiplier 조정</b>: 최고 배율 2.0→1.5 (DIAMOND), PLATINUM 2.0→1.4</li>
 * </ul>
 *
 * <h3>v3.2 등급 진입 속도 검증 (일반 사용자 기준, SIGNUP_BONUS 200P 포함)</h3>
 * <pre>
 *   활동 포인트 월 획득 시나리오:
 *     - 카주얼 (출석만)        : ~200P/월
 *     - 일반 (출석+리뷰+댓글)  : ~600P/월
 *     - 파워 (매일 풀활동)     : ~2,000P/월
 *
 *   BRONZE  (1,000P): 일반 기준 (1,000 - 200) / 600 = 1.3개월 ✓
 *   SILVER  (4,000P): 일반 기준 (4,000 - 200) / 600 = 6.3개월 ✓
 *   GOLD    (6,500P): 일반 기준 (6,500 - 200) / 600 = 10.5개월 ✓
 *   PLATINUM(10,000P): 일반 기준 (10,000 - 200) / 600 = 16.3개월 ✓
 *   DIAMOND (20,000P): 일반 기준 (20,000 - 200) / 600 = 33개월 (2.7년) — 슈퍼 충성 고객 ✓
 * </pre>
 *
 * <h3>연쇄 승급 방지 검증 (승급 보상 받아도 다음 등급 미도달)</h3>
 * <pre>
 *   BRONZE(1,000P) + GRADE_UP_BRONZE(100P) = 1,100P &lt; SILVER(4,000P)   ✓
 *   SILVER(4,000P) + GRADE_UP_SILVER(200P) = 4,200P &lt; GOLD(6,500P)    ✓
 *   GOLD(6,500P) + GRADE_UP_GOLD(300P)    = 6,800P &lt; PLATINUM(10,000P) ✓
 *   PLATINUM(10,000P) + GRADE_UP_PLATINUM(500P) = 10,500P &lt; DIAMOND(20,000P) ✓
 * </pre>
 *
 * <h3>AI 이용 3-소스 모델 (v3.2)</h3>
 * <pre>
 *   1. GRADE_FREE  : daily_ai_used &lt; grade.daily_ai_limit → daily_ai_used++ (일일 무료)
 *   2. SUB_BONUS   : 활성 구독 remaining_ai_bonus &gt; 0 → remaining_ai_bonus-- (구독 보너스)
 *   3. PURCHASED   : purchased_ai_tokens &gt; 0 AND monthly_coupon_used &lt; grade.monthly_ai_limit
 *                    → purchased_ai_tokens--, monthly_coupon_used++ (이용권)
 *   4. BLOCKED     : 모두 소진 → 등급/구독/이용권 안내
 * </pre>
 *
 * <h3>구독 등급 즉시 보장 (subscription_plan_type)</h3>
 * <pre>
 *   SILVER   (subscription_plan_type='basic')   : basic 구독 → 팝콘 등급 즉시 보장
 *   PLATINUM (subscription_plan_type='premium') : premium 구독 → 몽글팝콘 등급 즉시 보장
 *   effective_grade = max(earned_grade, subscription_guaranteed_grade)
 * </pre>
 *
 * <h3>초기 데이터 요약 (6개 등급 — 엑셀 Table 27 / 설계서 v3.2 §4.5)</h3>
 * <table border="1">
 *   <tr>
 *     <th>코드</th><th>한글명</th><th>최소P</th><th>일일무료</th><th>쿠폰월한도</th>
 *     <th>무료/일</th><th>최대입력</th><th>배율</th><th>일일상한</th><th>정렬</th><th>구독보장</th>
 *   </tr>
 *   <tr><td>NORMAL  </td><td>알갱이    </td><td>0      </td><td>3  </td><td>10 </td><td>0</td><td>200  </td><td>1.00</td><td>45 </td><td>1</td><td>-      </td></tr>
 *   <tr><td>BRONZE  </td><td>강냉이    </td><td>1,000  </td><td>5  </td><td>30 </td><td>1</td><td>400  </td><td>1.10</td><td>100</td><td>2</td><td>-      </td></tr>
 *   <tr><td>SILVER  </td><td>팝콘      </td><td>4,000  </td><td>7  </td><td>60 </td><td>2</td><td>500  </td><td>1.20</td><td>150</td><td>3</td><td>basic  </td></tr>
 *   <tr><td>GOLD    </td><td>카라멜팝콘</td><td>6,500  </td><td>10 </td><td>80 </td><td>2</td><td>800  </td><td>1.30</td><td>250</td><td>4</td><td>-      </td></tr>
 *   <tr><td>PLATINUM</td><td>몽글팝콘  </td><td>10,000 </td><td>15 </td><td>120</td><td>4</td><td>3,000</td><td>1.40</td><td>500</td><td>5</td><td>premium</td></tr>
 *   <tr><td>DIAMOND </td><td>몽아일체  </td><td>20,000 </td><td>-1 </td><td>-1 </td><td>4</td><td>-1   </td><td>1.50</td><td>0  </td><td>6</td><td>-      </td></tr>
 * </table>
 *
 * <h3>-1 / 0 특수값 의미</h3>
 * <ul>
 *   <li>{@code dailyAiLimit = -1}   : 일일 무료 AI 무제한 (DIAMOND)</li>
 *   <li>{@code monthlyAiLimit = -1} : AI 이용권 월간 한도 무제한 (DIAMOND)</li>
 *   <li>{@code maxInputLength = -1} : 입력 글자 수 무제한 (DIAMOND)</li>
 *   <li>{@code dailyEarnCap = 0}    : 일일 활동 리워드 상한 없음(무제한) (DIAMOND)</li>
 * </ul>
 *
 * <h3>실행 시점</h3>
 * <p>{@link ApplicationRunner}를 구현하므로 Spring 컨텍스트가 완전히 로드된 후
 * {@code run()} 메서드가 호출된다. {@code @PostConstruct}보다 늦게 실행되어
 * JPA EntityManager가 완전히 초기화된 상태에서 안전하게 동작한다.</p>
 *
 * <h3>멱등 전략</h3>
 * <p>각 등급 코드마다 {@link GradeRepository#findByGradeCode(String)}로 존재 여부를
 * 확인한 뒤 없는 경우에만 INSERT한다. ddl-auto=update 환경에서 재시작 시마다
 * 중복 INSERT 없이 안전하게 초기화된다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31 v1.0: 신규 생성 (5등급 초안)</li>
 *   <li>2026-04-02 v3.0: 3-소스 AI 모델 전환, free_daily_count 제거</li>
 *   <li>2026-04-02 v3.1: monthly_ai_limit 복원, 4-단계 AI 모델, daily_earn_cap 현실화</li>
 *   <li>2026-04-03 v3.2: 엑셀 Table 27 기준 전면 재설계.
 *       6등급(DIAMOND 신규), 팝콘 테마 한국어명, min_points 전면 재조정,
 *       daily_ai_limit=일일무료/monthly_ai_limit=쿠폰월한도 역할 명확 분리,
 *       free_daily_count 복원, subscription_plan_type 신규 추가,
 *       reward_multiplier 최대 1.5로 조정</li>
 * </ul>
 *
 * @see Grade
 * @see GradeRepository
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GradeInitializer implements ApplicationRunner {

    /** 등급 마스터 리포지토리 — grades 테이블 접근 */
    private final GradeRepository gradeRepository;

    /**
     * 애플리케이션 시작 시 등급 마스터 초기 데이터를 적재한다.
     *
     * <p>6개 등급(NORMAL, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND) 코드를 순서대로 확인하여
     * 존재하지 않는 경우에만 INSERT한다.
     * 이미 존재하는 등급 코드는 건너뜀으로써 멱등성을 보장한다.</p>
     *
     * <p>v3.2에서 기존 5등급 환경에서 재기동하면 DIAMOND 등급만 신규 INSERT되고
     * 나머지 5개는 건너뜀. 단, 기존 등급의 수치값(min_points 등)은 이 초기화기로
     * 변경되지 않으므로 필요 시 DB 직접 UPDATE 또는 별도 마이그레이션 필요.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("등급 마스터 초기화 시작 — grades 테이블 v3.2 기준 (6등급 팝콘 테마, 일일/쿠폰 한도 분리)");

        List<Grade> defaultGrades = buildDefaultGrades();

        int insertedCount = 0;
        int skippedCount = 0;

        for (Grade grade : defaultGrades) {
            // gradeCode 기준 멱등 확인: 이미 존재하면 건너뜀
            if (gradeRepository.findByGradeCode(grade.getGradeCode()).isPresent()) {
                log.debug("등급 이미 존재 (건너뜀): gradeCode={}, gradeName={}",
                        grade.getGradeCode(), grade.getGradeName());
                skippedCount++;
                continue;
            }

            gradeRepository.save(grade);
            insertedCount++;

            log.info(
                    "등급 INSERT: gradeCode={}, gradeName={}, minPoints={}, " +
                    "dailyAiLimit={}, monthlyAiLimit={}, freeDailyCount={}, " +
                    "maxInputLength={}, rewardMultiplier={}, dailyEarnCap={}, " +
                    "sortOrder={}, subscriptionPlanType={}",
                    grade.getGradeCode(), grade.getGradeName(), grade.getMinPoints(),
                    grade.getDailyAiLimit(), grade.getMonthlyAiLimit(), grade.getFreeDailyCount(),
                    grade.getMaxInputLength(), grade.getRewardMultiplier(), grade.getDailyEarnCap(),
                    grade.getSortOrder(), grade.getSubscriptionPlanType()
            );
        }

        if (insertedCount == 0) {
            log.info("등급 마스터 초기화 완료 — 모든 등급이 이미 존재함 (INSERT 없음, SKIP={})", skippedCount);
        } else {
            log.info("등급 마스터 초기화 완료 — INSERT={}, SKIP={}", insertedCount, skippedCount);
        }
    }

    /**
     * v3.2 기본 등급 6개를 Grade 엔티티 리스트로 생성한다.
     *
     * <p>엑셀 Table 27 / 설계서 v3.2 §4.5 시드 데이터 기준.</p>
     *
     * <p><b>등급 기준 포인트</b>: {@code user_points.earned_by_activity}
     * (순수 활동 포인트만 집계 — 결제 충전 포인트 제외, 구매→환불 반복 악용 방지)</p>
     *
     * <p><b>특수값 규칙</b>:
     * <ul>
     *   <li>dailyAiLimit / monthlyAiLimit / maxInputLength = -1 → 무제한 (DIAMOND)</li>
     *   <li>dailyEarnCap = 0 → 일일 리워드 상한 없음(무제한) (DIAMOND)</li>
     * </ul>
     * </p>
     *
     * @return 초기화할 Grade 엔티티 목록 (NORMAL → BRONZE → SILVER → GOLD → PLATINUM → DIAMOND 순서)
     */
    private List<Grade> buildDefaultGrades() {
        return List.of(

                // ────────────────────────────────────────────────────
                // NORMAL — 알갱이 (기본 등급, 가입 시 초기 등급)
                //   earned_by_activity 범위: 0 ~ 999P
                //   SIGNUP_BONUS=200P 부여 시 0+200=200P → BRONZE(1,000P) 미달 → NORMAL 유지 ✓
                //   일일 무료 AI 3회 / 이용권 월한도 10회 / 입력 200자
                // ────────────────────────────────────────────────────
                Grade.builder()
                        .gradeCode("NORMAL")
                        .gradeName("알갱이")                          // 팝콘 테마 한국어명 v3.2
                        .minPoints(0)                                 // 가입 직후 기본 등급
                        .dailyAiLimit(3)                              // 일일 무료 AI 3회 (이 횟수 소진 후 구독/이용권 전환)
                        .monthlyAiLimit(10)                           // 구매 AI 이용권(쿠폰) 월 사용 한도 10회
                        .freeDailyCount(0)                            // 매일 자동 지급 이용권 0회 (NORMAL은 무료 지급 없음)
                        .maxInputLength(200)                          // 최대 입력 200자
                        .rewardMultiplier(new BigDecimal("1.00"))     // 리워드 배율 ×1.0 (기본)
                        .dailyEarnCap(45)                             // 일일 활동 리워드 상한 45P
                        .sortOrder(1)                                 // 1-indexed 정렬 순서
                        .subscriptionPlanType(null)                   // 구독으로 즉시 보장되는 등급 아님
                        .isActive(true)
                        .build(),

                // ────────────────────────────────────────────────────
                // BRONZE — 강냉이 (earned_by_activity 1,000 ~ 3,999P)
                //   일반 사용자 기준 약 1.3개월 소요 (월 600P 기준)
                //   승급 보상: GRADE_UP_BRONZE(100P) → 1,100P < SILVER(4,000P) 연쇄 없음 ✓
                //   매일 AI 이용권 1회 자동 지급 (freeDailyCount=1)
                // ────────────────────────────────────────────────────
                Grade.builder()
                        .gradeCode("BRONZE")
                        .gradeName("강냉이")                          // 팝콘 테마 한국어명 v3.2
                        .minPoints(1_000)                             // v3.2: 2,000 → 1,000P (진입 장벽 현실화)
                        .dailyAiLimit(5)                              // 일일 무료 AI 5회 (NORMAL 대비 +2회)
                        .monthlyAiLimit(30)                           // 구매 AI 이용권 월 한도 30회
                        .freeDailyCount(1)                            // 매일 자동 지급 이용권 1회 (하루 1P 무료 AI 적립 개념)
                        .maxInputLength(400)                          // 최대 입력 400자
                        .rewardMultiplier(new BigDecimal("1.10"))     // 리워드 배율 ×1.1
                        .dailyEarnCap(100)                            // 일일 활동 리워드 상한 100P
                        .sortOrder(2)
                        .subscriptionPlanType(null)                   // 구독으로 즉시 보장되는 등급 아님
                        .isActive(true)
                        .build(),

                // ────────────────────────────────────────────────────
                // SILVER — 팝콘 (earned_by_activity 4,000 ~ 6,499P)
                //   일반 사용자 기준 약 6.3개월 소요 (월 600P 기준)
                //   승급 보상: GRADE_UP_SILVER(200P) → 4,200P < GOLD(6,500P) 연쇄 없음 ✓
                //   구독 basic 플랜 가입 시 즉시 팝콘 등급 보장 (subscription_plan_type='basic')
                //   매일 AI 이용권 2회 자동 지급 (freeDailyCount=2)
                // ────────────────────────────────────────────────────
                Grade.builder()
                        .gradeCode("SILVER")
                        .gradeName("팝콘")                            // 팝콘 테마 한국어명 v3.2
                        .minPoints(4_000)                             // v3.2: 8,000 → 4,000P 하향
                        .dailyAiLimit(7)                              // 일일 무료 AI 7회
                        .monthlyAiLimit(60)                           // 구매 AI 이용권 월 한도 60회
                        .freeDailyCount(2)                            // 매일 자동 지급 이용권 2회
                        .maxInputLength(500)                          // 최대 입력 500자
                        .rewardMultiplier(new BigDecimal("1.20"))     // 리워드 배율 ×1.2 (v3.2: 1.30 → 1.20 하향)
                        .dailyEarnCap(150)                            // 일일 활동 리워드 상한 150P
                        .sortOrder(3)
                        .subscriptionPlanType("basic")                // basic 구독 시 이 등급 즉시 보장
                        .isActive(true)
                        .build(),

                // ────────────────────────────────────────────────────
                // GOLD — 카라멜팝콘 (earned_by_activity 6,500 ~ 9,999P)
                //   일반 사용자 기준 약 10.5개월 소요 (월 600P 기준)
                //   승급 보상: GRADE_UP_GOLD(300P) → 6,800P < PLATINUM(10,000P) 연쇄 없음 ✓
                //   매일 AI 이용권 2회 자동 지급 (freeDailyCount=2, SILVER와 동일)
                // ────────────────────────────────────────────────────
                Grade.builder()
                        .gradeCode("GOLD")
                        .gradeName("카라멜팝콘")                      // 팝콘 테마 한국어명 v3.2
                        .minPoints(6_500)                             // v3.2: 20,000 → 6,500P 하향
                        .dailyAiLimit(10)                             // 일일 무료 AI 10회
                        .monthlyAiLimit(80)                           // 구매 AI 이용권 월 한도 80회
                        .freeDailyCount(2)                            // 매일 자동 지급 이용권 2회
                        .maxInputLength(800)                          // 최대 입력 800자
                        .rewardMultiplier(new BigDecimal("1.30"))     // 리워드 배율 ×1.3 (v3.2: 1.50 → 1.30 하향)
                        .dailyEarnCap(250)                            // 일일 활동 리워드 상한 250P
                        .sortOrder(4)
                        .subscriptionPlanType(null)                   // 구독으로 즉시 보장되는 등급 아님
                        .isActive(true)
                        .build(),

                // ────────────────────────────────────────────────────
                // PLATINUM — 몽글팝콘 (earned_by_activity 10,000 ~ 19,999P)
                //   일반 사용자 기준 약 16.3개월 소요 (월 600P 기준) ← v3.2 목표치 ✓
                //   파워 사용자(월 2,000P) 기준 약 4.9개월 — 충성 고객 도달 가능 ✓
                //   구독 premium 플랜 가입 시 즉시 몽글팝콘 등급 보장 (subscription_plan_type='premium')
                //   매일 AI 이용권 4회 자동 지급 (freeDailyCount=4)
                //   승급 보상: GRADE_UP_PLATINUM(500P) → 10,500P < DIAMOND(20,000P) 연쇄 없음 ✓
                // ────────────────────────────────────────────────────
                Grade.builder()
                        .gradeCode("PLATINUM")
                        .gradeName("몽글팝콘")                        // 팝콘 테마 한국어명 v3.2
                        .minPoints(10_000)                            // v3.2: 100,000 → 10,000P 대폭 하향 (16개월 목표)
                        .dailyAiLimit(15)                             // 일일 무료 AI 15회
                        .monthlyAiLimit(120)                          // 구매 AI 이용권 월 한도 120회
                        .freeDailyCount(4)                            // 매일 자동 지급 이용권 4회
                        .maxInputLength(3_000)                        // 최대 입력 3,000자 (v3.2: 2,000 → 3,000자 상향)
                        .rewardMultiplier(new BigDecimal("1.40"))     // 배율 ×1.4 (v3.2: 2.00 → 1.40 대폭 하향, 인플레이션 방지)
                        .dailyEarnCap(500)                            // 일일 활동 리워드 상한 500P (v3.2: 2,000 → 500P 하향)
                        .sortOrder(5)
                        .subscriptionPlanType("premium")              // premium 구독 시 이 등급 즉시 보장
                        .isActive(true)
                        .build(),

                // ────────────────────────────────────────────────────
                // DIAMOND — 몽아일체 (earned_by_activity 20,000P 이상, v3.2 신규 등급)
                //   일반 사용자 기준 약 33개월 (2.7년) 소요 — 슈퍼 충성 고객 전용
                //   파워 사용자(월 2,000P) 기준 약 9.9개월
                //   일일 무료 AI 무제한 / 이용권 월한도 무제한 / 입력 글자 무제한
                //   일일 활동 리워드 상한 없음(무제한) — dailyEarnCap=0
                //   매일 AI 이용권 4회 자동 지급 (PLATINUM과 동일)
                //   구독으로 즉시 보장되는 등급 아님 (오직 활동 포인트로만 달성 가능)
                // ────────────────────────────────────────────────────
                Grade.builder()
                        .gradeCode("DIAMOND")
                        .gradeName("몽아일체")                        // 팝콘 테마 한국어명 v3.2 (신규)
                        .minPoints(20_000)                            // v3.2 신규: 최고 등급 진입 기준
                        .dailyAiLimit(-1)                             // -1 = 일일 무료 AI 무제한
                        .monthlyAiLimit(-1)                           // -1 = AI 이용권 월간 한도 무제한
                        .freeDailyCount(4)                            // 매일 자동 지급 이용권 4회 (PLATINUM과 동일)
                        .maxInputLength(-1)                           // -1 = 입력 글자 수 무제한
                        .rewardMultiplier(new BigDecimal("1.50"))     // 배율 ×1.5 (최고 등급 보상)
                        .dailyEarnCap(0)                              // 0 = 일일 활동 리워드 상한 없음(무제한)
                        .sortOrder(6)
                        .subscriptionPlanType(null)                   // 구독으로 보장 불가 — 오직 활동 포인트만
                        .isActive(true)
                        .build()
        );
    }
}
