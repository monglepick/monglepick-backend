package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.content.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 배너 관리자 Repository.
 *
 * <p>관리자 페이지 "설정 → 배너 관리" 탭의 CRUD 작업에 사용된다.
 * 도메인 레이어의 content 패키지에 위치한 {@link Banner} 엔티티를 대상으로 한다.</p>
 */
public interface AdminBannerRepository extends JpaRepository<Banner, Long> {

    /**
     * 모든 배너 목록을 정렬 순서(sortOrder 오름차순)로 페이지네이션하여 조회한다.
     *
     * <p>관리자 배너 목록 화면에서 사용한다.
     * 숫자가 작을수록 우선 노출되므로 오름차순 정렬이 기본이다.</p>
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 배너 페이지
     */
    Page<Banner> findAllByOrderBySortOrderAsc(Pageable pageable);

    /**
     * 현재 활성화된(isActive=true) 배너 목록을 정렬 순서(sortOrder 오름차순)로 조회한다.
     *
     * <p>프론트엔드 메인 페이지에서 노출할 배너 목록을 가져올 때 사용한다.</p>
     *
     * @return 활성 배너 목록 (sortOrder 오름차순)
     */
    List<Banner> findByIsActiveTrueOrderBySortOrderAsc();
}
