package com.monglepick.monglepickbackend.domain.support.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 고객센터 상담 티켓 엔티티.
 *
 * <p>MySQL {@code support_tickets} 테이블과 매핑된다.
 * 사용자가 FAQ/도움말로 해결하지 못한 문제를 직접 문의하는 1:1 상담 채널이다.</p>
 *
 * <h3>티켓 상태 전이</h3>
 * <pre>
 * OPEN → IN_PROGRESS → RESOLVED → CLOSED
 *      ↘                         ↗
 *        (관리자 직접 종결 가능)
 * </pre>
 *
 * <h3>연관 관계</h3>
 * <ul>
 *   <li>userId: String FK — users 테이블 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑
 *       대신 String 직접 보관 (설계서 §15.4)</li>
 * </ul>
 */
@Entity
@Table(name = "support_tickets", indexes = {
        // 사용자별 티켓 목록 조회 시 사용 (user_id + 페이징)
        @Index(name = "idx_support_tickets_user", columnList = "user_id"),
        // 상태별 관리자 조회 시 사용
        @Index(name = "idx_support_tickets_status", columnList = "status"),
        // 최신순 정렬 시 사용
        @Index(name = "idx_support_tickets_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportTicket extends BaseAuditEntity {

    /**
     * 티켓 고유 식별자 (BIGINT AUTO_INCREMENT PK).
     * DB가 자동 생성하며 변경 불가.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    /**
     * 티켓 작성자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). nullable=false: 반드시 로그인한 사용자만
     * 티켓을 생성할 수 있다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 문의 카테고리.
     * GENERAL, ACCOUNT, CHAT, RECOMMENDATION, COMMUNITY, PAYMENT 중 하나.
     * EnumType.STRING으로 DB에 문자열로 저장된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportCategory category;

    /**
     * 문의 제목 (VARCHAR 100).
     * 사용자가 입력하는 간결한 문의 제목. 최소 2자, 최대 100자.
     */
    @Column(nullable = false, length = 100)
    private String title;

    /**
     * 문의 내용 (TEXT).
     * 상세 문의 내용. 최소 10자, 최대 2000자 (DTO 단에서 검증).
     * nullable = true: 제목만으로 충분한 경우 본문 없이 제출 가능.
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 티켓 처리 상태 (기본값: OPEN).
     * OPEN → IN_PROGRESS → RESOLVED → CLOSED 순서로 전이된다.
     * {@link TicketStatus} 열거형 참조.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    /** 우선순위 (LOW/NORMAL/HIGH/URGENT, 기본값: NORMAL) */
    @Column(name = "priority", length = 20)
    private String priority = "NORMAL";

    /** 담당 관리자 ID (FK → users.user_id) */
    @Column(name = "assigned_to", length = 50)
    private String assignedTo;

    /** 해결 일시 (RESOLVED 상태 전환 시 기록) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 종료 일시 (CLOSED 상태 전환 시 기록) */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * 생성자 (빌더 패턴).
     *
     * <p>status는 항상 {@link TicketStatus#OPEN}으로 초기화된다.
     * 상태 변경은 비즈니스 메서드를 통해서만 수행한다.</p>
     *
     * @param userId   티켓 작성자 ID (String FK)
     * @param category 문의 카테고리
     * @param title    문의 제목
     * @param content  문의 내용
     */
    @Builder
    public SupportTicket(String userId, SupportCategory category, String title, String content) {
        this.userId = userId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.status = TicketStatus.OPEN; // 티켓 생성 시 항상 OPEN 상태
        this.priority = "NORMAL";         // 티켓 생성 시 기본 우선순위
    }

    // ─────────────────────────────────────────────
    // 도메인 메서드 (상태 전이)
    // ─────────────────────────────────────────────

    /**
     * 처리 시작 — OPEN → IN_PROGRESS.
     * 관리자가 티켓을 확인하고 처리를 시작할 때 호출한다.
     */
    public void startProcessing() {
        this.status = TicketStatus.IN_PROGRESS;
    }

    /**
     * 처리 완료 — IN_PROGRESS → RESOLVED.
     * 관리자가 답변을 완료하고 해결 처리할 때 호출한다.
     * resolvedAt을 현재 시각으로 기록한다.
     */
    public void resolve() {
        this.status = TicketStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 티켓 종결 — RESOLVED 또는 OPEN → CLOSED.
     * 사용자가 해결 확인하거나 관리자가 직접 종결 처리할 때 호출한다.
     * closedAt을 현재 시각으로 기록한다.
     */
    public void close() {
        this.status = TicketStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    /**
     * 담당 관리자 배정.
     * 관리자가 티켓을 특정 담당자에게 배정할 때 호출한다.
     *
     * @param adminUserId 담당 관리자 ID (users.user_id)
     */
    public void assignTo(String adminUserId) {
        this.assignedTo = adminUserId;
    }

    /**
     * 우선순위 변경.
     * 관리자가 티켓 긴급도에 따라 우선순위를 조정할 때 호출한다.
     *
     * @param priority 우선순위 문자열 (LOW/NORMAL/HIGH/URGENT)
     */
    public void updatePriority(String priority) {
        this.priority = priority;
    }
}
