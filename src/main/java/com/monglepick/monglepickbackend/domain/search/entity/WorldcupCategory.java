package com.monglepick.monglepickbackend.domain.search.entity;

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
 * 월드컵 후보 카테고리 마스터 엔티티 — worldcup_category 테이블 매핑.
 *
 * <p>월드컵 후보 영화가 속하는 카테고리의 코드/이름/설명/관리 메모를 별도 마스터 테이블로 관리한다.</p>
 */
@Entity
@Table(
        name = "worldcup_category",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worldcup_category_code",
                columnNames = "category_code"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WorldcupCategory extends BaseAuditEntity {

    /** PK (BIGINT AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    /** 시스템 식별용 카테고리 코드 (예: DEFAULT, ACTION, DIRECTOR_NOLAN) */
    @Column(name = "category_code", nullable = false, length = 100, unique = true)
    private String categoryCode;

    /** 관리자/사용자 화면 표시용 카테고리 이름 */
    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    /** 카테고리 설명 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 관리자 전용 메모 */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    /** 카테고리 표시명/설명/관리 메모를 수정한다. */
    public void update(String categoryName, String description, String adminNote) {
        this.categoryName = categoryName;
        this.description = description;
        this.adminNote = adminNote;
    }
}
