package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.CreateOcrEventRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.OcrEventResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.UpdateOcrEventRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminOcrEventDto.UpdateStatusRequest;
import com.monglepick.monglepickbackend.admin.repository.AdminOcrEventRepository;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus;
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
 * 관리자 OCR 인증 이벤트(OcrEvent) 관리 서비스.
 *
 * <p>관리자가 특정 영화에 대해 실관람 인증 이벤트를 생성/수정/상태 전이/삭제한다.
 * 이벤트는 READY → ACTIVE → CLOSED 라이프사이클을 가진다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>이벤트 목록 조회 (페이징 + 상태 필터)</li>
 *   <li>이벤트 단건 조회</li>
 *   <li>이벤트 신규 등록 (admin_id는 SecurityContext에서 자동 추출)</li>
 *   <li>이벤트 메타 수정 (movieId/startDate/endDate)</li>
 *   <li>이벤트 상태 전이 (READY/ACTIVE/CLOSED)</li>
 *   <li>이벤트 hard delete</li>
 * </ol>
 *
 * <h3>날짜 검증</h3>
 * <p>startDate &lt; endDate 가 아닌 경우 INVALID_OCR_EVENT_PERIOD 발생.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOcrEventService {

    /** 관리자 전용 OCR 이벤트 리포지토리 (JPA) */
    private final AdminOcrEventRepository adminOcrEventRepository;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 이벤트 목록 조회 (페이징 + 상태 필터).
     *
     * @param status   READY/ACTIVE/CLOSED (null이면 전체)
     * @param pageable 페이지 정보
     * @return 페이징된 이벤트 응답
     */
    public Page<OcrEventResponse> getEvents(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            OcrEventStatus statusEnum = parseStatus(status);
            return adminOcrEventRepository
                    .findByStatusOrderByCreatedAtDesc(statusEnum, pageable)
                    .map(this::toResponse);
        }
        return adminOcrEventRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    /**
     * 이벤트 단건 조회.
     */
    public OcrEventResponse getEvent(Long eventId) {
        return toResponse(findEventByIdOrThrow(eventId));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 신규 OCR 이벤트 등록.
     *
     * <p>admin_id는 SecurityContextHolder에서 자동 추출.
     * 신규 등록 시 status는 READY 고정.</p>
     */
    @Transactional
    public OcrEventResponse createEvent(CreateOcrEventRequest request) {
        validatePeriod(request.startDate(), request.endDate());

        String adminId = resolveCurrentAdminId();

        OcrEvent entity = OcrEvent.builder()
                .movieId(request.movieId())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .adminId(adminId)
                .build();

        OcrEvent saved = adminOcrEventRepository.save(entity);
        log.info("[관리자] OCR 이벤트 등록 — eventId={}, movieId={}, adminId={}, period={}~{}",
                saved.getEventId(), saved.getMovieId(), adminId,
                saved.getStartDate(), saved.getEndDate());

        return toResponse(saved);
    }

    /**
     * 이벤트 메타 수정 (movieId/startDate/endDate).
     */
    @Transactional
    public OcrEventResponse updateEvent(Long eventId, UpdateOcrEventRequest request) {
        validatePeriod(request.startDate(), request.endDate());

        OcrEvent entity = findEventByIdOrThrow(eventId);
        entity.updateInfo(request.movieId(), request.startDate(), request.endDate());

        log.info("[관리자] OCR 이벤트 수정 — eventId={}, movieId={}, period={}~{}",
                eventId, entity.getMovieId(), entity.getStartDate(), entity.getEndDate());
        return toResponse(entity);
    }

    /**
     * 이벤트 상태 전이.
     *
     * <p>관리자가 임의로 READY/ACTIVE/CLOSED 상태를 변경할 수 있다.
     * 검증 없이 직접 전이 허용 (운영 유연성).</p>
     */
    @Transactional
    public OcrEventResponse updateStatus(Long eventId, UpdateStatusRequest request) {
        OcrEvent entity = findEventByIdOrThrow(eventId);
        OcrEventStatus newStatus = parseStatus(request.targetStatus());
        entity.changeStatus(newStatus);

        log.info("[관리자] OCR 이벤트 상태 변경 — eventId={}, newStatus={}", eventId, newStatus);
        return toResponse(entity);
    }

    /**
     * 이벤트 hard delete.
     */
    @Transactional
    public void deleteEvent(Long eventId) {
        OcrEvent entity = findEventByIdOrThrow(eventId);
        adminOcrEventRepository.delete(entity);
        log.warn("[관리자] OCR 이벤트 삭제 — eventId={}, movieId={}", eventId, entity.getMovieId());
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    /** ID로 이벤트 조회 또는 404 */
    private OcrEvent findEventByIdOrThrow(Long eventId) {
        return adminOcrEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.OCR_EVENT_NOT_FOUND,
                        "OCR 이벤트 ID " + eventId + "를 찾을 수 없습니다"));
    }

    /** 시작일/종료일 유효성 검증 */
    private void validatePeriod(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.INVALID_OCR_EVENT_PERIOD);
        }
    }

    /** 상태 문자열 → enum 파싱 */
    private OcrEventStatus parseStatus(String status) {
        try {
            return OcrEventStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 상태 값: " + status + " (READY/ACTIVE/CLOSED)");
        }
    }

    /** SecurityContextHolder에서 현재 관리자 ID 추출 */
    private String resolveCurrentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    /** 엔티티 → 응답 DTO */
    private OcrEventResponse toResponse(OcrEvent entity) {
        return new OcrEventResponse(
                entity.getEventId(),
                entity.getMovieId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getAdminId(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
