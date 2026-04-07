package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 도장깨기 코스 레포지토리 — roadmap_courses 테이블 접근.
 *
 * <p>코스 목록 조회, courseId(slug) 기반 단건 조회, 테마 필터 등을 제공한다.
 * PK는 {@code roadmapCourseId}(BIGINT)이며,
 * 비즈니스 식별자는 {@code courseId}(VARCHAR(50), UNIQUE) 슬러그 형태이다.</p>
 *
 * <h3>주요 쿼리 패턴</h3>
 * <ul>
 *   <li>{@link #findByCourseId} — 슬러그 기반 단건 조회 (API 경로 파라미터에 사용)</li>
 *   <li>{@link #findByTheme}   — 테마 필터 목록 조회</li>
 * </ul>
 */
@Repository
public interface RoadmapCourseRepository extends JpaRepository<RoadmapCourse, Long> {

    /**
     * 코스 슬러그(courseId)로 단건 조회한다.
     *
     * <p>API 경로 파라미터 {@code {courseId}}가 slug 형태이므로
     * PK(BIGINT)가 아닌 {@code course_id} 컬럼으로 조회한다.
     * UNIQUE 제약이 있으므로 결과는 0건 또는 1건이다.</p>
     *
     * @param courseId 코스 슬러그 (예: "nolan-filmography")
     * @return 코스 Optional (존재하지 않으면 empty)
     */
    Optional<RoadmapCourse> findByCourseId(String courseId);

    /**
     * 테마별 코스 목록을 전체 조회한다.
     *
     * <p>프론트엔드에서 테마 탭(예: "감독별", "장르별", "시대별")으로 필터링할 때 사용한다.
     * 데이터 규모가 크지 않아 페이징 없이 전체 반환한다.</p>
     *
     * @param theme 테마 문자열 (예: "감독별", "장르별")
     * @return 해당 테마의 코스 목록 (순서 보장 없음)
     */
    List<RoadmapCourse> findByTheme(String theme);
}
