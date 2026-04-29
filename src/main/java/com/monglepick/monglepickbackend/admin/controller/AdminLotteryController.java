package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.CreateLotteryRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.LotteryEntryItem;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.LotterySummary;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.LotteryUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminLotteryDto.ManualDrawResponse;
import com.monglepick.monglepickbackend.admin.service.AdminLotteryService;
import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketEntryStatus;
import com.monglepick.monglepickbackend.domain.reward.constants.MovieTicketLotteryStatus;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 관리자 — 영화 티켓 추첨 관리 API 컨트롤러 (2026-04-28 신규).
 *
 * <p>관리자 페이지 "결제/포인트 → 추첨 관리" 서브탭의 5개 EP 를 노출한다.
 * 자동 배치({@code MovieTicketLotteryBatch})와 별도로 운영자가 회차를 직접 제어할 수 있게 한다.</p>
 *
 * <h3>담당 엔드포인트 (8개, 2026-04-29 보강)</h3>
 * <ul>
 *   <li>GET /lottery/cycles — 회차 목록 페이징 (status 필터)</li>
 *   <li>POST /lottery/cycles — 회차 강제 생성 (운영자 사전 생성)</li>
 *   <li>GET /lottery/cycles/{lotteryId} — 회차 상세 (통계 포함)</li>
 *   <li>PATCH /lottery/cycles/{lotteryId} — winner_count / notes 수정</li>
 *   <li>POST /lottery/cycles/{lotteryId}/draw — 수동 추첨 실행</li>
 *   <li>POST /lottery/cycles/{lotteryId}/reset — DRAWING → PENDING 강제 복구</li>
 *   <li>GET /lottery/cycles/{lotteryId}/entries — 회차별 응모자 페이징 (status·keyword 필터)</li>
 *   <li>GET /lottery/cycles/{lotteryId}/entries/export — 응모자 CSV 다운로드</li>
 * </ul>
 *
 * <h3>인증 / 권한</h3>
 * <p>모든 EP 는 ADMIN 권한 필요. 수동 추첨처럼 결과가 비가역적인 EP 는
 * {@code @PreAuthorize("hasRole('ADMIN')")} 로 명시한다 (Step 2C 에서 SUPER_ADMIN/FINANCE_ADMIN 등으로
 * 세분화 예정).</p>
 *
 * <h3>응답 형식</h3>
 * <p>{@link ApiResponse} 래퍼 사용 — 다른 관리자 컨트롤러와 동일.</p>
 */
@Tag(name = "관리자 — 영화 티켓 추첨", description = "응모권 추첨 회차 관리, 수동 추첨, 응모자 명단")
@RestController
@RequestMapping("/api/v1/admin/lottery")
@RequiredArgsConstructor
@Slf4j
public class AdminLotteryController {

    /** 추첨 관리 비즈니스 로직 서비스 */
    private final AdminLotteryService adminLotteryService;

    // ──────────────────────────────────────────────
    // 회차 목록 / 상세
    // ──────────────────────────────────────────────

    /**
     * 회차 페이징 조회 — 회차별 통계 포함.
     *
     * @param status   회차 상태 필터 (PENDING/DRAWING/COMPLETED, 생략 시 전체)
     * @param pageable 페이징 (기본 cycleYearMonth DESC, size=20)
     * @return 회차 요약 페이지
     */
    @Operation(
            summary = "추첨 회차 목록 조회",
            description = "전체 회차를 cycleYearMonth DESC 정렬로 페이징 조회한다. "
                    + "각 회차마다 응모자 수·당첨자 수·미당첨자 수 통계를 포함한다."
    )
    @GetMapping("/cycles")
    public ResponseEntity<ApiResponse<Page<LotterySummary>>> getCycles(
            @Parameter(description = "회차 상태 필터 (PENDING/DRAWING/COMPLETED, 생략 시 전체)")
            @RequestParam(required = false) MovieTicketLotteryStatus status,
            @PageableDefault(size = 20, sort = "cycleYearMonth", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        log.debug("[AdminLottery] 회차 목록 조회 — status={}, page={}", status, pageable.getPageNumber());
        Page<LotterySummary> result = adminLotteryService.getLotteries(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 회차 단건 상세 조회.
     *
     * @param lotteryId 회차 PK
     * @return 회차 요약 (통계 포함)
     */
    @Operation(
            summary = "추첨 회차 상세 조회",
            description = "단건 회차의 상태·당첨자 수·통계·메모 등 전체 필드를 조회한다."
    )
    @GetMapping("/cycles/{lotteryId}")
    public ResponseEntity<ApiResponse<LotterySummary>> getCycle(
            @Parameter(description = "조회할 회차 PK") @PathVariable Long lotteryId
    ) {
        log.debug("[AdminLottery] 회차 상세 조회 — lotteryId={}", lotteryId);
        LotterySummary result = adminLotteryService.getLottery(lotteryId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 회차 강제 생성 (2026-04-29 신규).
     *
     * <p>자동 lazy 생성으로 충분하지만, 운영자가 미래 회차/특수 회차를 사전에 만들어 두고 싶을 때 사용.
     * 동일 cycleYearMonth 가 이미 존재하면 LTR002 (CONFLICT 의미)로 거부한다.</p>
     */
    @Operation(
            summary = "추첨 회차 강제 생성",
            description = "운영자가 미래 회차나 특수 회차를 사전 생성한다 (자동 lazy 생성 외 수동 보강용)."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cycles")
    public ResponseEntity<ApiResponse<LotterySummary>> createCycle(
            @RequestBody @Valid CreateLotteryRequest request
    ) {
        log.info("[AdminLottery] 회차 강제 생성 요청 — cycle={}, winnerCount={}",
                request.cycleYearMonth(), request.winnerCount());
        LotterySummary result = adminLotteryService.createCycle(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ──────────────────────────────────────────────
    // 수정 / 수동 추첨
    // ──────────────────────────────────────────────

    /**
     * 회차 수정 — winner_count / notes 부분 업데이트.
     *
     * <p>두 필드 모두 nullable. null 인 필드는 변경하지 않는다 (PATCH 시멘틱).
     * COMPLETED 회차의 winner_count 변경은 LTR002 로 차단된다.</p>
     *
     * @param lotteryId 회차 PK
     * @param request   수정 요청
     * @return 수정 후 회차 요약
     */
    @Operation(
            summary = "추첨 회차 수정",
            description = "회차의 당첨자 수 또는 운영자 메모를 수정한다. COMPLETED 회차의 당첨자 수는 변경 불가."
    )
    /* 회차 수정 — 추첨 결과에 영향을 주는 운영 액션이라 ADMIN 권한으로 보호 */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/cycles/{lotteryId}")
    public ResponseEntity<ApiResponse<LotterySummary>> updateCycle(
            @Parameter(description = "수정할 회차 PK") @PathVariable Long lotteryId,
            @RequestBody @Valid LotteryUpdateRequest request
    ) {
        log.info("[AdminLottery] 회차 수정 요청 — lotteryId={}, winnerCount={}, notes 갱신여부={}",
                lotteryId, request.winnerCount(), request.notes() != null);
        LotterySummary result = adminLotteryService.updateLottery(lotteryId, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 수동 추첨 실행.
     *
     * <p>도메인 {@code MovieTicketLotteryService.drawLottery} 를 호출한다.
     * 자동 배치(매월 1일 0시) 외에 운영자가 즉시 추첨하고 싶을 때 사용한다.</p>
     *
     * <h3>주의</h3>
     * <ul>
     *   <li>비가역적 — 한 번 실행하면 PENDING entry 가 모두 WON/LOST 로 확정된다.</li>
     *   <li>COMPLETED 회차는 LTR002 로 거부.</li>
     * </ul>
     *
     * @param lotteryId 회차 PK
     * @return 추첨 결과 응답
     */
    @Operation(
            summary = "추첨 회차 수동 실행",
            description = "자동 배치 외에 운영자가 직접 추첨을 실행한다. 비가역 작업이므로 신중히 호출한다."
    )
    /* 수동 추첨 — 비가역 운영 액션. ADMIN 권한으로 제한. Step 2C 에서 SUPER_ADMIN 으로 격상 검토. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cycles/{lotteryId}/draw")
    public ResponseEntity<ApiResponse<ManualDrawResponse>> drawCycle(
            @Parameter(description = "추첨할 회차 PK") @PathVariable Long lotteryId
    ) {
        log.warn("[AdminLottery] 수동 추첨 트리거 — lotteryId={}", lotteryId);
        ManualDrawResponse result = adminLotteryService.manualDraw(lotteryId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * DRAWING 상태 회차 PENDING 복구 (2026-04-29 신규).
     *
     * <p>운영 사고로 회차가 DRAWING 상태에서 멈춘 경우 운영자가 PENDING 으로 되돌려 재추첨을 가능하게 한다.</p>
     */
    @Operation(
            summary = "추첨 회차 강제 복구(DRAWING → PENDING)",
            description = "운영 사고로 회차가 DRAWING 상태에서 멈췄을 때 PENDING 으로 강제 복구한다."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cycles/{lotteryId}/reset")
    public ResponseEntity<ApiResponse<LotterySummary>> resetCycle(
            @Parameter(description = "복구할 회차 PK") @PathVariable Long lotteryId
    ) {
        log.warn("[AdminLottery] 회차 강제 복구 — lotteryId={}", lotteryId);
        LotterySummary result = adminLotteryService.resetDrawingToPending(lotteryId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ──────────────────────────────────────────────
    // 응모자 명단
    // ──────────────────────────────────────────────

    /**
     * 회차별 응모자 페이징 조회.
     *
     * <p>각 응모자 항목은 닉네임·이메일을 함께 노출하므로 운영자가 누구인지 확인 가능하다.
     * status 파라미터로 PENDING/WON/LOST 필터링 가능 — 보통 운영 시 "당첨자 명단" 만 따로 보고 싶다.</p>
     *
     * @param lotteryId 회차 PK
     * @param status    nullable — PENDING/WON/LOST
     * @param pageable  페이징 (기본 enrolledAt DESC, size=50)
     * @return 응모자 항목 페이지
     */
    @Operation(
            summary = "회차별 응모자 명단 조회",
            description = "특정 회차의 응모자(또는 당첨자) 명단을 페이징 조회한다. status·keyword 로 필터링 가능."
    )
    @GetMapping("/cycles/{lotteryId}/entries")
    public ResponseEntity<ApiResponse<Page<LotteryEntryItem>>> getEntries(
            @Parameter(description = "회차 PK") @PathVariable Long lotteryId,
            @Parameter(description = "응모 결과 필터 (PENDING/WON/LOST, 생략 시 전체)")
            @RequestParam(required = false) MovieTicketEntryStatus status,
            @Parameter(description = "닉네임/이메일/userId 부분 일치 검색 (대소문자 무시)")
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        log.debug("[AdminLottery] 응모자 명단 조회 — lotteryId={}, status={}, keyword={}, page={}",
                lotteryId, status, keyword, pageable.getPageNumber());
        Page<LotteryEntryItem> result = adminLotteryService.getEntries(lotteryId, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 회차별 응모자 CSV export (2026-04-29 신규).
     *
     * <p>관리자가 당첨자 명단을 외부 채널(이메일/알림톡)로 발송하기 위한 데이터 export.
     * 보통 status=WON 으로 호출해 당첨자만 다운로드한다.</p>
     */
    @Operation(
            summary = "회차별 응모자 CSV 다운로드",
            description = "응모자(또는 당첨자) 명단을 CSV 형식으로 다운로드한다. 외부 채널 발송용."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/cycles/{lotteryId}/entries/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportEntries(
            @Parameter(description = "회차 PK") @PathVariable Long lotteryId,
            @Parameter(description = "응모 결과 필터 (PENDING/WON/LOST, 생략 시 전체)")
            @RequestParam(required = false) MovieTicketEntryStatus status
    ) {
        log.info("[AdminLottery] 응모자 CSV export — lotteryId={}, status={}", lotteryId, status);
        List<LotteryEntryItem> all = adminLotteryService.getAllEntriesForExport(lotteryId, status);

        /* CSV 직렬화 — Excel 호환을 위해 UTF-8 BOM 부착 */
        StringBuilder sb = new StringBuilder();
        sb.append('﻿');
        sb.append("entryId,userId,nickname,email,status,userItemId,enrolledAt\n");
        for (LotteryEntryItem e : all) {
            sb.append(e.entryId()).append(',')
              .append(csvEscape(e.userId())).append(',')
              .append(csvEscape(e.nickname())).append(',')
              .append(csvEscape(e.email())).append(',')
              .append(csvEscape(e.status() != null ? e.status().name() : "")).append(',')
              .append(e.userItemId() != null ? e.userItemId().toString() : "").append(',')
              .append(csvEscape(e.enrolledAt() != null ? e.enrolledAt().toString() : ""))
              .append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);

        /* 파일명: lottery-entries-{lotteryId}-{status}.csv (status 가 null 이면 ALL) */
        String statusLabel = status != null ? status.name() : "ALL";
        String filename = "lottery-entries-" + lotteryId + "-" + statusLabel + ".csv";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /** CSV 한 셀 이스케이프 — null/콤마/따옴표/개행 대응. */
    private String csvEscape(String value) {
        if (value == null) return "";
        boolean needQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
