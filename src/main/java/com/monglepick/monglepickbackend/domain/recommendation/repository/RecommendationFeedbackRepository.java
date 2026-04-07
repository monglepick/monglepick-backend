package com.monglepick.monglepickbackend.domain.recommendation.repository;

import com.monglepick.monglepickbackend.domain.recommendation.entity.RecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 추천 피드백 JPA 리포지토리 — recommendation_feedback 테이블 데이터 접근.
 *
 * <p>사용자가 AI 추천 결과에 남긴 피드백(좋아요/싫어요/시청/관심없음)의
 * 통계 집계를 지원한다. 관리자 통계 탭의 추천 성과 분석에서 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #countByCreatedAtAfter(LocalDateTime)} — 기간 내 피드백 총 수</li>
 *   <li>{@link #countByFeedbackTypeAndCreatedAtAfter(String, LocalDateTime)} — 피드백 유형별 집계</li>
 *   <li>{@link #findByUserIdAndRecommendationLog_RecommendationLogId(String, Long)} — UPSERT용 기존 피드백 조회</li>
 * </ul>
 */
public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, Long> {

    /**
     * 특정 사용자와 추천 로그 조합으로 기존 피드백을 조회한다.
     *
     * <p>피드백 제출 시 UPSERT 처리를 위해 사용된다.
     * 이미 피드백이 존재하면 feedbackType과 comment를 업데이트하고,
     * 없으면 새 피드백을 생성한다.</p>
     *
     * <p>recommendation_feedback 테이블의 (user_id, recommendation_id) UNIQUE 제약을
     * 애플리케이션 레벨에서 안전하게 처리하기 위한 메서드이다.</p>
     *
     * @param userId              피드백을 남긴 사용자 ID
     * @param recommendationLogId 피드백 대상 추천 로그 ID
     * @return 기존 피드백 (없으면 빈 Optional)
     */
    Optional<RecommendationFeedback> findByUserIdAndRecommendationLog_RecommendationLogId(
            String userId, Long recommendationLogId);

    /**
     * 지정 시각 이후 생성된 피드백 총 수를 집계한다.
     *
     * <p>만족도 계산의 분모로 사용된다.
     * satisfactionRate = (like + watched) / total × 100</p>
     *
     * @param after 기준 시각
     * @return 해당 기간 내 피드백 총 수
     */
    long countByCreatedAtAfter(LocalDateTime after);

    /**
     * 지정 시각 이후 특정 피드백 유형의 수를 집계한다.
     *
     * <p>만족도 계산의 분자로 사용된다.
     * FeedbackType enum은 소문자(like, dislike, watched, not_interested)로 저장된다.</p>
     *
     * @param feedbackType 피드백 유형 문자열 (예: "like", "watched")
     * @param after        기준 시각
     * @return 해당 유형의 피드백 수
     */
    @Query("SELECT COUNT(f) FROM RecommendationFeedback f " +
           "WHERE CAST(f.feedbackType AS string) = :feedbackType AND f.createdAt > :after")
    long countByFeedbackTypeAndCreatedAtAfter(
            @Param("feedbackType") String feedbackType,
            @Param("after") LocalDateTime after);
}
