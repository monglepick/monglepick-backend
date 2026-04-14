package com.monglepick.monglepickbackend.domain.support.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * FAQ JPA 레포지토리.
 *
 * <p>{@link SupportFaq} 엔티티에 대한 데이터 접근 계층.
 * 카테고리별 조회와 전체 목록 조회를 제공한다.</p>
 */
public interface SupportFaqRepository extends JpaRepository<SupportFaq, Long> {

    /**
     * 특정 카테고리의 FAQ 목록을 정렬 조건에 따라 조회한다.
     *
     * <p>사용 예: 카테고리 탭 선택 시 해당 카테고리의 FAQ를 최신순 또는
     * 도움됨 순으로 정렬하여 조회한다.</p>
     *
     * <pre>{@code
     * // 최신순 조회
     * faqRepository.findByCategory(SupportCategory.PAYMENT, Sort.by(Sort.Direction.DESC, "createdAt"));
     *
     * // 도움됨 많은 순 조회
     * faqRepository.findByCategory(SupportCategory.ACCOUNT, Sort.by(Sort.Direction.DESC, "helpfulCount"));
     * }</pre>
     *
     * @param category 조회할 카테고리
     * @param sort     정렬 조건 (예: Sort.by(DESC, "createdAt"))
     * @return 해당 카테고리 FAQ 목록 (없으면 빈 리스트)
     */
    List<SupportFaq> findByCategory(SupportCategory category, Sort sort);

    /**
     * 전체 FAQ 목록을 생성일 내림차순(최신순)으로 조회한다.
     *
     * <p>관리자 FAQ 관리 화면 또는 "전체" 탭 선택 시 사용한다.</p>
     *
     * @return 전체 FAQ 목록 (최신 등록순)
     */
    List<SupportFaq> findAllByOrderByCreatedAtDesc();

    /**
     * 챗봇이 사용자 메시지와 매칭되는 FAQ 를 찾기 위한 키워드 검색 쿼리.
     *
     * <p>질문 또는 답변 본문에 키워드가 부분 일치(LIKE '%kw%')하는 FAQ 를
     * helpful_count 내림차순으로 반환한다. Pageable 상한(예: Top 3)을 함께 전달하여
     * 최상위 매칭만 챗봇 답변에 포함한다.</p>
     *
     * <p>소문자 LOWER() 비교를 통해 영문 대소문자를 구분하지 않는다.
     * 한글은 원문 그대로 매칭된다(MySQL utf8mb4 collation에 따라 동작).</p>
     *
     * @param keyword  LIKE 패턴에 삽입할 키워드 (호출측에서 소문자화 + trim 수행)
     * @param pageable 결과 크기 제한용 Pageable (예: PageRequest.of(0, 3))
     * @return 매칭 FAQ 목록 (helpful_count 내림차순)
     */
    @Query(
            "SELECT f FROM SupportFaq f " +
                    "WHERE LOWER(f.question) LIKE CONCAT('%', :keyword, '%') " +
                    "   OR LOWER(f.answer)   LIKE CONCAT('%', :keyword, '%') " +
                    "ORDER BY f.helpfulCount DESC, f.createdAt DESC"
    )
    List<SupportFaq> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
