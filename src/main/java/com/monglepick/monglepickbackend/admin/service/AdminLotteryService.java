package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.CreateLotteryRequest;
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
import org.springframework.transaction.annotation.Propagation;
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
     * <h3>트랜잭션 주의 (2026-04-29 버그 픽스)</h3>
     * <p>본 메서드를 클래스 레벨 {@code @Transactional(readOnly = true)} 만 적용 상태로 두면,
     * 내부 호출되는 {@link MovieTicketLotteryService#drawLottery} 가 REQUIRED 전파로 외곽 readOnly
     * 트랜잭션에 join 되어 {@code findByCycleYearMonthWithLock} 의 PESSIMISTIC_WRITE 락 획득에서
     * 실패한다 (read-only 트랜잭션 내 SELECT … FOR UPDATE 불가). 이로 인해 운영 화면에서 [수동 추첨]
     * 버튼이 항상 500 으로 떨어졌다.</p>
     * <p>{@link Propagation#NOT_SUPPORTED} 로 외곽 트랜잭션을 일시 중단시켜, drawLottery 가 자체
     * 쓰기 트랜잭션(REQUIRED)을 새로 시작하도록 보장한다. 사전/사후 read 는 각 호출이 자체
     * (auto-commit) 트랜잭션으로 처리되므로 정합성 영향 없음.</p>
     *
     * @param lotteryId 회차 PK
     * @return 추첨 결과 응답 (당첨자 수 + 최종 상태)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ManualDrawResponse manualDraw(Long lotteryId) {
        MovieTicketLottery lottery = findLotteryOrThrow(lotteryId);

        if (lottery.getStatus() == MovieTicketLotteryStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.LOTTERY_INVALID_STATE,
                    "이미 추첨이 완료된 회차입니다 (drawnAt=" + lottery.getDrawnAt() + ")");
        }

        int drawnCount = lotteryService.drawLottery(lottery.getCycleYearMonth());
        log.warn("[AdminLottery] 수동 추첨 실행 — lotteryId={}, cycle={}, drawnCount={}",
                lotteryId, lottery.getCycleYearMonth(), drawnCount);

        /* drawLottery 가 별도(쓰기) 트랜잭션에서 커밋된 후 — 최신 상태 재조회 */
        MovieTicketLottery refreshed = findLotteryOrThrow(lotteryId);
        return new ManualDrawResponse(
                refreshed.getLotteryId(),
                refreshed.getCycleYearMonth(),
                drawnCount,
                refreshed.getStatus()
        );
    }

    /**
     * DRAWING 상태 회차 강제 PENDING 복구 (2026-04-29 신규).
     *
     * <p>운영 사고로 회차가 DRAWING 상태에서 멈춘 경우(드물지만 발생 가능 — JVM crash, DB 단절 등)
     * 운영자가 PENDING 으로 되돌려 다시 추첨할 수 있도록 한다. PENDING/COMPLETED 회차에는
     * LTR002 로 거부.</p>
     *
     * @param lotteryId 회차 PK
     * @return 복구 후 회차 요약
     */
    @Transactional
    public LotterySummary resetDrawingToPending(Long lotteryId) {
        MovieTicketLottery lottery = findLotteryOrThrow(lotteryId);
        if (lottery.getStatus() != MovieTicketLotteryStatus.DRAWING) {
            throw new BusinessException(ErrorCode.LOTTERY_INVALID_STATE,
                    "DRAWING 상태에서만 PENDING 으로 되돌릴 수 있습니다 (현재: " + lottery.getStatus() + ")");
        }
        lottery.markPending();
        log.warn("[AdminLottery] 회차 강제 복구(DRAWING → PENDING) — lotteryId={}, cycle={}",
                lotteryId, lottery.getCycleYearMonth());
        return toSummaryWithStats(lottery);
    }

    /**
     * 회차 강제 생성 (2026-04-29 신규).
     *
     * <p>자동 lazy 생성이 정상 동작하지만, 운영자가 미래 회차/특수 회차를 사전 생성하고 싶을 때 사용.
     * 이미 동일 cycleYearMonth 가 존재하면 LTR002 (CONFLICT 의미로) 던진다 — UQ 위반 사전 차단.</p>
     *
     * @param request 회차 생성 요청 (cycleYearMonth, winnerCount)
     * @return 생성된 회차 요약
     */
    @Transactional
    public LotterySummary createCycle(CreateLotteryRequest request) {
        validateCycleFormat(request.cycleYearMonth());

        if (lotteryRepository.findByCycleYearMonth(request.cycleYearMonth()).isPresent()) {
            throw new BusinessException(ErrorCode.LOTTERY_INVALID_STATE,
                    "이미 존재하는 회차입니다: " + request.cycleYearMonth());
        }

        MovieTicketLottery created = lotteryRepository.save(
                MovieTicketLottery.builder()
                        .cycleYearMonth(request.cycleYearMonth())
                        .status(MovieTicketLotteryStatus.PENDING)
                        .winnerCount(request.winnerCount() != null ? request.winnerCount() : 5)
                        .build()
        );
        log.info("[AdminLottery] 회차 강제 생성 — lotteryId={}, cycle={}, winnerCount={}",
                created.getLotteryId(), created.getCycleYearMonth(), created.getWinnerCount());
        return toSummaryWithStats(created);
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
     * <p>2026-04-29 보강: {@code keyword} 파라미터 추가 — 닉네임/이메일/userId 부분 일치 검색을
     * 페이지 결과에 대해 in-memory 필터로 적용한다 (회차당 entry 수가 보통 수만 건 이내라 문제 없음).</p>
     *
     * @param lotteryId 회차 PK
     * @param status    nullable. PENDING/WON/LOST 필터
     * @param keyword   nullable. 닉네임/이메일/userId 부분 일치 (대소문자 무시)
     * @param pageable  페이징
     * @return 응모자 항목 페이지
     */
    public Page<LotteryEntryItem> getEntries(Long lotteryId,
                                             MovieTicketEntryStatus status,
                                             String keyword,
                                             Pageable pageable) {
        /* 회차 존재 검증 — 잘못된 lotteryId 에 대한 응답을 명확히 하기 위함 */
        findLotteryOrThrow(lotteryId);

        Page<MovieTicketEntry> page = entryRepository.findByLotteryIdForAdmin(lotteryId, status, pageable);
        Map<String, User> userCache = new HashMap<>();
        Page<LotteryEntryItem> items = page.map(entry -> {
            User user = userCache.computeIfAbsent(entry.getUserId(), userMapper::findById);
            String nickname = user != null ? user.getNickname() : null;
            String email = user != null ? user.getEmail() : null;
            return LotteryEntryItem.from(entry, nickname, email);
        });

        /* 키워드 필터 — null/blank 면 그대로 반환 */
        if (keyword == null || keyword.isBlank()) {
            return items;
        }
        String needle = keyword.trim().toLowerCase();
        List<LotteryEntryItem> filtered = items.stream()
                .filter(it -> matches(it.nickname(), needle)
                        || matches(it.email(), needle)
                        || matches(it.userId(), needle))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                filtered, pageable, filtered.size());
    }

    /**
     * 회차별 응모자 전체 export — CSV 다운로드용 (2026-04-29 신규).
     *
     * <p>관리자 화면에서 당첨자 명단을 외부 채널(이메일/알림톡)로 발송할 때 사용.
     * 페이징 없이 전체 entry 를 반환하므로 회차당 entry 수가 매우 큰 경우 메모리 사용에 주의.
     * status 필터가 권장(보통 status=WON 으로 당첨자만 export).</p>
     *
     * @param lotteryId 회차 PK
     * @param status    nullable. 보통 WON 만 export
     * @return 전체 응모자 항목 리스트 (시간 역순)
     */
    public List<LotteryEntryItem> getAllEntriesForExport(Long lotteryId,
                                                        MovieTicketEntryStatus status) {
        findLotteryOrThrow(lotteryId);
        org.springframework.data.domain.Sort sort =
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "createdAt");
        Pageable allInOne = org.springframework.data.domain.PageRequest.of(0, 10_000, sort);
        Page<MovieTicketEntry> page = entryRepository.findByLotteryIdForAdmin(lotteryId, status, allInOne);

        Map<String, User> userCache = new HashMap<>();
        return page.getContent().stream()
                .map(entry -> {
                    User user = userCache.computeIfAbsent(entry.getUserId(), userMapper::findById);
                    String nickname = user != null ? user.getNickname() : null;
                    String email = user != null ? user.getEmail() : null;
                    return LotteryEntryItem.from(entry, nickname, email);
                })
                .toList();
    }

    /** 부분 일치 검사 — null safe, 대소문자 무시. */
    private boolean matches(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase().contains(needleLower);
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
    private void validateCycleFormat(String cycleYearMonth) {
        if (cycleYearMonth == null || !CYCLE_YEAR_MONTH_PATTERN.matcher(cycleYearMonth).matches()) {
            throw new BusinessException(ErrorCode.LOTTERY_INVALID_CYCLE_FORMAT);
        }
    }
}
