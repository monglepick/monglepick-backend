package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCategoryDto.CategoryResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCategoryDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCategoryDto.UpdateRequest;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCategory;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCandidateRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCategoryRepository;
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
 * 관리자 월드컵 후보 카테고리 마스터 관리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorldcupCategoryService {

    private final WorldcupCategoryRepository worldcupCategoryRepository;
    private final WorldcupCandidateRepository worldcupCandidateRepository;

    public Page<CategoryResponse> getCategories(Pageable pageable) {
        return worldcupCategoryRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    public List<CategoryResponse> getAllCategories() {
        return worldcupCategoryRepository.findAllByOrderByCategoryNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse getCategory(Long id) {
        return toResponse(findByIdOrThrow(id));
    }

    @Transactional
    public CategoryResponse createCategory(CreateRequest request) {
        if (worldcupCategoryRepository.existsByCategoryCode(request.categoryCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_WORLDCUP_CATEGORY_CODE);
        }

        WorldcupCategory entity = WorldcupCategory.builder()
                .categoryCode(request.categoryCode())
                .categoryName(request.categoryName())
                .description(request.description())
                .adminNote(request.adminNote())
                .build();

        WorldcupCategory saved = worldcupCategoryRepository.save(entity);
        log.info("[관리자] 월드컵 카테고리 등록 — id={}, code={}", saved.getCategoryId(), saved.getCategoryCode());
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateRequest request) {
        WorldcupCategory entity = findByIdOrThrow(id);
        entity.update(request.categoryName(), request.description(), request.adminNote());
        log.info("[관리자] 월드컵 카테고리 수정 — id={}, code={}", id, entity.getCategoryCode());
        return toResponse(entity);
    }

    @Transactional
    public void deleteCategory(Long id) {
        WorldcupCategory entity = findByIdOrThrow(id);
        long candidateCount = worldcupCandidateRepository.countByCategoryCategoryId(id);
        if (candidateCount > 0) {
            throw new BusinessException(
                    ErrorCode.WORLDCUP_CATEGORY_IN_USE,
                    "카테고리 ID " + id + "에는 " + candidateCount + "개의 후보 영화가 연결되어 있습니다"
            );
        }
        worldcupCategoryRepository.delete(entity);
        log.info("[관리자] 월드컵 카테고리 삭제 — id={}, code={}", id, entity.getCategoryCode());
    }

    public WorldcupCategory findByCodeOrThrow(String categoryCode) {
        return worldcupCategoryRepository.findByCategoryCode(categoryCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WORLDCUP_CATEGORY_NOT_FOUND,
                        "카테고리 코드 " + categoryCode + "를 찾을 수 없습니다"
                ));
    }

    private WorldcupCategory findByIdOrThrow(Long id) {
        return worldcupCategoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WORLDCUP_CATEGORY_NOT_FOUND,
                        "카테고리 ID " + id + "를 찾을 수 없습니다"
                ));
    }

    private CategoryResponse toResponse(WorldcupCategory entity) {
        return new CategoryResponse(
                entity.getCategoryId(),
                entity.getCategoryCode(),
                entity.getCategoryName(),
                entity.getDescription(),
                entity.getAdminNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
