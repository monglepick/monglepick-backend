package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.PopularSearchKeyword;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 인기 검색어 마스터 레포지토리.
 *
 * <p>관리자 측 CRUD + 사용자 측 자동 집계 머지(자동 집계 결과에서 isExcluded=true 키워드 제외)
 * 두 가지 용도로 사용된다.</p>
 */
public interface PopularSearchKeywordRepository extends JpaRepository<PopularSearchKeyword, Long> {

    /** 키워드 존재 여부 (UNIQUE 제약 사전 검증용) */
    boolean existsByKeyword(String keyword);

    /** 키워드로 단건 조회 (사용자 측 isExcluded 필터링용) */
    Optional<PopularSearchKeyword> findByKeyword(String keyword);

    /** 페이징 조회 (관리자 화면) — manual_priority DESC, createdAt DESC */
    Page<PopularSearchKeyword> findAll(Pageable pageable);

    /** 제외(블랙리스트) 키워드 전체 조회 — 사용자 측 자동 집계 결과 필터링용 */
    List<PopularSearchKeyword> findByIsExcludedTrue();
}
