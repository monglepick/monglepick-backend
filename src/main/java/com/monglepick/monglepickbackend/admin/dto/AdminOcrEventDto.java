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
     */
    public record CreateOcrEventRequest(
            @NotBlank(message = "대상 영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

            @NotNull(message = "이벤트 시작일은 필수입니다.")
            LocalDateTime startDate,

            @NotNull(message = "이벤트 종료일은 필수입니다.")
            LocalDateTime endDate
    ) {}

    /**
     * 기존 OCR 이벤트 메타 수정 요청.
     */
    public record UpdateOcrEventRequest(
            @NotBlank(message = "대상 영화 ID는 필수입니다.")
            @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다.")
            String movieId,

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
            LocalDateTime startDate,
            LocalDateTime endDate,
            String adminId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
