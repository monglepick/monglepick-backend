package com.monglepick.monglepickbackend.domain.review.repository;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 리뷰 JPA 리포지토리
 *
 * <p>영화 리뷰의 CRUD 기능을 제공합니다.
 * 중복 리뷰 방지를 위한 존재 여부 확인 메서드를 포함합니다.</p>
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {
    /** 특정 영화의 리뷰 목록 조회 */
    List<Review> findByMovieId(Long movieId);

    /** 사용자별 리뷰 목록 조회 (마이페이지용) */
    Page<Review> findByUserId(Long userId, Pageable pageable);

    /** 중복 리뷰 존재 여부 확인 (같은 사용자가 같은 영화에 2개 이상 작성 방지) */
    boolean existsByUserIdAndMovieId(Long userId, Long movieId);
}
