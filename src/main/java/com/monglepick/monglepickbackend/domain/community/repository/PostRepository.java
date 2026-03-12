package com.monglepick.monglepickbackend.domain.community.repository;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게시글 JPA 리포지토리
 *
 * <p>커뮤니티 게시글의 CRUD와 검색 기능을 제공합니다.</p>
 */
public interface PostRepository extends JpaRepository<Post, Long> {
    /** 카테고리별 게시글 목록 조회 */
    Page<Post> findByCategory(Post.Category category, Pageable pageable);

    /** 사용자별 게시글 목록 조회 (마이페이지용) */
    Page<Post> findByUserId(Long userId, Pageable pageable);
}
