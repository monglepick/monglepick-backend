package com.monglepick.monglepickbackend.domain.recommendation.service;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationImpact;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationImpactRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 추천 임팩트 서비스 — 추천 후 사용자 행동 기록 비즈니스 로직.
 *
 * <p>AI Agent가 영화를 추천한 직후 {@link #createImpact}로 초기 레코드를 생성하고,
 * 이후 사용자가 클릭·상세조회·위시리스트·시청·평점 등의 행동을 취할 때마다
 * {@link #updateImpact}로 해당 플래그를 갱신한다.</p>
 *
 * <h3>흐름 요약</h3>
 * <pre>
 * [AI 추천 발생]
 *   → createImpact(userId, movieId, recLogId, position)  // 레코드 초기 생성
 *
 * [사용자 행동 발생]
 *   → updateImpact(userId, movieId, recLogId, "clicked")       // 카드 클릭
 *   → updateImpact(userId, movieId, recLogId, "detail_viewed") // 상세 조회
 *   → updateImpact(userId, movieId, recLogId, "wishlisted")    // 위시리스트
 *   → updateImpact(userId, movieId, recLogId, "watched")       // 시청 완료
 *   → updateImpact(userId, movieId, recLogId, "rated")         // 평점 부여
 * </pre>
 *
 * <h3>지원 actionType 목록</h3>
 * <ul>
 *   <li>{@code "clicked"} — 추천 카드 클릭 ({@code timeToClickSeconds}는 0으로 기록)</li>
 *   <li>{@code "detail_viewed"} — 영화 상세 페이지 조회</li>
 *   <li>{@code "wishlisted"} — 위시리스트 추가</li>
 *   <li>{@code "watched"} — 시청 완료</li>
 *   <li>{@code "rated"} — 평점/리뷰 작성</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 읽기 전용 (쓰기 메서드는 개별 @Transactional 오버라이드)
public class RecommendationImpactService {

    /** 추천 임팩트 JPA 리포지토리 */
    private final RecommendationImpactRepository impactRepository;

    /** 추천 로그 JPA 리포지토리 — 임팩트 생성 시 FK 연결에 사용 */
    private final RecommendationLogRepository recommendationLogRepository;

    /**
     * 추천 발생 시 임팩트 레코드를 초기 생성한다.
     *
     * <p>AI Agent가 영화를 추천한 직후 호출되며, 모든 행동 플래그가 false인
     * 초기 상태의 레코드를 생성한다. 이후 사용자 행동이 발생하면
     * {@link #updateImpact}로 플래그를 갱신한다.</p>
     *
     * <p>동일한 (userId, movieId, recLogId) 조합의 레코드가 이미 존재하면
     * DB 유니크 제약(uk_impact_user_movie_rec)에 의해 예외가 발생하므로,
     * 호출 측에서 중복 호출을 방지해야 한다.</p>
     *
     * @param userId   행동 주체 사용자 ID
     * @param movieId  추천된 영화 ID
     * @param recLogId 연관 추천 로그 ID
     * @param position 추천 목록 내 순위 (1부터 시작, null 허용)
     * @return 저장된 {@link RecommendationImpact} 엔티티
     * @throws BusinessException INTERNAL_SERVER_ERROR — recLogId에 해당하는 추천 로그가 없을 때
     */
    @Transactional
    public RecommendationImpact createImpact(
            String userId,
            String movieId,
            Long recLogId,
            Integer position) {

        // 연관 추천 로그 조회 — FK 연결 및 존재 여부 검증
        RecommendationLog recommendationLog = recommendationLogRepository.findById(recLogId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "추천 로그를 찾을 수 없습니다: recLogId=" + recLogId));

        // 초기 임팩트 레코드 생성 (모든 행동 플래그 false, Builder.Default 적용)
        RecommendationImpact impact = RecommendationImpact.builder()
                .userId(userId)
                .movieId(movieId)
                .recommendationLog(recommendationLog)
                .recommendationPosition(position)
                .build();

        RecommendationImpact saved = impactRepository.save(impact);
        log.info("추천 임팩트 초기 생성: impactId={}, userId={}, movieId={}, recLogId={}, position={}",
                saved.getImpactId(), userId, movieId, recLogId, position);

        return saved;
    }

    /**
     * 사용자 행동 발생 시 임팩트 레코드의 해당 플래그를 갱신한다.
     *
     * <p>(userId, movieId, recLogId) 조합으로 기존 임팩트 레코드를 조회한 뒤,
     * actionType에 대응하는 도메인 메서드를 호출하여 플래그를 true로 전환한다.
     * JPA 더티 체킹(dirty checking)에 의해 트랜잭션 커밋 시 자동으로 UPDATE된다.</p>
     *
     * <p>기존 임팩트 레코드가 존재하지 않으면 예외를 던지지 않고 WARN 로그만 기록한 뒤
     * 정상 반환한다. 이는 추천 로그 미생성 상태에서 발생하는 이벤트를 무시하여
     * 호출 측(Agent, 이벤트 리스너 등)의 안정성을 보장하기 위함이다.</p>
     *
     * <h3>actionType → 도메인 메서드 매핑</h3>
     * <ul>
     *   <li>{@code "clicked"}       → {@link RecommendationImpact#markClicked(int)} (timeToClick=0)</li>
     *   <li>{@code "detail_viewed"} → {@link RecommendationImpact#markDetailViewed()}</li>
     *   <li>{@code "wishlisted"}    → {@link RecommendationImpact#markWishlisted()}</li>
     *   <li>{@code "watched"}       → {@link RecommendationImpact#markWatched()}</li>
     *   <li>{@code "rated"}         → {@link RecommendationImpact#markRated()}</li>
     * </ul>
     *
     * @param userId     행동 주체 사용자 ID
     * @param movieId    행동 대상 영화 ID
     * @param recLogId   연관 추천 로그 ID
     * @param actionType 발생한 행동 유형 (위 목록 참조, 대소문자 무관)
     */
    @Transactional
    public void updateImpact(
            String userId,
            String movieId,
            Long recLogId,
            String actionType) {

        // 기존 임팩트 레코드 조회 — 없으면 WARN 후 무시 (에러 전파 금지)
        Optional<RecommendationImpact> optImpact =
                impactRepository.findByUserIdAndMovieIdAndRecommendationLog_RecommendationLogId(
                        userId, movieId, recLogId);

        if (optImpact.isEmpty()) {
            log.warn("임팩트 레코드 미존재 — 행동 업데이트 건너뜀: userId={}, movieId={}, recLogId={}, actionType={}",
                    userId, movieId, recLogId, actionType);
            return;
        }

        RecommendationImpact impact = optImpact.get();

        // actionType에 따라 해당 도메인 메서드 호출 (dirty checking → 자동 UPDATE)
        switch (actionType.toLowerCase()) {
            case "clicked" -> {
                // timeToClickSeconds를 정확히 측정하려면 호출 측에서 별도 API를 사용하거나
                // 이 메서드 시그니처를 확장해야 한다. 기본 흐름에서는 0으로 기록한다.
                impact.markClicked(0);
                log.info("임팩트 클릭 기록: impactId={}, userId={}, movieId={}",
                        impact.getImpactId(), userId, movieId);
            }
            case "detail_viewed" -> {
                impact.markDetailViewed();
                log.info("임팩트 상세조회 기록: impactId={}, userId={}, movieId={}",
                        impact.getImpactId(), userId, movieId);
            }
            case "wishlisted" -> {
                impact.markWishlisted();
                log.info("임팩트 위시리스트 기록: impactId={}, userId={}, movieId={}",
                        impact.getImpactId(), userId, movieId);
            }
            case "watched" -> {
                impact.markWatched();
                log.info("임팩트 시청완료 기록: impactId={}, userId={}, movieId={}",
                        impact.getImpactId(), userId, movieId);
            }
            case "rated" -> {
                impact.markRated();
                log.info("임팩트 평점부여 기록: impactId={}, userId={}, movieId={}",
                        impact.getImpactId(), userId, movieId);
            }
            default -> log.warn("알 수 없는 actionType — 업데이트 건너뜀: actionType={}, userId={}, movieId={}, recLogId={}",
                    actionType, userId, movieId, recLogId);
        }
    }
}
