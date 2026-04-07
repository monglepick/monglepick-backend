package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.BalanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.CheckResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.HistoryResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.QuotaDto.QuotaCheckResult;
import com.monglepick.monglepickbackend.domain.reward.entity.Grade;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;

import java.math.BigDecimal;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.exception.InsufficientPointException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 포인트 서비스 — 포인트 잔액 확인, 차감, 획득, 이력 조회 비즈니스 로직.
 *
 * <p>AI Agent(monglepick-agent)와 클라이언트(monglepick-client) 양쪽에서
 * 호출되는 포인트 시스템의 핵심 서비스이다.</p>
 *
 * <h3>동시성 처리</h3>
 * <p>포인트 변경(차감/획득)은 {@code findByUserIdForUpdate()} 비관적 락을 사용하여
 * 동시 요청 시 lost update를 방지한다. 읽기 전용 메서드는 락 없이 조회한다.</p>
 *
 * <h3>등급 체계 (설계서 v2.3 §4.5 — earned_by_activity 기준)</h3>
 * <ul>
 *   <li>NORMAL: 0~499 (일일3, 월간30, 무료0, 200자, 배율×1.0)</li>
 *   <li>BRONZE: 500~1,999 (일일5, 월간80, 무료1, 300자, 배율×1.1)</li>
 *   <li>SILVER: 2,000~4,999 (일일10, 월간200, 무료2, 500자, 배율×1.3)</li>
 *   <li>GOLD: 5,000~14,999 (일일30, 월간600, 무료5, 1000자, 배율×1.5)</li>
 *   <li>PLATINUM: 15,000+ (무제한, 무료10, 2000자, 배율×2.0)</li>
 * </ul>
 *
 * <h3>쿼터 시스템 (Phase R-3)</h3>
 * <p>{@link QuotaService}와 연동하여 등급별 일일/월간 AI 추천 사용 횟수를 제한한다.
 * {@link #checkPoint(String, int)} 메서드가 잔액 확인 전에 쿼터를 먼저 검사하고,
 * 쿼터 내이면 무료 사용 여부에 따라 실제 차감 비용(effectiveCost)을 결정한다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 기본 읽기 전용</li>
 *   <li>변경 메서드: 개별 {@code @Transactional} 오버라이드 — 쓰기 트랜잭션</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    /** 사용자 포인트 리포지토리 (잔액 조회/변경) */
    private final UserPointRepository userPointRepository;

    /** 포인트 변동 이력 리포지토리 (이력 기록/조회) */
    private final PointsHistoryRepository pointsHistoryRepository;

    /**
     * 등급별 쿼터 서비스 (Phase R-3 추가).
     *
     * <p>AI 추천 일일/월간 사용 횟수 제한 및 무료 사용 관리를 담당한다.
     * {@link QuotaService}는 {@link UserAiQuotaRepository}를 직접 주입받으므로
     * 순환 참조가 발생하지 않는다.</p>
     */
    private final QuotaService quotaService;

    /** 등급 마스터 리포지토리 (누적 포인트 기반 등급 조회) */
    private final GradeRepository gradeRepository;

    /**
     * 사용자 AI 쿼터 리포지토리 (v3.3 신규).
     *
     * <p>회원가입 시 user_points와 함께 user_ai_quota 초기 레코드를 생성한다.
     * AI 쿼터 4컬럼(daily_ai_used/monthly_coupon_used/monthly_reset/purchased_ai_tokens)이
     * user_points에서 분리되었으므로, initializePoint()에서 두 테이블에 모두 INSERT해야 한다.</p>
     */
    private final UserAiQuotaRepository userAiQuotaRepository;

    // ──────────────────────────────────────────────
    // 입력 검증 헬퍼
    // ──────────────────────────────────────────────

    /**
     * userId가 null이거나 공백인지 검증한다.
     *
     * <p>Controller의 resolveUserId()에서 1차 검증하지만,
     * 서비스 레이어에서도 방어적으로 검증하여 내부 호출 시 안전성을 보장한다.</p>
     *
     * @param userId 사용자 ID
     * @throws BusinessException userId가 null 또는 공백인 경우
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다");
        }
    }

    // ──────────────────────────────────────────────
    // 잔액 확인 (Agent 내부 호출, 락 없음)
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 및 등급별 쿼터를 통합 확인한다 (v3.0 QuotaCheckResult 연동).
     *
     * <p>AI Agent가 추천 실행 전에 호출한다. 락 없이 읽기 전용으로 조회하므로
     * 실시간 정확도보다 응답 속도를 우선한다.</p>
     *
     * <h4>v3.0 변경사항</h4>
     * <ul>
     *   <li>{@code checkQuota()} 파라미터에서 cost 제거 — QuotaService가 비용을 알 필요 없음</li>
     *   <li>월간 한도 폐지 → monthlyUsed/monthlyLimit/freeRemaining/effectiveCost 필드 제거</li>
     *   <li>AI 무과금 정책 → checkPoint()에서 잔액 검사 불필요 (cost는 응답에만 포함, 항상 0)</li>
     *   <li>source/subBonusRemaining/purchasedRemaining 필드로 소스 정보 전달</li>
     * </ul>
     *
     * <h4>처리 순서</h4>
     * <ol>
     *   <li>UserPoint 조회 (없으면 잔액 0, NORMAL 등급으로 처리)</li>
     *   <li>{@link QuotaService#checkQuota(String, String)}으로 등급별 일일 한도 및 소스 확인</li>
     *   <li>쿼터 차단(BLOCKED) 시 → allowed=false + 안내 메시지 반환</li>
     *   <li>쿼터 허용 시 → allowed=true, source/subBonusRemaining/purchasedRemaining 포함 반환</li>
     * </ol>
     *
     * <h4>하위 호환성</h4>
     * <p>Agent의 {@code point_client.py}는 기존 5개 필드(allowed, balance, cost, message,
     * maxInputLength)만 파싱하므로, v3.0 신규 필드들은 Agent가 무시한다.</p>
     *
     * @param userId 사용자 ID
     * @param cost   필요 포인트 (v3.0 AI 무과금 정책상 항상 0; 응답에 그대로 포함)
     * @return 쿼터 통합 확인 결과 (잔액 정보 포함)
     */
    public CheckResponse checkPoint(String userId, int cost) {
        validateUserId(userId);
        log.debug("포인트 + 쿼터 확인 (v3.0): userId={}, cost={}", userId, cost);

        // 1. UserPoint 조회 (없으면 잔액 0, NORMAL 등급 기본값)
        int balance;
        String grade;
        var userPointOpt = userPointRepository.findByUserId(userId);

        if (userPointOpt.isPresent()) {
            UserPoint userPoint = userPointOpt.get();
            balance = userPoint.getBalance();
            // getGradeCode(): grade FK 참조 null 시 "NORMAL" fallback
            grade = userPoint.getGradeCode();
        } else {
            // 포인트 레코드 미존재 → NORMAL 등급 기본값으로 쿼터만 확인
            log.warn("포인트 레코드 없음 (NORMAL 등급 fallback): userId={}", userId);
            balance = 0;
            grade = "NORMAL";
        }

        // 2. v3.0 쿼터 확인 — cost 파라미터 없음 (QuotaService가 source를 자체 결정)
        QuotaCheckResult quotaResult = quotaService.checkQuota(userId, grade);

        // 3. 쿼터 차단(source="BLOCKED") 시 → 즉시 사용 불가 반환
        if (!quotaResult.allowed()) {
            log.info("쿼터 차단으로 사용 불가: userId={}, grade={}, source={}, message={}",
                    userId, grade, quotaResult.source(), quotaResult.message());
            return new CheckResponse(
                    false,
                    balance,
                    cost,
                    quotaResult.message(),
                    quotaResult.maxInputLength(),
                    quotaResult.dailyUsed(),
                    quotaResult.dailyLimit(),
                    quotaResult.source(),
                    quotaResult.subBonusRemaining(),
                    quotaResult.purchasedRemaining()
            );
        }

        // 4. 쿼터 허용 → AI 무과금 정책상 잔액 검사 없이 즉시 허용
        // v3.0부터 AI 추천은 무과금이므로 effectiveCost 개념이 없음.
        // 소스별 안내 메시지 구성:
        //   GRADE_FREE  → 등급 무료 사용
        //   SUB_BONUS   → 구독 보너스 사용 (잔여 횟수 포함)
        //   PURCHASED   → 구매 토큰 사용 (잔여 횟수 포함)
        String message;
        switch (quotaResult.source()) {
            case "SUB_BONUS" ->
                    message = "구독 보너스로 AI 추천을 사용합니다. (잔여: " + quotaResult.subBonusRemaining() + "회)";
            case "PURCHASED" ->
                    message = "구매 이용권으로 AI 추천을 사용합니다. (잔여: " + quotaResult.purchasedRemaining() + "회)";
            default ->
                    // GRADE_FREE: 등급 무료 한도 내 사용
                    message = "AI 추천을 사용할 수 있습니다. (오늘 " + quotaResult.dailyUsed() + "/" +
                            (quotaResult.dailyLimit() == -1 ? "무제한" : quotaResult.dailyLimit()) + "회)";
        }

        log.debug("쿼터 확인 완료 (v3.0): userId={}, grade={}, source={}, dailyUsed={}/{}",
                userId, grade, quotaResult.source(),
                quotaResult.dailyUsed(), quotaResult.dailyLimit());

        // 5. 통합 CheckResponse 반환 (기존 5+2 필드 + v3.0 소스 3개 필드)
        return new CheckResponse(
                true,
                balance,
                0,      // v3.0 AI 무과금: cost는 항상 0
                message,
                quotaResult.maxInputLength(),
                quotaResult.dailyUsed(),
                quotaResult.dailyLimit(),
                quotaResult.source(),
                quotaResult.subBonusRemaining(),
                quotaResult.purchasedRemaining()
        );
    }

    // ──────────────────────────────────────────────
    // 포인트 차감 (Agent 내부 호출, 비관적 락)
    // ──────────────────────────────────────────────

    /**
     * 포인트를 차감한다.
     *
     * <p>비관적 쓰기 락(SELECT FOR UPDATE)으로 행을 잠근 뒤 차감하므로
     * 동시 요청에도 안전하다. 차감 성공 시 포인트 이력을 기록한다.</p>
     *
     * <h4>예외 상황</h4>
     * <ul>
     *   <li>포인트 레코드 없음 → {@link BusinessException}(POINT_NOT_FOUND)</li>
     *   <li>잔액 부족 → {@link InsufficientPointException}(balance, amount)</li>
     * </ul>
     *
     * @param userId      사용자 ID
     * @param amount      차감 포인트 (양수)
     * @param sessionId   채팅 세션 ID (이력 추적용, nullable)
     * @param description 차감 사유 (nullable)
     * @return 차감 결과 (success, balanceAfter, transactionId)
     * @throws BusinessException           포인트 레코드가 없는 경우
     * @throws InsufficientPointException  잔액 부족 시
     */
    @Transactional
    public DeductResponse deductPoint(String userId, int amount, String sessionId, String description) {
        validateUserId(userId);
        log.info("포인트 차감 시작: userId={}, amount={}, sessionId={}", userId, amount, sessionId);

        // 1. 비관적 락으로 포인트 레코드 조회 (SELECT FOR UPDATE)
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("포인트 레코드 없음 (차감 실패): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 2. 잔액 부족 확인
        if (userPoint.getBalance() < amount) {
            log.warn("포인트 부족 (차감 실패): userId={}, 보유={}, 필요={}",
                    userId, userPoint.getBalance(), amount);
            throw new InsufficientPointException(userPoint.getBalance(), amount);
        }

        // 3. 포인트 차감 (도메인 메서드)
        userPoint.deductPoints(amount);
        int newBalance = userPoint.getBalance();

        // 4. 변동 이력 기록: pointChange=-amount, pointType="spend"
        PointsHistory history = PointsHistory.builder()
                .userId(userId)
                .pointChange(-amount)
                .pointAfter(newBalance)
                .pointType("spend")
                .description(description != null ? description : "포인트 사용")
                .referenceId(sessionId)
                .build();
        PointsHistory savedHistory = pointsHistoryRepository.save(history);

        log.info("포인트 차감 완료: userId={}, 차감={}, 잔액={}, historyId={}",
                userId, amount, newBalance, savedHistory.getPointsHistoryId());

        // 5. 응답 반환
        return new DeductResponse(true, newBalance, savedHistory.getPointsHistoryId());
    }

    // ──────────────────────────────────────────────
    // 잔액 조회 (클라이언트/내부 공용)
    // ──────────────────────────────────────────────

    /**
     * 사용자의 포인트 잔액 정보를 조회한다.
     *
     * <p>포인트 레코드가 없으면 기본값(잔액 0, BRONZE, 누적 0)을 반환한다.
     * 클라이언트의 "내 포인트" 화면이나 헤더 잔액 표시에 사용된다.</p>
     *
     * @param userId 사용자 ID
     * @return 잔액 정보 (balance, grade, totalEarned)
     */
    public BalanceResponse getBalance(String userId) {
        validateUserId(userId);
        log.debug("잔액 조회: userId={}", userId);

        return userPointRepository.findByUserId(userId)
                .map(userPoint -> new BalanceResponse(
                        userPoint.getBalance(),
                        // getGradeCode(): grade FK 참조 null 시 "BRONZE" fallback
                        userPoint.getGradeCode(),
                        userPoint.getTotalEarned()
                ))
                .orElseGet(() -> {
                    log.debug("포인트 레코드 없음 (기본값 반환): userId={}", userId);
                    return new BalanceResponse(0, "BRONZE", 0);
                });
    }

    // ──────────────────────────────────────────────
    // 회원가입 시 포인트 초기화 (멱등)
    // ──────────────────────────────────────────────

    /**
     * 회원가입 시 포인트를 초기화한다.
     *
     * <p>멱등(idempotent) 동작: 이미 포인트 레코드가 존재하면 아무 작업도 하지 않는다.
     * 신규 사용자에게 무료 포인트(freePoints)를 지급하고 "bonus" 이력을 기록한다.</p>
     *
     * <h4>동시성 처리 전략 (2단계)</h4>
     * <ol>
     *   <li><b>1차 방어 (애플리케이션 레벨)</b>: {@code existsByUserId()}로 먼저 확인.
     *       일반적인 순차 호출에서는 여기서 걸러지며 DB 불필요한 INSERT를 방지한다.</li>
     *   <li><b>2차 방어 (DB 레벨)</b>: {@code user_id} 컬럼의 UNIQUE 제약이
     *       동시에 INSERT가 발생하는 TOCTOU(검사-사용 시간차) 경쟁 조건에서 중복을 차단한다.
     *       {@code DataIntegrityViolationException} 발생 시 기존 레코드를 조회하여 반환(멱등).</li>
     * </ol>
     *
     * <h4>트랜잭션 전파 전략</h4>
     * <p>{@code REQUIRES_NEW}를 사용하여 이 메서드를 <b>항상 독립 트랜잭션</b>으로 실행한다.
     * 이유: {@code DataIntegrityViolationException}이 발생하면 Spring이 현재 트랜잭션을
     * rollback-only로 마킹한다. 만약 상위 트랜잭션(예: 회원가입 흐름)에 참여 중이었다면
     * 상위 트랜잭션 전체가 롤백되는 부작용이 발생한다.
     * {@code REQUIRES_NEW}로 분리하면 UK 충돌을 이 메서드 안에서 완전히 처리할 수 있다.</p>
     *
     * @param userId     사용자 ID
     * @param freePoints 가입 보너스 포인트 (0이면 레코드만 생성)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void initializePoint(String userId, int freePoints) {
        validateUserId(userId);
        log.info("포인트 초기화: userId={}, freePoints={}", userId, freePoints);

        // ── 1차 방어: existsByUserId() 조회로 애플리케이션 레벨에서 중복 생성 방지 ──
        // 일반적인 순차 호출에서는 여기서 걸러짐 (DB 왕복 1회, INSERT 불필요)
        if (userPointRepository.existsByUserId(userId)) {
            log.debug("포인트 레코드 이미 존재 (초기화 건너뜀): userId={}", userId);
            return;
        }

        // 신규 UserPoint 레코드 구성 (NORMAL 등급 조회 후 FK 설정 — 설계서 v2.3 기본 등급)
        Grade normalGrade = gradeRepository.findByGradeCode("NORMAL").orElse(null);
        LocalDate today = LocalDate.now();
        // v3.0: monthlyReset 필드 제거됨 (월간 한도 폐지로 UserPoint 엔티티에서 삭제)
        UserPoint userPoint = UserPoint.builder()
                .userId(userId)
                .balance(freePoints)
                .totalEarned(freePoints)
                .dailyEarned(0)
                .dailyReset(today)
                .earnedByActivity(0)
                .dailyCapUsed(0)
                .grade(normalGrade)
                .build();

        try {
            // ── 2차 방어: DB UNIQUE 제약 위반(DataIntegrityViolationException)으로 중복 감지 ──
            // TOCTOU 경쟁 조건: existsByUserId() 통과 직후 다른 스레드가 INSERT를 완료하면
            // DB의 user_id UNIQUE 제약이 이를 차단한다.
            // REQUIRES_NEW 덕분에 이 트랜잭션만 롤백되고 상위 트랜잭션은 영향받지 않는다.
            userPointRepository.save(userPoint);

            // flush()를 명시적으로 호출하여 UK 위반을 save() 시점에 즉시 감지한다.
            // flush 없이는 트랜잭션 커밋 시점까지 DB 왕복이 지연되어
            // catch 블록 진입 전에 메서드가 종료될 수 있다.
            userPointRepository.flush();

            // ── v3.3: user_ai_quota 초기 레코드 동시 생성 ───────────────────────
            // user_points INSERT 성공 직후 user_ai_quota도 INSERT한다.
            // user_points의 UK 위반이 없었음이 확인된 시점이므로 AI 쿼터도 신규 생성 대상.
            // AI 쿼터 UK 위반은 user_points UK 위반과 동일한 catch 블록에서 처리된다.
            if (!userAiQuotaRepository.existsByUserId(userId)) {
                UserAiQuota userAiQuota = UserAiQuota.builder()
                        .userId(userId)
                        // 모든 카운터 초기값 0, 날짜 필드는 null (첫 요청 시 lazy reset)
                        .dailyAiUsed(0)
                        .monthlyCouponUsed(0)
                        .purchasedAiTokens(0)
                        .freeDailyGranted(0)
                        .build();
                userAiQuotaRepository.save(userAiQuota);
                log.debug("AI 쿼터 레코드 초기화 완료: userId={}", userId);
            }

        } catch (DataIntegrityViolationException e) {
            // userId UK 제약 위반 → 다른 스레드/요청이 이미 레코드를 생성한 상태.
            // 기존 레코드가 정상 생성된 것이므로 조용히 반환(멱등 동작).
            // 이 예외는 REQUIRES_NEW 트랜잭션 안에서 처리되므로 상위 트랜잭션에 영향 없음.
            log.info("포인트 초기화 중 중복 생성 감지 — 기존 레코드 정상 존재 (멱등 처리): userId={}, detail={}",
                    userId, e.getMessage());
            return;
        }

        // 가입 보너스 이력 기록 (freePoints > 0인 경우만)
        if (freePoints > 0) {
            PointsHistory history = PointsHistory.builder()
                    .userId(userId)
                    .pointChange(freePoints)
                    .pointAfter(freePoints)
                    .pointType("bonus")
                    .description("회원가입 보너스")
                    .build();
            pointsHistoryRepository.save(history);
            log.debug("가입 보너스 이력 기록: userId={}, freePoints={}", userId, freePoints);
        }

        log.info("포인트 초기화 완료: userId={}, 초기 포인트={}", userId, freePoints);
    }

    // ──────────────────────────────────────────────
    // 포인트 획득 (내부 서비스 간 호출, 비관적 락)
    // ──────────────────────────────────────────────

    /**
     * 포인트를 획득(지급)한다 — 하위 호환용 오버로드 (5-파라미터).
     *
     * <p>결제 충전, 관리자 수동 지급 등 활동 리워드가 아닌 포인트 지급에 사용.
     * {@code isActivityReward=false}로 호출하므로 earnedByActivity에 반영되지 않는다.</p>
     *
     * @param userId      사용자 ID
     * @param amount      획득 포인트 (양수)
     * @param pointType   변동 유형 (earn, bonus 등)
     * @param description 획득 사유 (nullable)
     * @param referenceId 참조 ID (nullable)
     * @return 획득 결과 (balanceAfter, grade)
     */
    @Transactional
    public EarnResponse earnPoint(String userId, int amount, String pointType,
                                  String description, String referenceId) {
        return earnPoint(userId, amount, pointType, description, referenceId, null, false);
    }

    /**
     * 포인트를 획득(지급)한다 — 확장판 (7-파라미터, 설계서 v2.3 §5.2 기준).
     *
     * <p>활동 리워드, 결제 충전, 관리자 지급 등 모든 포인트 획득에 사용하는 핵심 메서드.
     * 비관적 락으로 동시 요청을 처리하며, 획득 후 등급 재계산을 수행한다.</p>
     *
     * <h4>설계서 v2.3 변경사항</h4>
     * <ul>
     *   <li>{@code amount == 0}: user_points/points_history 스킵 (카운팅 전용 활동, 예: AI_CHAT_USE)</li>
     *   <li>{@code isActivityReward=true}: earnedByActivity 증가, dailyCapUsed 증가</li>
     *   <li>등급 산정 기준: {@code earned_by_activity} (total_earned가 아님)</li>
     *   <li>등급 연쇄 승급: 건너뛴 중간 등급도 감지 (old_sort < g.sort <= new_sort)</li>
     * </ul>
     *
     * <h4>등급 계산 기준 (earned_by_activity, 설계서 v2.3 §4.5)</h4>
     * <ul>
     *   <li>NORMAL: 0~499</li>
     *   <li>BRONZE: 500~1,999</li>
     *   <li>SILVER: 2,000~4,999</li>
     *   <li>GOLD: 5,000~14,999</li>
     *   <li>PLATINUM: 15,000+</li>
     * </ul>
     *
     * @param userId           사용자 ID
     * @param amount           획득 포인트 (양수 또는 0, 음수=revoke)
     * @param pointType        변동 유형 (earn, bonus, refund, revoke 등)
     * @param description      획득 사유 (nullable)
     * @param referenceId      참조 ID (nullable)
     * @param actionType       활동 유형 코드 (reward_policy.action_type, nullable — 비리워드 변동이면 null)
     * @param isActivityReward RewardService 경유 여부. true이면 earned_by_activity 반영 (등급 산정 포함).
     *                         false이면 결제 충전/관리자 지급 (등급 미반영).
     * @return 획득 결과 (balanceAfter, grade)
     * @throws BusinessException 포인트 레코드가 없는 경우
     */
    @Transactional
    public EarnResponse earnPoint(String userId, int amount, String pointType,
                                  String description, String referenceId,
                                  String actionType, boolean isActivityReward) {
        validateUserId(userId);
        log.info("포인트 획득 시작: userId={}, amount={}, type={}, actionType={}, isActivity={}",
                userId, amount, pointType, actionType, isActivityReward);

        // ★ 0P 스킵 로직 (설계서 v2.3 §5.2 단계 4)
        // AI_CHAT_USE 등 카운팅 전용 활동: 포인트 변동 없음 → user_points/points_history 스킵.
        // 0P 레코드가 원장에 쌓이면 point_after 체인 검증과 통계 집계에 노이즈 발생.
        if (amount == 0) {
            log.debug("0P 지급 스킵 (카운팅 전용): userId={}, actionType={}", userId, actionType);
            // 현재 잔액/등급만 조회하여 반환 (변경 없음)
            return userPointRepository.findByUserId(userId)
                    .map(up -> new EarnResponse(up.getBalance(), up.getGradeCode()))
                    .orElse(new EarnResponse(0, "NORMAL"));
        }

        // 1. 비관적 락으로 포인트 레코드 조회
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("포인트 레코드 없음 (획득 실패): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 1-1. lazy reset (일일)
        // v3.0: resetMonthlyIfNeeded() 제거 — 월간 한도 폐지로 UserPoint 엔티티에서 해당 메서드 삭제됨
        LocalDate today = LocalDate.now();
        userPoint.resetDailyIfNeeded(today);

        // 1-2. ★ 일일 활동 리워드 상한 검사 (설계서 v2.3 §4.1)
        // isActivityReward=true이고 amount > 0인 경우에만 검사.
        // 구독 포인트/관리자 지급은 상한 미적용.
        if (isActivityReward && amount > 0) {
            Grade currentGrade = userPoint.getGrade();
            if (currentGrade != null && currentGrade.getDailyEarnCap() > 0) {
                int capAfter = userPoint.getDailyCapUsed() + amount;
                if (capAfter > currentGrade.getDailyEarnCap()) {
                    log.info("일일 활동 리워드 상한 도달: userId={}, capUsed={}, amount={}, cap={}",
                            userId, userPoint.getDailyCapUsed(), amount, currentGrade.getDailyEarnCap());
                    return new EarnResponse(userPoint.getBalance(), userPoint.getGradeCode());
                }
            }
        }

        // 2. 포인트 추가 (도메인 메서드: balance/totalEarned/dailyEarned + 활동이면 earnedByActivity/dailyCapUsed)
        userPoint.addPoints(amount, today, isActivityReward);
        int newBalance = userPoint.getBalance();

        // 3. 등급 재계산 (★ earned_by_activity 기준, 설계서 v2.3 §4.5)
        // 결제 포인트(isActivityReward=false)로는 등급이 올라가지 않는다.
        String oldGradeCode = userPoint.getGradeCode();
        Grade newGrade = gradeRepository
                .findTopByMinPointsLessThanEqualAndIsActiveTrueOrderByMinPointsDesc(
                        userPoint.getEarnedByActivity())  // ★ totalEarned → earnedByActivity
                .orElse(null);
        String newGradeCode = (newGrade != null) ? newGrade.getGradeCode() : "NORMAL";
        if (!newGradeCode.equals(oldGradeCode)) {
            userPoint.updateGrade(newGrade);
            log.info("등급 변경: userId={}, {} → {}", userId, oldGradeCode, newGradeCode);
        }

        // 4. 변동 이력 기록 (★ actionType, baseAmount, appliedMultiplier 포함)
        PointsHistory history = PointsHistory.builder()
                .userId(userId)
                .pointChange(amount)
                .pointAfter(newBalance)
                .pointType(pointType)
                .description(description != null ? description : "포인트 획득")
                .referenceId(referenceId)
                .actionType(actionType)
                .build();
        pointsHistoryRepository.save(history);

        log.info("포인트 획득 완료: userId={}, 획득={}, 잔액={}, 등급={}, actionType={}",
                userId, amount, newBalance, newGradeCode, actionType);

        // 5. 응답 반환
        return new EarnResponse(newBalance, newGradeCode);
    }

    // ──────────────────────────────────────────────
    // 변동 이력 조회 (클라이언트 전용)
    // ──────────────────────────────────────────────

    /**
     * 사용자의 포인트 변동 이력을 페이징으로 조회한다.
     *
     * <p>최신 이력이 먼저 표시되며, 클라이언트의 "포인트 내역" 화면에서 사용된다.
     * 엔티티를 HistoryResponse DTO로 매핑하여 반환한다.</p>
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 정보 (page, size)
     * @return 포인트 변동 이력 페이지
     */
    public Page<HistoryResponse> getHistory(String userId, Pageable pageable) {
        validateUserId(userId);
        log.debug("포인트 이력 조회: userId={}, page={}, size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return pointsHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toHistoryResponse);
    }

    // ──────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────

    // 등급 계산은 gradeRepository.findTopByMinPoints...() 쿼리로 전환됨 (grades 마스터 테이블 기반)
    // calculateMaxInputLength()는 Phase R-3에서 QuotaService.getMaxInputLength()로 이전됨

    /**
     * PointsHistory 엔티티를 HistoryResponse DTO로 변환한다.
     *
     * @param history 포인트 이력 엔티티
     * @return DTO 변환 결과
     */
    private HistoryResponse toHistoryResponse(PointsHistory history) {
        return new HistoryResponse(
                history.getPointsHistoryId(),
                history.getPointChange(),
                history.getPointAfter(),
                history.getPointType(),
                history.getDescription(),
                history.getCreatedAt()
        );
    }
}
