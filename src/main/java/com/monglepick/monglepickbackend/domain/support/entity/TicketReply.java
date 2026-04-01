package com.monglepick.monglepickbackend.domain.support.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
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

/**
 * 고객센터 티켓 답변 엔티티 — ticket_replies 테이블 매핑.
 *
 * <p>관리자 또는 사용자가 1:1 문의 티켓({@code support_tickets})에 작성한 답변을 저장한다.
 * 하나의 티켓에 여러 답변이 달릴 수 있으며, 스레드 형태의 대화가 가능하다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code ticketId}   — 답변이 속한 티켓 ID (FK → support_tickets.ticket_id)</li>
 *   <li>{@code authorId}   — 답변 작성자 ID (관리자 또는 사용자 ID)</li>
 *   <li>{@code authorType} — 작성자 유형 (ADMIN: 관리자, USER: 사용자)</li>
 *   <li>{@code content}    — 답변 본문 (TEXT)</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>ticketId + created_at 인덱스 — 티켓별 답변 목록 최신순 조회 시 활용</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>authorId는 VARCHAR(50)으로 관리자·사용자 ID 형식이 동일하므로 단일 컬럼으로 관리한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "ticket_replies",
        indexes = {
                /* 티켓별 답변 목록 조회 (ticket_id 기준 페이징) */
                @Index(name = "idx_ticket_replies_ticket", columnList = "ticket_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class TicketReply extends BaseAuditEntity {

    /**
     * 티켓 답변 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reply_id")
    private Long replyId;

    /**
     * 답변이 속한 티켓 ID (BIGINT, NOT NULL).
     * support_tickets.ticket_id를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /**
     * 답변 작성자 ID (VARCHAR(50), NOT NULL).
     * 관리자이면 admin.admin_id, 사용자이면 users.user_id를 값으로 갖는다.
     * authorType과 함께 해석한다.
     */
    @Column(name = "author_id", length = 50, nullable = false)
    private String authorId;

    /**
     * 답변 작성자 유형 (VARCHAR(20), NOT NULL).
     * 기본값: "ADMIN".
     * <ul>
     *   <li>"ADMIN" — 관리자 답변</li>
     *   <li>"USER"  — 사용자(질문자) 추가 답장</li>
     * </ul>
     */
    @Column(name = "author_type", length = 20, nullable = false)
    @Builder.Default
    private String authorType = "ADMIN";

    /**
     * 답변 본문 (TEXT, NOT NULL).
     * 관리자 또는 사용자가 작성하는 답변 내용이다.
     * 최소 1자 이상이어야 한다 (DTO 레이어에서 @NotBlank 검증).
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 답변 내용을 수정한다.
     *
     * <p>답변 수정은 작성 후 일정 시간 내에만 허용해야 하며,
     * 해당 시간 제한 로직은 서비스 레이어에서 검증한다.</p>
     *
     * @param newContent 수정할 답변 내용
     */
    public void updateContent(String newContent) {
        this.content = newContent;
    }

    /**
     * 해당 답변이 관리자 작성인지 여부를 반환한다.
     *
     * @return true이면 관리자 답변, false이면 사용자 답변
     */
    public boolean isAdminReply() {
        return "ADMIN".equals(this.authorType);
    }
}
