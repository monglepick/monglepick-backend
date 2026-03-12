package com.monglepick.monglepickbackend.domain.community.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
 */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {


    /** 게시글 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 작성자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    /** 게시글 작성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 게시글 수정 시각 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 게시글 카테고리 열거형
     */
    public enum Category {
        /** 자유 게시판 */
        FREE,
        /** 영화 토론 */
        DISCUSSION,
        /** 추천 요청/공유 */
        RECOMMENDATION,
        /** 영화 뉴스/소식 */
        NEWS
    }

    @Builder
    public Post(User user, String title, String content, Category category) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.category = category;
        this.viewCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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
}
