package com.monglepick.monglepickbackend.domain.chat.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 세션 아카이브 엔티티 — chat_session_archive 테이블 매핑.
 *
 * <p>AI Agent와의 채팅 세션이 종료된 후, Redis에서 MySQL로 아카이빙된 대화 기록을 저장한다.
 * 실시간 대화는 Redis에서 관리되며, 세션 종료/만료 시 이 테이블로 영속화된다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code user} — 대화 참여 사용자 (FK → users.user_id)</li>
 *   <li>{@code sessionId} — 세션 UUID (UNIQUE, Redis 세션 키와 동일)</li>
 *   <li>{@code messages} — 전체 대화 내역 (JSON 배열, 필수)</li>
 *   <li>{@code turnCount} — 대화 턴 수 (사용자 메시지 수)</li>
 *   <li>{@code intentSummary} — 세션 중 감지된 의도 요약 (JSON)</li>
 *   <li>{@code startedAt} — 세션 시작 시각 (도메인 고유 필드, 유지)</li>
 *   <li>{@code endedAt} — 세션 종료 시각 (도메인 고유 필드, 유지)</li>
 * </ul>
 *
 * <h3>messages JSON 구조</h3>
 * <pre>
 * [
 *   {"role": "user", "content": "우울한데 영화 추천해줘", "timestamp": "..."},
 *   {"role": "assistant", "content": "...", "timestamp": "...", "movies": [...]}
 * ]
 * </pre>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → chatSessionArchiveId (컬럼명: chat_session_archive_id)</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>수동 createdAt 필드 및 @CreationTimestamp 제거 — BaseTimeEntity에서 상속</li>
 *   <li>startedAt, endedAt 도메인 고유 타임스탬프는 유지 (@CreationTimestamp 제거)</li>
 * </ul>
 */
@Entity
@Table(
        name = "chat_session_archive",
        indexes = {
                // 사용자별 채팅 세션 목록 조회 시 사용 (user_id + 최신순 정렬)
                @Index(name = "idx_chat_session_user", columnList = "user_id"),
                // 세션 시작 시각 기준 정렬/필터 시 사용
                @Index(name = "idx_chat_session_started", columnList = "started_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatSessionArchive extends BaseAuditEntity {

    /**
     * 아카이브 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: id → chatSessionArchiveId (엔티티 PK 네이밍 통일)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_session_archive_id")
    private Long chatSessionArchiveId;

    /**
     * 대화 참여 사용자.
     * chat_session_archive.user_id → users.user_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 세션 UUID (v4).
     * Redis 세션 키와 동일한 값이며, UNIQUE 제약이 있다.
     */
    @Column(name = "session_id", length = 36, nullable = false, unique = true)
    private String sessionId;

    /**
     * 전체 대화 내역 (JSON 배열, 필수).
     * user/assistant 메시지를 시간순으로 저장한다.
     */
    @Column(name = "messages", columnDefinition = "json", nullable = false)
    private String messages;

    /** 대화 턴 수 (사용자 메시지 수, 기본값: 0) */
    @Column(name = "turn_count")
    @Builder.Default
    private Integer turnCount = 0;

    /**
     * 세션 중 감지된 의도 요약 (JSON 객체).
     * 예: {"recommend": 3, "search": 1, "general": 2}
     */
    @Column(name = "intent_summary", columnDefinition = "json")
    private String intentSummary;

    /**
     * 세션 시작 시각 (도메인 고유 타임스탬프).
     * Redis 세션이 최초 생성된 시각을 기록한다.
     * created_at(아카이빙 시각)과 별개로 유지된다.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * 세션 종료 시각 (도메인 고유 타임스탬프).
     * 세션이 아직 진행 중이면 NULL이다.
     */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 채팅 제목 (목록 표시용, 첫 메시지 요약 또는 사용자 설정) */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * 마지막 메시지 시각 (정렬용).
     * 채팅 세션 목록을 최신 대화 순으로 정렬할 때 사용한다.
     * 각 메시지 추가 시 갱신된다.
     */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 소프트 삭제 여부 (REQ_054: 이전 채팅 내역 삭제) */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /** 소프트 삭제 시각 (30일 후 물리삭제 스케줄링 기준) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 (아카이빙 시각) */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /** 채팅 세션 소프트 삭제 (REQ_054) */
    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /** 소프트 삭제 복원 (관리자 기능) */
    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }
}
