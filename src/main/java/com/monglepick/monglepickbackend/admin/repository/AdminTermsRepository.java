package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.content.entity.Terms;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 약관/정책 관리자 Repository.
 *
 * <p>관리자 페이지 "설정 → 약관/정책 관리" 탭의 CRUD 작업에 사용된다.
 * 도메인 레이어의 content 패키지에 위치한 {@link Terms} 엔티티를 대상으로 한다.</p>
 */
public interface AdminTermsRepository extends JpaRepository<Terms, Long> {

    /**
     * 모든 약관 목록을 최신 등록순(createdAt 내림차순)으로 페이지네이션하여 조회한다.
     *
     * <p>관리자 약관 목록 화면에서 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 약관 페이지
     */
    Page<Terms> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 현재 활성화된(isActive=true) 약관 전체 목록을 조회한다.
     *
     * <p>프론트엔드 회원가입 화면에서 동의 목록을 렌더링할 때 사용한다.</p>
     *
     * @return 활성 약관 목록
     */
    List<Terms> findByIsActiveTrue();
}
