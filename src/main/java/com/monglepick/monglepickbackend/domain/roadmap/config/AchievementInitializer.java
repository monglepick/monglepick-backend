package com.monglepick.monglepickbackend.domain.roadmap.config;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.repository.AchievementTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 앱 시작 시 기본 업적 유형 데이터를 자동으로 초기화하는 컴포넌트.
 *
 * <p>{@link ApplicationRunner}를 구현하여 Spring Boot 컨텍스트 완전 로드 이후,
 * DB 트랜잭션이 가능한 시점에 실행된다.</p>
 *
 * <h3>초기화 전략</h3>
 * <ul>
 *   <li>achievement_code UNIQUE 제약을 활용해 코드별로 존재 여부를 확인한다.</li>
 *   <li>이미 존재하는 코드는 INSERT를 건너뛴다 (멱등성 보장).</li>
 *   <li>신규 코드만 INSERT하므로 운영 중 재시작해도 기존 데이터에 영향이 없다.</li>
 * </ul>
 *
 * <h3>기본 업적 유형 4종</h3>
 * <ul>
 *   <li>{@code course_complete}  — 도장깨기 코스 완주 (1회, 100P)</li>
 *   <li>{@code quiz_perfect}     — 퀴즈 만점 달성 (1회, 50P)</li>
 *   <li>{@code review_count_10}  — 리뷰 10개 달성 (10회, 200P)</li>
 *   <li>{@code genre_explorer}   — 5개 장르 탐험 (5회, 150P)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementInitializer implements ApplicationRunner {

    private final AchievementTypeRepository achievementTypeRepository;

    /**
     * 앱 기동 후 기본 업적 유형이 없으면 INSERT한다.
     *
     * <p>각 업적 코드에 대해 DB 조회 후 부재 시에만 저장한다.
     * 트랜잭션 내에서 처리되므로 초기화 도중 예외 발생 시 전체 롤백된다.</p>
     *
     * @param args 앱 실행 인수 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[AchievementInitializer] 기본 업적 유형 초기화 시작");

        /* 초기화할 기본 업적 유형 정의 목록
         * category 값: VIEWING(시청), SOCIAL(소셜/커뮤니티), COLLECTION(수집), CHALLENGE(도전과제)
         * 프론트엔드가 이 값으로 탭 필터링을 수행한다. */
        List<AchievementType> defaults = List.of(

                /* 도장깨기 코스 완주 — 코스 1개를 끝까지 완료했을 때 달성
                 * category=CHALLENGE: 코스 완주는 도전과제 성격의 업적 */
                AchievementType.builder()
                        .achievementCode("course_complete")
                        .achievementName("코스 완주")
                        .description("도장깨기 코스를 완주하면 획득할 수 있습니다. 코스 내 모든 영화를 시청하세요.")
                        .requiredCount(1)
                        .rewardPoints(100)
                        .category("CHALLENGE")
                        .isActive(true)
                        .build(),

                /* 퀴즈 만점 — 코스 내 퀴즈를 오답 없이 완료했을 때 달성
                 * category=CHALLENGE: 퀴즈 만점은 지식 도전과제 업적 */
                AchievementType.builder()
                        .achievementCode("quiz_perfect")
                        .achievementName("퀴즈 만점")
                        .description("도장깨기 코스의 퀴즈에서 만점을 받으면 획득할 수 있습니다.")
                        .requiredCount(1)
                        .rewardPoints(50)
                        .category("CHALLENGE")
                        .isActive(true)
                        .build(),

                /* 리뷰 10개 달성 — 누적 리뷰 작성 수가 10개에 도달했을 때 달성
                 * category=SOCIAL: 리뷰 작성은 커뮤니티/소셜 활동 업적 */
                AchievementType.builder()
                        .achievementCode("review_count_10")
                        .achievementName("리뷰 10개 달성")
                        .description("영화 리뷰를 10개 이상 작성하면 획득할 수 있습니다.")
                        .requiredCount(10)
                        .rewardPoints(200)
                        .category("SOCIAL")
                        .isActive(true)
                        .build(),

                /* 5개 장르 탐험 — 서로 다른 장르 영화를 5개 이상 시청했을 때 달성
                 * category=VIEWING: 다양한 장르 시청은 시청 활동 업적 */
                AchievementType.builder()
                        .achievementCode("genre_explorer")
                        .achievementName("5개 장르 탐험")
                        .description("5가지 이상의 서로 다른 장르 영화를 시청하면 획득할 수 있습니다.")
                        .requiredCount(5)
                        .rewardPoints(150)
                        .category("VIEWING")
                        .isActive(true)
                        .build()
        );

        int insertedCount = 0;

        for (AchievementType type : defaults) {
            /* achievement_code 기준으로 이미 존재하는지 확인 — 존재하면 INSERT 건너뜀 */
            boolean exists = achievementTypeRepository
                    .findByAchievementCode(type.getAchievementCode())
                    .isPresent();

            if (exists) {
                log.debug("[AchievementInitializer] 이미 존재하는 업적 유형 건너뜀: code={}", type.getAchievementCode());
                continue;
            }

            /* 신규 업적 유형 저장 */
            achievementTypeRepository.save(type);
            insertedCount++;
            log.info("[AchievementInitializer] 업적 유형 신규 등록: code={}, name={}, rewardPoints={}",
                    type.getAchievementCode(), type.getAchievementName(), type.getRewardPoints());
        }

        log.info("[AchievementInitializer] 기본 업적 유형 초기화 완료 — 신규 등록 {}건 / 전체 {}건",
                insertedCount, defaults.size());
    }
}
