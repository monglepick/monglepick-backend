package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.community.entity.PostDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 신고(PostDeclaration) JPA 리포지토리.
 *
 * <p>커뮤니티 게시글·댓글 신고 내역을 관리자 화면에서 조회하기 위한 쿼리 메서드를 제공한다.
 * 일반 사용자 도메인(CommunityService 등)에서는 사용하지 않는다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByStatusOrderByCreatedAtDesc} — 특정 처리 상태의 신고 목록 최신순 조회</li>
 *   <li>{@link #findAllByOrderByCreatedAtDesc}     — 전체 신고 목록 최신순 조회 (필터 없음)</li>
 * </ul>
 *
 * <h3>PostDeclaration 처리 상태(status) 유효값</h3>
 * <ul>
 *   <li>"pending"   — 접수 대기 중</li>
 *   <li>"reviewed"  — 검토 완료 (blind/delete 조치 후)</li>
 *   <li>"resolved"  — 처리 완료</li>
 *   <li>"dismissed" — 기각</li>
 * </ul>
 */
public interface AdminReportRepository extends JpaRepository<PostDeclaration, Long> {

    /**
     * 특정 처리 상태의 신고 목록을 최신순으로 페이징 조회한다.
     *
     * <p>관리자 화면에서 "대기 중(pending)" 탭 또는 "처리 완료(reviewed)" 탭 등
     * 상태별 필터 조회에 사용한다.</p>
     *
     * @param status   처리 상태 문자열 (pending/reviewed/resolved/dismissed)
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 해당 상태의 신고 목록 페이지
     */
    Page<PostDeclaration> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 전체 신고 목록을 최신순으로 페이징 조회한다.
     *
     * <p>관리자 화면에서 상태 필터 없이 전체 신고 내역을 조회할 때 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size)
     * @return 전체 신고 목록 페이지
     */
    Page<PostDeclaration> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 처리 상태의 신고 수를 카운트한다.
     *
     * <p>관리자 대시보드 KPI 카드에서 미처리(pending) 신고 건수를 집계할 때 사용한다.</p>
     *
     * <p>예시: 미처리 신고 수</p>
     * <pre>
     * long pending = adminReportRepository.countByStatus("pending");
     * </pre>
     *
     * @param status 처리 상태 문자열 (pending / reviewed / resolved / dismissed)
     * @return 해당 상태의 신고 수
     */
    long countByStatus(String status);
}
