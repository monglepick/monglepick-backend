package com.monglepick.monglepickbackend.domain.recommendation.service;

import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationHistoryDto;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationFeedbackRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 추천 이력 서비스 — 사용자별 추천 이력 조회 및 찜/봤어요 토글 비즈니스 로직.
 *
 * <p>클라이언트 추천 이력 탭에서 호출하는 3개 API의 비즈니스 로직을 담당한다.</p>
 *
 * <h3>찜/봤어요 상태 관리 전략</h3>
 * <p>RecommendationLog 엔티티에 wishlist_yn, watched_yn 컬럼이 없으므로,
 * 기존 {@link RecommendationImpact} 엔티티의 {@code wishlisted}, {@code watched} 필드를 활용한다.</p>
 * <ul>
 *   <li>Impact 레코드가 없으면 새로 생성 (INSERT)하여 해당 필드를 true로 설정</li>
 *   <li>Impact 레코드가 있으면 기존 레코드의 필드를 토글 (dirty checking 자동 UPDATE)</li>
 * </ul>
 *
 * <h3>토글 동작</h3>
 * <ul>
 *   <li>현재 false → true (추가)</li>
 *   <li>현재 true → false (취소)</li>
 * </ul>
 *
 * <h3>소유권 검증</h3>
 * <p>findByRecommendationLogIdAndUserId 쿼리로 단건 조회 시 userId를 함께 조건으로 걸어
 * 타인의 추천 이력에 대한 토글을 원천 차단한다 (REC002, 404 응답).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 레벨: 기본 읽기 전용 트랜잭션
public class RecommendationHistoryService {

    /** 추천 로그 리포지토리 — 사용자별 이력 조회 및 소유권 검증 */
    private final RecommendationLogRepository recommendationLogRepository;

    /** 추천 임팩트 리포지토리 — 찜/봤어요 상태 저장 및 토글 */
    private final RecommendationImpactRepository recommendationImpactRepository;

    /**
     * 추천 피드백 리포지토리 — 별점/피드백유형 복원용 (QA #172).
     * getRecommendationHistory 에서 페이지 단위 배치 조회로 N+1 을 회피한다.
     */
    private final RecommendationFeedbackRepository recommendationFeedbackRepository;

    // ─────────────────────────────────────────────
    // 조회 (readOnly = true 상속)
    // ─────────────────────────────────────────────

    /**
     * 사용자의 추천 이력 목록을 페이징 조회한다.
     *
     * <p>RecommendationLog를 최신 순으로 페이징 조회하고,
     * 각 로그에 대응하는 RecommendationImpact를 개별 조회하여 찜/봤어요 상태를 합산한다.</p>
     *
     * <h3>N+1 처리 방식</h3>
     * <p>페이지 크기가 일반적으로 20건 이하이므로, 각 로그마다 Impact를 1건씩 추가 조회해도
     * 최대 20회 쿼리로 허용 범위 내에 있다. 향후 성능 문제 시 LEFT JOIN FETCH로 개선 가능.</p>
     *
     * @param userId   JWT에서 추출한 사용자 ID
     * @param pageable 페이지 번호·크기·정렬 정보 (기본 정렬: createdAt DESC)
     * @return 추천 이력 응답 DTO 페이지
     */
    public Page<RecommendationHistoryDto.RecommendationHistoryResponse> getRecommendationHistory(
            String userId, String status, Pageable pageable) {

        log.debug("추천 이력 조회: userId={}, page={}, status={}",
                userId, pageable.getPageNumber(), status);

        // QA 후속 (2026-04-23): status 필터 지원.
        //   - null/"ALL" → 기존 전체 목록
        //   - "WISHLIST" → Impact.wishlisted=true 만
        //   - "WATCHED"  → Impact.watched=true 만
        // 그 외 임의 값은 전체로 폴백 (대소문자 무시).
        final String normalized = status == null ? "ALL" : status.trim().toUpperCase();

        // 사용자별 추천 로그 페이징 조회 (movie JOIN FETCH — N+1 방지)
        final Page<RecommendationLog> logPage;
        switch (normalized) {
            case "WISHLIST" -> logPage =
                    recommendationLogRepository.findByUserIdWishlistedWithMovie(userId, pageable);
            case "WATCHED" -> logPage =
                    recommendationLogRepository.findByUserIdWatchedWithMovie(userId, pageable);
            default -> logPage =
                    recommendationLogRepository.findByUserIdWithMovie(userId, pageable);
        }

        // QA #172 (2026-04-23): 현재 페이지의 모든 로그 ID 를 모아 한 번의 쿼리로 피드백을 배치 조회.
        // 루프 안에서 feedback 단건 조회를 돌리면 페이지당 최대 20회 추가 쿼리가 발생하므로
        // IN 절 1회 조회로 대체해 N+1 을 방지한다. 피드백이 없으면 맵에서 get() 결과 null.
        List<Long> pageLogIds = logPage.getContent().stream()
                .map(RecommendationLog::getRecommendationLogId)
                .toList();
        Map<Long, RecommendationFeedback> feedbackByLogId = pageLogIds.isEmpty()
                ? Map.of()
                : recommendationFeedbackRepository
                        .findAllByUserIdAndLogIdIn(userId, pageLogIds)
                        .stream()
                        .collect(Collectors.toMap(
                                fb -> fb.getRecommendationLog().getRecommendationLogId(),
                                Function.identity(),
                                // user_id+recommendation_id UNIQUE 제약으로 중복 불가하지만
                                // 방어적으로 가장 나중 것 우선.
                                (a, b) -> b
                        ));

        // 각 로그에 대응하는 Impact + Feedback 을 조립해 DTO 로 변환.
        return logPage.map(recLog -> {
            // (userId, movieId, recommendationLogId) 조합으로 Impact 단건 조회
            Optional<RecommendationImpact> impact =
                    recommendationImpactRepository
                            .findByUserIdAndMovieIdAndRecommendationLog_RecommendationLogId(
                                    userId,
                                    recLog.getMovie().getMovieId(),
                                    recLog.getRecommendationLogId()
                            );

            // Impact 레코드가 없으면 찜/봤어요 모두 false
            boolean wishlisted = impact.map(RecommendationImpact::getWishlisted).orElse(false);
            boolean watched    = impact.map(RecommendationImpact::getWatched).orElse(false);

            RecommendationFeedback feedback = feedbackByLogId.get(recLog.getRecommendationLogId());
            return RecommendationHistoryDto.RecommendationHistoryResponse.from(
                    recLog, wishlisted, watched, feedback);
        });
    }

    // ─────────────────────────────────────────────
    // 쓰기 (개별 @Transactional 오버라이드)
    // ─────────────────────────────────────────────

    /**
     * 추천 이력에서 특정 영화의 찜 상태를 토글한다.
     *
     * <p>RecommendationImpact 레코드를 업서트하여 {@code wishlisted} 필드를 토글한다.</p>
     * <ul>
     *   <li>Impact 없음 → 새 레코드 생성, wishlisted=true</li>
     *   <li>Impact 있고 wishlisted=false → wishlisted=true</li>
     *   <li>Impact 있고 wishlisted=true → wishlisted=false (찜 취소)</li>
     * </ul>
     *
     * @param recommendationLogId 찜 토글 대상 추천 로그 ID
     * @param userId              JWT에서 추출한 사용자 ID (소유권 검증)
     * @return 토글 후 찜 상태 응답 DTO
     * @throws BusinessException REC001 — 해당 추천 로그가 없거나 본인 로그가 아닐 때
     */
    @Transactional
    public RecommendationHistoryDto.WishlistToggleResponse toggleWishlist(
            Long recommendationLogId, String userId) {

        log.info("추천 이력 찜 토글: recommendationLogId={}, userId={}", recommendationLogId, userId);

        // 추천 로그 조회 + 소유권 검증 (userId 불일치 시 빈 Optional → REC001)
        RecommendationLog recLog = findOwnedLog(recommendationLogId, userId);
        String movieId = recLog.getMovie().getMovieId();

        // Impact 레코드 업서트 (없으면 생성, 있으면 토글)
        RecommendationImpact impact = recommendationImpactRepository
                .findByUserIdAndMovieIdAndRecommendationLog_RecommendationLogId(
                        userId, movieId, recommendationLogId)
                .orElseGet(() -> {
                    // Impact 레코드가 없으면 신규 생성
                    log.debug("Impact 레코드 없음 — 신규 생성: userId={}, movieId={}", userId, movieId);
                    return recommendationImpactRepository.save(
                            RecommendationImpact.builder()
                                    .userId(userId)
                                    .movieId(movieId)
                                    .recommendationLog(recLog)
                                    .build()
                    );
                });

        // 현재 상태 반전 (토글)
        boolean newWishlisted = !Boolean.TRUE.equals(impact.getWishlisted());
        if (newWishlisted) {
            impact.markWishlisted(); // true로 설정
        } else {
            // wishlisted=false 로 되돌리는 도메인 메서드가 없으므로 직접 처리
            // RecommendationImpact는 INSERT-HEAVY 엔티티이지만 찜 취소는 허용
            cancelWishlisted(impact);
        }

        log.info("추천 이력 찜 토글 완료: recommendationLogId={}, wishlisted={}", recommendationLogId, newWishlisted);
        return new RecommendationHistoryDto.WishlistToggleResponse(newWishlisted);
    }

    /**
     * 추천 이력에서 특정 영화의 봤어요 상태를 토글한다.
     *
     * <p>RecommendationImpact 레코드를 업서트하여 {@code watched} 필드를 토글한다.</p>
     * <ul>
     *   <li>Impact 없음 → 새 레코드 생성, watched=true</li>
     *   <li>Impact 있고 watched=false → watched=true</li>
     *   <li>Impact 있고 watched=true → watched=false (봤어요 취소)</li>
     * </ul>
     *
     * @param recommendationLogId 봤어요 토글 대상 추천 로그 ID
     * @param userId              JWT에서 추출한 사용자 ID (소유권 검증)
     * @return 토글 후 봤어요 상태 응답 DTO
     * @throws BusinessException REC001 — 해당 추천 로그가 없거나 본인 로그가 아닐 때
     */
    @Transactional
    public RecommendationHistoryDto.WatchedToggleResponse toggleWatched(
            Long recommendationLogId, String userId) {

        log.info("추천 이력 봤어요 토글: recommendationLogId={}, userId={}", recommendationLogId, userId);

        // 추천 로그 조회 + 소유권 검증
        RecommendationLog recLog = findOwnedLog(recommendationLogId, userId);
        String movieId = recLog.getMovie().getMovieId();

        // Impact 레코드 업서트
        RecommendationImpact impact = recommendationImpactRepository
                .findByUserIdAndMovieIdAndRecommendationLog_RecommendationLogId(
                        userId, movieId, recommendationLogId)
                .orElseGet(() -> {
                    log.debug("Impact 레코드 없음 — 신규 생성: userId={}, movieId={}", userId, movieId);
                    return recommendationImpactRepository.save(
                            RecommendationImpact.builder()
                                    .userId(userId)
                                    .movieId(movieId)
                                    .recommendationLog(recLog)
                                    .build()
                    );
                });

        // 현재 상태 반전 (토글)
        boolean newWatched = !Boolean.TRUE.equals(impact.getWatched());
        if (newWatched) {
            impact.markWatched(); // true로 설정
        } else {
            cancelWatched(impact);
        }

        log.info("추천 이력 봤어요 토글 완료: recommendationLogId={}, watched={}", recommendationLogId, newWatched);
        return new RecommendationHistoryDto.WatchedToggleResponse(newWatched);
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ─────────────────────────────────────────────

    /**
     * 추천 로그를 조회하고 소유권을 검증한다.
     *
     * <p>recommendationLogId와 userId를 조합하여 조회한다.
     * 로그가 없거나 소유자가 다르면 REC001(404)을 던진다.
     * (403 대신 404를 사용하여 타인 ID 존재 여부를 노출하지 않는다.)</p>
     *
     * @param recommendationLogId 추천 로그 ID
     * @param userId              소유자 사용자 ID
     * @return 소유권이 확인된 RecommendationLog 엔티티 (movie JOIN FETCH 포함)
     * @throws BusinessException REC001 — 로그 없음 또는 소유자 불일치
     */
    private RecommendationLog findOwnedLog(Long recommendationLogId, String userId) {
        return recommendationLogRepository
                .findByRecommendationLogIdAndUserId(recommendationLogId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_LOG_NOT_FOUND,
                        "추천 이력을 찾을 수 없습니다: recommendationLogId=" + recommendationLogId));
    }

    /**
     * RecommendationImpact의 wishlisted 필드를 false로 되돌린다 (찜 취소).
     *
     * <p>RecommendationImpact에 cancelWishlisted 도메인 메서드가 없으므로
     * 서비스 레이어에서 리플렉션 없이 처리한다.
     * 해당 엔티티에 도메인 메서드를 추가하는 대신, 임팩트 취소는 드문 케이스이므로
     * 여기서 직접 처리한다.</p>
     *
     * <p>JPA dirty checking으로 트랜잭션 커밋 시 자동 UPDATE된다.</p>
     *
     * @param impact 찜 취소할 Impact 엔티티
     */
    private void cancelWishlisted(RecommendationImpact impact) {
        // RecommendationImpact 엔티티에 cancelWishlisted 메서드 추가 없이 처리
        // wishlisted 필드를 직접 false로 업데이트하기 위해 엔티티 도메인 메서드 호출
        // 엔티티에 cancel 메서드가 없으므로 RecommendationImpactService 패턴 참조하여
        // 새 레코드 저장 대신 기존 레코드의 필드를 수정 후 dirty checking 적용
        //
        // 설계 결정: RecommendationImpact.cancelWishlisted()를 엔티티에 추가하는 것이 바람직하나,
        // 팀 컨벤션(INSERT-ONLY 권장)과 실제 사용 빈도를 고려하여 서비스에서 처리함
        impact.cancelWishlisted();
    }

    /**
     * RecommendationImpact의 watched 필드를 false로 되돌린다 (봤어요 취소).
     *
     * @param impact 봤어요 취소할 Impact 엔티티
     */
    private void cancelWatched(RecommendationImpact impact) {
        impact.cancelWatched();
    }
}
