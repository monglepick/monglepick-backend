package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ExtendSubscriptionResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionPlanResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionStatusResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 구독 서비스 — 구독 상품 조회, 구독 생성/취소/만료 비즈니스 로직.
 *
 * <p>사용자의 구독 라이프사이클(생성 → 활성 → 취소/만료)을 관리하는 서비스이다.
 * 결제 완료 후 구독을 활성화하고, 사용자 요청으로 취소하며,
 * 스케줄러로 만료된 구독을 자동 처리한다.</p>
 *
 * <h3>구독 상태 전이</h3>
 * <pre>
 * [결제 완료] → ACTIVE ──→ CANCELLED (사용자 취소, 만료일까지 혜택 유지)
 *                     ──→ EXPIRED   (만료일 도래, 자동 갱신 아닌 경우)
 * </pre>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>한 사용자는 동시에 1개의 ACTIVE 구독만 보유 가능</li>
 *   <li>취소된 구독은 만료일까지 서비스 이용 가능</li>
 *   <li>자동 갱신은 현재 단순 만료 처리 (PG 정기결제는 향후 구현)</li>
 * </ul>
 *
 * <h3>스케줄러</h3>
 * <p>{@link #processExpiredSubscriptions()} 메서드가 매일 새벽 2시에 실행되어
 * 만료일이 지난 구독을 자동으로 EXPIRED 상태로 변경한다.
 * {@code @EnableScheduling}이 애플리케이션 클래스에 선언되어 있어야 한다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 기본 읽기 전용</li>
 *   <li>변경 메서드: 개별 {@code @Transactional} 오버라이드</li>
 * </ul>
 *
 * @see SubscriptionPlan 구독 상품 엔티티
 * @see UserSubscription 사용자 구독 엔티티
 * @see PaymentService 결제 서비스 (결제 완료 후 구독 생성 호출)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    /** 구독 상품 리포지토리 */
    private final SubscriptionPlanRepository planRepository;

    /** 사용자 구독 리포지토리 */
    private final UserSubscriptionRepository subscriptionRepository;

    /**
     * 등급 마스터 리포지토리.
     *
     * <p>구독 결제 완료 시 {@code Grade.subscriptionPlanType} 기준으로
     * 해당 플랜이 즉시 보장해야 하는 등급(SILVER/PLATINUM)을 조회하기 위해 사용한다.
     * 설계서 v3.2 §4.5 의 "구독 등급 보장" 정책을 구현한다.</p>
     */
    private final GradeRepository gradeRepository;

    /**
     * 사용자 포인트 리포지토리.
     *
     * <p>구독 결제 완료 시 현재 사용자의 등급보다 구독 보장 등급이 더 높으면
     * 즉시 업그레이드하기 위해 사용한다. 활동 포인트({@code earned_by_activity}) 기반
     * 등급과 구독 보장 등급 중 <b>더 높은 쪽</b>을 최종 등급으로 설정한다 (effective grade).</p>
     */
    private final UserPointRepository userPointRepository;

    /**
     * 포인트 서비스 — 구독 만료 시 활동 포인트 기반 등급 재계산용.
     *
     * <p>구독 결제로 즉시 SILVER/PLATINUM 으로 끌어올린 등급을 만료 시점에
     * 활동 포인트({@code earned_by_activity}) 기반 자연 등급으로 되돌리기 위해 사용한다.
     * 이 호출이 없으면 한 달짜리 구독으로 영구 PLATINUM 등급을 우회 획득할 수 있다.</p>
     *
     * @see PointService#recalculateActivityGrade(String)
     */
    private final PointService pointService;

    // ──────────────────────────────────────────────
    // 구독 상품 조회
    // ──────────────────────────────────────────────

    /**
     * 활성 구독 상품 목록을 조회한다.
     *
     * <p>클라이언트의 "구독 상품 목록" 화면에서 사용한다.
     * {@code is_active = TRUE}인 상품만 가격 오름차순으로 반환한다.
     * 비활성화된 상품은 신규 구독이 불가하므로 제외된다.</p>
     *
     * @return 활성 구독 상품 목록 (가격 오름차순)
     */
    /* 캐시 제거: 구독 상품은 조회 빈도 낮고, Redis 직렬화 이슈 방지 */
    public List<SubscriptionPlanResponse> getActivePlans() {
        log.debug("활성 구독 상품 목록 조회");

        List<SubscriptionPlan> plans = planRepository.findByIsActiveTrueOrderByPriceAsc();

        return plans.stream()
                .map(this::toPlanResponse)
                .toList();
    }

    // ──────────────────────────────────────────────
    // 구독 상태 조회
    // ──────────────────────────────────────────────

    /**
     * 사용자의 구독 상태를 조회한다.
     *
     * <p>활성 구독이 있으면 상세 정보(상품명, 상태, 시작/만료일, 자동갱신 여부)를 반환하고,
     * 없으면 {@code hasActiveSubscription=false}만 반환한다.
     * 클라이언트의 "내 구독" 화면에서 사용된다.</p>
     *
     * @param userId 사용자 ID
     * @return 구독 상태 응답 (활성 구독 존재 여부 + 상세 정보)
     */
    public SubscriptionStatusResponse getStatus(String userId) {
        log.debug("구독 상태 조회: userId={}", userId);

        // JOIN FETCH로 plan을 즉시 로딩 (N+1 방지)
        Optional<UserSubscription> activeSub = subscriptionRepository
                .findByUserIdAndStatusFetchPlan(userId, UserSubscription.Status.ACTIVE);

        // 활성 구독이 없는 경우
        if (activeSub.isEmpty()) {
            log.debug("활성 구독 없음: userId={}", userId);
            return new SubscriptionStatusResponse(false, null, null, null, null, null, false);
        }

        // 활성 구독이 있는 경우 (plan은 이미 즉시 로딩됨)
        UserSubscription sub = activeSub.get();
        log.debug("활성 구독 발견: userId={}, planId={}, expiresAt={}",
                userId, sub.getPlan().getSubscriptionPlanId(), sub.getExpiresAt());

        // planCode 는 클라이언트가 "같은 플랜 재결제 버튼 비활성" 판정에 사용한다.
        // 2026-04-14 추가 — DTO 에 필드가 없어서 프론트는 planName 문자열 매칭에 의존했고,
        // 다국어/공백 이슈로 판정이 불안정했다. planCode 로 명확히 내려준다.
        return new SubscriptionStatusResponse(
                true,
                sub.getPlan().getPlanCode(),
                sub.getPlan().getName(),
                sub.getStatus().name(),
                sub.getStartedAt(),
                sub.getExpiresAt(),
                sub.getAutoRenew()
        );
    }

    // ──────────────────────────────────────────────
    // 구독 생성 (결제 완료 후 호출)
    // ──────────────────────────────────────────────

    /**
     * 구독을 생성한다 (결제 완료 후 호출).
     *
     * <p>결제 서비스(PaymentService)에서 구독 결제 승인 완료 후 이 메서드를 호출한다.
     * 현재 시각을 기준으로 시작일을 설정하고, 구독 주기에 따라 만료일을 계산한다.</p>
     *
     * <h4>만료일 계산</h4>
     * <ul>
     *   <li>MONTHLY: 현재 시각 + 1개월</li>
     *   <li>YEARLY: 현재 시각 + 1년</li>
     * </ul>
     *
     * <h4>중복 구독 방지</h4>
     * <p>이미 ACTIVE 구독이 존재하면 {@code ACTIVE_SUBSCRIPTION_EXISTS} 에러를 반환한다.
     * 한 사용자는 동시에 1개의 활성 구독만 보유할 수 있다.</p>
     *
     * @param userId 사용자 ID
     * @param plan   구독 상품 (SubscriptionPlan 엔티티)
     * @throws BusinessException 이미 활성 구독이 존재하는 경우 (ACTIVE_SUBSCRIPTION_EXISTS)
     */
    @Transactional
    public void createSubscription(String userId, SubscriptionPlan plan) {
        log.info("구독 생성 시작: userId={}, planCode={}", userId, plan.getPlanCode());

        // 1. 기존 활성 구독 확인 (PESSIMISTIC_WRITE 비관적 락)
        //
        // 단순 findByUserIdAndStatus() 대신 FOR UPDATE 잠금 쿼리를 사용한다.
        // 동일 사용자가 여러 탭/기기에서 동시에 구독 결제를 완료해도
        // 첫 번째 트랜잭션이 커밋될 때까지 DB 레벨에서 대기시키므로
        // TOCTOU 경쟁 조건으로 인한 다중 ACTIVE 생성을 원천 차단한다.
        Optional<UserSubscription> existingActiveOpt = subscriptionRepository
                .findByUserIdAndStatusForUpdate(userId, UserSubscription.Status.ACTIVE);

        if (existingActiveOpt.isPresent()) {
            UserSubscription existing = existingActiveOpt.get();
            String existingPlanCode = existing.getPlan() != null
                    ? existing.getPlan().getPlanCode() : null;

            // 1-1. 동일 planCode 는 여전히 차단 (중복 결제 방지)
            //      여기까지 도달했다는 것은 PaymentService.createOrder 의 사전 검사를
            //      우회했다는 뜻(동시성 또는 이전 트랜잭션에서 planCode 가 바뀐 케이스)이므로
            //      안전망으로 강력하게 막는다.
            if (existingPlanCode != null && existingPlanCode.equals(plan.getPlanCode())) {
                log.warn("동일 planCode 중복 구독 시도 차단 (createSubscription 안전망): userId={}, planCode={}",
                        userId, plan.getPlanCode());
                throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_EXISTS);
            }

            // 1-2. 다른 planCode(플랜 변경) — 기존 구독을 CANCELLED 로 전이하고 진행
            //
            //   정책:
            //     - 기존 구독 status = CANCELLED (autoRenew=false, cancelledAt=now)
            //     - 기존 구독의 만료일(expiresAt)은 건드리지 않음 → 만료 배치가 정상 처리
            //     - 기존 구독의 만료일까지 잔여 혜택은 정책상 "새 플랜이 우선"되므로 실질 이용 불가
            //       (QuotaService 가 ACTIVE 단일 조회로 동작하기 때문)
            //     - 이 손실은 프론트엔드 확인 모달에서 반드시 사용자에게 고지되어야 한다.
            //
            //   안전성:
            //     - 본 메서드는 PaymentConfirmProcessor 의 @Transactional 범위 안에서 실행되므로
            //       결제 승인 직후 실패(예: DB 오류)로 롤백되면 기존 ACTIVE 도 그대로 복원된다.
            //     - Toss 보상 환불 패턴이 호출자(PaymentService)에서 연결되어 있어 카드 결제도 안전하다.
            log.info("플랜 변경 — 기존 구독 CANCELLED 전이: userId={}, 기존={}, 신규={}",
                    userId, existingPlanCode, plan.getPlanCode());
            existing.cancel();

            // 1-3. ★ 플랜 변경 전 등급 리셋 (2026-04-14 다운그레이드 버그 수정)
            //
            //   applyGuaranteedGradeForSubscription() 는 "현재 등급의 sort_order 가 보장 등급보다
            //   높으면 강등하지 않는다" 정책으로 동작한다. 이는 활동 포인트로 정상 달성한 상위 등급을
            //   낮은 플랜 구독 결제로 강등하지 않기 위한 방어 로직이다.
            //
            //   그러나 "기존 premium 구독으로 끌어올린 PLATINUM 상태"에서 basic 으로 다운그레이드하면
            //   새 구독 보장 등급(SILVER)보다 현재 등급(PLATINUM)이 높다는 이유로 강등이 막혀
            //   PLATINUM 이 영구 유지되는 버그가 발생한다.
            //
            //   해결: 기존 구독이 CANCELLED 된 직후 activity 기반 자연 등급으로 먼저 되돌린 뒤
            //   applyGuaranteed 가 새 구독 보장 등급으로 max(activity, new_sub) 재적용하도록 순서를 맞춘다.
            //
            //   모든 케이스 동작:
            //     • 업그레이드 (basic→premium): activity=BRONZE → basic 리셋 → premium 적용 → PLATINUM ✓
            //     • 다운그레이드 (premium→basic): activity=BRONZE → basic 리셋 → basic 적용 → SILVER ✓
            //     • 주기 변경 (monthly↔yearly 동일 등급): activity=BRONZE → basic 리셋 → basic 적용 → SILVER ✓
            //     (activity 기반 등급이 이미 상위라면 max 연산으로 자연스럽게 보존됨)
            try {
                pointService.recalculateActivityGrade(userId);
            } catch (Exception gradeErr) {
                log.error("플랜 변경 중 activity 등급 리셋 실패 — 다운그레이드 시 강등이 누락될 수 있음. "
                                + "userId={}, error={}",
                        userId, gradeErr.getMessage(), gradeErr);
                // 등급 리셋 실패해도 결제/구독 자체는 진행한다 (운영팀 수동 조치 대상)
            }
        }

        // 2. 시작/만료일 계산
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = plan.getPeriodType() == SubscriptionPlan.PeriodType.MONTHLY
                ? now.plusMonths(1)   // 월간 구독: 1개월 후 만료
                : now.plusYears(1);   // 연간 구독: 1년 후 만료

        // 3. UserSubscription 생성 및 저장
        UserSubscription subscription = UserSubscription.builder()
                .userId(userId)
                .plan(plan)
                .status(UserSubscription.Status.ACTIVE)
                .startedAt(now)
                .expiresAt(expiresAt)
                .autoRenew(true)
                .build();

        // 4. AI 보너스 풀 초기화 (v3.0 — 3-소스 모델 소스 2: SUB_BONUS)
        //    구독 활성화 직후 remainingAiBonus를 plan.monthlyAiBonus로 설정하고
        //    aiBonusReset을 오늘 날짜로 기록한다.
        //    이 호출이 누락되면 구독 후 AI 보너스가 0회로 남아 SUB_BONUS 소스가 작동하지 않는다.
        if (plan.getMonthlyAiBonus() != null && plan.getMonthlyAiBonus() > 0) {
            subscription.initAiBonus(plan.getMonthlyAiBonus());
            log.info("구독 AI 보너스 초기화: userId={}, monthlyAiBonus={}",
                    userId, plan.getMonthlyAiBonus());
        }

        subscriptionRepository.save(subscription);

        // 5. 구독 등급 보장 적용 (v3.2 §4.5 — 2026-04-14 버그 수정)
        //
        // 과거에는 구독 결제 완료 후 포인트만 지급되고 등급은 변경되지 않아,
        // "몽글팝콘(PLATINUM) 보장" 같은 구독 혜택이 실질적으로 작동하지 않았다.
        // 포인트 지급 경로(PointService.earnPoint)는 "활동 포인트" 기반 등급 계산만
        // 수행하므로 구독 결제로 받은 포인트(isActivityReward=false)는 등급 상승에
        // 기여하지 않는 것이 정상 설계다. 구독 자체에 의한 즉시 등급 보장은 별도 흐름이
        // 필요하며 이 블록이 그 역할을 담당한다.
        //
        // 정책: planCode 의 "basic" / "premium" 토큰을 통해 Grade.subscriptionPlanType 을
        //      매핑해 해당 등급을 조회하고, 현재 사용자 등급의 sort_order 와 비교해
        //      더 높은 쪽을 최종 등급으로 설정한다 (effective_grade = max(기존, 구독 보장)).
        //      이미 더 높은 등급이라면 강등하지 않는다 (활동 포인트로 달성한 등급 보존).
        applyGuaranteedGradeForSubscription(userId, plan);

        log.info("구독 생성 완료: userId={}, planCode={}, expiresAt={}, aiBonus={}",
                userId, plan.getPlanCode(), expiresAt,
                subscription.getRemainingAiBonus());
    }

    /**
     * 구독 플랜이 보장하는 등급을 사용자에게 즉시 적용한다.
     *
     * <p>설계서 v3.2 §4.5 의 구독 등급 보장 정책 구현.
     * {@code monthly_basic / yearly_basic} → SILVER,
     * {@code monthly_premium / yearly_premium} → PLATINUM 을 즉시 보장한다.</p>
     *
     * <h4>동작 규칙</h4>
     * <ul>
     *   <li>플랜 코드에서 "premium" / "basic" 토큰을 추출 → {@code subscription_plan_type} 으로 매핑</li>
     *   <li>매핑되는 Grade 가 없으면 (예: 신규 플랜 타입) 스킵 — 기존 등급 유지</li>
     *   <li>사용자 UserPoint 가 없으면 스킵 (포인트 시스템 초기화 후 적용)</li>
     *   <li>현재 등급의 {@code sort_order} 가 더 높으면 강등하지 않음 (정상 설계)</li>
     *   <li>현재 등급과 보장 등급이 같으면 무변화 로그만 기록</li>
     * </ul>
     *
     * <p>예외는 로그만 남기고 삼킨다 — 등급 적용 실패가 구독 생성 자체를 롤백해서는 안 된다
     * (카드 승인 완료, 구독 레코드 생성 완료 상태에서 등급 갱신만 실패한 것이므로
     * 이를 롤백하면 유저가 더 큰 피해를 입는다. 대신 경고 로그로 운영팀이 인지하게 한다).</p>
     *
     * @param userId 사용자 ID
     * @param plan   방금 생성된 구독의 플랜
     */
    private void applyGuaranteedGradeForSubscription(String userId, SubscriptionPlan plan) {
        try {
            // planCode 기반 subscription_plan_type 추출 (엔티티에 별도 필드가 없으므로 코드에서 파싱)
            String planCode = plan.getPlanCode() != null ? plan.getPlanCode().toLowerCase() : "";
            String guaranteedType;
            String fallbackGradeCode;     // 마스터 데이터에 매핑이 없을 때 사용할 hard-coded 등급 코드
            if (planCode.contains("premium")) {
                guaranteedType = "premium";
                fallbackGradeCode = "PLATINUM";
            } else if (planCode.contains("basic")) {
                guaranteedType = "basic";
                fallbackGradeCode = "SILVER";
            } else {
                log.debug("구독 등급 보장 스킵 (basic/premium 토큰 없음): planCode={}", plan.getPlanCode());
                return;
            }

            // ── 1차 매핑: subscription_plan_type 컬럼 기반 조회 (정상 시드 환경) ──
            //   GradeRepository.findFirstBySubscriptionPlanTypeAndIsActiveTrue 는
            //   2026-04-14 신설되어 단일 쿼리로 매핑된 등급을 가져온다.
            Grade guaranteedGrade = gradeRepository
                    .findFirstBySubscriptionPlanTypeAndIsActiveTrue(guaranteedType)
                    .orElse(null);

            // ── 2차 fallback: 마스터 데이터의 subscription_plan_type 이 NULL 인 환경 ──
            //   v3.2 이전(2026-04-03 이전) 환경에서 등급이 INSERT 되었고 컬럼만 추가된 채
            //   값이 채워지지 않은 케이스. GradeInitializer 에 NULL 보정 로직을 넣었지만
            //   부팅 순서/장애 시점에 따라 타이밍 갭이 생길 수 있으므로 런타임 안전망을 둔다.
            //   gradeCode 기준으로 직접 조회하여 SILVER/PLATINUM 을 찾는다.
            if (guaranteedGrade == null) {
                guaranteedGrade = gradeRepository.findByGradeCode(fallbackGradeCode).orElse(null);
                if (guaranteedGrade != null) {
                    log.warn("subscription_plan_type 마스터 매핑 누락 — gradeCode 기준 fallback 사용: "
                                    + "planCode={}, type={}, fallbackGradeCode={}",
                            plan.getPlanCode(), guaranteedType, fallbackGradeCode);
                }
            }

            if (guaranteedGrade == null) {
                log.error("구독 보장 등급 마스터 데이터 없음 (1차/2차 모두 실패) — 등급 적용 스킵: "
                                + "planCode={}, type={}, fallbackGradeCode={}",
                        plan.getPlanCode(), guaranteedType, fallbackGradeCode);
                return;
            }

            // 사용자 UserPoint 조회 (FOR UPDATE 비관적 락 — 활동 포인트 적립과 동시 경쟁 방지)
            UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId).orElse(null);
            if (userPoint == null) {
                log.warn("UserPoint 없음 — 구독 등급 적용 스킵: userId={}", userId);
                return;
            }

            // 현재 등급과 보장 등급 비교 (sort_order 기준, 클수록 상위 등급)
            Grade currentGrade = userPoint.getGrade();
            int currentOrder = currentGrade != null && currentGrade.getSortOrder() != null
                    ? currentGrade.getSortOrder() : 0;
            int guaranteedOrder = guaranteedGrade.getSortOrder() != null
                    ? guaranteedGrade.getSortOrder() : 0;

            if (currentOrder >= guaranteedOrder) {
                log.info("현재 등급이 구독 보장 등급보다 높거나 같음 — 강등하지 않음: userId={}, 현재={}, 보장={}",
                        userId,
                        currentGrade != null ? currentGrade.getGradeCode() : "NORMAL",
                        guaranteedGrade.getGradeCode());
                return;
            }

            // 등급 업그레이드 적용
            String previousGradeCode = currentGrade != null ? currentGrade.getGradeCode() : "NORMAL";
            userPoint.updateGrade(guaranteedGrade);
            log.info("구독 등급 보장 적용: userId={}, planCode={}, 등급={} → {}",
                    userId, plan.getPlanCode(), previousGradeCode, guaranteedGrade.getGradeCode());
        } catch (Exception e) {
            // 등급 적용 실패가 구독 자체를 롤백해서는 안 되므로 로그만 남긴다.
            log.error("구독 등급 보장 적용 실패 — 구독은 정상 생성됨 (운영팀 수동 조치 필요): "
                            + "userId={}, planCode={}, error={}",
                    userId, plan.getPlanCode(), e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // 구독 취소
    // ──────────────────────────────────────────────

    /**
     * 구독을 취소한다.
     *
     * <p>사용자가 구독 취소를 요청하면 호출된다.
     * 활성 구독의 상태를 CANCELLED로 변경하고 자동 갱신을 중지한다.
     * 취소 후에도 만료일({@code expiresAt})까지 서비스 이용이 가능하다.</p>
     *
     * <p>내부적으로 {@link UserSubscription#cancel()} 도메인 메서드를 호출하여
     * 상태 전이, 자동 갱신 중지, 취소 시각 기록을 수행한다.</p>
     *
     * @param userId 사용자 ID
     * @throws BusinessException 활성 구독이 없는 경우 (ORDER_NOT_FOUND)
     */
    @Transactional
    public void cancelSubscription(String userId) {
        log.info("구독 취소 시작: userId={}", userId);

        // JOIN FETCH로 plan을 즉시 로딩 (N+1 방지)
        UserSubscription sub = subscriptionRepository
                .findByUserIdAndStatusFetchPlan(userId, UserSubscription.Status.ACTIVE)
                .orElseThrow(() -> {
                    log.error("활성 구독 없음 (취소 실패): userId={}", userId);
                    return new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
                });

        // 도메인 메서드: status=CANCELLED, autoRenew=false, cancelledAt=now
        sub.cancel();

        // ── 구독 취소 시 등급 복원 처리 주의사항 ──
        // 유저는 만료일까지 혜택을 유지하므로 이 시점에는 등급을 **강등하지 않는다**.
        // 실제 강등은 processExpiredSubscriptions() 스케줄러에서 EXPIRED 전환 시
        // earned_by_activity 기반 등급으로 재계산하거나, 별도 배치로 처리해야 한다.
        // (현재 Phase 1 범위를 넘어서므로 이 메서드에서는 상태만 전이한다.)

        log.info("구독 취소 완료 (등급 유지, 만료일까지 혜택 지속): userId={}, subscriptionId={}, expiresAt={}",
                userId, sub.getUserSubscriptionId(), sub.getExpiresAt());
    }

    // ──────────────────────────────────────────────
    // 만료 구독 자동 처리 (스케줄러)
    // ──────────────────────────────────────────────

    /**
     * 만료된 구독을 자동으로 EXPIRED 상태로 변경하는 스케줄러.
     *
     * <p>매일 새벽 2시(서울 시간)에 실행된다.
     * 만료일({@code expiresAt})이 현재 시각보다 이전이면서
     * 자동 갱신이 활성화된({@code autoRenew=true}) ACTIVE 구독을 조회하여
     * EXPIRED 상태로 변경한다.</p>
     *
     * <h4>현재 동작</h4>
     * <p>PG 정기결제(자동 갱신 결제)는 아직 구현되지 않았으므로,
     * 만료일이 지난 모든 활성 구독을 단순히 만료 처리한다.
     * 향후 Toss 빌링키 연동 시 자동 갱신 로직을 추가할 예정이다.</p>
     *
     * <h4>스케줄러 활성화 조건</h4>
     * <p>{@code MonglepickBackendApplication} 클래스에 {@code @EnableScheduling}이
     * 선언되어 있어야 이 스케줄러가 동작한다.</p>
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    @Transactional
    public void processExpiredSubscriptions() {
        log.info("만료 구독 처리 시작 (스케줄러)");

        LocalDateTime now = LocalDateTime.now();
        int totalProcessed = 0;
        int totalErrors = 0;

        // 100건씩 페이징하여 만료 구독 처리 (OOM 방지)
        // 처리 후 상태가 EXPIRED로 변경되므로 항상 page 0을 조회한다.
        //
        // ★ 무한 루프 방지: expire() 실패 시 상태가 ACTIVE로 유지되어
        //   다음 루프에서 동일 레코드가 재조회된다. maxIterations 상한으로 무한 루프를 차단한다.
        //   실패 건은 다음 스케줄러 실행(내일 새벽 2시)에서 재시도된다.
        final int maxIterations = 100; // 최대 100 * 100 = 10,000건 처리 상한
        int iteration = 0;

        Page<UserSubscription> page;
        do {
            page = subscriptionRepository.findExpiredWithPlan(
                    UserSubscription.Status.ACTIVE, now, PageRequest.of(0, 100));

            if (page.isEmpty() && totalProcessed == 0) {
                log.info("만료 대상 구독 없음");
                return;
            }

            int pageErrors = 0;
            for (UserSubscription sub : page.getContent()) {
                try {
                    // TODO: PG 정기결제(빌링키) 연동 시 자동 갱신 시도 로직 추가.
                    log.warn("구독 만료 처리: userId={}, planId={}, expiresAt={}",
                            sub.getUserId(), sub.getPlan().getSubscriptionPlanId(), sub.getExpiresAt());
                    sub.expire();

                    // ★ 2026-04-14 신설: 구독 만료 시 활동 포인트 기반 등급 재계산 (강등 포함)
                    //
                    // 구독 결제 시 createSubscription() 의 applyGuaranteedGradeForSubscription() 으로
                    // SILVER/PLATINUM 을 영구 박아두는 구조이므로, 만료 시점에 활동 기반 자연 등급으로
                    // 되돌리지 않으면 한 달짜리 구독으로 영구 등급 우회가 가능해진다.
                    //
                    // 활동 포인트(earned_by_activity)가 SILVER/PLATINUM 임계값 이상인 사용자는
                    // 자연 등급이 그대로 유지되며, 그 미만이면 자연 등급으로 정상 강등된다.
                    //
                    // 이 호출이 실패해도 만료 처리 자체는 이미 sub.expire() 로 완료된 상태이므로,
                    // 등급 sync 누락만 발생하고 만료 배치는 정상 진행되도록 try/catch 안에서 한 번 더 보호한다.
                    try {
                        pointService.recalculateActivityGrade(sub.getUserId());
                    } catch (Exception gradeErr) {
                        log.error("구독 만료 후 등급 재계산 실패 — 만료는 정상, 등급 sync 만 누락 (운영팀 수동 조치 필요): "
                                        + "userId={}, subscriptionId={}, error={}",
                                sub.getUserId(), sub.getUserSubscriptionId(),
                                gradeErr.getMessage(), gradeErr);
                    }

                    totalProcessed++;
                } catch (Exception e) {
                    totalErrors++;
                    pageErrors++;
                    log.error("구독 만료 처리 실패: subscriptionId={}, error={}",
                            sub.getUserSubscriptionId(), e.getMessage(), e);
                }
            }

            // 현재 페이지의 모든 건이 실패하면 다음 루프에서도 동일 건이 조회되므로 즉시 탈출
            if (pageErrors == page.getNumberOfElements()) {
                log.error("만료 처리 현재 페이지 전건 실패 — 무한 루프 방지 탈출. 실패 {}건은 다음 실행에서 재시도",
                        pageErrors);
                break;
            }

            iteration++;
        } while (page.hasContent() && iteration < maxIterations);

        if (iteration >= maxIterations) {
            log.warn("만료 처리 최대 반복 횟수({}) 도달 — 잔여 건은 다음 실행에서 처리", maxIterations);
        }

        log.info("만료 구독 처리 완료: 성공 {}건, 실패 {}건", totalProcessed, totalErrors);
    }

    // ──────────────────────────────────────────────
    // 구독 연장 (관리자 전용)
    // ──────────────────────────────────────────────

    /**
     * 구독을 1주기 연장한다 (관리자 전용).
     *
     * <p>장애 보상, 프로모션, 수동 재활성화 등 관리자가 특정 구독을 연장할 때 호출한다.
     * 연장 주기는 구독 상품의 {@code periodType}에 따라 자동 결정된다.</p>
     *
     * <h4>연장 주기 계산</h4>
     * <ul>
     *   <li>MONTHLY: expiresAt + 1개월</li>
     *   <li>YEARLY: expiresAt + 1년</li>
     * </ul>
     *
     * <h4>연장 가능 상태</h4>
     * <ul>
     *   <li>ACTIVE — 만료일 연장 후 ACTIVE 유지</li>
     *   <li>CANCELLED — 만료일 연장 후 ACTIVE로 재활성화 (autoRenew는 false 유지)</li>
     *   <li>EXPIRED — 신규 구독을 생성하는 것이 원칙이므로 이 메서드로 처리 불가</li>
     * </ul>
     *
     * @param subscriptionId 연장할 구독 레코드 ID (user_subscriptions.user_subscription_id)
     * @param adminNote      관리자 연장 사유 (감사 로그용, null이면 "관리자 연장"으로 기록)
     * @return 연장 결과 (success, newExpiresAt, message)
     * @throws BusinessException 구독 미발견(SUBSCRIPTION_NOT_FOUND) 또는
     *                           EXPIRED 상태에서 호출(IllegalStateException으로 래핑)한 경우
     */
    @Transactional
    public ExtendSubscriptionResponse extendSubscription(Long subscriptionId, String adminNote) {
        log.info("구독 연장 시작: subscriptionId={}, adminNote={}", subscriptionId, adminNote);

        // 1. 구독 조회 — plan JOIN FETCH로 N+1 방지
        //    UserSubscriptionRepository의 findById()는 plan을 LAZY 로딩하므로,
        //    plan.periodType 접근 전에 명시적으로 즉시 로딩 쿼리를 사용한다.
        UserSubscription sub = subscriptionRepository.findByIdWithPlan(subscriptionId)
                .orElseThrow(() -> {
                    log.error("구독 연장 실패 — 구독 미발견: subscriptionId={}", subscriptionId);
                    return new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
                });

        // 2. 연장 가능 상태 검증 (EXPIRED는 신규 구독 생성으로 처리해야 함)
        if (sub.getStatus() == UserSubscription.Status.EXPIRED) {
            log.error("구독 연장 실패 — EXPIRED 상태 연장 불가: subscriptionId={}, userId={}",
                    subscriptionId, sub.getUserId());
            throw new BusinessException(
                    ErrorCode.SUBSCRIPTION_NOT_FOUND,
                    "만료된 구독은 연장할 수 없습니다. 신규 구독을 생성하세요. subscriptionId=" + subscriptionId
            );
        }

        // 3. 연장 주기 계산 — 구독 상품의 periodType 기준
        //    현재 expiresAt에서 1주기를 더한다.
        //    (현재 시각 기준이 아닌 expiresAt 기준으로 더해야 기존 남은 기간을 보존한다.)
        SubscriptionPlan plan = sub.getPlan();
        LocalDateTime currentExpiresAt = sub.getExpiresAt();
        LocalDateTime newExpiresAt = plan.getPeriodType() == SubscriptionPlan.PeriodType.MONTHLY
                ? currentExpiresAt.plusMonths(1)   // 월간 구독: 1개월 연장
                : currentExpiresAt.plusYears(1);    // 연간 구독: 1년 연장

        // 4. 도메인 메서드로 만료일 갱신 + ACTIVE 상태 복원
        //    UserSubscription.renew()는 expiresAt 설정 + status=ACTIVE 처리를 수행한다.
        //    CANCELLED 상태에서 호출하면 ACTIVE로 재활성화된다 (autoRenew는 false 유지).
        sub.renew(newExpiresAt);

        // 5. 관리자 메모 로그 기록
        //    별도 감사 로그 테이블이 없으므로 INFO 레벨 로그로 기록한다.
        //    향후 AdminAuditLog 엔티티 연동 시 이 위치에 INSERT 호출을 추가한다.
        String note = (adminNote != null && !adminNote.isBlank()) ? adminNote : "관리자 연장";
        log.info("구독 연장 완료: subscriptionId={}, userId={}, planCode={}, "
                        + "이전만료={}, 새만료={}, 관리자메모={}",
                subscriptionId, sub.getUserId(), plan.getPlanCode(),
                currentExpiresAt, newExpiresAt, note);

        // 6. 응답 반환
        String message = String.format("구독이 %s 연장되었습니다. 새 만료일: %s",
                plan.getPeriodType() == SubscriptionPlan.PeriodType.MONTHLY ? "1개월" : "1년",
                newExpiresAt);
        return new ExtendSubscriptionResponse(true, newExpiresAt, message);
    }

    // ──────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────

    /**
     * SubscriptionPlan 엔티티를 SubscriptionPlanResponse DTO로 변환한다.
     *
     * @param plan 구독 상품 엔티티
     * @return 구독 상품 응답 DTO
     */
    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getSubscriptionPlanId(),
                plan.getPlanCode(),
                plan.getName(),
                plan.getPeriodType().name(),
                plan.getPrice(),
                plan.getPointsPerPeriod(),
                plan.getDescription()
        );
    }
}
