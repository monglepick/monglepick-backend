package com.monglepick.monglepickbackend.domain.review.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영화 리뷰 엔티티
 *
 * <p>MySQL reviews 테이블과 매핑됩니다.
 * 사용자가 영화에 대해 작성하는 평점과 리뷰를 저장합니다.</p>
 *
 * <p>한 사용자가 같은 영화에 대해 중복 리뷰를 작성할 수 없습니다.
 * (서비스 레이어에서 검증)</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → reviewId (컬럼명: review_id)</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>수동 createdAt 필드 제거 — BaseTimeEntity에서 상속</li>
 *   <li>@PrePersist onCreate() 메서드 제거 — BaseTimeEntity의 @CreationTimestamp가 처리</li>
 * </ul>
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_user_movie", columnNames = {"user_id", "movie_id"}
        ),
        indexes = {
                @Index(name = "idx_reviews_movie", columnList = "movie_id"),
                @Index(name = "idx_reviews_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseAuditEntity {

    /**
     * 리뷰 고유 식별자 (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: id → reviewId (엔티티 PK 네이밍 통일)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    /** 리뷰 작성자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 리뷰 대상 영화 ID (movies 테이블의 movie_id VARCHAR(50) 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /** 평점 (1.0 ~ 5.0, 0.5 단위) */
    @Column(nullable = false)
    private Double rating;

    /** 리뷰 본문 (TEXT 타입) */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 소프트 삭제 여부 (관리자 콘텐츠 관리: 리뷰 삭제) */
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /** 신고 블라인드 여부 (관리자 콘텐츠 관리: 신고 처리 시 블라인드) */
    @Column(name = "is_blinded", nullable = false)
    private boolean isBlinded = false;

    /** 스포일러 포함 여부 (기본값: false) */
    @Column(name = "spoiler", nullable = false)
    private boolean spoiler = false;

    /** 좋아요 수 (비정규화, 기본값: 0) */
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    /* created_at, updated_at → BaseTimeEntity에서 상속 (수동 createdAt 및 @PrePersist 제거) */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    @Builder
    public Review(User user, String movieId, Double rating, String content,
                  Boolean spoiler, Integer likeCount) {
        this.user = user;
        this.movieId = movieId;
        this.rating = rating;
        this.content = content;
        this.isDeleted = false;
        this.isBlinded = false;
        this.spoiler = spoiler != null ? spoiler : false;
        this.likeCount = likeCount != null ? likeCount : 0;
    }

    /* @PrePersist onCreate() 제거 — BaseTimeEntity의 @CreationTimestamp가 created_at 자동 설정 */

    /** 리뷰 내용 및 평점 수정 */
    public void update(Double rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    /** 소프트 삭제 처리 (관리자 콘텐츠 관리) */
    public void softDelete() {
        this.isDeleted = true;
    }

    /** 소프트 삭제 복원 (관리자 기능) */
    public void restore() {
        this.isDeleted = false;
    }

    /** 신고 블라인드 처리 (관리자 콘텐츠 관리) */
    public void blind() {
        this.isBlinded = true;
    }

    /** 블라인드 해제 (관리자 기능) */
    public void unblind() {
        this.isBlinded = false;
    }
}
