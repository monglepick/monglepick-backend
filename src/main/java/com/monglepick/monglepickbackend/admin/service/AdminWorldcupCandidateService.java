package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.BulkOperationResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CandidateResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.DeactivateBelowRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.UpdateRequest;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCandidate;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCandidateRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 월드컵 후보 영화(WorldcupCandidate) 관리 서비스.
 *
 * <p>월드컵 후보 풀의 등록·수정·활성화 토글·일괄 비활성화·삭제 비즈니스 로직.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>후보 목록 조회 (페이징 + 카테고리 필터)</li>
 *   <li>후보 단건 조회</li>
 *   <li>신규 후보 등록 (movieId+category UNIQUE)</li>
 *   <li>후보 메타 수정</li>
 *   <li>활성화 토글</li>
 *   <li>인기도 임계값 미만 일괄 비활성화</li>
 *   <li>후보 hard delete</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorldcupCandidateService {

    private final WorldcupCandidateRepository repository;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    public Page<CandidateResponse> getCandidates(String category, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            return repository.findByCategoryOrderByCreatedAtDesc(category, pageable)
                    .map(this::toResponse);
        }
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    public CandidateResponse getCandidate(Long id) {
        return toResponse(findOrThrow(id));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    @Transactional
    public CandidateResponse createCandidate(CreateRequest request) {
        String category = (request.category() != null && !request.category().isBlank())
                ? request.category() : "DEFAULT";

        if (repository.existsByMovieIdAndCategory(request.movieId(), category)) {
            throw new BusinessException(ErrorCode.DUPLICATE_WORLDCUP_CANDIDATE);
        }

        WorldcupCandidate entity = WorldcupCandidate.builder()
                .movieId(request.movieId())
                .category(category)
                .popularity(request.popularity() != null ? request.popularity() : 0.0)
                .isActive(true)
                .addedBy(resolveCurrentAdminId())
                .adminNote(request.adminNote())
                .build();

        WorldcupCandidate saved = repository.save(entity);
        log.info("[관리자] 월드컵 후보 등록 — id={}, movieId={}, category={}",
                saved.getId(), saved.getMovieId(), saved.getCategory());

        return toResponse(saved);
    }

    @Transactional
    public CandidateResponse updateCandidate(Long id, UpdateRequest request) {
        WorldcupCandidate entity = findOrThrow(id);
        entity.updateInfo(request.popularity(), request.isActive(), request.adminNote());

        log.info("[관리자] 월드컵 후보 수정 — id={}, movieId={}", id, entity.getMovieId());
        return toResponse(entity);
    }

    @Transactional
    public CandidateResponse updateActiveStatus(Long id, UpdateActiveRequest request) {
        WorldcupCandidate entity = findOrThrow(id);
        boolean newActive = Boolean.TRUE.equals(request.isActive());
        entity.updateActiveStatus(newActive);

        log.info("[관리자] 월드컵 후보 활성 토글 — id={}, isActive={}", id, newActive);
        return toResponse(entity);
    }

    /**
     * 인기도 임계값 미만 일괄 비활성화.
     *
     * @param request 임계값 (popularity &lt; threshold 인 후보 → isActive=false)
     * @return 영향받은 행 수
     */
    @Transactional
    public BulkOperationResponse deactivateBelowPopularity(DeactivateBelowRequest request) {
        double threshold = request.threshold() != null ? request.threshold() : 0.0;
        int affected = repository.deactivateBelowPopularity(threshold);
        log.info("[관리자] 월드컵 후보 일괄 비활성화 — threshold={}, affected={}", threshold, affected);
        return new BulkOperationResponse(
                affected,
                String.format("popularity < %.2f 인 %d개 후보를 비활성화했습니다.", threshold, affected)
        );
    }

    @Transactional
    public void deleteCandidate(Long id) {
        WorldcupCandidate entity = findOrThrow(id);
        repository.delete(entity);
        log.warn("[관리자] 월드컵 후보 삭제 — id={}, movieId={}, category={}",
                id, entity.getMovieId(), entity.getCategory());
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    private WorldcupCandidate findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WORLDCUP_CANDIDATE_NOT_FOUND,
                        "월드컵 후보 ID " + id + "를 찾을 수 없습니다"));
    }

    private String resolveCurrentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    private CandidateResponse toResponse(WorldcupCandidate entity) {
        return new CandidateResponse(
                entity.getId(),
                entity.getMovieId(),
                entity.getCategory(),
                entity.getPopularity(),
                entity.getIsActive(),
                entity.getAddedBy(),
                entity.getAdminNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
