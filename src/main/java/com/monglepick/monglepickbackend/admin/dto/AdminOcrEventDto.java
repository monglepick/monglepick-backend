package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 OCR 인증 이벤트(OcrEvent) 관리 DTO 모음.
 *
 * <p>관리자가 특정 영화에 대해 실관람 인증 이벤트를 생성/수정한다.
 * 이벤트는 READY → ACTIVE → CLOSED 라이프사이클을 가진다.</p>
 *
 * <h3>포함 DTO</h3>
 * <ul>
 *   <li>{@link CreateOcrEventRequest} — 신규 이벤트 등록</li>
 *   <li>{@link UpdateOcrEventRequest} — 기존 이벤트 메타 수정 (movieId/start/end)</li>
 *   <li>{@link UpdateStatusRequest}   — 상태 전이 (READY/ACTIVE/CLOSED)</li>
 *   <li>{@link OcrEventResponse}      — 이벤트 단일 항목 응답</li>
 * </ul>
 */
public class AdminOcrEventDto {

    /**
     * 신규 OCR 이벤트 등록 요청.
     *
     * <p>2026-04-14: title(제목)·memo(상세/메모) 필드 추가.
     * 유저 페이지 커뮤니티 "실관람인증" 탭 카드에 노출되는 핵심 메타.</p>
     */
    public record CreateOcrEventRequest(
            @NotBlank(message = "대상 영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

            /** 이벤트 제목 (관리자/유저 양쪽 노출) */
            @NotBlank(message = "이벤트 제목은 필수입니다.")
            @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
            String title,

            /** 이벤트 상세 메모 (선택, 유저 카드 본문) */
            @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
            String memo,

            @NotNull(message = "이벤트 시작일은 필수입니다.")
            LocalDateTime startDate,

            @NotNull(message = "이벤트 종료일은 필수입니다.")
            LocalDateTime endDate
    ) {}

    /**
     * 기존 OCR 이벤트 메타 수정 요청.
     *
     * <p>2026-04-14: title/memo 필드 추가. 수정 폼에서 제목/메모도 함께 편집 가능.</p>
     */
    public record UpdateOcrEventRequest(
            @NotBlank(message = "대상 영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

            @NotBlank(message = "이벤트 제목은 필수입니다.")
            @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
            String title,

            @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
            String memo,

            @NotNull(message = "이벤트 시작일은 필수입니다.")
            LocalDateTime startDate,

            @NotNull(message = "이벤트 종료일은 필수입니다.")
            LocalDateTime endDate
    ) {}

    /**
     * OCR 이벤트 상태 전이 요청.
     *
     * <p>{@code targetStatus} = READY/ACTIVE/CLOSED. 잘못된 값은 400.</p>
     */
    public record UpdateStatusRequest(
            @NotBlank(message = "변경할 상태(targetStatus)는 필수입니다.")
            String targetStatus
    ) {}

    /**
     * OCR 이벤트 단일 항목 응답.
     */
    public record OcrEventResponse(
            Long eventId,
            String movieId,
            String title,
            String memo,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String adminId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * 유저 실관람 인증 단일 항목 응답 (관리자 검토용).
     *
     * <p>OCR 분석 결과 전 필드 + 영수증 원문 이미지 URL + 관리자 처리 상태를 반환한다.</p>
     */
    public record VerificationResponse(
            Long verificationId,
            String userId,
            String movieId,
            String eventId,
            String imageUrl,
            String extractedMovieName,
            String extractedWatchDate,
            Integer extractedHeadcount,
            String extractedSeat,
            String extractedTheater,
            String extractedVenue,
            String extractedScreeningTime,
            String extractedWatchedAt,
            Double ocrConfidence,
            String parsedText,
            String status,           // PENDING / APPROVED / REJECTED
            String reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt
    ) {}

    /** 관리자 승인/반려 요청 */
    public record ReviewRequest(
            @NotBlank(message = "action은 APPROVE 또는 REJECT 여야 합니다.")
            String action   // "APPROVE" | "REJECT"
    ) {}
}
