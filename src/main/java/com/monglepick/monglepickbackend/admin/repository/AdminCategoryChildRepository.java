package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.community.entity.CategoryChild;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 관리자 전용 게시글 하위 카테고리(CategoryChild) 리포지토리.
 *
 * <p>CategoryChild 엔티티는 이민수 community 도메인이지만, admin 관리는
 * 윤형주 admin 도메인에서 별도 JpaRepository로 처리한다.</p>
 */
public interface AdminCategoryChildRepository extends JpaRepository<CategoryChild, Long> {

    /** 같은 상위 카테고리 내 하위 카테고리명 중복 검증 (복합 UNIQUE 제약) */
    boolean existsByCategoryIdAndCategoryChild(Long categoryId, String categoryChild);

    /** 상위 카테고리 ID로 하위 목록 전체 조회 */
    List<CategoryChild> findByCategoryIdOrderByCategoryChildAsc(Long categoryId);

    /** 상위 카테고리 삭제 시 하위 카테고리 자동 정리용 */
    void deleteByCategoryId(Long categoryId);

    /** 상위 카테고리에 연결된 하위 개수 (삭제 경고용) */
    long countByCategoryId(Long categoryId);
}
