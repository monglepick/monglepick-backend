package com.monglepick.monglepickbackend.domain.roadmap.mapper;

import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 도장깨기 코스 MyBatis Mapper.
 *
 * <p>roadmap_courses 테이블의 CRUD를 담당한다.
 * SQL 정의: {@code resources/mapper/roadmap/RoadmapCourseMapper.xml}</p>
 */
@Mapper
public interface RoadmapCourseMapper {

    /** PK로 코스 단건 조회 */
    RoadmapCourse findById(@Param("roadmapCourseId") Long roadmapCourseId);

    /** course_id(slug)로 코스 단건 조회 */
    RoadmapCourse findByCourseId(@Param("courseId") String courseId);

    /** 전체 코스 목록 조회 (활성/비활성 모두) */
    List<RoadmapCourse> findAll();

    /** 테마별 코스 목록 조회 */
    List<RoadmapCourse> findByTheme(@Param("theme") String theme);

    /** 관리자용 페이징 조회 (활성/비활성 모두) */
    List<RoadmapCourse> findAllPaged(@Param("offset") int offset, @Param("limit") int limit);

    /** 관리자용 전체 건수 */
    long countAll();

    /** course_id 중복 여부 검사 */
    boolean existsByCourseId(@Param("courseId") String courseId);

    /** 코스 신규 등록 — useGeneratedKeys로 roadmapCourseId 자동 세팅 */
    void insertCourse(RoadmapCourse course);

    /** 코스 수정 (course_id 제외) */
    void updateCourse(RoadmapCourse course);

    /** 활성/비활성 상태만 변경 */
    void updateActiveStatus(@Param("roadmapCourseId") Long roadmapCourseId,
                            @Param("isActive") boolean isActive);
}
