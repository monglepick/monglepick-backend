package com.monglepick.monglepickbackend.domain.community.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 커뮤니티 게시글 엔티티
 *
 * <p>MySQL posts 테이블과 매핑됩니다.
 * 사용자가 작성하는 영화 토론, 자유 게시글, 추천 요청 등을 저장합니다.</p>
 *
 * <p>게시글 카테고리:</p>
 * <ul>
 *   <li>FREE: 자유 게시판</li>
 *   <li>DISCUSSION: 영화 토론</li>
 *   <li>RECOMMENDATION: 추천 요청/공유</li>
 *   <li>NEWS: 영화 뉴스/소식</li>
 * </ul>
 *
 * <h3>임시저장 기능 (Downloads POST 파일 적용)</h3>
 * <ul>
 *   <li>DRAFT: 임시저장 상태 (작성자만 조회 가능)</li>
 *   <li>PUBLISHED: 게시 완료 상태 (전체 공개)</li>
 * </ul>
 */
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_category", columnList = "category"),
        @Index(name = "idx_posts_user", columnList = "user_id"),
        @Index(name = "idx_posts_status", columnList = "status"),
        @Index(name = "idx_posts_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseAuditEntity {

    /** 게시글 고유 식별자 (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    /**
     * 작성자 ID — users.user_id를 String으로 직접 참조한다 (JPA/MyBatis 하이브리드 §15.4).
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 @ManyToOne 매핑 대신 String FK만 보관한다.
     * Post 목록/상세 조회 시 작성자 닉네임은 {@link #nickname} 필드로 MyBatis JOIN 결과를 받는다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 작성자 닉네임 (DB 비영속, JOIN 결과 캐리어).
     *
     * <p>MyBatis PostMapper의 JOIN 쿼리(users 테이블과 조인)로 채운다.
     * DB 컬럼이 아니므로 {@code @Transient}로 JPA 영속성 제외한다.
     * 기본 SELECT({@code findById} 등)는 이 값을 null로 남겨두며,
     * 목록/상세 조회 시 JOIN 쿼리 결과에서만 세팅된다.</p>
     */
    @Transient
    @Setter
    private String nickname;

    /** 게시글 제목 (최대 200자) */
    @Column(nullable = false, length = 200)
    private String title;

    /** 게시글 본문 (TEXT 타입) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 게시글 카테고리 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    /** 조회수 (기본값 0) */
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    /** 게시글 상태 — 임시저장(DRAFT) / 게시됨(PUBLISHED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    /** 좋아요 수 비정규화 (목록 조회 성능 최적화, 매번 COUNT 쿼리 방지) */
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    /** 댓글 수 비정규화 (목록 조회 성능 최적화, 매번 COUNT 쿼리 방지) */
    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    /** 소프트 삭제 여부 (REQ_043,045: 유저/관리자 게시글 삭제) */
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /** 소프트 삭제 시각 (30일 후 물리삭제 스케줄링 기준) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 신고 누적 블라인드 여부 (REQ_046: 누적 5회 이상 신고 시 자동 true) */
    @Column(name = "is_blinded", nullable = false)
    private boolean isBlinded = false;

    /**
     * 연결된 플레이리스트 ID (PLAYLIST_SHARE 카테고리 전용, 나머지는 null).
     * playlist.playlist_id를 FK로 참조한다.
     */
    @Column(name = "playlist_id")
    private Long playlistId;

    // ─── PLAYLIST_SHARE 전용 @Transient JOIN 캐리어 ───

    /** 플레이리스트 이름 (JOIN playlist, PLAYLIST_SHARE 조회 시 세팅) */
    @Transient @Setter private String playlistName;
    /** 플레이리스트 설명 (JOIN playlist) */
    @Transient @Setter private String playlistDescription;
    /** 플레이리스트 커버 이미지 URL (JOIN playlist) */
    @Transient @Setter private String playlistCoverImageUrl;
    /** 플레이리스트 좋아요 수 (JOIN playlist) */
    @Transient @Setter private Integer playlistLikeCount;
    /** 플레이리스트 영화 수 (서브쿼리 COUNT) */
    @Transient @Setter private Integer playlistMovieCount;

    /**
     * 게시글 카테고리 열거형.
     *
     * <p>Jackson 역직렬화 시 대소문자를 무관하게 처리한다.
     * 프론트엔드가 "general", "FREE", "Discussion" 등 어떤 형태로 전송해도
     * {@link #fromValue(String)} 팩토리 메서드가 대문자로 정규화하여 매핑한다.</p>
     *
     * <p>프론트엔드 호환 별칭: "general" → {@link #FREE} (자유 게시판 대응)</p>
     */
    public enum Category {
        /** 자유 게시판 (프론트 "general" 별칭 포함) */
        FREE,
        /** 영화 토론 */
        DISCUSSION,
        /** 추천 요청/공유 */
        RECOMMENDATION,
        /** 영화 뉴스/소식 */
        NEWS,
        /** 플레이리스트 공유 — 공개 플레이리스트를 커뮤니티에 공유 */
        PLAYLIST_SHARE;

        /**
         * JSON 문자열 → Category 변환 팩토리 메서드 (대소문자 무관).
         *
         * <p>Jackson이 요청 본문의 category 필드를 역직렬화할 때 호출된다.
         * 프론트엔드 레거시 값 "general"은 FREE로 매핑하여 하위 호환성을 유지한다.</p>
         *
         * @param value JSON에서 전달된 카테고리 문자열 (예: "general", "FREE", "discussion")
         * @return 매핑된 Category 열거 상수
         * @throws IllegalArgumentException 알 수 없는 카테고리 값인 경우
         */
        @JsonCreator
        public static Category fromValue(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("카테고리 값이 비어 있습니다.");
            }
            // 대문자로 정규화하여 비교 (소문자/혼합 대소문자 모두 허용)
            String normalized = value.trim().toUpperCase();

            // 프론트엔드 레거시 별칭 처리: "general" → FREE (자유 게시판)
            if ("GENERAL".equals(normalized)) {
                return FREE;
            }

            try {
                return Category.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "유효하지 않은 카테고리입니다: '" + value + "'. 허용 값: FREE, DISCUSSION, RECOMMENDATION, NEWS, PLAYLIST_SHARE"
                );
            }
        }

        /**
         * Category → JSON 직렬화 시 소문자로 반환.
         *
         * <p>응답 JSON에서 "FREE" 대신 "free"로 내려가므로
         * 프론트엔드 컨벤션(소문자 enum)과 일치한다.</p>
         *
         * @return 소문자 카테고리 문자열
         */
        @JsonValue
        public String toValue() {
            return this.name().toLowerCase();
        }
    }

    @Builder
    public Post(String userId, String title, String content, Category category, PostStatus status, Long playlistId) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = status != null ? status : PostStatus.PUBLISHED;
        this.playlistId = playlistId;
        this.viewCount = 0;
        this.likeCount = 0;
        this.commentCount = 0;
        this.isDeleted = false;
        this.isBlinded = false;
    }

    /** 게시글 내용 수정 */
    public void update(String title, String content, Category category) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    /** 조회수 1 증가 */
    public void incrementViewCount() {
        this.viewCount++;
    }

    /** 임시저장 → 게시글 업로드 */
    public void publish() {
        this.status = PostStatus.PUBLISHED;
    }

    /** 소프트 삭제 처리 (REQ_043: 유저 삭제, REQ_045: 관리자 삭제) */
    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /** 소프트 삭제 복원 (관리자 기능) */
    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }

    /**
     * 첨부 이미지 URL 목록 (콤마로 구분하여 저장)
     * 예: "http://localhost:8080/images/userId/a.jpg,http://localhost:8080/images/userId/b.jpg"
     *
     * 【추후 S3 전환 시】
     * URL만 바뀌므로 DB 구조 변경 불필요
     * 예: "https://objectstorage.kakaocloud.com/bucket/userId/a.jpg,..."
     */
    @Column(name = "image_urls", columnDefinition = "TEXT")
    @Setter
    private String imageUrls;

    /** 신고 블라인드 처리 (REQ_046: 누적 5회 이상 신고 시 호출) */
    public void blind() {
        this.isBlinded = true;
    }

    /** 블라인드 해제 (관리자 기능) */
    public void unblind() {
        this.isBlinded = false;
    }

    /** 좋아요 수 증가 (PostLike 생성 시 호출) */
    public void incrementLikeCount() {
        this.likeCount++;
    }

    /** 좋아요 수 감소 (PostLike 삭제 시 호출) */
    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    /** 댓글 수 증가 (PostComment 생성 시 호출) */
    public void incrementCommentCount() {
        this.commentCount++;
    }

    /** 댓글 수 감소 (PostComment 삭제 시 호출) */
    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }
}
