package com.monglepick.monglepickbackend.domain.review.mapper;

import com.monglepick.monglepickbackend.domain.review.dto.MyReviewSummaryRow;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewLike;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** PK 존재 여부만 확인 (관리자 삭제 등 가벼운 존재 검증용) */
    boolean existsById(@Param("reviewId") Long reviewId);

    /** 리뷰의 소프트 삭제 여부만 조회 (없으면 null) */
    Boolean isDeletedById(@Param("reviewId") Long reviewId);

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

    /**
     * (user_id, movie_id) 활성 리뷰 단건 조회 — 추천 카드 UPSERT 분기용.
     *
     * <p>{@code is_deleted = false} 조건. 소프트 삭제된 리뷰는 무시하여
     * 추천 카드에서 새 별점 입력 시 별도 신규 리뷰로 작성되도록 한다.
     * {@code ReviewService#createOrUpdateFromRecommendation} 에서 사용.</p>
     */
    Review findByUserIdAndMovieId(@Param("userId") String userId,
                                   @Param("movieId") String movieId);

    /**
     * (user_id, [movieIdList]) 활성 리뷰 배치 조회 — 추천 이력 페이지 별점 복원용.
     *
     * <p>{@code is_deleted = false} 조건. {@code RecommendationHistoryService} 가
     * 추천 이력 페이지에 표시할 페이지(20건) 단위로 배치 조회하여 N+1 회피.
     * 빈 리스트가 들어오면 빈 결과 반환 (Mapper XML 측 if-test 가드).</p>
     */
    List<Review> findByUserIdAndMovieIds(@Param("userId") String userId,
                                          @Param("movieIds") java.util.Collection<String> movieIds);

    // ═══ Review 쓰기 ═══

    /** 리뷰 등록 (INSERT) */
    void insert(Review review);

    /** 리뷰 수정 (rating, content) */
    void update(Review review);

    /** 리뷰 소프트 삭제 */
    int softDelete(@Param("reviewId") Long reviewId);

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

    // ══════════════════════════════════════════════
    // 관리자 통계용 집계 쿼리 (AdminStatsService 섹션 13, 14 — 콘텐츠 성과/전환 퍼널)
    // ══════════════════════════════════════════════

    /**
     * review_category_code별 리뷰 건수를 집계한다.
     *
     * <p>관리자 통계 "리뷰 품질 — 카테고리별 건수" 차트에 사용된다.
     * 소프트 삭제(is_deleted=true)된 리뷰는 제외한다.
     * 반환: [{categoryCode, cnt}] 형태의 Map 리스트.</p>
     *
     * @return categoryCode별 건수 맵 리스트 (cnt 내림차순)
     */
    List<Map<String, Object>> countGroupByCategory();

    /**
     * 평점(1~5점)별 리뷰 건수를 집계한다.
     *
     * <p>관리자 통계 "리뷰 품질 — 평점 분포" 차트에 사용된다.
     * 소프트 삭제(is_deleted=true)된 리뷰는 제외한다.
     * 반환: [{rating, cnt}] 형태의 Map 리스트.</p>
     *
     * @return 평점별 건수 맵 리스트 (rating 오름차순)
     */
    List<Map<String, Object>> countGroupByRating();

    /**
     * 지정 기간 내 리뷰를 작성한 고유 사용자 수를 반환한다 (전환 퍼널 단계 4용).
     *
     * <p>DISTINCT user_id 로 중복을 제거하여 실제로 리뷰를 작성한 고유 사용자만 카운트한다.
     * 소프트 삭제된 리뷰도 포함한다(퍼널 분석은 실제 발생량 기준).</p>
     *
     * @param start 기간 시작 시각
     * @param end   기간 종료 시각
     * @return 해당 기간 리뷰 작성 고유 사용자 수
     */
    long countDistinctUserByCreatedAtBetween(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    // ═══ 내 리뷰 목록 조회 — 고객센터 AI 봇 진단용 (v4 신규, 2026-04-28) ═══

    /**
     * 사용자 본인의 리뷰를 날짜 필터 + 페이징으로 조회한다 (영화 제목 LEFT JOIN 포함).
     *
     * <p>{@code GET /api/v1/users/me/reviews} 에서 사용. reviews LEFT JOIN movies 로
     * 영화 제목을 한 번에 가져온다. 소프트 삭제(is_deleted=true) 제외.</p>
     *
     * @param userId    사용자 ID (JWT 강제 주입)
     * @param since     조회 시작 시각 (reviews.created_at >= since)
     * @param offset    페이징 오프셋 (0-based)
     * @param limit     페이지 크기
     * @return 리뷰 + 영화 제목 행 목록 (created_at DESC)
     */
    List<MyReviewSummaryRow> findMyReviewsWithTitle(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 날짜 필터 조건의 사용자 리뷰 총 건수를 반환한다 (페이징 total 계산용).
     *
     * <p>소프트 삭제(is_deleted=true) 제외.</p>
     *
     * @param userId 사용자 ID
     * @param since  조회 시작 시각
     * @return 조건에 맞는 총 리뷰 건수
     */
    long countMyReviewsSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);

    /**
     * 사용자가 리뷰를 작성한 영화들에서 중복 없는 장르 수를 집계한다 (genre_explorer 업적용).
     *
     * <p>movies.genres JSON 배열(예: ["액션","SF"])을 MySQL JSON_TABLE로 펼쳐
     * DISTINCT 장르 개수를 반환한다. 소프트 삭제된 리뷰는 제외한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자가 탐험한 고유 장르 수
     */
    long countDistinctExploredGenres(@Param("userId") String userId);

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
