package com.monglepick.monglepickbackend.domain.search.service;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupMatchDto;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupResultResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartResponse;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupMatch;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupResult;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSession;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupStatus;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupMatchRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupResultRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupSessionRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이상형 월드컵 게임 서비스.
 *
 * <p>세션 시작, 매치 선택, 완주 처리, 결과 조회 및 리워드 지급을 담당한다.</p>
 *
 * <h3>라운드 진행 흐름</h3>
 * <pre>
 * 시작(roundSize강) → 매치 선택 반복 → 라운드 완료 → 다음 라운드 매치 생성
 * → ... → 결승(currentRound=2) → 최종 완료(currentRound=1)
 * </pre>
 *
 * <h3>후보 영화 선택 정책 (v2 — Frontend 호환)</h3>
 * <ul>
 *   <li>candidateMovieIds가 null/empty이면 DB에서 랜덤으로 roundSize개 영화를 선택한다
 *       ({@link MovieRepository#findRandomMovieIdsByGenre(String, int)}).</li>
 *   <li>candidateMovieIds가 제공되면 크기 검증 후 그대로 사용한다.</li>
 * </ul>
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

    /** 월드컵 결과 레포지토리 — 결과 저장/조회 */
    private final WorldcupResultRepository resultRepo;

    /** 영화 레포지토리 — 후보 영화 랜덤 선택 및 결과 조회 시 상세 정보 조회 */
    private final MovieRepository movieRepo;

    /**
     * 월드컵 후보 풀 레포지토리 — 관리자가 큐레이션한 활성 후보 영화 조회.
     *
     * <p>resolveCandidates() 진입 시 활성 후보 풀에서 우선 선택하고,
     * 부족한 경우 기존 movieRepo.findRandomMovieIdsByGenre() fallback.</p>
     */
    private final com.monglepick.monglepickbackend.domain.search.repository.WorldcupCandidateRepository
            worldcupCandidateRepo;

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
     *   <li>candidateMovieIds가 null/empty이면 DB에서 장르 기반으로 랜덤 선택한다.</li>
     *   <li>candidateMovieIds가 제공되면 크기가 roundSize와 일치하는지 검증한다.</li>
     *   <li>{@link WorldcupSession} 엔티티를 생성하고 저장한다.</li>
     *   <li>첫 라운드(roundSize강) 매치를 pairs로 생성한다:
     *       candidates[0] vs candidates[1], candidates[2] vs candidates[3], ...</li>
     *   <li>생성된 매치 목록을 포함한 {@link WorldcupStartResponse}를 반환한다.
     *       {@code gameId}는 {@code sessionId}와 동일한 값 (Frontend 호환).</li>
     * </ol>
     *
     * @param userId            사용자 ID
     * @param genreFilter       장르 필터 (nullable — null이면 전체 장르에서 랜덤 선택)
     * @param roundSize         토너먼트 크기 (8/16/32)
     * @param candidateMovieIds 후보 영화 ID 목록 (optional — null/empty이면 DB에서 랜덤 선택)
     * @return 세션 ID(gameId 포함) 및 첫 라운드 매치 목록
     * @throws BusinessException {@link ErrorCode#INVALID_INPUT} candidateMovieIds 크기 불일치 또는
     *                           DB 조회 결과가 roundSize에 미치지 못할 때
     */
    @Transactional
    public WorldcupStartResponse startWorldcup(String userId, String genreFilter,
                                               int roundSize, List<String> candidateMovieIds) {
        // ① 후보 영화 목록 결정 — null/empty이면 DB에서 랜덤 선택, 제공 시 크기 검증
        List<String> candidates = resolveCandidates(genreFilter, roundSize, candidateMovieIds);

        // ② 세션 생성
        WorldcupSession session = WorldcupSession.builder()
                .userId(userId)
                .category(genreFilter)
                .roundSize(roundSize)
                .currentRound(roundSize)    // 첫 라운드 = 전체 크기 (예: 16강)
                .currentMatchOrder(0)
                .startedAt(LocalDateTime.now())
                .build();
        session = sessionRepo.save(session);
        log.info("월드컵 세션 생성: userId={}, sessionId={}, roundSize={}",
                userId, session.getSessionId(), roundSize);

        // ③ 첫 라운드 매치 생성 (candidates 짝 지어 매치 구성)
        List<WorldcupMatch> matches = createMatchPairs(session, roundSize, candidates);
        List<WorldcupMatchDto> matchDtos = matches.stream()
                .map(WorldcupMatchDto::from)
                .collect(Collectors.toList());

        /* gameId = sessionId 별칭 (Frontend data.gameId 호환) */
        return new WorldcupStartResponse(
                session.getSessionId(),
                session.getSessionId(),   // gameId alias
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
            // 라운드 미완료 — 현재 상태만 반환 (정적 팩토리 메서드 사용)
            return WorldcupPickResponse.inProgress(sessionId, currentRound);
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
     * 특정 사용자의 진행 중인 월드컵 세션 목록을 조회한다 (IN_PROGRESS 상태).
     *
     * @param userId 사용자 ID
     * @return 진행 중인 세션 목록
     */
    public List<WorldcupSession> getMySessions(String userId) {
        return sessionRepo.findByUserIdAndStatus(userId, WorldcupStatus.IN_PROGRESS);
    }

    /**
     * 완료된 월드컵 세션의 결과를 조회한다.
     *
     * <p>Frontend의 {@code GET /api/v1/worldcup/result/{sessionId}} 요청에 응답한다.
     * 세션이 COMPLETED 상태가 아니면 {@link ErrorCode#INVALID_INPUT} 예외를 발생시킨다.</p>
     *
     * <h4>응답 구성</h4>
     * <ol>
     *   <li>sessionId로 WorldcupSession을 조회한다.</li>
     *   <li>세션의 winnerMovieId로 Movie 상세 정보를 조회한다 (조회 실패 시 winner=null).</li>
     *   <li>{@link WorldcupResultResponse}를 구성하여 반환한다 (gameId=sessionId 별칭 포함).</li>
     * </ol>
     *
     * @param sessionId 세션 ID (Frontend의 gameId와 동일한 값)
     * @return 결과 응답 DTO (gameId, sessionId, winnerMovieId, winner 상세, completedAt)
     * @throws BusinessException 세션 미발견 또는 COMPLETED 상태가 아닐 때
     */
    public WorldcupResultResponse getResult(Long sessionId) {
        // 세션 조회
        WorldcupSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "세션을 찾을 수 없습니다: sessionId=" + sessionId));

        // COMPLETED 상태 확인
        if (session.getStatus() != WorldcupStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "완료되지 않은 세션입니다: sessionId=" + sessionId
                            + ", status=" + session.getStatus());
        }

        // 우승 영화 상세 조회 (posterPath, title, releaseYear 포함)
        Movie winnerMovie = null;
        if (session.getWinnerMovieId() != null) {
            winnerMovie = movieRepo.findById(session.getWinnerMovieId()).orElse(null);
            if (winnerMovie == null) {
                log.warn("우승 영화를 찾을 수 없습니다: winnerMovieId={}", session.getWinnerMovieId());
            }
        }

        return WorldcupResultResponse.from(session, winnerMovie);
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 후보 영화 목록을 결정한다.
     *
     * <p>candidateMovieIds가 null 또는 빈 목록이면 DB에서 랜덤으로 roundSize개를 선택한다.
     * candidateMovieIds가 제공되면 크기가 roundSize와 일치하는지 검증 후 반환한다.</p>
     *
     * <h4>DB 랜덤 선택 전략</h4>
     * <ol>
     *   <li>genre 필터로 1차 시도.</li>
     *   <li>결과가 roundSize에 미치지 못하면 genre 제한 해제 후 2차 시도.</li>
     *   <li>그래도 부족하면 {@link ErrorCode#INVALID_INPUT} 예외.</li>
     * </ol>
     *
     * @param genreFilter       장르 필터 (nullable)
     * @param roundSize         토너먼트 크기
     * @param candidateMovieIds Frontend가 전달한 후보 목록 (optional)
     * @return 최종 후보 영화 ID 목록 (roundSize 크기 보장)
     * @throws BusinessException 크기 불일치 또는 DB 조회 결과 부족 시
     */
    private List<String> resolveCandidates(String genreFilter, int roundSize,
                                            List<String> candidateMovieIds) {
        if (candidateMovieIds == null || candidateMovieIds.isEmpty()) {
            // Frontend가 candidateMovieIds를 전달하지 않은 경우 — 후보 풀 우선 사용
            log.info("candidateMovieIds 미제공 — 후보 풀 우선 + DB fallback: genreFilter={}, roundSize={}",
                    genreFilter, roundSize);

            // ① 1차: WorldcupCandidate 활성 풀에서 우선 조회
            //   - genreFilter가 있으면 카테고리로 매칭, 없으면 전체 활성 후보
            //   - 풀 크기가 roundSize 이상이면 셔플 후 앞쪽 roundSize개 사용
            String poolCategory = (genreFilter != null && !genreFilter.isBlank())
                    ? genreFilter : null;
            List<String> poolIds = worldcupCandidateRepo.findActiveMovieIdsByCategoryCode(poolCategory);

            if (poolIds.size() >= roundSize) {
                java.util.List<String> mutable = new java.util.ArrayList<>(poolIds);
                java.util.Collections.shuffle(mutable);
                List<String> selected = mutable.subList(0, roundSize);
                log.info("월드컵 후보 풀 사용 — category={}, poolSize={}, selected={}",
                        poolCategory, poolIds.size(), roundSize);
                return selected;
            }

            // 풀에 후보가 부족하면 카테고리 무시하고 전체 활성 풀 재시도
            if (poolCategory != null) {
                List<String> allPoolIds = worldcupCandidateRepo.findActiveMovieIdsByCategoryCode(null);
                if (allPoolIds.size() >= roundSize) {
                    java.util.List<String> mutable = new java.util.ArrayList<>(allPoolIds);
                    java.util.Collections.shuffle(mutable);
                    List<String> selected = mutable.subList(0, roundSize);
                    log.info("월드컵 후보 풀(전체) 사용 — poolSize={}, selected={}",
                            allPoolIds.size(), roundSize);
                    return selected;
                }
            }

            // ② 2차 fallback: 기존 Movie 랜덤 조회 (후보 풀이 비어 있거나 부족할 때)
            log.info("월드컵 후보 풀 부족({}) — Movie 랜덤 조회로 fallback", poolIds.size());
            String genre = (genreFilter != null && !genreFilter.isBlank()) ? genreFilter : null;
            List<String> randomIds = movieRepo.findRandomMovieIdsByGenre(genre, roundSize);

            if (randomIds.size() < roundSize) {
                // 장르 필터 결과 부족 시 전체 장르로 재시도
                log.warn("장르 필터({})로 충분한 영화({}/{})를 찾지 못함 — 전체 장르로 재시도",
                        genreFilter, randomIds.size(), roundSize);
                randomIds = movieRepo.findRandomMovieIdsByGenre(null, roundSize);
            }

            if (randomIds.size() < roundSize) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "DB에서 후보 영화(" + roundSize + "개)를 충분히 찾을 수 없습니다. "
                                + "poster_path와 rating이 있는 영화가 부족합니다.");
            }

            return randomIds;
        }

        // Frontend가 candidateMovieIds를 전달한 경우 — 크기 검증 후 사용
        if (candidateMovieIds.size() != roundSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "후보 영화 수(" + candidateMovieIds.size()
                            + ")가 라운드 크기(" + roundSize + ")와 일치하지 않습니다");
        }

        return candidateMovieIds;
    }

    /**
     * 월드컵 결과를 WorldcupResult 테이블에 저장한다.
     *
     * <p>세션 완료 직후 호출되며, 실패해도 게임 완료 흐름에 영향을 주지 않도록
     * 예외를 try/catch로 흡수한다.</p>
     *
     * @param session     완료된 세션
     * @param winnerMovieId 최종 우승 영화 ID
     */
    private void saveWorldcupResult(WorldcupSession session, String winnerMovieId) {
        try {
            // userId는 String FK 직접 보관 (JPA/MyBatis 하이브리드 §15.4)
            // 사용자 존재 검증은 세션 시작 시점에 이미 수행됨
            String userId = session.getUserId();
            if (userId == null) {
                log.warn("WorldcupResult 저장 건너뜀 — 세션에 userId 없음: sessionId={}",
                        session.getSessionId());
                return;
            }

            // 우승 영화 엔티티 조회 (FK 용 — Movie는 backend가 DDL 마스터, @ManyToOne 유지)
            Movie winnerMovie = movieRepo.findById(winnerMovieId).orElse(null);
            if (winnerMovie == null) {
                log.warn("WorldcupResult 저장 건너뜀 — 우승 영화 미발견: winnerMovieId={}", winnerMovieId);
                return;
            }

            // 결과 저장 (sessionId, rewardGranted, totalMatches 포함)
            int totalMatches = (session.getRoundSize() - 1); // 16강 = 15경기
            WorldcupResult result = WorldcupResult.builder()
                    .userId(userId)
                    .roundSize(session.getRoundSize())
                    .winnerMovie(winnerMovie)
                    .sessionId(session.getSessionId())
                    .rewardGranted(true)
                    .totalMatches(totalMatches)
                    .onboardingCompleted(false)
                    .build();
            resultRepo.save(result);
            log.info("WorldcupResult 저장 완료: sessionId={}, winnerMovieId={}",
                    session.getSessionId(), winnerMovieId);
        } catch (Exception e) {
            log.warn("WorldcupResult 저장 실패 (무시): sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
        }
    }

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

        /* 라운드 완료 — 정적 팩토리 메서드 사용 (alias 필드 자동 설정) */
        return WorldcupPickResponse.roundComplete(session.getSessionId(), nextRound, nextMatchDtos);
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

        // WorldcupResult INSERT — 결과 이력 보존 (실패해도 게임 완료 응답은 정상 반환)
        saveWorldcupResult(session, finalWinner);

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

        // gameComplete 팩토리 메서드 사용 — isFinished/finalWinner/nextMatch alias 자동 세팅
        return WorldcupPickResponse.gameComplete(session.getSessionId(), finalWinner);
    }
}
