package com.monglepick.monglepickbackend.domain.payment.controller;

import com.monglepick.monglepickbackend.domain.payment.entity.PointPackPrice;
import com.monglepick.monglepickbackend.domain.payment.repository.PointPackPriceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포인트팩(PointPackPrice) 사용자 조회 전용 API 컨트롤러.
 *
 * <p>관리자 페이지({@code /api/v1/admin/point-packs})에서 수정된 마스터 데이터가
 * 유저 결제 페이지에 실시간으로 반영되도록, 활성 포인트팩 목록만 조회하는 공개 엔드포인트이다.
 * 기존에는 클라이언트가 포인트팩 구성을 하드코딩으로 렌더링하여 관리자 수정이 반영되지 않는
 * 버그가 있었다 — 본 컨트롤러 도입으로 "단일 진실 원본 = point_pack_prices 테이블" 원칙을 확립한다.</p>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET /api/v1/point-packs — 활성 포인트팩 목록 (sortOrder ASC, 비로그인 허용)</li>
 * </ul>
 *
 * <h3>관리자 EP와의 차이</h3>
 * <ul>
 *   <li>활성 상태(is_active=true) 팩만 반환 — 비활성은 숨김</li>
 *   <li>페이징 없음 — 팩 개수가 소수(수개~수십개)임을 전제</li>
 *   <li>인증 불필요 — 결제 전 비로그인 사용자도 가격 확인 가능 (구독 플랜과 동일 정책)</li>
 * </ul>
 *
 * @see com.monglepick.monglepickbackend.admin.controller.AdminPointPackController 관리자 CRUD
 * @see PointPackPriceRepository#findByIsActiveTrueOrderBySortOrderAsc()
 */
@Tag(name = "포인트팩", description = "사용자용 포인트팩 조회 (공개)")
@RestController
@RequestMapping("/api/v1/point-packs")
@RequiredArgsConstructor
@Slf4j
public class PointPackController {

    /** 포인트팩 가격 마스터 리포지토리 — 단순 read-only 조회이므로 별도 Service 없이 직접 주입. */
    private final PointPackPriceRepository pointPackPriceRepository;

    /**
     * 활성 포인트팩 목록을 조회한다.
     *
     * <p>클라이언트의 "포인트 충전" 섹션에서 호출. sortOrder 오름차순으로 정렬된
     * 활성 팩만 반환하여 관리자가 토글한 비활성 상품이 유저 화면에 노출되지 않도록 한다.</p>
     *
     * @return 200 OK + 활성 포인트팩 목록
     */
    @Operation(
            summary = "활성 포인트팩 목록 조회",
            description = "관리자 페이지에서 관리하는 point_pack_prices 테이블의 활성 팩 목록을 sortOrder 오름차순으로 반환 (비로그인 허용)"
    )
    @ApiResponse(responseCode = "200", description = "포인트팩 목록 조회 성공")
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<List<PointPackResponse>> getActivePacks() {
        log.debug("활성 포인트팩 목록 조회 API 호출");

        /* is_active=true 인 팩만 sortOrder 순으로 조회 — Repository 기존 메서드 재사용 */
        List<PointPackResponse> packs = pointPackPriceRepository
                .findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(PointPackResponse::from)
                .toList();

        return ResponseEntity.ok(packs);
    }

    /**
     * 사용자 화면 노출용 포인트팩 DTO.
     *
     * <p>관리자 응답({@code AdminPointPackDto.PackResponse})과 달리 감사 필드(createdAt/updatedAt)와
     * 내부 플래그(isActive — 이미 필터링됨)를 제외해 외부 노출 필드를 최소화한다.</p>
     *
     * <h4>필드</h4>
     * <ul>
     *   <li>{@code packId}       — 포인트팩 PK (클라이언트가 결제 요청에는 사용하지 않지만 React key 용도)</li>
     *   <li>{@code packName}     — 상품명 (관리자가 수정 가능)</li>
     *   <li>{@code price}        — 결제 금액 (KRW)</li>
     *   <li>{@code pointsAmount} — 지급 포인트</li>
     *   <li>{@code sortOrder}    — 정렬 순서 (이미 정렬된 상태로 내려가지만 클라이언트 안정 정렬용)</li>
     * </ul>
     */
    public record PointPackResponse(
            Long packId,
            String packName,
            Integer price,
            Integer pointsAmount,
            Integer sortOrder
    ) {
        /** 엔티티 → 응답 DTO 매핑. */
        public static PointPackResponse from(PointPackPrice entity) {
            return new PointPackResponse(
                    entity.getPackId(),
                    entity.getPackName(),
                    entity.getPrice(),
                    entity.getPointsAmount(),
                    entity.getSortOrder() != null ? entity.getSortOrder() : 0
            );
        }
    }
}
