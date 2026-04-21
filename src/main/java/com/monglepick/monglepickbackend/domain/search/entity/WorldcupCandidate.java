package com.monglepick.monglepickbackend.domain.search.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * 월드컵 후보 영화 풀 엔티티 — worldcup_candidate 테이블 매핑.
 *
 * <p>이상형 월드컵에서 사용되는 후보 영화를 관리자가 직접 큐레이션하기 위한 마스터 데이터.
 * 기존 {@code WorldcupService.startWorldcup()}는 {@code Movie.findRandomMovieIdsByGenre()}로
 * 무작위 영화를 선택했지만, 본 엔티티 도입 이후에는 활성화된 후보 영화 풀에서 우선 선택한다.</p>
 *
 * <h3>운영 모델</h3>
 * <ul>
 *   <li>관리자가 후보 영화를 추가하고 카테고리 마스터({@link WorldcupCategory})를 연결한다.</li>
 *   <li>{@code popularity} 임계값을 설정하여 인기 없는 영화를 풀에서 자동 제외한다.</li>
 *   <li>{@code isActive=false}이면 해당 영화는 월드컵 후보로 노출되지 않는다.</li>
 * </ul>
 *
 * <p>{@code (movieId, category_id)} 복합 UNIQUE — 같은 영화를 여러 카테고리에 등록 가능하지만
 * 같은 카테고리 내에서는 중복 불가.</p>
 */
@Entity
@Table(
        name = "worldcup_candidate",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worldcup_candidate_movie_category",
                columnNames = {"movie_id", "category_id"}
        ),
        indexes = {
                @Index(name = "idx_wcc_active", columnList = "is_active"),
                @Index(name = "idx_wcc_category", columnList = "category_id"),
                @Index(name = "idx_wcc_popularity", columnList = "popularity")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WorldcupCandidate extends BaseAuditEntity {

    /** PK (BIGINT AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 영화 ID (movies.movie_id 참조, VARCHAR(50)) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /** 연결된 월드컵 카테고리 마스터 (FK → worldcup_category.category_id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private WorldcupCategory category;

    /**
     * 영화 인기도 점수 (참조용 비정규화 값, 기본 0).
     *
     * <p>관리자 화면 성능과 운영 편의를 위해 snapshot으로 보관한다.
     * 생성/수정 시 movies.popularity_score 기준으로 재동기화되며,
     * 일괄 비활성화는 항상 movies 테이블의 최신 popularity_score를 직접 사용한다.</p>
     */
    @Column(name = "popularity", nullable = false)
    @Builder.Default
    private Double popularity = 0.0;

    /**
     * 후보 활성화 여부 (기본 true).
     *
     * <p>false이면 월드컵 시작 시 후보 선택에서 제외된다.</p>
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 후보를 추가한 관리자 ID (users.user_id 참조, nullable) */
    @Column(name = "added_by", length = 50)
    private String addedBy;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 후보 메타 정보를 수정한다 (movieId/category 제외).
     */
    public void updateInfo(Double popularity, Boolean isActive) {
        this.popularity = popularity != null ? popularity : 0.0;
        this.isActive = isActive != null ? isActive : true;
    }

    /** 활성화 토글 */
    public void updateActiveStatus(boolean active) {
        this.isActive = active;
    }
}
