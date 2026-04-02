package com.monglepick.monglepickbackend.domain.search.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
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

import java.time.LocalDateTime;

/**
 * 검색 이력 엔티티 — search_history 테이블 매핑.
 *
 * <p>사용자의 검색 키워드 이력을 저장한다.
 * 최근 검색어 표시 및 개인화 검색 추천에 활용된다.
 * 한 사용자가 같은 키워드를 다시 검색하면 기존 레코드가 업데이트된다
 * (user_id, keyword UNIQUE 제약).</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-24: BaseAuditEntity 상속 추가 (created_at/updated_at/created_by/updated_by 자동 관리)</li>
 *   <li>2026-03-24: PK 필드명 id → searchHistoryId 로 변경, @Column(name = "search_history_id") 추가</li>
 *   <li>2026-03-24: searchedAt의 @CreationTimestamp 제거 — 도메인 고유 타임스탬프로 유지</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code user} — 검색한 사용자 (FK → users.user_id)</li>
 *   <li>{@code keyword} — 검색 키워드 (최대 200자, 필수)</li>
 *   <li>{@code searchedAt} — 검색 시각 (도메인 고유 타임스탬프, BaseAuditEntity의 created_at과 별도)</li>
 * </ul>
 */
@Entity
@Table(name = "search_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_search_history_user_keyword", columnNames = {"user_id", "keyword"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속 추가: created_at, updated_at, created_by, updated_by 컬럼 자동 관리 */
public class SearchHistory extends BaseAuditEntity {

    /**
     * 검색 이력 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 기존 필드명 'id'에서 'searchHistoryId'로 변경하여 엔티티 식별 명확화.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_history_id")
    private Long searchHistoryId;

    /**
     * 검색한 사용자.
     * search_history.user_id → users.user_id FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 검색 키워드 (최대 200자, 필수) */
    @Column(name = "keyword", length = 200, nullable = false)
    private String keyword;

    /**
     * 검색 시각 (도메인 고유 타임스탬프).
     * 동일 키워드 재검색 시 이 값이 갱신된다.
     * BaseAuditEntity의 created_at과는 별도로, 실제 검색 시점을 기록하는 도메인 필드.
     * 서비스 레이어에서 직접 설정/갱신한다.
     */
    @Column(name = "searched_at")
    private LocalDateTime searchedAt;

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 검색 시각을 현재 시각으로 갱신한다 (재검색 UPSERT 처리용).
     *
     * <p>동일 사용자가 같은 키워드를 다시 검색하면 새 레코드를 생성하는 대신
     * 이 메서드로 searchedAt을 현재 시각으로 업데이트한다.
     * (user_id, keyword) UNIQUE 제약을 애플리케이션 레벨에서 처리한다.</p>
     */
    public void updateSearchedAt() {
        this.searchedAt = LocalDateTime.now();
    }
}
