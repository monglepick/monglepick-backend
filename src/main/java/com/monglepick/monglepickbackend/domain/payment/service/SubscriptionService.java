package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.ExtendSubscriptionResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionPlanResponse;
import com.monglepick.monglepickbackend.domain.payment.dto.PaymentDto.SubscriptionStatusResponse;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
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
            return new SubscriptionStatusResponse(false, null, null, null, null, false);
        }

        // 활성 구독이 있는 경우 (plan은 이미 즉시 로딩됨)
        UserSubscription sub = activeSub.get();
        log.debug("활성 구독 발견: userId={}, planId={}, expiresAt={}",
                userId, sub.getPlan().getSubscriptionPlanId(), sub.getExpiresAt());

        return new SubscriptionStatusResponse(
                true,
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

        // 1. 기존 활성 구독 확인 (중복 방지 — PESSIMISTIC_WRITE 비관적 락)
        //
        // 단순 findByUserIdAndStatus() 대신 FOR UPDATE 잠금 쿼리를 사용한다.
        // 동일 사용자가 여러 탭/기기에서 동시에 구독 결제를 완료하면
        // TOCTOU 경쟁 조건으로 두 트랜잭션 모두 "ACTIVE 없음"을 읽고
        // 2개의 ACTIVE 구독이 생성되는 문제가 발생한다.
        // FOR UPDATE 잠금으로 첫 번째 트랜잭션이 커밋될 때까지
        // 후속 트랜잭션의 진입을 DB 레벨에서 차단한다.
        if (subscriptionRepository.findByUserIdAndStatusForUpdate(userId, UserSubscription.Status.ACTIVE).isPresent()) {
            log.warn("중복 구독 시도 차단 (FOR UPDATE 잠금): userId={}", userId);
            throw new BusinessException(ErrorCode.ACTIVE_SUBSCRIPTION_EXISTS);
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
        subscriptionRepository.save(subscription);

        log.info("구독 생성 완료: userId={}, planCode={}, expiresAt={}",
                userId, plan.getPlanCode(), expiresAt);
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

        log.info("구독 취소 완료: userId={}, subscriptionId={}, expiresAt={}",
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
        Page<UserSubscription> page;
        do {
            page = subscriptionRepository.findExpiredWithPlan(
                    UserSubscription.Status.ACTIVE, now, PageRequest.of(0, 100));

            if (page.isEmpty() && totalProcessed == 0) {
                log.info("만료 대상 구독 없음");
                return;
            }

            for (UserSubscription sub : page.getContent()) {
                try {
                    // TODO: PG 정기결제(빌링키) 연동 시 자동 갱신 시도 로직 추가.
                    log.warn("구독 만료 처리: userId={}, planId={}, expiresAt={}",
                            sub.getUserId(), sub.getPlan().getSubscriptionPlanId(), sub.getExpiresAt());
                    sub.expire();
                    totalProcessed++;
                } catch (Exception e) {
                    totalErrors++;
                    log.error("구독 만료 처리 실패: subscriptionId={}, error={}",
                            sub.getUserSubscriptionId(), e.getMessage(), e);
                }
            }
        } while (page.hasContent());

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
