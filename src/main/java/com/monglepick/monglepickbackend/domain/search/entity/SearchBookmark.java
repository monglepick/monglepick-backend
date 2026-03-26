package com.monglepick.monglepickbackend.domain.search.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검색 키워드 즐겨찾기 엔티티 — search_bookmarks 테이블 매핑 (REQ_078).
 *
 * <p>사용자가 자주 사용하는 검색 키워드를 즐겨찾기로 저장하여 빠르게 재검색할 수 있도록 한다.
 * 동일 사용자가 동일 키워드를 중복 즐겨찾기할 수 없다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code user} — 즐겨찾기 소유 사용자 (FK → users.user_id)</li>
 *   <li>{@code keyword} — 즐겨찾기된 검색 키워드</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(user_id, keyword) — 동일 사용자가 동일 키워드를 중복 즐겨찾기 불가.</p>
 */
@Entity
@Table(
        name = "search_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_search_bookmark",
                columnNames = {"user_id", "keyword"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SearchBookmark extends BaseAuditEntity {

    /** 검색 즐겨찾기 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_bookmark_id")
    private Long searchBookmarkId;

    /** 즐겨찾기 소유 사용자 (지연 로딩). users.user_id를 참조한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 즐겨찾기된 검색 키워드 (VARCHAR(500), NOT NULL) */
    @Column(name = "keyword", length = 500, nullable = false)
    private String keyword;
}
