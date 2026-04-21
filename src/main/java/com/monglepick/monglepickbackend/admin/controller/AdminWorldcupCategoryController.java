package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCategoryDto.CategoryResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCategoryDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCategoryDto.UpdateRequest;
import com.monglepick.monglepickbackend.admin.service.AdminWorldcupCategoryService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 월드컵 후보 카테고리 마스터 관리 API.
 */
@Tag(name = "관리자 — 월드컵 카테고리", description = "WorldcupCategory 마스터 데이터 등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/admin/worldcup-categories")
@RequiredArgsConstructor
@Slf4j
public class AdminWorldcupCategoryController {

    private final AdminWorldcupCategoryService adminWorldcupCategoryService;

    @Operation(summary = "월드컵 카테고리 목록 조회 (페이징)", description = "createdAt DESC 정렬")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getCategories(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(adminWorldcupCategoryService.getCategories(pageable)));
    }

    @Operation(summary = "월드컵 카테고리 전체 목록", description = "드롭다운/필터용 전체 카테고리")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.ok(adminWorldcupCategoryService.getAllCategories()));
    }

    @Operation(summary = "월드컵 카테고리 단건 조회", description = "category_id로 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminWorldcupCategoryService.getCategory(id)));
    }

    @Operation(summary = "월드컵 카테고리 신규 등록", description = "category_code UNIQUE — 중복 시 409, description/adminNote 저장 가능")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateRequest request
    ) {
        log.info("[관리자] 월드컵 카테고리 등록 요청 — code={}", request.categoryCode());
        CategoryResponse created = adminWorldcupCategoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @Operation(summary = "월드컵 카테고리 수정", description = "카테고리 이름/설명/adminNote 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        log.info("[관리자] 월드컵 카테고리 수정 요청 — id={}", id);
        return ResponseEntity.ok(ApiResponse.ok(adminWorldcupCategoryService.updateCategory(id, request)));
    }

    @Operation(summary = "월드컵 카테고리 삭제", description = "후보 영화가 연결된 카테고리는 삭제할 수 없습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        log.warn("[관리자] 월드컵 카테고리 삭제 요청 — id={}", id);
        adminWorldcupCategoryService.deleteCategory(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
