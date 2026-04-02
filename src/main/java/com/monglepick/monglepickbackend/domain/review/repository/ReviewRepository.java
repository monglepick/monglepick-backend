package com.monglepick.monglepickbackend.domain.review.repository;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 JPA 리포지토리
 *
 * <p>영화 리뷰의 CRUD 기능을 제공합니다.
 * 중복 리뷰 방지를 위한 존재 여부 확인 메서드를 포함합니다.</p>
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {
    /** 특정 영화의 리뷰 목록 조회 (전체 — 소량 조회용) */
    List<Review> findByMovieId(String movieId);

    /** 특정 영화의 리뷰 목록 페이징 조회 (대량 데이터 안전) */
    Page<Review> findByMovieId(String movieId, Pageable pageable);

    /** 사용자별 리뷰 목록 조회 (마이페이지용) */
    Page<Review> findByUser_UserId(String userId, Pageable pageable);

    /** 중복 리뷰 존재 여부 확인 (같은 사용자가 같은 영화에 2개 이상 작성 방지) */
    boolean existsByUser_UserIdAndMovieId(String userId, String movieId);

    /**
     * 생성 시각 범위 내 작성된 리뷰 수를 카운트한다.
     *
     * <p>관리자 통계 탭에서 일별 신규 리뷰 수 집계에 사용된다.</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 해당 범위 내 리뷰 수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 전체 리뷰의 평균 평점을 반환한다.
     *
     * <p>관리자 통계 탭 KPI 카드의 "서비스 평균 평점" 계산에 사용된다.
     * rating이 null인 리뷰는 AVG 집계 대상에서 자동 제외된다.
     * 리뷰가 없는 경우 null을 반환하므로 서비스 레이어에서 null-safe 처리가 필요하다.</p>
     *
     * @return 전체 리뷰 평균 평점 (리뷰 없으면 null)
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.rating IS NOT NULL")
    Double findAverageRating();

    /**
     * 특정 기간 내 작성된 리뷰의 평균 평점을 반환한다.
     *
     * <p>기간별 서비스 평점 추이 분석에 활용 가능하다.</p>
     *
     * @param start 범위 시작 시각
     * @param end   범위 종료 시각
     * @return 해당 기간 평균 평점 (해당 기간 리뷰 없으면 null)
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.rating IS NOT NULL AND r.createdAt BETWEEN :start AND :end")
    Double findAverageRatingBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
