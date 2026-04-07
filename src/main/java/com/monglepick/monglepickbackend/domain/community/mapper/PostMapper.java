package com.monglepick.monglepickbackend.domain.community.mapper;

import com.monglepick.monglepickbackend.domain.community.entity.CommentLike;
import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostComment;
import com.monglepick.monglepickbackend.domain.community.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 커뮤니티 통합 MyBatis Mapper.
 *
 * <p>{@code posts}, {@code post_comment}, {@code post_likes}, {@code comment_likes}
 * 네 테이블의 CRUD + 집계를 통합 담당한다. 이민수 도메인 + admin 검색/집계 쿼리까지 포괄한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/community/PostMapper.xml}</p>
 *
 * <h3>JPA/MyBatis 하이브리드 (§15)</h3>
 * <ul>
 *   <li>Post/PostComment/PostLike/CommentLike {@code @Entity}는 DDL 정의 전용.
 *       데이터 R/W는 이 Mapper로 100% 처리한다.</li>
 *   <li>Post는 {@code String userId} + {@code @Transient String nickname} 구조이며,
 *       목록/상세 조회 시 JOIN users로 nickname을 로드해 Post.nickname 필드에 주입한다.</li>
 *   <li>count 증감({@code view_count}/{@code like_count}/{@code comment_count})은
 *       원자적 DB UPDATE로 race condition을 방지한다.</li>
 * </ul>
 */
@Mapper
public interface PostMapper {

    // ═══ Post 단건 조회 ═══

    /** PK로 게시글 조회 (없으면 null, nickname 미포함) */
    Post findById(@Param("postId") Long postId);

    /** PK로 게시글 + 작성자 닉네임 조회 (JOIN users, 상세 화면용) */
    Post findByIdWithNickname(@Param("postId") Long postId);

    // ═══ Post 목록 조회 (JOIN users) ═══

    /**
     * 상태별 게시글 목록 조회 (닉네임 포함, 페이징, 소프트 삭제 제외).
     *
     * @param status PostStatus enum 이름 ("PUBLISHED" / "DRAFT")
     */
    List<Post> findByStatusWithNickname(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 카테고리 + 상태별 게시글 목록 (닉네임 포함, 페이징) */
    List<Post> findByCategoryAndStatusWithNickname(@Param("category") String category,
                                                    @Param("status") String status,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /** 상태별 총 건수 (소프트 삭제 제외) */
    long countByStatus(@Param("status") String status);

    /** 카테고리 + 상태별 총 건수 (소프트 삭제 제외) */
    long countByCategoryAndStatus(@Param("category") String category,
                                   @Param("status") String status);

    // ═══ 사용자별 Post ═══

    /** 사용자의 모든 게시글 목록 (페이징, 상태 무관 — 관리자 활동 조회용) */
    List<Post> findByUserId(@Param("userId") String userId,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /** 사용자의 모든 게시글 총 건수 (관리자 활동 카운트용) */
    long countByUserId(@Param("userId") String userId);

    /** 사용자 + 상태별 게시글 목록 (임시저장 목록, 마이페이지) */
    List<Post> findByUserIdAndStatus(@Param("userId") String userId,
                                      @Param("status") String status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 사용자 + 상태별 총 건수 */
    long countByUserIdAndStatus(@Param("userId") String userId,
                                 @Param("status") String status);

    // ═══ Post 쓰기 ═══

    /** 게시글 등록 (INSERT) — useGeneratedKeys로 postId 자동 세팅 */
    void insert(Post post);

    /** 게시글 본문 수정 (UPDATE) — title, content, category, status 변경 */
    void update(Post post);

    /** 관리자 상태 변경 (블라인드/복구 등) — isDeleted, deletedAt, isBlinded 반영 */
    void updateAdminStatus(Post post);

    /** 게시글 하드 삭제 (실무에서는 소프트 삭제 권장, 현재는 호환용) */
    void deleteById(@Param("postId") Long postId);

    /** 조회수 원자적 1 증가 (race condition 방지) */
    void incrementViewCount(@Param("postId") Long postId);

    /** 좋아요 수 원자적 증감 (delta: +1 또는 -1, 0 미만 방지) */
    void updateLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    /** 댓글 수 원자적 증감 (delta: +1 또는 -1, 0 미만 방지) */
    void updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    // ═══ Post 관리자 통계 ═══

    /** 지정 기간 내 생성된 게시글 수 (상태 무관, 일일 통계용) */
    long countByCreatedAtBetween(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 지정 상태 + 기간 내 생성된 게시글 수 (PUBLISHED 일별 집계용) */
    long countByStatusAndCreatedAtBetween(@Param("status") String status,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    // ═══ Post 관리자 동적 검색 ═══

    /**
     * 관리자 게시글 동적 검색 (키워드 + 카테고리 + 상태, 페이징, 닉네임 포함).
     *
     * <p>각 파라미터가 null/빈값이면 해당 필터를 건너뛴다.
     * AdminContentService의 findByFilters를 대체한다.</p>
     */
    List<Post> searchAdminPosts(@Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /** 관리자 게시글 동적 검색 총 건수 (검색 조건 동일) */
    long countAdminPosts(@Param("keyword") String keyword,
                          @Param("category") String category,
                          @Param("status") String status);

    // ═══ PostComment ═══

    /** PK로 댓글 조회 (없으면 null) */
    PostComment findCommentById(@Param("postCommentId") Long postCommentId);

    /**
     * 게시글의 유효 댓글 목록 조회 (소프트 삭제 제외, 페이징).
     */
    List<PostComment> findCommentsByPostIdAndIsDeletedFalse(@Param("postId") Long postId,
                                                             @Param("offset") int offset,
                                                             @Param("limit") int limit);

    /** 게시글의 유효 댓글 총 건수 */
    long countCommentsByPostIdAndIsDeletedFalse(@Param("postId") Long postId);

    /** 사용자의 모든 댓글 목록 (페이징, 삭제 포함 — 관리자 활동 조회용) */
    List<PostComment> findCommentsByUserId(@Param("userId") String userId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /** 사용자의 유효 댓글 수 (소프트 삭제 제외 — 관리자 카운트 표시용) */
    long countCommentsByUserIdAndIsDeletedFalse(@Param("userId") String userId);

    /** 댓글 등록 (INSERT) */
    void insertComment(PostComment comment);

    /** 댓글 소프트 삭제 */
    void softDeleteComment(@Param("postCommentId") Long postCommentId);

    /** 부모 댓글 존재 여부 확인 (대댓글 INSERT 시 부모 확인용) */
    boolean existsCommentById(@Param("postCommentId") Long postCommentId);

    /** 댓글 좋아요 수 원자적 증감 */
    void updateCommentLikeCount(@Param("postCommentId") Long postCommentId,
                                 @Param("delta") int delta);

    // ═══ PostLike ═══

    /** PostLike 단건 조회 (없으면 null) */
    PostLike findPostLikeByPostIdAndUserId(@Param("postId") Long postId,
                                            @Param("userId") String userId);

    /** PostLike 존재 여부 */
    boolean existsPostLikeByPostIdAndUserId(@Param("postId") Long postId,
                                             @Param("userId") String userId);

    /** 특정 게시글의 좋아요 수 (실시간 카운트 — 비정규화 값과 별개) */
    long countPostLikeByPostId(@Param("postId") Long postId);

    /** PostLike 생성 */
    void insertPostLike(PostLike postLike);

    /** PostLike 삭제 (hard-delete) */
    void deletePostLikeByPostIdAndUserId(@Param("postId") Long postId,
                                          @Param("userId") String userId);

    // ═══ CommentLike ═══

    /** CommentLike 단건 조회 (없으면 null) */
    CommentLike findCommentLikeByCommentIdAndUserId(@Param("commentId") Long commentId,
                                                     @Param("userId") String userId);

    /** CommentLike 존재 여부 */
    boolean existsCommentLikeByCommentIdAndUserId(@Param("commentId") Long commentId,
                                                    @Param("userId") String userId);

    /** 특정 댓글의 좋아요 수 (실시간 카운트) */
    long countCommentLikeByCommentId(@Param("commentId") Long commentId);

    /** CommentLike 생성 */
    void insertCommentLike(CommentLike commentLike);

    /** CommentLike 삭제 (hard-delete) */
    void deleteCommentLikeByCommentIdAndUserId(@Param("commentId") Long commentId,
                                                 @Param("userId") String userId);
}
