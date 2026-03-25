package com.monglepick.monglepickbackend.domain.community.entity;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseAuditEntity {

    /** 게시글 고유 식별자 (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

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

    /** 게시글 상태 — 임시저장(DRAFT) / 게시됨(PUBLISHED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

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
        NEWS;

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
                        "유효하지 않은 카테고리입니다: '" + value + "'. 허용 값: FREE, DISCUSSION, RECOMMENDATION, NEWS"
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
    public Post(User user, String title, String content, Category category, PostStatus status) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = status != null ? status : PostStatus.PUBLISHED;
        this.viewCount = 0;
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
}
