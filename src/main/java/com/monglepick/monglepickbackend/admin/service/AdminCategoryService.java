package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CategoryChildResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CategoryResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CreateCategoryRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.CreateChildRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.UpdateCategoryRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminCategoryDto.UpdateChildRequest;
import com.monglepick.monglepickbackend.admin.repository.AdminCategoryChildRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminCategoryRepository;
import com.monglepick.monglepickbackend.domain.community.entity.Category;
import com.monglepick.monglepickbackend.domain.community.entity.CategoryChild;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 게시글 카테고리(Category/CategoryChild) 마스터 관리 서비스.
 *
 * <p>커뮤니티 게시판의 상위/하위 카테고리 마스터 데이터를 관리한다.
 * 상위 카테고리 삭제 시 하위 카테고리 자동 정리 (cascade-like 처리).</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>상위 카테고리 CRUD (목록/단건/등록/수정/삭제)</li>
 *   <li>하위 카테고리 CRUD (목록은 상위 단건 응답에 포함)</li>
 *   <li>상위 삭제 시 하위 일괄 정리</li>
 * </ol>
 *
 * <h3>제약 검증</h3>
 * <ul>
 *   <li>Category.upCategory UNIQUE — 사전 검증</li>
 *   <li>CategoryChild (categoryId, categoryChild) 복합 UNIQUE — 사전 검증</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCategoryService {

    private final AdminCategoryRepository categoryRepository;
    private final AdminCategoryChildRepository childRepository;

    // ─────────────────────────────────────────────
    // 상위 카테고리
    // ─────────────────────────────────────────────

    public Page<CategoryResponse> getCategories(Pageable pageable) {
        return categoryRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(c -> toResponse(c, false));
    }

    /** 단건 + 하위 목록 포함 */
    public CategoryResponse getCategoryDetail(Long id) {
        Category category = findCategoryByIdOrThrow(id);
        return toResponse(category, true);
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByUpCategory(request.upCategory())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        Category entity = Category.builder()
                .upCategory(request.upCategory())
                .build();

        Category saved = categoryRepository.save(entity);
        log.info("[관리자] 상위 카테고리 등록 — id={}, name={}",
                saved.getCategoryId(), saved.getUpCategory());
        return toResponse(saved, false);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category entity = findCategoryByIdOrThrow(id);

        // 다른 카테고리에서 같은 이름이 사용 중이면 거부
        if (!entity.getUpCategory().equals(request.upCategory())
                && categoryRepository.existsByUpCategory(request.upCategory())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        entity.updateName(request.upCategory());
        log.info("[관리자] 상위 카테고리 수정 — id={}, name={}", id, entity.getUpCategory());
        return toResponse(entity, false);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category entity = findCategoryByIdOrThrow(id);
        long childCount = childRepository.countByCategoryId(id);
        if (childCount > 0) {
            log.warn("[관리자] 상위 카테고리 삭제 — 하위 {}개 함께 삭제됨 (id={})", childCount, id);
            childRepository.deleteByCategoryId(id);
        }
        categoryRepository.delete(entity);
        log.info("[관리자] 상위 카테고리 삭제 — id={}, name={}", id, entity.getUpCategory());
    }

    // ─────────────────────────────────────────────
    // 하위 카테고리
    // ─────────────────────────────────────────────

    public List<CategoryChildResponse> getChildren(Long categoryId) {
        // 상위 존재 검증
        findCategoryByIdOrThrow(categoryId);
        return childRepository.findByCategoryIdOrderByCategoryChildAsc(categoryId)
                .stream()
                .map(this::toChildResponse)
                .toList();
    }

    @Transactional
    public CategoryChildResponse createChild(CreateChildRequest request) {
        // 상위 존재 검증
        findCategoryByIdOrThrow(request.categoryId());

        if (childRepository.existsByCategoryIdAndCategoryChild(
                request.categoryId(), request.categoryChild())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_CHILD);
        }

        CategoryChild entity = CategoryChild.builder()
                .categoryId(request.categoryId())
                .categoryChild(request.categoryChild())
                .build();

        CategoryChild saved = childRepository.save(entity);
        log.info("[관리자] 하위 카테고리 등록 — id={}, parent={}, name={}",
                saved.getCategoryChildId(), saved.getCategoryId(), saved.getCategoryChild());
        return toChildResponse(saved);
    }

    @Transactional
    public CategoryChildResponse updateChild(Long id, UpdateChildRequest request) {
        CategoryChild entity = findChildByIdOrThrow(id);

        if (!entity.getCategoryChild().equals(request.categoryChild())
                && childRepository.existsByCategoryIdAndCategoryChild(
                entity.getCategoryId(), request.categoryChild())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_CHILD);
        }

        entity.updateName(request.categoryChild());
        log.info("[관리자] 하위 카테고리 수정 — id={}, name={}", id, entity.getCategoryChild());
        return toChildResponse(entity);
    }

    @Transactional
    public void deleteChild(Long id) {
        CategoryChild entity = findChildByIdOrThrow(id);
        childRepository.delete(entity);
        log.info("[관리자] 하위 카테고리 삭제 — id={}, name={}", id, entity.getCategoryChild());
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    private Category findCategoryByIdOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "상위 카테고리 ID " + id + "를 찾을 수 없습니다"));
    }

    private CategoryChild findChildByIdOrThrow(Long id) {
        return childRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "하위 카테고리 ID " + id + "를 찾을 수 없습니다"));
    }

    private CategoryResponse toResponse(Category entity, boolean includeChildren) {
        List<CategoryChildResponse> children = null;
        if (includeChildren) {
            children = childRepository
                    .findByCategoryIdOrderByCategoryChildAsc(entity.getCategoryId())
                    .stream()
                    .map(this::toChildResponse)
                    .toList();
        }
        return new CategoryResponse(
                entity.getCategoryId(),
                entity.getUpCategory(),
                children,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CategoryChildResponse toChildResponse(CategoryChild entity) {
        return new CategoryChildResponse(
                entity.getCategoryChildId(),
                entity.getCategoryId(),
                entity.getCategoryChild(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
