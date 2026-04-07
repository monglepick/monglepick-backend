package com.monglepick.monglepickbackend.domain.community.entity;

/* BaseAuditEntity로 변경 — created_at/updated_at에 더해 created_by/updated_by 자동 관리 */
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
 * 커뮤니티 카테고리 엔티티 — category 테이블 매핑.
 *
 * <p>커뮤니티 게시판의 상위 카테고리를 정의한다.
 * 하위 카테고리는 {@link CategoryChild}에서 관리된다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code categoryId} — 카테고리 고유 ID (PK, 변경 없음)</li>
 *   <li>{@code upCategory} — 상위 카테고리명 (UNIQUE)</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(up_category) — 상위 카테고리명은 중복될 수 없다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>BaseTimeEntity → BaseAuditEntity로 변경 (created_by/updated_by 추가)</li>
 *   <li>PK 필드(categoryId)는 이미 올바른 네이밍이므로 변경 없음</li>
 * </ul>
 */
@Entity
@Table(
        name = "category",
        uniqueConstraints = @UniqueConstraint(columnNames = "up_category")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Category extends BaseAuditEntity {

    /** 카테고리 고유 ID (BIGINT AUTO_INCREMENT PK, 변경 없음) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * 상위 카테고리명 (VARCHAR(100), NOT NULL, UNIQUE).
     * 예: "자유게시판", "영화토론", "리뷰", "질문"
     */
    @Column(name = "up_category", length = 100, nullable = false)
    private String upCategory;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 카테고리명을 변경한다 (관리자 마스터 관리 전용).
     *
     * <p>UNIQUE 제약이 있으므로 호출자(Service)에서 중복 검증 필수.</p>
     */
    public void updateName(String upCategory) {
        this.upCategory = upCategory;
    }
}
