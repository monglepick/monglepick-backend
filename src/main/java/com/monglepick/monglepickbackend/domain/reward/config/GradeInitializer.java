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
 * 등급 마스터 초기 데이터 적재기.
 *
 * <p>애플리케이션 시작 시 {@code grades} 테이블에 5개 등급 초기 데이터가 없으면 INSERT한다.
 * 이미 존재하는 등급은 건너뛰어 멱등(idempotent) 동작을 보장한다.</p>
 *
 * <h3>v3.1 변경 — AI 4-단계 모델, monthly_ai_limit 복원 (설계서 v3.1 §4.5)</h3>
 * <ul>
 *   <li>복원: monthlyAiLimit — 월간 AI 절대 상한. NORMAL=200, BRONZE=500, SILVER=1000, GOLD=2000, PLATINUM=-1(무제한)</li>
 *   <li>제거 유지: freeDailyCount — daily_ai_limit 내 전부 무료로 통합. DB 컬럼 방치.</li>
 *   <li>min_points: BRONZE 2,000 / SILVER 8,000 / GOLD 20,000 / PLATINUM 50,000 (v3.0 기준 유지)</li>
 *   <li>daily_ai_limit: NORMAL=3, BRONZE=7, SILVER=15, GOLD=30, PLATINUM=-1(무제한)</li>
 *   <li>daily_earn_cap: NORMAL=500, BRONZE=900, SILVER=1,500, GOLD=2,500, PLATINUM=5,000</li>
 * </ul>
 *
 * <h3>v3.0 등급 진입 속도 검증 (일반 사용자 월 600P 기준, SIGNUP_BONUS 200P 포함)</h3>
 * <ul>
 *   <li>BRONZE(2,000P) : (2,000 - 200) / 600 = 3개월 ✓</li>
 *   <li>SILVER(8,000P) : (8,000 - 200) / 600 = 13개월 ✓</li>
 *   <li>GOLD(20,000P)  : (20,000 - 200) / 600 = 33개월 (2.7년) ✓</li>
 *   <li>PLATINUM(50,000P): (50,000 - 200) / 600 = 83개월 (6.9년, 슈퍼 충성 고객) ✓</li>
 * </ul>
 *
 * <h3>연쇄 승급 확인 (승급 보상 받아도 다음 등급 미도달)</h3>
 * <ul>
 *   <li>BRONZE(2,000P) + GRADE_UP_BRONZE(100P) = 2,100P &lt; SILVER(8,000P) ✓</li>
 *   <li>SILVER(8,000P) + GRADE_UP_SILVER(200P) = 8,200P &lt; GOLD(20,000P) ✓</li>
 *   <li>GOLD(20,000P) + GRADE_UP_GOLD(500P) = 20,500P &lt; PLATINUM(50,000P) ✓</li>
 * </ul>
 *
 * <h3>초기 데이터 (5개 등급 — 설계서 v3.1 §4.5 기준)</h3>
 * <table border="1">
 *   <tr><th>코드</th><th>한글명</th><th>최소포인트</th><th>일일AI</th><th>월간AI</th><th>최대입력</th><th>배율</th><th>일일상한</th><th>정렬</th></tr>
 *   <tr><td>NORMAL</td><td>일반</td><td>0</td><td>3</td><td>200</td><td>200</td><td>1.00</td><td>500</td><td>0</td></tr>
 *   <tr><td>BRONZE</td><td>브론즈</td><td>2,000</td><td>7</td><td>500</td><td>300</td><td>1.10</td><td>900</td><td>1</td></tr>
 *   <tr><td>SILVER</td><td>실버</td><td>8,000</td><td>15</td><td>1,000</td><td>500</td><td>1.30</td><td>1,500</td><td>2</td></tr>
 *   <tr><td>GOLD</td><td>골드</td><td>20,000</td><td>30</td><td>2,000</td><td>1,000</td><td>1.50</td><td>2,500</td><td>3</td></tr>
 *   <tr><td>PLATINUM</td><td>플래티넘</td><td>50,000</td><td>-1(무제한)</td><td>-1(무제한)</td><td>2,000</td><td>2.00</td><td>5,000</td><td>4</td></tr>
 * </table>
 *
 * <h3>등급 기준: {@code user_points.earned_by_activity} (순수 활동 포인트, 결제 충전 제외)</h3>
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
     * <p>5개 등급(NORMAL, BRONZE, SILVER, GOLD, PLATINUM) 코드를 순서대로 확인하여
     * 없는 경우에만 INSERT한다. 이미 존재하는 등급은 건너뜀으로써 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("등급 마스터 초기화 시작 — grades 테이블 초기 데이터 확인 (v3.1 4-단계 모델, monthly_ai_limit 복원)");

        List<Grade> defaultGrades = buildDefaultGrades();

        int insertedCount = 0;
        for (Grade grade : defaultGrades) {
            // 이미 존재하면 건너뜀 (멱등)
            if (gradeRepository.findByGradeCode(grade.getGradeCode()).isPresent()) {
                log.debug("등급 이미 존재 (건너뜀): gradeCode={}", grade.getGradeCode());
                continue;
            }
            gradeRepository.save(grade);
            insertedCount++;
            log.info("등급 초기 데이터 INSERT: gradeCode={}, minPoints={}, dailyAiLimit={}, monthlyAiLimit={}, maxInputLength={}, rewardMultiplier={}, dailyEarnCap={}",
                    grade.getGradeCode(), grade.getMinPoints(),
                    grade.getDailyAiLimit(), grade.getMonthlyAiLimit(),
                    grade.getMaxInputLength(), grade.getRewardMultiplier(), grade.getDailyEarnCap());
        }

        if (insertedCount == 0) {
            log.info("등급 마스터 초기화 완료 — 모든 등급이 이미 존재함 (INSERT 없음)");
        } else {
            log.info("등급 마스터 초기화 완료 — {}개 등급 INSERT 완료", insertedCount);
        }
    }

    /**
     * v3.1 기본 등급 5개를 Grade 엔티티 리스트로 생성한다.
     *
     * <p>설계서 v3.1 §4.5 시드 데이터 기준. monthly_ai_limit 복원.
     * min_points 기준값은 v3.0 상향값을 유지하여 신규 가입 즉시 등급 상승 문제를 방지한다.</p>
     *
     * <p>등급 기준: {@code user_points.earned_by_activity} (순수 활동 포인트, 결제 충전 제외)</p>
     *
     * @return 초기화할 Grade 엔티티 목록 (NORMAL → BRONZE → SILVER → GOLD → PLATINUM 순서)
     */
    private List<Grade> buildDefaultGrades() {
        return List.of(

                // NORMAL: 기본 등급 (가입 시 초기 등급 — earned_by_activity 0~1,999)
                // SIGNUP_BONUS=200P이므로 가입 직후 200P → BRONZE(2,000P) 미달 → NORMAL 유지 ✓
                Grade.builder()
                        .gradeCode("NORMAL")
                        .gradeName("일반")
                        .minPoints(0)
                        .dailyAiLimit(3)              // 일일 AI 무료 3회 (초과 시 구독/구매 토큰 필요)
                        .monthlyAiLimit(200)          // 월간 AI 절대 상한 200회 (전 소스 합산)
                        .maxInputLength(200)           // 최대 입력 200자
                        .rewardMultiplier(new BigDecimal("1.00"))  // 배율 ×1.0 (기본)
                        .dailyEarnCap(500)             // 일일 활동 리워드 상한 500P
                        .sortOrder(0)
                        .isActive(true)
                        .build(),

                // BRONZE: earned_by_activity 2,000~7,999
                // 월 600P 기준 신규 가입 후 약 3개월 소요 (연쇄 승급 없음 ✓)
                Grade.builder()
                        .gradeCode("BRONZE")
                        .gradeName("브론즈")
                        .minPoints(2_000)
                        .dailyAiLimit(7)               // 일일 AI 무료 7회 (NORMAL 대비 +4회)
                        .monthlyAiLimit(500)          // 월간 AI 절대 상한 500회
                        .maxInputLength(300)           // 최대 입력 300자
                        .rewardMultiplier(new BigDecimal("1.10"))  // 배율 ×1.1
                        .dailyEarnCap(900)             // 일일 활동 리워드 상한 900P
                        .sortOrder(1)
                        .isActive(true)
                        .build(),

                // SILVER: earned_by_activity 8,000~19,999
                // 월 600P 기준 약 13개월 소요
                Grade.builder()
                        .gradeCode("SILVER")
                        .gradeName("실버")
                        .minPoints(8_000)
                        .dailyAiLimit(15)              // 일일 AI 무료 15회
                        .monthlyAiLimit(1_000)        // 월간 AI 절대 상한 1,000회
                        .maxInputLength(500)           // 최대 입력 500자
                        .rewardMultiplier(new BigDecimal("1.30"))  // 배율 ×1.3
                        .dailyEarnCap(1_500)           // 일일 활동 리워드 상한 1,500P
                        .sortOrder(2)
                        .isActive(true)
                        .build(),

                // GOLD: earned_by_activity 20,000~49,999
                // 월 600P 기준 약 33개월 (2.7년) 소요
                Grade.builder()
                        .gradeCode("GOLD")
                        .gradeName("골드")
                        .minPoints(20_000)
                        .dailyAiLimit(30)              // 일일 AI 무료 30회
                        .monthlyAiLimit(2_000)        // 월간 AI 절대 상한 2,000회
                        .maxInputLength(1_000)         // 최대 입력 1,000자
                        .rewardMultiplier(new BigDecimal("1.50"))  // 배율 ×1.5
                        .dailyEarnCap(2_500)           // 일일 활동 리워드 상한 2,500P
                        .sortOrder(3)
                        .isActive(true)
                        .build(),

                // PLATINUM: earned_by_activity 50,000+ (최고 등급, 슈퍼 충성 고객)
                // 월 600P 기준 약 83개월 (6.9년) 소요
                Grade.builder()
                        .gradeCode("PLATINUM")
                        .gradeName("플래티넘")
                        .minPoints(50_000)
                        .dailyAiLimit(-1)              // 일일 무제한 (-1 = 무제한)
                        .monthlyAiLimit(-1)            // 월간 무제한 (-1 = 무제한)
                        .maxInputLength(2_000)         // 최대 입력 2,000자
                        .rewardMultiplier(new BigDecimal("2.00"))  // 배율 ×2.0 (최고)
                        .dailyEarnCap(5_000)           // 일일 활동 리워드 상한 5,000P (어뷰징 방지용)
                        .sortOrder(4)
                        .isActive(true)
                        .build()
        );
    }
}
