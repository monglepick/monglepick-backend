package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.FavDirector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 선호 감독 JPA 리포지토리.
 *
 * <p>fav_directors 테이블에 대한 CRUD 및 사용자별 조회/삭제 쿼리를 제공한다.
 * {@link FavActorRepository}와 대칭적인 구조로 설계되었다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByUserId} — 사용자의 선호 감독 전체 목록 조회 (온보딩 GET)</li>
 *   <li>{@link #deleteByUserIdAndDirectorName} — 특정 감독 1건 제거 (부분 수정 시)</li>
 *   <li>{@link #deleteByUserId} — 사용자의 전체 선호 감독 삭제 (일괄 재저장 시)</li>
 * </ul>
 */
public interface FavDirectorRepository extends JpaRepository<FavDirector, Long> {

    /**
     * 특정 사용자의 선호 감독 등록 수를 집계한다.
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 선호 감독 수
     */
    long countByUserId(String userId);

    /**
     * 특정 사용자의 선호 감독 목록을 전체 조회한다.
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 선호 감독 레코드 목록 (없으면 빈 리스트)
     */
    List<FavDirector> findByUserId(String userId);

    /**
     * 특정 사용자의 특정 감독 선호 레코드를 삭제한다.
     *
     * <p>UNIQUE(user_id, director_name) 제약이 있으므로 최대 1건 삭제된다.</p>
     *
     * @param userId       사용자 ID
     * @param directorName 삭제할 감독 이름
     */
    void deleteByUserIdAndDirectorName(String userId, String directorName);

    /**
     * 특정 사용자의 선호 감독 레코드를 전부 삭제한다.
     *
     * <p>일괄 재저장(saveDirectors) 시 기존 데이터를 초기화하는 용도로 사용된다.</p>
     *
     * @param userId 사용자 ID
     */
    void deleteByUserId(String userId);
}
