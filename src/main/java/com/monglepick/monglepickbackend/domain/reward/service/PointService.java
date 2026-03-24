package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.BalanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.CheckResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.HistoryResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.QuotaDto.QuotaCheckResult;
import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import com.monglepick.monglepickbackend.domain.reward.repository.PointsHistoryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.exception.InsufficientPointException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <h3>등급 체계</h3>
 * <ul>
 *   <li>BRONZE: 누적 0~999 포인트 (maxInputLength: 200자)</li>
 *   <li>SILVER: 누적 1,000~4,999 포인트 (maxInputLength: 500자)</li>
 *   <li>GOLD: 누적 5,000~19,999 포인트 (maxInputLength: 1,000자)</li>
 *   <li>PLATINUM: 누적 20,000+ 포인트 (maxInputLength: 2,000자)</li>
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
     * {@link QuotaService}는 {@link PointsHistoryRepository}만 주입받으므로
     * 순환 참조가 발생하지 않는다.</p>
     */
    private final QuotaService quotaService;

    // ──────────────────────────────────────────────
    // 등급별 상수 정의
    // ──────────────────────────────────────────────

    /** SILVER 등급 승급 기준 누적 포인트 */
    private static final int SILVER_THRESHOLD = 1_000;

    /** GOLD 등급 승급 기준 누적 포인트 */
    private static final int GOLD_THRESHOLD = 5_000;

    /** PLATINUM 등급 승급 기준 누적 포인트 */
    private static final int PLATINUM_THRESHOLD = 20_000;

    // maxInputLength 상수는 Phase R-3에서 QuotaService로 이전됨
    // (application.yml의 app.quota.*.max-input-length에서 관리)

    // ──────────────────────────────────────────────
    // 잔액 확인 (Agent 내부 호출, 락 없음)
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 및 등급별 쿼터를 통합 확인한다.
     *
     * <p>AI Agent가 추천 실행 전에 호출한다. 락 없이 읽기 전용으로 조회하므로
     * 실시간 정확도보다 응답 속도를 우선한다.</p>
     *
     * <h4>Phase R-3 변경: 쿼터 검사 통합</h4>
     * <p>기존에는 잔액만 확인했으나, 이제 등급별 일일/월간 쿼터를 먼저 검사한다.
     * 검사 순서:</p>
     * <ol>
     *   <li>UserPoint 조회 (없으면 잔액 0, BRONZE 등급으로 처리)</li>
     *   <li>{@link QuotaService#checkQuota}로 등급별 일일/월간 한도 확인</li>
     *   <li>쿼터 초과 시 → allowed=false + 쿼터 초과 메시지 반환</li>
     *   <li>쿼터 OK이면 → effectiveCost(무료면 0)로 잔액 확인</li>
     *   <li>잔액 부족 시 → allowed=false + 잔액 부족 메시지 반환</li>
     * </ol>
     *
     * <h4>하위 호환성</h4>
     * <p>Agent의 {@code point_client.py}는 기존 5개 필드(allowed, balance, cost, message,
     * maxInputLength)만 파싱하므로, 추가된 6개 쿼터 필드(dailyUsed, dailyLimit, monthlyUsed,
     * monthlyLimit, freeRemaining, effectiveCost)는 Agent가 무시한다.</p>
     *
     * @param userId 사용자 ID
     * @param cost   필요 포인트 (0이면 잔액 확인 건너뜀)
     * @return 잔액 + 쿼터 통합 확인 결과
     */
    public CheckResponse checkPoint(String userId, int cost) {
        log.debug("포인트 잔액 + 쿼터 확인: userId={}, cost={}", userId, cost);

        // 1. UserPoint 조회 (없으면 잔액 0, BRONZE 등급으로 처리)
        int balance;
        String grade;
        var userPointOpt = userPointRepository.findByUserId(userId);

        if (userPointOpt.isPresent()) {
            UserPoint userPoint = userPointOpt.get();
            balance = userPoint.getPointHave();
            grade = userPoint.getUserGrade() != null ? userPoint.getUserGrade() : "BRONZE";
        } else {
            // 포인트 레코드 미존재: 잔액 0, BRONZE 등급
            log.warn("포인트 레코드 없음: userId={}", userId);
            balance = 0;
            grade = "BRONZE";

            // cost > 0이면 무조건 사용 불가 (레코드 없으므로 쿼터 검사도 의미 없음)
            if (cost > 0) {
                // 쿼터 정보는 BRONZE 기본값으로 채움
                QuotaCheckResult quotaResult = quotaService.checkQuota(userId, grade, cost);
                return new CheckResponse(
                        false,
                        0,
                        cost,
                        "포인트 정보가 없습니다. 회원가입 후 이용해 주세요.",
                        quotaResult.maxInputLength(),
                        quotaResult.dailyUsed(),
                        quotaResult.dailyLimit(),
                        quotaResult.monthlyUsed(),
                        quotaResult.monthlyLimit(),
                        quotaResult.freeRemaining(),
                        quotaResult.effectiveCost()
                );
            }

            // cost == 0이면 항상 allowed=true (쿼터 정보는 기본값)
            QuotaCheckResult quotaResult = quotaService.checkQuota(userId, grade, cost);
            return new CheckResponse(
                    true,
                    0,
                    cost,
                    "포인트가 충분합니다.",
                    quotaResult.maxInputLength(),
                    quotaResult.dailyUsed(),
                    quotaResult.dailyLimit(),
                    quotaResult.monthlyUsed(),
                    quotaResult.monthlyLimit(),
                    quotaResult.freeRemaining(),
                    quotaResult.effectiveCost()
            );
        }

        // 2. 등급별 쿼터 확인 (일일/월간 한도 + 무료 잔여 계산)
        QuotaCheckResult quotaResult = quotaService.checkQuota(userId, grade, cost);

        // 3. 쿼터 초과 시 → 즉시 차단 (잔액 확인 불필요)
        if (!quotaResult.allowed()) {
            log.info("쿼터 초과로 사용 불가: userId={}, grade={}, message={}",
                    userId, grade, quotaResult.message());
            return new CheckResponse(
                    false,
                    balance,
                    cost,
                    quotaResult.message(),
                    quotaResult.maxInputLength(),
                    quotaResult.dailyUsed(),
                    quotaResult.dailyLimit(),
                    quotaResult.monthlyUsed(),
                    quotaResult.monthlyLimit(),
                    quotaResult.freeRemaining(),
                    quotaResult.effectiveCost()
            );
        }

        // 4. 쿼터 OK → effectiveCost로 잔액 확인 (무료면 effectiveCost=0이므로 항상 통과)
        int effectiveCost = quotaResult.effectiveCost();
        boolean balanceSufficient = balance >= effectiveCost;
        String message;

        if (balanceSufficient) {
            // 무료 사용이면 "무료 사용 가능" 메시지, 아니면 "포인트 충분" 메시지
            message = effectiveCost == 0
                    ? "무료 AI 추천을 사용합니다. (남은 무료 횟수: " + quotaResult.freeRemaining() + "회)"
                    : "포인트가 충분합니다.";
        } else {
            // 잔액 부족 (무료가 아닌 경우에만 발생)
            message = "포인트가 부족합니다. 보유: " + balance + ", 필요: " + effectiveCost;
        }

        log.debug("포인트 + 쿼터 확인 결과: userId={}, allowed={}, balance={}, grade={}, " +
                        "effectiveCost={}, dailyUsed={}/{}, freeRemaining={}",
                userId, balanceSufficient, balance, grade,
                effectiveCost, quotaResult.dailyUsed(), quotaResult.dailyLimit(),
                quotaResult.freeRemaining());

        // 5. 통합 CheckResponse 반환 (기존 5개 필드 + Phase R-3 쿼터 6개 필드)
        return new CheckResponse(
                balanceSufficient,
                balance,
                cost,
                message,
                quotaResult.maxInputLength(),
                quotaResult.dailyUsed(),
                quotaResult.dailyLimit(),
                quotaResult.monthlyUsed(),
                quotaResult.monthlyLimit(),
                quotaResult.freeRemaining(),
                effectiveCost
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
        log.info("포인트 차감 시작: userId={}, amount={}, sessionId={}", userId, amount, sessionId);

        // 1. 비관적 락으로 포인트 레코드 조회 (SELECT FOR UPDATE)
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("포인트 레코드 없음 (차감 실패): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 2. 잔액 부족 확인
        if (userPoint.getPointHave() < amount) {
            log.warn("포인트 부족 (차감 실패): userId={}, 보유={}, 필요={}",
                    userId, userPoint.getPointHave(), amount);
            throw new InsufficientPointException(userPoint.getPointHave(), amount);
        }

        // 3. 포인트 차감 (도메인 메서드)
        userPoint.deductPoints(amount);
        int newBalance = userPoint.getPointHave();

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
                userId, amount, newBalance, savedHistory.getPointHistoryId());

        // 5. 응답 반환
        return new DeductResponse(true, newBalance, savedHistory.getPointHistoryId());
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
        log.debug("잔액 조회: userId={}", userId);

        return userPointRepository.findByUserId(userId)
                .map(userPoint -> new BalanceResponse(
                        userPoint.getPointHave(),
                        userPoint.getUserGrade(),
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
     * @param userId     사용자 ID
     * @param freePoints 가입 보너스 포인트 (0이면 레코드만 생성)
     */
    @Transactional
    public void initializePoint(String userId, int freePoints) {
        log.info("포인트 초기화: userId={}, freePoints={}", userId, freePoints);

        // 이미 존재하면 건너뛰기 (멱등성)
        if (userPointRepository.existsByUserId(userId)) {
            log.debug("포인트 레코드 이미 존재 (초기화 건너뜀): userId={}", userId);
            return;
        }

        // UserPoint 레코드 생성
        UserPoint userPoint = UserPoint.builder()
                .userId(userId)
                .pointHave(freePoints)
                .totalEarned(freePoints)
                .dailyEarned(0)
                .dailyReset(LocalDate.now())
                .userGrade("BRONZE")
                .build();
        userPointRepository.save(userPoint);

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
        }

        log.info("포인트 초기화 완료: userId={}, 초기 포인트={}", userId, freePoints);
    }

    // ──────────────────────────────────────────────
    // 포인트 획득 (내부 서비스 간 호출, 비관적 락)
    // ──────────────────────────────────────────────

    /**
     * 포인트를 획득(지급)한다.
     *
     * <p>출석 체크, 퀴즈 보상, 이벤트 보너스 등 포인트를 지급할 때 사용한다.
     * 비관적 락으로 동시 요청을 처리하며, 획득 후 등급 재계산을 수행한다.</p>
     *
     * <h4>등급 계산 기준 (누적 포인트 기반)</h4>
     * <ul>
     *   <li>0 ~ 999: BRONZE</li>
     *   <li>1,000 ~ 4,999: SILVER</li>
     *   <li>5,000 ~ 19,999: GOLD</li>
     *   <li>20,000+: PLATINUM</li>
     * </ul>
     *
     * @param userId      사용자 ID
     * @param amount      획득 포인트 (양수)
     * @param pointType   변동 유형 (earn, bonus 등)
     * @param description 획득 사유 (nullable)
     * @param referenceId 참조 ID (nullable, 이벤트 ID 등)
     * @return 획득 결과 (balanceAfter, grade)
     * @throws BusinessException 포인트 레코드가 없는 경우
     */
    @Transactional
    public EarnResponse earnPoint(String userId, int amount, String pointType,
                                  String description, String referenceId) {
        log.info("포인트 획득 시작: userId={}, amount={}, type={}", userId, amount, pointType);

        // 1. 비관적 락으로 포인트 레코드 조회
        UserPoint userPoint = userPointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> {
                    log.error("포인트 레코드 없음 (획득 실패): userId={}", userId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 2. 포인트 추가 (도메인 메서드: 보유/누적/일일 갱신)
        userPoint.addPoints(amount, LocalDate.now());
        int newBalance = userPoint.getPointHave();

        // 3. 등급 재계산 (누적 포인트 기반)
        String newGrade = calculateGrade(userPoint.getTotalEarned());
        String oldGrade = userPoint.getUserGrade();
        if (!newGrade.equals(oldGrade)) {
            userPoint.updateGrade(newGrade);
            log.info("등급 변경: userId={}, {} → {}", userId, oldGrade, newGrade);
        }

        // 4. 변동 이력 기록
        PointsHistory history = PointsHistory.builder()
                .userId(userId)
                .pointChange(amount)
                .pointAfter(newBalance)
                .pointType(pointType)
                .description(description != null ? description : "포인트 획득")
                .referenceId(referenceId)
                .build();
        pointsHistoryRepository.save(history);

        log.info("포인트 획득 완료: userId={}, 획득={}, 잔액={}, 등급={}",
                userId, amount, newBalance, newGrade);

        // 5. 응답 반환
        return new EarnResponse(newBalance, newGrade);
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
        log.debug("포인트 이력 조회: userId={}, page={}, size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return pointsHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toHistoryResponse);
    }

    // ──────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────

    // calculateMaxInputLength()는 Phase R-3에서 QuotaService.getMaxInputLength()로 이전됨

    /**
     * 누적 포인트 기반으로 등급을 계산한다.
     *
     * <p>등급은 누적 획득 포인트(totalEarned)에 의해 결정되며,
     * 포인트를 사용해도 등급이 하락하지 않는다.</p>
     *
     * @param totalEarned 누적 획득 포인트
     * @return 등급 문자열 (BRONZE, SILVER, GOLD, PLATINUM)
     */
    private String calculateGrade(int totalEarned) {
        if (totalEarned >= PLATINUM_THRESHOLD) {
            return "PLATINUM";
        } else if (totalEarned >= GOLD_THRESHOLD) {
            return "GOLD";
        } else if (totalEarned >= SILVER_THRESHOLD) {
            return "SILVER";
        } else {
            return "BRONZE";
        }
    }

    /**
     * PointsHistory 엔티티를 HistoryResponse DTO로 변환한다.
     *
     * @param history 포인트 이력 엔티티
     * @return DTO 변환 결과
     */
    private HistoryResponse toHistoryResponse(PointsHistory history) {
        return new HistoryResponse(
                history.getPointHistoryId(),
                history.getPointChange(),
                history.getPointAfter(),
                history.getPointType(),
                history.getDescription(),
                history.getCreatedAt()
        );
    }
}
