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
 * 설계서 v2.3 §13.1 시드 데이터 기준.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PointPackPriceInitializer implements ApplicationRunner {

    private final PointPackPriceRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        /* 이미 데이터가 존재하면 건너뜀 (멱등) */
        if (repository.count() > 0) {
            log.info("포인트팩 가격 마스터 이미 존재 — 초기화 건너뜀");
            return;
        }

        List<PointPackPrice> packs = List.of(
                PointPackPrice.builder()
                        .packName("100 포인트")
                        .amount(1_000).pointsAmount(100).sortOrder(0).build(),
                PointPackPrice.builder()
                        .packName("500 포인트")
                        .amount(4_500).pointsAmount(500).sortOrder(1).build(),
                PointPackPrice.builder()
                        .packName("1,000 포인트")
                        .amount(8_000).pointsAmount(1_000).sortOrder(2).build(),
                PointPackPrice.builder()
                        .packName("3,000 포인트")
                        .amount(22_000).pointsAmount(3_000).sortOrder(3).build(),
                PointPackPrice.builder()
                        .packName("5,000 포인트")
                        .amount(35_000).pointsAmount(5_000).sortOrder(4).build()
        );

        repository.saveAll(packs);
        log.info("포인트팩 가격 마스터 초기화 완료 — {}개 INSERT", packs.size());
    }
}
