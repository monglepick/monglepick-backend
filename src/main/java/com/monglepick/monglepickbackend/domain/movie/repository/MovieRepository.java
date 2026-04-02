package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 영화 JPA 리포지토리
 *
 * <p>MySQL movies 테이블에 대한 데이터 접근 레이어입니다.
 * 157,194건의 영화 데이터를 조회합니다.</p>
 *
 * <p>복잡한 검색(전문검색, 유사도 검색)은 Elasticsearch/Qdrant를 사용하며,
 * 이 리포지토리는 RDB 기반의 기본 조회에만 사용됩니다.</p>
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {

    /**
     * TMDB ID로 영화를 조회합니다.
     * <p>외부 API 연동 시 TMDB ID 기반으로 영화를 찾을 때 사용됩니다.</p>
     *
     * @param tmdbId TMDB 영화 ID
     * @return 영화 Optional
     */
    Optional<Movie> findByTmdbId(Long tmdbId);

    /**
     * 제목으로 영화를 검색합니다. (한국어/영어 제목 모두 검색)
     * <p>LIKE 검색을 사용하며, 간단한 제목 검색에 사용됩니다.
     * 고급 검색은 Elasticsearch를 통해 수행합니다.</p>
     *
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 페이지 단위의 영화 검색 결과
     */
    @Query("SELECT m FROM Movie m WHERE m.title LIKE %:keyword% OR m.titleEn LIKE %:keyword%")
    Page<Movie> searchByTitle(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 인기 영화 조회 (평점 내림차순, 평점 NULL 제외).
     * <p>홈 페이지 "인기 영화" 섹션에서 사용됩니다.</p>
     *
     * @param pageable 페이징 정보
     * @return 평점순 정렬된 영화 페이지
     */
    Page<Movie> findByRatingIsNotNullOrderByRatingDesc(Pageable pageable);

    /**
     * 행동 프로필 배치용 — movie_id 목록에 해당하는 영화의 genres, director 필드만 일괄 조회.
     *
     * <p>BehaviorProfileScheduler에서 장르·감독 친화도 계산 시 사용한다.
     * IN 절로 최대 100건을 한 번에 조회하므로 N+1 문제가 발생하지 않는다.
     * genres, director 필드만 필요하지만 Movie 전체를 반환하여 호출 측에서 선택 사용한다.</p>
     *
     * @param movieIds 조회할 movie_id 목록 (최대 100건 권장)
     * @return 해당 영화 목록 (존재하지 않는 ID는 결과에서 제외됨)
     */
    @Query("SELECT m FROM Movie m WHERE m.movieId IN :movieIds")
    List<Movie> findAllByMovieIdIn(@Param("movieIds") List<String> movieIds);
}
