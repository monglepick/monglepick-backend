package com.monglepick.monglepickbackend.domain.search.config;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCategory;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * worldcup_category 기본 시드 적재기.
 */
@Slf4j
@Component
@Order(130)
@RequiredArgsConstructor
public class WorldcupCategoryInitializer implements ApplicationRunner {

    private final WorldcupCategoryRepository worldcupCategoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (worldcupCategoryRepository.existsByCategoryCode("DEFAULT")) {
            return;
        }

        WorldcupCategory saved = worldcupCategoryRepository.save(
                WorldcupCategory.builder()
                        .categoryCode("DEFAULT")
                        .categoryName("기본 카테고리")
                        .description("별도 테마를 지정하지 않은 기본 월드컵 후보 카테고리")
                        .adminNote("시스템 기본 카테고리")
                        .enabled(true)
                        .displayOrder(0)
                        .build()
        );
        log.info("[WorldcupCategoryInitializer] 기본 카테고리 시드 완료 — id={}, code={}",
                saved.getCategoryId(), saved.getCategoryCode());
    }
}
