package com.monglepick.monglepickbackend.domain.support.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 고객센터 챗봇 사용 통계·감사 로그 엔티티.
 *
 * <p>MySQL {@code support_chat_log} 테이블과 매핑된다.
 * Agent (FastAPI / Python) 가 매 턴 응답 직후 fire-and-forget 으로 직접 INSERT 한다
 * (Backend API 를 거치지 않음 — agent 측 SQL 직삽 — 응답 지연 0).</p>
 *
 * <h3>설계 의도 (2026-04-28)</h3>
 * <ul>
 *   <li>사용자가 어떤 질문을 했고 봇이 어떤 의도로 분류했는지 추적</li>
 *   <li>1:1 유도 비율 / 자주 묻는 질문 / 의도별 분포 통계</li>
 *   <li>관리자 페이지에서 시계열·필터·검색 가능</li>
 * </ul>
 *
 * <h3>DDL 자동 생성</h3>
 * <p>JPA {@code ddl-auto=update} 가 부팅 시 테이블을 자동 생성/갱신한다.
 * Flyway 미도입 정책에 따라 수동 SQL 마이그레이션은 작성하지 않는다.</p>
 *
 * <h3>인덱스</h3>
 * <ul>
 *   <li>{@code created_at} — 시계열 통계 (최근 N일)</li>
 *   <li>{@code intent_kind, created_at} — 의도별 추이</li>
 *   <li>{@code needs_human, created_at} — 1:1 유도 비율 추이</li>
 *   <li>{@code session_id} — 세션 단위 트레이스</li>
 *   <li>{@code user_id} — 사용자별 사용 패턴</li>
 * </ul>
 *
 * <h3>JPA 사용 패턴</h3>
 * <p>이 엔티티는 Backend 에서는 <strong>READ ONLY</strong> 로만 사용된다 (관리자 페이지 조회).
 * INSERT 는 Agent 가 aiomysql 직접 SQL 로 수행. 따라서 setter / 도메인 메서드 미제공.</p>
 */
@Entity
@Table(
        name = "support_chat_log",
        indexes = {
                @Index(name = "idx_support_chat_log_created", columnList = "created_at"),
                @Index(name = "idx_support_chat_log_intent_created", columnList = "intent_kind, created_at"),
                @Index(name = "idx_support_chat_log_needs_human_created", columnList = "needs_human, created_at"),
                @Index(name = "idx_support_chat_log_session", columnList = "session_id"),
                @Index(name = "idx_support_chat_log_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportChatLog {

    /**
     * 로그 고유 식별자 (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * LangGraph thread_id 와 동일한 세션 ID (UUID 기반).
     * 같은 session_id 의 로그를 모아보면 한 사용자의 대화 흐름 트레이스가 된다.
     */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /**
     * 로그인 사용자 ID (VARCHAR 50). 게스트면 NULL.
     * User 엔티티 JOIN 미사용 — 통계 목적이므로 문자열 보관.
     */
    @Column(name = "user_id", length = 50)
    private String userId;

    /**
     * 게스트 여부.
     * Agent {@code context_loader} 노드가 user_id 빈값일 때 true 로 설정.
     */
    @Column(name = "is_guest", nullable = false)
    private boolean guest;

    /**
     * 사용자 원본 발화. TEXT 컬럼으로 길이 제한 없음.
     */
    @Lob
    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    /**
     * 봇 최종 응답 본문. TEXT 컬럼으로 길이 제한 없음.
     */
    @Lob
    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    /**
     * 분류된 의도. v4 6종 + unknown.
     * faq / personal_data / policy / redirect / smalltalk / complaint / unknown
     */
    @Column(name = "intent_kind", nullable = false, length = 32)
    private String intentKind;

    /**
     * 분류 신뢰도 0.00~1.00.
     * 낮은 confidence 패턴 분석으로 분류기 개선에 활용.
     */
    @Column(name = "intent_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal intentConfidence;

    /**
     * 분류 근거 한 줄 (255자로 잘림).
     * LangSmith 트레이스 대신 빠른 디버깅용.
     */
    @Column(name = "intent_reason", length = 255)
    private String intentReason;

    /**
     * 1:1 유도 여부.
     * Client 의 '상담원 연결' 배너 노출 여부와 일치.
     */
    @Column(name = "needs_human", nullable = false)
    private boolean needsHuman;

    /**
     * ReAct 루프 hop 수. policy/personal_data 경로에서 0 이상.
     * faq/redirect/smalltalk/complaint 는 보통 0~1.
     */
    @Column(name = "hop_count", nullable = false)
    private int hopCount;

    /**
     * tool_call_history JSON 직렬화. 어떤 tool 이 어떤 순서로 실행됐는지.
     * 예: {@code [{"hop":1,"tool_name":"lookup_my_ai_quota","ok":true,"error":null}]}
     */
    @Lob
    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    /**
     * 로그 생성 시각.
     * Agent 측에서 명시 UTC datetime 으로 INSERT — Hibernate {@code @CreationTimestamp} 도
     * INSERT 누락 방어용으로 함께 부착 (NULL 방지).
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 빌더 — Backend 측에서는 read-only 사용을 권장하지만 테스트 / 시드 용도로 유지.
     */
    @Builder
    public SupportChatLog(
            String sessionId,
            String userId,
            boolean guest,
            String userMessage,
            String responseText,
            String intentKind,
            BigDecimal intentConfidence,
            String intentReason,
            boolean needsHuman,
            int hopCount,
            String toolCallsJson
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.guest = guest;
        this.userMessage = userMessage;
        this.responseText = responseText;
        this.intentKind = intentKind;
        this.intentConfidence = intentConfidence;
        this.intentReason = intentReason;
        this.needsHuman = needsHuman;
        this.hopCount = hopCount;
        this.toolCallsJson = toolCallsJson;
    }
}
