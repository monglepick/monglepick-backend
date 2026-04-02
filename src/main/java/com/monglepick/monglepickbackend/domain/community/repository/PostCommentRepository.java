package com.monglepick.monglepickbackend.domain.community.repository;

import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게시글 댓글 JPA 리포지토리 — post_comment 테이블 접근 계층.
 *
 * <p>커뮤니티 댓글의 CRUD와 사용자별/게시글별 조회를 제공한다.
 * 소프트 삭제(is_deleted=true)된 댓글은 건수 집계에서 제외하는 메서드를 함께 제공한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserId(String, Pageable)} — 사용자별 댓글 목록 (관리자 활동 조회)</li>
 *   <li>{@link #countByUserIdAndIsDeletedFalse(String)} — 유효 댓글 수 (소프트 삭제 제외)</li>
 * </ul>
 */
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * 사용자 ID로 댓글 목록을 최신순으로 페이징 조회한다.
     *
     * <p>관리자 사용자 상세 화면의 활동 이력 탭에서 사용된다.
     * 소프트 삭제된 댓글도 포함하며, 관리자가 삭제 여부를 확인할 수 있다.</p>
     *
     * @param userId   사용자 ID (VARCHAR(50))
     * @param pageable 페이징 정보 (page, size)
     * @return 댓글 페이지 (최신순 정렬)
     */
    Page<PostComment> findByUserId(String userId, Pageable pageable);

    /**
     * 사용자의 유효 댓글 수를 조회한다 (소프트 삭제 제외).
     *
     * <p>관리자 사용자 상세 화면에서 활동 카운트(commentCount)를 표시할 때 사용한다.
     * is_deleted=false인 댓글만 집계하여 삭제된 댓글은 제외한다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 유효 댓글 건수
     */
    long countByUserIdAndIsDeletedFalse(String userId);

    /**
     * 게시글 ID로 댓글 목록을 페이징 조회한다.
     *
     * <p>소프트 삭제된 댓글(is_deleted=true)은 결과에서 제외한다.
     * PostCommentController의 GET /api/v1/posts/{postId}/comments 에서 사용한다.</p>
     *
     * @param postId   게시글 ID
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 유효 댓글 페이지 (소프트 삭제 제외)
     */
    Page<PostComment> findByPostIdAndIsDeletedFalse(Long postId, Pageable pageable);
}
