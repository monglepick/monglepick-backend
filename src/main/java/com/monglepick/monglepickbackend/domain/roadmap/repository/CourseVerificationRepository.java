package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 도장깨기 인증(course_verification) 도메인 JPA Repository.
 *
 * <p>사용자가 영화 시청을 리뷰/퀴즈/이미지로 인증할 때 레코드를 저장하고,
 * 이미 인증된 영화인지 조회하는 데 사용된다. 관리자 전용 복합 필터 쿼리는
 * {@link com.monglepick.monglepickbackend.admin.repository.AdminCourseVerificationRepository}
 * 에서 담당한다.</p>
 */
@Repository
public interface CourseVerificationRepository extends JpaRepository<CourseVerification, Long> {

    /**
     * 특정 사용자 + 코스 + 영화 조합의 인증 기록 단건 조회.
     * UNIQUE 제약(user_id, course_id, movie_id)에 의해 결과는 최대 1건이다.
     */
    Optional<CourseVerification> findByUserIdAndCourseIdAndMovieId(
            String userId, String courseId, String movieId);

    /**
     * 반려(AUTO_REJECTED / ADMIN_REJECTED) 처리된 영화의 [movieId, decisionReason] 목록 조회.
     * getCourseDetail()의 rejectedMovies 구성 및 completedMovieIds 필터링에 사용된다.
     */
    @Query("SELECT cv.movieId, cv.decisionReason FROM CourseVerification cv " +
           "WHERE cv.userId = :userId AND cv.courseId = :courseId " +
           "AND cv.reviewStatus IN ('ADMIN_REJECTED', 'AUTO_REJECTED')")
    List<Object[]> findRejectedMoviesByUserIdAndCourseId(
            @Param("userId") String userId, @Param("courseId") String courseId);

    /**
     * AI 검증 대기(PENDING) 또는 관리자 검토 필요(NEEDS_REVIEW) 상태 영화 ID 목록 조회.
     * getCourseDetail()의 pendingMovieIds 구성에 사용된다.
     */
    @Query("SELECT cv.movieId FROM CourseVerification cv " +
           "WHERE cv.userId = :userId AND cv.courseId = :courseId " +
           "AND cv.reviewStatus IN ('PENDING', 'NEEDS_REVIEW')")
    List<String> findPendingMovieIdsByUserIdAndCourseId(
            @Param("userId") String userId, @Param("courseId") String courseId);
}
