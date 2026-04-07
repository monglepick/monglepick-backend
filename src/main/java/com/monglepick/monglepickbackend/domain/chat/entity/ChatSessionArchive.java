package com.monglepick.monglepickbackend.domain.chat.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 세션 엔티티 — chat_session_archive 테이블 매핑.
 *
 * <p>AI Agent와의 채팅 세션을 MySQL 단일 저장소에 영속화한다.
 * Agent가 매 턴마다 Backend API를 통해 세션 상태를 저장/로드하며,
 * Client는 이력 API로 이전 대화 목록 조회 및 이어하기를 수행한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 대화 참여 사용자 ID (FK → users.user_id, String 직접 참조,
 *       JPA/MyBatis 하이브리드 경계 격리)</li>
 *   <li>{@code sessionId} — 세션 UUID (UNIQUE)</li>
 *   <li>{@code messages} — 전체 대화 내역 (JSON 배열, 필수)</li>
 *   <li>{@code sessionState} — Agent 세션 상태 (preferences, emotion 등 JSON)</li>
 *   <li>{@code turnCount} — 대화 턴 수 (사용자 메시지 수)</li>
 *   <li>{@code isActive} — 진행 중 세션 여부</li>
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
 * <h3>sessionState JSON 구조</h3>
 * <pre>
 * {
 *   "preferences": { ... ExtractedPreferences ... },
 *   "emotion": { ... EmotionResult ... },
 *   "user_profile": { ... },
 *   "watch_history": [ ... ]   // Agent state 키 (legacy 명칭). 실제 데이터 출처는 reviews 테이블.
 * }
 * </pre>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → chatSessionArchiveId (컬럼명: chat_session_archive_id)</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>Redis → MySQL 단일 저장소 전환: sessionState, isActive 컬럼 추가</li>
 * </ul>
 */
@Entity
@Table(
        name = "chat_session_archive",
        indexes = {
                // 사용자별 채팅 세션 목록 조회 시 사용 (user_id + 최신순 정렬)
                @Index(name = "idx_chat_session_user", columnList = "user_id"),
                // 세션 시작 시각 기준 정렬/필터 시 사용
                @Index(name = "idx_chat_session_started", columnList = "started_at"),
                // 마지막 메시지 시각 기준 정렬 (이력 목록 최신순)
                @Index(name = "idx_chat_session_last_msg", columnList = "last_message_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatSessionArchive extends BaseAuditEntity {

    /**
     * 아카이브 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_session_archive_id")
    private Long chatSessionArchiveId;

    /**
     * 대화 참여 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (JPA/MyBatis 하이브리드 경계 격리, 설계서 §15.4).</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 세션 UUID (v4). UNIQUE 제약.
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
     * Agent 세션 상태 (JSON).
     * preferences, emotion, user_profile, watch_history(=리뷰 기반 시청 이력)를 묶어 저장한다.
     * 이어하기 시 Agent가 이 상태를 복원하여 대화 맥락을 유지한다.
     * watch_history 키는 Agent state의 legacy 명칭일 뿐, 실제 데이터는 reviews 테이블에서 로드된다.
     */
    @Column(name = "session_state", columnDefinition = "json")
    private String sessionState;

    /**
     * 세션 중 감지된 의도 요약 (JSON 객체).
     * 예: {"recommend": 3, "search": 1, "general": 2}
     */
    @Column(name = "intent_summary", columnDefinition = "json")
    private String intentSummary;

    /**
     * 세션 시작 시각 (도메인 고유 타임스탬프).
     * 세션이 최초 생성된 시각을 기록한다.
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
     */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 세션 활성 여부 (진행 중 = true, 종료 = false) */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 소프트 삭제 여부 (REQ_054: 이전 채팅 내역 삭제) */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /** 소프트 삭제 시각 (30일 후 물리삭제 스케줄링 기준) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ── 비즈니스 메서드 ──

    /** 매 턴마다 Agent가 호출: 메시지 + 턴 수 + 마지막 메시지 시각 갱신 */
    public void updateMessages(String messages, int turnCount, LocalDateTime lastMessageAt) {
        this.messages = messages;
        this.turnCount = turnCount;
        this.lastMessageAt = lastMessageAt;
    }

    /** Agent 세션 상태 갱신 (preferences, emotion 등) */
    public void updateSessionState(String sessionState) {
        this.sessionState = sessionState;
    }

    /** 의도 요약 갱신 */
    public void updateIntentSummary(String intentSummary) {
        this.intentSummary = intentSummary;
    }

    /** 세션 제목 변경 */
    public void updateTitle(String title) {
        this.title = title;
    }

    /** 세션 종료 처리 */
    public void endSession() {
        this.isActive = false;
        this.endedAt = LocalDateTime.now();
    }

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

    // ========== Excel Table 기준 추가 컬럼 (1개) ==========

    /**
     * 이 세션에서 추천된 영화 수 (기본값: 0).
     * Agent가 추천 응답을 생성할 때마다 incrementRecommendedMovieCount()로 증가시킨다.
     * 세션별 추천 활동량 분석 및 통계에 활용된다.
     */
    @Column(name = "recommended_movie_count")
    @Builder.Default
    private Integer recommendedMovieCount = 0;

    /**
     * 추천된 영화 수를 1 증가시킨다.
     * Agent가 영화 추천 응답을 반환할 때마다 호출한다.
     */
    public void incrementRecommendedMovieCount() {
        this.recommendedMovieCount = (this.recommendedMovieCount == null ? 0 : this.recommendedMovieCount) + 1;
    }
}
