package com.monglepick.monglepickbackend.domain.community.ocrevent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 유저 OCR 실관람 인증 요청/응답 DTO 모음 (2026-04-14 신규).
 *
 * <p>클라이언트는 먼저 {@code POST /api/v1/images/upload} 로 영수증 이미지를
 * 업로드해 URL 을 얻은 뒤, 본 DTO 로 이벤트에 인증을 제출한다.
 * (기존 커뮤니티 이미지 업로드 인프라 재사용 — 별도 multipart EP 신설 회피.)</p>
 */
public class UserVerificationDto {

    /**
     * 영수증 이미지 URL 기반 인증 제출 요청.
     *
     * <p>프론트엔드가 {@code POST /api/v1/ocr-events/analyze} 로 미리 추출한 OCR 결과를
     * 함께 전달한다. 백엔드는 이 값을 그대로 저장하므로 제출 시 OCR 재호출이 없다.</p>
     */
    public record SubmitRequest(
            /** 이미지 업로드 EP 로부터 받은 영수증 이미지 URL */
            @NotBlank(message = "영수증 이미지 URL은 필수입니다.")
            @Size(max = 500, message = "이미지 URL 은 500자 이하여야 합니다.")
            String imageUrl,

            /** /analyze 에서 추출된 영화명 (null 허용) */
            @Size(max = 200, message = "영화명은 200자 이하여야 합니다.")
            String extractedMovieName,

            /** /analyze 에서 추출된 관람일 (null 허용) */
            @Size(max = 50, message = "관람일은 50자 이하여야 합니다.")
            String extractedWatchDate,

            /** /analyze 에서 추출된 관람 인원 수 (null 허용) */
            Integer extractedHeadcount,

            /** /analyze OCR 신뢰도 (null 허용) */
            Double ocrConfidence,

            /** /analyze 에서 추출된 좌석 정보 (null 허용) */
            @Size(max = 100)
            String extractedSeat,

            /** /analyze 에서 추출된 상영관 번호 (null 허용) */
            @Size(max = 50)
            String extractedTheater,

            /** /analyze 에서 추출된 영화관 지점명 (null 허용) */
            @Size(max = 100)
            String extractedVenue,

            /** /analyze 에서 추출된 상영 시각 HH:MM (null 허용) */
            @Size(max = 20)
            String extractedScreeningTime,

            /** /analyze 에서 추출된 관람일시 조합 YYYY-MM-DD HH:MM (null 허용) */
            @Size(max = 30)
            String extractedWatchedAt,

            /** OCR 정규화 원문 텍스트 (null 허용) */
            @Size(max = 5000)
            String parsedText
    ) {}

    /**
     * 인증 제출 성공 응답.
     *
     * @param verificationId 저장된 인증 PK
     * @param eventId        대상 이벤트 PK
     * @param message        유저 노출용 친화 메시지
     */
    public record SubmitResponse(
            Long verificationId,
            Long eventId,
            String message
    ) {}

    /**
     * OCR 미리보기 분석 요청.
     * 프론트엔드가 이미지 업로드 직후 영화명/관람일 자동 추출 결과를 확인하기 위해 호출한다.
     */
    public record AnalyzeRequest(
            @NotBlank(message = "영수증 이미지 URL은 필수입니다.")
            @Size(max = 500)
            String imageUrl,

            String eventId
    ) {}

    /**
     * 개별 OCR 필드 값 + 추출 성공 여부 묶음.
     *
     * @param <T> 필드 값 타입 (String / Integer)
     */
    public record OcrField<T>(T value, boolean ok) {
        public static <T> OcrField<T> of(T value) {
            return new OcrField<>(value, value != null);
        }
        public static <T> OcrField<T> failed() {
            return new OcrField<>(null, false);
        }
    }

    /**
     * OCR 미리보기 분석 응답.
     *
     * <p>{@code status}: SUCCESS(영화명+관람일 모두 추출) /
     * PARTIAL_SUCCESS(1개 이상) / FAILED(없음).</p>
     */
    public record AnalyzeResponse(
            boolean success,
            /** SUCCESS / PARTIAL_SUCCESS / FAILED */
            String status,
            OcrField<String> movieName,
            OcrField<String> watchDate,
            OcrField<Integer> headcount,
            OcrField<String> seat,
            OcrField<String> screeningTime,
            OcrField<String> theater,
            /** 영화관 지점명 (예: CGV 홍대) */
            OcrField<String> venue,
            /** 날짜 + 시간 조합 (YYYY-MM-DD HH:MM) */
            OcrField<String> watchedAt,
            Double ocrConfidence,
            /** OCR 정규화 원문 텍스트 (제출 시 SubmitRequest.parsedText 로 전달) */
            String parsedText,
            String errorMessage
    ) {}
}
