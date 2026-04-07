package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqReorderRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeReorderRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.ProfanityCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.ProfanityImportResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.ProfanityResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketReplyItem;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketReplyRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketStats;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketStatusUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketSummary;
import com.monglepick.monglepickbackend.admin.service.AdminSupportService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 관리자 고객센터 API 컨트롤러.
 *
 * <p>관리자 페이지 "고객센터" 탭의 23개 엔드포인트를 제공한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.3 고객센터(23 API).</p>
 *
 * <h3>담당 엔드포인트 (23개)</h3>
 * <ul>
 *   <li>공지사항(5): GET/POST /notices, PUT/DELETE /notices/{id}, PUT /notices/reorder</li>
 *   <li>FAQ(5): GET/POST /faq, PUT/DELETE /faq/{id}, PUT /faq/reorder</li>
 *   <li>도움말(4): GET/POST /help-articles, PUT/DELETE /help-articles/{id}</li>
 *   <li>티켓(5): GET /tickets, GET /tickets/{id}, POST /tickets/{id}/reply, PUT /tickets/{id}/status, GET /tickets/stats</li>
 *   <li>비속어(4): GET/POST /profanity, DELETE /profanity/{id}, POST /profanity/import, GET /profanity/export</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 고객센터", description = "공지사항/FAQ/도움말/상담 티켓/비속어 사전 관리")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminSupportController {

    /** 고객센터 비즈니스 로직 서비스 */
    private final AdminSupportService adminSupportService;

    // ======================== 공지사항 ========================

    @Operation(summary = "공지사항 목록 조회",
            description = "공지사항 목록을 상단 고정 우선 + 최신순으로 페이징 조회한다. noticeType 필터 가능.")
    @GetMapping("/notices")
    public ResponseEntity<ApiResponse<Page<NoticeResponse>>> getNotices(
            @Parameter(description = "유형 필터 (NOTICE/UPDATE/MAINTENANCE)")
            @RequestParam(required = false) String noticeType,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getNotices(noticeType, pageable)));
    }

    @Operation(summary = "공지사항 등록")
    @PostMapping("/notices")
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(
            @RequestBody @Valid NoticeCreateRequest request
    ) {
        NoticeResponse result = adminSupportService.createNotice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    @Operation(summary = "공지사항 수정")
    @PutMapping("/notices/{id}")
    public ResponseEntity<ApiResponse<NoticeResponse>> updateNotice(
            @PathVariable Long id,
            @RequestBody @Valid NoticeUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.updateNotice(id, request)));
    }

    @Operation(summary = "공지사항 삭제")
    @DeleteMapping("/notices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@PathVariable Long id) {
        adminSupportService.deleteNotice(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "공지사항 순서 변경",
            description = "orderedIds 의 배열 인덱스를 그대로 sortOrder 에 반영한다.")
    @PutMapping("/notices/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderNotices(
            @RequestBody @Valid NoticeReorderRequest request
    ) {
        adminSupportService.reorderNotices(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ======================== FAQ ========================

    @Operation(summary = "FAQ 목록 조회",
            description = "FAQ 목록을 표시 순서 + 최신순으로 페이징 조회한다. category 필터 가능.")
    @GetMapping("/faq")
    public ResponseEntity<ApiResponse<Page<FaqResponse>>> getFaqs(
            @Parameter(description = "카테고리 필터")
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getFaqs(category, pageable)));
    }

    @Operation(summary = "FAQ 등록")
    @PostMapping("/faq")
    public ResponseEntity<ApiResponse<FaqResponse>> createFaq(
            @RequestBody @Valid FaqCreateRequest request
    ) {
        FaqResponse result = adminSupportService.createFaq(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    @Operation(summary = "FAQ 수정")
    @PutMapping("/faq/{id}")
    public ResponseEntity<ApiResponse<FaqResponse>> updateFaq(
            @PathVariable Long id,
            @RequestBody @Valid FaqUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.updateFaq(id, request)));
    }

    @Operation(summary = "FAQ 삭제")
    @DeleteMapping("/faq/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(@PathVariable Long id) {
        adminSupportService.deleteFaq(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "FAQ 순서 변경")
    @PutMapping("/faq/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderFaqs(
            @RequestBody @Valid FaqReorderRequest request
    ) {
        adminSupportService.reorderFaqs(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ======================== 도움말 ========================

    @Operation(summary = "도움말 목록 조회")
    @GetMapping("/help-articles")
    public ResponseEntity<ApiResponse<Page<HelpArticleResponse>>> getHelpArticles(
            @Parameter(description = "카테고리 필터")
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getHelpArticles(category, pageable)));
    }

    @Operation(summary = "도움말 등록")
    @PostMapping("/help-articles")
    public ResponseEntity<ApiResponse<HelpArticleResponse>> createHelpArticle(
            @RequestBody @Valid HelpArticleCreateRequest request
    ) {
        HelpArticleResponse result = adminSupportService.createHelpArticle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    @Operation(summary = "도움말 수정")
    @PutMapping("/help-articles/{id}")
    public ResponseEntity<ApiResponse<HelpArticleResponse>> updateHelpArticle(
            @PathVariable Long id,
            @RequestBody @Valid HelpArticleUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.updateHelpArticle(id, request)));
    }

    @Operation(summary = "도움말 삭제")
    @DeleteMapping("/help-articles/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHelpArticle(@PathVariable Long id) {
        adminSupportService.deleteHelpArticle(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ======================== 티켓 ========================

    @Operation(summary = "상담 티켓 목록 조회",
            description = "전체 티켓 또는 상태별 필터 조회. 최신순 페이징.")
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<Page<TicketSummary>>> getTickets(
            @Parameter(description = "티켓 상태 필터 (OPEN/IN_PROGRESS/RESOLVED/CLOSED)")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getTickets(status, pageable)));
    }

    @Operation(summary = "상담 티켓 통계 조회")
    @GetMapping("/tickets/stats")
    public ResponseEntity<ApiResponse<TicketStats>> getTicketStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getTicketStats()));
    }

    @Operation(summary = "상담 티켓 상세 조회",
            description = "티켓 본문 + 답변 리스트를 시간 오름차순으로 반환한다.")
    @GetMapping("/tickets/{id}")
    public ResponseEntity<ApiResponse<TicketDetail>> getTicketDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getTicketDetail(id)));
    }

    @Operation(summary = "상담 티켓 답변 작성",
            description = "답변 저장 시 티켓이 OPEN 상태였다면 IN_PROGRESS 로 자동 전이된다.")
    @PostMapping("/tickets/{id}/reply")
    public ResponseEntity<ApiResponse<TicketReplyItem>> replyToTicket(
            @PathVariable Long id,
            @RequestBody @Valid TicketReplyRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String adminUserId = principal != null ? principal.getUsername() : null;
        TicketReplyItem result = adminSupportService.replyToTicket(id, adminUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    @Operation(summary = "상담 티켓 상태 변경",
            description = "IN_PROGRESS / RESOLVED / CLOSED 로 전이. OPEN 으로의 되돌리기는 지원하지 않는다.")
    @PutMapping("/tickets/{id}/status")
    public ResponseEntity<ApiResponse<TicketSummary>> updateTicketStatus(
            @PathVariable Long id,
            @RequestBody @Valid TicketStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.updateTicketStatus(id, request)));
    }

    // ======================== 비속어 ========================

    @Operation(summary = "비속어 목록 조회")
    @GetMapping("/profanity")
    public ResponseEntity<ApiResponse<Page<ProfanityResponse>>> getProfanities(
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminSupportService.getProfanities(pageable)));
    }

    @Operation(summary = "비속어 추가")
    @PostMapping("/profanity")
    public ResponseEntity<ApiResponse<ProfanityResponse>> addProfanity(
            @RequestBody @Valid ProfanityCreateRequest request
    ) {
        ProfanityResponse result = adminSupportService.addProfanity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
    }

    @Operation(summary = "비속어 삭제")
    @DeleteMapping("/profanity/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProfanity(@PathVariable Long id) {
        adminSupportService.deleteProfanity(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "비속어 CSV 임포트",
            description = "CSV 형식: word,severity,note. 첫 줄이 헤더이면 자동으로 건너뛴다.")
    @PostMapping(value = "/profanity/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfanityImportResponse>> importProfanity(
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드 파일이 비어 있습니다.");
        }
        try {
            ProfanityImportResponse result = adminSupportService.importProfanityCsv(file.getBytes());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IOException e) {
            log.error("[AdminSupport] CSV 파일 읽기 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일 읽기 실패: " + e.getMessage());
        }
    }

    @Operation(summary = "비속어 CSV 익스포트")
    @GetMapping("/profanity/export")
    public ResponseEntity<Resource> exportProfanity() {
        String csv = adminSupportService.exportProfanityCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"profanity.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
