package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketEntry;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketLottery;
import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;
import com.monglepick.monglepickbackend.domain.reward.repository.MovieTicketEntryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.MovieTicketLotteryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 영화 티켓 추첨 서비스 (2026-04-14 신규, 후속 #3).
 *
 * <p>응모권 사용 시 entry 발급, 매월 1일 추첨 배치 진입점, 회차 자동 생성 등을 담당한다.
 * {@link UserItemService#useItem} 가 APPLY_MOVIE_TICKET 카테고리 아이템 사용 시 본 서비스의
 * {@link #enrollEntry} 를 호출하여 응모 처리한다 (서비스 통합은 다음 PR 단계).</p>
 *
 * <h3>회차 운영 규칙</h3>
 * <ul>
 *   <li><b>회차 단위</b>: 매월 1개 회차 (cycleYearMonth = 'YYYY-MM' 의 현재 월)</li>
 *   <li><b>회차 자동 생성</b>: enroll 시점에 현재 월 회차가 없으면 lazy 생성 (UQ 위반은 catch 후 재조회)</li>
 *   <li><b>추첨 시점</b>: 매월 1일 0시 — 직전 월 회차를 추첨하고 새 회차(현재 월) 생성</li>
 *   <li><b>당첨 정원</b>: lottery.winnerCount (기본 5명)</li>
 *   <li><b>알림</b>: MVP 미구현. 향후 NotificationService 연동 (당첨자에 알림톡/이메일 발송)</li>
 * </ul>
 *
 * <h3>동시성</h3>
 * <ul>
 *   <li>회차 lazy 생성 시 멱등성: UQ 위반 catch 후 findByCycleYearMonth 재조회</li>
 *   <li>추첨 작업: 회차 row 비관적 락 + status 전이 (PENDING → DRAWING → COMPLETED)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MovieTicketLotteryService {

    /** 회차 식별자 포맷 — `YYYY-MM` (java.time.YearMonth.toString() 과 호환) */
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 추첨 무작위성 — 보안 난수 사용 (단순 운 보장 + 예측 불가). */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MovieTicketLotteryRepository lotteryRepository;
    private final MovieTicketEntryRepository entryRepository;

    // ──────────────────────────────────────────────
    // 응모 entry 발급 (UserItemService 에서 호출)
    // ──────────────────────────────────────────────

    /**
     * 응모권 사용 → 현재 월 회차에 entry INSERT.
     *
     * <p>{@link UserItemService#useItem} 가 APPLY_MOVIE_TICKET 카테고리를 사용 처리한 직후 호출.
     * 회차가 존재하지 않으면 lazy 생성. 이미 COMPLETED 회차에는 응모 차단.</p>
     *
     * @param userId   응모자 ID
     * @param userItem 사용된 응모권 보유 레코드 (status=USED 전환 직후)
     * @return 발급된 entry
     * @throws IllegalStateException 현재 월 회차가 이미 COMPLETED 상태 (운영 사고 — 거의 발생하지 않음)
     */
    @Transactional
    public MovieTicketEntry enrollEntry(String userId, UserItem userItem) {
        String currentCycle = LocalDate.now().format(YEAR_MONTH_FORMAT);
        MovieTicketLottery lottery = getOrCreateLottery(currentCycle);

        if (lottery.getStatus() == MovieTicketLotteryStatus.COMPLETED) {
            log.error("응모 실패 — 현재 회차가 이미 COMPLETED: userId={}, cycle={}", userId, currentCycle);
            throw new IllegalStateException("현재 회차의 추첨이 이미 완료되어 응모할 수 없습니다.");
        }

        MovieTicketEntry entry = MovieTicketEntry.builder()
                .userId(userId)
                .userItem(userItem)
                .lottery(lottery)
                .status(MovieTicketEntryStatus.PENDING)
                .build();

        MovieTicketEntry saved = entryRepository.save(entry);
        log.info("영화 티켓 응모 접수: userId={}, entryId={}, lotteryId={}, cycle={}",
                userId, saved.getEntryId(), lottery.getLotteryId(), currentCycle);
        return saved;
    }

    // ──────────────────────────────────────────────
    // 회차 관리
    // ──────────────────────────────────────────────

    /**
     * 회차 lazy 조회/생성 — UQ 위반 시 catch 후 재조회로 멱등 보장.
     *
     * <p>두 트랜잭션이 동시에 신규 회차를 생성하려 하면 한 쪽은 DataIntegrityViolationException 발생.
     * 이를 catch 하여 findByCycleYearMonth 로 재조회하면 안전하게 한 row 만 사용하게 된다.</p>
     */
    @Transactional
    public MovieTicketLottery getOrCreateLottery(String cycleYearMonth) {
        return lotteryRepository.findByCycleYearMonth(cycleYearMonth)
                .orElseGet(() -> createLotterySafely(cycleYearMonth));
    }

    private MovieTicketLottery createLotterySafely(String cycleYearMonth) {
        try {
            MovieTicketLottery created = lotteryRepository.save(MovieTicketLottery.builder()
                    .cycleYearMonth(cycleYearMonth)
                    .status(MovieTicketLotteryStatus.PENDING)
                    .winnerCount(5) // 운영 정책 기본값
                    .build());
            log.info("신규 추첨 회차 생성: cycle={}, lotteryId={}", cycleYearMonth, created.getLotteryId());
            return created;
        } catch (DataIntegrityViolationException e) {
            // 동시 INSERT 경쟁 → 한쪽은 UQ 위반. 다른 트랜잭션이 만든 row 를 재조회.
            log.warn("추첨 회차 동시 생성 경쟁 감지 — 재조회: cycle={}", cycleYearMonth);
            return lotteryRepository.findByCycleYearMonth(cycleYearMonth)
                    .orElseThrow(() -> new IllegalStateException(
                            "회차 동시 생성 후 재조회 실패 (정합성 오류): " + cycleYearMonth));
        }
    }

    // ──────────────────────────────────────────────
    // 추첨 실행 (배치에서 호출)
    // ──────────────────────────────────────────────

    /**
     * 단일 회차 추첨 실행 — 배치가 호출하는 핵심 메서드.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>회차 비관적 락 획득 (status=PENDING 검증)</li>
     *   <li>status=DRAWING 으로 전환</li>
     *   <li>PENDING entry 전체 로드 → SecureRandom shuffle</li>
     *   <li>winnerCount 만큼 WON, 나머지 LOST</li>
     *   <li>status=COMPLETED + drawnAt 기록</li>
     * </ol>
     *
     * <p>중간 실패 시 트랜잭션 롤백 → 회차는 PENDING 으로 복귀 → 다음 배치 재시도 가능.</p>
     *
     * @param cycleYearMonth 추첨할 회차 (예: "2026-03" — 직전 월)
     * @return 당첨자 수 (0이면 entry 없거나 회차 미존재)
     */
    @Transactional
    public int drawLottery(String cycleYearMonth) {
        MovieTicketLottery lottery = lotteryRepository.findByCycleYearMonthWithLock(cycleYearMonth)
                .orElse(null);
        if (lottery == null) {
            log.info("추첨 대상 회차 없음 (스킵): cycle={}", cycleYearMonth);
            return 0;
        }
        if (lottery.getStatus() == MovieTicketLotteryStatus.COMPLETED) {
            log.info("이미 추첨 완료된 회차 (스킵): cycle={}, drawnAt={}",
                    cycleYearMonth, lottery.getDrawnAt());
            return 0;
        }

        // 1) DRAWING 전이 (PENDING 만 허용 — DRAWING 이면 markDrawing 에서 IllegalStateException)
        if (lottery.getStatus() == MovieTicketLotteryStatus.PENDING) {
            lottery.markDrawing();
        }

        // 2) PENDING entry 전체 로드 — MVP 는 in-memory shuffle, 회차당 수만 건 이내 가정
        List<MovieTicketEntry> pending = entryRepository.findPendingByLotteryId(lottery.getLotteryId());
        int total = pending.size();
        if (total == 0) {
            log.info("응모 entry 0건 — 추첨 없이 회차 종료: cycle={}", cycleYearMonth);
            lottery.markCompleted();
            return 0;
        }

        // 3) 무작위 셔플 + 당첨자 절단
        List<MovieTicketEntry> mutable = new ArrayList<>(pending);
        Collections.shuffle(mutable, RANDOM);
        int winnerCount = Math.min(lottery.getWinnerCount() == null ? 0 : lottery.getWinnerCount(), total);

        for (int i = 0; i < total; i++) {
            MovieTicketEntry e = mutable.get(i);
            if (i < winnerCount) {
                e.markWon();
            } else {
                e.markLost();
            }
        }

        // 4) 회차 완료 전이
        lottery.markCompleted();

        log.info("추첨 완료: cycle={}, lotteryId={}, total={}, winners={}",
                cycleYearMonth, lottery.getLotteryId(), total, winnerCount);
        return winnerCount;
    }

    /**
     * 직전 월 yearMonth 문자열 반환 — 배치가 어느 회차를 추첨해야 할지 계산.
     *
     * <p>매월 1일 0시 배치가 호출하는 시점의 LocalDate.now() 가 새 달 1일이므로,
     * 한 달을 빼면 직전 월이 된다.</p>
     */
    public String previousCycleYearMonth() {
        return LocalDate.now().minusMonths(1).format(YEAR_MONTH_FORMAT);
    }

    /**
     * 현재 월 yearMonth 문자열 반환 — 배치가 새 회차를 미리 생성할 때 사용.
     */
    public String currentCycleYearMonth() {
        return LocalDate.now().format(YEAR_MONTH_FORMAT);
    }

    // ──────────────────────────────────────────────
    // 사용자 응모 현황 조회 (UserItemController 와 별개 경로)
    // ──────────────────────────────────────────────

    /**
     * 유저 응모 현황 페이지 조회.
     *
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return entry 페이지 (lottery 정보 fetch 됨)
     */
    public org.springframework.data.domain.Page<MovieTicketEntry> getMyEntries(
            String userId, org.springframework.data.domain.Pageable pageable) {
        return entryRepository.findByUserIdWithLottery(userId, pageable);
    }
}
