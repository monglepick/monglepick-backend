package com.monglepick.monglepickbackend.domain.recommendation.repository;

import com.monglepick.monglepickbackend.domain.recommendation.entity.UserImplicitRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 암시적 평점 JPA 리포지토리 — user_implicit_rating 테이블 데이터 접근.
 *
 * <p>사용자 행동 기반 암시적 평점의 조회·저장을 담당한다.
 * CF 추천 엔진이 사용자-영화 상호작용 행렬을 구성할 때 이 리포지토리를 통해
 * 데이터를 읽어간다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndMovieId(String, String)} — UPSERT 처리를 위한 단건 조회</li>
 *   <li>{@link #findByUserIdOrderByImplicitScoreDesc(String)} — 유저 전체 암시적 평점 (점수 내림차순)</li>
 * </ul>
 */
public interface UserImplicitRatingRepository extends JpaRepository<UserImplicitRating, Long> {

    /**
     * 사용자 ID와 영화 ID 조합으로 기존 암시적 평점 레코드를 조회한다.
     *
     * <p>행동 기록 시 UPSERT 처리에 사용된다.
     * 레코드가 존재하면 점수와 기여 행동 카운트를 갱신하고,
     * 없으면 새 레코드를 생성한다.</p>
     *
     * <p>user_implicit_rating 테이블의 UNIQUE(user_id, movie_id) 제약과
     * 대응되는 애플리케이션 레벨 조회 메서드이다.</p>
     *
     * @param userId  사용자 ID
     * @param movieId 영화 ID
     * @return 기존 암시적 평점 레코드 (없으면 빈 Optional)
     */
    Optional<UserImplicitRating> findByUserIdAndMovieId(String userId, String movieId);

    /**
     * 특정 사용자의 모든 암시적 평점을 점수 내림차순으로 조회한다.
     *
     * <p>CF 추천 엔진이 해당 사용자의 선호 영화 목록을 구성할 때 사용된다.
     * 점수가 높을수록 해당 영화에 대한 선호도가 높음을 의미한다.</p>
     *
     * @param userId 사용자 ID
     * @return 점수 내림차순 정렬된 암시적 평점 목록 (없으면 빈 리스트)
     */
    List<UserImplicitRating> findByUserIdOrderByImplicitScoreDesc(String userId);
}
