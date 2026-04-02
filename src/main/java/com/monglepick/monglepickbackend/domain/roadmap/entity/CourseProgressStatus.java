package com.monglepick.monglepickbackend.domain.roadmap.entity;

/**
 * 도장깨기 코스 진행 상태 열거형.
 *
 * <p>사용자가 특정 코스({@link RoadmapCourse})를 진행하는 상태를 나타낸다.
 * {@link UserCourseProgress}의 {@code status} 필드에 사용된다.</p>
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} — 진행 중 (아직 모든 영화를 인증하지 않은 상태)</li>
 *   <li>{@code COMPLETED}   — 완료 (코스 내 모든 영화 인증 완료, 리워드 지급 대상)</li>
 * </ul>
 */
public enum CourseProgressStatus {

    /** 코스 진행 중 — 아직 모든 영화 인증이 완료되지 않은 상태 */
    IN_PROGRESS,

    /** 코스 완료 — 코스 내 모든 영화 인증 완료, 리워드 지급 대상 */
    COMPLETED
}
