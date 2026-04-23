package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유저 OCR 실관람 인증 제출 컨트롤러 (2026-04-14 신규).
 *
 * <p>영화 상세 페이지 상단 배너 → "인증하러 가기" → 영수증 업로드 → 제출 플로우의 제출 EP.
 * 조회·목록 EP 가 있는 {@link OcrEventController}({@code GET /api/v1/ocr-events/**}) 와
 * 경로를 공유하되, 본 컨트롤러는 JWT 보호 쓰기 작업 전용이라 파일을 분리했다.</p>
 *
 * <h3>보안</h3>
 * <p>{@code POST /api/v1/ocr-events/{eventId}/verify} 는 {@code SecurityConfig}
 * 의 기본 정책에 따라 인증 필요. GET permitAll 규칙은 메서드 한정이므로 POST 는
 * 별도 명시 없이도 {@code authenticated()} 로 적용된다.</p>
 */
@Tag(name = "커뮤니티 — OCR 이벤트", description = "유저 실관람 인증 제출")
@Slf4j
@RestController
@RequestMapping("/api/v1/ocr-events")
@RequiredArgsConstructor
public class UserVerificationController {

    private final UserVerificationService userVerificationService;
    private final OcrAnalysisClient ocrAnalysisClient;

    /**
     * OCR 미리보기 분석 — 이미지에서 영화명/관람일/인원 추출 결과를 반환한다.
     *
     * <p>프론트엔드 OcrVerificationModal 이 이미지 업로드 직후 호출하여
     * 유저에게 자동 추출 결과를 미리 보여주기 위한 읽기 전용 엔드포인트.
     * DB 에 저장하지 않으며 인증 제출과 무관하게 항상 200 을 반환한다.</p>
     */
    @Operation(
            summary = "OCR 영수증 미리보기 분석",
            description = "이미지 URL 을 전달하면 Python OCR 서버가 영화명/관람일/인원을 추출해 반환한다. DB 저장 없음."
    )
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<UserVerificationDto.AnalyzeResponse>> analyze(
            @Valid @RequestBody UserVerificationDto.AnalyzeRequest request
    ) {
        OcrAnalysisClient.OcrResponse ocr = ocrAnalysisClient.analyze(
                request.imageUrl(),
                request.eventId() != null ? request.eventId() : ""
        );

        UserVerificationDto.AnalyzeResponse response;
        if (ocr != null && ocr.success()) {
            response = new UserVerificationDto.AnalyzeResponse(
                    true,
                    ocr.status(),
                    UserVerificationDto.OcrField.of(ocr.movieName()),
                    UserVerificationDto.OcrField.of(ocr.watchDate()),
                    UserVerificationDto.OcrField.of(ocr.headcount()),
                    UserVerificationDto.OcrField.of(ocr.seat()),
                    UserVerificationDto.OcrField.of(ocr.screeningTime()),
                    UserVerificationDto.OcrField.of(ocr.theater()),
                    UserVerificationDto.OcrField.of(ocr.venue()),
                    UserVerificationDto.OcrField.of(ocr.watchedAt()),
                    ocr.confidence(),
                    ocr.parsedText(),
                    null
            );
        } else {
            String errorMsg = (ocr != null) ? ocr.errorMessage() : "OCR 서버에 연결할 수 없습니다.";
            response = new UserVerificationDto.AnalyzeResponse(
                    false, "FAILED",
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    UserVerificationDto.OcrField.failed(),
                    null, null, errorMsg
            );
        }

        log.info("[OCR 미리보기] imageUrl={} status={} movie={} date={} headcount={} seat={} time={} theater={} venue={} watchedAt={} confidence={}",
                request.imageUrl(), response.status(),
                response.movieName().value(), response.watchDate().value(),
                response.headcount().value(), response.seat().value(),
                response.screeningTime().value(), response.theater().value(),
                response.venue().value(), response.watchedAt().value(),
                response.ocrConfidence());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 영수증 이미지 URL 기반 OCR 인증 제출.
     *
     * <p>클라이언트는 먼저 {@code POST /api/v1/images/upload} 로 영수증 이미지를
     * 업로드해 URL 을 받은 뒤, 본 EP 에 JSON 본문으로 제출한다.</p>
     *
     * @param userId  JWT principal 로 주입된 사용자 ID
     * @param eventId 대상 이벤트 PK
     * @param request 이미지 URL + 관람일(선택) + 영화명(선택)
     * @return 저장된 인증 PK + 안내 메시지 (201 Created)
     */
    @Operation(
            summary = "OCR 실관람 인증 제출",
            description = "영수증 이미지 URL 로 실관람 인증 제출. 이벤트가 ACTIVE 상태일 때만 허용, 중복 제출 차단."
    )
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{eventId}/verify")
    public ResponseEntity<ApiResponse<UserVerificationDto.SubmitResponse>> submit(
            @AuthenticationPrincipal String userId,
            @PathVariable Long eventId,
            @Valid @RequestBody UserVerificationDto.SubmitRequest request
    ) {
        UserVerificationDto.SubmitResponse result =
                userVerificationService.submitVerification(userId, eventId, request);
        log.info("[OCR 인증] 제출 완료 — userId={}, eventId={}, verificationId={}",
                userId, eventId, result.verificationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }
}
