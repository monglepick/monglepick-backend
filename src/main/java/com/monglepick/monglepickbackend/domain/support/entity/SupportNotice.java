package com.monglepick.monglepickbackend.domain.support.entity;

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
 * 고객센터 공지사항 엔티티 — support_notices 테이블 매핑.
 *
 * <p>관리자 페이지에서 등록·수정·삭제하고, 사용자 앱 "공지사항" 화면에서 목록으로 노출된다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.3 고객센터(23 API) 중 공지사항 5개 API 전용 엔티티.</p>
 *
 * <h3>유형 (notice_type)</h3>
 * <ul>
 *   <li>{@code NOTICE} — 일반 공지 (기본값)</li>
 *   <li>{@code UPDATE} — 업데이트/릴리스 노트</li>
 *   <li>{@code MAINTENANCE} — 서비스 점검 안내</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code title} — 공지 제목 (필수, 최대 200자)</li>
 *   <li>{@code content} — 공지 본문 (마크다운/HTML 허용, TEXT)</li>
 *   <li>{@code noticeType} — 유형 코드 (NOTICE/UPDATE/MAINTENANCE)</li>
 *   <li>{@code isPinned} — 상단 고정 여부 (기본 false)</li>
 *   <li>{@code sortOrder} — 고정 공지 순서 (낮은 값이 상위)</li>
 *   <li>{@code publishedAt} — 공개 시각 (nullable, null 이면 초안)</li>
 * </ul>
 */
@Entity
@Table(
        name = "support_notices",
        indexes = {
                /* 최신순 노출을 위한 created_at 인덱스 */
                @Index(name = "idx_support_notices_created_at", columnList = "created_at"),
                /* 상단 고정 공지 우선 정렬을 위한 pinned + sort_order 조합 인덱스 */
                @Index(name = "idx_support_notices_pinned_order", columnList = "is_pinned, sort_order"),
                /* 유형별 필터링 */
                @Index(name = "idx_support_notices_type", columnList = "notice_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SupportNotice extends BaseAuditEntity {

    /** 공지사항 고유 ID (BIGINT AUTO_INCREMENT PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    /** 공지 제목 (VARCHAR(200), 필수). */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 공지 본문 (TEXT, 필수). 마크다운/HTML 허용. */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 공지 유형 (VARCHAR(30), 기본값: NOTICE).
     * NOTICE / UPDATE / MAINTENANCE 중 하나.
     */
    @Column(name = "notice_type", length = 30, nullable = false)
    @Builder.Default
    private String noticeType = "NOTICE";

    /** 상단 고정 여부 (기본값: false). */
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    /**
     * 정렬 순서 (nullable).
     * 고정 공지끼리의 정렬에 사용한다. 낮은 값일수록 상위에 노출된다.
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 공개 시각 (nullable).
     * null 이면 초안 상태. 값이 지정되면 해당 시각 이후 사용자에게 노출된다.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /** 제목/본문/유형 수정. */
    public void update(String title, String content, String noticeType) {
        this.title = title;
        this.content = content;
        if (noticeType != null && !noticeType.isBlank()) {
            this.noticeType = noticeType;
        }
    }

    /** 상단 고정 설정/해제. */
    public void setPinned(boolean pinned) {
        this.isPinned = pinned;
    }

    /** 정렬 순서 변경. */
    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 공개 시각 설정. */
    public void publish(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
