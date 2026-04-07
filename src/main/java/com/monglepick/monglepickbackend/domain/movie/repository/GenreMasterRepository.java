package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.GenreMaster;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
