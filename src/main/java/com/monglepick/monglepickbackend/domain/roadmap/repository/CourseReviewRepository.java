package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 도장깨기 코스 리뷰(course_review) JPA Repository.
 *
 * <p>{@link CourseReview} 엔티티 R/W 를 담당한다. 메서드 시그니처는
 * Spring Data JPA 의 메서드 네임 컨벤션을 그대로 활용하며, 파생 쿼리만으로
 * 현재 RoadmapService 가 요구하는 4가지 동작(중복 인증 검사 / 인증 목록 조회 /
 * 인증 카운트 / 신규 저장)을 모두 충족한다.</p>
 *
 * <h3>메서드별 사용처</h3>
 * <ul>
 *     <li>{@link #findByCourseIdAndMovieIdAndUserId(String, String, String)}
 *         — RoadmapService.completeMovieWithReview 의 "이미 인증된 영화" 중복 가드</li>
 *     <li>{@link #findAllByCourseIdAndUserId(String, String)}
 *         — RoadmapService.getCourseDetail 에서 해당 사용자가 코스 내 인증한 영화 ID 목록 추출</li>
 *     <li>{@link #countByCourseIdAndUserId(String, String)}
 *         — RoadmapService.resolveProgressPercent 에서 진행률 계산용 인증 개수 집계</li>
 *     <li>{@code save(CourseReview)} — JpaRepository 기본 메서드 (신규 인증 저장)</li>
 * </ul>
 *
 * <h3>제약</h3>
 * <p>course_review 테이블에는 (course_id, movie_id, user_id) UNIQUE 제약이 있어
 * 동일 코스+영화+사용자 조합으로 두 번 INSERT 시 DB 레이어에서 차단된다.
 * 그럼에도 서비스 레이어에서는 {@link #findByCourseIdAndMovieIdAndUserId} 로 선검증하여
 * 사용자 친화적 응답(이미 인증됨)을 반환한다.</p>
 */
@Repository
public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    /**
     * 특정 코스 + 영화 + 사용자 조합의 리뷰 단건 조회.
     * 중복 인증 가드(이미 인증한 영화에 대한 재요청 차단)에 사용된다.
     */
    Optional<CourseReview> findByCourseIdAndMovieIdAndUserId(String courseId, String movieId, String userId);

    /**
     * 특정 사용자가 특정 코스에서 인증한 모든 리뷰 목록.
     * 코스 상세 화면에서 "내가 본 영화" 표시 / 진행률 표시에 사용된다.
     */
    List<CourseReview> findAllByCourseIdAndUserId(String courseId, String userId);

    /**
     * 특정 사용자가 특정 코스에서 인증한 영화 개수.
     * 진행률 계산({@code completedCount / totalMovies}) 에 사용된다.
     */
    long countByCourseIdAndUserId(String courseId, String userId);
}
