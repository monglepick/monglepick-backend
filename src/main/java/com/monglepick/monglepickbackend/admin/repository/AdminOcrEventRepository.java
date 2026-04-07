package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 OCR 인증 이벤트 리포지토리.
 *
 * <p>OcrEvent 엔티티는 이민수 community 도메인이지만, 본 리포지토리는 admin 도메인에서
 * 별도 관리한다. AdminReportRepository(PostDeclaration JPA)와 같은 패턴.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findAllByOrderByCreatedAtDesc} — 전체 이벤트 최신순 페이징</li>
 *   <li>{@link #findByStatusOrderByCreatedAtDesc} — 상태별 이벤트 최신순 페이징</li>
 * </ul>
 */
public interface AdminOcrEventRepository extends JpaRepository<OcrEvent, Long> {

    /**
     * 전체 OCR 이벤트 최신순 페이징 조회.
     *
     * @param pageable 페이지 정보
     * @return 이벤트 페이지
     */
    Page<OcrEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 상태의 OCR 이벤트 최신순 페이징 조회.
     *
     * @param status   READY/ACTIVE/CLOSED
     * @param pageable 페이지 정보
     * @return 이벤트 페이지
     */
    Page<OcrEvent> findByStatusOrderByCreatedAtDesc(OcrEventStatus status, Pageable pageable);
}
