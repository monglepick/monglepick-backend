package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.community.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 게시글 상위 카테고리(Category) 리포지토리.
 *
 * <p>Category 엔티티는 이민수 community 도메인이지만, admin 관리는
 * 윤형주 admin 도메인에서 별도 JpaRepository로 처리한다 (AdminReportRepository와 동일 패턴).</p>
 */
public interface AdminCategoryRepository extends JpaRepository<Category, Long> {

    /** 상위 카테고리명 중복 검증 (UNIQUE 제약 사전 검증용) */
    boolean existsByUpCategory(String upCategory);

    /** 페이징 조회 (관리자 화면) — createdAt DESC */
    Page<Category> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
