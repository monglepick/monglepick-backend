package com.monglepick.monglepickbackend.domain.playlist.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
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

/**
 * 플레이리스트 좋아요 엔티티 — playlist_likes 테이블 매핑.
 *
 * <p>사용자가 공개 플레이리스트({@code playlist})에 누른 좋아요를 기록한다.
 * 동일 사용자가 동일 플레이리스트에 중복 좋아요를 누를 수 없다 (UNIQUE 제약).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code playlistId} — 좋아요 대상 플레이리스트 ID (FK → playlist.playlist_id)</li>
 *   <li>{@code userId}     — 좋아요를 누른 사용자 ID (FK → users.user_id)</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(playlist_id, user_id) — 동일 사용자가 동일 플레이리스트에 중복 좋아요 불가</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>비공개(isPublic=false) 플레이리스트에 대한 좋아요는 서비스 레이어에서 차단한다.</li>
 *   <li>좋아요 수는 이 테이블의 COUNT 집계 또는 playlist 테이블의 별도 like_count
 *       캐시 컬럼을 서비스 레이어에서 동기화하는 방식으로 관리한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "playlist_likes",
        uniqueConstraints = {
                /* 동일 사용자 동일 플레이리스트 중복 좋아요 방지 */
                @UniqueConstraint(
                        name = "uk_playlist_likes_playlist_user",
                        columnNames = {"playlist_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class PlaylistLike extends BaseAuditEntity {

    /**
     * 플레이리스트 좋아요 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "playlist_like_id")
    private Long playlistLikeId;

    /**
     * 좋아요 대상 플레이리스트 ID (BIGINT, NOT NULL).
     * playlist.playlist_id를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    /**
     * 좋아요를 누른 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
