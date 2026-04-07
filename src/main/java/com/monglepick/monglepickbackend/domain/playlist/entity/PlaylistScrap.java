package com.monglepick.monglepickbackend.domain.playlist.entity;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이리스트 스크랩 엔티티 — playlist_scrap 테이블
 *
 * <p>엑셀 설계 첫 번째 시트 19번 테이블 기준.
 * 커뮤니티에 공유된 플레이리스트를 다른 사용자가 자신의 목록으로 가져올 때 기록됩니다.</p>
 *
 * <p>동일 사용자가 동일 플레이리스트를 중복 스크랩할 수 없습니다.
 * (uk_playlist_scrap_user_playlist UNIQUE 제약)</p>
 */
@Entity
@Table(
        name = "playlist_scrap",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_playlist_scrap_user_playlist",
                columnNames = {"user_id", "playlist_id"}
        ),
        indexes = {
                @Index(name = "idx_playlist_scrap_user",     columnList = "user_id"),
                @Index(name = "idx_playlist_scrap_playlist", columnList = "playlist_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistScrap extends BaseAuditEntity {

    /** 스크랩 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;

    /** 스크랩한 사용자 ID (users.user_id 참조) */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 스크랩 대상 플레이리스트 ID (playlist.playlist_id 참조) */
    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @Builder
    public PlaylistScrap(String userId, Long playlistId) {
        this.userId = userId;
        this.playlistId = playlistId;
    }
}
