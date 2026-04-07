package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 공지사항 리포지토리.
 *
 * <p>관리자 페이지 "고객센터 → 공지사항" 탭의 CRUD 쿼리 메서드를 제공한다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findAllByOrderByIsPinnedDescCreatedAtDesc} — 상단 고정 우선 + 최신순 페이징</li>
 *   <li>{@link #findByNoticeTypeOrderByIsPinnedDescCreatedAtDesc} — 유형별 필터 + 상단 고정 우선</li>
 * </ul>
 */
public interface AdminNoticeRepository extends JpaRepository<SupportNotice, Long> {

    /**
     * 상단 고정 여부(내림차순) + 생성일시(내림차순) 우선순위로 공지 목록을 페이징 조회한다.
     *
     * <p>고정 공지가 먼저 나오고, 그 다음으로 최신순 공지가 정렬된다.</p>
     *
     * @param pageable 페이지 정보
     * @return 공지사항 페이지
     */
    Page<SupportNotice> findAllByOrderByIsPinnedDescCreatedAtDesc(Pageable pageable);

    /**
     * 유형별 공지 목록을 상단 고정 우선 + 최신순으로 페이징 조회한다.
     *
     * @param noticeType 유형 코드 (NOTICE/UPDATE/MAINTENANCE)
     * @param pageable   페이지 정보
     * @return 해당 유형의 공지사항 페이지
     */
    Page<SupportNotice> findByNoticeTypeOrderByIsPinnedDescCreatedAtDesc(
            String noticeType, Pageable pageable
    );
}
