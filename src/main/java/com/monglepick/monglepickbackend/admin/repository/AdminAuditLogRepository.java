package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 감사 로그 Repository.
 *
 * <p>관리자 페이지 "설정 → 감사 로그" 탭의 조회 작업에 사용된다.
 * 도메인 레이어의 admin 패키지에 위치한 {@link AdminAuditLog} 엔티티를 대상으로 한다.</p>
 *
 * <p>감사 로그는 보존 정책상 삭제하지 않으므로, 쓰기 메서드는 별도로 정의하지 않는다.</p>
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * 모든 감사 로그를 최신순(createdAt 내림차순)으로 페이지네이션하여 조회한다.
     *
     * <p>관리자 감사 로그 목록 화면 기본 조회에 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 감사 로그 페이지
     */
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * actionType에 특정 문자열이 포함된(대소문자 무시) 감사 로그를 최신순으로 조회한다.
     *
     * <p>예: "USER"로 필터링하면 USER_SUSPEND, USER_UNSUSPEND 등이 모두 조회된다.</p>
     *
     * @param actionType 검색할 행위 유형 키워드 (부분 일치)
     * @param pageable   페이지 정보
     * @return 필터링된 감사 로그 페이지
     */
    Page<AdminAuditLog> findByActionTypeContainingIgnoreCaseOrderByCreatedAtDesc(
            String actionType, Pageable pageable);
}
