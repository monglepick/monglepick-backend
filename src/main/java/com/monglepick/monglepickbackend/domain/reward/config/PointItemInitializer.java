package com.monglepick.monglepickbackend.domain.reward.config;

import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포인트 상점 아이템 초기 데이터 적재기 — point_items 테이블 시드 데이터 삽입.
 *
 * <p>애플리케이션 시작 시 {@code point_items} 테이블에 시드 아이템이 없으면 INSERT한다.
 * 이미 존재하는 아이템(itemName 기준)은 건너뛰어 멱등(idempotent) 동작을 보장한다.</p>
 *
 * <h3>v3.2 AI 3-소스 모델 — 아이템 설계 원칙</h3>
 * <p>포인트 상점에서 "AI 이용권"을 판매하여 grade 일일 한도와 구독 보너스 풀이
 * 모두 소진된 사용자에게 추가 AI 사용 경로를 제공한다 (source="PURCHASED").</p>
 *
 * <h3>v3.2 AI 이용권 가격 설계 (1P=10원 통일, 구독 유도)</h3>
 * <p>포인트팩 기준 1P = 10원으로 환산하면:</p>
 * <ul>
 *   <li>AI 이용권 1회 (10P = 100원) — 단발성, 긴급 사용</li>
 *   <li>AI 이용권 5회 (50P = 500원) → 100원/회</li>
 *   <li>AI 이용권 20회 (200P = 2,000원) → 100원/회 (볼륨 동일)</li>
 *   <li>AI 이용권 50회 (500P = 5,000원) → 100원/회 (볼륨 동일)</li>
 *   <li>monthly_basic 구독 (2,900원/30회) → 96.7원/회 (이용권 대비 3% 저렴 + 포인트 지급)</li>
 *   <li>→ 이용권과 구독 단가가 유사하나 구독은 포인트까지 지급 → 구독 강력 유도 ✓</li>
 * </ul>
 *
 * <h3>시드 데이터 (7개 아이템)</h3>
 * <table border="1">
 *   <tr><th>아이템명</th><th>카테고리</th><th>가격</th><th>설명</th></tr>
 *   <tr><td>AI 이용권 1회</td><td>COUPON</td><td>10P</td><td>grade 한도 초과 AI 1회 (30일 유효)</td></tr>
 *   <tr><td>AI 이용권 5회</td><td>COUPON</td><td>50P</td><td>grade 한도 초과 AI 5회 (30일 유효)</td></tr>
 *   <tr><td>AI 이용권 20회</td><td>COUPON</td><td>200P</td><td>grade 한도 초과 AI 20회 (30일 유효)</td></tr>
 *   <tr><td>AI 이용권 50회</td><td>COUPON</td><td>500P</td><td>grade 한도 초과 AI 50회 (60일 유효)</td></tr>
 *   <tr><td>영화 티켓 응모권</td><td>APPLY</td><td>150P</td><td>CGV/롯데시네마 영화 티켓 응모 1회</td></tr>
 *   <tr><td>프로필 아바타 - 몽글이</td><td>avatar</td><td>150P</td><td>몽글이 캐릭터 프로필 이미지</td></tr>
 *   <tr><td>프리미엄 배지 (1개월)</td><td>coupon</td><td>100P</td><td>1개월간 프로필 프리미엄 배지</td></tr>
 * </table>
 *
 * <h3>AI 이용권 구매 시 처리 흐름</h3>
 * <ol>
 *   <li>사용자가 포인트 상점에서 AI 이용권 구매 → PointItemService에서 처리</li>
 *   <li>포인트 차감 (deductPoints) + {@code UserPoint.addPurchasedTokens(count)} 호출</li>
 *   <li>AI 요청 시 grade 한도/구독 보너스 소진 후 → {@code UserPoint.consumePurchasedToken()} 호출</li>
 *   <li>v3.2: consumePurchasedToken()에서 monthly_coupon_used도 자동 증가</li>
 * </ol>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(4)} 지정 — GradeInitializer, RewardPolicyInitializer(@Order(2)),
 * SubscriptionPlanInitializer(@Order(3)) 이후 실행.</p>
 *
 * <h3>멱등 전략</h3>
 * <p>itemName 기준으로 존재 여부를 확인한 후 없는 경우에만 INSERT한다.
 * 전체 카운트 방식이 아닌 개별 아이템 확인으로 부분 실패 시에도 안전하게 복구된다.</p>
 *
 * @see PointItem 포인트 아이템 엔티티
 * @see PointItemRepository
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(4) // GradeInitializer, RewardPolicyInitializer, SubscriptionPlanInitializer 이후 실행
public class PointItemInitializer implements ApplicationRunner {

    /** 포인트 아이템 리포지토리 — point_items 테이블 접근 */
    private final PointItemRepository pointItemRepository;

    /**
     * 애플리케이션 시작 시 포인트 상점 아이템 시드 데이터를 적재한다.
     *
     * <p>7개 아이템을 itemName 기준으로 확인하여 없는 경우에만 INSERT한다.
     * 이미 존재하는 아이템은 건너뛰어 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("포인트 상점 아이템 초기화 시작 — point_items 테이블 시드 데이터 확인 (v3.2)");

        List<PointItem> items = buildDefaultItems();

        int insertedCount = 0;
        int skippedCount = 0;

        for (PointItem item : items) {
            // itemName 기준으로 이미 존재하는 아이템은 건너뜀 (멱등)
            boolean exists = pointItemRepository
                    .findByIsActiveTrueOrderByItemPriceAsc()
                    .stream()
                    .anyMatch(existing -> existing.getItemName().equals(item.getItemName()));

            if (exists) {
                log.debug("포인트 아이템 이미 존재 (건너뜀): itemName={}", item.getItemName());
                skippedCount++;
                continue;
            }

            pointItemRepository.save(item);
            insertedCount++;
            log.info("포인트 아이템 INSERT: itemName={}, itemCategory={}, itemPrice={}P",
                    item.getItemName(), item.getItemCategory(), item.getItemPrice());
        }

        if (insertedCount == 0) {
            log.info("포인트 상점 아이템 초기화 완료 — 모든 아이템이 이미 존재함 (INSERT 없음, 건너뜀={}개)", skippedCount);
        } else {
            log.info("포인트 상점 아이템 초기화 완료 — {}개 아이템 INSERT 완료, {}개 건너뜀", insertedCount, skippedCount);
        }
    }

    /**
     * v3.2 기본 포인트 상점 아이템 7개를 PointItem 엔티티 리스트로 생성한다.
     *
     * <p>설계서 v3.2 §4.6 시드 데이터 기준.
     * AI 이용권 4종(category=COUPON) + 영화티켓응모권 1종(APPLY) + 아바타 1종(avatar) + 배지 1종(coupon).</p>
     *
     * <h4>v3.2 AI 이용권 가격 (1P=10원 통일)</h4>
     * <ul>
     *   <li>1회: 10P (100원) — 볼륨 할인 없음, 모두 100원/회</li>
     *   <li>5회: 50P (500원)</li>
     *   <li>20회: 200P (2,000원)</li>
     *   <li>50회: 500P (5,000원)</li>
     * </ul>
     *
     * @return 초기화할 PointItem 엔티티 목록 (가격 오름차순)
     */
    private List<PointItem> buildDefaultItems() {
        return List.of(

                // ── AI 이용권 (category="COUPON") ────────────────────────────
                // v3.2: 카테고리명 "ai" → "COUPON" (이용권 역할 명확화)
                // grade 일일 한도 + 구독 보너스 풀 소진 후 사용 (source="PURCHASED")
                // UserPoint.addPurchasedTokens(count) 로 횟수 적립
                // UserPoint.consumePurchasedToken() 으로 1회씩 차감 (monthly_coupon_used++ 포함)

                // AI 이용권 1회 — 10P (100원, 100원/회)
                // 단발성, 긴급 사용. 포인트가 충분치 않은 경우 선택지
                PointItem.builder()
                        .itemName("AI 이용권 1회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 1회. 구매 후 30일 이내 사용. "
                                + "단발성·긴급 사용 시 적합. 구독 가입 시 월 30~67회 기본 포함.")
                        .itemPrice(10)                  // v3.2: 10P = 100원 (1P=10원 통일)
                        .itemCategory("COUPON")         // v3.2: ai → COUPON
                        .isActive(true)
                        .build(),

                // AI 이용권 5회 — 50P (500원, 100원/회)
                // 소량 패키지. 1회권(10P) × 5 = 50P로 볼륨 할인 없음 (1P=10원 통일)
                PointItem.builder()
                        .itemName("AI 이용권 5회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 5회. 구매 후 30일 이내 사용. "
                                + "월간 Basic 구독(2,900원/30회=96.7원/회) 대비 약 3% 비싸나 포인트 지급 없음 — 구독 권장.")
                        .itemPrice(50)                  // v3.2: 50P = 500원 (1P=10원 통일)
                        .itemCategory("COUPON")         // v3.2: ai → COUPON
                        .isActive(true)
                        .build(),

                // AI 이용권 20회 — 200P (2,000원, 100원/회)
                // 중간 패키지. 볼륨 할인 없음 (1P=10원 통일)
                PointItem.builder()
                        .itemName("AI 이용권 20회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 20회. 구매 후 30일 이내 사용. "
                                + "월간 Premium 구독(5,900원/60회=98.3원/회)보다 단가 약간 저렴하나 포인트 지급 없음 — 구독 권장.")
                        .itemPrice(200)                 // v3.2: 200P = 2,000원 (1P=10원 통일)
                        .itemCategory("COUPON")         // v3.2: ai → COUPON
                        .isActive(true)
                        .build(),

                // AI 이용권 50회 — 500P (5,000원, 100원/회)
                // 대량 패키지. 볼륨 할인 없음 (1P=10원 통일)
                PointItem.builder()
                        .itemName("AI 이용권 50회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 50회. 구매 후 60일 이내 사용. "
                                + "구독 대비 포인트 지급 없음 — 구독 전환 권장.")
                        .itemPrice(500)                 // v3.2: 500P = 5,000원 (1P=10원 통일)
                        .itemCategory("COUPON")         // v3.2: ai → COUPON
                        .isActive(true)
                        .build(),

                // ── 응모권 (category="APPLY") ─────────────────────────────────
                // v3.2 신규: 영화 티켓 응모권 — 소비처 다양화
                // CGV/롯데시네마 등 제휴 영화 티켓 추첨 참여 1회권

                // 영화 티켓 응모권 — 150P (1,500원 환산)
                PointItem.builder()
                        .itemName("영화 티켓 응모권")
                        .itemDescription("CGV, 롯데시네마 등 제휴 영화관 무료 티켓 추첨에 참여할 수 있는 응모권 1회. "
                                + "매월 말 추첨 진행. 당첨 시 문자 발송.")
                        .itemPrice(150)                 // v3.2: 150P = 1,500원 환산
                        .itemCategory("APPLY")          // v3.2 신규 카테고리
                        .isActive(true)
                        .build(),

                // ── 아바타 (category="avatar") ───────────────────────────
                // 프로필 꾸미기 아이템 — AI 쿼터와 무관한 순수 아이템 소비

                // 몽글이 프로필 아바타 — 150P
                PointItem.builder()
                        .itemName("프로필 아바타 - 몽글이")
                        .itemDescription("몽글픽 마스코트 '몽글이' 캐릭터 프로필 이미지. 적용 후 프로필 사진으로 영구 사용 가능.")
                        .itemPrice(150)                 // 150P = 1,500원 환산
                        .itemCategory("avatar")
                        .isActive(true)
                        .build(),

                // ── 쿠폰 (category="coupon") ──────────────────────────────
                // 기간 한정 배지/쿠폰 — 소비 후 만료

                // 프리미엄 배지 (1개월) — 100P
                PointItem.builder()
                        .itemName("프리미엄 배지 (1개월)")
                        .itemDescription("1개월간 프로필에 프리미엄 배지가 표시되어 다른 사용자와 차별화. 만료 후 자동 제거.")
                        .itemPrice(100)                 // 100P = 1,000원 환산
                        .itemCategory("coupon")
                        .isActive(true)
                        .build()
        );
    }
}
