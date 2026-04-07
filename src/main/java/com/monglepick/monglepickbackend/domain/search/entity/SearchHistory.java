package com.monglepick.monglepickbackend.domain.search.entity;

/* BaseAuditEntity: created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 *   <li>{@code userId} — 검색한 사용자 ID (String FK → users.user_id, JPA/MyBatis 하이브리드 §15.4)</li>
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
     * 검색한 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (JPA/MyBatis 하이브리드 경계 격리, 설계서 §15.4).</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

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

    // ========== Excel Table 기준 추가 컬럼 (3개) ==========

    /**
     * 검색 결과 수.
     * 해당 키워드로 검색했을 때 반환된 영화 결과 수를 기록한다.
     * 결과 0건 키워드 분석 및 데이터 보강 우선순위 결정에 활용된다.
     */
    @Column(name = "result_count")
    private Integer resultCount;

    /**
     * 검색 후 클릭한 영화 ID (VARCHAR(50), nullable).
     * movies.movie_id를 논리적으로 참조한다.
     * 어떤 검색어로 어떤 영화를 클릭했는지 추적하여 CTR(클릭률) 분석에 활용된다.
     */
    @Column(name = "clicked_movie_id", length = 50)
    private String clickedMovieId;

    /**
     * 사용한 필터 조건 (JSON, nullable).
     * 검색 시 적용된 필터(장르, 연도, 평점 등)를 JSON 형태로 저장한다.
     * 예: {"genre": "액션", "year_from": 2020, "rating_min": 7.0}
     */
    @Column(name = "filters", columnDefinition = "json")
    private String filters;

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

    /**
     * 검색 결과를 클릭한 영화 ID를 기록한다.
     *
     * <p>사용자가 검색 결과 목록에서 특정 영화를 클릭할 때 호출된다.
     * 동일 세션에서 여러 번 클릭해도 마지막 클릭 영화 ID만 기록된다.</p>
     *
     * @param clickedMovieId 클릭한 영화의 movie_id (VARCHAR(50))
     */
    public void updateClickedMovie(String clickedMovieId) {
        this.clickedMovieId = clickedMovieId;
    }
}
