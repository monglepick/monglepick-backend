package com.monglepick.monglepickbackend.domain.recommendation.service;

import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationLogBatchDto.SaveBatchRequest;
import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationLogBatchDto.SaveBatchResponse;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 추천 로그 배치 저장 서비스 (2026-04-15 신규).
 *
 * <p>AI Agent 가 {@code movie_card} 이벤트를 발행하는 시점에 추천된 영화 N 개를
 * {@code recommendation_log} 테이블에 한 번에 INSERT 한다. 본 서비스가 생기기 전에는
 * Agent → Backend 저장 경로가 아예 없어서 유저 대면 "마이픽 > 추천 내역" 페이지와
 * 관리자 "AI 추천 분석" 탭이 모두 빈 화면으로 나왔다.</p>
 *
 * <h3>책임 경계</h3>
 * <ul>
 *   <li>본 서비스: <b>쓰기 전용</b> (Agent 내부 호출 경로). ServiceKey 인증 경유.</li>
 *   <li>{@link RecommendationHistoryService}: <b>유저 대면 조회</b>. 저장된 로그를
 *       RecommendationImpact 의 찜/봤어요 상태와 병합해 반환.</li>
 *   <li>{@link RecommendationFeedbackService}: <b>피드백(좋아요/관심없음)</b>.
 *       본 서비스가 저장한 {@code recommendation_log_id} 를 FK 로 사용한다.</li>
 * </ul>
 *
 * <h3>movie_id 존재 검증</h3>
 * <p>Agent 는 Qdrant/ES 검색 결과의 movie_id 를 그대로 전달하는데, 아주 드물게
 * 해당 ID 가 {@code movies} 테이블에 아직 없을 수 있다 (데이터 파이프라인 지연/삭제 등).
 * 이 경우 {@code getReferenceById} 후 flush 시 {@code EntityNotFoundException} 이 터지므로,
 * 각 Item 을 INSERT 하기 전에 {@code existsById} 로 pre-check 하고 없으면 <b>해당 Item 만 skip</b>
 * (WARN 로그) + 응답 PK 리스트의 해당 자리를 {@code null} 로 채워 길이를 보존한다.
 * Agent 는 null 을 만나면 해당 영화는 {@code recommendation_log_id=null} 로 SSE 를 내려
 * 피드백 버튼만 비활성화되고 추천 노출은 유지된다 (graceful).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecommendationLogService {

    private final RecommendationLogRepository recommendationLogRepository;
    private final MovieRepository movieRepository;

    /**
     * 추천 로그를 배치로 저장한다.
     *
     * <p>요청 items 순서를 보존해 저장하며, 각 Item 에 매칭되는 PK 를 담은 응답 리스트를
     * 반환한다. movieId 가 {@code movies} 테이블에 없으면 해당 Item 은 skip 하고 응답의
     * 해당 자리에 {@code null} 을 채운다.</p>
     *
     * @param request Agent 가 넘긴 배치 요청
     * @return 저장된 {@code recommendation_log_id} 리스트 (items 와 동일 길이, skip 자리는 null)
     */
    @Transactional
    public SaveBatchResponse saveBatch(SaveBatchRequest request) {
        String userId = request.userId();
        String sessionId = request.sessionId();
        String userIntent = request.userIntent();  // nullable
        Integer responseTimeMs = request.responseTimeMs();  // nullable
        String modelVersion = request.modelVersion();  // nullable

        List<Long> resultIds = new ArrayList<>(request.items().size());
        int savedCount = 0;
        int skippedCount = 0;

        for (SaveBatchRequest.Item item : request.items()) {
            String movieId = item.movieId();

            // FK pre-check — 존재하지 않는 movieId 면 skip + null 채움 (graceful)
            if (!movieRepository.existsById(movieId)) {
                log.warn("recommendation_log_batch_skip_missing_movie userId={} sessionId={} movieId={}",
                        userId, sessionId, movieId);
                resultIds.add(null);
                skippedCount++;
                continue;
            }

            // reason 은 Entity NOT NULL 이므로 빈 값이면 공백 1자로 대체
            String safeReason = (item.reason() == null || item.reason().isBlank()) ? " " : item.reason();

            RecommendationLog logEntity = RecommendationLog.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    // Movie FK — existsById 로 확인했으므로 getReferenceById 안전
                    .movie(movieRepository.getReferenceById(movieId))
                    .reason(safeReason)
                    .score(item.score())
                    .cfScore(item.cfScore())
                    .cbfScore(item.cbfScore())
                    .hybridScore(item.hybridScore())
                    .genreMatch(item.genreMatch())
                    .moodMatch(item.moodMatch())
                    .rankPosition(item.rankPosition())
                    .userIntent(userIntent)
                    .responseTimeMs(responseTimeMs)
                    .modelVersion(modelVersion)
                    .clicked(false)
                    .build();

            RecommendationLog saved = recommendationLogRepository.save(logEntity);
            resultIds.add(saved.getRecommendationLogId());
            savedCount++;
        }

        log.info("recommendation_log_batch_created userId={} sessionId={} requested={} saved={} skipped={} logIds={}",
                userId, sessionId, request.items().size(), savedCount, skippedCount, resultIds);

        return new SaveBatchResponse(resultIds);
    }
}
