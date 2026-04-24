package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseFinalMovie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 도장깨기 최종 감상평 리포지토리 — course_final_movie 테이블.
 *
 * <p>사용자가 모든 영화 인증을 완료한 후 작성하는 코스 전체 총평을 관리한다.</p>
 */
public interface CourseFinalMovieRepository extends JpaRepository<CourseFinalMovie, Long> {

    /** 특정 사용자의 특정 코스 최종 감상평 조회 */
    Optional<CourseFinalMovie> findByCourseIdAndUserId(String courseId, String userId);

    /** 특정 사용자의 특정 코스 최종 감상평 존재 여부 확인 (중복 제출 방지) */
    boolean existsByCourseIdAndUserId(String courseId, String userId);
}
