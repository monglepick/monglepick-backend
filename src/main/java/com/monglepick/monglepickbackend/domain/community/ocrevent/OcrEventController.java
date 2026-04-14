package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 유저 전용 OCR 인증 이벤트 공개 컨트롤러.
 *
 * <p>관리자 {@code /api/v1/admin/ocr-events}와 분리된 유저 공개 API.
 * 커뮤니티 "실관람인증" 탭에서 진행 중/예정 이벤트 목록을 보여주기 위한 단일 EP 를 제공한다.</p>
 *
 * <h3>보안</h3>
 * <p>비로그인 접근 허용. {@code SecurityConfig} 에서
 * {@code GET /api/v1/ocr-events/**} 경로를 permitAll 로 노출한다.</p>
 */
@Tag(name = "커뮤니티 — OCR 이벤트", description = "유저 공개 실관람 인증 이벤트 조회")
@Slf4j
@RestController
@RequestMapping("/api/v1/ocr-events")
@RequiredArgsConstructor
public class OcrEventController {

    private final OcrEventService ocrEventService;

    /**
     * 현재 진행 중이거나 곧 시작하는 OCR 인증 이벤트 목록 조회.
     *
     * <p>반환 조건: {@code status IN (ACTIVE, READY)} AND {@code endDate > now()}.
     * CLOSED 혹은 이미 종료된 이벤트는 포함되지 않는다.</p>
     *
     * @return OCR 이벤트 공개 응답 목록 (영화 메타 포함)
     */
    @Operation(
            summary = "공개 OCR 이벤트 목록",
            description = "커뮤니티 실관람인증 탭용. ACTIVE/READY 상태의 종료되지 않은 이벤트를 반환한다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<OcrEventPublicResponse>>> getPublicEvents() {
        List<OcrEventPublicResponse> events = ocrEventService.getPublicEvents();
        log.debug("[OCR 이벤트] 공개 목록 조회 — count={}", events.size());
        return ResponseEntity.ok(ApiResponse.ok(events));
    }
}
