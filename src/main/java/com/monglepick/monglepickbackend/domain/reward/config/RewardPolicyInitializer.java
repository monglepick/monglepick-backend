package com.monglepick.monglepickbackend.domain.reward.config;

import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicy;
import com.monglepick.monglepickbackend.domain.reward.repository.RewardPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 리워드 정책 초기 데이터 적재기 — reward_policy 테이블 시드 데이터 삽입.
 *
 * <p>애플리케이션 시작 시 {@code reward_policy} 테이블에 42개 활동 정책이 없으면 INSERT한다.
 * 이미 존재하는 정책(action_type 기준)은 건너뛰어 멱등(idempotent) 동작을 보장한다.</p>
 *
 * <h3>시드 데이터 5개 카테고리 (설계서 v2.4 §5.1 기준)</h3>
 * <ul>
 *   <li><b>A. 반복 활동</b> (8개) — ATTENDANCE/CONTENT/ENGAGEMENT, point_type=earn</li>
 *   <li><b>B. 일회성 마일스톤</b> (6개) — 회원가입 보너스 등, point_type=bonus, max_count=1</li>
 *   <li><b>C-1. 누적 마일스톤</b> (15개) — AI 추천/리뷰/게시글/댓글/코스 달성, threshold_target=TOTAL</li>
 *   <li><b>C-2. 연속 출석</b> (3개) — 7/15/30일 스트릭, threshold_target=STREAK</li>
 *   <li><b>C-3. 일일 달성</b> (4개) — 오늘 N건 달성, threshold_target=DAILY</li>
 *   <li><b>D. 등급 승급 보상</b> (4개) — BRONZE~PLATINUM 승급 시 1회 지급</li>
 *   <li><b>E. 월드컵/업적/퀴즈</b> (4개) — 월드컵 완주, 업적 달성, 퀴즈 정답</li>
 * </ul>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(2)} 지정 — {@link GradeInitializer}(@Order 기본값=Integer.MAX_VALUE)보다
 * 명시적으로 나중에 실행되도록 보장한다. grades 테이블이 먼저 채워진 후 reward_policy가 삽입된다.</p>
 *
 * <h3>멱등 전략</h3>
 * <p>각 정책마다 {@link RewardPolicyRepository#existsByActionType(String)}으로 존재 여부를
 * 확인한 후, 없는 경우에만 INSERT한다. ddl-auto=update 환경에서 재시작 시마다 안전하게 동작한다.</p>
 *
 * <h3>가변 포인트 정책 (pointsAmount=0)</h3>
 * <p>COURSE_COMPLETE, ACHIEVEMENT_UNLOCK 정책은 pointsAmount=0으로 등록한다.
 * 이 두 정책은 RewardService.grantRewardWithAmount()를 통해 호출 시점에 금액을 동적으로 결정한다.</p>
 *
 * @see GradeInitializer
 * @see RewardPolicy
 * @see RewardPolicyRepository
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // GradeInitializer보다 나중에 실행 (grades 테이블 의존 없지만 논리적 순서 보장)
public class RewardPolicyInitializer implements ApplicationRunner {

    /** 리워드 정책 리포지토리 — reward_policy 테이블 접근 */
    private final RewardPolicyRepository rewardPolicyRepository;

    /**
     * 애플리케이션 시작 시 리워드 정책 시드 데이터를 적재한다.
     *
     * <p>5개 카테고리 37개 정책을 순서대로 확인하여 없는 경우에만 INSERT한다.
     * 이미 존재하는 정책은 건너뛰어 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("리워드 정책 초기화 시작 — reward_policy 테이블 시드 데이터 확인");

        // 삽입할 전체 정책 목록 빌드
        List<RewardPolicy> policies = buildAllPolicies();

        int insertedCount = 0;
        int skippedCount = 0;

        for (RewardPolicy policy : policies) {
            // existsByActionType으로 이미 존재하는 정책은 건너뜀 (멱등)
            if (rewardPolicyRepository.existsByActionType(policy.getActionType())) {
                log.debug("리워드 정책 이미 존재 (건너뜀): actionType={}", policy.getActionType());
                skippedCount++;
                continue;
            }
            rewardPolicyRepository.save(policy);
            insertedCount++;
            log.debug("리워드 정책 INSERT: actionType={}, activityName={}, pointsAmount={}, pointType={}",
                    policy.getActionType(), policy.getActivityName(),
                    policy.getPointsAmount(), policy.getPointType());
        }

        if (insertedCount == 0) {
            log.info("리워드 정책 초기화 완료 — 모든 정책이 이미 존재함 (INSERT 없음, 건너뜀={}개)", skippedCount);
        } else {
            log.info("리워드 정책 초기화 완료 — {}개 정책 INSERT 완료, {}개 건너뜀", insertedCount, skippedCount);
        }
    }

    /**
     * 전체 리워드 정책 37개를 RewardPolicy 엔티티 리스트로 생성한다.
     *
     * <p>설계서 v2.4 §5.1 시드 데이터 기준. 카테고리별로 묶어 가독성을 높였으며,
     * 각 빌더 호출에 상세 주석을 달아 의도를 명확히 했다.</p>
     *
     * @return 초기화할 RewardPolicy 엔티티 목록 (총 42개)
     */
    private List<RewardPolicy> buildAllPolicies() {
        return List.of(
                // ─────────────────────────────────────────────────────────────
                // A. 반복 활동 (8개) — point_type=earn, 등급 배율 적용 대상
                //    발생할 때마다 dailyLimit 내에서 반복 지급
                // ─────────────────────────────────────────────────────────────

                // A-1. 출석 기본 — 하루 1회 출석 시 10P 지급
                RewardPolicy.builder()
                        .actionType("ATTENDANCE_BASE")
                        .activityName("출석 기본")
                        .actionCategory("ATTENDANCE")
                        .pointsAmount(10)
                        .pointType("earn")
                        .dailyLimit(1)          // 하루 최대 1회
                        .maxCount(0)            // 평생 무제한
                        .cooldownSeconds(0)     // 쿨다운 없음
                        .minContentLength(0)    // 길이 검사 없음
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("매일 첫 로그인 시 지급. 연속 출석 스트릭 카운터의 기준 활동.")
                        .build(),

                // A-2. 리뷰 작성 — 하루 최대 3회, 10자 이상 리뷰에만 지급
                // v3.1: 20P → 10P (리워드 과다 지급 방지. 구 값: 1회=20P, 일 최대 60P+보너스=90P)
                RewardPolicy.builder()
                        .actionType("REVIEW_CREATE")
                        .activityName("리뷰 작성")
                        .actionCategory("CONTENT")
                        .pointsAmount(10)       // v3.1: 20 → 10P
                        .pointType("earn")
                        .dailyLimit(3)          // 하루 최대 3회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(10)   // 최소 10자 이상 리뷰만 지급
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 리뷰 작성 시 지급. 10자 미만 리뷰는 리워드 미지급. 일일 3건 한도.")
                        .build(),

                // A-3. 게시글 작성 — 하루 최대 5회, 20자 이상만 지급
                // v3.1: 20P → 10P
                RewardPolicy.builder()
                        .actionType("POST_REWARD")
                        .activityName("게시글 작성")
                        .actionCategory("CONTENT")
                        .pointsAmount(10)       // v3.1: 20 → 10P
                        .pointType("earn")
                        .dailyLimit(5)          // 하루 최대 5회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(20)   // 최소 20자 이상
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("커뮤니티 게시글 작성 시 지급. 20자 미만 게시글 제외. 일일 5건 한도.")
                        .build(),

                // A-4. 댓글 작성 — 하루 최대 10회
                // v3.1: 10P → 5P
                RewardPolicy.builder()
                        .actionType("COMMENT_CREATE")
                        .activityName("댓글 작성")
                        .actionCategory("CONTENT")
                        .pointsAmount(5)        // v3.1: 10 → 5P
                        .pointType("earn")
                        .dailyLimit(10)         // 하루 최대 10회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("게시글/리뷰 댓글 작성 시 지급. 일일 10건 한도.")
                        .build(),

                // A-5. 위시리스트 추가 — 하루 최대 10회, 평생 최대 10회
                //      평생 10회 제한으로 과도한 반복 획득 방지
                RewardPolicy.builder()
                        .actionType("WISHLIST_ADD")
                        .activityName("위시리스트 추가")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(5)
                        .pointType("earn")
                        .dailyLimit(10)         // 하루 최대 10회
                        .maxCount(10)           // 평생 최대 10회 (남용 방지)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 위시리스트(찜) 추가 시 지급. 평생 10회, 일일 10회 한도.")
                        .build(),

                // A-6. FAQ 피드백 — 하루 최대 5회
                RewardPolicy.builder()
                        .actionType("FAQ_FEEDBACK")
                        .activityName("FAQ 피드백")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(3)
                        .pointType("earn")
                        .dailyLimit(5)          // 하루 최대 5회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("FAQ 도움됨/안됨 피드백 제출 시 지급. 일일 5건 한도.")
                        .build(),

                // A-7. 상담 티켓 생성 — 하루 최대 2회
                RewardPolicy.builder()
                        .actionType("TICKET_CREATE")
                        .activityName("상담 티켓")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(10)
                        .pointType("earn")
                        .dailyLimit(2)          // 하루 최대 2회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("고객센터 상담 티켓 생성 시 지급. 일일 2건 한도.")
                        .build(),

                // A-8. AI 추천 사용 — 카운팅 전용 (pointsAmount=0, 포인트 미지급)
                //      누적 마일스톤(RECOMMENDATION_10 등) 및 쿼터 차감의 기준이 되는 활동
                RewardPolicy.builder()
                        .actionType("AI_CHAT_USE")
                        .activityName("AI 추천 사용")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(0)        // 포인트 미지급 (카운팅만)
                        .pointType("earn")
                        .dailyLimit(0)          // 횟수 제한 없음 (쿼터 시스템에서 별도 관리)
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType(null)
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("AI 추천 기능 사용 횟수 카운팅 전용. 포인트 미지급. 누적 마일스톤(RECOMMENDATION_10 등)의 부모 활동.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // B. 일회성 마일스톤 (6개) — point_type=bonus, max_count=1
                //    등급 배율 미적용, 평생 1회만 지급
                // ─────────────────────────────────────────────────────────────

                // B-1. 회원가입 보너스 — 가입 완료 시 즉시 200P
                // v3.0 변경: 500P → 200P
                //   이유: 기존 500P는 구 BRONZE 기준(500P)과 동일하여 신규 가입 즉시 BRONZE 승급 버그 발생.
                //         신규 기준 BRONZE=2,000P 대비 충분히 낮아 NORMAL 유지 보장.
                //         연쇄 승급 검증: 200P < BRONZE(2,000P) ✓
                RewardPolicy.builder()
                        .actionType("SIGNUP_BONUS")
                        .activityName("회원가입 보너스")
                        .actionCategory("MILESTONE")
                        .pointsAmount(200)
                        .pointType("bonus")     // 등급 배율 미적용 (고정 지급)
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("신규 회원가입 완료 시 1회 지급. 소셜 로그인(OAuth2) 최초 가입 포함. (v3.0: 200P, BRONZE 기준 2,000P 대비 안전 마진 확보)")
                        .build(),

                // B-2. 첫 AI 추천 — AI 추천 최초 사용 시 50P
                RewardPolicy.builder()
                        .actionType("FIRST_RECOMMENDATION")
                        .activityName("첫 AI 추천")
                        .actionCategory("MILESTONE")
                        .pointsAmount(50)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("AI 추천 기능 최초 사용 시 1회 지급.")
                        .build(),

                // B-3. 첫 리뷰 작성 — 리뷰 최초 작성 시 50P
                RewardPolicy.builder()
                        .actionType("FIRST_REVIEW")
                        .activityName("첫 리뷰 작성")
                        .actionCategory("MILESTONE")
                        .pointsAmount(50)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 리뷰 최초 작성 시 1회 지급.")
                        .build(),

                // B-4. 첫 게시글 작성 — 게시글 최초 작성 시 30P
                RewardPolicy.builder()
                        .actionType("FIRST_POST")
                        .activityName("첫 게시글 작성")
                        .actionCategory("MILESTONE")
                        .pointsAmount(30)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("커뮤니티 게시글 최초 작성 시 1회 지급.")
                        .build(),

                // B-5. 선호도 설정 — 영화 선호 장르/무드 최초 설정 시 30P
                RewardPolicy.builder()
                        .actionType("PREFERENCE_SETUP")
                        .activityName("선호도 설정")
                        .actionCategory("MILESTONE")
                        .pointsAmount(30)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("AI 추천 정확도를 위한 선호도(장르/무드/배우) 최초 설정 시 지급.")
                        .build(),

                // B-6. 프로필 완성 — 닉네임+프로필 이미지+자기소개 모두 입력 시 20P
                RewardPolicy.builder()
                        .actionType("PROFILE_COMPLETE")
                        .activityName("프로필 완성")
                        .actionCategory("MILESTONE")
                        .pointsAmount(20)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("닉네임, 프로필 이미지, 자기소개 모두 입력 완료 시 1회 지급.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // C-1. 누적 마일스톤 (15개) — threshold_target=TOTAL
                //      부모 활동의 total_count ≥ threshold_count 달성 시 1회 지급
                // ─────────────────────────────────────────────────────────────

                // C-1-a. AI 추천 누적 달성 (4단계)
                RewardPolicy.builder()
                        .actionType("RECOMMENDATION_10")
                        .activityName("AI 추천 10회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(30)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)            // 달성 보너스는 1회만
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(10)     // AI_CHAT_USE total_count >= 10
                        .thresholdTarget("TOTAL")
                        .parentActionType("AI_CHAT_USE")
                        .isActive(true)
                        .description("AI 추천 누적 10회 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("RECOMMENDATION_50")
                        .activityName("AI 추천 50회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(50)     // AI_CHAT_USE total_count >= 50
                        .thresholdTarget("TOTAL")
                        .parentActionType("AI_CHAT_USE")
                        .isActive(true)
                        .description("AI 추천 누적 50회 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("RECOMMENDATION_100")
                        .activityName("AI 추천 100회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(200)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(100)    // AI_CHAT_USE total_count >= 100
                        .thresholdTarget("TOTAL")
                        .parentActionType("AI_CHAT_USE")
                        .isActive(true)
                        .description("AI 추천 누적 100회 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("RECOMMENDATION_500")
                        .activityName("AI 추천 500회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(500)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(500)    // AI_CHAT_USE total_count >= 500
                        .thresholdTarget("TOTAL")
                        .parentActionType("AI_CHAT_USE")
                        .isActive(true)
                        .description("AI 추천 누적 500회 달성 보너스.")
                        .build(),

                // C-1-b. 리뷰 작성 누적 달성 (3단계)
                RewardPolicy.builder()
                        .actionType("REVIEW_MILESTONE_5")
                        .activityName("리뷰 5회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(30)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(5)      // REVIEW_CREATE total_count >= 5
                        .thresholdTarget("TOTAL")
                        .parentActionType("REVIEW_CREATE")
                        .isActive(true)
                        .description("리뷰 누적 5건 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("REVIEW_MILESTONE_20")
                        .activityName("리뷰 20회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(50)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(20)     // REVIEW_CREATE total_count >= 20
                        .thresholdTarget("TOTAL")
                        .parentActionType("REVIEW_CREATE")
                        .isActive(true)
                        .description("리뷰 누적 20건 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("REVIEW_MILESTONE_50")
                        .activityName("리뷰 50회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(50)     // REVIEW_CREATE total_count >= 50
                        .thresholdTarget("TOTAL")
                        .parentActionType("REVIEW_CREATE")
                        .isActive(true)
                        .description("리뷰 누적 50건 달성 보너스.")
                        .build(),

                // C-1-c. 게시글 작성 누적 달성 (3단계)
                RewardPolicy.builder()
                        .actionType("POST_MILESTONE_5")
                        .activityName("게시글 5회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(30)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(5)      // POST_REWARD total_count >= 5
                        .thresholdTarget("TOTAL")
                        .parentActionType("POST_REWARD")
                        .isActive(true)
                        .description("게시글 누적 5건 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("POST_MILESTONE_30")
                        .activityName("게시글 30회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(80)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(30)     // POST_REWARD total_count >= 30
                        .thresholdTarget("TOTAL")
                        .parentActionType("POST_REWARD")
                        .isActive(true)
                        .description("게시글 누적 30건 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("POST_MILESTONE_50")
                        .activityName("게시글 50회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(150)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(50)     // POST_REWARD total_count >= 50
                        .thresholdTarget("TOTAL")
                        .parentActionType("POST_REWARD")
                        .isActive(true)
                        .description("게시글 누적 50건 달성 보너스.")
                        .build(),

                // C-1-d. 댓글 작성 누적 달성 (2단계)
                RewardPolicy.builder()
                        .actionType("COMMENT_MILESTONE_30")
                        .activityName("댓글 30회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(50)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(30)     // COMMENT_CREATE total_count >= 30
                        .thresholdTarget("TOTAL")
                        .parentActionType("COMMENT_CREATE")
                        .isActive(true)
                        .description("댓글 누적 30건 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("COMMENT_MILESTONE_100")
                        .activityName("댓글 100회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(100)    // COMMENT_CREATE total_count >= 100
                        .thresholdTarget("TOTAL")
                        .parentActionType("COMMENT_CREATE")
                        .isActive(true)
                        .description("댓글 누적 100건 달성 보너스.")
                        .build(),

                // C-1-e. 도장깨기 코스 완주 — 가변 포인트 (pointsAmount=0)
                //        RewardService.grantRewardWithAmount()로 코스별 포인트를 동적 결정
                RewardPolicy.builder()
                        .actionType("COURSE_COMPLETE")
                        .activityName("도장깨기 완료")
                        .actionCategory("MILESTONE")
                        .pointsAmount(0)        // 가변 포인트 — grantRewardWithAmount 사용
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(0)            // 코스별 1회 (PER_REF: 특정 레퍼런스당 1회)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("PER_REF")   // 특정 코스 ID 기준 1회 지급
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("도장깨기 코스 1개 완주 시 지급. 포인트는 코스 설정값에 따라 동적 결정(grantRewardWithAmount). 코스당 1회.")
                        .build(),

                // C-1-f. 도장깨기 코스 누적 달성 (2단계)
                RewardPolicy.builder()
                        .actionType("COURSE_MILESTONE_5")
                        .activityName("코스 5개 완주")
                        .actionCategory("MILESTONE")
                        .pointsAmount(300)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(5)      // COURSE_COMPLETE total_count >= 5
                        .thresholdTarget("TOTAL")
                        .parentActionType("COURSE_COMPLETE")
                        .isActive(true)
                        .description("도장깨기 코스 5개 완주 달성 보너스.")
                        .build(),

                RewardPolicy.builder()
                        .actionType("COURSE_MILESTONE_10")
                        .activityName("코스 10개 완주")
                        .actionCategory("MILESTONE")
                        .pointsAmount(500)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(10)     // COURSE_COMPLETE total_count >= 10
                        .thresholdTarget("TOTAL")
                        .parentActionType("COURSE_COMPLETE")
                        .isActive(true)
                        .description("도장깨기 코스 10개 완주 달성 보너스.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // C-2. 연속 출석 (3개) — threshold_target=STREAK
                //      ATTENDANCE_BASE의 current_streak % threshold_count == 0 시 지급
                //      limitType=STREAK: 달성 횟수 제한 없음, 반복 달성 가능
                // ─────────────────────────────────────────────────────────────

                // C-2-a. 7일 연속 출석 — 7의 배수 달성마다 반복 지급
                RewardPolicy.builder()
                        .actionType("ATTENDANCE_STREAK_7")
                        .activityName("7일 연속 출석")
                        .actionCategory("ATTENDANCE")
                        .pointsAmount(50)
                        .pointType("bonus")
                        .dailyLimit(1)          // 하루 최대 1회 지급 (같은 날 중복 방지)
                        .maxCount(0)            // 평생 제한 없음 (반복 달성 가능)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("STREAK")
                        .thresholdCount(7)      // current_streak % 7 == 0 시 지급
                        .thresholdTarget("STREAK")
                        .parentActionType("ATTENDANCE_BASE")
                        .isActive(true)
                        .description("7일 연속 출석마다 지급. 7의 배수 스트릭 달성 시 반복 수령 가능.")
                        .build(),

                // C-2-b. 15일 연속 출석
                RewardPolicy.builder()
                        .actionType("ATTENDANCE_STREAK_15")
                        .activityName("15일 연속 출석")
                        .actionCategory("ATTENDANCE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(1)
                        .maxCount(0)            // 반복 달성 가능
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("STREAK")
                        .thresholdCount(15)     // current_streak % 15 == 0 시 지급
                        .thresholdTarget("STREAK")
                        .parentActionType("ATTENDANCE_BASE")
                        .isActive(true)
                        .description("15일 연속 출석마다 지급. 15의 배수 스트릭 달성 시 반복 수령 가능.")
                        .build(),

                // C-2-c. 30일 연속 출석
                RewardPolicy.builder()
                        .actionType("ATTENDANCE_STREAK_30")
                        .activityName("30일 연속 출석")
                        .actionCategory("ATTENDANCE")
                        .pointsAmount(300)
                        .pointType("bonus")
                        .dailyLimit(1)
                        .maxCount(0)            // 반복 달성 가능
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("STREAK")
                        .thresholdCount(30)     // current_streak % 30 == 0 시 지급
                        .thresholdTarget("STREAK")
                        .parentActionType("ATTENDANCE_BASE")
                        .isActive(true)
                        .description("30일 연속 출석마다 지급. 30의 배수 스트릭 달성 시 반복 수령 가능.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // C-3. 일일 달성 (4개) — threshold_target=DAILY
                //      부모 활동의 daily_count == threshold_count 시 지급
                //      limitType=DAILY: 매일 초기화, 반복 달성 가능
                // ─────────────────────────────────────────────────────────────

                // C-3-a. 오늘 리뷰 3건 — 당일 리뷰 3건 도달 시 보너스
                // v3.1: 30P → 15P
                RewardPolicy.builder()
                        .actionType("DAILY_REVIEW_3")
                        .activityName("오늘 리뷰 3건")
                        .actionCategory("CONTENT")
                        .pointsAmount(15)       // v3.1: 30 → 15P
                        .pointType("bonus")
                        .dailyLimit(1)          // 하루 1회 (일일 목표 달성 1회만)
                        .maxCount(0)            // 매일 달성 가능 (평생 무제한)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(3)      // REVIEW_CREATE daily_count == 3
                        .thresholdTarget("DAILY")
                        .parentActionType("REVIEW_CREATE")
                        .isActive(true)
                        .description("하루 리뷰 3건 작성 달성 시 보너스. 매일 갱신.")
                        .build(),

                // C-3-b. 오늘 댓글 5건
                // v3.1: 20P → 10P
                RewardPolicy.builder()
                        .actionType("DAILY_COMMENT_5")
                        .activityName("오늘 댓글 5건")
                        .actionCategory("CONTENT")
                        .pointsAmount(10)       // v3.1: 20 → 10P
                        .pointType("bonus")
                        .dailyLimit(1)
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(5)      // COMMENT_CREATE daily_count == 5
                        .thresholdTarget("DAILY")
                        .parentActionType("COMMENT_CREATE")
                        .isActive(true)
                        .description("하루 댓글 5건 작성 달성 시 보너스. 매일 갱신.")
                        .build(),

                // C-3-c. 오늘 게시글 3건
                // v3.1: 30P → 15P
                RewardPolicy.builder()
                        .actionType("DAILY_POST_3")
                        .activityName("오늘 게시글 3건")
                        .actionCategory("CONTENT")
                        .pointsAmount(15)       // v3.1: 30 → 15P
                        .pointType("bonus")
                        .dailyLimit(1)
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(3)      // POST_REWARD daily_count == 3
                        .thresholdTarget("DAILY")
                        .parentActionType("POST_REWARD")
                        .isActive(true)
                        .description("하루 게시글 3건 작성 달성 시 보너스. 매일 갱신.")
                        .build(),

                // C-3-d. 오늘 찜 5건
                RewardPolicy.builder()
                        .actionType("DAILY_WISHLIST_5")
                        .activityName("오늘 찜 5건")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(15)
                        .pointType("bonus")
                        .dailyLimit(1)
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(5)      // WISHLIST_ADD daily_count == 5
                        .thresholdTarget("DAILY")
                        .parentActionType("WISHLIST_ADD")
                        .isActive(true)
                        .description("하루 위시리스트 추가 5건 달성 시 보너스. 매일 갱신.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // D. 등급 승급 보상 (4개) — point_type=bonus, max_count=1
                //    등급 승급 이벤트 발생 시 RewardService가 직접 호출
                // ─────────────────────────────────────────────────────────────

                // D-1. 브론즈 승급 (earned_by_activity 2,000 달성)
                // v3.0: BRONZE 기준 500→2,000P 상향. 포인트 100P 유지.
                //   연쇄 승급 검증: 2,000 + 100 = 2,100P < SILVER(8,000P) ✓
                RewardPolicy.builder()
                        .actionType("GRADE_UP_BRONZE")
                        .activityName("브론즈 승급")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회 (등급은 되돌아갈 수 없음)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("NORMAL → BRONZE 등급 승급 달성 시 1회 지급. earned_by_activity 2,000P 달성 기준. (v3.0: 기준 2,000P)")
                        .build(),

                // D-2. 실버 승급 (earned_by_activity 8,000 달성)
                // v3.0: SILVER 기준 2,000→8,000P 상향. 포인트 300P → 200P 축소.
                //   이유: 승급 보상이 다음 등급 임계값의 2.5% 이내로 유지하여 연쇄 승급 방지.
                //   연쇄 승급 검증: 8,000 + 200 = 8,200P < GOLD(20,000P) ✓
                RewardPolicy.builder()
                        .actionType("GRADE_UP_SILVER")
                        .activityName("실버 승급")
                        .actionCategory("MILESTONE")
                        .pointsAmount(200)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("BRONZE → SILVER 등급 승급 달성 시 1회 지급. earned_by_activity 8,000P 달성 기준. (v3.0: 기준 8,000P, 보상 200P)")
                        .build(),

                // D-3. 골드 승급 (earned_by_activity 20,000 달성)
                // v3.0: GOLD 기준 5,000→20,000P 상향. 포인트 500P 유지.
                //   연쇄 승급 검증: 20,000 + 500 = 20,500P < PLATINUM(50,000P) ✓
                RewardPolicy.builder()
                        .actionType("GRADE_UP_GOLD")
                        .activityName("골드 승급")
                        .actionCategory("MILESTONE")
                        .pointsAmount(500)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("SILVER → GOLD 등급 승급 달성 시 1회 지급. earned_by_activity 20,000P 달성 기준. (v3.0: 기준 20,000P)")
                        .build(),

                // D-4. 플래티넘 승급 (earned_by_activity 10,000 달성, v3.2 기준)
                // v3.2: PLATINUM 기준 100,000→10,000P 하향. 포인트 500P.
                //   연쇄 승급 검증: 10,000 + 500 = 10,500P < DIAMOND(20,000P) ✓
                RewardPolicy.builder()
                        .actionType("GRADE_UP_PLATINUM")
                        .activityName("플래티넘 승급")
                        .actionCategory("MILESTONE")
                        .pointsAmount(500)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("GOLD → PLATINUM(몽글팝콘) 등급 승급 달성 시 1회 지급. earned_by_activity 10,000P 달성 기준. (v3.2: 기준 10,000P, 보상 500P)")
                        .build(),

                // D-5. 다이아몬드 승급 (earned_by_activity 20,000 달성, v3.2 신규 등급)
                // v3.2: DIAMOND(몽아일체) 최고 등급 신규 추가. 최고 등급 달성 보상.
                //   연쇄 승급 없음 (DIAMOND가 최고 등급) ✓
                RewardPolicy.builder()
                        .actionType("GRADE_UP_DIAMOND")
                        .activityName("다이아몬드 승급")
                        .actionCategory("MILESTONE")
                        .pointsAmount(250)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("PLATINUM → DIAMOND(몽아일체) 등급 승급 달성 시 1회 지급. earned_by_activity 20,000P 달성 기준. (v3.2 신규)")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // E. 월드컵/업적/퀴즈 (4개)
                // ─────────────────────────────────────────────────────────────

                // E-1. 월드컵 완주 — 하루 최대 5회 반복 지급 (bonus)
                // v3.1: earn → bonus (PLATINUM ×2.0 인플레이션 방지. 고정 20P×5=100P/일)
                RewardPolicy.builder()
                        .actionType("WORLDCUP_COMPLETE")
                        .activityName("월드컵 완주")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(20)
                        .pointType("bonus")       // v3.1: earn→bonus (PLATINUM ×2.0 인플레이션 방지. 고정 20P×5=100P/일)
                        .dailyLimit(5)          // 하루 최대 5회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 월드컵 토너먼트 완주 시 지급. 일일 5회 한도. (v3.1: earn→bonus, 등급 배율 미적용)")
                        .build(),

                // E-2. 첫 월드컵 완주 — 평생 1회 보너스 (bonus)
                RewardPolicy.builder()
                        .actionType("WORLDCUP_FIRST")
                        .activityName("첫 월드컵 완주")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(1)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 월드컵 최초 완주 시 1회 지급.")
                        .build(),

                // E-3. 퀴즈 정답 — 하루 최대 10회 반복 지급 (bonus)
                // v3.1: earn → bonus (퀴즈 반복 파밍 + 배율 인플레이션 방지. 고정 10P×10=100P/일)
                RewardPolicy.builder()
                        .actionType("QUIZ_CORRECT")
                        .activityName("퀴즈 정답")
                        .actionCategory("CONTENT")
                        .pointsAmount(10)
                        .pointType("bonus")       // v3.1: earn→bonus (퀴즈 반복 파밍 + 배율 인플레이션 방지. 고정 10P×10=100P/일)
                        .dailyLimit(10)         // 하루 최대 10회
                        .maxCount(0)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 관련 퀴즈 정답 시 지급. 일일 10회 한도. (v3.1: earn→bonus, 등급 배율 미적용)")
                        .build(),

                // E-4. 업적 달성 — 가변 포인트 (pointsAmount=0), 업적별 동적 결정
                //      RewardService.grantRewardWithAmount()로 업적 설정값을 동적 적용
                RewardPolicy.builder()
                        .actionType("ACHIEVEMENT_UNLOCK")
                        .activityName("업적 달성")
                        .actionCategory("MILESTONE")
                        .pointsAmount(0)        // 가변 포인트 — grantRewardWithAmount 사용
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(0)            // 업적별 1회 (PER_REF: 특정 업적 ID 기준)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("PER_REF")   // 특정 업적 ID 기준 1회 지급
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("업적(Achievement) 달성 시 지급. 포인트는 업적별 설정값으로 동적 결정(grantRewardWithAmount). 업적당 1회.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // E-5~9. 도장깨기/퀴즈/업적 추가 정책
                //        E-1~E-4(WORLDCUP_COMPLETE, WORLDCUP_FIRST, QUIZ_CORRECT, ACHIEVEMENT_UNLOCK)
                //        는 위에 이미 정의됨. 아래는 신규 추가 정책.
                // ─────────────────────────────────────────────────────────────

                // E-5. 첫 도장깨기 코스 완주 — 평생 1회 보너스 (bonus)
                //      COURSE_COMPLETE 이후 최초 완주 감지 시 추가 지급
                RewardPolicy.builder()
                        .actionType("COURSE_FIRST")
                        .activityName("첫 코스 완주")
                        .actionCategory("MILESTONE")
                        .pointsAmount(200)
                        .pointType("bonus")     // 등급 배율 미적용 (고정 지급)
                        .dailyLimit(1)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("도장깨기 코스 최초 완주 시 1회 지급. 이후 COURSE_COMPLETE 정책으로 전환.")
                        .build(),

                // E-6. 퀴즈 만점 — 하루 최대 3회 반복 지급 (bonus)
                //      퀴즈 전체 정답(모든 문제 통과) 시 추가 보너스
                RewardPolicy.builder()
                        .actionType("QUIZ_PERFECT")
                        .activityName("퀴즈 만점")
                        .actionCategory("CONTENT")
                        .pointsAmount(50)
                        .pointType("bonus")     // 등급 배율 미적용 (고정 지급)
                        .dailyLimit(3)          // 하루 최대 3회
                        .maxCount(0)            // 평생 무제한
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("DAILY")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("퀴즈 전 문항 정답(만점) 달성 시 보너스 지급. 일일 3회 한도.")
                        .build(),

                // E-7. 첫 퀴즈 정답 — 평생 1회 보너스 (bonus)
                RewardPolicy.builder()
                        .actionType("QUIZ_FIRST")
                        .activityName("첫 퀴즈 정답")
                        .actionCategory("MILESTONE")
                        .pointsAmount(30)
                        .pointType("bonus")     // 등급 배율 미적용 (고정 지급)
                        .dailyLimit(1)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("퀴즈 최초 정답 시 1회 지급. QUIZ_CORRECT와 함께 지급.")
                        .build(),

                // E-8. 5개 장르 탐험 업적 — 평생 1회 보너스 (bonus)
                //      서로 다른 5개 장르의 영화를 시청/평가한 사용자에게 지급
                //      AchievementService.checkAndGrant("GENRE_EXPLORER", ...) 호출 시 연동
                RewardPolicy.builder()
                        .actionType("GENRE_EXPLORER")
                        .activityName("5개 장르 탐험")
                        .actionCategory("MILESTONE")
                        .pointsAmount(150)
                        .pointType("bonus")     // 등급 배율 미적용 (고정 지급)
                        .dailyLimit(1)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("서로 다른 5개 장르의 영화를 탐험(시청/평가) 시 1회 지급. AchievementType.achievementCode=genre_explorer 연동.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // C-1-g. 위시리스트 누적 달성 (3단계) — v3.1 신규 추가
                //        WISHLIST_ADD total_count 기준 달성 시 보너스 1회 지급
                // ─────────────────────────────────────────────────────────────

                // C-1-g-1. 위시리스트 10건 달성
                RewardPolicy.builder()
                        .actionType("WISHLIST_MILESTONE_10")
                        .activityName("찜 10회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(20)
                        .pointType("bonus")     // 등급 배율 미적용 (달성 보너스)
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(10)     // WISHLIST_ADD total_count >= 10
                        .thresholdTarget("TOTAL")
                        .parentActionType("WISHLIST_ADD")
                        .isActive(true)
                        .description("위시리스트 누적 10건 달성 보너스.")
                        .build(),

                // C-1-g-2. 위시리스트 50건 달성
                RewardPolicy.builder()
                        .actionType("WISHLIST_MILESTONE_50")
                        .activityName("찜 50회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(50)
                        .pointType("bonus")     // 등급 배율 미적용 (달성 보너스)
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(50)     // WISHLIST_ADD total_count >= 50
                        .thresholdTarget("TOTAL")
                        .parentActionType("WISHLIST_ADD")
                        .isActive(true)
                        .description("위시리스트 누적 50건 달성 보너스.")
                        .build(),

                // C-1-g-3. 위시리스트 100건 달성
                RewardPolicy.builder()
                        .actionType("WISHLIST_MILESTONE_100")
                        .activityName("찜 100회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")     // 등급 배율 미적용 (달성 보너스)
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(100)    // WISHLIST_ADD total_count >= 100
                        .thresholdTarget("TOTAL")
                        .parentActionType("WISHLIST_ADD")
                        .isActive(true)
                        .description("위시리스트 누적 100건 달성 보너스.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // C-1-h. 게시글/댓글 대량 누적 달성 — v3.1 신규 추가
                //        기존 POST_MILESTONE_5/30/50 시리즈의 고단계 연장
                // ─────────────────────────────────────────────────────────────

                // C-1-h-1. 게시글 100건 달성
                RewardPolicy.builder()
                        .actionType("POST_MILESTONE_100")
                        .activityName("게시글 100회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(300)
                        .pointType("bonus")     // 등급 배율 미적용 (달성 보너스)
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(100)    // POST_REWARD total_count >= 100
                        .thresholdTarget("TOTAL")
                        .parentActionType("POST_REWARD")
                        .isActive(true)
                        .description("게시글 누적 100건 달성 보너스.")
                        .build(),

                // C-1-h-2. 댓글 500건 달성
                RewardPolicy.builder()
                        .actionType("COMMENT_MILESTONE_500")
                        .activityName("댓글 500회")
                        .actionCategory("MILESTONE")
                        .pointsAmount(300)
                        .pointType("bonus")     // 등급 배율 미적용 (달성 보너스)
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(500)    // COMMENT_CREATE total_count >= 500
                        .thresholdTarget("TOTAL")
                        .parentActionType("COMMENT_CREATE")
                        .isActive(true)
                        .description("댓글 누적 500건 달성 보너스.")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // F. 온보딩 마일스톤 (4개) — point_type=bonus, ONCE
                //    신규 가입 후 초기 설정 완료 시 지급 (각 항목 평생 1회)
                //    엑셀 t2_09 시트 reward_policy 기준
                // ─────────────────────────────────────────────────────────────

                // F-1. 영화 월드컵 온보딩 완료 — 온보딩 중 월드컵 완주 시 1회
                RewardPolicy.builder()
                        .actionType("MOVIE_CUP_ONBOARD")
                        .activityName("영화 월드컵 온보딩 완료")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)            // 평생 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("온보딩 단계에서 영화 월드컵을 처음 완료 시 1회 지급. (엑셀 기준)")
                        .build(),

                // F-2. 인생영화 온보딩 설정 — 온보딩 중 인생영화 등록 시 1회
                RewardPolicy.builder()
                        .actionType("FAV_MOVIE_ONBOARD")
                        .activityName("인생영화 온보딩 설정")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("온보딩 단계에서 인생영화를 처음 설정 시 1회 지급. (엑셀 기준)")
                        .build(),

                // F-3. 선호장르 온보딩 설정 — 온보딩 중 선호장르 등록 시 1회
                RewardPolicy.builder()
                        .actionType("FAV_GENRE_ONBOARD")
                        .activityName("선호장르 온보딩 설정")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("온보딩 단계에서 선호장르를 처음 설정 시 1회 지급. (엑셀 기준)")
                        .build(),

                // F-4. 온보딩 3개 완료 보너스 — F-1~F-3 모두 완료 시 추가 보너스
                RewardPolicy.builder()
                        .actionType("ONBOARD_COMPLETE")
                        .activityName("온보딩 3개 완료 보너스")
                        .actionCategory("MILESTONE")
                        .pointsAmount(100)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(0)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("영화 월드컵·인생영화·선호장르 온보딩 3개 항목 모두 완료 시 추가 보너스 1회 지급. (엑셀 기준)")
                        .build(),

                // ─────────────────────────────────────────────────────────────
                // G. 플레이리스트 활동 (1개)
                // ─────────────────────────────────────────────────────────────

                // G-1. 플레이리스트 공유 — 평생 1회 보너스
                RewardPolicy.builder()
                        .actionType("PLAYLIST_SHARE")
                        .activityName("플레이리스트 공유")
                        .actionCategory("ENGAGEMENT")
                        .pointsAmount(10)
                        .pointType("bonus")
                        .dailyLimit(0)
                        .maxCount(1)            // 첫 공유 시 1회
                        .cooldownSeconds(0)
                        .minContentLength(0)
                        .limitType("ONCE")
                        .thresholdCount(1)
                        .thresholdTarget(null)
                        .parentActionType(null)
                        .isActive(true)
                        .description("플레이리스트를 처음 공유 시 1회 지급. (엑셀 기준)")
                        .build()
        );
    }
}
