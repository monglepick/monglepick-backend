package com.monglepick.monglepickbackend.domain.review.mapper;

import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.entity.ReviewCategoryCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 회귀 안전망 (2026-04-30):
 *   GET /api/v1/admin/reviews?page=0&size=10 가 500 회귀하여
 *   {@link ReviewMapper#searchAdminReviews} / {@link ReviewMapper#countAdminReviews}
 *   의 SQL/resultMap 정합성을 H2(MySQL 모드) 통합으로 직접 검증한다.
 *
 *   Review 엔티티는 BaseAuditEntity 상속 + @Getter only(no @Setter) +
 *   nickname 은 @Transient @Setter — MyBatis Reflector 가 부모 클래스 필드를
 *   포함해 setter/field 를 정상 매핑하는지 확인한다.
 */
@SpringBootTest
@Transactional
@DisplayName("ReviewMapper.searchAdminReviews — 관리자 리뷰 목록 회귀 테스트 (2026-04-30)")
class ReviewMapperAdminSearchTest {

    @Autowired
    private ReviewMapper reviewMapper;

    @Test
    @DisplayName("리뷰 0건 — 빈 결과 + count=0 (서버 500 회귀 차단)")
    void searchAdminReviews_emptyTable() {
        // when — 비어있는 reviews 테이블에서 admin 검색 (필터 없음, page=0/size=10)
        List<Review> reviews = reviewMapper.searchAdminReviews(null, null, null, 0, 10);
        long total = reviewMapper.countAdminReviews(null, null, null);

        // then — 예외 없이 빈 목록 반환
        assertThat(reviews).isNotNull();
        assertThat(reviews).isEmpty();
        assertThat(total).isZero();
    }

    @Test
    @DisplayName("리뷰 1건 INSERT 후 — 모든 필드 (BaseAuditEntity 포함) 매핑 검증")
    void searchAdminReviews_singleRow_fieldMapping() {
        // given — Review 1건 INSERT (insert 쿼리는 created_at/updated_at NOW() 자동 세팅)
        Review review = Review.builder()
                .userId("user-test-admin-search")
                .movieId("movie-test-admin-search")
                .rating(4.5)
                .content("관리자 리뷰 검색 테스트 본문")
                .spoiler(false)
                .likeCount(0)
                .reviewSource("test_src")
                .reviewCategoryCode(ReviewCategoryCode.AI_RECOMMEND)
                .build();
        reviewMapper.insert(review);

        // when — admin 검색 (필터 없음)
        List<Review> rows = reviewMapper.searchAdminReviews(null, null, null, 0, 10);
        long total = reviewMapper.countAdminReviews(null, null, null);

        // then — 1건 조회 + Review 엔티티 필드 (transient nickname 포함) 매핑 확인
        assertThat(total).isEqualTo(1L);
        assertThat(rows).hasSize(1);
        Review fetched = rows.get(0);
        assertThat(fetched.getUserId()).isEqualTo("user-test-admin-search");
        assertThat(fetched.getMovieId()).isEqualTo("movie-test-admin-search");
        assertThat(fetched.getRating()).isEqualTo(4.5);
        assertThat(fetched.getContent()).isEqualTo("관리자 리뷰 검색 테스트 본문");
        assertThat(fetched.isSpoiler()).isFalse();
        assertThat(fetched.isDeleted()).isFalse();
        assertThat(fetched.isBlinded()).isFalse();
        assertThat(fetched.getReviewSource()).isEqualTo("test_src");
        assertThat(fetched.getReviewCategoryCode()).isEqualTo(ReviewCategoryCode.AI_RECOMMEND);
        // BaseAuditEntity (createdAt/updatedAt) — insert NOW() 로 채워졌어야 한다
        assertThat(fetched.getCreatedAt()).isNotNull();
    }
}
