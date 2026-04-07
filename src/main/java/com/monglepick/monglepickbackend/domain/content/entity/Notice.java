package com.monglepick.monglepickbackend.domain.content.entity;

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
 * 공지사항 엔티티 — notices 테이블 매핑.
 *
 * <p>서비스 운영 공지사항(서비스 안내, 이벤트, 업데이트, 점검 등)을 저장한다.
 * 관리자 페이지에서 작성/수정/삭제하며, 클라이언트 공지사항 목록/상세 페이지에서 조회된다.
 * 상단 고정(isPinned)과 예약 공개(publishAt)를 지원한다.</p>
 *
 * <p>Excel DB 설계서 Table 58 기준으로 생성되었다.</p>
 *
 * <h3>카테고리 (category)</h3>
 * <ul>
 *   <li>{@code SERVICE} — 서비스 안내 (이용약관 변경, 기능 추가 등)</li>
 *   <li>{@code EVENT} — 이벤트 공지 (포인트 적립 이벤트, 설문 등)</li>
 *   <li>{@code UPDATE} — 앱/서비스 업데이트 안내</li>
 *   <li>{@code MAINTENANCE} — 점검 안내 (서버 점검, 기능 점검)</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code title} — 공지 제목 (최대 500자, 필수)</li>
 *   <li>{@code content} — 공지 본문 (TEXT, 필수)</li>
 *   <li>{@code category} — 공지 분류 코드 (최대 50자)</li>
 *   <li>{@code isPinned} — 상단 고정 여부 (기본값: false)</li>
 *   <li>{@code isPublished} — 공개 여부 (기본값: false, 저장 직후 비공개)</li>
 *   <li>{@code publishAt} — 예약 공개일 (nullable, null이면 즉시 공개)</li>
 *   <li>{@code viewCount} — 조회 수 (기본값: 0)</li>
 *   <li>{@code adminId} — 작성 관리자 ID (VARCHAR(50), nullable)</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_notices_published} — 공개 여부 + 상단고정 + 생성시각 복합 조회</li>
 *   <li>{@code idx_notices_category} — 카테고리별 필터 조회</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 58 기준으로 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "notices",
        indexes = {
                /* 공개된 공지사항 목록 조회: 상단 고정 우선, 최신순 정렬 */
                @Index(name = "idx_notices_published", columnList = "is_published, is_pinned, created_at"),
                /* 카테고리별 필터 조회 */
                @Index(name = "idx_notices_category", columnList = "category")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notice extends BaseAuditEntity {

    /**
     * 공지사항 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    /**
     * 공지 제목 (VARCHAR(500), NOT NULL).
     * 목록 및 상세 화면에서 표시되는 공지사항 제목.
     */
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    /**
     * 공지 본문 (TEXT, NOT NULL).
     * HTML 또는 마크다운 형식의 공지사항 본문 내용.
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 공지 분류 코드 (VARCHAR(50), NOT NULL).
     * 허용 값: SERVICE, EVENT, UPDATE, MAINTENANCE
     */
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    /**
     * 상단 고정 여부 (기본값: false).
     * true이면 공지사항 목록 최상단에 고정되어 표시된다.
     * 중요 공지(점검, 긴급 안내)에 사용한다.
     */
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    /**
     * 공개 여부 (기본값: false).
     * false이면 클라이언트에서 조회 불가(관리자만 확인 가능).
     * publish() 호출 또는 publishAt 도달 시 true로 변경된다.
     */
    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    /**
     * 예약 공개일 (DATETIME, nullable).
     * null이면 isPublished=true 설정 즉시 공개.
     * null이 아니면 해당 시각 이후부터 공개 처리 (스케줄러 또는 조회 시 필터링).
     */
    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    /**
     * 조회 수 (기본값: 0).
     * 공지사항 상세 페이지 접근마다 incrementViewCount()로 증가시킨다.
     */
    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    /**
     * 작성 관리자 ID (VARCHAR(50), nullable).
     * users.user_id를 논리적으로 참조한다.
     * 시스템 자동 생성 공지의 경우 NULL.
     */
    @Column(name = "admin_id", length = 50)
    private String adminId;

    /* created_at, updated_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 조회 수를 1 증가시킨다.
     * 공지사항 상세 API 호출마다 실행된다.
     */
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
    }

    /**
     * 공지사항을 상단에 고정한다.
     * 관리자가 중요 공지를 목록 최상단에 노출할 때 호출한다.
     */
    public void pin() {
        this.isPinned = true;
    }

    /**
     * 공지사항 상단 고정을 해제한다.
     * 일반 공지로 전환할 때 호출한다.
     */
    public void unpin() {
        this.isPinned = false;
    }

    /**
     * 공지사항을 공개 상태로 전환한다.
     * 관리자가 작성 완료 후 즉시 공개하거나 예약 공개 도달 시 호출한다.
     */
    public void publish() {
        this.isPublished = true;
    }

    /**
     * 공지사항을 비공개 상태로 전환한다.
     * 임시 숨김 처리나 수정 중 비공개 전환에 사용한다.
     */
    public void unpublish() {
        this.isPublished = false;
    }
}
