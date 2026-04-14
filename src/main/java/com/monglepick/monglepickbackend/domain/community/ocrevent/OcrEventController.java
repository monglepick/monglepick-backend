package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * 특정 영화의 진행 중 OCR 인증 이벤트 조회 (2026-04-14 신규).
     *
     * <p>영화 상세 페이지 상단 "실관람 인증 진행중" 배너용. AI 추천 / 검색 /
     * 커뮤니티 등 어디서 진입했든 현재 해당 영화가 OCR 이벤트 대상인지
     * 즉시 확인할 수 있도록 단건 조회 EP 를 별도로 제공한다.</p>
     *
     * <p>이벤트가 없거나 종료된 경우 {@code data: null} 로 반환한다(200 OK).
     * 클라이언트는 null 인지만 확인하고, 배너를 렌더링하지 않으면 된다.</p>
     *
     * @param movieId 영화 ID (movies.movie_id)
     * @return 단건 응답 (없으면 null data)
     */
    @Operation(
            summary = "특정 영화의 진행 중 OCR 이벤트 조회",
            description = "영화 상세 페이지 상단 배너용. ACTIVE/READY + 종료되지 않은 이벤트 1건. 없으면 data=null."
    )
    @GetMapping("/by-movie/{movieId}")
    public ResponseEntity<ApiResponse<OcrEventPublicResponse>> getByMovie(@PathVariable String movieId) {
        OcrEventPublicResponse event = ocrEventService.getActiveEventByMovie(movieId).orElse(null);
        log.debug("[OCR 이벤트] 영화별 조회 — movieId={}, found={}", movieId, event != null);
        return ResponseEntity.ok(ApiResponse.ok(event));
    }
}
