package com.monglepick.monglepickbackend.domain.review.mapper;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewLike;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 영화 리뷰 통합 MyBatis Mapper.
 *
 * <p>{@code reviews}, {@code review_likes}, {@code review_votes} 세 테이블의 CRUD + 집계 +
 * 관리자 동적 검색을 통합 담당한다. 이민수 review 도메인 + admin 화면 쿼리까지 포괄한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/review/ReviewMapper.xml}</p>
 *
 * <h3>JPA/MyBatis 하이브리드 (§15)</h3>
 * <ul>
 *   <li>Review {@code @Entity}는 DDL 정의 전용 — 데이터 R/W는 이 Mapper로 100% 처리.</li>
 *   <li>Review는 {@code String userId} + {@code @Transient String nickname} 구조.
 *       목록/상세 조회 시 JOIN users로 nickname을 Review 필드에 주입한다.</li>
 *   <li>ReviewLike / ReviewVote는 단순 한 테이블이므로 동일 Mapper에 통합한다.</li>
 * </ul>
 */
@Mapper
public interface ReviewMapper {

    // ═══ Review 단건 조회 ═══

    /** PK로 리뷰 조회 (없으면 null, nickname 미포함) */
    Review findById(@Param("reviewId") Long reviewId);

    /** PK로 리뷰 + 작성자 닉네임 조회 (JOIN users) */
    Review findByIdWithNickname(@Param("reviewId") Long reviewId);

    // ═══ Review 목록 조회 (JOIN users) ═══

    /** 영화별 리뷰 목록 (페이징, 닉네임 포함, 소프트 삭제 제외) */
    List<Review> findByMovieIdWithNickname(@Param("movieId") String movieId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /** 영화별 리뷰 총 건수 */
    long countByMovieId(@Param("movieId") String movieId);

    /** 사용자별 리뷰 목록 (페이징, 소프트 삭제 제외) */
    List<Review> findByUserId(@Param("userId") String userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 사용자별 유효 리뷰 총 건수 (관리자 활동 카운트용, 소프트 삭제 제외) */
    long countByUserId(@Param("userId") String userId);

    /** 중복 리뷰 존재 여부 확인 — (user_id, movie_id) UNIQUE 제약 사전 검증 */
    boolean existsByUserIdAndMovieId(@Param("userId") String userId,
                                      @Param("movieId") String movieId);

    // ═══ Review 쓰기 ═══

    /** 리뷰 등록 (INSERT) */
    void insert(Review review);

    /** 리뷰 수정 (rating, content) */
    void update(Review review);

    /** 리뷰 소프트 삭제 */
    void softDelete(@Param("reviewId") Long reviewId);

    /** 리뷰 소프트 삭제 복원 (관리자 기능) */
    void restore(@Param("reviewId") Long reviewId);

    /** 리뷰 블라인드 처리 (관리자 기능) */
    void blind(@Param("reviewId") Long reviewId);

    /** 리뷰 블라인드 해제 (관리자 기능) */
    void unblind(@Param("reviewId") Long reviewId);

    /** 리뷰 하드 삭제 (소프트 삭제 권장, 호환용) */
    void deleteById(@Param("reviewId") Long reviewId);

    // ═══ Review 통계 ═══

    /** 전체 리뷰 수 */
    long count();

    /** 지정 기간 내 작성된 리뷰 수 */
    long countByCreatedAtBetween(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 전체 리뷰의 평균 평점 (리뷰 없으면 null) */
    Double findAverageRating();

    /** 지정 기간 내 평균 평점 (해당 기간 리뷰 없으면 null) */
    Double findAverageRatingBetween(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    // ═══ Review 관리자 동적 검색 ═══

    /**
     * 관리자 리뷰 동적 검색 (movieId + minRating + categoryCode, 페이징, 닉네임 포함).
     *
     * <p>각 파라미터가 null이면 해당 필터를 건너뛴다.</p>
     *
     * <p>{@code categoryCode}는 {@link com.monglepick.monglepickbackend.domain.review.entity.ReviewCategoryCode}
     * enum 이름 문자열(THEATER_RECEIPT/COURSE/WORLDCUP/WISHLIST/AI_RECOMMEND/PLAYLIST)이며,
     * 도장깨기 인증 리뷰 모니터링 화면에서 "COURSE" 필터로 활용된다.</p>
     */
    List<Review> searchAdminReviews(@Param("movieId") String movieId,
                                     @Param("minRating") Double minRating,
                                     @Param("categoryCode") String categoryCode,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /** 관리자 리뷰 동적 검색 총 건수 (movieId + minRating + categoryCode) */
    long countAdminReviews(@Param("movieId") String movieId,
                            @Param("minRating") Double minRating,
                            @Param("categoryCode") String categoryCode);

    /** 관리자 리뷰 전체 페이징 조회 (필터 없음, 최신순, 닉네임 포함) */
    List<Review> findAllAdminReviews(@Param("offset") int offset,
                                      @Param("limit") int limit);

    // ═══ ReviewLike ═══

    /** ReviewLike 단건 조회 (없으면 null) */
    ReviewLike findReviewLikeByReviewIdAndUserId(@Param("reviewId") Long reviewId,
                                                   @Param("userId") String userId);

    /** ReviewLike 존재 여부 */
    boolean existsReviewLikeByReviewIdAndUserId(@Param("reviewId") Long reviewId,
                                                  @Param("userId") String userId);

    /** 특정 리뷰의 좋아요 수 */
    long countReviewLikeByReviewId(@Param("reviewId") Long reviewId);

    /** ReviewLike 생성 */
    void insertReviewLike(ReviewLike reviewLike);

    /** ReviewLike 삭제 (hard-delete) */
    void deleteReviewLikeByReviewIdAndUserId(@Param("reviewId") Long reviewId,
                                               @Param("userId") String userId);

    // ═══ ReviewVote ═══

    /** ReviewVote 단건 조회 (UNIQUE(user_id, review_id) 기반, 없으면 null) */
    ReviewVote findReviewVoteByUserIdAndReviewId(@Param("userId") String userId,
                                                   @Param("reviewId") Long reviewId);

    /** 특정 리뷰의 "도움됨" 투표 수 (helpful=true) */
    long countReviewVoteByReviewIdAndHelpfulTrue(@Param("reviewId") Long reviewId);

    /** 특정 리뷰의 "도움안됨" 투표 수 (helpful=false) */
    long countReviewVoteByReviewIdAndHelpfulFalse(@Param("reviewId") Long reviewId);

    /** ReviewVote 생성 (INSERT) */
    void insertReviewVote(ReviewVote reviewVote);

    /** ReviewVote의 helpful 값 갱신 (기존 투표 변경) */
    void updateReviewVoteHelpful(@Param("reviewVoteId") Long reviewVoteId,
                                  @Param("helpful") boolean helpful);
}
