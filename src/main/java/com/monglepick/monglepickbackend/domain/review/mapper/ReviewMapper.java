package com.monglepick.monglepickbackend.domain.review.mapper;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 영화 리뷰 MyBatis Mapper.
 *
 * <p>reviews 테이블의 CRUD를 담당한다.
 * 한 사용자가 같은 영화에 중복 리뷰를 작성할 수 없다 (UNIQUE 제약).</p>
 *
 * <p>SQL 정의: {@code resources/mapper/review/ReviewMapper.xml}</p>
 */
@Mapper
public interface ReviewMapper {

    /** PK로 리뷰 조회 */
    Review findById(@Param("reviewId") Long reviewId);

    /** 리뷰 + 작성자 정보 함께 조회 (JOIN users) */
    Review findByIdWithUser(@Param("reviewId") Long reviewId);

    /** 영화별 리뷰 목록 + 작성자 (페이징) */
    List<Review> findByMovieIdWithUser(@Param("movieId") String movieId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 영화별 리뷰 총 건수 */
    long countByMovieId(@Param("movieId") String movieId);

    /** 사용자별 리뷰 목록 (마이페이지, 페이징) */
    List<Review> findByUserId(@Param("userId") String userId,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /** 중복 리뷰 존재 여부 확인 */
    boolean existsByUserIdAndMovieId(@Param("userId") String userId,
                                     @Param("movieId") String movieId);

    /** 리뷰 등록 (INSERT) */
    void insert(Review review);

    /** 리뷰 수정 (평점, 본문) */
    void update(Review review);

    /** 리뷰 삭제 */
    void deleteById(@Param("reviewId") Long reviewId);

    /** 리뷰 소프트 삭제 */
    void softDelete(@Param("reviewId") Long reviewId);

    /** 리뷰 블라인드 처리 */
    void blind(@Param("reviewId") Long reviewId);
}
