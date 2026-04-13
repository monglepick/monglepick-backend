package com.monglepick.monglepickbackend.domain.review.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 영화 리뷰 엔티티
 *
 * <p>MySQL reviews 테이블과 매핑됩니다.
 * 사용자가 영화에 대해 작성하는 평점과 리뷰를 저장합니다.</p>
 *
 * <p>한 사용자가 같은 영화에 대해 중복 리뷰를 작성할 수 없습니다.
 * (서비스 레이어에서 검증)</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>PK 필드명: id → reviewId (컬럼명: review_id)</li>
 *   <li>BaseAuditEntity 상속 추가 — created_at/updated_at/created_by/updated_by 자동 관리</li>
 *   <li>수동 createdAt 필드 제거 — BaseTimeEntity에서 상속</li>
 *   <li>@PrePersist onCreate() 메서드 제거 — BaseTimeEntity의 @CreationTimestamp가 처리</li>
 *   <li>엑셀 5번 reviews 정합 — DB 컬럼 {@code content → contents}, {@code spoiler → is_spoiler} 매핑 변경.
 *       Java 필드명은 클라이언트 호환을 위해 유지.</li>
 *   <li>엑셀 5번 reviews 8번 컬럼(영문명 누락) → {@code review_category_code} 신규 추가
 *       (enum {@link ReviewCategoryCode}, 6종 분류).</li>
 * </ul>
 */
@Entity
@Table(
        name = "reviews",
        indexes = {
                @Index(name = "idx_reviews_movie", columnList = "movie_id"),
                @Index(name = "idx_reviews_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseAuditEntity {

    /**
     * 리뷰 고유 식별자 (BIGINT AUTO_INCREMENT PK).
     * 필드명 변경: id → reviewId (엔티티 PK 네이밍 통일)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    /**
     * 리뷰 작성자 ID — users.user_id를 String으로 직접 참조 (JPA/MyBatis 하이브리드 §15.4).
     *
     * <p>users 테이블의 쓰기 소유는 김민규(MyBatis). JPA @ManyToOne 매핑 대신 String FK만 보관한다.
     * 상세/목록 조회 시 작성자 닉네임은 {@link #nickname} 필드로 JOIN 결과를 받는다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 작성자 닉네임 (DB 비영속, JOIN 결과 캐리어).
     *
     * <p>MyBatis ReviewMapper의 JOIN 쿼리(users 테이블과 조인)로 채운다.
     * DB 컬럼이 아니므로 {@code @Transient}로 JPA 영속성에서 제외한다.</p>
     */
    @Transient
    @Setter
    private String nickname;

    /** 리뷰 대상 영화 ID (movies 테이블의 movie_id VARCHAR(50) 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /** 평점 (1.0 ~ 5.0, 0.5 단위) */
    @Column(nullable = false)
    private Double rating;

    /**
     * 리뷰 본문 (TEXT 타입).
     *
     * <p>엑셀 5번 reviews 행82 [4] 컬럼명 = {@code contents} 기준으로 DB 매핑.
     * Java 필드명은 클라이언트 JSON 키 호환을 위해 {@code content}로 유지한다.</p>
     */
    @Column(name = "contents", columnDefinition = "TEXT")
    private String content;

    /** 소프트 삭제 여부 (관리자 콘텐츠 관리: 리뷰 삭제) */
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /** 신고 블라인드 여부 (관리자 콘텐츠 관리: 신고 처리 시 블라인드) */
    @Column(name = "is_blinded", nullable = false)
    private boolean isBlinded = false;

    /**
     * 스포일러 포함 여부 (기본값: false).
     *
     * <p>엑셀 5번 reviews 행82 [6] 컬럼명 = {@code is_spoiler} 기준으로 DB 매핑.
     * Java 필드명은 다른 boolean 필드와의 일관성을 깨지 않기 위해 {@code spoiler}로 유지한다.</p>
     */
    @Column(name = "is_spoiler", nullable = false)
    private boolean spoiler = false;

    /** 좋아요 수 (비정규화, 기본값: 0) */
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    /**
     * 리뷰 작성 출처 — 어떤 기능/경로에서 리뷰를 작성했는지 참조 엔티티 ID를 저장.
     *
     * <p>엑셀 설계 첫 번째 시트 5번 테이블(행80) 기준: "참조값 ID (어디서 리뷰를 적었는지?)"</p>
     *
     * <p>값 형식 예시:</p>
     * <ul>
     *   <li>{@code chat_ses_001} — AI 챗봇 추천 세션</li>
     *   <li>{@code like_31239_001} — 좋아요 목록</li>
     *   <li>{@code p_mov_001} — 인생영화(fav_movie)</li>
     *   <li>{@code cup_mch_005} — 이상형 월드컵 매치 결과</li>
     *   <li>{@code sh_005} — 검색 결과(search_history)</li>
     *   <li>{@code wsh_2345_003} — 위시리스트</li>
     * </ul>
     */
    @Column(name = "review_source", length = 50)
    private String reviewSource;

    /**
     * 리뷰 작성 카테고리 코드 — 어떤 기능 카테고리에서 작성된 리뷰인지 분류.
     *
     * <p>엑셀 5번 reviews 8번 컬럼(영문 헤더 누락) → {@link ReviewCategoryCode} enum 매핑.
     * 6종 분류 중 하나(THEATER_RECEIPT/COURSE/WORLDCUP/WISHLIST/AI_RECOMMEND/PLAYLIST).</p>
     *
     * <p>{@link #reviewSource}와의 차이는 {@link ReviewCategoryCode} javadoc 참조.
     * 클라이언트가 명시적으로 보내지 않으면 {@code null}로 저장되며, 기존 호출처와의 호환을 위해 nullable.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_category_code", length = 30)
    private ReviewCategoryCode reviewCategoryCode;

    /* created_at, updated_at → BaseTimeEntity에서 상속 (수동 createdAt 및 @PrePersist 제거) */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    @Builder
    public Review(String userId, String movieId, Double rating, String content,
                  Boolean spoiler, Integer likeCount,
                  String reviewSource, ReviewCategoryCode reviewCategoryCode) {
        this.userId = userId;
        this.movieId = movieId;
        this.rating = rating;
        this.content = content;
        this.isDeleted = false;
        this.isBlinded = false;
        this.spoiler = spoiler != null ? spoiler : false;
        this.likeCount = likeCount != null ? likeCount : 0;
        this.reviewSource = reviewSource;
        this.reviewCategoryCode = reviewCategoryCode;
    }

    /* @PrePersist onCreate() 제거 — BaseTimeEntity의 @CreationTimestamp가 created_at 자동 설정 */

    /** 리뷰 내용 및 평점 수정 */
    public void update(Double rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    /** 소프트 삭제 처리 (관리자 콘텐츠 관리) */
    public void softDelete() {
        this.isDeleted = true;
    }

    /** 소프트 삭제 복원 (관리자 기능) */
    public void restore() {
        this.isDeleted = false;
    }

    /** 신고 블라인드 처리 (관리자 콘텐츠 관리) */
    public void blind() {
        this.isBlinded = true;
    }

    /** 블라인드 해제 (관리자 기능) */
    public void unblind() {
        this.isBlinded = false;
    }
}
