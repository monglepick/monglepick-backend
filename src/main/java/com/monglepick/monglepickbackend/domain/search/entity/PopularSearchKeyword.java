package com.monglepick.monglepickbackend.domain.search.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인기 검색어 마스터 엔티티 — popular_search_keyword 테이블 매핑.
 *
 * <p>인기 검색어는 기본적으로 {@code SearchHistory}로부터 자동 집계되지만,
 * 관리자가 수동으로 강제 노출/제외할 수 있도록 별도 마스터 데이터를 둔다.
 * 부적절한 키워드를 차단하거나, 마케팅 목적으로 특정 키워드를 강제 상위 노출한다.</p>
 *
 * <h3>운영 모델</h3>
 * <ul>
 *   <li>{@code isExcluded=true} — 자동 집계 결과에서 제외 (블랙리스트)</li>
 *   <li>{@code manualPriority &gt; 0} — 수동 우선순위 부여 (높을수록 상단 노출)</li>
 *   <li>{@code displayRank} — 관리자가 지정한 고정 노출 순위 (nullable)</li>
 *   <li>{@code adminNote} — 관리자 메모 (제외 사유, 마케팅 목적 등)</li>
 * </ul>
 *
 * <p>JPA {@code ddl-auto=update}로 자동 테이블 생성됨.</p>
 */
@Entity
@Table(
        name = "popular_search_keyword",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_popular_search_keyword", columnNames = "keyword"
        ),
        indexes = {
                @Index(name = "idx_psk_excluded", columnList = "is_excluded"),
                @Index(name = "idx_psk_priority", columnList = "manual_priority")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PopularSearchKeyword extends BaseAuditEntity {

    /** PK (BIGINT AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 검색어 (UNIQUE) */
    @Column(name = "keyword", nullable = false, length = 200)
    private String keyword;

    /**
     * 관리자가 지정한 고정 노출 순위 (nullable).
     *
     * <p>null이면 자동 집계 순위를 따른다.
     * 값이 있으면 해당 순위에 강제로 노출된다.</p>
     */
    @Column(name = "display_rank")
    private Integer displayRank;

    /**
     * 수동 우선순위 (기본 0, 높을수록 상단 노출).
     *
     * <p>자동 집계 점수에 가중치로 적용되거나,
     * 동일 자동 점수에서 정렬 순서를 결정하는 데 사용된다.</p>
     */
    @Column(name = "manual_priority", nullable = false)
    @Builder.Default
    private Integer manualPriority = 0;

    /**
     * 자동 집계 제외 여부 (블랙리스트, 기본 false).
     *
     * <p>true이면 인기 검색어 노출에서 제외된다 (부적절 키워드 차단).</p>
     */
    @Column(name = "is_excluded", nullable = false)
    @Builder.Default
    private Boolean isExcluded = false;

    /**
     * 관리자 메모 (TEXT).
     *
     * <p>제외 사유, 마케팅 목적, 관리 이력 등을 자유 형식으로 기록한다.</p>
     */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 키워드 메타 정보를 수정한다 (keyword 제외).
     *
     * <p>키워드 본문은 식별자 역할이므로 변경 불가. 노출 순위/우선순위/제외/메모만 수정.</p>
     */
    public void updateInfo(Integer displayRank, Integer manualPriority,
                           Boolean isExcluded, String adminNote) {
        this.displayRank = displayRank;
        this.manualPriority = manualPriority != null ? manualPriority : 0;
        this.isExcluded = isExcluded != null ? isExcluded : false;
        this.adminNote = adminNote;
    }

    /** 제외 토글 (블랙리스트 전환) */
    public void updateExcluded(boolean excluded) {
        this.isExcluded = excluded;
    }
}
