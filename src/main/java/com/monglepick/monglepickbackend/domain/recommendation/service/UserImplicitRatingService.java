package com.monglepick.monglepickbackend.domain.recommendation.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.recommendation.entity.UserImplicitRating;
import com.monglepick.monglepickbackend.domain.recommendation.repository.UserImplicitRatingRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 암시적 평점 서비스 — 사용자 행동 기반 암시적 평점 비즈니스 로직.
 *
 * <p>클릭·상세 조회·트레일러 재생·즐겨찾기·좋아요·시청 완료 등 사용자 행동을
 * 가중치 기반으로 합산하여 user_implicit_rating 테이블에 누적 저장한다.</p>
 *
 * <h3>UPSERT 처리 흐름</h3>
 * <ol>
 *   <li>(userId, movieId)로 기존 레코드 조회</li>
 *   <li>없으면 implicitScore=0.0, contributingActions="{}" 으로 신규 생성</li>
 *   <li>행동 유형에 따른 가중치(ACTION_WEIGHTS)를 {@link UserImplicitRating#addScore(double)}에 전달</li>
 *   <li>contributingActions JSON의 해당 행동 카운트를 1 증가시켜 저장</li>
 * </ol>
 *
 * <h3>행동 가중치 (ACTION_WEIGHTS)</h3>
 * <pre>
 *   click           +0.5   (검색 결과/카드 클릭)
 *   detail_view     +1.0   (상세 페이지 진입)
 *   trailer_play    +0.5   (트레일러 재생)
 *   wishlist_add    +1.5   (위시리스트 추가)
 *   like            +2.0   (좋아요)
 *   watch_complete  +2.0   (시청 완료)
 *   rate_high       +1.0   (별점 4.0 이상 등록)
 *   not_interested  -3.0   (관심 없음)
 *   skip            -0.3   (추천 스킵)
 * </pre>
 *
 * <p>점수는 {@link UserImplicitRating#addScore(double)} 내부에서
 * 항상 [0.0, 5.0] 범위로 클램프된다.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 레벨: 읽기 전용 (쓰기 메서드는 개별 @Transactional 오버라이드)
public class UserImplicitRatingService {

    /** 암시적 평점 JPA 리포지토리 */
    private final UserImplicitRatingRepository userImplicitRatingRepository;

    /**
     * JSON 직렬화/역직렬화용 ObjectMapper.
     * Spring Boot 자동 구성 빈(@Primary)을 주입받는다.
     */
    private final ObjectMapper objectMapper;

    /**
     * 행동 유형별 암시적 평점 가중치 맵.
     *
     * <p>긍정 행동은 양수(+), 부정 행동은 음수(-)로 정의한다.
     * 최종 점수는 항상 [0.0, 5.0]으로 클램프된다.</p>
     */
    private static final Map<String, Double> ACTION_WEIGHTS = Map.of(
            "click",           0.5,   // 검색 결과·추천 카드 클릭
            "detail_view",     1.0,   // 영화 상세 페이지 진입
            "trailer_play",    0.5,   // 트레일러 재생
            "wishlist_add",    1.5,   // 위시리스트(찜) 추가
            "like",            2.0,   // 좋아요 (명시적 긍정 신호)
            "watch_complete",  2.0,   // 시청 완료 (가장 강한 긍정 신호)
            "rate_high",       1.0,   // 별점 4.0 이상 등록
            "not_interested", -3.0,   // 관심 없음 (강한 부정 신호)
            "skip",           -0.3    // 추천 스킵 (약한 부정 신호)
    );

    // -------------------------------------------------------------------------
    // 쓰기 메서드
    // -------------------------------------------------------------------------

    /**
     * 사용자의 영화 관련 행동을 기록하고 암시적 평점을 갱신한다.
     *
     * <p>기존 레코드가 없으면 새 레코드를 생성하고,
     * 있으면 점수와 contributing_actions 카운트를 누적 갱신한다.
     * 알 수 없는 actionType이 전달되면 경고를 로깅하고 즉시 반환한다.</p>
     *
     * @param userId     행동을 수행한 사용자 ID
     * @param movieId    행동 대상 영화 ID
     * @param actionType 행동 유형 문자열 (ACTION_WEIGHTS 키 참조)
     * @throws BusinessException G002 — contributingActions JSON 파싱/직렬화 실패 시
     */
    @Transactional
    public void recordAction(String userId, String movieId, String actionType) {

        // 1. 알 수 없는 행동 유형 조기 반환
        Double weight = ACTION_WEIGHTS.get(actionType);
        if (weight == null) {
            log.warn("알 수 없는 행동 유형 — 암시적 평점 갱신 생략: userId={}, movieId={}, actionType={}",
                    userId, movieId, actionType);
            return;
        }

        // 2. 기존 레코드 조회 또는 신규 생성
        UserImplicitRating rating = userImplicitRatingRepository
                .findByUserIdAndMovieId(userId, movieId)
                .orElseGet(() -> {
                    log.debug("암시적 평점 신규 생성: userId={}, movieId={}", userId, movieId);
                    return userImplicitRatingRepository.save(
                            UserImplicitRating.builder()
                                    .userId(userId)
                                    .movieId(movieId)
                                    .implicitScore(0.0)
                                    .contributingActions("{}")
                                    .build()
                    );
                });

        // 3. 암시적 점수 갱신 (clamp 0.0~5.0 내부 적용)
        double scoreBefore = rating.getImplicitScore();
        rating.addScore(weight);
        log.debug("암시적 점수 갱신: userId={}, movieId={}, action={}, weight={}, {} → {}",
                userId, movieId, actionType, weight, scoreBefore, rating.getImplicitScore());

        // 4. contributingActions JSON 카운트 갱신
        String updatedJson = incrementActionCount(rating.getContributingActions(), actionType);
        rating.updateContributingActions(updatedJson);

        // JPA dirty checking — 트랜잭션 커밋 시 자동 UPDATE
        log.info("암시적 평점 기록 완료: userId={}, movieId={}, actionType={}, implicitScore={}",
                userId, movieId, actionType, rating.getImplicitScore());
    }

    // -------------------------------------------------------------------------
    // 읽기 메서드
    // -------------------------------------------------------------------------

    /**
     * 특정 사용자-영화 쌍의 암시적 평점을 조회한다.
     *
     * <p>레코드가 존재하지 않으면 {@code null}을 반환한다.
     * CF 추천 엔진이 사용자-영화 점수를 빠르게 참조할 때 사용된다.</p>
     *
     * @param userId  사용자 ID
     * @param movieId 영화 ID
     * @return 암시적 평점 (0.0~5.0), 기록 없으면 null
     */
    public Double getImplicitScore(String userId, String movieId) {
        return userImplicitRatingRepository
                .findByUserIdAndMovieId(userId, movieId)
                .map(UserImplicitRating::getImplicitScore)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // private 헬퍼
    // -------------------------------------------------------------------------

    /**
     * 기존 contributingActions JSON 문자열에서 지정 행동의 카운트를 1 증가시킨다.
     *
     * <p>JSON 역직렬화 → 카운트 증가 → 재직렬화 순으로 처리한다.
     * JSON이 null이거나 파싱에 실패하면 빈 맵({})으로 초기화한 뒤 진행한다.</p>
     *
     * @param existingJson 기존 contributing_actions JSON 문자열 (null 허용)
     * @param actionType   카운트를 증가시킬 행동 유형 키
     * @return 갱신된 JSON 문자열
     * @throws BusinessException G002 — JSON 직렬화 실패 시 (역직렬화 실패는 경고 후 빈 맵으로 복구)
     */
    private String incrementActionCount(String existingJson, String actionType) {

        // 4-a. 기존 JSON 역직렬화 — 실패 시 경고 로그 후 빈 맵으로 복구
        Map<String, Integer> actionCounts = new HashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                actionCounts = objectMapper.readValue(
                        existingJson,
                        new TypeReference<Map<String, Integer>>() {}
                );
            } catch (JacksonException e) {
                log.warn("contributingActions JSON 파싱 실패 — 빈 맵으로 초기화: existingJson={}, error={}",
                        existingJson, e.getMessage());
            }
        }

        // 4-b. 해당 행동 카운트 1 증가 (없으면 0에서 시작)
        actionCounts.merge(actionType, 1, Integer::sum);

        // 4-c. 재직렬화 — 실패 시 BusinessException 발생
        try {
            return objectMapper.writeValueAsString(actionCounts);
        } catch (JacksonException e) {
            log.error("contributingActions JSON 직렬화 실패: actionType={}, error={}", actionType, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "행동 카운트 JSON 직렬화 실패: " + e.getMessage());
        }
    }
}
