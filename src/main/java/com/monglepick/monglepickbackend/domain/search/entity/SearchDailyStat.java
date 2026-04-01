package com.monglepick.monglepickbackend.domain.search.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
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

import java.time.LocalDate;

/**
 * 일일 검색 통계 엔티티 — search_daily_stats 테이블 매핑.
 *
 * <p>키워드별 일별 검색 횟수를 집계하여 저장한다.
 * {@link TrendingKeyword}가 전체 누적 인기 검색어를 관리한다면,
 * 이 엔티티는 날짜별 검색량 추이를 분석하기 위한 시계열 통계 테이블이다.</p>
 *
 * <h3>활용 사례</h3>
 * <ul>
 *   <li>관리자 통계 탭 — 날짜 범위별 검색 트렌드 차트</li>
 *   <li>추천 엔진 — 최근 N일 급상승 키워드 감지</li>
 *   <li>콘텐츠 기획 — 기간별 인기 검색어 리포트</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code keyword}     — 검색 키워드 (최대 200자)</li>
 *   <li>{@code searchDate}  — 검색 날짜 (DATE 타입)</li>
 *   <li>{@code searchCount} — 해당 날짜의 검색 횟수 (기본값: 0)</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(keyword, search_date) — 동일 키워드의 동일 날짜 레코드 중복 생성 불가.
 *       서비스 레이어에서 UPSERT 패턴(조회 후 없으면 생성, 있으면 카운트 증가)을 사용한다.</li>
 *   <li>idx_search_daily_date — 날짜 범위 조회(WHERE search_date BETWEEN) 최적화</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>검색 요청마다 실시간 UPDATE를 하면 DB 부하가 크므로,
 *       배치 또는 비동기 방식으로 주기적으로 집계하는 방식을 권장한다.</li>
 *   <li>{@link TrendingKeyword}와의 동기화:
 *       스케줄러가 매일 자정에 이 테이블을 집계하여 TrendingKeyword의
 *       searchCount와 lastSearchedAt을 갱신한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "search_daily_stats",
        uniqueConstraints = {
                /* 동일 키워드의 동일 날짜 레코드 중복 방지 — UPSERT 패턴 활용 */
                @UniqueConstraint(
                        name = "uk_search_daily_stats_keyword_date",
                        columnNames = {"keyword", "search_date"}
                )
        },
        indexes = {
                /* 날짜 범위 조회(WHERE search_date BETWEEN ?) 최적화 */
                @Index(name = "idx_search_daily_date", columnList = "search_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class SearchDailyStat extends BaseAuditEntity {

    /**
     * 일일 검색 통계 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 검색 키워드 (VARCHAR(200), NOT NULL).
     * 사용자가 입력한 원본 검색어를 저장한다.
     * UNIQUE 제약은 search_date와 복합으로 적용된다.
     */
    @Column(name = "keyword", length = 200, nullable = false)
    private String keyword;

    /**
     * 검색 날짜 (DATE 타입, NOT NULL).
     * 해당 키워드가 검색된 날짜(LocalDate).
     * UNIQUE 제약은 keyword와 복합으로 적용된다.
     */
    @Column(name = "search_date", nullable = false)
    private LocalDate searchDate;

    /**
     * 해당 날짜의 검색 횟수 (기본값: 0).
     * 검색이 발생할 때마다 증가한다.
     * 배치 집계 방식 사용 시 한 번에 N씩 증가할 수 있다.
     */
    @Column(name = "search_count")
    @Builder.Default
    private Integer searchCount = 0;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 검색 횟수를 1 증가시킨다.
     *
     * <p>단건 검색 이벤트 처리 시 사용한다.
     * 배치 집계의 경우 {@link #incrementBy(int)}를 사용한다.</p>
     */
    public void increment() {
        this.searchCount++;
    }

    /**
     * 검색 횟수를 지정한 수만큼 증가시킨다.
     *
     * <p>배치 집계 방식에서 여러 건을 한 번에 반영할 때 사용한다.</p>
     *
     * @param count 증가시킬 횟수 (양수여야 함)
     * @throws IllegalArgumentException count가 0 이하인 경우
     */
    public void incrementBy(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("증가 횟수는 1 이상이어야 합니다: " + count);
        }
        this.searchCount += count;
    }
}
