package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 유저 도장깨기 코스 레포지토리 — course 테이블 CRUD.
 *
 * <p>AI가 사용자를 위해 생성한 개인화 영화 도장깨기 코스를 관리한다.
 * 사용자별 코스 목록 조회, 만료 코스 조회 등에 활용된다.</p>
 */
@Repository
public interface UserCourseRepository extends JpaRepository<UserCourse, Long> {

    /**
     * 사용자 ID로 코스 목록을 최신순으로 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자의 코스 목록
     */
    List<UserCourse> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 사용자 ID로 코스 목록을 페이징하여 조회한다.
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 사용자의 코스 목록 페이지
     */
    Page<UserCourse> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 사용자 ID와 테마로 코스를 조회한다.
     *
     * <p>같은 테마의 코스를 중복 생성하지 않기 위해 사전 확인에 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @param courseTheme 코스 테마 (예: 감독, 장르, 시대)
     * @return 해당 사용자의 해당 테마 코스 목록
     */
    List<UserCourse> findByUserIdAndCourseTheme(String userId, String courseTheme);
}
