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
 * 월드컵 후보 영화 풀 엔티티 — worldcup_candidate 테이블 매핑.
 *
 * <p>이상형 월드컵에서 사용되는 후보 영화를 관리자가 직접 큐레이션하기 위한 마스터 데이터.
 * 기존 {@code WorldcupService.startWorldcup()}는 {@code Movie.findRandomMovieIdsByGenre()}로
 * 무작위 영화를 선택했지만, 본 엔티티 도입 이후에는 활성화된 후보 영화 풀에서 우선 선택한다.</p>
 *
 * <h3>운영 모델</h3>
 * <ul>
 *   <li>관리자가 후보 영화를 추가하고 카테고리(예: 액션/감독별/시대별)를 부여한다.</li>
 *   <li>{@code popularity} 임계값을 설정하여 인기 없는 영화를 풀에서 자동 제외한다.</li>
 *   <li>{@code isActive=false}이면 해당 영화는 월드컵 후보로 노출되지 않는다.</li>
 *   <li>{@code adminNote}로 큐레이션 사유/관리 이력을 자유 형식으로 기록한다.</li>
 * </ul>
 *
 * <p>{@code (movieId, category)} 복합 UNIQUE — 같은 영화를 여러 카테고리에 등록 가능하지만
 * 같은 카테고리 내에서는 중복 불가.</p>
 */
@Entity
@Table(
        name = "worldcup_candidate",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worldcup_candidate_movie_category",
                columnNames = {"movie_id", "category"}
        ),
        indexes = {
                @Index(name = "idx_wcc_active", columnList = "is_active"),
                @Index(name = "idx_wcc_category", columnList = "category"),
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

    /**
     * 후보 풀 카테고리 (예: "DEFAULT", "ACTION", "DIRECTOR_NOLAN", "ROMANCE_2020S").
     *
     * <p>월드컵 시작 시 카테고리별로 후보를 묶어 다양한 테마의 토너먼트를 운영할 수 있다.
     * 별도 카테고리 없이 일반 풀로 사용할 경우 "DEFAULT"를 지정한다.</p>
     */
    @Column(name = "category", nullable = false, length = 100)
    @Builder.Default
    private String category = "DEFAULT";

    /**
     * 영화 인기도 점수 (참조용 비정규화 값, 기본 0).
     *
     * <p>관리자가 임계값 기반 일괄 제외 시 활용한다.
     * Movie.popularityScore와 동기화되지 않으므로 운영 시 별도 갱신이 필요하다.</p>
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

    /** 관리자 메모 (큐레이션 사유, 마케팅 목적 등) */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 후보 메타 정보를 수정한다 (movieId/category 제외).
     */
    public void updateInfo(Double popularity, Boolean isActive, String adminNote) {
        this.popularity = popularity != null ? popularity : 0.0;
        this.isActive = isActive != null ? isActive : true;
        this.adminNote = adminNote;
    }

    /** 활성화 토글 */
    public void updateActiveStatus(boolean active) {
        this.isActive = active;
    }
}
