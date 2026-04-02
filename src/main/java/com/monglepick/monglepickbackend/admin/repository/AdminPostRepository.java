package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 전용 게시글(Post) JPA 리포지토리.
 *
 * <p>관리자 콘텐츠 관리 화면에서 키워드·카테고리·상태 조합 필터 조회가 필요하므로
 * 기존 {@code PostRepository}에 없는 JPQL 쿼리를 별도로 정의한다.</p>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>기존 {@code PostRepository}를 수정하지 않고 Admin 전용 리포지토리를 분리하여
 *       도메인 레이어와 관리자 레이어 간 의존성 방향을 단방향으로 유지한다.</li>
 *   <li>모든 조회 메서드에 {@code JOIN FETCH p.user}를 적용하여 N+1 문제를 방지한다.</li>
 * </ul>
 */
public interface AdminPostRepository extends JpaRepository<Post, Long> {

    /**
     * 키워드·카테고리·상태 조합 필터로 게시글 목록을 최신순 페이징 조회한다.
     *
     * <p>각 파라미터가 null이면 해당 조건을 무시한다 (동적 필터).
     * 키워드는 제목({@code title}) 또는 본문({@code content}) 부분 일치(LIKE)로 검색한다.</p>
     *
     * <h3>파라미터 조합 예시</h3>
     * <ul>
     *   <li>keyword=null, category=null, status=null  → 전체 조회</li>
     *   <li>keyword="영화", category=null, status=null → 제목/본문에 "영화" 포함 게시글</li>
     *   <li>keyword=null, category=FREE, status=null  → FREE 카테고리 전체</li>
     *   <li>keyword=null, category=null, status=PUBLISHED → 게시 완료 상태 전체</li>
     * </ul>
     *
     * @param keyword  검색어 (제목·본문 LIKE, null이면 조건 무시)
     * @param category 카테고리 열거값 (null이면 조건 무시)
     * @param status   게시 상태 열거값 (null이면 조건 무시)
     * @param pageable 페이지 정보 (page, size)
     * @return 조건에 맞는 게시글 페이지 (User 즉시 로딩 포함)
     */
    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.user
            WHERE (:keyword IS NULL
                   OR p.title LIKE %:keyword%
                   OR p.content LIKE %:keyword%)
              AND (:category IS NULL OR p.category = :category)
              AND (:status   IS NULL OR p.status   = :status)
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findByFilters(
            @Param("keyword")  String keyword,
            @Param("category") Post.Category category,
            @Param("status")   PostStatus status,
            Pageable pageable
    );
}
