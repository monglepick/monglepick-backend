package com.monglepick.monglepickbackend.domain.content.controller;

import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerResponse;
import com.monglepick.monglepickbackend.admin.service.AdminSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사용자 홈 슬라이드 배너 공개 조회 컨트롤러 (비로그인 허용) — 2026-04-14 신규.
 *
 * <p>관리자 페이지({@code /api/v1/admin/banners}) 에 이미 구축된 배너 CRUD 와는 별개로,
 * 유저 화면(홈)에서 슬라이드 배너를 노출하기 위한 공개(permitAll) GET 엔드포인트를
 * 제공한다. AdminSettingsService 의 비즈니스 로직을 그대로 재사용하며, 추가로 DB/엔티티/
 * 서비스 레이어를 만들지 않는다.</p>
 *
 * <h3>응답 조건</h3>
 * <ul>
 *   <li>{@code is_active = true}</li>
 *   <li>{@code (start_date IS NULL OR start_date <= NOW())}</li>
 *   <li>{@code (end_date   IS NULL OR end_date   >= NOW())}</li>
 *   <li>정렬: {@code sort_order ASC} (낮을수록 먼저 노출)</li>
 * </ul>
 *
 * <p>공지(Notice) 공개 API({@code AppNoticeController}) 와 동일한 패턴으로 작성했다.</p>
 */
@Tag(name = "배너", description = "홈 슬라이드 배너 공개 조회 (비로그인 허용)")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
@Slf4j
public class AppBannerController {

    /** 관리자 설정 서비스 — 기존 Banner CRUD 로직 재사용 (DDL/Mapper 단일 진실 원본 유지) */
    private final AdminSettingsService adminSettingsService;

    /**
     * 현재 노출 중인 배너 목록 조회.
     *
     * @param position 위치 필터 (예: "MAIN"). 생략 시 서비스 레이어에서 "MAIN" 으로 설정.
     * @return 활성 배너 목록 (sort_order ASC)
     */
    @Operation(
            summary = "노출 중 배너 조회",
            description = "현재 시각 기준 is_active=true AND start_date~end_date 범위 내인 "
                    + "배너를 sort_order 오름차순으로 반환한다. 비로그인 접근 허용."
    )
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<List<BannerResponse>> getActiveBanners(
            @Parameter(description = "위치 필터 (예: MAIN, SIDE). 생략 시 MAIN")
            @RequestParam(required = false) String position
    ) {
        log.debug("[AppBannerController] 노출 중 배너 조회 — position={}", position);
        return ResponseEntity.ok(adminSettingsService.getActiveBanners(position));
    }
}
