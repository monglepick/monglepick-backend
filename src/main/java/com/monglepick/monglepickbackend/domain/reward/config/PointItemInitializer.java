package com.monglepick.monglepickbackend.domain.reward.config;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemCategory;
import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
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
import java.util.Map;

/**
 * 포인트 상점 아이템 초기 데이터 적재기 — point_items 테이블 시드 데이터 삽입 및 정규화.
 *
 * <p>애플리케이션 시작 시 {@code point_items} 테이블을 3단계로 정리한다:</p>
 * <ol>
 *   <li><b>정규화</b> — 카테고리 대소문자 통일 (예: "COUPON" → "coupon"), 신규 {@link PointItemType}
 *       필드를 이름 매핑으로 채움. ddl-auto=update로는 자동 이관되지 않는 기존 행 보정.</li>
 *   <li><b>구버전 시드 비활성화</b> — data.sql에서 주입되던 5종({@code "ai_feature"},
 *       {@code "profile"}, {@code "roadmap"} 카테고리)을 {@code is_active=false}로 전환하여
 *       사용자 노출을 차단한다. 포인트 이력 보존을 위해 DELETE는 하지 않는다.</li>
 *   <li><b>시드 적재</b> — 설계서 v3.2 7종을 itemName 기준 멱등 INSERT.</li>
 * </ol>
 *
 * <h3>v3.2 AI 3-소스 모델 — 아이템 설계 원칙</h3>
 * <p>포인트 상점에서 "AI 이용권"을 판매하여 grade 일일 한도와 구독 보너스 풀이
 * 모두 소진된 사용자에게 추가 AI 사용 경로를 제공한다 (source="PURCHASED").</p>
 *
 * <h3>시드 데이터 (15개 아이템, 모두 소문자 카테고리)</h3>
 * <p>2026-04-27 확장: 아바타 4종 + 배지 3종 추가. 신규 시드는 모두 {@link PointItemType#AVATAR_GENERIC}
 * / {@link PointItemType#BADGE_GENERIC} 으로 dispatch 하므로 향후 enum 추가 없이 행 데이터만 늘려도 운영 가능.</p>
 * <table border="1">
 *   <tr><th>아이템명</th><th>카테고리</th><th>itemType</th><th>가격</th><th>유효기간</th></tr>
 *   <tr><td>AI 이용권 1회</td><td>coupon</td><td>AI_TOKEN_1</td><td>10P</td><td>30일</td></tr>
 *   <tr><td>AI 이용권 5회</td><td>coupon</td><td>AI_TOKEN_5</td><td>50P</td><td>30일</td></tr>
 *   <tr><td>AI 이용권 20회</td><td>coupon</td><td>AI_TOKEN_20</td><td>200P</td><td>30일</td></tr>
 *   <tr><td>AI 이용권 50회</td><td>coupon</td><td>AI_TOKEN_50</td><td>500P</td><td>60일</td></tr>
 *   <tr><td>영화 티켓 응모권</td><td>apply</td><td>APPLY_MOVIE_TICKET</td><td>150P</td><td>월말</td></tr>
 *   <tr><td>프로필 아바타 - 몽글이</td><td>avatar</td><td>AVATAR_MONGLE</td><td>150P</td><td>영구</td></tr>
 *   <tr><td>프로필 아바타 - 클래퍼보드</td><td>avatar</td><td>AVATAR_GENERIC</td><td>150P</td><td>영구</td></tr>
 *   <tr><td>프로필 아바타 - 필름 릴</td><td>avatar</td><td>AVATAR_GENERIC</td><td>180P</td><td>영구</td></tr>
 *   <tr><td>프로필 아바타 - 팝콘 박스</td><td>avatar</td><td>AVATAR_GENERIC</td><td>150P</td><td>영구</td></tr>
 *   <tr><td>프로필 아바타 - 별 평론가</td><td>avatar</td><td>AVATAR_GENERIC</td><td>200P</td><td>영구</td></tr>
 *   <tr><td>프리미엄 배지 (1개월)</td><td>badge</td><td>BADGE_PREMIUM</td><td>100P</td><td>30일</td></tr>
 *   <tr><td>시네필 배지 (3개월)</td><td>badge</td><td>BADGE_GENERIC</td><td>180P</td><td>90일</td></tr>
 *   <tr><td>얼리어답터 배지 (영구)</td><td>badge</td><td>BADGE_GENERIC</td><td>500P</td><td>영구</td></tr>
 *   <tr><td>30일 연속 출석 배지 (1개월)</td><td>badge</td><td>BADGE_GENERIC</td><td>80P</td><td>30일</td></tr>
 *   <tr><td>퀴즈 힌트</td><td>hint</td><td>QUIZ_HINT</td><td>50P</td><td>영구</td></tr>
 * </table>
 *
 * <p>도장깨기(roadmap)는 영화 코스 시청·인증 챌린지 시스템으로 "힌트" 메커니즘이 적용되지 않는다.
 * 따라서 위 시드의 "퀴즈 힌트"는 퀴즈(객관식 영화 지식 게임) 도메인 전용이다.</p>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(4)} — GradeInitializer, RewardPolicyInitializer, SubscriptionPlanInitializer 이후 실행.</p>
 *
 * @see PointItem 포인트 아이템 엔티티
 * @see PointItemCategory 카테고리 상수
 * @see PointItemType 타입 ENUM
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class PointItemInitializer implements ApplicationRunner {

    /** 포인트 아이템 리포지토리 — point_items 테이블 접근 */
    private final PointItemRepository pointItemRepository;

    /**
     * 구버전 카테고리(대문자/레거시) → 정규 소문자 카테고리 매핑.
     *
     * <p>ddl-auto=update로는 데이터 이관이 자동으로 일어나지 않으므로,
     * 기동 시 이 매핑 기준으로 UPDATE를 수행한다.</p>
     */
    private static final Map<String, String> CATEGORY_NORMALIZATION = Map.of(
            "COUPON", PointItemCategory.COUPON,
            "APPLY", PointItemCategory.APPLY,
            "AI", PointItemCategory.COUPON,       // 구버전 data.sql "ai" / Initializer "AI"
            "ai", PointItemCategory.COUPON
    );

    /**
     * data.sql 기반 구버전 시드 이름 → is_active=false 대상 (카테고리 전환 불가능한 legacy).
     *
     * <p>v3.2 시드와 이름이 완전히 다르므로 카테고리 정규화로는 복구할 수 없다.
     * 포인트 이력 보존을 위해 삭제 대신 비활성화.</p>
     */
    /*
     * "도장깨기 힌트" 가 여기에 포함된 이유:
     * - 도장깨기(roadmap)는 코스 영화를 시청·인증하는 챌린지 시스템으로 "힌트" 개념이 적용되지 않는다.
     * - 레거시 시드의 "도장깨기 힌트" 는 잘못된 명명이었으며, v2(C 방향)에서 비활성화 처리된 게 결과적으로 옳다.
     * - 힌트 메커니즘은 퀴즈(객관식 영화 지식 게임) 도메인 전용이며, "퀴즈 힌트" 라는 새 시드로 별도 등록된다(아래 buildCanonicalItems()).
     */
    private static final List<String> LEGACY_ITEM_NAMES_TO_DEACTIVATE = List.of(
            "AI 추천 1회",
            "AI 추천 5회 팩",
            "프로필 테마",
            "칭호 변경",
            "도장깨기 힌트"
    );

    /**
     * v3.2 시드 아이템 이름 → PointItemType 매핑 (신규 필드 채우기용).
     *
     * <p>ddl-auto=update로 item_type 컬럼이 추가되면 기존 v3.2 시드 행의 값은 NULL이 된다.
     * 기동 시 이 맵을 순회하며 itemName으로 조회 후 itemType/amount/durationDays/imageUrl을 채운다.</p>
     */
    private static final Map<String, PointItemType> ITEM_NAME_TO_TYPE = Map.of(
            "AI 이용권 1회", PointItemType.AI_TOKEN_1,
            "AI 이용권 5회", PointItemType.AI_TOKEN_5,
            "AI 이용권 20회", PointItemType.AI_TOKEN_20,
            "AI 이용권 50회", PointItemType.AI_TOKEN_50,
            "영화 티켓 응모권", PointItemType.APPLY_MOVIE_TICKET,
            "프로필 아바타 - 몽글이", PointItemType.AVATAR_MONGLE,
            "프리미엄 배지 (1개월)", PointItemType.BADGE_PREMIUM,
            /* 2026-04-14 B' 후속: 퀴즈 힌트 신규 시드. 도장깨기 힌트(레거시)와 무관, 퀴즈 도메인 전용. */
            "퀴즈 힌트", PointItemType.QUIZ_HINT
    );

    /**
     * 애플리케이션 시작 시 3단계로 point_items 테이블을 정리한다.
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("포인트 상점 아이템 초기화 시작 (v3.2 + C 방향 확장, 2026-04-14)");

        // 1) 카테고리 대소문자 정규화 + itemType/amount/durationDays/imageUrl 보정
        normalizeLegacyRows();

        // 2) data.sql 레거시 시드 5종 비활성화 (삭제 대신)
        deactivateLegacySeeds();

        // 3) v3.2 7종 시드 멱등 INSERT
        seedCanonicalItems();

        log.info("포인트 상점 아이템 초기화 완료");
    }

    /**
     * 1단계: 기존 행을 정규 카테고리/신규 컬럼으로 업데이트.
     *
     * <p>ddl-auto=update는 컬럼 추가만 하고 데이터는 건드리지 않으므로,
     * 기동 시점에 기존 시드 행들을 다음과 같이 보정한다:</p>
     * <ul>
     *   <li>itemCategory 대문자/"ai" → 소문자 정규값으로 교체</li>
     *   <li>ITEM_NAME_TO_TYPE에 매칭되는 행은 itemType/amount/durationDays/imageUrl을 채움</li>
     * </ul>
     */
    private void normalizeLegacyRows() {
        List<PointItem> all = pointItemRepository.findAll();
        int updated = 0;

        for (PointItem row : all) {
            boolean dirty = false;

            /* 카테고리 정규화 — "COUPON" → "coupon" 등 */
            String normalizedCategory = CATEGORY_NORMALIZATION.get(row.getItemCategory());
            if (normalizedCategory != null && !normalizedCategory.equals(row.getItemCategory())) {
                /* @Setter 없는 엔티티이므로 Builder-기반 재구성 대신 JPQL UPDATE 또는 Reflection 회피하고,
                   PointItem 엔티티에 setter 없이 업데이트하려면 새 엔티티로 교체 저장이 필요.
                   여기서는 간단히 id·created_*를 유지한 채 빌더로 재구성 후 save() 수행. */
                PointItem replaced = rebuildFrom(row, normalizedCategory, null);
                pointItemRepository.save(replaced);
                updated++;
                dirty = true;
                log.info("카테고리 정규화: id={}, name={}, {} → {}",
                        row.getPointItemId(), row.getItemName(),
                        row.getItemCategory(), normalizedCategory);
                continue; // 아래의 itemType 보정은 다음 기동 때 처리 (save 후 1차 캐시 혼선 방지)
            }

            /* itemType 보정 — v3.2 시드인데 NULL이면 이름 매핑으로 채움 */
            PointItemType mappedType = ITEM_NAME_TO_TYPE.get(row.getItemName());
            if (mappedType != null && row.getItemType() == null) {
                PointItem replaced = rebuildFrom(row, null, mappedType);
                pointItemRepository.save(replaced);
                updated++;
                dirty = true;
                log.info("itemType 보정: id={}, name={} → type={}",
                        row.getPointItemId(), row.getItemName(), mappedType.name());
                continue;
            }

            /* imageUrl 마이그레이션 — 과거 시드의 .png 를 .svg 로 일회성 교체.
               itemType 이 이미 채워져 있고 카테고리도 정상이지만 이미지 경로만 옛날인 경우. */
            String currentImage = row.getImageUrl();
            if (currentImage != null && currentImage.endsWith(".png")) {
                /* rebuildFrom 의 마이그레이션 분기를 그대로 활용 — newType=null 이지만
                   imageUrl 변환 로직은 항상 실행됨 */
                PointItem replaced = rebuildFrom(row, null, null);
                pointItemRepository.save(replaced);
                updated++;
                dirty = true;
                log.info("imageUrl 마이그레이션: id={}, name={}, {} → {}",
                        row.getPointItemId(), row.getItemName(),
                        currentImage, replaced.getImageUrl());
            }
        }

        if (updated == 0) {
            log.debug("정규화 대상 없음 — 모든 행이 최신 스키마와 일치");
        } else {
            log.info("정규화 완료: {}개 행 업데이트", updated);
        }
    }

    /**
     * 2단계: data.sql 시절 구버전 이름으로 남아있는 5종을 비활성화.
     *
     * <p>이름이 정확히 일치하는 레거시 시드만 대상이며, 이미 비활성화되어 있으면 건너뛴다.
     * DELETE하지 않는 이유: points_history에 "item-{id}" referenceId가 남아있어
     * 무결성 및 감사 추적을 유지해야 함.</p>
     */
    private void deactivateLegacySeeds() {
        int deactivated = 0;
        for (String legacyName : LEGACY_ITEM_NAMES_TO_DEACTIVATE) {
            List<PointItem> matches = pointItemRepository.findAll().stream()
                    .filter(p -> legacyName.equals(p.getItemName()) && Boolean.TRUE.equals(p.getIsActive()))
                    .toList();

            for (PointItem legacy : matches) {
                PointItem deactivatedCopy = PointItem.builder()
                        .pointItemId(legacy.getPointItemId())
                        .itemName(legacy.getItemName())
                        .itemDescription(legacy.getItemDescription())
                        .itemPrice(legacy.getItemPrice())
                        .itemCategory(legacy.getItemCategory())
                        .itemType(legacy.getItemType())
                        .amount(legacy.getAmount())
                        .durationDays(legacy.getDurationDays())
                        .imageUrl(legacy.getImageUrl())
                        .isActive(false) // ← 비활성화
                        .build();
                pointItemRepository.save(deactivatedCopy);
                deactivated++;
                log.info("레거시 시드 비활성화: id={}, name={}", legacy.getPointItemId(), legacy.getItemName());
            }
        }

        if (deactivated == 0) {
            log.debug("비활성화 대상 레거시 시드 없음");
        } else {
            log.info("레거시 시드 비활성화 완료: {}개", deactivated);
        }
    }

    /**
     * 3단계: v3.2 표준 7종을 itemName 기준으로 멱등 INSERT.
     */
    private void seedCanonicalItems() {
        List<PointItem> items = buildCanonicalItems();
        int inserted = 0;
        int skipped = 0;

        for (PointItem item : items) {
            boolean exists = pointItemRepository.findAll().stream()
                    .anyMatch(existing -> existing.getItemName().equals(item.getItemName()));
            if (exists) {
                skipped++;
                continue;
            }
            pointItemRepository.save(item);
            inserted++;
            log.info("v3.2 시드 INSERT: name={}, category={}, type={}, price={}P",
                    item.getItemName(), item.getItemCategory(),
                    item.getItemType() != null ? item.getItemType().name() : "null",
                    item.getItemPrice());
        }

        log.info("v3.2 시드 적재 완료: INSERT={}개, 건너뜀={}개", inserted, skipped);
    }

    /**
     * 기존 엔티티를 새 카테고리·타입으로 재구성한다 — @Setter 없는 불변 엔티티 대응.
     *
     * @param src            기존 엔티티
     * @param newCategory    새 카테고리 (null이면 기존 값 유지)
     * @param newType        새 itemType (null이면 기존 값 유지)
     * @return id/가격/설명 등은 유지한 새 PointItem 인스턴스 (save 시 UPDATE 수행됨)
     */
    private PointItem rebuildFrom(PointItem src, String newCategory, PointItemType newType) {
        PointItemType typeToSet = newType != null ? newType : src.getItemType();
        Integer amount = src.getAmount();
        Integer durationDays = src.getDurationDays();
        String imageUrl = src.getImageUrl();

        /* itemType을 새로 매핑하는 경우 — 기본값도 함께 채운다 */
        if (newType != null) {
            if (amount == null) amount = newType.getAmount();
            if (durationDays == null) durationDays = newType.getDurationDays();
            if (imageUrl == null) imageUrl = defaultImageUrlFor(newType);
        }

        /* 2026-04-14: 과거 .png 경로로 시드된 행을 .svg 로 마이그레이션 (일회성).
           imageUrl 이 .png 로 끝나면 동일 이름의 .svg 로 교체. 디자인 정적 리소스가 SVG 로 통일됨. */
        if (imageUrl != null && imageUrl.endsWith(".png")) {
            imageUrl = imageUrl.substring(0, imageUrl.length() - 4) + ".svg";
        }

        return PointItem.builder()
                .pointItemId(src.getPointItemId())
                .itemName(src.getItemName())
                .itemDescription(src.getItemDescription())
                .itemPrice(src.getItemPrice())
                .itemCategory(newCategory != null ? newCategory : src.getItemCategory())
                .itemType(typeToSet)
                .amount(amount)
                .durationDays(durationDays)
                .imageUrl(imageUrl)
                .isActive(src.getIsActive())
                .build();
    }

    /**
     * itemType별 기본 이미지 경로 반환.
     *
     * <p>아바타·배지는 프론트엔드 정적 리소스를 참조한다. NULL이어도 무방 (프론트가 fallback 처리).</p>
     */
    private String defaultImageUrlFor(PointItemType type) {
        /* 2026-04-14: 정적 리소스를 SVG 로 제공 (monglepick-client/public/avatars,/badges).
           PNG 대신 SVG 사용 — 크기 작고 해상도 무관, 디자이너 교체 시 파일 덮어쓰기만으로 적용. */
        return switch (type) {
            case AVATAR_MONGLE -> "/avatars/mongle.svg";
            case BADGE_PREMIUM -> "/badges/premium.svg";
            default -> null;
        };
    }

    /**
     * v3.2 표준 7종 시드 정의.
     *
     * <p>설계서 §4.6 + §16 "포인트 소비처" 기준.
     * AI 이용권 4종(coupon) + 응모권 1종(apply) + 아바타 1종(avatar) + 배지 1종(badge).</p>
     *
     * @return 시드 엔티티 리스트
     */
    private List<PointItem> buildCanonicalItems() {
        return List.of(
                // ── AI 이용권 (category="coupon", itemType=AI_TOKEN_*) ──
                PointItem.builder()
                        .itemName("AI 이용권 1회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 1회. 구매 후 30일 이내 사용. "
                                + "단발성·긴급 사용 시 적합. 구독 가입 시 월 30~67회 기본 포함.")
                        .itemPrice(10)
                        .itemCategory(PointItemCategory.COUPON)
                        .itemType(PointItemType.AI_TOKEN_1)
                        .amount(1)
                        .durationDays(30)
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("AI 이용권 5회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 5회. 구매 후 30일 이내 사용. "
                                + "월간 Basic 구독(2,900원/30회=96.7원/회) 대비 약 3% 비싸나 포인트 지급 없음 — 구독 권장.")
                        .itemPrice(50)
                        .itemCategory(PointItemCategory.COUPON)
                        .itemType(PointItemType.AI_TOKEN_5)
                        .amount(5)
                        .durationDays(30)
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("AI 이용권 20회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 20회. 구매 후 30일 이내 사용. "
                                + "월간 Premium 구독(5,900원/60회=98.3원/회)보다 단가 약간 저렴하나 포인트 지급 없음 — 구독 권장.")
                        .itemPrice(200)
                        .itemCategory(PointItemCategory.COUPON)
                        .itemType(PointItemType.AI_TOKEN_20)
                        .amount(20)
                        .durationDays(30)
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("AI 이용권 50회")
                        .itemDescription("일일 AI 추천 한도 초과 시 사용할 수 있는 추가 이용권 50회. 구매 후 60일 이내 사용. "
                                + "구독 대비 포인트 지급 없음 — 구독 전환 권장.")
                        .itemPrice(500)
                        .itemCategory(PointItemCategory.COUPON)
                        .itemType(PointItemType.AI_TOKEN_50)
                        .amount(50)
                        .durationDays(60)
                        .isActive(true)
                        .build(),

                // ── 응모권 (category="apply") ──
                PointItem.builder()
                        .itemName("영화 티켓 응모권")
                        .itemDescription("CGV, 롯데시네마 등 제휴 영화관 무료 티켓 추첨에 참여할 수 있는 응모권 1회. "
                                + "매월 말 추첨 진행. 당첨 시 문자 발송.")
                        .itemPrice(150)
                        .itemCategory(PointItemCategory.APPLY)
                        .itemType(PointItemType.APPLY_MOVIE_TICKET)
                        .amount(1)
                        .durationDays(null)
                        .isActive(true)
                        .build(),

                // ── 아바타 (category="avatar") ──
                PointItem.builder()
                        .itemName("프로필 아바타 - 몽글이")
                        .itemDescription("몽글픽 마스코트 '몽글이' 캐릭터 프로필 이미지. 적용 후 프로필 사진으로 영구 사용 가능.")
                        .itemPrice(150)
                        .itemCategory(PointItemCategory.AVATAR)
                        .itemType(PointItemType.AVATAR_MONGLE)
                        .amount(1)
                        .durationDays(null)
                        .imageUrl("/avatars/mongle.svg")
                        .isActive(true)
                        .build(),

                // ── 아바타 신규 컬렉션 (2026-04-27 — AVATAR_GENERIC 시리즈) ──
                // itemType=AVATAR_GENERIC 으로 통일 — Admin UI 에서 추가 등록 시에도 같은 패턴 사용.
                // 기존 AVATAR_MONGLE 처럼 enum 상수를 늘리지 않고 행 데이터(이미지/이름)만 차이.
                PointItem.builder()
                        .itemName("프로필 아바타 - 클래퍼보드")
                        .itemDescription("영화 제작 현장의 클래퍼보드 캐릭터. 클래식한 흑백 슬레이트 디자인으로 영화 마니아에게 추천.")
                        .itemPrice(150)
                        .itemCategory(PointItemCategory.AVATAR)
                        .itemType(PointItemType.AVATAR_GENERIC)
                        .amount(1)
                        .durationDays(null)
                        .imageUrl("/avatars/clapperboard.svg")
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("프로필 아바타 - 필름 릴")
                        .itemDescription("구식 영사기 휠 디자인. 보라/은빛 톤의 빈티지 레트로 컬렉션.")
                        .itemPrice(180)
                        .itemCategory(PointItemCategory.AVATAR)
                        .itemType(PointItemType.AVATAR_GENERIC)
                        .amount(1)
                        .durationDays(null)
                        .imageUrl("/avatars/film_reel.svg")
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("프로필 아바타 - 팝콘 박스")
                        .itemDescription("영화관 클래식 팝콘 박스. 빨강/흰 줄무늬와 솟아오른 팝콘 알갱이가 시원한 분위기.")
                        .itemPrice(150)
                        .itemCategory(PointItemCategory.AVATAR)
                        .itemType(PointItemType.AVATAR_GENERIC)
                        .amount(1)
                        .durationDays(null)
                        .imageUrl("/avatars/popcorn_box.svg")
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("프로필 아바타 - 별 평론가")
                        .itemDescription("5점 만점의 별 평론가. 영화에 대한 진지한 시선을 좋아하는 분에게.")
                        .itemPrice(200)
                        .itemCategory(PointItemCategory.AVATAR)
                        .itemType(PointItemType.AVATAR_GENERIC)
                        .amount(1)
                        .durationDays(null)
                        .imageUrl("/avatars/star_critic.svg")
                        .isActive(true)
                        .build(),

                // ── 배지 (category="badge") ──
                PointItem.builder()
                        .itemName("프리미엄 배지 (1개월)")
                        .itemDescription("1개월간 프로필에 프리미엄 배지가 표시되어 다른 사용자와 차별화. 만료 후 자동 제거.")
                        .itemPrice(100)
                        .itemCategory(PointItemCategory.BADGE)
                        .itemType(PointItemType.BADGE_PREMIUM)
                        .amount(1)
                        .durationDays(30)
                        .imageUrl("/badges/premium.svg")
                        .isActive(true)
                        .build(),

                // ── 배지 신규 컬렉션 (2026-04-27 — BADGE_GENERIC 시리즈) ──
                // 유효기간을 행 단위로 가변 설정 — 30일/90일/영구 모두 가능.
                PointItem.builder()
                        .itemName("시네필 배지 (3개월)")
                        .itemDescription("영화 마니아 인증 배지. 90일간 프로필에 표시되며 다양한 영화 장르에 깊은 관심이 있는 사용자에게 추천.")
                        .itemPrice(180)
                        .itemCategory(PointItemCategory.BADGE)
                        .itemType(PointItemType.BADGE_GENERIC)
                        .amount(1)
                        .durationDays(90)
                        .imageUrl("/badges/cinephile.svg")
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("얼리어답터 배지 (영구)")
                        .itemDescription("초기 베타 회원에게 한정 발급된 영구 배지. 만료되지 않으며 한 번 보유하면 평생 착용 가능.")
                        .itemPrice(500)
                        .itemCategory(PointItemCategory.BADGE)
                        .itemType(PointItemType.BADGE_GENERIC)
                        .amount(1)
                        .durationDays(null)  // 영구
                        .imageUrl("/badges/early_bird.svg")
                        .isActive(true)
                        .build(),

                PointItem.builder()
                        .itemName("30일 연속 출석 배지 (1개월)")
                        .itemDescription("30일 출석 마일스톤 기념 배지. 30일간 프로필에 표시. 출석 챔피언의 인증.")
                        .itemPrice(80)
                        .itemCategory(PointItemCategory.BADGE)
                        .itemType(PointItemType.BADGE_GENERIC)
                        .amount(1)
                        .durationDays(30)
                        .imageUrl("/badges/streak_30.svg")
                        .isActive(true)
                        .build(),

                // ── 힌트 (category="hint") — 2026-04-14 B' 후속 신규 ──
                // 퀴즈 도메인 전용. 도장깨기는 힌트 메커니즘이 적용되지 않으므로 별도 시드 없음.
                // 현재는 인프라(보유·소비) 만 정비되어 있으며, QuizCard 의 힌트 사용 UX(50:50 룰 등)는
                // 별도 작업으로 분리되어 있다 — Backend 에 hint 사용 EP(POST /api/v1/quizzes/{id}/hint)와
                // 정답 유도 정책(오답 인덱스 반환 / 점수 차감 등) 정의 후 클라이언트 연동 예정.
                PointItem.builder()
                        .itemName("퀴즈 힌트")
                        .itemDescription("퀴즈 풀이 시 사용할 수 있는 힌트 1회. 사용 시 잔여 수량이 1 차감되고 0이 되면 자동 소진. "
                                + "도장깨기(영화 코스 챌린지)에는 적용되지 않습니다 — 퀴즈 전용.")
                        .itemPrice(50)
                        .itemCategory(PointItemCategory.HINT)
                        .itemType(PointItemType.QUIZ_HINT)
                        .amount(1)
                        .durationDays(null)  // 무기한 보유 (만료 배치 대상 아님)
                        .isActive(true)
                        .build()
        );
    }
}
