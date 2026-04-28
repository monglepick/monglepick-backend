package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.PurchaseResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItem;
import com.monglepick.monglepickbackend.domain.reward.dto.PointShopDto.ShopItemsResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PointShopService 통합 테스트 (v4 DB 기반 전환 검증, 2026-04-28).
 *
 * <p>H2 인메모리 DB + {@code @SpringBootTest} 로 실제 컨텍스트를 로드하여 검증한다.
 * {@link com.monglepick.monglepickbackend.domain.reward.config.PointItemInitializer}
 * 가 ApplicationRunner로 자동 실행되므로 AI 이용권 4종 시드는 테스트 시작 시 이미 존재한다.</p>
 *
 * <h3>핵심 검증 사항</h3>
 * <ul>
 *   <li>DB 활성 상품만 상점 목록에 노출되는지 확인 (admin 비활성화 즉시 반영)</li>
 *   <li>admin 이 가격을 변경하면 구매 차감 포인트에 반영되는지 확인</li>
 *   <li>비활성화된 상품 구매 시 {@link ErrorCode#ITEM_NOT_FOUND} 예외 발생</li>
 *   <li>잘못된 packType 입력 시 {@link ErrorCode#INVALID_INPUT} 예외 발생</li>
 * </ul>
 *
 * <h3>트랜잭션 전략</h3>
 * <p>각 테스트 메서드는 {@code @Transactional}로 감싸 종료 시 자동 롤백된다.
 * 단, {@link PointService#initializePoint}는 {@code REQUIRES_NEW} 전파가 있어
 * 별도 트랜잭션으로 커밋되므로 {@link #setUp()} 에서 직접 Repository 를 통해
 * UserPoint/UserAiQuota 를 INSERT 한다.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("PointShopService — DB 기반 상점 전환 통합 테스트 (v4, 2026-04-28)")
class PointShopServiceTest {

    @Autowired
    private PointShopService pointShopService;

    @Autowired
    private PointItemRepository pointItemRepository;

    @Autowired
    private com.monglepick.monglepickbackend.domain.reward.repository.UserPointRepository userPointRepository;

    @Autowired
    private com.monglepick.monglepickbackend.domain.reward.repository.UserAiQuotaRepository userAiQuotaRepository;

    @Autowired
    private com.monglepick.monglepickbackend.domain.reward.repository.GradeRepository gradeRepository;

    // ──────────────────────────────────────────────
    // 테스트 픽스처 상수
    // ──────────────────────────────────────────────

    /** 테스트에 사용할 사용자 ID — 실제 DB와 충돌하지 않도록 고정값 사용 */
    private static final String TEST_USER_ID = "test-shop-user-001";

    /** 테스트 사용자의 초기 포인트 잔액 (충분히 크게 설정하여 잔액 부족 예외를 방지) */
    private static final int INITIAL_BALANCE = 10_000;

    // ──────────────────────────────────────────────
    // 공통 설정
    // ──────────────────────────────────────────────

    /**
     * 각 테스트 전에 실행되는 공통 setup.
     *
     * <p>테스트 사용자의 UserPoint + UserAiQuota 레코드를 직접 INSERT 한다.
     * PointService.initializePoint() 는 REQUIRES_NEW 트랜잭션 전파를 사용하여
     * 별도 커밋이 발생하므로, 단순히 Repository 를 통해 직접 INSERT 하여
     * 테스트 트랜잭션(@Transactional 자동 롤백) 안에 포함시킨다.</p>
     */
    @BeforeEach
    void setUp() {
        // NORMAL 등급 조회 (GradeInitializer 가 시드하므로 존재해야 함)
        com.monglepick.monglepickbackend.domain.reward.entity.Grade normalGrade =
                gradeRepository.findByGradeCode("NORMAL").orElse(null);

        // UserPoint 레코드가 없을 때만 INSERT (멱등성 — 테스트 메서드가 재실행될 수 있음)
        if (!userPointRepository.existsByUserId(TEST_USER_ID)) {
            com.monglepick.monglepickbackend.domain.reward.entity.UserPoint userPoint =
                    com.monglepick.monglepickbackend.domain.reward.entity.UserPoint.builder()
                            .userId(TEST_USER_ID)
                            .balance(INITIAL_BALANCE)
                            .totalEarned(INITIAL_BALANCE)
                            .dailyEarned(0)
                            .dailyReset(java.time.LocalDate.now())
                            .earnedByActivity(0)
                            .dailyCapUsed(0)
                            .grade(normalGrade)
                            .build();
            userPointRepository.save(userPoint);
        }

        // UserAiQuota 레코드가 없을 때만 INSERT
        if (!userAiQuotaRepository.existsByUserId(TEST_USER_ID)) {
            com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota userAiQuota =
                    com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota.builder()
                            .userId(TEST_USER_ID)
                            .dailyAiUsed(0)
                            .monthlyCouponUsed(0)
                            .purchasedAiTokens(0)
                            .freeDailyGranted(0)
                            .build();
            userAiQuotaRepository.save(userAiQuota);
        }
    }

    // ══════════════════════════════════════════════
    // getShopItems 테스트 그룹
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("getShopItems — 상점 목록 조회")
    class GetShopItemsTest {

        /**
         * 정상 케이스: PointItemInitializer가 시드한 AI 이용권 4종이 모두 활성일 때
         * getShopItems 가 4건을 반환하는지 검증한다.
         *
         * <p>PointItemInitializer 는 애플리케이션 시작 시 자동 실행되므로
         * AI_TOKEN_1/5/20/50 시드가 이미 존재한다. 모두 isActive=true 이므로 4건 반환.</p>
         */
        @Test
        @DisplayName("getShopItems_정상_DB활성상품반환 — 활성 AI 이용권 4종 전체 반환")
        void getShopItems_정상_DB활성상품반환() {
            // when
            ShopItemsResponse response = pointShopService.getShopItems(TEST_USER_ID);

            // then: 4종 AI 이용권이 모두 반환되어야 한다
            assertThat(response).isNotNull();
            assertThat(response.items()).hasSize(4);

            // 반환된 아이템 ID 가 AI_TOKEN_* 형태인지 확인
            List<String> itemIds = response.items().stream()
                    .map(ShopItem::itemId)
                    .toList();
            assertThat(itemIds).containsExactlyInAnyOrder(
                    "AI_TOKEN_1", "AI_TOKEN_5", "AI_TOKEN_20", "AI_TOKEN_50"
            );

            // 현재 잔액이 응답에 포함되는지 확인
            assertThat(response.currentBalance()).isEqualTo(INITIAL_BALANCE);
        }

        /**
         * admin 비활성화 즉시 반영 케이스:
         * AI_TOKEN_5 를 isActive=false 로 변경하면 getShopItems 결과에서 제외되어야 한다.
         *
         * <p>v4 전환의 핵심 기능 — 하드코딩 시절에는 admin 변경이 반영되지 않았다.</p>
         */
        @Test
        @DisplayName("getShopItems_비활성상품제외 — AI_TOKEN_5 비활성화 시 3건만 반환")
        void getShopItems_비활성상품제외() {
            // given: AI_TOKEN_5 상품을 isActive=false 로 변경한다
            PointItem token5 = pointItemRepository
                    .findFirstByItemTypeAndIsActiveTrue(PointItemType.AI_TOKEN_5)
                    .orElseThrow(() -> new IllegalStateException("AI_TOKEN_5 시드가 없음 — PointItemInitializer 확인 필요"));

            // Builder-기반 재구성으로 isActive=false 처리 (엔티티에 @Setter 없음)
            PointItem deactivated = PointItem.builder()
                    .pointItemId(token5.getPointItemId())
                    .itemName(token5.getItemName())
                    .itemDescription(token5.getItemDescription())
                    .itemPrice(token5.getItemPrice())
                    .itemCategory(token5.getItemCategory())
                    .itemType(token5.getItemType())
                    .amount(token5.getAmount())
                    .durationDays(token5.getDurationDays())
                    .imageUrl(token5.getImageUrl())
                    .isActive(false)  // ← 비활성화 (admin 처리 시뮬레이션)
                    .build();
            pointItemRepository.save(deactivated);
            pointItemRepository.flush(); // DB 반영 강제

            // when
            ShopItemsResponse response = pointShopService.getShopItems(TEST_USER_ID);

            // then: AI_TOKEN_5 를 제외한 3건만 반환되어야 한다
            assertThat(response.items()).hasSize(3);

            List<String> itemIds = response.items().stream()
                    .map(ShopItem::itemId)
                    .toList();
            assertThat(itemIds).doesNotContain("AI_TOKEN_5");
            assertThat(itemIds).containsExactlyInAnyOrder("AI_TOKEN_1", "AI_TOKEN_20", "AI_TOKEN_50");
        }

        /**
         * 가격 수정 즉시 반영 케이스:
         * AI_TOKEN_1 가격을 999P 로 변경하면 getShopItems 응답의 cost 가 999 여야 한다.
         */
        @Test
        @DisplayName("getShopItems_가격수정반영 — DB 가격 변경이 상점 목록 cost에 반영")
        void getShopItems_가격수정반영() {
            // given: AI_TOKEN_1 가격을 999P 로 변경 (admin 수정 시뮬레이션)
            PointItem token1 = pointItemRepository
                    .findFirstByItemTypeAndIsActiveTrue(PointItemType.AI_TOKEN_1)
                    .orElseThrow(() -> new IllegalStateException("AI_TOKEN_1 시드가 없음"));

            int newPrice = 999; // admin 이 바꾼 임의 가격
            PointItem modified = PointItem.builder()
                    .pointItemId(token1.getPointItemId())
                    .itemName(token1.getItemName())
                    .itemDescription(token1.getItemDescription())
                    .itemPrice(newPrice)                  // ← 가격 변경
                    .itemCategory(token1.getItemCategory())
                    .itemType(token1.getItemType())
                    .amount(token1.getAmount())
                    .durationDays(token1.getDurationDays())
                    .imageUrl(token1.getImageUrl())
                    .isActive(true)
                    .build();
            pointItemRepository.save(modified);
            pointItemRepository.flush();

            // when
            ShopItemsResponse response = pointShopService.getShopItems(TEST_USER_ID);

            // then: AI_TOKEN_1 의 cost 가 변경된 999 여야 한다
            ShopItem token1Item = response.items().stream()
                    .filter(i -> "AI_TOKEN_1".equals(i.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("AI_TOKEN_1 이 목록에 없음"));

            assertThat(token1Item.cost()).isEqualTo(newPrice);
        }
    }

    // ══════════════════════════════════════════════
    // purchaseAiTokens 테스트 그룹
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("purchaseAiTokens — AI 이용권 구매")
    class PurchaseAiTokensTest {

        /**
         * 비활성 상품 구매 차단 케이스:
         * admin 이 AI_TOKEN_5 를 비활성화한 뒤 구매를 시도하면
         * {@link ErrorCode#ITEM_NOT_FOUND} BusinessException 이 발생해야 한다.
         *
         * <p>v4 전환 핵심 — 하드코딩 시절에는 DB 비활성화가 무시되고 구매가 성공했다.</p>
         */
        @Test
        @DisplayName("purchaseAiTokens_비활성상품_ITEM_NOT_FOUND — 비활성화 시 구매 차단")
        void purchaseAiTokens_비활성상품_ITEM_NOT_FOUND() {
            // given: AI_TOKEN_5 비활성화 (admin 처리 시뮬레이션)
            PointItem token5 = pointItemRepository
                    .findFirstByItemTypeAndIsActiveTrue(PointItemType.AI_TOKEN_5)
                    .orElseThrow(() -> new IllegalStateException("AI_TOKEN_5 시드가 없음"));

            PointItem deactivated = PointItem.builder()
                    .pointItemId(token5.getPointItemId())
                    .itemName(token5.getItemName())
                    .itemDescription(token5.getItemDescription())
                    .itemPrice(token5.getItemPrice())
                    .itemCategory(token5.getItemCategory())
                    .itemType(token5.getItemType())
                    .amount(token5.getAmount())
                    .durationDays(token5.getDurationDays())
                    .imageUrl(token5.getImageUrl())
                    .isActive(false)  // ← 비활성화
                    .build();
            pointItemRepository.save(deactivated);
            pointItemRepository.flush();

            // when & then: 구매 시도 → ITEM_NOT_FOUND 예외 발생
            assertThatThrownBy(() -> pointShopService.purchaseAiTokens(TEST_USER_ID, "AI_TOKEN_5"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND);
                    });
        }

        /**
         * 가격 수정 반영 케이스:
         * admin 이 AI_TOKEN_20 가격을 777P 로 변경하면 구매 시 차감 포인트가 777 이어야 한다.
         *
         * <p>v4 전환 핵심 — 하드코딩 상수(COST_AI_TOKEN_20=200) 를 사용하던 구버전에서는
         * admin 이 DB 가격을 바꿔도 항상 200P 가 차감되었다.</p>
         */
        @Test
        @DisplayName("purchaseAiTokens_가격수정반영 — DB 변경 가격으로 포인트 차감")
        void purchaseAiTokens_가격수정반영() {
            // given: AI_TOKEN_20 가격을 777P 로 변경 (admin 수정 시뮬레이션)
            PointItem token20 = pointItemRepository
                    .findFirstByItemTypeAndIsActiveTrue(PointItemType.AI_TOKEN_20)
                    .orElseThrow(() -> new IllegalStateException("AI_TOKEN_20 시드가 없음"));

            int originalPrice = token20.getItemPrice(); // 원래 가격 (기본 200P)
            int modifiedPrice = 777;                    // admin 이 변경한 가격

            PointItem modified = PointItem.builder()
                    .pointItemId(token20.getPointItemId())
                    .itemName(token20.getItemName())
                    .itemDescription(token20.getItemDescription())
                    .itemPrice(modifiedPrice)            // ← 가격 변경
                    .itemCategory(token20.getItemCategory())
                    .itemType(token20.getItemType())
                    .amount(token20.getAmount())
                    .durationDays(token20.getDurationDays())
                    .imageUrl(token20.getImageUrl())
                    .isActive(true)
                    .build();
            pointItemRepository.save(modified);
            pointItemRepository.flush();

            // when: 변경된 가격으로 구매
            PurchaseResponse response = pointShopService.purchaseAiTokens(TEST_USER_ID, "AI_TOKEN_20");

            // then: 차감 포인트가 777P 이어야 한다 (하드코딩 200P 가 아님)
            assertThat(response.deductedPoints())
                    .as("DB 에 저장된 수정 가격(%dP)이 차감되어야 한다 (원래 %dP 아님)", modifiedPrice, originalPrice)
                    .isEqualTo(modifiedPrice);

            // 지급 토큰 수는 amount 컬럼(20회) 그대로여야 한다
            assertThat(response.addedTokens()).isEqualTo(20);

            // 구매 후 잔액 = 초기 잔액 - 변경된 가격
            assertThat(response.remainingBalance()).isEqualTo(INITIAL_BALANCE - modifiedPrice);
        }

        /**
         * 잘못된 packType 검증 케이스:
         * 허용되지 않는 문자열을 packType 으로 전달하면 {@link ErrorCode#INVALID_INPUT} 이 발생해야 한다.
         */
        @Test
        @DisplayName("purchaseAiTokens_잘못된packType_INVALID_INPUT — 비정상 packType 차단")
        void purchaseAiTokens_잘못된packType_INVALID_INPUT() {
            // when & then: "FOO_BAR" 는 AI_TOKEN_* 에 해당하지 않으므로 INVALID_INPUT 예외
            assertThatThrownBy(() -> pointShopService.purchaseAiTokens(TEST_USER_ID, "FOO_BAR"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });

            // 빈 문자열도 INVALID_INPUT 이어야 한다
            assertThatThrownBy(() -> pointShopService.purchaseAiTokens(TEST_USER_ID, ""))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });

            // AI 이용권이 아닌 다른 유효한 PointItemType 이름도 INVALID_INPUT 이어야 한다
            // (AVATAR_MONGLE 은 PointItemType 에 존재하지만 Dispense.AI_TOKEN 이 아님)
            assertThatThrownBy(() -> pointShopService.purchaseAiTokens(TEST_USER_ID, "AVATAR_MONGLE"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    });
        }

        /**
         * 정상 구매 케이스:
         * AI_TOKEN_1 을 구매하면 DB 에 저장된 가격(10P)만큼 차감되고
         * 이용권이 1회 지급되어야 한다.
         */
        @Test
        @DisplayName("purchaseAiTokens_정상구매_AI_TOKEN_1 — 기본 10P 차감, 이용권 1회 지급")
        void purchaseAiTokens_정상구매() {
            // when
            PurchaseResponse response = pointShopService.purchaseAiTokens(TEST_USER_ID, "AI_TOKEN_1");

            // then
            assertThat(response.deductedPoints()).isEqualTo(10);      // 기본 가격 10P
            assertThat(response.addedTokens()).isEqualTo(1);           // 지급 토큰 1회
            assertThat(response.remainingBalance()).isEqualTo(INITIAL_BALANCE - 10);
            assertThat(response.totalPurchasedTokens()).isEqualTo(1);  // 이전 0 + 지급 1
        }
    }
}
