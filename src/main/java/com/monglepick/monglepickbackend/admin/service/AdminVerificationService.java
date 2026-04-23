package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.VerificationResponse;
import com.monglepick.monglepickbackend.admin.repository.AdminVerificationRepository;
import com.monglepick.monglepickbackend.domain.community.entity.UserVerification;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 유저 인증(UserVerification) 검토 서비스.
 *
 * <p>이벤트별 인증 목록 조회 / 승인(APPROVE) / 반려(REJECT) 를 담당한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminVerificationService {

    private final AdminVerificationRepository verificationRepository;

    /**
     * 특정 이벤트의 인증 목록 조회.
     *
     * @param eventId 이벤트 ID
     * @param status  PENDING / APPROVED / REJECTED (null이면 전체)
     * @param pageable 페이지 정보
     */
    public Page<VerificationResponse> getVerifications(Long eventId, String status, Pageable pageable) {
        String eventIdStr = String.valueOf(eventId);

        Page<UserVerification> page = (status != null && !status.isBlank())
                ? verificationRepository.findByEventIdAndStatusOrderByCreatedAtDesc(eventIdStr, status.toUpperCase(), pageable)
                : verificationRepository.findByEventIdOrderByCreatedAtDesc(eventIdStr, pageable);

        return page.map(this::toResponse);
    }

    /**
     * 인증 단건 조회.
     */
    public VerificationResponse getVerification(Long verificationId) {
        return toResponse(findOrThrow(verificationId));
    }

    /**
     * 인증 승인 처리.
     */
    @Transactional
    public VerificationResponse approve(Long verificationId) {
        UserVerification v = findOrThrow(verificationId);
        v.approve(resolveAdminId());
        log.info("[관리자] 인증 승인 — verificationId={}, adminId={}", verificationId, v.getReviewedBy());
        return toResponse(v);
    }

    /**
     * 인증 반려 처리.
     */
    @Transactional
    public VerificationResponse reject(Long verificationId) {
        UserVerification v = findOrThrow(verificationId);
        v.reject(resolveAdminId());
        log.info("[관리자] 인증 반려 — verificationId={}, adminId={}", verificationId, v.getReviewedBy());
        return toResponse(v);
    }

    private UserVerification findOrThrow(Long verificationId) {
        return verificationRepository.findById(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OCR_EVENT_NOT_FOUND,
                        "인증 ID " + verificationId + "를 찾을 수 없습니다."));
    }

    private String resolveAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "unknown";
    }

    private VerificationResponse toResponse(UserVerification v) {
        return new VerificationResponse(
                v.getVerificationId(),
                v.getUserId(),
                v.getMovieId(),
                v.getEventId(),
                v.getImageId(),
                v.getExtractedMovieName(),
                v.getExtractedWatchDate(),
                v.getExtractedHeadcount(),
                v.getExtractedSeat(),
                v.getExtractedTheater(),
                v.getExtractedVenue(),
                v.getExtractedScreeningTime(),
                v.getExtractedWatchedAt(),
                v.getOcrConfidence(),
                v.getParsedText(),
                v.getStatus(),
                v.getReviewedBy(),
                v.getReviewedAt(),
                v.getCreatedAt()
        );
    }
}