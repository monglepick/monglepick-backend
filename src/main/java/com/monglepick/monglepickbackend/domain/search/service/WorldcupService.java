package com.monglepick.monglepickbackend.domain.search.service;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupCategoryOptionResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupMatchDto;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupPickResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupResultResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartOptionsRequest;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartOptionsResponse;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartRequest;
import com.monglepick.monglepickbackend.domain.search.dto.WorldcupStartResponse;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCategory;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupMatch;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupResult;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSession;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSourceType;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupStatus;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCandidateRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCategoryRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupMatchRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupMovieQueryRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupResultRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupSessionRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이상형 월드컵 게임 서비스.
 *
 * <p>카테고리 기반 / 장르 기반 후보 산정, 가능 라운드 계산, 세션 진행을 담당한다.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorldcupService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long MIN_GENRE_VOTE_COUNT = 100L;
    private static final List<Integer> SUPPORTED_ROUND_SIZES = List.of(64, 32, 16, 8);

    private final WorldcupSessionRepository sessionRepo;
    private final WorldcupMatchRepository matchRepo;
    private final WorldcupResultRepository resultRepo;
    private final MovieRepository movieRepo;
    private final WorldcupCandidateRepository worldcupCandidateRepo;
    private final WorldcupCategoryRepository worldcupCategoryRepo;
    private final WorldcupMovieQueryRepository worldcupMovieQueryRepo;
    private final RewardService rewardService;

    /**
     * 사용자에게 노출할 월드컵 카테고리 목록을 반환한다.
     */
    public List<WorldcupCategoryOptionResponse> getAvailableCategories() {
        return worldcupCategoryRepo.findByEnabledTrueOrderByDisplayOrderAscCategoryNameAsc()
                .stream()
                .map(this::toCategoryOptionResponse)
                .toList();
    }

    /**
     * 선택한 시작 조건으로 생성 가능한 라운드 목록을 반환한다.
     */
    public WorldcupStartOptionsResponse getStartOptions(WorldcupStartOptionsRequest request) {
        CandidatePoolInfo poolInfo = resolveCandidatePoolInfo(
                request.sourceType(),
                request.categoryId(),
                request.selectedGenres()
        );

        return new WorldcupStartOptionsResponse(
                poolInfo.sourceType(),
                poolInfo.categoryId(),
                poolInfo.selectedGenres(),
                poolInfo.candidatePoolSize(),
                computeAvailableRoundSizes(poolInfo.candidatePoolSize())
        );
    }

    /**
     * 월드컵 세션을 시작하고 첫 라운드 매치를 생성한다.
     */
    @Transactional
    public WorldcupStartResponse startWorldcup(String userId, WorldcupStartRequest request) {
        validateRoundSize(request.roundSize());
        StartContext startContext = resolveStartContext(request);

        WorldcupSession session = WorldcupSession.builder()
                .userId(userId)
                .sourceType(startContext.sourceType())
                .categoryId(startContext.categoryId())
                .selectedGenresJson(serializeGenres(startContext.selectedGenres()))
                .candidatePoolSize(startContext.candidatePoolSize())
                .roundSize(request.roundSize())
                .currentRound(request.roundSize())
                .currentMatchOrder(0)
                .startedAt(LocalDateTime.now())
                .build();
        session = sessionRepo.save(session);

        List<WorldcupMatch> matches = createMatchPairs(session, request.roundSize(), startContext.candidateMovieIds());
        List<WorldcupMatchDto> matchDtos = matches.stream()
                .map(WorldcupMatchDto::from)
                .collect(Collectors.toList());

        log.info("월드컵 세션 생성: userId={}, sessionId={}, sourceType={}, roundSize={}, poolSize={}",
                userId, session.getSessionId(), startContext.sourceType(), request.roundSize(), startContext.candidatePoolSize());

        return new WorldcupStartResponse(
                session.getSessionId(),
                session.getSessionId(),
                request.roundSize(),
                request.roundSize(),
                matchDtos
        );
    }

    /**
     * 매치에서 승자를 선택하고 라운드/게임 완료 여부를 처리한다.
     */
    @Transactional
    public WorldcupPickResponse pick(String userId, Long sessionId, Long matchId, String winnerMovieId) {
        WorldcupSession session = sessionRepo
                .findByUserIdAndSessionIdAndStatus(userId, sessionId, WorldcupStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "진행 중인 세션을 찾을 수 없습니다: sessionId=" + sessionId));

        WorldcupMatch match = matchRepo.findById(matchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "매치를 찾을 수 없습니다: matchId=" + matchId));

        if (!match.getSession().getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "해당 세션의 매치가 아닙니다: matchId=" + matchId);
        }

        if (!winnerMovieId.equals(match.getMovieAId()) && !winnerMovieId.equals(match.getMovieBId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "승자 영화 ID가 해당 매치의 대결 영화가 아닙니다: winnerMovieId=" + winnerMovieId);
        }

        match.selectWinner(winnerMovieId);
        int currentRound = session.getCurrentRound();
        List<WorldcupMatch> roundMatches = matchRepo.findBySessionSessionIdAndRoundNumber(sessionId, currentRound);

        boolean roundCompleted = roundMatches.stream().allMatch(item -> item.getWinnerMovieId() != null);
        if (!roundCompleted) {
            return WorldcupPickResponse.inProgress(sessionId, currentRound);
        }

        if (currentRound == 2) {
            return handleGameComplete(session, roundMatches);
        }
        return handleNextRound(session, roundMatches);
    }

    public List<WorldcupSession> getMySessions(String userId) {
        return sessionRepo.findByUserIdAndStatus(userId, WorldcupStatus.IN_PROGRESS);
    }

    public WorldcupResultResponse getResult(Long sessionId) {
        WorldcupSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "세션을 찾을 수 없습니다: sessionId=" + sessionId));

        if (session.getStatus() != WorldcupStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "완료되지 않은 세션입니다: sessionId=" + sessionId + ", status=" + session.getStatus());
        }

        Movie winnerMovie = null;
        if (session.getWinnerMovieId() != null) {
            winnerMovie = movieRepo.findById(session.getWinnerMovieId()).orElse(null);
        }
        return WorldcupResultResponse.from(session, winnerMovie);
    }

    private WorldcupCategoryOptionResponse toCategoryOptionResponse(WorldcupCategory category) {
        int candidatePoolSize = Math.toIntExact(
                worldcupCandidateRepo.countByCategoryCategoryIdAndIsActiveTrue(category.getCategoryId())
        );
        return new WorldcupCategoryOptionResponse(
                category.getCategoryId(),
                category.getCategoryCode(),
                category.getCategoryName(),
                category.getDescription(),
                category.getDisplayOrder(),
                candidatePoolSize,
                computeAvailableRoundSizes(candidatePoolSize)
        );
    }

    private StartContext resolveStartContext(WorldcupStartRequest request) {
        CandidatePoolInfo poolInfo = resolveCandidatePoolInfo(
                request.sourceType(),
                request.categoryId(),
                request.selectedGenres()
        );
        List<Integer> availableRoundSizes = computeAvailableRoundSizes(poolInfo.candidatePoolSize());
        if (!availableRoundSizes.contains(request.roundSize())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "선택한 조건으로는 " + request.roundSize() + "강을 시작할 수 없습니다"
            );
        }

        List<String> candidateMovieIds = switch (poolInfo.sourceType()) {
            case CATEGORY -> resolveCategoryCandidateMovieIds(poolInfo.categoryId(), request.roundSize());
            case GENRE -> resolveGenreCandidateMovieIds(poolInfo.selectedGenres(), request.roundSize());
        };

        return new StartContext(
                poolInfo.sourceType(),
                poolInfo.categoryId(),
                poolInfo.selectedGenres(),
                poolInfo.candidatePoolSize(),
                candidateMovieIds
        );
    }

    private CandidatePoolInfo resolveCandidatePoolInfo(
            WorldcupSourceType sourceType,
            Long categoryId,
            List<String> selectedGenres
    ) {
        if (sourceType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월드컵 시작 방식은 필수입니다");
        }

        return switch (sourceType) {
            case CATEGORY -> {
                WorldcupCategory category = worldcupCategoryRepo.findByCategoryIdAndEnabledTrue(categoryId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.WORLDCUP_CATEGORY_NOT_FOUND,
                                "사용 가능한 월드컵 카테고리를 찾을 수 없습니다: categoryId=" + categoryId
                        ));
                int candidatePoolSize = Math.toIntExact(
                        worldcupCandidateRepo.countByCategoryCategoryIdAndIsActiveTrue(category.getCategoryId())
                );
                yield new CandidatePoolInfo(sourceType, category.getCategoryId(), List.of(), candidatePoolSize);
            }
            case GENRE -> {
                List<String> normalizedGenres = normalizeGenres(selectedGenres);
                if (normalizedGenres.isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "장르 기반 월드컵은 최소 1개 장르를 선택해야 합니다");
                }
                int candidatePoolSize = Math.toIntExact(
                        worldcupMovieQueryRepo.countEligibleMovieIdsByGenresAllMatched(normalizedGenres, MIN_GENRE_VOTE_COUNT)
                );
                yield new CandidatePoolInfo(sourceType, null, normalizedGenres, candidatePoolSize);
            }
        };
    }

    private List<String> resolveCategoryCandidateMovieIds(Long categoryId, int roundSize) {
        List<String> poolIds = new ArrayList<>(worldcupCandidateRepo.findActiveMovieIdsByCategoryId(categoryId));
        Collections.shuffle(poolIds);
        if (poolIds.size() < roundSize) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "카테고리 후보 영화가 부족합니다. 요청 라운드=" + roundSize + ", 실제 후보 수=" + poolIds.size()
            );
        }
        return new ArrayList<>(poolIds.subList(0, roundSize));
    }

    private List<String> resolveGenreCandidateMovieIds(List<String> selectedGenres, int roundSize) {
        List<String> candidateMovieIds = worldcupMovieQueryRepo.findRandomEligibleMovieIdsByGenresAllMatched(
                selectedGenres,
                MIN_GENRE_VOTE_COUNT,
                roundSize
        );
        if (candidateMovieIds.size() < roundSize) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "선택한 장르를 모두 만족하는 후보 영화가 부족합니다. 요청 라운드=" + roundSize
                            + ", 실제 후보 수=" + candidateMovieIds.size()
            );
        }
        return candidateMovieIds;
    }

    private List<Integer> computeAvailableRoundSizes(int candidatePoolSize) {
        return SUPPORTED_ROUND_SIZES.stream()
                .filter(roundSize -> candidatePoolSize >= roundSize)
                .toList();
    }

    private void validateRoundSize(int roundSize) {
        if (!SUPPORTED_ROUND_SIZES.contains(roundSize)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 라운드 크기입니다: " + roundSize + ". 지원값=" + SUPPORTED_ROUND_SIZES);
        }
    }

    private List<String> normalizeGenres(List<String> selectedGenres) {
        if (selectedGenres == null || selectedGenres.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String genre : selectedGenres) {
            if (genre == null) {
                continue;
            }
            String trimmed = genre.trim();
            if (!trimmed.isBlank()) {
                deduped.add(trimmed);
            }
        }
        return List.copyOf(deduped);
    }

    private String serializeGenres(List<String> selectedGenres) {
        if (selectedGenres == null || selectedGenres.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(selectedGenres);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "선택 장르 직렬화에 실패했습니다");
        }
    }

    private void saveWorldcupResult(WorldcupSession session, WorldcupMatch finalMatch) {
        try {
            String finalWinner = finalMatch.getWinnerMovieId();
            if (finalWinner == null) {
                return;
            }

            Movie winnerMovie = movieRepo.findById(finalWinner).orElse(null);
            if (winnerMovie == null) {
                log.warn("WorldcupResult 저장 건너뜀 — 우승 영화 미발견: winnerMovieId={}", finalWinner);
                return;
            }

            String runnerUpMovieId = finalWinner.equals(finalMatch.getMovieAId())
                    ? finalMatch.getMovieBId()
                    : finalMatch.getMovieAId();
            Movie runnerUpMovie = null;
            if (runnerUpMovieId != null) {
                runnerUpMovie = movieRepo.findById(runnerUpMovieId).orElse(null);
            }

            int totalMatches = session.getRoundSize() - 1;
            WorldcupResult result = WorldcupResult.builder()
                    .userId(session.getUserId())
                    .roundSize(session.getRoundSize())
                    .winnerMovie(winnerMovie)
                    .runnerUpMovie(runnerUpMovie)
                    .sessionId(session.getSessionId())
                    .rewardGranted(true)
                    .totalMatches(totalMatches)
                    .onboardingCompleted(false)
                    .build();
            resultRepo.save(result);
            log.info("WorldcupResult 저장 완료: sessionId={}, winnerMovieId={}",
                    session.getSessionId(), finalWinner);
        } catch (Exception e) {
            log.warn("WorldcupResult 저장 실패 (무시): sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
        }
    }

    private List<WorldcupMatch> createMatchPairs(WorldcupSession session, int roundNumber, List<String> candidates) {
        if (candidates.size() % 2 != 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "라운드 후보 수는 짝수여야 합니다: size=" + candidates.size());
        }

        List<WorldcupMatch> matches = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += 2) {
            WorldcupMatch match = WorldcupMatch.builder()
                    .session(session)
                    .roundNumber(roundNumber)
                    .matchOrder(i / 2)
                    .movieAId(candidates.get(i))
                    .movieBId(candidates.get(i + 1))
                    .build();
            matches.add(matchRepo.save(match));
        }
        return matches;
    }

    private WorldcupPickResponse handleNextRound(WorldcupSession session, List<WorldcupMatch> roundMatches) {
        List<String> winners = roundMatches.stream()
                .sorted(Comparator.comparingInt(WorldcupMatch::getMatchOrder))
                .map(WorldcupMatch::getWinnerMovieId)
                .collect(Collectors.toList());

        session.advanceRound();
        int nextRound = session.getCurrentRound();
        List<WorldcupMatch> nextMatches = createMatchPairs(session, nextRound, winners);
        List<WorldcupMatchDto> nextMatchDtos = nextMatches.stream()
                .map(WorldcupMatchDto::from)
                .collect(Collectors.toList());

        return WorldcupPickResponse.roundComplete(session.getSessionId(), nextRound, nextMatchDtos);
    }

    private WorldcupPickResponse handleGameComplete(WorldcupSession session, List<WorldcupMatch> roundMatches) {
        WorldcupMatch finalMatch = roundMatches.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "결승 매치를 찾을 수 없습니다"));
        String finalWinner = finalMatch.getWinnerMovieId();
        if (finalWinner == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결승 매치 승자가 설정되지 않았습니다");
        }

        session.complete(finalWinner);
        session.markRewardGranted();
        saveWorldcupResult(session, finalMatch);

        rewardService.grantReward(
                session.getUserId(),
                "WORLDCUP_COMPLETE",
                "session_" + session.getSessionId(),
                0
        );
        rewardService.grantReward(
                session.getUserId(),
                "WORLDCUP_FIRST",
                "worldcup_first",
                0
        );

        log.info("월드컵 게임 완료: sessionId={}, userId={}, winnerMovieId={}",
                session.getSessionId(), session.getUserId(), finalWinner);
        return WorldcupPickResponse.gameComplete(session.getSessionId(), finalWinner);
    }

    private record CandidatePoolInfo(
            WorldcupSourceType sourceType,
            Long categoryId,
            List<String> selectedGenres,
            int candidatePoolSize
    ) {}

    private record StartContext(
            WorldcupSourceType sourceType,
            Long categoryId,
            List<String> selectedGenres,
            int candidatePoolSize,
            List<String> candidateMovieIds
    ) {}
}
