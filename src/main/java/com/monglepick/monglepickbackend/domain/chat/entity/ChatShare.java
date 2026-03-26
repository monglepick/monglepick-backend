package com.monglepick.monglepickbackend.domain.chat.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 공유 링크 엔티티 — chat_shares 테이블 매핑 (REQ_055).
 *
 * <p>사용자가 AI 채팅 세션을 외부에 공유할 수 있도록 임시 공유 토큰을 생성한다.
 * 공유 링크는 생성 후 7일간 유효하며, 만료 후에는 접근할 수 없다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code sessionId} — 공유 대상 채팅 세션 ID (FK → chat_session_archive.chat_session_archive_id)</li>
 *   <li>{@code shareToken} — 공유 URL에 사용되는 고유 토큰 (UNIQUE, UUID v4)</li>
 *   <li>{@code expiresAt} — 공유 링크 만료 시각 (생성 후 7일)</li>
 * </ul>
 */
@Entity
@Table(name = "chat_shares", indexes = {
        @Index(name = "idx_chat_shares_token", columnList = "share_token"),
        @Index(name = "idx_chat_shares_session", columnList = "session_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatShare extends BaseAuditEntity {

    /** 공유 링크 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_share_id")
    private Long chatShareId;

    /** 공유 대상 채팅 세션 ID (BIGINT, NOT NULL). chat_session_archive.chat_session_archive_id를 참조한다. */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /**
     * 공유 URL에 사용되는 고유 토큰 (VARCHAR(50), UNIQUE).
     * UUID v4 기반으로 생성되며, /chat/share/{shareToken} 형태로 접근한다.
     */
    @Column(name = "share_token", length = 50, nullable = false, unique = true)
    private String shareToken;

    /** 공유 링크 만료 시각 (생성 후 7일). 만료 후에는 접근 불가. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 공유 링크가 아직 유효한지 확인 */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
