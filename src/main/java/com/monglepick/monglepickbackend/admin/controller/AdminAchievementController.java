package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.AchievementResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.CreateAchievementRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.UpdateAchievementRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminAchievementDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.service.AdminAchievementService;
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
 * 관리자 업적(AchievementType) 마스터 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "업적 관리" 메뉴의 5개 엔드포인트를 제공한다.
 * AchievementType 마스터 데이터의 등록·수정·활성화 토글을 담당한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/achievements         — 업적 마스터 목록 (페이징, 활성/비활성 모두)</li>
 *   <li>GET    /api/v1/admin/achievements/{id}    — 업적 마스터 단건 조회</li>
 *   <li>POST   /api/v1/admin/achievements         — 업적 마스터 신규 등록</li>
 *   <li>PUT    /api/v1/admin/achievements/{id}    — 업적 마스터 수정 (코드 제외)</li>
 *   <li>PATCH  /api/v1/admin/achievements/{id}/active — 활성/비활성 토글</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다. SecurityConfig에서 일괄 처리.</p>
 *
 * <h3>비활성화 정책</h3>
 * <p>물리 삭제(DELETE) EP는 의도적으로 제공하지 않는다. 사용자 달성 기록
 * ({@code user_achievement} 테이블)이 FK로 마스터를 참조하므로 삭제 시 정합성이 깨진다.
 * 더 이상 사용하지 않는 업적은 PATCH로 비활성화만 한다.</p>
 */
@Tag(name = "관리자 — 업적 관리", description = "AchievementType 마스터 등록/수정/활성화 토글")
@RestController
@RequestMapping("/api/v1/admin/achievements")
@RequiredArgsConstructor
@Slf4j
public class AdminAchievementController {

    /** 업적 마스터 관리 서비스 */
    private final AdminAchievementService adminAchievementService;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 업적 마스터 목록 조회 (페이징, 활성/비활성 모두).
     *
     * @param page 페이지 번호 (0부터 시작, 기본 0)
     * @param size 페이지 크기 (기본 20)
     * @return 200 OK + 페이징된 업적 목록
     */
    @Operation(
            summary = "업적 마스터 목록 조회",
            description = "활성/비활성 상태에 관계없이 모든 업적 유형을 페이징하여 조회. createdAt DESC 정렬."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AchievementResponse>>> getAchievements(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AchievementResponse> result = adminAchievementService.getAchievements(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 업적 마스터 단건 조회.
     *
     * @param id 업적 유형 ID
     * @return 200 OK + 업적 응답 DTO
     */
    @Operation(
            summary = "업적 마스터 단건 조회",
            description = "achievement_type_id로 단건 조회"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AchievementResponse>> getAchievement(
            @Parameter(description = "업적 유형 ID")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminAchievementService.getAchievement(id)));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 업적 마스터 신규 등록.
     *
     * <p>achievement_code는 UNIQUE — 중복 시 409.</p>
     *
     * @param request 신규 업적 등록 요청
     * @return 201 Created + 생성된 업적 DTO
     */
    @Operation(
            summary = "업적 마스터 등록",
            description = "신규 업적 유형을 등록. achievement_code는 UNIQUE이므로 중복 시 409 응답"
    )
    @PostMapping
    public ResponseEntity<ApiResponse<AchievementResponse>> createAchievement(
            @Valid @RequestBody CreateAchievementRequest request
    ) {
        log.info("[관리자] 업적 마스터 등록 요청 — code={}, name={}",
                request.achievementCode(), request.achievementName());
        AchievementResponse created = adminAchievementService.createAchievement(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /**
     * 업적 마스터 수정 (코드 제외).
     *
     * <p>achievement_code는 사용자 달성 기록과 연결된 식별자이므로 변경 불가.
     * 표시명/설명/조건/보상/아이콘/카테고리만 수정 가능하다.</p>
     *
     * @param id      수정 대상 ID
     * @param request 수정 요청
     * @return 200 OK + 수정된 업적 DTO
     */
    @Operation(
            summary = "업적 마스터 수정",
            description = "achievement_code 제외 모든 필드 수정 가능. 코드는 사용자 달성 기록 연결 키이므로 불변."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AchievementResponse>> updateAchievement(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAchievementRequest request
    ) {
        log.info("[관리자] 업적 마스터 수정 요청 — id={}", id);
        AchievementResponse updated = adminAchievementService.updateAchievement(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * 업적 마스터 활성/비활성 토글.
     *
     * <p>비활성화는 hard delete가 아니다. 사용자 달성 기록은 보존되며,
     * 신규 달성 판정만 차단된다.</p>
     *
     * @param id      대상 ID
     * @param request 활성 여부 토글
     * @return 200 OK + 갱신된 DTO
     */
    @Operation(
            summary = "업적 활성/비활성 토글",
            description = "is_active 값을 변경. 비활성화는 신규 달성 차단이며 기존 달성 기록은 보존."
    )
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<AchievementResponse>> updateActiveStatus(
            @PathVariable Long id,
            @RequestBody UpdateActiveRequest request
    ) {
        log.info("[관리자] 업적 활성 상태 변경 요청 — id={}, isActive={}", id, request.isActive());
        AchievementResponse updated = adminAchievementService.updateActiveStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * (의도적으로 미구현) DELETE 매핑 — 업적 마스터 hard delete는 지원하지 않는다.
     *
     * <p>{@code user_achievement} 테이블이 FK로 이 마스터를 참조하므로 삭제 시
     * 정합성이 깨진다. 더 이상 사용하지 않는 업적은 PATCH /active 로 비활성화한다.</p>
     */
    @Operation(
            summary = "(미지원) 업적 마스터 삭제",
            description = "FK 정합성 보호를 위해 hard delete는 지원하지 않습니다. PATCH /active로 비활성화하세요."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteAchievement(@PathVariable Long id) {
        log.warn("[관리자] 업적 마스터 hard delete 시도 — id={} (지원되지 않음)", id);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.ok("업적 마스터는 비활성화로만 관리합니다. PATCH /active 사용"));
    }
}
