package com.monglepick.monglepickbackend.domain.review.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 투표 엔티티 — review_votes 테이블 매핑.
 *
 * <p>사용자가 영화 리뷰({@link Review})에 대해 "도움이 됐어요 / 도움이 안 됐어요"를
 * 투표한 이력을 저장한다.
 * 동일 사용자가 동일 리뷰에 중복 투표할 수 없으며 (UNIQUE 제약),
 * 기존 투표가 있을 경우 {@link #updateHelpful(boolean)} 도메인 메서드로 값을 변경한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId}   — 투표한 사용자 ID (FK → users.user_id)</li>
 *   <li>{@code reviewId} — 투표 대상 리뷰 ID (FK → reviews.review_id)</li>
 *   <li>{@code helpful}  — true=도움됨, false=도움안됨</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <ul>
 *   <li>UNIQUE(user_id, review_id) — 동일 사용자가 동일 리뷰에 중복 투표 불가</li>
 *   <li>idx_review_votes_review — 리뷰별 투표 집계 최적화</li>
 *   <li>idx_review_votes_user   — 사용자별 투표 이력 조회 최적화</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).</li>
 *   <li>투표 변경은 레코드를 삭제/재생성하지 않고 {@link #updateHelpful(boolean)} 업데이트로 처리한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "review_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vote_user_review",
                columnNames = {"user_id", "review_id"}
        ),
        indexes = {
                /* 리뷰별 투표 수 집계 최적화 */
                @Index(name = "idx_review_votes_review", columnList = "review_id"),
                /* 사용자별 투표 이력 조회 최적화 */
                @Index(name = "idx_review_votes_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class ReviewVote extends BaseAuditEntity {

    /**
     * 리뷰 투표 고유 ID (BIGINT AUTO_INCREMENT PK, 컬럼명: review_vote_id).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_vote_id")
    private Long reviewVoteId;

    /**
     * 투표한 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 투표 대상 리뷰 ID (BIGINT, NOT NULL).
     * reviews.review_id를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    /**
     * 투표 종류.
     * {@code true} = 도움이 됐어요, {@code false} = 도움이 안 됐어요.
     */
    @Column(name = "helpful", nullable = false)
    private Boolean helpful;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 투표 종류를 변경한다.
     *
     * <p>이미 투표한 사용자가 의견을 바꿀 때 호출된다.
     * setter 대신 의미 있는 도메인 메서드로 제공한다.</p>
     *
     * @param helpful 변경할 투표 값 (true=도움됨, false=도움안됨)
     */
    public void updateHelpful(boolean helpful) {
        this.helpful = helpful;
    }
}
