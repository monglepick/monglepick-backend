package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.KeywordResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.UpdateExcludedRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPopularSearchDto.UpdateRequest;
import com.monglepick.monglepickbackend.domain.search.entity.PopularSearchKeyword;
import com.monglepick.monglepickbackend.domain.search.repository.PopularSearchKeywordRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 인기 검색어(PopularSearchKeyword) 관리 서비스.
 *
 * <p>인기 검색어 마스터 데이터의 등록·수정·제외 토글·삭제 비즈니스 로직.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPopularSearchService {

    private final PopularSearchKeywordRepository repository;

    public Page<KeywordResponse> getKeywords(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public KeywordResponse getKeyword(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public KeywordResponse createKeyword(CreateRequest request) {
        if (repository.existsByKeyword(request.keyword())) {
            throw new BusinessException(ErrorCode.DUPLICATE_POPULAR_KEYWORD);
        }

        PopularSearchKeyword entity = PopularSearchKeyword.builder()
                .keyword(request.keyword())
                .displayRank(request.displayRank())
                .manualPriority(request.manualPriority() != null ? request.manualPriority() : 0)
                .isExcluded(Boolean.TRUE.equals(request.isExcluded()))
                .adminNote(request.adminNote())
                .build();

        PopularSearchKeyword saved = repository.save(entity);
        log.info("[관리자] 인기 검색어 등록 — id={}, keyword={}, isExcluded={}",
                saved.getId(), saved.getKeyword(), saved.getIsExcluded());

        return toResponse(saved);
    }

    @Transactional
    public KeywordResponse updateKeyword(Long id, UpdateRequest request) {
        PopularSearchKeyword entity = findOrThrow(id);
        entity.updateInfo(
                request.displayRank(),
                request.manualPriority(),
                request.isExcluded(),
                request.adminNote()
        );
        log.info("[관리자] 인기 검색어 수정 — id={}, keyword={}", id, entity.getKeyword());
        return toResponse(entity);
    }

    @Transactional
    public KeywordResponse updateExcluded(Long id, UpdateExcludedRequest request) {
        PopularSearchKeyword entity = findOrThrow(id);
        boolean newExcluded = Boolean.TRUE.equals(request.isExcluded());
        entity.updateExcluded(newExcluded);
        log.info("[관리자] 인기 검색어 제외 토글 — id={}, keyword={}, isExcluded={}",
                id, entity.getKeyword(), newExcluded);
        return toResponse(entity);
    }

    @Transactional
    public void deleteKeyword(Long id) {
        PopularSearchKeyword entity = findOrThrow(id);
        repository.delete(entity);
        log.warn("[관리자] 인기 검색어 삭제 — id={}, keyword={}", id, entity.getKeyword());
    }

    private PopularSearchKeyword findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POPULAR_SEARCH_NOT_FOUND,
                        "인기 검색어 ID " + id + "를 찾을 수 없습니다"));
    }

    private KeywordResponse toResponse(PopularSearchKeyword entity) {
        return new KeywordResponse(
                entity.getId(),
                entity.getKeyword(),
                entity.getDisplayRank(),
                entity.getManualPriority(),
                entity.getIsExcluded(),
                entity.getAdminNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
