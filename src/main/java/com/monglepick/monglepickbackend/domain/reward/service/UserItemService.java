package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemCategory;
import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.domain.reward.constants.UserItemStatus;
import com.monglepick.monglepickbackend.domain.reward.dto.UserItemDto.UserItemPageResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.UserItemDto.UserItemResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.UserItemDto.UserItemSummaryResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;
import com.monglepick.monglepickbackend.domain.reward.repository.UserItemRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 사용자 보유 아이템 서비스 (2026-04-14 신규, C 방향).
 *
 * <p>"내 아이템" 페이지 및 MyPage 프로필 상단 착용 아이템 표시를 지원한다.
 * 목록·요약 조회와 착용/해제/사용 전이를 담당한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #getMyItems} — 카테고리 필터 + 페이징 목록</li>
 *   <li>{@link #getSummary} — 카테고리별 요약 + 착용 아이템</li>
 *   <li>{@link #equipItem} — 아바타/배지 착용 (같은 카테고리 내 기존 착용 자동 해제)</li>
 *   <li>{@link #unequipItem} — 착용 해제</li>
 *   <li>{@link #useItem} — 힌트/응모권 1회 소비</li>
 * </ul>
 *
 * <h3>착용 규칙</h3>
 * <ul>
 *   <li>아바타·배지에만 착용 개념 존재 (쿠폰/응모권/힌트는 착용 불가)</li>
 *   <li>카테고리당 1개만 EQUIPPED 허용 — 새 착용 시 기존 착용 자동 ACTIVE로 해제</li>
 *   <li>만료/사용 완료된 아이템은 착용 불가</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemService {

    /** 착용 가능한 카테고리 — 아바타/배지만 해당 */
    private static final Set<String> EQUIPPABLE_CATEGORIES = Set.of(
            PointItemCategory.AVATAR,
            PointItemCategory.BADGE
    );

    /** 보유 아이템 리포지토리 */
    private final UserItemRepository userItemRepository;

    /**
     * 영화 티켓 추첨 서비스 (2026-04-14 후속 #3).
     *
     * <p>응모권({@code APPLY_MOVIE_TICKET}) 사용 시 자동 entry 발급용. 다른 카테고리는 영향 없음.</p>
     */
    private final MovieTicketLotteryService lotteryService;

    // ──────────────────────────────────────────────
    // 목록 / 요약 조회
    // ──────────────────────────────────────────────

    /**
     * 유저의 보유 아이템 목록을 페이징으로 조회한다.
     *
     * <p>카테고리 필터가 null/빈 문자열이면 전체를 반환. 기본 정렬은 acquiredAt DESC
     * (최근 획득순). Pageable에 정렬이 포함되어 있으면 그것을 우선한다.</p>
     *
     * @param userId   사용자 ID
     * @param category 필터 카테고리 (nullable — 전체 조회 시 null)
     * @param pageable 페이지 정보
     * @return 페이지 응답 DTO
     */
    public UserItemPageResponse getMyItems(String userId, String category, Pageable pageable) {
        /* 기본 정렬 — 호출자가 명시하지 않았으면 acquiredAt DESC */
        Pageable effective = pageable;
        if (pageable.getSort().isUnsorted()) {
            effective = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "acquiredAt")
            );
        }

        Page<UserItem> page = (category == null || category.isBlank())
                ? userItemRepository.findByUserIdWithItem(userId, effective)
                : userItemRepository.findByUserIdAndCategoryWithItem(userId, category, effective);

        List<UserItemResponse> content = page.getContent().stream()
                .map(UserItemResponse::from)
                .toList();

        log.debug("보유 아이템 목록 조회: userId={}, category={}, page={}, size={}, total={}",
                userId, category, page.getNumber(), page.getSize(), page.getTotalElements());

        return new UserItemPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * 카테고리별 보유 개수 요약 + 착용 중인 아바타/배지를 반환한다.
     *
     * @param userId 사용자 ID
     * @return 요약 DTO
     */
    public UserItemSummaryResponse getSummary(String userId) {
        long totalActive = userItemRepository.countByUserIdAndStatus(userId, UserItemStatus.ACTIVE)
                + userItemRepository.countByUserIdAndStatus(userId, UserItemStatus.EQUIPPED);

        long avatarCount = countByCategory(userId, PointItemCategory.AVATAR);
        long badgeCount = countByCategory(userId, PointItemCategory.BADGE);
        long couponCount = countByCategory(userId, PointItemCategory.COUPON);
        long applyCount = userItemRepository.countByUserAndCategoryAndStatus(
                userId, PointItemCategory.APPLY, UserItemStatus.ACTIVE);
        long hintCount = userItemRepository.countByUserAndCategoryAndStatus(
                userId, PointItemCategory.HINT, UserItemStatus.ACTIVE);

        UserItemResponse equippedAvatar = pickFirstEquipped(userId, PointItemCategory.AVATAR);
        UserItemResponse equippedBadge = pickFirstEquipped(userId, PointItemCategory.BADGE);

        return new UserItemSummaryResponse(
                totalActive,
                avatarCount,
                badgeCount,
                couponCount,
                applyCount,
                hintCount,
                equippedAvatar,
                equippedBadge
        );
    }

    /**
     * 유저의 착용 아이템(아바타+배지)만 조회 — 프로필 렌더링용 경량 API.
     *
     * @param userId 사용자 ID
     * @return 각 카테고리 1개씩 (없으면 null 포함)
     */
    public List<UserItemResponse> getEquipped(String userId) {
        UserItemResponse avatar = pickFirstEquipped(userId, PointItemCategory.AVATAR);
        UserItemResponse badge = pickFirstEquipped(userId, PointItemCategory.BADGE);
        return java.util.Arrays.asList(avatar, badge); // null 포함 가능 — 클라이언트 방어 필요
    }

    // ──────────────────────────────────────────────
    // 상태 전이 (쓰기 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 아이템 착용 — ACTIVE → EQUIPPED 전환.
     *
     * <p>같은 카테고리에 이미 착용 중인 다른 아이템이 있으면 자동 해제(ACTIVE로 되돌림)한다.
     * 쿠폰/응모권/힌트는 착용 불가 (USER_ITEM_NOT_EQUIPPABLE).
     * 만료된 아이템은 착용 불가 (USER_ITEM_INVALID_STATE).</p>
     *
     * @param userId     사용자 ID
     * @param userItemId 착용할 아이템 PK
     * @return 착용된 아이템 응답
     */
    @Transactional
    public UserItemResponse equipItem(String userId, Long userItemId) {
        UserItem target = loadOwnedOrThrow(userId, userItemId);

        String category = target.getPointItem().getItemCategory();
        if (!EQUIPPABLE_CATEGORIES.contains(category)) {
            log.warn("착용 불가 카테고리 요청: userId={}, userItemId={}, category={}",
                    userId, userItemId, category);
            throw new BusinessException(ErrorCode.USER_ITEM_NOT_EQUIPPABLE);
        }

        /* 만료 선검증 — 지연 처리로 아직 EXPIRED가 아니어도 expires_at이 지났으면 차단 */
        LocalDateTime now = LocalDateTime.now();
        if (target.isExpired(now) || target.getStatus() == UserItemStatus.EXPIRED) {
            log.warn("만료된 아이템 착용 시도: userId={}, userItemId={}", userId, userItemId);
            throw new BusinessException(ErrorCode.USER_ITEM_INVALID_STATE, "만료된 아이템입니다.");
        }

        /* 동일 카테고리에 기존 착용 아이템이 있으면 해제 */
        List<UserItem> currentlyEquipped = userItemRepository.findEquippedByUserAndCategory(userId, category);
        for (UserItem equipped : currentlyEquipped) {
            if (!equipped.getUserItemId().equals(userItemId)) {
                equipped.unequip();
                log.info("기존 착용 자동 해제: userId={}, userItemId={}, category={}",
                        userId, equipped.getUserItemId(), category);
            }
        }

        /* 대상 착용 */
        if (target.getStatus() != UserItemStatus.EQUIPPED) {
            target.equip();
        }

        log.info("아이템 착용 완료: userId={}, userItemId={}, category={}", userId, userItemId, category);
        return UserItemResponse.from(target);
    }

    /**
     * 아이템 착용 해제 — EQUIPPED → ACTIVE.
     *
     * <p>이미 ACTIVE면 no-op. USED/EXPIRED 상태에서는 차단.</p>
     *
     * @param userId     사용자 ID
     * @param userItemId 해제할 아이템 PK
     * @return 해제 후 응답
     */
    @Transactional
    public UserItemResponse unequipItem(String userId, Long userItemId) {
        UserItem target = loadOwnedOrThrow(userId, userItemId);

        if (target.getStatus() == UserItemStatus.USED || target.getStatus() == UserItemStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.USER_ITEM_INVALID_STATE,
                    "사용 완료되거나 만료된 아이템은 해제할 수 없습니다.");
        }

        target.unequip();

        log.info("아이템 착용 해제: userId={}, userItemId={}", userId, userItemId);
        return UserItemResponse.from(target);
    }

    /**
     * 특정 itemType의 ACTIVE 보유 아이템 중 가장 오래된 1건을 사용한다 (2026-04-14 B' 후속).
     *
     * <p>호출자가 userItemId를 알 수 없는 컨텍스트(예: 퀴즈 화면에서 "힌트 사용" 버튼)를 위한 헬퍼.
     * FIFO 정책으로 만료 위험이 큰 오래된 아이템부터 소비한다.</p>
     *
     * <h4>사용 시나리오</h4>
     * <p>퀴즈 도메인에서 향후 힌트 EP 가 신설되면 다음과 같이 호출:</p>
     * <pre>{@code
     * userItemService.useFirstActiveByType(userId, PointItemType.QUIZ_HINT);
     * }</pre>
     *
     * <h4>예외</h4>
     * <ul>
     *   <li>해당 타입의 ACTIVE 아이템이 0개 → {@link BusinessException}({@link ErrorCode#USER_ITEM_NOT_FOUND})</li>
     *   <li>도메인 메서드 {@link UserItem#consumeOne} 실패 → {@link ErrorCode#USER_ITEM_INVALID_STATE}</li>
     * </ul>
     *
     * @param userId   사용자 ID
     * @param itemType 사용할 아이템 타입 (예: PointItemType.QUIZ_HINT)
     * @return 사용 후 응답
     */
    @Transactional
    public UserItemResponse useFirstActiveByType(String userId, PointItemType itemType) {
        List<UserItem> candidates = userItemRepository.findActiveByUserAndType(userId, itemType);
        if (candidates.isEmpty()) {
            log.warn("타입별 사용 실패 (보유 없음): userId={}, itemType={}", userId, itemType);
            throw new BusinessException(ErrorCode.USER_ITEM_NOT_FOUND,
                    itemType.name() + " 보유 아이템이 없습니다.");
        }
        UserItem target = candidates.get(0);

        try {
            target.consumeOne();
        } catch (IllegalStateException e) {
            log.warn("타입별 아이템 사용 실패: userId={}, userItemId={}, itemType={}, reason={}",
                    userId, target.getUserItemId(), itemType, e.getMessage());
            throw new BusinessException(ErrorCode.USER_ITEM_INVALID_STATE, e.getMessage());
        }

        /* 후속 #3 통합: 응모권 타입이면 자동 entry 발급 (useItem 과 동일 정책). */
        enrollLotteryIfApplyTicket(userId, target);

        log.info("타입별 아이템 사용: userId={}, userItemId={}, itemType={}, remaining={}, status={}",
                userId, target.getUserItemId(), itemType,
                target.getRemainingQuantity(), target.getStatus());
        return UserItemResponse.from(target);
    }

    /**
     * 아이템 1회 사용 — 힌트/응모권 등 소모성 아이템의 remainingQuantity 감소.
     *
     * <p>잔여 수량이 0이 되면 status=USED로 자동 전환된다. 도메인 메서드 {@link UserItem#consumeOne}이
     * 상태·수량 검증을 수행한다.</p>
     *
     * @param userId     사용자 ID
     * @param userItemId 사용할 아이템 PK
     * @return 사용 후 응답
     */
    @Transactional
    public UserItemResponse useItem(String userId, Long userItemId) {
        UserItem target = loadOwnedOrThrow(userId, userItemId);

        try {
            target.consumeOne();
        } catch (IllegalStateException e) {
            log.warn("아이템 사용 실패: userId={}, userItemId={}, reason={}",
                    userId, userItemId, e.getMessage());
            throw new BusinessException(ErrorCode.USER_ITEM_INVALID_STATE, e.getMessage());
        }

        /* 후속 #3 통합: 응모권 사용 시 자동으로 현재 월 추첨 회차에 entry 발급.
           다른 카테고리(힌트 등)는 영향 없음. enrollEntry 가 동일 트랜잭션 내에서 수행되므로
           consumeOne() 실패 시 entry 도 발급되지 않고, entry INSERT 실패 시 사용 처리도 롤백. */
        enrollLotteryIfApplyTicket(userId, target);

        log.info("아이템 사용: userId={}, userItemId={}, remaining={}, status={}",
                userId, userItemId, target.getRemainingQuantity(), target.getStatus());
        return UserItemResponse.from(target);
    }

    /**
     * 응모권({@code APPLY_MOVIE_TICKET}) 사용 시 추첨 entry 자동 발급 — 후속 #3.
     *
     * <p>{@link #useItem} / {@link #useFirstActiveByType} 양쪽에서 호출되어,
     * 카테고리·타입이 일치할 때만 {@link MovieTicketLotteryService#enrollEntry}를 호출한다.
     * 다른 아이템 사용 시 no-op.</p>
     *
     * @param userId   사용자 ID
     * @param userItem 사용 처리된 보유 아이템 (consumeOne 호출 직후)
     */
    private void enrollLotteryIfApplyTicket(String userId, UserItem userItem) {
        com.monglepick.monglepickbackend.domain.reward.constants.PointItemType type =
                userItem.getPointItem().getItemType();
        if (type == com.monglepick.monglepickbackend.domain.reward.constants.PointItemType.APPLY_MOVIE_TICKET) {
            try {
                lotteryService.enrollEntry(userId, userItem);
            } catch (IllegalStateException e) {
                /* 회차가 이미 COMPLETED 인 극단 상황 — 사용 처리는 유지하고 응모만 실패.
                   포인트 손실 없이 아이템만 USED 처리되어 사용자에게 손해. 운영 모니터링 필요. */
                log.error("응모권 사용 후 entry 발급 실패 (회차 이슈): userId={}, userItemId={}, reason={}",
                        userId, userItem.getUserItemId(), e.getMessage());
                throw new BusinessException(ErrorCode.USER_ITEM_INVALID_STATE,
                        "이번 달 추첨이 이미 마감되었습니다. 다음 달 응모를 이용해 주세요.");
            }
        }
    }

    // ──────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────

    /**
     * 유저 소유 아이템을 조회하되 타인 소유이거나 존재하지 않으면 예외.
     *
     * <p>권한 체크(USER_ITEM_ACCESS_DENIED)와 존재 확인을 한 번에 수행하여
     * 외부에서 userItemId만으로 조회 시 발생할 수 있는 정보 누수를 방지한다.</p>
     *
     * @param userId     호출자 ID
     * @param userItemId 조회 대상 PK
     * @return 소유가 확인된 UserItem (pointItem이 LAZY라 추가 접근 시 초기화됨)
     */
    private UserItem loadOwnedOrThrow(String userId, Long userItemId) {
        return userItemRepository.findByUserItemIdAndUserId(userItemId, userId)
                .orElseThrow(() -> {
                    /* 존재 자체를 모르는 외부 유저에게는 NOT_FOUND로 통일해 존재 여부 누수 방지 */
                    log.warn("보유 아이템 조회 실패 (미존재 또는 타인 소유): userId={}, userItemId={}",
                            userId, userItemId);
                    return new BusinessException(ErrorCode.USER_ITEM_NOT_FOUND);
                });
    }

    /**
     * 특정 카테고리의 ACTIVE + EQUIPPED 개수 합산.
     */
    private long countByCategory(String userId, String category) {
        long active = userItemRepository.countByUserAndCategoryAndStatus(
                userId, category, UserItemStatus.ACTIVE);
        long equipped = userItemRepository.countByUserAndCategoryAndStatus(
                userId, category, UserItemStatus.EQUIPPED);
        return active + equipped;
    }

    /**
     * 특정 카테고리의 첫 번째 착용 아이템을 응답 DTO로 변환 반환.
     *
     * <p>정합성 오류로 2개 이상 EQUIPPED가 있으면 가장 최근 착용한 것만 선택.</p>
     *
     * @return 착용 중 아이템 DTO (없으면 null)
     */
    private UserItemResponse pickFirstEquipped(String userId, String category) {
        List<UserItem> equipped = userItemRepository.findEquippedByUserAndCategory(userId, category);
        if (equipped.isEmpty()) {
            return null;
        }
        if (equipped.size() > 1) {
            log.warn("동일 카테고리에 복수 착용 감지 — 첫 번째만 반환: userId={}, category={}, count={}",
                    userId, category, equipped.size());
        }
        return UserItemResponse.from(equipped.get(0));
    }
}
