package com.monglepick.monglepickbackend.domain.search.service;

import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupMatchDto;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartResponse;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupMatch;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSession;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupStatus;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupMatchRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupSessionRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이상형 월드컵 게임 서비스.
 *
 * <p>세션 시작, 매치 선택, 완주 처리 및 리워드 지급을 담당한다.</p>
 *
 * <h3>라운드 진행 흐름</h3>
 * <pre>
 * 시작(roundSize강) → 매치 선택 반복 → 라운드 완료 → 다음 라운드 매치 생성
 * → ... → 결승(currentRound=2) → 최종 완료(currentRound=1)
 * </pre>
 *
 * <h3>라운드 완료 판정</h3>
 * <p>해당 라운드의 모든 매치({@code winnerMovieId != null})가 완료되면
 * 승자 목록으로 다음 라운드 매치를 자동 생성한다.
 * currentRound == 2인 상태에서 라운드가 완료되면 결승 매치(currentRound=1)를 생성하고,
 * currentRound == 1이 완료되면 세션을 COMPLETED로 전환하고 리워드를 지급한다.</p>
 *
 * <h3>리워드 지급 정책</h3>
 * <ul>
 *   <li>{@code WORLDCUP_COMPLETE} — 매 완주 시 지급 (일일 최대 5회)</li>
 *   <li>{@code WORLDCUP_FIRST}    — 최초 완주 시 1회만 지급 (max_count=1)</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorldcupService {

    /** 월드컵 세션 레포지토리 — 세션 생성/조회 */
    private final WorldcupSessionRepository sessionRepo;

    /** 월드컵 매치 레포지토리 — 매치 생성/조회 */
    private final WorldcupMatchRepository matchRepo;

    /** 리워드 서비스 — 완주 시 포인트 지급 위임 */
    private final RewardService rewardService;

    // ────────────────────────────────────────────────────────────────
    // public 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 월드컵 세션을 시작하고 첫 라운드 매치를 생성한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>candidateMovieIds 크기가 roundSize와 일치하는지 검증한다.</li>
     *   <li>{@link WorldcupSession} 엔티티를 생성하고 저장한다.</li>
     *   <li>첫 라운드(roundSize강) 매치를 pairs로 생성한다:
     *       candidates[0] vs candidates[1], candidates[2] vs candidates[3], ...</li>
     *   <li>생성된 매치 목록을 포함한 {@link WorldcupStartResponse}를 반환한다.</li>
     * </ol>
     *
     * @param userId            사용자 ID
     * @param genreFilter       장르 필터 (nullable)
     * @param roundSize         토너먼트 크기 (16/32/64)
     * @param candidateMovieIds 후보 영화 ID 목록 (roundSize 크기여야 함)
     * @return 세션 ID 및 첫 라운드 매치 목록
     * @throws BusinessException {@link ErrorCode#INVALID_INPUT} candidateMovieIds 크기 불일치 시
     */
    @Transactional
    public WorldcupStartResponse startWorldcup(String userId, String genreFilter,
                                               int roundSize, List<String> candidateMovieIds) {
        // 후보 영화 수가 라운드 크기와 일치하는지 검증
        if (candidateMovieIds == null || candidateMovieIds.size() != roundSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "후보 영화 수(" + (candidateMovieIds == null ? 0 : candidateMovieIds.size())
                            + ")가 라운드 크기(" + roundSize + ")와 일치하지 않습니다");
        }

        // 세션 생성
        WorldcupSession session = WorldcupSession.builder()
                .userId(userId)
                .genreFilter(genreFilter)
                .roundSize(roundSize)
                .currentRound(roundSize)    // 첫 라운드 = 전체 크기 (예: 16강)
                .currentMatchOrder(0)
                .startedAt(LocalDateTime.now())
                .build();
        session = sessionRepo.save(session);
        log.info("월드컵 세션 생성: userId={}, sessionId={}, roundSize={}",
                userId, session.getSessionId(), roundSize);

        // 첫 라운드 매치 생성 (candidates 짝 지어 매치 구성)
        List<WorldcupMatch> matches = createMatchPairs(session, roundSize, candidateMovieIds);
        List<WorldcupMatchDto> matchDtos = matches.stream()
                .map(WorldcupMatchDto::from)
                .collect(Collectors.toList());

        return new WorldcupStartResponse(
                session.getSessionId(),
                roundSize,
                roundSize,
                matchDtos
        );
    }

    /**
     * 매치에서 승자를 선택하고 라운드/게임 완료 여부를 처리한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>세션을 조회한다 (소유자 확인 + IN_PROGRESS 상태 확인).</li>
     *   <li>matchId로 매치를 조회하고 해당 세션 소속인지 확인한다.</li>
     *   <li>winnerMovieId가 movieAId 또는 movieBId인지 검증한다.</li>
     *   <li>{@link WorldcupMatch#selectWinner(String)}를 호출해 승자를 기록한다.</li>
     *   <li>현재 라운드 매치 전체 완료 여부를 확인한다.</li>
     *   <li>라운드 완료 시:
     *     <ul>
     *       <li>currentRound == 1이면 게임 완료 처리 및 리워드 지급.</li>
     *       <li>currentRound > 1이면 다음 라운드 매치를 생성한다.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param userId       사용자 ID
     * @param sessionId    세션 ID
     * @param matchId      선택할 매치 ID
     * @param winnerMovieId 선택한 승자 영화 ID
     * @return 현재 진행 상태와 다음 라운드 매치 목록
     * @throws BusinessException 세션/매치 미발견, 소유권 불일치, 잘못된 승자 ID 시
     */
    @Transactional
    public WorldcupPickResponse pick(String userId, Long sessionId, Long matchId, String winnerMovieId) {
        // ① 세션 조회 — 소유자 확인 + IN_PROGRESS 상태 확인
        WorldcupSession session = sessionRepo
                .findByUserIdAndSessionIdAndStatus(userId, sessionId, WorldcupStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "진행 중인 세션을 찾을 수 없습니다: sessionId=" + sessionId));

        // ② 매치 조회
        WorldcupMatch match = matchRepo.findById(matchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "매치를 찾을 수 없습니다: matchId=" + matchId));

        // ③ 매치가 해당 세션 소속인지 확인
        if (!match.getSession().getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "해당 세션의 매치가 아닙니다: matchId=" + matchId);
        }

        // ④ winnerMovieId가 movieA 또는 movieB인지 검증
        if (!winnerMovieId.equals(match.getMovieAId()) && !winnerMovieId.equals(match.getMovieBId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "승자 영화 ID가 해당 매치의 대결 영화가 아닙니다: winnerMovieId=" + winnerMovieId);
        }

        // ⑤ 승자 기록
        match.selectWinner(winnerMovieId);
        log.debug("매치 선택: sessionId={}, matchId={}, roundNumber={}, winnerMovieId={}",
                sessionId, matchId, match.getRoundNumber(), winnerMovieId);

        // ⑥ 현재 라운드 완료 여부 확인
        int currentRound = session.getCurrentRound();
        List<WorldcupMatch> roundMatches = matchRepo
                .findBySessionSessionIdAndRoundNumber(sessionId, currentRound);

        boolean roundCompleted = roundMatches.stream()
                .allMatch(m -> m.getWinnerMovieId() != null);

        if (!roundCompleted) {
            // 라운드 미완료 — 현재 상태만 반환
            return new WorldcupPickResponse(
                    sessionId, currentRound, false, false, null, Collections.emptyList()
            );
        }

        // ⑦ 라운드 완료 처리
        if (currentRound == 1) {
            // 결승 완료 → 게임 종료
            return handleGameComplete(session, roundMatches);
        } else {
            // 다음 라운드 진입
            return handleNextRound(session, roundMatches);
        }
    }

    /**
     * 특정 사용자의 월드컵 세션 목록을 조회한다 (IN_PROGRESS 상태).
     *
     * @param userId 사용자 ID
     * @return 진행 중인 세션 목록
     */
    public List<WorldcupSession> getMySessions(String userId) {
        return sessionRepo.findByUserIdAndStatus(userId, WorldcupStatus.IN_PROGRESS);
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 후보 영화 ID 목록으로 매치 페어를 생성하고 저장한다.
     *
     * <p>candidates[0] vs candidates[1], candidates[2] vs candidates[3], ...
     * 순서로 매치를 구성한다. matchOrder는 0-based이다.</p>
     *
     * @param session    소속 세션
     * @param roundNumber 라운드 번호
     * @param candidates  대결 영화 ID 목록 (짝수 크기)
     * @return 생성된 WorldcupMatch 엔티티 목록 (matchOrder 오름차순)
     */
    private List<WorldcupMatch> createMatchPairs(WorldcupSession session,
                                                  int roundNumber,
                                                  List<String> candidates) {
        List<WorldcupMatch> matches = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += 2) {
            WorldcupMatch match = WorldcupMatch.builder()
                    .session(session)
                    .roundNumber(roundNumber)
                    .matchOrder(i / 2)          // 0-based 순서
                    .movieAId(candidates.get(i))
                    .movieBId(candidates.get(i + 1))
                    .build();
            matches.add(matchRepo.save(match));
        }
        log.debug("매치 생성 완료: sessionId={}, roundNumber={}, matchCount={}",
                session.getSessionId(), roundNumber, matches.size());
        return matches;
    }

    /**
     * 다음 라운드 매치를 생성하고 응답을 반환한다.
     *
     * <p>현재 라운드 매치의 승자 목록으로 다음 라운드 매치를 생성한다.
     * 세션의 currentRound를 절반으로 줄이고 currentMatchOrder를 0으로 초기화한다.</p>
     *
     * @param session      현재 진행 중인 세션
     * @param roundMatches 현재 라운드의 완료된 매치 목록
     * @return 다음 라운드 매치 목록을 포함한 응답 DTO
     */
    private WorldcupPickResponse handleNextRound(WorldcupSession session,
                                                  List<WorldcupMatch> roundMatches) {
        // 현재 라운드 승자 목록 추출 (matchOrder 오름차순)
        List<String> winners = roundMatches.stream()
                .sorted((a, b) -> Integer.compare(a.getMatchOrder(), b.getMatchOrder()))
                .map(WorldcupMatch::getWinnerMovieId)
                .collect(Collectors.toList());

        // 세션 라운드 전환 (currentRound 절반, matchOrder 초기화)
        session.advanceRound();
        int nextRound = session.getCurrentRound();
        log.info("라운드 전환: sessionId={}, nextRound={}", session.getSessionId(), nextRound);

        // 다음 라운드 매치 생성
        List<WorldcupMatch> nextMatches = createMatchPairs(session, nextRound, winners);
        List<WorldcupMatchDto> nextMatchDtos = nextMatches.stream()
                .map(WorldcupMatchDto::from)
                .collect(Collectors.toList());

        return new WorldcupPickResponse(
                session.getSessionId(),
                nextRound,
                true,
                false,
                null,
                nextMatchDtos
        );
    }

    /**
     * 게임 완료를 처리하고 리워드를 지급한다.
     *
     * <p>결승 매치(currentRound=1)의 승자를 최종 우승 영화로 확정하고,
     * 세션을 COMPLETED 상태로 전환한다. 이후 리워드를 지급한다.</p>
     *
     * <h4>리워드 지급</h4>
     * <ul>
     *   <li>{@code WORLDCUP_COMPLETE} — 매 완주 시 지급 (일일 5회 한도)</li>
     *   <li>{@code WORLDCUP_FIRST}    — 최초 완주 1회만 지급</li>
     * </ul>
     *
     * @param session      완료 처리할 세션
     * @param roundMatches 결승 라운드 매치 목록 (단 1경기)
     * @return 게임 완료 응답 DTO (winnerMovieId 포함, nextMatches 빈 목록)
     */
    private WorldcupPickResponse handleGameComplete(WorldcupSession session,
                                                     List<WorldcupMatch> roundMatches) {
        // 결승 매치 승자 = 최종 우승 영화
        String finalWinner = roundMatches.stream()
                .findFirst()
                .map(WorldcupMatch::getWinnerMovieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "결승 매치 승자가 설정되지 않았습니다"));

        // 세션 완료 처리
        session.complete(finalWinner);
        session.markRewardGranted();
        log.info("월드컵 게임 완료: sessionId={}, userId={}, winnerMovieId={}",
                session.getSessionId(), session.getUserId(), finalWinner);

        // 리워드 지급 — WORLDCUP_COMPLETE (일일 5회 한도)
        rewardService.grantReward(
                session.getUserId(),
                "WORLDCUP_COMPLETE",
                "session_" + session.getSessionId(),
                0
        );

        // 리워드 지급 — WORLDCUP_FIRST (평생 1회, max_count=1이므로 중복 지급 자동 차단)
        rewardService.grantReward(
                session.getUserId(),
                "WORLDCUP_FIRST",
                "worldcup_first",
                0
        );

        return new WorldcupPickResponse(
                session.getSessionId(),
                1,
                true,
                true,
                finalWinner,
                Collections.emptyList()
        );
    }
}
