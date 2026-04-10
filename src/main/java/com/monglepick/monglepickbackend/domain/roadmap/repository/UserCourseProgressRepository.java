package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    // ══════════════════════════════════════════════
    // 관리자 통계용 집계 쿼리 (AdminStatsService 섹션 13 — 콘텐츠 성과)
    // ══════════════════════════════════════════════

    /**
     * 전체 완주 코스 수를 집계한다 (관리자 KPI용).
     *
     * <p>status=COMPLETED 인 레코드 전체 수를 반환한다.
     * 특정 사용자 기준이 아닌 전체 합산이다.</p>
     *
     * @param status 집계할 상태 ({@link CourseProgressStatus})
     * @return 해당 상태의 전체 코스 진행 레코드 수
     */
    long countByStatus(CourseProgressStatus status);

    /**
     * courseId별 시작자 수, 완주자 수, 평균 진행률을 집계한다.
     *
     * <p>관리자 통계 "코스별 완주율" 테이블에 사용된다.
     * 반환: [courseId(String), totalStarters(Long), completedCount(Long), avgProgress(Double)]
     * 형태의 Object[] 리스트. completedCount 내림차순 정렬.</p>
     *
     * <p>CASE WHEN으로 COMPLETED 상태만 카운트하여 완주자 수를 계산한다.
     * COALESCE로 평균 진행률이 null일 경우 0.0을 반환하여 NPE를 방지한다.</p>
     *
     * @return [courseId, totalStarters, completedCount, avgProgressPercent] Object[] 리스트
     */
    @Query("""
            SELECT ucp.courseId,
                   COUNT(ucp),
                   SUM(CASE WHEN ucp.status = com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus.COMPLETED THEN 1 ELSE 0 END),
                   COALESCE(AVG(ucp.progressPercent), 0.0)
            FROM UserCourseProgress ucp
            GROUP BY ucp.courseId
            ORDER BY SUM(CASE WHEN ucp.status = com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus.COMPLETED THEN 1 ELSE 0 END) DESC
            """)
    List<Object[]> countGroupByCourseId();
}
