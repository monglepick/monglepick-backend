package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportHelpArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 도움말 문서 리포지토리.
 *
 * <p>관리자 페이지 "고객센터 → 도움말" 탭에서 도움말 문서 목록을 페이징 조회하기 위한
 * 쿼리 메서드를 제공한다.</p>
 */
public interface AdminHelpArticleRepository extends JpaRepository<SupportHelpArticle, Long> {

    /**
     * 전체 도움말 문서를 최신순으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 도움말 문서 페이지
     */
    Page<SupportHelpArticle> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 카테고리의 도움말 문서를 최신순으로 페이징 조회한다.
     *
     * @param category 카테고리
     * @param pageable 페이지 정보
     * @return 해당 카테고리의 도움말 문서 페이지
     */
    Page<SupportHelpArticle> findByCategoryOrderByCreatedAtDesc(
            SupportCategory category, Pageable pageable
    );
}
