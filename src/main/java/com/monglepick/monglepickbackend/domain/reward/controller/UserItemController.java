package com.monglepick.monglepickbackend.domain.reward.controller;

import com.monglepick.monglepickbackend.domain.reward.dto.UserItemDto.UserItemPageResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.UserItemDto.UserItemResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.UserItemDto.UserItemSummaryResponse;
import com.monglepick.monglepickbackend.domain.reward.service.UserItemService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 사용자 보유 아이템(UserItem) REST 컨트롤러 (2026-04-14 신규, C 방향).
 *
 * <p>"내 아이템" 페이지 및 프로필 착용 아이템 표시용 엔드포인트 5종.
 * 모두 JWT 인증 필요 (Principal.getName()이 userId가 된다).</p>
 *
 * <h3>엔드포인트</h3>
 * <table border="1">
 *   <tr><th>메서드</th><th>경로</th><th>설명</th></tr>
 *   <tr><td>GET</td><td>/api/v1/users/me/items</td><td>페이징 목록 (카테고리 필터)</td></tr>
 *   <tr><td>GET</td><td>/api/v1/users/me/items/summary</td><td>카테고리별 개수 + 착용 정보</td></tr>
 *   <tr><td>GET</td><td>/api/v1/users/me/items/equipped</td><td>착용 중인 아바타/배지만</td></tr>
 *   <tr><td>POST</td><td>/api/v1/users/me/items/{id}/equip</td><td>착용 (아바타/배지)</td></tr>
 *   <tr><td>POST</td><td>/api/v1/users/me/items/{id}/unequip</td><td>착용 해제</td></tr>
 *   <tr><td>POST</td><td>/api/v1/users/me/items/{id}/use</td><td>1회 사용 (힌트/응모권)</td></tr>
 * </table>
 *
 * <h3>권한</h3>
 * <p>모든 경로가 본인(userId) 소유 아이템에만 접근 가능. 타인 소유 UserItem 조작은
 * 서비스 레이어에서 404(USER_ITEM_NOT_FOUND)로 통일 반환하여 존재 여부 누수 방지.</p>
 */
@Tag(name = "보유 아이템", description = "포인트로 교환한 아이템 인벤토리 조회·착용·사용")
@RestController
@RequestMapping("/api/v1/users/me/items")
@Slf4j
@RequiredArgsConstructor
public class UserItemController extends BaseController {

    /** 보유 아이템 서비스 */
    private final UserItemService userItemService;

    /**
     * 보유 아이템 목록 — 페이징 + 카테고리 필터.
     *
     * @param principal JWT principal (userId 소스)
     * @param category  필터 카테고리 (coupon/avatar/badge/apply/hint, 선택)
     * @param page      0-indexed 페이지 번호 (기본 0)
     * @param size      페이지 크기 (기본 20)
     * @return 페이지 응답 (content + 페이징 메타)
     */
    @GetMapping
    @Operation(summary = "내 보유 아이템 목록", description = "카테고리 필터와 페이징을 지원한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserItemPageResponse> getMyItems(
            Principal principal,
            @Parameter(description = "카테고리 필터 — coupon/avatar/badge/apply/hint")
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String userId = resolveUserId(principal);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        UserItemPageResponse response = userItemService.getMyItems(userId, category, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 보유 아이템 요약 — 카테고리별 개수 + 착용 아이템.
     *
     * @param principal JWT principal
     * @return 요약 응답
     */
    @GetMapping("/summary")
    @Operation(summary = "내 보유 아이템 요약",
            description = "카테고리별 ACTIVE+EQUIPPED 개수와 현재 착용 중인 아바타·배지를 반환한다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserItemSummaryResponse> getSummary(Principal principal) {
        String userId = resolveUserId(principal);
        return ResponseEntity.ok(userItemService.getSummary(userId));
    }

    /**
     * 착용 중인 아바타/배지만 반환 — 프로필 렌더링 경량 API.
     *
     * <p>리스트 인덱스 순서는 [0]=아바타, [1]=배지. 해당 카테고리 미착용 시 null 포함.</p>
     *
     * @param principal JWT principal
     * @return 2-원소 리스트 (avatar, badge)
     */
    @GetMapping("/equipped")
    @Operation(summary = "착용 아이템 조회 (프로필 렌더링용)",
            description = "착용 중인 아바타와 배지 각각 1개씩, 총 2원소 배열을 반환한다 (없으면 null).")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<UserItemResponse>> getEquipped(Principal principal) {
        String userId = resolveUserId(principal);
        return ResponseEntity.ok(userItemService.getEquipped(userId));
    }

    /**
     * 아이템 착용 — 아바타/배지만 가능.
     *
     * <p>동일 카테고리 기존 착용 아이템은 자동 해제된다.</p>
     *
     * @param principal JWT principal
     * @param userItemId 착용 대상 user_items PK
     * @return 착용된 아이템 응답
     */
    @PostMapping("/{userItemId}/equip")
    @Operation(summary = "아이템 착용",
            description = "아바타/배지 카테고리만 허용. 같은 카테고리 기존 착용은 자동 해제된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "착용 완료"),
            @ApiResponse(responseCode = "400", description = "착용 불가 카테고리 또는 만료 아이템"),
            @ApiResponse(responseCode = "404", description = "아이템 미존재 또는 타인 소유")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserItemResponse> equipItem(
            Principal principal,
            @PathVariable Long userItemId
    ) {
        String userId = resolveUserId(principal);
        return ResponseEntity.ok(userItemService.equipItem(userId, userItemId));
    }

    /**
     * 아이템 착용 해제 — EQUIPPED → ACTIVE.
     *
     * @param principal JWT principal
     * @param userItemId 해제 대상 PK
     * @return 해제 후 응답
     */
    @PostMapping("/{userItemId}/unequip")
    @Operation(summary = "아이템 착용 해제")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserItemResponse> unequipItem(
            Principal principal,
            @PathVariable Long userItemId
    ) {
        String userId = resolveUserId(principal);
        return ResponseEntity.ok(userItemService.unequipItem(userId, userItemId));
    }

    /**
     * 아이템 1회 사용 — 힌트/응모권 등 소모성.
     *
     * <p>remainingQuantity가 1 감소하며 0이 되면 status=USED로 자동 전환.</p>
     *
     * @param principal JWT principal
     * @param userItemId 사용할 아이템 PK
     * @return 사용 후 응답
     */
    @PostMapping("/{userItemId}/use")
    @Operation(summary = "아이템 1회 사용",
            description = "힌트·응모권 등 소모성 아이템의 잔여 수량을 1 차감한다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserItemResponse> useItem(
            Principal principal,
            @PathVariable Long userItemId
    ) {
        String userId = resolveUserId(principal);
        return ResponseEntity.ok(userItemService.useItem(userId, userItemId));
    }
}
