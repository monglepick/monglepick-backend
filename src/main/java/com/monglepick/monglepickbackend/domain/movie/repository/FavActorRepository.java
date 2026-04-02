package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.FavActor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 선호 배우 JPA 리포지토리.
 *
 * <p>fav_actors 테이블에 대한 CRUD 및 사용자별 조회/삭제 쿼리를 제공한다.
 * {@link FavDirectorRepository}와 대칭적인 구조로 설계되었다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByUserId} — 사용자의 선호 배우 전체 목록 조회 (온보딩 GET)</li>
 *   <li>{@link #deleteByUserIdAndActorName} — 특정 배우 1건 제거 (부분 수정 시)</li>
 *   <li>{@link #deleteByUserId} — 사용자의 전체 선호 배우 삭제 (일괄 재저장 시)</li>
 * </ul>
 */
public interface FavActorRepository extends JpaRepository<FavActor, Long> {

    /**
     * 특정 사용자의 선호 배우 목록을 전체 조회한다.
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 선호 배우 레코드 목록 (없으면 빈 리스트)
     */
    List<FavActor> findByUserId(String userId);

    /**
     * 특정 사용자의 특정 배우 선호 레코드를 삭제한다.
     *
     * <p>UNIQUE(user_id, actor_name) 제약이 있으므로 최대 1건 삭제된다.</p>
     *
     * @param userId    사용자 ID
     * @param actorName 삭제할 배우 이름
     */
    void deleteByUserIdAndActorName(String userId, String actorName);

    /**
     * 특정 사용자의 선호 배우 레코드를 전부 삭제한다.
     *
     * <p>일괄 재저장(saveActors) 시 기존 데이터를 초기화하는 용도로 사용된다.</p>
     *
     * @param userId 사용자 ID
     */
    void deleteByUserId(String userId);
}
