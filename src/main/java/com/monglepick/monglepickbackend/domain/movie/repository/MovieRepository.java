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
     * 최신 영화 조회 (개봉일 내림차순).
     *
     * <p>홈 페이지 "최신 영화" 섹션과 클라이언트 {@code getLatestMovies()}에서 호출된다.
     * 개봉일이 NULL이거나 포스터 경로가 없는 레코드는 UX 품질 보호를 위해 제외한다.</p>
     *
     * <h4>정렬 기준</h4>
     * <p>{@code release_date DESC}. {@code release_year} 인덱스({@code idx_movies_release_year})가
     * 존재하지만 일(day) 단위 정밀도를 위해 {@code release_date}를 사용한다.
     * 대량 조회 시 성능이 문제되면 {@code release_date} 단독 인덱스 추가를 검토할 것.</p>
     *
     * @param pageable 페이징 정보 (기본 size=8)
     * @return 최신 개봉 영화 페이지
     */
    Page<Movie> findByReleaseDateIsNotNullAndPosterPathIsNotNullOrderByReleaseDateDesc(Pageable pageable);

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

    /**
     * 장르 필터 기반 랜덤 영화 ID 목록 조회 — 이상형 월드컵 후보 선정용.
     *
     * <p>candidateMovieIds를 전달하지 않는 Frontend 요청에 대응하여
     * 서버가 DB에서 직접 후보 영화를 선택할 때 사용한다.</p>
     *
     * <h4>동작 방식</h4>
     * <ul>
     *   <li>{@code genre}가 null이면 장르 조건을 무시하고 전체 영화에서 랜덤 선택한다.</li>
     *   <li>{@code genre}가 지정되면 {@code genres} JSON 배열에 해당 장르가 포함된 영화만 선택한다.</li>
     *   <li>포스터 경로({@code poster_path})와 평점({@code rating})이 모두 존재하는 영화만 대상으로 한다
     *       (월드컵 UI에서 포스터 표시가 필수이므로 NULL 영화 제외).</li>
     *   <li>{@code ORDER BY RAND()}로 매 호출마다 다른 후보가 선택된다.</li>
     * </ul>
     *
     * <h4>성능 참고</h4>
     * <p>{@code ORDER BY RAND()}는 대용량 테이블에서 느릴 수 있다.
     * 157,194건 기준 poster_path + rating 필터 후 약 3,617건 대상이므로 실용적인 속도이다.</p>
     *
     * @param genre 장르명 (nullable — null이면 전체 장르 대상)
     * @param limit 선택할 영화 수 (roundSize와 동일하게 전달)
     * @return 랜덤 선택된 movie_id 목록 (최대 limit 개)
     */
    @Query(value = "SELECT m.movie_id FROM movies m " +
            "WHERE (:genre IS NULL OR JSON_CONTAINS(m.genres, JSON_QUOTE(:genre))) " +
            "AND m.poster_path IS NOT NULL " +
            "AND m.rating IS NOT NULL " +
            "ORDER BY RAND() " +
            "LIMIT :limit",
            nativeQuery = true)
    List<String> findRandomMovieIdsByGenre(@Param("genre") String genre, @Param("limit") int limit);
}
