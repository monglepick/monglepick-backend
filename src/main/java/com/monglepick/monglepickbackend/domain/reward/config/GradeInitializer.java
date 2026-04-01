package com.monglepick.monglepickbackend.domain.reward.config;

import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 등급 마스터 초기 데이터 적재기.
 *
 * <p>애플리케이션 시작 시 {@code grades} 테이블에 4개 등급 초기 데이터가 없으면 INSERT한다.
 * 이미 존재하는 등급은 건너뛰어 멱등(idempotent) 동작을 보장한다.</p>
 *
 * <h3>초기 데이터 (4개 등급)</h3>
 * <table border="1">
 *   <tr><th>코드</th><th>한글명</th><th>최소포인트</th><th>일일한도</th><th>월간한도</th><th>무료일일</th><th>최대입력</th><th>정렬</th></tr>
 *   <tr><td>BRONZE</td><td>브론즈</td><td>0</td><td>3</td><td>30</td><td>0</td><td>200</td><td>1</td></tr>
 *   <tr><td>SILVER</td><td>실버</td><td>1000</td><td>10</td><td>200</td><td>2</td><td>500</td><td>2</td></tr>
 *   <tr><td>GOLD</td><td>골드</td><td>5000</td><td>30</td><td>600</td><td>5</td><td>1000</td><td>3</td></tr>
 *   <tr><td>PLATINUM</td><td>플래티넘</td><td>20000</td><td>-1(무제한)</td><td>-1(무제한)</td><td>10</td><td>2000</td><td>4</td></tr>
 * </table>
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
     * <p>각 등급 코드를 순서대로 확인하여 없는 경우에만 INSERT한다.
     * 이미 존재하는 등급은 건너뜀으로써 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("등급 마스터 초기화 시작 — grades 테이블 초기 데이터 확인");

        // 초기화할 4개 등급 정의 (sortOrder 오름차순)
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
            log.info("등급 초기 데이터 INSERT: gradeCode={}, minPoints={}, dailyAiLimit={}, monthlyAiLimit={}, freeDailyCount={}, maxInputLength={}",
                    grade.getGradeCode(), grade.getMinPoints(),
                    grade.getDailyAiLimit(), grade.getMonthlyAiLimit(),
                    grade.getFreeDailyCount(), grade.getMaxInputLength());
        }

        if (insertedCount == 0) {
            log.info("등급 마스터 초기화 완료 — 모든 등급이 이미 존재함 (INSERT 없음)");
        } else {
            log.info("등급 마스터 초기화 완료 — {}개 등급 INSERT 완료", insertedCount);
        }
    }

    /**
     * 기본 등급 4개를 Grade 엔티티 리스트로 생성한다.
     *
     * <p>이 설계값은 {@code CLAUDE.md} 등급별 쿼터 표 및
     * {@code application.yml}의 {@code app.quota.*} 설정과 동기화되어 있다.</p>
     *
     * @return 초기화할 Grade 엔티티 목록 (BRONZE → SILVER → GOLD → PLATINUM 순서)
     */
    private List<Grade> buildDefaultGrades() {
        return List.of(
                // BRONZE: 기본 등급 (누적 0~999 포인트)
                Grade.builder()
                        .gradeCode("BRONZE")
                        .gradeName("브론즈")
                        .minPoints(0)
                        .dailyAiLimit(3)         // 일일 AI 추천 3회
                        .monthlyAiLimit(30)       // 월간 AI 추천 30회
                        .freeDailyCount(0)        // 무료 일일 횟수 없음
                        .maxInputLength(200)      // 최대 입력 200자
                        .sortOrder(1)
                        .isActive(true)
                        .build(),

                // SILVER: 1,000~4,999 누적 포인트
                Grade.builder()
                        .gradeCode("SILVER")
                        .gradeName("실버")
                        .minPoints(1_000)
                        .dailyAiLimit(10)         // 일일 AI 추천 10회
                        .monthlyAiLimit(200)      // 월간 AI 추천 200회
                        .freeDailyCount(2)        // 무료 일일 2회
                        .maxInputLength(500)      // 최대 입력 500자
                        .sortOrder(2)
                        .isActive(true)
                        .build(),

                // GOLD: 5,000~19,999 누적 포인트
                Grade.builder()
                        .gradeCode("GOLD")
                        .gradeName("골드")
                        .minPoints(5_000)
                        .dailyAiLimit(30)         // 일일 AI 추천 30회
                        .monthlyAiLimit(600)      // 월간 AI 추천 600회
                        .freeDailyCount(5)        // 무료 일일 5회
                        .maxInputLength(1_000)    // 최대 입력 1000자
                        .sortOrder(3)
                        .isActive(true)
                        .build(),

                // PLATINUM: 20,000+ 누적 포인트 (무제한 등급)
                Grade.builder()
                        .gradeCode("PLATINUM")
                        .gradeName("플래티넘")
                        .minPoints(20_000)
                        .dailyAiLimit(-1)         // 일일 무제한 (-1)
                        .monthlyAiLimit(-1)       // 월간 무제한 (-1)
                        .freeDailyCount(10)       // 무료 일일 10회
                        .maxInputLength(2_000)    // 최대 입력 2000자
                        .sortOrder(4)
                        .isActive(true)
                        .build()
        );
    }
}
