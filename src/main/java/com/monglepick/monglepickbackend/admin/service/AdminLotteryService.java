package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.LotteryEntryItem;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.LotterySummary;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.LotteryUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.ManualDrawResponse;
import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketEntry;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketLottery;
import com.monglepick.monglepickbackend.domain.reward.repository.MovieTicketEntryRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.MovieTicketLotteryRepository;
import com.monglepick.monglepickbackend.domain.reward.service.MovieTicketLotteryService;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 관리자 — 영화 티켓 추첨 관리 서비스 (2026-04-28 신규).
 *
 * <p>관리자 페이지 "결제/포인트 → 추첨 관리" 서브탭의 5개 EP 비즈니스 로직을 담당한다.
 * 도메인 {@link MovieTicketLotteryService} 를 재사용하며, 회차별 통계 집계와 사용자 식별 정보
 * lookup 등 관리자 전용 보강 로직만 본 서비스에서 처리한다.</p>
 *
 * <h3>담당 기능 (5 EP)</h3>
 * <ul>
 *   <li>회차 목록 페이징 조회</li>
 *   <li>회차 상세 (단건 통계 포함)</li>
 *   <li>회차 수정 (winner_count / notes)</li>
 *   <li>수동 추첨 트리거 (PENDING → COMPLETED)</li>
 *   <li>회차별 응모자 페이징 조회</li>
 * </ul>
 *
 * <h3>트랜잭션</h3>
 * <p>클래스 레벨 {@code @Transactional(readOnly = true)} 로 기본 설정.
 * 쓰기 메서드(updateLottery, manualDraw)는 도메인 레이어가 자체 {@code @Transactional} 을
 * 갖고 있어 새 트랜잭션이 시작된다. updateLottery 만 본 클래스에서 직접 쓰기 트랜잭션을 연다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLotteryService {

    /** 회차 식별자 형식 검증 — `YYYY-MM` (예: 2026-04). */
    private static final Pattern CYCLE_YEAR_MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private final MovieTicketLotteryRepository lotteryRepository;
    private final MovieTicketEntryRepository entryRepository;
    private final MovieTicketLotteryService lotteryService;
    private final UserMapper userMapper;

    // ──────────────────────────────────────────────
    // 회차 목록 / 상세
    // ──────────────────────────────────────────────

    /**
     * 회차 페이징 조회 (관리자 추첨 관리 화면).
     *
     * <p>각 회차마다 entry 통계(WON/LOST/총)를 집계해 응답한다.
     * 회차 수가 많지 않은 운영 특성상(매월 1건) N+1 영향이 미미해 단순 루프로 처리한다.</p>
     *
     * @param status   상태 필터 (null 이면 전체)
     * @param pageable 페이징 (정렬은 보통 cycleYearMonth DESC)
     * @return 회차 요약 페이지
     */
    public Page<LotterySummary> getLotteries(MovieTicketLotteryStatus status, Pageable pageable) {
        Page<MovieTicketLottery> page = lotteryRepository.findAllForAdmin(status, pageable);
        return page.map(this::toSummaryWithStats);
    }

    /**
     * 회차 단건 상세 조회.
     *
     * @param lotteryId 회차 PK
     * @return 회차 요약 (통계 포함)
     * @throws BusinessException 회차 미존재 시 LOTTERY_NOT_FOUND
     */
    public LotterySummary getLottery(Long lotteryId) {
        MovieTicketLottery lottery = findLotteryOrThrow(lotteryId);
        return toSummaryWithStats(lottery);
    }

    // ──────────────────────────────────────────────
    // 회차 수정 / 수동 추첨
    // ──────────────────────────────────────────────

    /**
     * 회차 수정 — winner_count / notes 부분 업데이트.
     *
     * <p>두 필드 모두 nullable. null 인 필드는 무시한다 (PATCH 시멘틱).
     * COMPLETED 회차의 winner_count 변경은 도메인 메서드에서 차단된다 (이미 추첨 완료).</p>
     *
     * @param lotteryId 회차 PK
     * @param request   수정 요청
     * @return 수정 후 회차 요약
     */
    @Transactional
    public LotterySummary updateLottery(Long lotteryId, LotteryUpdateRequest request) {
        MovieTicketLottery lottery = findLotteryOrThrow(lotteryId);

        if (request.winnerCount() != null) {
            try {
                lottery.changeWinnerCount(request.winnerCount());
            } catch (IllegalStateException e) {
                /* COMPLETED 회차 변경 시도 — 도메인 메서드가 던지는 IllegalState 를 LTR002 로 매핑 */
                throw new BusinessException(ErrorCode.LOTTERY_INVALID_STATE, e.getMessage());
            }
        }
        if (request.notes() != null) {
            lottery.changeNotes(request.notes());
        }
        log.info("[AdminLottery] 회차 수정 완료 — lotteryId={}, winnerCount={}, notes 갱신여부={}",
                lotteryId, request.winnerCount(), request.notes() != null);

        return toSummaryWithStats(lottery);
    }

    /**
     * 수동 추첨 트리거.
     *
     * <p>도메인 {@link MovieTicketLotteryService#drawLottery} 를 호출한다.
     * COMPLETED 회차는 도메인 측에서 스킵 처리되어 0 을 반환하므로,
     * 본 서비스에서 사전 검증으로 LTR002 를 던져 운영자에게 명확한 메시지를 노출한다.</p>
     *
     * @param lotteryId 회차 PK
     * @return 추첨 결과 응답 (당첨자 수 + 최종 상태)
     */
    public ManualDrawResponse manualDraw(Long lotteryId) {
        MovieTicketLottery lottery = findLotteryOrThrow(lotteryId);

        if (lottery.getStatus() == MovieTicketLotteryStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.LOTTERY_INVALID_STATE,
                    "이미 추첨이 완료된 회차입니다 (drawnAt=" + lottery.getDrawnAt() + ")");
        }

        int drawnCount = lotteryService.drawLottery(lottery.getCycleYearMonth());
        log.warn("[AdminLottery] 수동 추첨 실행 — lotteryId={}, cycle={}, drawnCount={}",
                lotteryId, lottery.getCycleYearMonth(), drawnCount);

        /* drawLottery 는 별도 트랜잭션이라 lottery 객체 상태가 stale 하므로 재조회한다 */
        MovieTicketLottery refreshed = findLotteryOrThrow(lotteryId);
        return new ManualDrawResponse(
                refreshed.getLotteryId(),
                refreshed.getCycleYearMonth(),
                drawnCount,
                refreshed.getStatus()
        );
    }

    // ──────────────────────────────────────────────
    // 응모자 명단
    // ──────────────────────────────────────────────

    /**
     * 회차별 응모자 페이징 조회.
     *
     * <p>userId 별로 닉네임·이메일을 lookup 하여 운영자 식별 가능하게 노출한다.
     * 페이지 단위로 lookup 하므로 동일 페이지 내 중복 userId 는 캐시한다.</p>
     *
     * @param lotteryId 회차 PK
     * @param status    nullable. PENDING/WON/LOST 필터
     * @param pageable  페이징
     * @return 응모자 항목 페이지
     */
    public Page<LotteryEntryItem> getEntries(Long lotteryId,
                                             MovieTicketEntryStatus status,
                                             Pageable pageable) {
        /* 회차 존재 검증 — 잘못된 lotteryId 에 대한 응답을 명확히 하기 위함 */
        findLotteryOrThrow(lotteryId);

        Page<MovieTicketEntry> page = entryRepository.findByLotteryIdForAdmin(lotteryId, status, pageable);
        Map<String, User> userCache = new HashMap<>();
        return page.map(entry -> {
            User user = userCache.computeIfAbsent(entry.getUserId(), userMapper::findById);
            String nickname = user != null ? user.getNickname() : null;
            String email = user != null ? user.getEmail() : null;
            return LotteryEntryItem.from(entry, nickname, email);
        });
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    /**
     * 회차 단건 조회 헬퍼 — 미존재 시 LTR001 던짐.
     */
    private MovieTicketLottery findLotteryOrThrow(Long lotteryId) {
        return lotteryRepository.findById(lotteryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOTTERY_NOT_FOUND));
    }

    /**
     * 회차 entry 통계(WON/LOST/총) 를 집계해 LotterySummary 로 변환한다.
     */
    private LotterySummary toSummaryWithStats(MovieTicketLottery lottery) {
        long won = entryRepository.countByLotteryAndStatus(
                lottery.getLotteryId(), MovieTicketEntryStatus.WON);
        long lost = entryRepository.countByLotteryAndStatus(
                lottery.getLotteryId(), MovieTicketEntryStatus.LOST);
        long pending = entryRepository.countByLotteryAndStatus(
                lottery.getLotteryId(), MovieTicketEntryStatus.PENDING);
        long total = won + lost + pending;
        return LotterySummary.from(lottery, total, won, lost);
    }

    /**
     * cycleYearMonth 형식 검증 — 'YYYY-MM' 만 허용.
     *
     * <p>현재 EP 에서는 lotteryId 기반이라 직접 사용하지 않으나, 후속 EP("회차 강제 생성") 도입 시
     * 사용할 수 있도록 미리 정의해 둔다.</p>
     *
     * @throws BusinessException 형식 위반 시 LTR004
     */
    @SuppressWarnings("unused") // 후속 EP 에서 사용 예정
    private void validateCycleFormat(String cycleYearMonth) {
        if (cycleYearMonth == null || !CYCLE_YEAR_MONTH_PATTERN.matcher(cycleYearMonth).matches()) {
            throw new BusinessException(ErrorCode.LOTTERY_INVALID_CYCLE_FORMAT);
        }
    }
}
