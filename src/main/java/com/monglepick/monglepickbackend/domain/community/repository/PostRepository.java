package com.monglepick.monglepickbackend.domain.community.repository;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 JPA 리포지토리
 *
 * <p>커뮤니티 게시글의 CRUD와 검색 기능을 제공합니다.
 * 임시저장(DRAFT)/게시(PUBLISHED) 상태별 조회를 지원합니다.</p>
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /** 카테고리별 게시글 목록 조회 (상태 무관, 하위 호환용) */
    Page<Post> findByCategory(Post.Category category, Pageable pageable);

    /** 카테고리 + 상태별 게시글 조회 (게시 완료된 글만 필터) */
    Page<Post> findByCategoryAndStatus(Post.Category category, PostStatus status, Pageable pageable);

    /** 상태별 전체 게시글 조회 */
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    /** 사용자별 게시글 목록 조회 (마이페이지용) */
    Page<Post> findByUser_UserId(String userId, Pageable pageable);

    /** 사용자 + 상태별 게시글 조회 (임시저장 목록) */
    Page<Post> findByUserAndStatus(User user, PostStatus status, Pageable pageable);

    /** 상태별 전체 게시글 조회 + User 즉시 로딩 (N+1 방지) */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.status = :status")
    Page<Post> findByStatusWithUser(@Param("status") PostStatus status, Pageable pageable);

    /** 카테고리 + 상태별 게시글 조회 + User 즉시 로딩 (N+1 방지) */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.category = :category AND p.status = :status")
    Page<Post> findByCategoryAndStatusWithUser(@Param("category") Post.Category category, @Param("status") PostStatus status, Pageable pageable);

    /** 조회수 원자적 증가 (단일 UPDATE 쿼리, write lock 최소화) */
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void incrementViewCount(@Param("postId") Long postId);
}
