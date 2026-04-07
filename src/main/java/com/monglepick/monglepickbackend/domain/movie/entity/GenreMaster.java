package com.monglepick.monglepickbackend.domain.movie.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
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

/**
 * 장르 마스터 엔티티 — genre_master 테이블 매핑.
 *
 * <p>서비스에서 사용하는 영화 장르의 마스터 데이터를 관리한다.
 * 장르 코드(genre_code)는 내부 식별자로, 장르명(genre_name)은 사용자에게 표시되는 한국어 명칭이다.
 * 각 장르에 속한 영화 수(contentsCount)를 집계하여 장르별 통계 및 필터 정렬에 활용한다.</p>
 *
 * <p>Excel DB 설계서 Table 7 기준으로 생성되었다.</p>
 *
 * <h3>genre_code 예시</h3>
 * <ul>
 *   <li>{@code ACTION} → 액션</li>
 *   <li>{@code DRAMA} → 드라마</li>
 *   <li>{@code COMEDY} → 코미디</li>
 *   <li>{@code HORROR} → 공포</li>
 *   <li>{@code ROMANCE} → 로맨스</li>
 *   <li>{@code SF} → SF</li>
 *   <li>{@code ANIMATION} → 애니메이션</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code genreCode} — 장르 코드 (UNIQUE, 내부 식별자, 최대 50자)</li>
 *   <li>{@code genreName} — 장르 한국어 명칭 (최대 100자, 필수)</li>
 *   <li>{@code contentsCount} — 해당 장르에 속한 영화 수 (기본값: 0)</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 7 기준으로 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "genre_master",
        uniqueConstraints = {
                /* 장르 코드 중복 방지 (예: ACTION은 하나만 존재) */
                @UniqueConstraint(name = "uk_genre_master_code", columnNames = "genre_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GenreMaster extends BaseAuditEntity {

    /**
     * 장르 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "genre_id")
    private Long genreId;

    /**
     * 장르 코드 (VARCHAR(50), UNIQUE, NOT NULL).
     * 시스템 내부에서 장르를 식별하는 영문 대문자 코드.
     * 예: "ACTION", "DRAMA", "COMEDY", "HORROR"
     */
    @Column(name = "genre_code", length = 50, nullable = false, unique = true)
    private String genreCode;

    /**
     * 장르 한국어 명칭 (VARCHAR(100), NOT NULL).
     * 사용자에게 표시되는 장르 이름.
     * 예: "액션", "드라마", "코미디", "공포"
     */
    @Column(name = "genre_name", length = 100, nullable = false)
    private String genreName;

    /**
     * 해당 장르에 속한 영화 수 (기본값: 0).
     * 영화 등록/삭제 시 incrementContentsCount() / decrementContentsCount()로 동기화한다.
     * 장르 필터 UI에서 영화 수 표시 및 인기 장르 정렬에 활용된다.
     */
    @Column(name = "contents_count", nullable = false)
    @Builder.Default
    private Integer contentsCount = 0;

    /* created_at, updated_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 해당 장르의 영화 수를 1 증가시킨다.
     * 새 영화가 이 장르에 추가될 때 호출한다.
     */
    public void incrementContentsCount() {
        this.contentsCount = (this.contentsCount == null ? 0 : this.contentsCount) + 1;
    }

    /**
     * 해당 장르의 영화 수를 1 감소시킨다 (최소 0).
     * 영화가 이 장르에서 제거되거나 삭제될 때 호출한다.
     * 0 미만으로 내려가지 않도록 보호한다.
     */
    public void decrementContentsCount() {
        this.contentsCount = Math.max(0, (this.contentsCount == null ? 0 : this.contentsCount) - 1);
    }
}
