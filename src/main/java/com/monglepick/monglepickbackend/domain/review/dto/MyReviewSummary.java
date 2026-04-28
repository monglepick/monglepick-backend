package com.monglepick.monglepickbackend.domain.review.dto;

import com.monglepick.monglepickbackend.domain.review.entity.Review;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 내 리뷰 요약 응답 DTO — 고객센터 AI 봇 진단용 (v4 신규, 2026-04-28).
 *
 * <p>고객센터 지원 봇(support_assistant v4)이 "리뷰 작성했는데 포인트 안 들어와요" 류의
 * 발화를 진단할 때 {@code GET /api/v1/users/me/reviews} 를 통해 조회한다.</p>
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>JWT 강제 주입 — 쿼리 파라미터로 userId 를 받지 않는다 (BOLA 방지).</li>
 *   <li>{@code contentPreview} — 리뷰 본문을 100자로 잘라 반환한다.</li>
 *   <li>{@code pointAwarded / pointAwardedAt} — 리워드 적립 이력과 조인. 이력이 없으면 null.</li>
 *   <li>시각 필드는 KST(Asia/Seoul) OffsetDateTime 으로 반환한다.</li>
 * </ul>
 *
 * @param reviewId        리뷰 PK (reviews.review_id)
 * @param movieId         영화 ID (reviews.movie_id, VARCHAR(50))
 * @param movieTitle      영화 제목 (movies.title, JOIN 결과)
 * @param rating          평점 (1.0~5.0, 0.5 단위)
 * @param contentPreview  리뷰 본문 앞 100자 (null 이면 null)
 * @param createdAt       리뷰 작성 시각 (KST OffsetDateTime)
 * @param pointAwarded    포인트 적립 여부 (points_history 에 REVIEW_CREATE 이력 존재 시 true, 없으면 null)
 * @param pointAwardedAt  포인트 적립 시각 (적립 이력이 없으면 null, KST OffsetDateTime)
 */
public record MyReviewSummary(
        Long reviewId,
        String movieId,
        String movieTitle,
        Double rating,
        String contentPreview,
        OffsetDateTime createdAt,
        Boolean pointAwarded,
        OffsetDateTime pointAwardedAt
) {

    /** 내용 미리보기 최대 길이 */
    private static final int PREVIEW_MAX_LENGTH = 100;

    /** KST 타임존 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * Review 엔티티 + 포인트 적립 시각으로 DTO를 생성한다.
     *
     * <p>포인트 이력 조인은 서비스 레이어에서 별도 쿼리 + Map 매핑 패턴으로 처리하며,
     * 이 팩토리 메서드는 이미 조회된 값을 받아 DTO 로 조립한다.</p>
     *
     * @param review          Review 엔티티 (movieTitle 은 @Transient 필드로 별도 세팅됨)
     * @param movieTitle      영화 제목 (JOIN 으로 조회된 값, null 허용)
     * @param pointAwardedAt  포인트 적립 시각 (없으면 null)
     * @return MyReviewSummary DTO
     */
    public static MyReviewSummary of(Review review, String movieTitle, LocalDateTime pointAwardedAt) {
        // 본문 100자 미리보기
        String preview = null;
        if (review.getContent() != null && !review.getContent().isBlank()) {
            String raw = review.getContent();
            preview = raw.length() > PREVIEW_MAX_LENGTH
                    ? raw.substring(0, PREVIEW_MAX_LENGTH)
                    : raw;
        }

        // createdAt → KST OffsetDateTime 변환
        OffsetDateTime createdAtKst = review.getCreatedAt() != null
                ? review.getCreatedAt().atZone(KST).toOffsetDateTime()
                : null;

        // pointAwardedAt → KST OffsetDateTime 변환 (null 허용)
        OffsetDateTime awardedAtKst = pointAwardedAt != null
                ? pointAwardedAt.atZone(KST).toOffsetDateTime()
                : null;

        // pointAwarded: 이력 존재 여부. 이력이 없으면 null (미확인)
        Boolean awarded = (awardedAtKst != null) ? Boolean.TRUE : null;

        return new MyReviewSummary(
                review.getReviewId(),
                review.getMovieId(),
                movieTitle,
                review.getRating(),
                preview,
                createdAtKst,
                awarded,
                awardedAtKst
        );
    }
}
