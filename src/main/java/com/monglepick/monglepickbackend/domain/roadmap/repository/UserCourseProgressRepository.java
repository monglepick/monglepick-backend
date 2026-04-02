package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 코스 진행 현황 레포지토리 — user_course_progress 테이블 접근.
 *
 * <p>사용자별 코스 진행 상태 조회, 신규 진행 레코드 생성, 완주 통계 집계 등을 담당한다.
 * (user_id, course_id) UNIQUE 제약에 대응하는 단건 조회 메서드를 핵심으로 제공한다.</p>
 */
public interface UserCourseProgressRepository extends JpaRepository<UserCourseProgress, Long> {

    /**
     * 특정 사용자의 특정 코스 진행 현황을 단건 조회한다.
     *
     * <p>영화 인증(verifyMovie) 처리 전 기존 진행 레코드 존재 여부를 확인할 때 사용한다.
     * (user_id, course_id) UNIQUE 제약에 의해 결과는 0건 또는 1건이다.</p>
     *
     * @param userId   사용자 ID
     * @param courseId 코스 ID (roadmap_courses.course_id slug 형태)
     * @return 진행 현황 Optional (미시작이면 empty → 서비스 레이어에서 신규 생성)
     */
    Optional<UserCourseProgress> findByUserIdAndCourseId(String userId, String courseId);

    /**
     * 특정 사용자의 전체 코스 진행 현황 목록을 조회한다.
     *
     * <p>마이페이지 도장깨기 탭에서 진행 중/완료 코스 목록을 표시할 때 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 전체 코스 진행 현황 리스트
     */
    List<UserCourseProgress> findByUserId(String userId);

    /**
     * 특정 사용자의 특정 상태 코스 수를 집계한다.
     *
     * <p>완주한 코스 수 집계 시 {@code status=COMPLETED}로 호출한다.
     * 업적 달성 조건(코스 N개 완주) 판정에도 사용된다.</p>
     *
     * @param userId 사용자 ID
     * @param status 집계할 상태 ({@link CourseProgressStatus})
     * @return 해당 상태의 코스 수
     */
    long countByUserIdAndStatus(String userId, CourseProgressStatus status);
}
