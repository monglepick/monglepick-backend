package com.monglepick.monglepickbackend.domain.community.mapper;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 커뮤니티 게시글/댓글 MyBatis Mapper.
 *
 * <p>posts, post_comment 테이블의 CRUD를 담당한다.
 * 카테고리별 조회, 임시저장(DRAFT), 조회수 증가, 좋아요/댓글 수 비정규화를 지원한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/community/PostMapper.xml}</p>
 */
@Mapper
public interface PostMapper {

    // ── Post CRUD ──

    /** PK로 게시글 조회 */
    Post findById(@Param("postId") Long postId);

    /** 게시글 + 작성자 정보 함께 조회 (JOIN users) */
    Post findByIdWithUser(@Param("postId") Long postId);

    /** 상태별 게시글 목록 + 작성자 (페이징) */
    List<Post> findByStatusWithUser(@Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /** 카테고리 + 상태별 게시글 목록 + 작성자 (페이징) */
    List<Post> findByCategoryAndStatusWithUser(@Param("category") String category,
                                               @Param("status") String status,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /** 상태별 총 건수 (페이징 count) */
    long countByStatus(@Param("status") String status);

    /** 카테고리 + 상태별 총 건수 (페이징 count) */
    long countByCategoryAndStatus(@Param("category") String category,
                                  @Param("status") String status);

    /** 사용자 + 상태별 게시글 목록 (마이페이지, 임시저장 목록) */
    List<Post> findByUserIdAndStatus(@Param("userId") String userId,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /** 게시글 등록 (INSERT) */
    void insert(Post post);

    /** 게시글 수정 (UPDATE) */
    void update(Post post);

    /** 게시글 삭제 */
    void deleteById(@Param("postId") Long postId);

    /** 조회수 원자적 1 증가 */
    void incrementViewCount(@Param("postId") Long postId);

    /** 좋아요 수 원자적 증가/감소 */
    void updateLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    /** 댓글 수 원자적 증가/감소 */
    void updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    // ── PostComment CRUD ──

    /** 게시글의 댓글 목록 조회 (최신순) */
    List<PostComment> findCommentsByPostId(@Param("postId") Long postId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 댓글 등록 */
    void insertComment(PostComment comment);

    /** 댓글 삭제 (소프트) */
    void softDeleteComment(@Param("postCommentId") Long postCommentId);
}
