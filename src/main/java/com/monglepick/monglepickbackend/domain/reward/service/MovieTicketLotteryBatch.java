package com.monglepick.monglepickbackend.domain.reward.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 영화 티켓 추첨 월말 배치 (2026-04-14 신규, 후속 #3).
 *
 * <p>매월 1일 0시(KST) 에 직전 월 회차를 추첨하고 새 회차(현재 월)를 미리 생성한다.
 * {@link com.monglepick.monglepickbackend.MonglepickBackendApplication} 에
 * {@code @EnableScheduling} 이 이미 선언되어 있어 추가 설정 없이 동작한다.</p>
 *
 * <h3>스케줄</h3>
 * <ul>
 *   <li>cron: {@code "0 0 0 1 * *"} — 매월 1일 0시 0분 0초</li>
 *   <li>실행 시점의 LocalDate.now() 가 새 달 1일이므로,
 *       {@link MovieTicketLotteryService#previousCycleYearMonth()} 가 직전 월을 반환한다.</li>
 * </ul>
 *
 * <h3>실패 시 동작</h3>
 * <p>{@code drawLottery} 가 트랜잭션 단위로 실행되므로 중간 실패 시 해당 회차는 PENDING 으로 복귀.
 * 다음 배치 실행 또는 운영자 수동 트리거({@link #manualDraw}) 로 재시도 가능.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MovieTicketLotteryBatch {

    private final MovieTicketLotteryService lotteryService;

    /**
     * 매월 1일 0시 자동 실행 — 직전 월 추첨 + 새 회차 사전 생성.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void runMonthlyDraw() {
        String previous = lotteryService.previousCycleYearMonth();
        String current = lotteryService.currentCycleYearMonth();

        log.info("영화 티켓 추첨 월말 배치 시작: previousCycle={}, currentCycle={}", previous, current);

        try {
            int winners = lotteryService.drawLottery(previous);
            log.info("배치 추첨 완료: cycle={}, winners={}", previous, winners);
        } catch (Exception e) {
            log.error("배치 추첨 실패 (다음 배치/수동 재시도 필요): cycle={}", previous, e);
        }

        try {
            // 새 회차 미리 생성 — 첫 응모자가 들어오기 전에 row 가 존재하도록.
            // getOrCreateLottery 는 멱등이므로 이미 있어도 안전.
            lotteryService.getOrCreateLottery(current);
            log.info("신규 회차 사전 생성/확인 완료: cycle={}", current);
        } catch (Exception e) {
            log.error("신규 회차 사전 생성 실패 (응모 시점에 lazy 생성됨): cycle={}", current, e);
        }
    }

    /**
     * 운영자 수동 추첨 트리거 — 특정 회차를 즉시 추첨.
     *
     * <p>관리자 페이지에서 호출할 수 있도록 public. 배치 실패 또는 강제 추첨 시나리오용.</p>
     *
     * @param cycleYearMonth 추첨할 회차 ('YYYY-MM')
     * @return 당첨자 수
     */
    public int manualDraw(String cycleYearMonth) {
        log.warn("영화 티켓 추첨 수동 트리거: cycle={}", cycleYearMonth);
        return lotteryService.drawLottery(cycleYearMonth);
    }
}
