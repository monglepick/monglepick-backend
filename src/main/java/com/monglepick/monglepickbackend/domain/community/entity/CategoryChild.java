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
 * 하위 카테고리 엔티티 — category_child 테이블 매핑.
 *
 * <p>상위 카테고리({@link Category}) 하위의 세부 카테고리를 정의한다.
 * 동일 상위 카테고리 내에서 하위 카테고리명은 중복될 수 없다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code categoryChildId} — 하위 카테고리 고유 ID (PK)</li>
 *   <li>{@code categoryId} — 상위 카테고리 ID (FK → category.category_id)</li>
 *   <li>{@code categoryChild} — 하위 카테고리명</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(category_id, category_child) — 동일 상위 카테고리 내 하위명 중복 불가.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: downCategoryId → categoryChildId (컬럼명: category_child_id)</li>
 *   <li>BaseTimeEntity → BaseAuditEntity로 변경 (created_by/updated_by 추가)</li>
 * </ul>
 */
@Entity
@Table(
        name = "category_child",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "category_child"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CategoryChild extends BaseAuditEntity {

    /**
     * 하위 카테고리 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: downCategoryId → categoryChildId (컬럼명: category_child_id)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_child_id")
    private Long categoryChildId;

    /**
     * 상위 카테고리 ID (BIGINT, NOT NULL).
     * category.category_id를 참조한다.
     */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /**
     * 하위 카테고리명 (VARCHAR(100), NOT NULL).
     * 예: "일반", "스포일러", "추천", "비추"
     */
    @Column(name = "category_child", length = 100, nullable = false)
    private String categoryChild;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 하위 카테고리명을 변경한다 (관리자 마스터 관리 전용).
     *
     * <p>(category_id, category_child) UNIQUE 제약이 있으므로
     * 호출자(Service)에서 중복 검증 필수.</p>
     */
    public void updateName(String categoryChild) {
        this.categoryChild = categoryChild;
    }
}
