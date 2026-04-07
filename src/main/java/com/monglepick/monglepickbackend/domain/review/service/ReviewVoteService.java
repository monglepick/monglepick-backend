package com.monglepick.monglepickbackend.domain.review.service;

import com.monglepick.monglepickbackend.domain.review.dto.ReviewVoteResponse;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewVote;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 투표 서비스 — "도움이 됐어요" 투표 처리 및 집계 조회.
 *
 * <p>사용자가 특정 리뷰에 "도움이 됐어요(true) / 도움이 안 됐어요(false)"를
 * 투표하거나 기존 투표를 변경할 수 있다. JPA/MyBatis 하이브리드 §15에 따라
 * 모든 데이터 접근은 {@link ReviewMapper}를 통해 이루어진다.</p>
 *
 * <h3>투표 처리 로직</h3>
 * <ol>
 *   <li>기존 투표 없음 → 신규 {@link ReviewVote} INSERT</li>
 *   <li>기존 투표 있음 → 값 변경 시 명시적 UPDATE (MyBatis dirty checking 미지원)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewVoteService {

    private final ReviewMapper reviewMapper;

    /**
     * 리뷰에 투표하거나 기존 투표를 변경한다.
     *
     * <p>UNIQUE(user_id, review_id) 제약 덕분에 동일 사용자-리뷰 쌍은 최대 1건만 존재한다.
     * 기존 레코드가 있으면 helpful 값만 갱신하고, 없으면 신규 INSERT한다.</p>
     */
    @Transactional
    public ReviewVoteResponse vote(String userId, Long reviewId, boolean helpful) {
        ReviewVote existing = reviewMapper.findReviewVoteByUserIdAndReviewId(userId, reviewId);

        if (existing != null) {
            /* 기존 투표 존재 — 값 갱신 (UPDATE) */
            reviewMapper.updateReviewVoteHelpful(existing.getReviewVoteId(), helpful);
            log.info("리뷰 투표 변경 - userId: {}, reviewId: {}, helpful: {}", userId, reviewId, helpful);
        } else {
            /* 기존 투표 없음 — 신규 등록 (INSERT) */
            ReviewVote newVote = ReviewVote.builder()
                    .userId(userId)
                    .reviewId(reviewId)
                    .helpful(helpful)
                    .build();
            reviewMapper.insertReviewVote(newVote);
            log.info("리뷰 투표 신규 등록 - userId: {}, reviewId: {}, helpful: {}", userId, reviewId, helpful);
        }

        return buildResponse(reviewId, userId);
    }

    /**
     * 특정 리뷰의 투표 현황과 현재 사용자의 투표 상태를 조회한다.
     *
     * <p>현재 사용자가 투표하지 않은 경우 {@code myVote = null}이 반환된다.</p>
     */
    public ReviewVoteResponse getVoteCounts(Long reviewId, String userId) {
        return buildResponse(reviewId, userId);
    }

    /**
     * 리뷰 투표 집계 + 현재 사용자 투표 상태를 조합하여 응답 DTO를 생성한다.
     */
    private ReviewVoteResponse buildResponse(Long reviewId, String userId) {
        long helpfulCount = reviewMapper.countReviewVoteByReviewIdAndHelpfulTrue(reviewId);
        long unhelpfulCount = reviewMapper.countReviewVoteByReviewIdAndHelpfulFalse(reviewId);

        // 현재 사용자의 투표 상태 조회 (userId가 null이면 비로그인 → myVote=null)
        Boolean myVote = null;
        if (userId != null) {
            ReviewVote myVoteEntity = reviewMapper.findReviewVoteByUserIdAndReviewId(userId, reviewId);
            if (myVoteEntity != null) {
                myVote = myVoteEntity.getHelpful();
            }
        }

        return ReviewVoteResponse.of(helpfulCount, unhelpfulCount, myVote);
    }
}
