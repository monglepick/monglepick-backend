package com.monglepick.monglepickbackend.domain.notice.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeResponse;
import com.monglepick.monglepickbackend.admin.service.AdminSupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 앱 메인 공지 사용자 조회 컨트롤러 (비로그인 허용).
 *
 * <p>2026-04-08 개편: AppNotice 엔티티 폐기로 본 컨트롤러는 SupportNotice의
 * displayType IN (BANNER/POPUP/MODAL) 레코드를 조회하도록 변경되었다.
 * 공개 엔드포인트 경로 {@code GET /api/v1/notices} 는 하위 호환 유지.</p>
 *
 * <p>활성 + 기간 조건을 만족하는 공지만 반환한다:
 * {@code is_active=true AND (start_at IS NULL OR start_at <= NOW())
 *        AND (end_at IS NULL OR end_at >= NOW())}</p>
 */
@Tag(name = "공지사항", description = "앱 메인 BANNER/POPUP/MODAL 공지 조회 (비로그인 허용)")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
@Slf4j
public class AppNoticeController {

    /** 2026-04-08: 구 AdminAppNoticeService → AdminSupportService(통합 서비스)로 전환 */
    private final AdminSupportService adminSupportService;

    /**
     * 현재 노출 중인 앱 메인 공지 목록 조회.
     *
     * @param type BANNER/POPUP/MODAL 필터 (생략 시 앱 메인 전체)
     * @return 노출 중 공지 목록 (priority DESC, createdAt DESC)
     */
    @Operation(
            summary = "노출 중 공지 조회",
            description = "현재 시각 기준 is_active=true AND 시작일~종료일 범위 내인 "
                    + "BANNER/POPUP/MODAL 공지만 반환 (LIST_ONLY 공지는 제외)"
    )
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<List<NoticeResponse>> getActiveNotices(
            @Parameter(description = "공지 종류 필터 (BANNER/POPUP/MODAL, 생략 시 전체)")
            @RequestParam(required = false) String type,
            @Parameter(description = "고정 여부 필터 (true=고정만, false=미고정만, 생략 시 전체). "
                    + "홈 화면은 pinned=true 로 호출해 배너 과다 노출을 방지한다.")
            @RequestParam(required = false) Boolean pinned
    ) {
        log.debug("[AppNoticeController] 노출 중 공지 조회 — type={}, pinned={}", type, pinned);
        return ResponseEntity.ok(adminSupportService.getActiveAppNotices(type, pinned));
    }

    /**
     * 커뮤니티 공지 탭용 전체 활성 공지 페이징 조회 (비로그인 허용).
     *
     * <p>{@code GET /api/v1/notices} (홈 메인용) 가 BANNER/POPUP/MODAL 만 반환하는 것과
     * 달리, 이 EP 는 LIST_ONLY 포함 전체 활성/기간 내 공지를 반환한다.
     * 정렬: isPinned DESC (고정 우선), createdAt DESC (최신).</p>
     *
     * @param page 페이지 번호 (0-base, 기본 0)
     * @param size 페이지 크기 (기본 20, 최대 100)
     * @return 활성 공지 페이지 (Spring Data Page 직렬화)
     */
    @Operation(
            summary = "커뮤니티 공지 전체 목록 조회 (페이징)",
            description = "커뮤니티 '공지사항' 탭용. LIST_ONLY 포함 활성/기간 내 공지를 "
                    + "고정(isPinned) 우선 + 최신순으로 페이징 반환"
    )
    @SecurityRequirement(name = "")
    @GetMapping("/list")
    public ResponseEntity<Page<NoticeResponse>> getNoticeList(
            @Parameter(description = "페이지 번호 (0-base)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)")
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("[AppNoticeController] 공개 공지 페이지 조회 — page={}, size={}", page, size);
        // 페이지 크기 방어 — 악성/오타 방지. 음수/0 은 20, 100 초과는 100 으로 클램프.
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return ResponseEntity.ok(adminSupportService.getActivePublicNotices(pageable));
    }

    /**
     * 공지 단건 상세 조회 (비로그인 허용).
     *
     * <p>커뮤니티 공지 탭에서 특정 공지로 딥링크 진입 시
     * ({@code /community?tab=notices&noticeId={id}}) 상세 본문을 가져올 때 사용한다.
     * <b>활성 + 기간 내</b> 공지만 반환하며, 조건을 만족하지 않으면 404 와 동일한
     * "찾을 수 없음" 으로 응답한다 (정보 누출 방지).</p>
     *
     * @param noticeId 공지 PK
     * @return 공개 가능한 공지 상세
     */
    @Operation(
            summary = "공지 단건 상세 조회",
            description = "활성/기간 내 공지만 반환. 비활성 또는 기간 외 공지는 "
                    + "'찾을 수 없음' 동일 응답 (보안상 구분 제거)"
    )
    @SecurityRequirement(name = "")
    @GetMapping("/{noticeId}")
    public ResponseEntity<NoticeResponse> getNoticeDetail(
            @Parameter(description = "공지 PK", required = true)
            @PathVariable Long noticeId
    ) {
        log.debug("[AppNoticeController] 공개 공지 단건 조회 — noticeId={}", noticeId);
        return ResponseEntity.ok(adminSupportService.getPublicNotice(noticeId));
    }
}
