package com.monglepick.monglepickbackend.domain.review.service;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewVoteResponse;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewVote;
import com.monglepick.monglepickbackend.domain.review.repository.ReviewVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 리뷰 투표 서비스 — "도움이 됐어요" 투표 처리 및 집계 조회.
 *
 * <p>사용자가 특정 리뷰에 "도움이 됐어요(true) / 도움이 안 됐어요(false)"를
 * 투표하거나 기존 투표를 변경할 수 있다.
 * 투표 후에는 최신 집계 결과와 현재 사용자의 투표 상태를 함께 반환한다.</p>
 *
 * <h3>투표 처리 로직 (2분기)</h3>
 * <ol>
 *   <li>기존 투표 없음 → 신규 {@link ReviewVote} INSERT</li>
 *   <li>기존 투표 있음 → {@link ReviewVote#updateHelpful(boolean)} 도메인 메서드로 값 갱신 (UPDATE)</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code readOnly=true} — 조회 메서드 기본값</li>
 *   <li>{@link #vote}: {@code @Transactional} 오버라이드 — INSERT/UPDATE 원자성 보장</li>
 * </ul>
 *
 * @see ReviewVoteRepository
 * @see ReviewVoteResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewVoteService {

    private final ReviewVoteRepository reviewVoteRepository;

    /**
     * 리뷰에 투표하거나 기존 투표를 변경한다.
     *
     * <p>UNIQUE(user_id, review_id) 제약 덕분에 동일 사용자-리뷰 쌍은 최대 1건만 존재한다.
     * 기존 레코드가 있으면 {@link ReviewVote#updateHelpful(boolean)}으로 갱신하고,
     * 없으면 신규 레코드를 INSERT한다.</p>
     *
     * <p>처리 후 최신 집계 결과와 사용자의 투표 상태를 담은
     * {@link ReviewVoteResponse}를 반환한다.</p>
     *
     * @param userId   투표 사용자 ID (JWT Principal에서 추출)
     * @param reviewId 투표 대상 리뷰 ID
     * @param helpful  true=도움됨, false=도움안됨
     * @return 최신 투표 집계 + 현재 사용자 투표 상태
     */
    @Transactional
    public ReviewVoteResponse vote(String userId, Long reviewId, boolean helpful) {
        Optional<ReviewVote> existing = reviewVoteRepository.findByUserIdAndReviewId(userId, reviewId);

        if (existing.isPresent()) {
            /* 케이스 1: 기존 투표 존재 — 값 갱신 (UPDATE) */
            ReviewVote vote = existing.get();
            vote.updateHelpful(helpful);
            log.info("리뷰 투표 변경 - userId: {}, reviewId: {}, helpful: {}", userId, reviewId, helpful);
        } else {
            /* 케이스 2: 기존 투표 없음 — 신규 등록 (INSERT) */
            ReviewVote newVote = ReviewVote.builder()
                    .userId(userId)
                    .reviewId(reviewId)
                    .helpful(helpful)
                    .build();
            reviewVoteRepository.save(newVote);
            log.info("리뷰 투표 신규 등록 - userId: {}, reviewId: {}, helpful: {}", userId, reviewId, helpful);
        }

        /* 투표 처리 후 최신 집계 조회 */
        return buildResponse(reviewId, userId);
    }

    /**
     * 특정 리뷰의 투표 현황과 현재 사용자의 투표 상태를 조회한다.
     *
     * <p>GET 엔드포인트에서 호출된다.
     * 현재 사용자가 투표하지 않은 경우 {@code myVote = null}이 반환된다.</p>
     *
     * @param reviewId 조회 대상 리뷰 ID
     * @param userId   현재 사용자 ID (JWT Principal에서 추출)
     * @return 투표 집계 + 현재 사용자 투표 상태
     */
    public ReviewVoteResponse getVoteCounts(Long reviewId, String userId) {
        return buildResponse(reviewId, userId);
    }

    /**
     * 리뷰 투표 집계와 현재 사용자 투표 상태를 조합하여 응답 DTO를 생성한다.
     *
     * <p>내부 공통 헬퍼 메서드. {@link #vote}와 {@link #getVoteCounts} 양쪽에서 재사용한다.</p>
     *
     * @param reviewId 집계 대상 리뷰 ID
     * @param userId   현재 사용자 ID
     * @return ReviewVoteResponse 인스턴스
     */
    private ReviewVoteResponse buildResponse(Long reviewId, String userId) {
        long helpfulCount = reviewVoteRepository.countByReviewIdAndHelpfulTrue(reviewId);
        long unhelpfulCount = reviewVoteRepository.countByReviewIdAndHelpfulFalse(reviewId);

        /* 현재 사용자의 투표 상태 조회 (미투표면 null) */
        Boolean myVote = reviewVoteRepository.findByUserIdAndReviewId(userId, reviewId)
                .map(ReviewVote::getHelpful)
                .orElse(null);

        return ReviewVoteResponse.of(helpfulCount, unhelpfulCount, myVote);
    }
}
