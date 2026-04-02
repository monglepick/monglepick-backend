package com.monglepick.monglepickbackend.domain.movie.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영화 좋아요 엔티티 — likes 테이블 매핑.
 *
 * <p>사용자가 특정 영화에 좋아요를 누른 기록을 저장한다.
 * 소프트 삭제(deleted_at)를 지원하여, 좋아요 취소 시 레코드를 삭제하지 않고
 * deleted_at에 삭제 시각을 기록한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID</li>
 *   <li>{@code movieId} — 영화 ID</li>
 *   <li>{@code deletedAt} — 소프트 삭제 시각 (null이면 활성 좋아요)</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(user_id, movie_id) — 동일 사용자가 동일 영화에 중복 좋아요 불가.</p>
 *
 * <h3>타임스탬프</h3>
 * <p>BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리.
 * 기존 수동 @CreationTimestamp created_at 필드 제거됨.
 * deletedAt은 소프트 삭제용 도메인 고유 필드이므로 유지.</p>
 */
@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — PK 필드명: id → likeId로 변경 (DDL 컬럼명 like_id 매핑)
 * — 수동 @CreationTimestamp created_at 필드 제거됨
 * — deletedAt은 소프트 삭제용 도메인 고유 필드이므로 유지
 */
public class Like extends BaseAuditEntity {

    /** 좋아요 레코드 고유 ID (PK, BIGINT AUTO_INCREMENT, 컬럼명: like_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 영화 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 참조한다.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /* created_at은 BaseAuditEntity(→BaseTimeEntity)에서 자동 관리 — 수동 @CreationTimestamp 필드 제거됨 */

    /**
     * 소프트 삭제 시각 (nullable).
     * null이면 활성 좋아요, 값이 있으면 좋아요가 취소된 상태.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 이름 사용)
    // ─────────────────────────────────────────────

    /**
     * 좋아요를 소프트 삭제(취소) 처리한다.
     *
     * <p>deleted_at에 현재 시각을 기록하여 논리적으로 삭제된 상태로 전환한다.
     * 물리적으로 레코드를 삭제하지 않아 이력 조회가 가능하다.</p>
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 취소된 좋아요를 복구(재활성화)한다.
     *
     * <p>deleted_at을 null로 초기화하여 활성 좋아요 상태로 되돌린다.
     * 기존 레코드를 재사용하므로 UNIQUE(user_id, movie_id) 제약을 위반하지 않는다.</p>
     */
    public void restore() {
        this.deletedAt = null;
    }
}
