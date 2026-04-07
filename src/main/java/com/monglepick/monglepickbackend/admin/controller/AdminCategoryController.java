package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CategoryChildResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CategoryResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CreateCategoryRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CreateChildRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.UpdateCategoryRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.UpdateChildRequest;
import com.monglepick.monglepickbackend.admin.service.AdminCategoryService;
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
 * 관리자 게시글 카테고리(Category/CategoryChild) 관리 API 컨트롤러.
 *
 * <p>관리자 페이지 "운영 도구 → 카테고리" 메뉴의 9개 엔드포인트를 제공한다.
 * 상위 카테고리(Category) 5 EP + 하위 카테고리(CategoryChild) 4 EP.</p>
 *
 * <h3>상위 카테고리 EP</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/categories                    — 목록 페이징</li>
 *   <li>GET    /api/v1/admin/categories/{id}               — 단건 + 하위 목록 포함</li>
 *   <li>POST   /api/v1/admin/categories                    — 신규 등록 (UNIQUE)</li>
 *   <li>PUT    /api/v1/admin/categories/{id}               — 이름 수정</li>
 *   <li>DELETE /api/v1/admin/categories/{id}               — 삭제 (하위 자동 정리)</li>
 * </ul>
 *
 * <h3>하위 카테고리 EP</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/categories/{id}/children      — 하위 목록</li>
 *   <li>POST   /api/v1/admin/categories/children           — 신규 등록 (categoryId 포함)</li>
 *   <li>PUT    /api/v1/admin/categories/children/{childId} — 이름 수정</li>
 *   <li>DELETE /api/v1/admin/categories/children/{childId} — 삭제</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 카테고리", description = "Category/CategoryChild 마스터 등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Slf4j
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    // ─────────────────────────────────────────────
    // 상위 카테고리
    // ─────────────────────────────────────────────

    @Operation(summary = "상위 카테고리 목록 조회", description = "createdAt DESC 정렬")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getCategories(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(adminCategoryService.getCategories(pageable)));
    }

    @Operation(
            summary = "상위 카테고리 단건 조회",
            description = "단건 정보 + 하위 카테고리 목록 포함"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminCategoryService.getCategoryDetail(id)));
    }

    @Operation(
            summary = "상위 카테고리 신규 등록",
            description = "up_category UNIQUE — 중복 시 409"
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        log.info("[관리자] 상위 카테고리 등록 요청 — name={}", request.upCategory());
        CategoryResponse created = adminCategoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @Operation(summary = "상위 카테고리 수정", description = "이름만 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        log.info("[관리자] 상위 카테고리 수정 요청 — id={}, newName={}", id, request.upCategory());
        return ResponseEntity.ok(ApiResponse.ok(adminCategoryService.updateCategory(id, request)));
    }

    @Operation(
            summary = "상위 카테고리 삭제",
            description = "하위 카테고리도 함께 삭제됩니다."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        log.warn("[관리자] 상위 카테고리 삭제 요청 — id={}", id);
        adminCategoryService.deleteCategory(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─────────────────────────────────────────────
    // 하위 카테고리
    // ─────────────────────────────────────────────

    @Operation(summary = "하위 카테고리 목록 조회", description = "특정 상위 카테고리의 하위 목록")
    @GetMapping("/{id}/children")
    public ResponseEntity<ApiResponse<List<CategoryChildResponse>>> getChildren(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminCategoryService.getChildren(id)));
    }

    @Operation(
            summary = "하위 카테고리 신규 등록",
            description = "(categoryId, categoryChild) 복합 UNIQUE — 중복 시 409"
    )
    @PostMapping("/children")
    public ResponseEntity<ApiResponse<CategoryChildResponse>> createChild(
            @Valid @RequestBody CreateChildRequest request
    ) {
        log.info("[관리자] 하위 카테고리 등록 요청 — parentId={}, name={}",
                request.categoryId(), request.categoryChild());
        CategoryChildResponse created = adminCategoryService.createChild(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @Operation(summary = "하위 카테고리 수정", description = "이름만 수정")
    @PutMapping("/children/{childId}")
    public ResponseEntity<ApiResponse<CategoryChildResponse>> updateChild(
            @PathVariable Long childId,
            @Valid @RequestBody UpdateChildRequest request
    ) {
        log.info("[관리자] 하위 카테고리 수정 요청 — childId={}, newName={}",
                childId, request.categoryChild());
        return ResponseEntity.ok(ApiResponse.ok(adminCategoryService.updateChild(childId, request)));
    }

    @Operation(summary = "하위 카테고리 삭제", description = "hard delete")
    @DeleteMapping("/children/{childId}")
    public ResponseEntity<Void> deleteChild(@PathVariable Long childId) {
        log.warn("[관리자] 하위 카테고리 삭제 요청 — childId={}", childId);
        adminCategoryService.deleteChild(childId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
