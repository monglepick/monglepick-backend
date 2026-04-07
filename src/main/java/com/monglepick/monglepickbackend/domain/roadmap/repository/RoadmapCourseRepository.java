package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 코스 슬러그(course_id) 중복 여부 검사 (관리자 신규 등록 시).
     *
     * <p>course_id 컬럼에 UNIQUE 제약이 있으므로 INSERT 전에 사전 검증한다.</p>
     *
     * @param courseId 검사할 코스 슬러그
     * @return 이미 존재하면 true
     */
    boolean existsByCourseId(String courseId);

    /**
     * 관리자용 — 코스 전체 페이징 조회 (활성/비활성 모두 포함).
     *
     * <p>정렬은 호출자의 {@link Pageable}이 결정한다. 사용자 노출용 조회는
     * is_active=true 필터가 필요하지만, 관리자 화면에서는 모든 코스를 표시한다.</p>
     */
    Page<RoadmapCourse> findAll(Pageable pageable);
}
