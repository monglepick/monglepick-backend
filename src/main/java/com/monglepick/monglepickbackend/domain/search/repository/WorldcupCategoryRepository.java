package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 월드컵 후보 카테고리 마스터 레포지토리.
 */
public interface WorldcupCategoryRepository extends JpaRepository<WorldcupCategory, Long> {

    boolean existsByCategoryCode(String categoryCode);

    Optional<WorldcupCategory> findByCategoryCode(String categoryCode);

    Page<WorldcupCategory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<WorldcupCategory> findAllByOrderByCategoryNameAsc();
}
