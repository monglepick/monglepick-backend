package com.monglepick.monglepickbackend.domain.userwatchhistory.repository;

import com.monglepick.monglepickbackend.domain.userwatchhistory.entity.UserWatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 실 유저 시청 이력 JPA 리포지토리
 *
 * <p>윤형주 도메인이므로 Phase 2 하이브리드 원칙(설계서 §15)에 따라 JPA Repository 를 유지한다.
 * 김민규/이민수 도메인의 JpaRepository 작성 금지 원칙은 본 리포지토리에 적용되지 않는다.</p>
 *
 * <p>{@code kaggle_watch_history} 테이블(26M Kaggle 시드)과는 완전히 분리된 운영 테이블이며,
 * 본 리포지토리는 오직 {@code user_watch_history} 만 다룬다.</p>
 */
public interface UserWatchHistoryRepository extends JpaRepository<UserWatchHistory, Long> {

    /**
     * 사용자별 시청 이력 페이징 조회.
     *
     * <p>마이페이지 "시청 이력" 탭에서 사용한다. 정렬은 호출 측 Pageable 에서 지정하며,
     * 컨트롤러는 {@code @PageableDefault(sort = "watchedAt", direction = DESC)} 로 최신순을 기본값으로 한다.</p>
     */
    Page<UserWatchHistory> findByUserId(String userId, Pageable pageable);

    /**
     * 시청 이력 단건 조회 (소유권 검증 포함).
     *
     * <p>삭제 API 에서 본인 기록인지 동시에 확인하는 용도. {@code findById} + 별도 owner 체크 대신
     * 한 번의 쿼리로 처리하여 race condition 을 줄인다.</p>
     */
    Optional<UserWatchHistory> findByUserWatchHistoryIdAndUserId(Long userWatchHistoryId, String userId);

    /**
     * 특정 사용자가 특정 영화를 몇 번 시청했는지 카운트.
     *
     * <p>재관람 카운트 표시 (예: "이 영화를 3번 봤어요") 에 사용한다.
     * 같은 영화의 모든 레코드를 합산하므로 중복 허용 정책과 정합한다.</p>
     */
    long countByUserIdAndMovieId(String userId, String movieId);

    /**
     * 행동 분석 배치용 — 특정 사용자의 최근 시청 영화 ID 목록을 추출한다.
     *
     * <p>현재 {@code BehaviorProfileScheduler} 는 reviews 기반으로 동작하므로 본 메서드는
     * 즉시 사용되지 않는다. 향후 시청 이력 신호를 행동 프로파일 입력에 추가하기로 결정될 경우
     * (옵션 γ — reviews ∪ user_watch_history UNION 모드)를 위한 사전 인터페이스이다.</p>
     *
     * @param userId 조회 대상 사용자 ID
     * @param limit  반환할 최대 영화 ID 수 (예: 100)
     * @return 최신 시청 순으로 정렬된 영화 ID 목록 (중복 영화는 1회만 등장)
     */
    @Query(value = """
            SELECT movie_id FROM user_watch_history
            WHERE user_id = :userId
            GROUP BY movie_id
            ORDER BY MAX(watched_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findRecentDistinctMovieIdsByUserId(
            @Param("userId") String userId,
            @Param("limit") int limit
    );
}
