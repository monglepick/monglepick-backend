package com.monglepick.monglepickbackend.domain.playlist.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이리스트 엔티티 — playlist 테이블 매핑.
 *
 * <p>사용자가 생성한 영화 플레이리스트(큐레이션)를 저장한다.
 * 공개/비공개 설정을 지원하며, 플레이리스트에 포함된 영화는
 * {@link PlaylistItem}에서 관리한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseTimeEntity → BaseAuditEntity 변경 (created_by/updated_by 추가)</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 소유자 사용자 ID</li>
 *   <li>{@code playlistName} — 플레이리스트 이름 (필수)</li>
 *   <li>{@code description} — 플레이리스트 설명 (선택)</li>
 *   <li>{@code isPublic} — 공개 여부 (기본값: false)</li>
 * </ul>
 */
@Entity
@Table(name = "playlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseTimeEntity → BaseAuditEntity 변경: created_by, updated_by 컬럼 추가 관리 */
public class Playlist extends BaseAuditEntity {

    /** 플레이리스트 고유 ID (BIGINT AUTO_INCREMENT PK) — PK 컬럼명 변경 없음 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "playlist_id")
    private Long playlistId;

    /**
     * 소유자 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /** 플레이리스트 이름 (필수, 최대 200자) */
    @Column(name = "playlist_name", length = 200, nullable = false)
    private String playlistName;

    /** 플레이리스트 설명 (TEXT 타입, 선택) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 공개 여부.
     * 기본값: false (비공개).
     * true로 설정하면 다른 사용자도 이 플레이리스트를 볼 수 있다.
     */
    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    // ========== Excel Table 기준 추가 컬럼 (2개) ==========

    /**
     * 커버 이미지 URL (최대 500자, nullable).
     * 플레이리스트 목록/상세 화면에서 대표 이미지로 표시된다.
     * 미설정 시 첫 번째 영화 포스터로 대체한다.
     */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /**
     * 좋아요(추천) 수.
     * 기본값: 0.
     * 다른 사용자가 공개 플레이리스트에 좋아요를 누를 때 증가한다.
     */
    @Column(name = "like_count")
    @Builder.Default
    private Integer likeCount = 0;

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 좋아요 수를 1 증가시킨다.
     * 다른 사용자가 이 플레이리스트에 좋아요를 누를 때 호출된다.
     */
    public void incrementLikeCount() {
        this.likeCount = (this.likeCount == null ? 0 : this.likeCount) + 1;
    }

    /**
     * 좋아요 수를 1 감소시킨다 (최소 0).
     * 좋아요를 취소할 때 호출된다. 0 미만으로 내려가지 않도록 보호한다.
     */
    public void decrementLikeCount() {
        this.likeCount = Math.max(0, (this.likeCount == null ? 0 : this.likeCount) - 1);
    }

    /**
     * 플레이리스트 정보를 수정한다 (null-safe).
     *
     * <p>null로 전달된 필드는 기존 값을 유지한다.
     * {@code PUT /api/v1/playlists/{playlistId}} 서비스 레이어에서 호출된다.</p>
     *
     * @param playlistName 변경할 이름 (null이면 기존 값 유지)
     * @param description  변경할 설명 (null이면 기존 값 유지)
     * @param isPublic     변경할 공개 여부 (null이면 기존 값 유지)
     */
    public void update(String playlistName, String description, Boolean isPublic) {
        /* null 전달 시 기존 값을 유지하는 null-safe 패치 방식 */
        if (playlistName != null) {
            this.playlistName = playlistName;
        }
        if (description != null) {
            this.description = description;
        }
        if (isPublic != null) {
            this.isPublic = isPublic;
        }
    }
}
