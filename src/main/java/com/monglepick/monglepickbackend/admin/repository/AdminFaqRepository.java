package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 FAQ 리포지토리.
 *
 * <p>관리자 페이지 "고객센터 → FAQ" 탭에서 FAQ 목록을 페이징 조회하기 위한 쿼리 메서드를 제공한다.
 * 도메인 레이어의 {@code SupportFaqRepository}는 List 반환에 특화되어 있으므로 페이징 검색은 별도로 분리한다.</p>
 */
public interface AdminFaqRepository extends JpaRepository<SupportFaq, Long> {

    /**
     * 전체 FAQ 목록을 표시 순서(오름차순, null last) + 생성일시(최신순)로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return FAQ 페이지
     */
    Page<SupportFaq> findAllByOrderBySortOrderAscCreatedAtDesc(Pageable pageable);

    /**
     * 특정 카테고리의 FAQ 목록을 표시 순서 + 최신순으로 페이징 조회한다.
     *
     * @param category FAQ 카테고리
     * @param pageable 페이지 정보
     * @return 해당 카테고리의 FAQ 페이지
     */
    Page<SupportFaq> findByCategoryOrderBySortOrderAscCreatedAtDesc(
            SupportCategory category, Pageable pageable
    );
}
