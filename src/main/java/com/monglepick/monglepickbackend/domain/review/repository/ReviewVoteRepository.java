package com.monglepick.monglepickbackend.domain.review.repository;

import com.monglepick.monglepickbackend.domain.review.entity.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 리뷰 투표 JPA 리포지토리.
 *
 * <p>review_votes 테이블에 대한 CRUD 및 투표 집계 쿼리를 제공한다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndReviewId} — 사용자+리뷰 조합으로 기존 투표 조회 (중복 투표 판별 및 업데이트 용도)</li>
 *   <li>{@link #countByReviewIdAndHelpfulTrue} — 특정 리뷰의 "도움됨" 수 집계</li>
 *   <li>{@link #countByReviewIdAndHelpfulFalse} — 특정 리뷰의 "도움안됨" 수 집계</li>
 * </ul>
 */
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, Long> {

    /**
     * 사용자 ID와 리뷰 ID로 기존 투표 레코드를 조회한다.
     *
     * <p>UNIQUE(user_id, review_id) 제약이 있으므로 최대 1건만 반환된다.
     * 투표 시 기존 레코드 존재 여부를 판별하고,
     * 존재하면 {@link ReviewVote#updateHelpful(boolean)}로 값을 갱신한다.</p>
     *
     * @param userId   투표한 사용자 ID
     * @param reviewId 투표 대상 리뷰 ID
     * @return 기존 투표 레코드 (없으면 Optional.empty())
     */
    Optional<ReviewVote> findByUserIdAndReviewId(String userId, Long reviewId);

    /**
     * 특정 리뷰의 "도움이 됐어요" 투표 수를 반환한다.
     *
     * <p>helpful = true 인 레코드 수를 COUNT 집계한다.</p>
     *
     * @param reviewId 집계 대상 리뷰 ID
     * @return "도움됨" 투표 수 (0 이상)
     */
    long countByReviewIdAndHelpfulTrue(Long reviewId);

    /**
     * 특정 리뷰의 "도움이 안 됐어요" 투표 수를 반환한다.
     *
     * <p>helpful = false 인 레코드 수를 COUNT 집계한다.</p>
     *
     * @param reviewId 집계 대상 리뷰 ID
     * @return "도움안됨" 투표 수 (0 이상)
     */
    long countByReviewIdAndHelpfulFalse(Long reviewId);
}
