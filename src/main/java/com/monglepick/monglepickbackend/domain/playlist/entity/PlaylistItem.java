package com.monglepick.monglepickbackend.domain.playlist.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 플레이리스트 아이템 엔티티 — playlist_item 테이블 매핑.
 *
 * <p>{@link Playlist}에 포함된 개별 영화 항목을 저장한다.
 * 동일 플레이리스트에 동일 영화를 중복 추가할 수 없다.
 * sort_order로 영화의 정렬 순서를 지정할 수 있다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseAuditEntity 상속 추가 (created_at/updated_at/created_by/updated_by 자동 관리)</li>
 *   <li>2026-03-24: PK 필드명 itemId → playlistItemId 로 변경, @Column(name = "playlist_item_id") 추가</li>
 *   <li>2026-03-24: addedAt의 @CreationTimestamp 제거 — 도메인 고유 타임스탬프로 유지, 서비스 레이어에서 설정</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code playlistId} — 플레이리스트 ID (FK → playlist.playlist_id)</li>
 *   <li>{@code movieId} — 영화 ID</li>
 *   <li>{@code sortOrder} — 정렬 순서 (기본값: 0)</li>
 *   <li>{@code addedAt} — 아이템 추가 시각 (도메인 고유 타임스탬프, BaseAuditEntity의 created_at과 별도)</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(playlist_id, movie_id) — 동일 플레이리스트 내 영화 중복 불가.</p>
 */
@Entity
@Table(
        name = "playlist_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"playlist_id", "movie_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속 추가: created_at, updated_at, created_by, updated_by 컬럼 자동 관리 */
public class PlaylistItem extends BaseAuditEntity {

    /**
     * 플레이리스트 아이템 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 기존 필드명 'itemId'에서 'playlistItemId'로 변경하여 엔티티 식별 명확화.
     * 기존 컬럼명 'item_id'에서 'playlist_item_id'로 변경.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "playlist_item_id")
    private Long playlistItemId;

    /**
     * 플레이리스트 ID (BIGINT, NOT NULL).
     * playlist.playlist_id를 참조한다.
     */
    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    /**
     * 영화 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 참조한다.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /**
     * 정렬 순서.
     * 기본값: 0.
     * 낮은 숫자일수록 앞쪽에 표시된다.
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * 아이템 추가 시각 (도메인 고유 타임스탬프).
     * BaseAuditEntity의 created_at과는 별도로, 플레이리스트에 아이템이 추가된 시점을 기록.
     * 서비스 레이어에서 직접 설정한다.
     */
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;

    /** 영화 제목 — DB 컬럼 아님, JOIN movies 조회 시 MyBatis가 주입 */
    @Transient
    private String title;

    /** 포스터 이미지 경로 — DB 컬럼 아님, JOIN movies 조회 시 MyBatis가 주입 */
    @Transient
    private String posterPath;

    /** 평균 평점 — DB 컬럼 아님, JOIN movies 조회 시 MyBatis가 주입 */
    @Transient
    private Double rating;
}
