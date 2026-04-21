package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 월드컵 후보 영화 풀 레포지토리.
 *
 * <p>관리자 측 CRUD + WorldcupService의 활성 후보 조회용으로 사용된다.</p>
 */
public interface WorldcupCandidateRepository extends JpaRepository<WorldcupCandidate, Long> {

    /** 같은 movieId+categoryId 중복 검증 */
    boolean existsByMovieIdAndCategoryCategoryId(String movieId, Long categoryId);

    /** 단건 조회 (movieId+category UNIQUE) */
    Optional<WorldcupCandidate> findByMovieIdAndCategoryCategoryId(String movieId, Long categoryId);

    /** 카테고리 코드별 페이징 조회 (관리자 화면) */
    Page<WorldcupCandidate> findByCategoryCategoryCodeOrderByCreatedAtDesc(String categoryCode, Pageable pageable);

    /** 전체 페이징 (관리자 화면, 카테고리 필터 없음) */
    Page<WorldcupCandidate> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 특정 카테고리에 연결된 후보 영화 수 */
    long countByCategoryCategoryId(Long categoryId);

    /** 특정 카테고리의 활성 후보 영화 수 */
    long countByCategoryCategoryIdAndIsActiveTrue(Long categoryId);

    /** 특정 카테고리의 활성 후보 movieId 목록 */
    @Query("""
            SELECT w.movieId
            FROM WorldcupCandidate w
            WHERE w.isActive = true
              AND w.category.categoryId = :categoryId
            """)
    List<String> findActiveMovieIdsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 활성화된 후보 movieId 목록 조회 (WorldcupService 후보 선택용).
     *
     * <p>{@code is_active=true} 인 후보의 movieId만 반환한다.
     * 카테고리 코드가 null이면 전체 활성 후보, 값이 있으면 해당 카테고리만.</p>
     */
    @Query("""
            SELECT w.movieId
            FROM WorldcupCandidate w
            WHERE w.isActive = true
              AND (:categoryCode IS NULL OR w.category.categoryCode = :categoryCode)
            """)
    List<String> findActiveMovieIdsByCategoryCode(@Param("categoryCode") String categoryCode);

    /**
     * movies.popularity_score 기준으로 인기도 임계값 미만 후보를 일괄 비활성화한다.
     *
     * <p>월드컵 후보 테이블의 비정규화 popularity 컬럼이 아니라
     * movies 테이블의 최신 popularity_score를 직접 비교한다.</p>
     *
     * @param threshold popularity 최소값
     * @return 영향받은 행 수
     */
    @Modifying
    @Query(value = """
            UPDATE worldcup_candidate w
            JOIN movies m ON m.movie_id = w.movie_id
            SET w.is_active = false
            WHERE w.is_active = true
              AND COALESCE(m.popularity_score, 0) < :threshold
            """, nativeQuery = true)
    int deactivateBelowPopularity(@Param("threshold") double threshold);
}
