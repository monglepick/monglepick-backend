package com.monglepick.monglepickbackend.domain.watchhistory.repository;

import com.monglepick.monglepickbackend.domain.watchhistory.entity.WatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 시청 이력 JPA 리포지토리
 *
 * <p>대용량 테이블(26M+ 행)이므로 반드시 페이징을 사용해야 합니다.</p>
 */
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    /**
     * 사용자별 시청 이력 조회 (페이징 필수).
     *
     * <p>대용량 테이블이므로 Pageable 없이 전체 조회는 금지한다.</p>
     */
    Page<WatchHistory> findByUser_UserId(String userId, Pageable pageable);

    /**
     * 행동 프로필 배치용 — 최근 시청 이력 최대 100건 조회.
     *
     * <p>BehaviorProfileScheduler에서 장르·감독 친화도 계산에 사용한다.
     * 대용량 테이블 부하를 최소화하기 위해 100건으로 제한하며,
     * JPQL로 user 연관 객체를 즉시 로딩(JOIN FETCH)하여 N+1을 방지한다.</p>
     *
     * @param userId 조회 대상 사용자 ID
     * @param pageable 페이징 설정 (PageRequest.of(0, 100) 고정 사용 권장)
     * @return 최신순 정렬된 시청 이력 목록
     */
    @Query("SELECT wh FROM WatchHistory wh WHERE wh.user.userId = :userId ORDER BY wh.watchedAt DESC")
    List<WatchHistory> findRecentByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * 행동 프로필 배치용 — 특정 유저의 영화 ID 목록만 최근순으로 조회.
     *
     * <p>영화 엔티티 조인 없이 movie_id 값만 추출하여 집계 성능을 최적화한다.
     * 장르/감독 친화도 계산 시 movies 테이블과의 추가 조인이 필요할 경우
     * 이 메서드로 ID 목록을 먼저 얻은 뒤 Movie 리포지토리를 별도 조회한다.</p>
     *
     * @param userId   조회 대상 사용자 ID
     * @param pageable 페이징 설정 (PageRequest.of(0, 100) 고정 사용 권장)
     * @return movie_id 문자열 목록
     */
    @Query("SELECT wh.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId ORDER BY wh.watchedAt DESC")
    List<String> findRecentMovieIdsByUserId(@Param("userId") String userId, Pageable pageable);
}
