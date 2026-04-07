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

    /** 같은 movieId+category 중복 검증 */
    boolean existsByMovieIdAndCategory(String movieId, String category);

    /** 단건 조회 (movieId+category UNIQUE) */
    Optional<WorldcupCandidate> findByMovieIdAndCategory(String movieId, String category);

    /** 카테고리별 페이징 조회 (관리자 화면) */
    Page<WorldcupCandidate> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    /** 전체 페이징 (관리자 화면, 카테고리 필터 없음) */
    Page<WorldcupCandidate> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 활성화된 후보 movieId 목록 조회 (WorldcupService 후보 선택용).
     *
     * <p>{@code is_active=true} 인 후보의 movieId만 반환한다.
     * 카테고리가 null이면 전체 활성 후보, 값이 있으면 해당 카테고리만.</p>
     */
    @Query("""
            SELECT w.movieId
            FROM WorldcupCandidate w
            WHERE w.isActive = true
              AND (:category IS NULL OR w.category = :category)
            """)
    List<String> findActiveMovieIds(@Param("category") String category);

    /**
     * 인기도 임계값 미만 후보를 일괄 비활성화한다 (관리자 일괄 작업).
     *
     * @param threshold popularity 최소값
     * @return 영향받은 행 수
     */
    @Modifying
    @Query("""
            UPDATE WorldcupCandidate w
            SET w.isActive = false
            WHERE w.popularity < :threshold
              AND w.isActive = true
            """)
    int deactivateBelowPopularity(@Param("threshold") double threshold);
}
