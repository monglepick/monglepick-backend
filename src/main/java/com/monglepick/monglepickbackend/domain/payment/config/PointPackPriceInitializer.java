package com.monglepick.monglepickbackend.domain.payment.config;

import com.monglepick.monglepickbackend.domain.payment.entity.PointPackPrice;
import com.monglepick.monglepickbackend.domain.payment.repository.PointPackPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포인트팩 가격 마스터 초기 데이터 적재기.
 *
 * <p>애플리케이션 시작 시 point_pack_prices 테이블에 시드 데이터가 없으면 INSERT한다.
 * 설계서 v3.2 §13.1, 엑셀 Table 50 기준.</p>
 *
 * <h3>v3.2 포인트팩 6종 (1P = 10원 통일)</h3>
 * <table border="1">
 *   <tr><th>팩명</th><th>가격(원)</th><th>포인트(P)</th></tr>
 *   <tr><td>100 포인트</td><td>1,000원</td><td>100P</td></tr>
 *   <tr><td>200 포인트</td><td>2,000원</td><td>200P</td></tr>
 *   <tr><td>500 포인트</td><td>5,000원</td><td>500P</td></tr>
 *   <tr><td>1,000 포인트</td><td>10,000원</td><td>1,000P</td></tr>
 *   <tr><td>5,000 포인트</td><td>50,000원</td><td>5,000P</td></tr>
 *   <tr><td>10,000 포인트</td><td>100,000원</td><td>10,000P</td></tr>
 * </table>
 *
 * <h3>멱등 전략</h3>
 * <p>테이블에 레코드가 하나라도 있으면 INSERT하지 않는다.
 * 포인트팩 구성이 변경된 경우 기존 데이터를 수동 삭제 후 재기동하거나 DB 마이그레이션 스크립트로 처리한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PointPackPriceInitializer implements ApplicationRunner {

    /** 포인트팩 가격 마스터 리포지토리 — point_pack_prices 테이블 접근 */
    private final PointPackPriceRepository repository;

    /**
     * 애플리케이션 시작 시 포인트팩 가격 마스터 시드 데이터를 적재한다.
     *
     * <p>테이블에 기존 데이터가 없는 경우에만 6개 포인트팩을 INSERT한다.
     * 이미 존재하면 건너뛰어 멱등성을 보장한다.</p>
     *
     * @param args 애플리케이션 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        /* 이미 데이터가 존재하면 건너뜀 (멱등) */
        if (repository.count() > 0) {
            log.info("포인트팩 가격 마스터 이미 존재 — 초기화 건너뜀");
            return;
        }

        List<PointPackPrice> packs = buildDefaultPacks();
        repository.saveAll(packs);
        log.info("포인트팩 가격 마스터 초기화 완료 — {}개 INSERT (v3.2, 1P=10원)", packs.size());
    }

    /**
     * v3.2 기본 포인트팩 6종을 PointPackPrice 엔티티 리스트로 생성한다.
     *
     * <p>설계서 v3.2 §13.1, 엑셀 Table 50 기준.
     * 1P = 10원 통일. 볼륨 할인 없음 (단일 환산율).</p>
     *
     * @return 초기화할 PointPackPrice 엔티티 목록 (가격 오름차순)
     */
    private List<PointPackPrice> buildDefaultPacks() {
        return List.of(

                // ── 100 포인트 ──────────────────────────────
                // 1,000원 / 100P (1P=10원)
                // 소액 결제, AI 이용권 1회(10P) × 10회 분량
                PointPackPrice.builder()
                        .packName("100 포인트")
                        .price(1_000)           // v3.2: 1,000원 (1P=10원 통일)
                        .pointsAmount(100)
                        .sortOrder(0)
                        .build(),

                // ── 200 포인트 ──────────────────────────────
                // 2,000원 / 200P (1P=10원)
                // AI 이용권 5회(50P) × 4회 분량
                PointPackPrice.builder()
                        .packName("200 포인트")
                        .price(2_000)           // v3.2: 2,000원 (1P=10원 통일)
                        .pointsAmount(200)
                        .sortOrder(1)
                        .build(),

                // ── 500 포인트 ──────────────────────────────
                // 5,000원 / 500P (1P=10원)
                // AI 이용권 20회(200P) × 2.5회 분량
                PointPackPrice.builder()
                        .packName("500 포인트")
                        .price(5_000)           // v3.2: 5,000원 (1P=10원 통일)
                        .pointsAmount(500)
                        .sortOrder(2)
                        .build(),

                // ── 1,000 포인트 ────────────────────────────
                // 10,000원 / 1,000P (1P=10원)
                // AI 이용권 50회(500P) × 2회 분량
                PointPackPrice.builder()
                        .packName("1,000 포인트")
                        .price(10_000)          // v3.2: 10,000원 (1P=10원 통일)
                        .pointsAmount(1_000)
                        .sortOrder(3)
                        .build(),

                // ── 5,000 포인트 ────────────────────────────
                // 50,000원 / 5,000P (1P=10원)
                // 대량 구매, AI 이용권 50회(500P) × 10회 분량
                PointPackPrice.builder()
                        .packName("5,000 포인트")
                        .price(50_000)          // v3.2: 50,000원 (1P=10원 통일)
                        .pointsAmount(5_000)
                        .sortOrder(4)
                        .build(),

                // ── 10,000 포인트 ───────────────────────────
                // 100,000원 / 10,000P (1P=10원)
                // 최대 패키지, 파워 유저용
                PointPackPrice.builder()
                        .packName("10,000 포인트")
                        .price(100_000)         // v3.2: 100,000원 (1P=10원 통일)
                        .pointsAmount(10_000)
                        .sortOrder(5)
                        .build()
        );
    }
}
