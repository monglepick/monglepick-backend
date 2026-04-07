package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.PackResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPointPackDto.UpdateRequest;
import com.monglepick.monglepickbackend.domain.payment.entity.PointPackPrice;
import com.monglepick.monglepickbackend.domain.payment.repository.PointPackPriceRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 포인트팩(PointPackPrice) 마스터 관리 서비스.
 *
 * <p>포인트팩은 결제 검증의 핵심 가격표이다. 클라이언트가 임의의 (price, pointsAmount)를
 * 보내 무제한 포인트를 획득하지 못하도록, 본 마스터 데이터로 정확 매칭 검증한다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>포인트팩 목록 페이징 조회</li>
 *   <li>포인트팩 단건 조회</li>
 *   <li>신규 등록 ((price, pointsAmount) UNIQUE)</li>
 *   <li>메타 수정</li>
 *   <li>활성 상태 토글</li>
 *   <li>hard delete (운영 중인 팩 삭제 시 결제 흐름에 영향 주의)</li>
 * </ol>
 *
 * <h3>운영 주의사항</h3>
 * <ul>
 *   <li>가격 변경은 결제 안정성에 영향. PointPackPrice가 결제 검증 단일 원천</li>
 *   <li>폐지된 팩은 hard delete보다 isActive=false 토글 권장</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPointPackService {

    private final PointPackPriceRepository repository;

    public Page<PackResponse> getPacks(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PackResponse getPack(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public PackResponse createPack(CreateRequest request) {
        if (repository.existsByPriceAndPointsAmount(request.price(), request.pointsAmount())) {
            throw new BusinessException(ErrorCode.DUPLICATE_POINT_PACK);
        }

        PointPackPrice entity = PointPackPrice.builder()
                .packName(request.packName())
                .price(request.price())
                .pointsAmount(request.pointsAmount())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        PointPackPrice saved = repository.save(entity);
        log.info("[관리자] 포인트팩 등록 — packId={}, name={}, price={}원, points={}P",
                saved.getPackId(), saved.getPackName(), saved.getPrice(), saved.getPointsAmount());

        return toResponse(saved);
    }

    @Transactional
    public PackResponse updatePack(Long id, UpdateRequest request) {
        PointPackPrice entity = findOrThrow(id);
        entity.updateInfo(
                request.packName(),
                request.price(),
                request.pointsAmount(),
                request.isActive(),
                request.sortOrder()
        );
        log.info("[관리자] 포인트팩 수정 — packId={}, name={}, price={}원, points={}P",
                id, entity.getPackName(), entity.getPrice(), entity.getPointsAmount());
        return toResponse(entity);
    }

    @Transactional
    public PackResponse updateActive(Long id, UpdateActiveRequest request) {
        PointPackPrice entity = findOrThrow(id);
        boolean newActive = Boolean.TRUE.equals(request.isActive());
        entity.updateActive(newActive);
        log.info("[관리자] 포인트팩 활성 토글 — packId={}, isActive={}", id, newActive);
        return toResponse(entity);
    }

    @Transactional
    public void deletePack(Long id) {
        PointPackPrice entity = findOrThrow(id);
        repository.delete(entity);
        log.warn("[관리자] 포인트팩 hard delete — packId={}, name={}", id, entity.getPackName());
    }

    private PointPackPrice findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POINT_PACK_NOT_FOUND,
                        "포인트팩 ID " + id + "를 찾을 수 없습니다"));
    }

    private PackResponse toResponse(PointPackPrice entity) {
        return new PackResponse(
                entity.getPackId(),
                entity.getPackName(),
                entity.getPrice(),
                entity.getPointsAmount(),
                entity.getIsActive(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
