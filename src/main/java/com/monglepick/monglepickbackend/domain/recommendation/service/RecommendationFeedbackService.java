package com.monglepick.monglepickbackend.domain.recommendation.service;

import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationFeedbackRequest;
import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationFeedbackResponse;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationFeedbackRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 피드백 서비스 — 추천 결과에 대한 사용자 피드백 비즈니스 로직.
 *
 * <p>사용자가 AI 추천 결과에 좋아요/싫어요/시청/관심없음 피드백을 남길 수 있다.
 * 동일 사용자가 동일 추천에 재제출하면 기존 피드백을 덮어쓴다 (UPSERT 방식).
 * 수집된 피드백은 추천 모델 품질 개선 및 사용자 선호도 학습에 활용된다.</p>
 *
 * <h3>UPSERT 처리 흐름</h3>
 * <ol>
 *   <li>RecommendationLog 조회 — recommendationLogId로 추천 로그 조회</li>
 *   <li>기존 피드백 확인 — (userId, recommendationLogId) 조합으로 조회</li>
 *   <li>존재하면 update(), 없으면 새 엔티티 생성 후 save()</li>
 * </ol>
 *
 * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA에서 fetch 하지 않고
 * String userId만 보관한다 (설계서 §15.4). 사용자 존재 검증은 JWT/ServiceKey 인증
 * 단계에서 이미 수행되었으므로 이 서비스 내부 검증은 생략한다.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 읽기 전용 (쓰기 메서드는 개별 @Transactional 오버라이드)
public class RecommendationFeedbackService {

    /** 피드백 JPA 리포지토리 */
    private final RecommendationFeedbackRepository feedbackRepository;

    /** 추천 로그 JPA 리포지토리 (피드백 대상 로그 존재 여부 확인용) */
    private final RecommendationLogRepository recommendationLogRepository;

    /*
     * users 테이블 쓰기 소유는 김민규(MyBatis)이므로 UserRepository 의존성을 제거하고
     * String userId만 보관한다 (설계서 §15.4). 사용자 존재 검증은 인증 단계에서 완료된다.
     */

    /**
     * 추천 피드백을 제출한다 (UPSERT).
     *
     * <p>동일 사용자가 동일 추천 로그에 이미 피드백을 남긴 경우,
     * 새 레코드를 추가하는 대신 기존 피드백의 유형과 코멘트를 갱신한다.
     * 처음 제출하는 경우에는 새 피드백 레코드를 생성한다.</p>
     *
     * @param userId              피드백을 남기는 사용자 ID (JWT 또는 ServiceKey에서 추출)
     * @param recommendationLogId 피드백 대상 추천 로그 ID (URL 경로 파라미터)
     * @param request             피드백 유형 및 코멘트 요청 DTO
     * @return 저장된 피드백 응답 DTO
     * @throws BusinessException USER_NOT_FOUND — userId에 해당하는 사용자가 없을 때
     * @throws BusinessException INTERNAL_SERVER_ERROR (G001) — 추천 로그가 없을 때 (커스텀 메시지)
     */
    @Transactional
    public RecommendationFeedbackResponse submitFeedback(
            String userId,
            Long recommendationLogId,
            RecommendationFeedbackRequest request) {

        // 1. 사용자 존재 검증은 인증 단계에서 이미 처리됨 — String userId 그대로 사용
        //    (JPA/MyBatis 하이브리드 §15.4)

        // 2. 추천 로그 조회 — 피드백 대상 로그가 실제로 존재하는지 확인
        RecommendationLog recommendationLog = recommendationLogRepository.findById(recommendationLogId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR, "추천 로그를 찾을 수 없습니다"));
        log.debug("피드백 대상 추천 로그 확인: recommendationLogId={}", recommendationLogId);

        // 3. 기존 피드백 여부 확인 (UPSERT 분기)
        RecommendationFeedback feedback = feedbackRepository
                .findByUserIdAndRecommendationLog_RecommendationLogId(userId, recommendationLogId)
                .map(existing -> {
                    // 3-a. 기존 피드백이 있으면 유형/코멘트/별점 갱신 (dirty checking 자동 UPDATE)
                    log.info("기존 피드백 업데이트: feedbackId={}, userId={}, recommendationLogId={}, newType={}, rating={}",
                            existing.getRecommendationFeedbackId(), userId, recommendationLogId,
                            request.feedbackType(), request.rating());
                    existing.update(request.feedbackType(), request.comment(), request.rating());
                    return existing;
                })
                .orElseGet(() -> {
                    // 3-b. 기존 피드백이 없으면 새 피드백 생성 (별점 포함 — QA #172)
                    log.info("신규 피드백 생성: userId={}, recommendationLogId={}, type={}, rating={}",
                            userId, recommendationLogId, request.feedbackType(), request.rating());
                    return feedbackRepository.save(
                            RecommendationFeedback.builder()
                                    .userId(userId)
                                    .recommendationLog(recommendationLog)
                                    .feedbackType(request.feedbackType())
                                    .rating(request.rating())
                                    .comment(request.comment())
                                    .build()
                    );
                });

        return RecommendationFeedbackResponse.from(feedback);
    }
}
