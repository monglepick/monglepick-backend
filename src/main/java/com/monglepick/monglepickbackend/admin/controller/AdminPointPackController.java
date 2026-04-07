package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.PackResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.UpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminPointPackService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 포인트팩(PointPackPrice) 마스터 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 포인트팩" 메뉴의 6개 엔드포인트를 제공한다.
 * 포인트팩은 결제 검증의 핵심 가격표이며, 클라이언트 변조 방지를 위해 본 마스터 데이터를
 * 정확 매칭으로 검증한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/point-packs            — 목록 (페이징)</li>
 *   <li>GET    /api/v1/admin/point-packs/{id}       — 단건 조회</li>
 *   <li>POST   /api/v1/admin/point-packs            — 신규 등록</li>
 *   <li>PUT    /api/v1/admin/point-packs/{id}       — 메타 수정</li>
 *   <li>PATCH  /api/v1/admin/point-packs/{id}/active — 활성 토글</li>
 *   <li>DELETE /api/v1/admin/point-packs/{id}       — 삭제</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 *
 * <h3>운영 주의사항</h3>
 * <p>가격(price) 변경은 결제 안정성에 영향을 준다. 운영 중인 팩의 가격을 변경하면
 * 진행 중인 주문이나 결제 검증에 영향을 줄 수 있으므로, 폐지된 팩은 hard delete보다
 * isActive=false 토글을 권장한다.</p>
 */
@Tag(name = "관리자 — 포인트팩", description = "PointPackPrice 마스터 등록/수정/활성 토글")
@RestController
@RequestMapping("/api/v1/admin/point-packs")
@RequiredArgsConstructor
@Slf4j
public class AdminPointPackController {

    private final AdminPointPackService adminPointPackService;

    /** 포인트팩 목록 조회 (페이징) */
    @Operation(
            summary = "포인트팩 목록 조회",
            description = "sortOrder ASC + price ASC 정렬"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PackResponse>>> getPacks(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page, size,
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("price"))
        );
        Page<PackResponse> result = adminPointPackService.getPacks(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 포인트팩 단건 조회 */
    @Operation(summary = "포인트팩 단건 조회", description = "pack_id로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackResponse>> getPack(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminPointPackService.getPack(id)));
    }

    /** 포인트팩 신규 등록 */
    @Operation(
            summary = "포인트팩 신규 등록",
            description = "(price, pointsAmount) 조합 UNIQUE — 중복 시 409"
    )
    @PostMapping
    public ResponseEntity<ApiResponse<PackResponse>> createPack(
            @Valid @RequestBody CreateRequest request
    ) {
        log.info("[관리자] 포인트팩 등록 요청 — name={}, price={}, points={}",
                request.packName(), request.price(), request.pointsAmount());
        PackResponse created = adminPointPackService.createPack(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 포인트팩 메타 수정 */
    @Operation(
            summary = "포인트팩 수정",
            description = "packName/price/pointsAmount/isActive/sortOrder 일괄 수정"
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PackResponse>> updatePack(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        log.info("[관리자] 포인트팩 수정 요청 — packId={}", id);
        return ResponseEntity.ok(ApiResponse.ok(adminPointPackService.updatePack(id, request)));
    }

    /** 활성 토글 */
    @Operation(
            summary = "포인트팩 활성/비활성 토글",
            description = "is_active 변경. 폐지된 팩은 hard delete보다 본 EP 사용 권장."
    )
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<PackResponse>> updateActive(
            @PathVariable Long id,
            @RequestBody UpdateActiveRequest request
    ) {
        log.info("[관리자] 포인트팩 활성 토글 요청 — packId={}, isActive={}", id, request.isActive());
        return ResponseEntity.ok(ApiResponse.ok(adminPointPackService.updateActive(id, request)));
    }

    /** 포인트팩 삭제 */
    @Operation(
            summary = "포인트팩 삭제 (hard delete)",
            description = "결제 흐름에 영향 주의. 가능한 PATCH /active로 비활성화 권장."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePack(@PathVariable Long id) {
        log.warn("[관리자] 포인트팩 삭제 요청 — packId={}", id);
        adminPointPackService.deletePack(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
