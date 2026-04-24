package com.monglepick.monglepickbackend.domain.roadmap.entity;

/**
 * 도장깨기 코스 진행 상태 열거형.
 *
 * <p>사용자가 특정 코스({@link RoadmapCourse})를 진행하는 상태를 나타낸다.
 * {@link UserCourseProgress}의 {@code status} 필드에 사용된다.</p>
 *
 * <ul>
 *   <li>{@code IN_PROGRESS}          — 진행 중 (아직 모든 영화를 인증하지 않은 상태)</li>
 *   <li>{@code FINAL_REVIEW_PENDING} — 모든 영화 인증 완료, 최종 감상평 작성 대기 중</li>
 *   <li>{@code COMPLETED}            — 완료 (최종 감상평까지 작성 완료, 리워드 지급 완료)</li>
 * </ul>
 */
public enum CourseProgressStatus {

    /** 코스 진행 중 — 아직 모든 영화 인증이 완료되지 않은 상태 */
    IN_PROGRESS,

    /**
     * 최종 감상평 작성 대기 중 — 모든 영화 인증은 완료됐으나 최종 감상평이 아직 제출되지 않은 상태.
     * 이 상태에서 프론트엔드는 최종 감상평 작성 화면을 표시해야 한다.
     * 최종 감상평 제출 후 COMPLETED로 전환되며 리워드가 지급된다.
     */
    FINAL_REVIEW_PENDING,

    /** 코스 완료 — 최종 감상평 작성까지 완료, 리워드 지급 완료 */
    COMPLETED
}
