package com.monglepick.monglepickbackend.domain.reward.controller;

import com.monglepick.monglepickbackend.domain.reward.dto.MovieTicketLotteryDto.EntryPageResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.MovieTicketLotteryDto.EntryResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.MovieTicketEntry;
import com.monglepick.monglepickbackend.domain.reward.service.MovieTicketLotteryService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 영화 티켓 추첨 응모 현황 컨트롤러 (2026-04-14 신규, 후속 #3 MVP).
 *
 * <p>유저가 자신의 응모 현황을 조회한다. 응모권 자체의 사용은
 * {@link UserItemController#useItem} 경로(`POST /api/v1/users/me/items/{id}/use`) 에서
 * 자동으로 entry 가 발급되므로, 별도의 "응모 EP" 는 신설하지 않는다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET {@code /api/v1/users/me/lottery/entries} — 페이징 응모 현황</li>
 * </ul>
 *
 * <h3>관리자 추첨 트리거</h3>
 * <p>운영자 수동 추첨은 별도 관리자 컨트롤러에서 노출 (현재 MVP 미구현 — 필요 시
 * {@link com.monglepick.monglepickbackend.domain.reward.service.MovieTicketLotteryBatch#manualDraw}
 * 호출). 자동 배치는 매월 1일 0시.</p>
 */
@Tag(name = "영화 티켓 응모", description = "응모권 사용 현황 및 추첨 결과 조회")
@RestController
@RequestMapping("/api/v1/users/me/lottery")
@Slf4j
@RequiredArgsConstructor
public class MovieTicketLotteryController extends BaseController {

    private final MovieTicketLotteryService lotteryService;

    /**
     * 내 응모 현황 페이징 조회.
     *
     * <p>회차별로 entry 가 1건 이상 있을 수 있으며, 각 entry 의 status (PENDING/WON/LOST) 와
     * 회차의 추첨 시각(drawnAt) 을 함께 반환한다. 정렬 기본 enrolledAt DESC.</p>
     *
     * @param principal JWT principal
     * @param page      0-indexed
     * @param size      페이지 크기
     * @return 응모 현황 페이지 응답
     */
    @GetMapping("/entries")
    @Operation(summary = "내 영화 티켓 응모 현황",
            description = "응모권 사용 시 자동 발급된 entry 를 회차 정보와 함께 페이징 조회.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<EntryPageResponse> getMyEntries(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String userId = resolveUserId(principal);
        Page<MovieTicketEntry> result = lotteryService.getMyEntries(
                userId,
                PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        EntryPageResponse response = new EntryPageResponse(
                result.getContent().stream().map(EntryResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
        return ResponseEntity.ok(response);
    }
}
