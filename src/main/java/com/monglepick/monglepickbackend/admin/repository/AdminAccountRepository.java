package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.user.entity.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 관리자 계정 Repository.
 *
 * <p>관리자 페이지 "설정 → 관리자 계정 관리" 탭의 조회/역할 수정 작업에 사용된다.
 * 도메인 레이어의 user 패키지에 위치한 {@link Admin} 엔티티를 대상으로 한다.</p>
 */
public interface AdminAccountRepository extends JpaRepository<Admin, Long> {

    /**
     * 모든 관리자 계정을 최신 등록순(createdAt 내림차순)으로 페이지네이션하여 조회한다.
     *
     * <p>관리자 계정 목록 화면에서 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 관리자 계정 페이지
     */
    Page<Admin> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * userId로 관리자 계정을 단건 조회한다.
     *
     * <p>특정 사용자의 관리자 권한 존재 여부 확인 또는 역할 수정 전 사전 검증에 사용한다.</p>
     *
     * @param userId 사용자 ID (VARCHAR 50)
     * @return 관리자 계정 Optional
     */
    Optional<Admin> findByUserId(String userId);
}
