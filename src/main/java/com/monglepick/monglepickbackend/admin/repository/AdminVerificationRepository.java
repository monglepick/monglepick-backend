package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.community.entity.UserVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 유저 인증(UserVerification) 리포지토리.
 *
 * <p>이벤트별 인증 목록 조회 + 상태별 필터링을 담당한다.</p>
 */
public interface AdminVerificationRepository extends JpaRepository<UserVerification, Long> {

    /** 이벤트 ID의 전체 인증 목록 (최신순) */
    Page<UserVerification> findByEventIdOrderByCreatedAtDesc(String eventId, Pageable pageable);

    /** 이벤트 ID + 상태별 인증 목록 (최신순) */
    Page<UserVerification> findByEventIdAndStatusOrderByCreatedAtDesc(
            String eventId, String status, Pageable pageable);

    /** 이벤트 ID의 전체 인증 건수 */
    long countByEventId(String eventId);

    /** 이벤트 ID + 상태별 인증 건수 */
    long countByEventIdAndStatus(String eventId, String status);
}