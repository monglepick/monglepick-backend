package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 전용 리뷰(Review) JPA 리포지토리.
 *
 * <p>관리자 콘텐츠 관리 화면에서 영화 ID·최소 평점 조합 필터 조회가 필요하므로
 * 기존 {@code ReviewRepository}에 없는 JPQL 쿼리를 별도로 정의한다.</p>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>기존 {@code ReviewRepository}를 수정하지 않고 Admin 전용 리포지토리를 분리하여
 *       도메인 레이어와 관리자 레이어 간 의존성 방향을 단방향으로 유지한다.</li>
 *   <li>모든 조회 메서드에 {@code JOIN FETCH r.user}를 적용하여 N+1 문제를 방지한다.</li>
 * </ul>
 */
public interface AdminReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 영화 ID·최소 평점 조합 필터로 리뷰 목록을 최신순 페이징 조회한다.
     *
     * <p>각 파라미터가 null이면 해당 조건을 무시한다 (동적 필터).
     * JPQL에서 {@code :movieId IS NULL} 조건으로 null 파라미터를 처리한다.</p>
     *
     * <h3>파라미터 조합 예시</h3>
     * <ul>
     *   <li>movieId=null,  minRating=null  → 전체 리뷰 최신순</li>
     *   <li>movieId="tt123", minRating=null → 해당 영화 전체 리뷰</li>
     *   <li>movieId=null,  minRating=4.0   → 모든 영화 중 평점 4.0 이상</li>
     *   <li>movieId="tt123", minRating=4.0 → 해당 영화 중 평점 4.0 이상</li>
     * </ul>
     *
     * @param movieId   영화 ID 필터 (null이면 조건 무시)
     * @param minRating 최소 평점 필터 (null이면 조건 무시)
     * @param pageable  페이지 정보 (page, size)
     * @return 조건에 맞는 리뷰 페이지 (User 즉시 로딩 포함)
     */
    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.user
            WHERE (:movieId   IS NULL OR r.movieId = :movieId)
              AND (:minRating IS NULL OR r.rating >= :minRating)
            ORDER BY r.createdAt DESC
            """)
    Page<Review> findByFilters(
            @Param("movieId")   String movieId,
            @Param("minRating") Double minRating,
            Pageable pageable
    );
}
