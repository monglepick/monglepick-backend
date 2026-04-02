package com.monglepick.monglepickbackend.domain.recommendation.service;

import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationFeedbackRequest;
import com.monglepick.monglepickbackend.domain.recommendation.dto.RecommendationFeedbackResponse;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;
import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationLog;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationFeedbackRepository;
import com.monglepick.monglepickbackend.domain.recommendation.repository.RecommendationLogRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
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
 *   <li>User 조회 — userId로 users 테이블 조회</li>
 *   <li>RecommendationLog 조회 — recommendationLogId로 추천 로그 조회</li>
 *   <li>기존 피드백 확인 — (userId, recommendationLogId) 조합으로 조회</li>
 *   <li>존재하면 update(), 없으면 새 엔티티 생성 후 save()</li>
 * </ol>
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

    /** 사용자 JPA 리포지토리 (사용자 존재 여부 확인 및 FK 연결용) */
    private final UserRepository userRepository;

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

        // 1. 사용자 조회 — UserRepository PK가 String(userId)이므로 findById 사용
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        log.debug("피드백 제출 사용자 확인: userId={}", userId);

        // 2. 추천 로그 조회 — 피드백 대상 로그가 실제로 존재하는지 확인
        RecommendationLog recommendationLog = recommendationLogRepository.findById(recommendationLogId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR, "추천 로그를 찾을 수 없습니다"));
        log.debug("피드백 대상 추천 로그 확인: recommendationLogId={}", recommendationLogId);

        // 3. 기존 피드백 여부 확인 (UPSERT 분기)
        RecommendationFeedback feedback = feedbackRepository
                .findByUser_UserIdAndRecommendationLog_RecommendationLogId(userId, recommendationLogId)
                .map(existing -> {
                    // 3-a. 기존 피드백이 있으면 유형과 코멘트를 갱신 (dirty checking으로 자동 UPDATE)
                    log.info("기존 피드백 업데이트: feedbackId={}, userId={}, recommendationLogId={}, newType={}",
                            existing.getRecommendationFeedbackId(), userId, recommendationLogId,
                            request.feedbackType());
                    existing.update(request.feedbackType(), request.comment());
                    return existing;
                })
                .orElseGet(() -> {
                    // 3-b. 기존 피드백이 없으면 새 피드백 생성
                    log.info("신규 피드백 생성: userId={}, recommendationLogId={}, type={}",
                            userId, recommendationLogId, request.feedbackType());
                    return feedbackRepository.save(
                            RecommendationFeedback.builder()
                                    .user(user)
                                    .recommendationLog(recommendationLog)
                                    .feedbackType(request.feedbackType())
                                    .comment(request.comment())
                                    .build()
                    );
                });

        return RecommendationFeedbackResponse.from(feedback);
    }
}
