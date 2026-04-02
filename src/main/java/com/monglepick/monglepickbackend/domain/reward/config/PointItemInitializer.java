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
 * <h3>v3.0 AI 3-소스 모델 — 아이템 설계 원칙</h3>
 * <p>포인트 상점에서 "AI 이용권"을 판매하여 grade 일일 한도와 구독 보너스 풀이
 * 모두 소진된 사용자에게 추가 AI 사용 경로를 제공한다 (source="PURCHASED").</p>
 *
 * <h3>AI 이용권 가격 설계 (구독 유도) — v3.1 인상</h3>
 * <p>포인트팩 기준 1P = 10원으로 환산하면:</p>
 * <ul>
 *   <li>AI 이용권 5회 (200P = 2,000원) → 400원/회</li>
 *   <li>AI 이용권 20회 (700P = 7,000원) → 350원/회 (볼륨 할인)</li>
 *   <li>AI 이용권 50회 (1,600P = 16,000원) → 320원/회 (볼륨 할인)</li>
 *   <li>monthly_basic 구독 → 49원/회 (이용권 대비 85% 저렴)</li>
 *   <li>→ 이용권은 긴급/단발성 용도, 지속 사용자는 구독으로 강하게 유도 ✓</li>
 *   <li>인상 이유: 구 가격(80P/5회=16P/회)이 너무 저렴해 구독 가치 희석. 평균 사용자가
 *       월 리워드만으로 100회 추가 구매 가능 → 구독 불필요 상황 발생.</li>
 * </ul>
 *
 * <h3>시드 데이터 (5개 아이템)</h3>
 * <table border="1">
 *   <tr><th>아이템명</th><th>카테고리</th><th>가격</th><th>설명</th></tr>
 *   <tr><td>AI 이용권 5회</td><td>ai</td><td>200P</td><td>grade 한도 초과 AI 5회 (30일 유효)</td></tr>
 *   <tr><td>AI 이용권 20회</td><td>ai</td><td>700P</td><td>grade 한도 초과 AI 20회 (30일 유효)</td></tr>
 *   <tr><td>AI 이용권 50회</td><td>ai</td><td>1,600P</td><td>grade 한도 초과 AI 50회 (60일 유효)</td></tr>
 *   <tr><td>프로필 아바타 - 몽글이</td><td>avatar</td><td>150P</td><td>몽글이 캐릭터 프로필 이미지</td></tr>
 *   <tr><td>프리미엄 배지 (1개월)</td><td>coupon</td><td>100P</td><td>1개월간 프로필 프리미엄 배지</td></tr>
 * </table>
 *
 * <h3>AI 이용권 구매 시 처리 흐름</h3>
 * <ol>
 *   <li>사용자가 포인트 상점에서 AI 이용권 구매 → PointItemService에서 처리</li>
 *   <li>포인트 차감 (deductPoints) + {@code UserPoint.addPurchasedTokens(count)} 호출</li>
 *   <li>AI 요청 시 grade 한도/구독 보너스 소진 후 → {@code UserPoint.consumePurchasedToken()} 호출</li>
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
     * <p>5개 아이템을 itemName 기준으로 확인하여 없는 경우에만 INSERT한다.
     * 이미 존재하는 아이템은 건너뛰어 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("포인트 상점 아이템 초기화 시작 — point_items 테이블 시드 데이터 확인 (v3.0)");

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
     * v3.0 기본 포인트 상점 아이템 5개를 PointItem 엔티티 리스트로 생성한다.
     *
     * <p>설계서 v3.0 §4.10 시드 데이터 기준.
     * AI 이용권 3종 + 아바타 1종 + 배지 1종.</p>
     *
     * @return 초기화할 PointItem 엔티티 목록 (가격 오름차순)
     */
    private List<PointItem> buildDefaultItems() {
        return List.of(

                // ── AI 이용권 (category="ai") ────────────────────────────
                // grade 일일 한도 + 구독 보너스 풀 소진 후 사용 (source="PURCHASED")
                // UserPoint.addPurchasedTokens(count) 로 횟수 적립
                // UserPoint.consumePurchasedToken() 으로 1회씩 차감

                // AI 이용권 5회 — 200P (2,000원 환산, 400원/회)
                // 소량 구매 / 급할 때 단발성 용도. 구독 미가입자가 일시적으로 초과 시 사용
                PointItem.builder()
                        .itemName("AI 이용권 5회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 5회. 구매 후 30일 이내 사용. "
                                + "월간 Basic 구독(49원/회) 대비 약 8배 비싸므로, 지속 사용 시 구독 강력 권장.")
                        .itemPrice(200)                 // 200P = 2,000원 환산, 400원/회
                        .itemCategory("ai")
                        .isActive(true)
                        .build(),

                // AI 이용권 20회 — 700P (7,000원 환산, 350원/회)
                // 볼륨 할인 적용 (5회 대비 12.5% 저렴), 중간 사용자 대상
                PointItem.builder()
                        .itemName("AI 이용권 20회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 20회. 구매 후 30일 이내 사용. "
                                + "5회권(400원/회) 대비 볼륨 할인 적용(350원/회).")
                        .itemPrice(700)                 // 700P = 7,000원 환산, 350원/회
                        .itemCategory("ai")
                        .isActive(true)
                        .build(),

                // AI 이용권 50회 — 1,600P (16,000원 환산, 320원/회)
                // 최대 볼륨 할인. 그래도 구독보다 6배 이상 비싸므로 구독 유도 효과 강력
                PointItem.builder()
                        .itemName("AI 이용권 50회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 50회. 구매 후 60일 이내 사용. "
                                + "최대 볼륨 할인(320원/회). 월간 Basic 구독(49원/회) 대비 약 6.5배 비쌈 — 구독 전환 강력 권장.")
                        .itemPrice(1600)                // 1,600P = 16,000원 환산, 320원/회
                        .itemCategory("ai")
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
