package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.GenreMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 장르 마스터 레포지토리 — genre_master 테이블 CRUD.
 *
 * <p>장르 코드 및 이름으로 장르 마스터 데이터를 조회한다.
 * 추천 엔진, 필터 목록 API, 온보딩 장르 선택 등에서 활용한다.</p>
 */
@Repository
public interface GenreMasterRepository extends JpaRepository<GenreMaster, Long> {

    /**
     * 장르 코드로 장르를 조회한다.
     *
     * @param genreCode 장르 코드 (예: ACTION, DRAMA, COMEDY)
     * @return 장르 정보 (없으면 empty)
     */
    Optional<GenreMaster> findByGenreCode(String genreCode);

    /**
     * 장르 한국어명으로 장르를 조회한다.
     *
     * @param genreName 장르 한국어명 (예: 액션, 드라마, 코미디)
     * @return 장르 정보 (없으면 empty)
     */
    Optional<GenreMaster> findByGenreName(String genreName);

    /**
     * 장르 코드 존재 여부를 확인한다.
     *
     * @param genreCode 확인할 장르 코드
     * @return 존재하면 true
     */
    boolean existsByGenreCode(String genreCode);

    /**
     * movies.genres JSON 배열을 기준으로 genre_master.contents_count 를 일괄 재계산한다.
     *
     * <p>운영 초기 적재 시 0으로 넣은 contents_count 를 실제 영화 수로 보정할 때 사용한다.
     * 장르명 표기 차이는 SQL 레벨에서 최소한으로 정규화한다.</p>
     *
     * @return UPDATE 된 genre_master 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE genre_master gm
            LEFT JOIN (
                SELECT
                    x.normalized_genre_name,
                    COUNT(DISTINCT x.movie_id) AS movie_count
                FROM (
                    SELECT
                        m.movie_id,
                        CASE
                            WHEN jt.genre_name IN ('모험', '어드벤처') THEN '어드벤처'
                            WHEN jt.genre_name IN ('서부', '서부극(웨스턴)', '서부극') THEN '서부극'
                            WHEN jt.genre_name IN ('공포', '공포(호러)') THEN '공포'
                            WHEN jt.genre_name IN ('음악', '뮤직') THEN '음악'
                            ELSE jt.genre_name
                        END COLLATE utf8mb4_unicode_ci AS normalized_genre_name
                    FROM movies m
                    JOIN JSON_TABLE(
                        m.genres,
                        '$[*]' COLUMNS (
                            genre_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PATH '$'
                        )
                    ) jt
                ) x
                GROUP BY x.normalized_genre_name
            ) cnt
                ON gm.genre_name COLLATE utf8mb4_unicode_ci = cnt.normalized_genre_name COLLATE utf8mb4_unicode_ci
            SET
                gm.contents_count = COALESCE(cnt.movie_count, 0),
                gm.updated_at = NOW(),
                gm.updated_by = 'SYSTEM'
            """, nativeQuery = true)
    int rebuildContentsCountFromMovies();
}
