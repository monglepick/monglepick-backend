package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 도장깨기 마무리 리뷰 엔티티 — course_final_movie 테이블
 *
 * <p>엑셀 설계 첫 번째 시트 23번 테이블 기준 (담당: 김민규).
 * 도장깨기 코스를 모두 완료한 후 사용자가 작성하는 최종 감상문을 저장합니다.</p>
 *
 * <p>course_review(개별 영화 인증 리뷰)와 구분됩니다.
 * 마무리 리뷰는 코스 전체에 대한 총평입니다.</p>
 */
@Entity
@Table(
        name = "course_final_movie",
        indexes = {
                @Index(name = "idx_course_final_course", columnList = "course_id"),
                @Index(name = "idx_course_final_user",   columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseFinalMovie extends BaseAuditEntity {

    /** 마무리 리뷰 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "final_review_id")
    private Long finalReviewId;

    /** 대상 코스 ID (roadmap_course.course_id 참조) */
    @Column(name = "course_id", nullable = false, length = 50)
    private String courseId;

    /** 작성자 사용자 ID (users.user_id 참조) */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 도장깨기 마무리 총평 본문 */
    @Column(name = "final_review_text", columnDefinition = "TEXT")
    private String finalReviewText;

    /** 마무리 리뷰 인증 완료 여부 (기본값: false) */
    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted = false;

    /** 마무리 리뷰 인증 완료 시각 (완료 전: null) */
    @Column(name = "complete_at")
    private LocalDateTime completeAt;

    @Builder
    public CourseFinalMovie(String courseId, String userId, String finalReviewText) {
        this.courseId = courseId;
        this.userId = userId;
        this.finalReviewText = finalReviewText;
        this.isCompleted = false;
    }

    /** 마무리 리뷰 인증 완료 처리 */
    public void complete() {
        this.isCompleted = true;
        this.completeAt = LocalDateTime.now();
    }
}
