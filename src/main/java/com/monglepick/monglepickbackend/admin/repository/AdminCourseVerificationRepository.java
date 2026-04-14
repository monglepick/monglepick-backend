package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 관리자 전용 도장깨기 리뷰 인증 리포지토리 — 2026-04-14 신규.
 *
 * <p>관리자 페이지 "AI 운영 → 리뷰 인증" 탭에서 AI 에이전트가 판정한 리뷰 기반 시청 인증
 * 기록({@link CourseVerification} 중 {@code verificationType=REVIEW})을 복합 필터로 페이징
 * 조회하기 위한 쿼리 전용 리포지토리.</p>
 *
 * <h3>분리 이유</h3>
 * <p>기존 도메인 측 {@code CourseVerificationRepository}(있다면)는 사용자 자신의 인증 진행률
 * 조회에 특화되어 있다. 관리자 운영은 "모든 사용자"를 대상으로 status/신뢰도/기간/사용자/코스
 * 복합 조건 검색이 필요하므로 {@link com.monglepick.monglepickbackend.admin.repository.AdminQuizRepository}
 * 와 동일한 패턴으로 admin 패키지에 분리한다.</p>
 *
 * <h3>필터 규칙</h3>
 * <ul>
 *   <li>모든 파라미터는 {@code null} 허용 — null 이면 해당 조건 무시 ({@code :param IS NULL} 패턴)</li>
 *   <li>{@code verificationType} 은 파라미터가 아닌 WHERE 절에 리터럴 "REVIEW" 로 고정</li>
 *   <li>{@code reviewStatus}: 정확 일치 (PENDING/AUTO_VERIFIED/NEEDS_REVIEW/AUTO_REJECTED/ADMIN_APPROVED/ADMIN_REJECTED)</li>
 *   <li>{@code minConfidence}: {@code aiConfidence >= :minConfidence} — null 인 aiConfidence 는 이 조건이 주어지면 자동 제외</li>
 *   <li>{@code userId / courseId}: 부분 일치 (대소문자 무시) — 운영 검색 편의</li>
 *   <li>{@code fromDate / toDate}: {@code createdAt} 범위 (리뷰 접수 시각 기준)</li>
 * </ul>
 *
 * <h3>정렬</h3>
 * <p>JPQL 에 {@code ORDER BY createdAt DESC} 하드코딩. Pageable sort 파라미터는 무시된다.</p>
 */
public interface AdminCourseVerificationRepository extends JpaRepository<CourseVerification, Long> {

    /**
     * 리뷰 인증 복합 필터 검색.
     *
     * @param reviewStatus  리뷰 인증 세부 상태 (nullable)
     * @param minConfidence 최소 aiConfidence (nullable)
     * @param userId        사용자 ID 부분 일치 (nullable)
     * @param courseId      코스 ID 부분 일치 (nullable)
     * @param fromDate      createdAt 시작 inclusive (nullable)
     * @param toDate        createdAt 종료 exclusive (nullable — 자정 경계 누락 방지 위해 서비스 레이어에서 +1day)
     * @param pageable      페이지 정보
     * @return 필터링된 리뷰 인증 페이지 (createdAt DESC)
     */
    @Query(
        "SELECT v FROM CourseVerification v WHERE " +
        "v.verificationType = 'REVIEW' AND " +
        "(:reviewStatus  IS NULL OR v.reviewStatus = :reviewStatus) AND " +
        "(:minConfidence IS NULL OR (v.aiConfidence IS NOT NULL AND v.aiConfidence >= :minConfidence)) AND " +
        "(:userId        IS NULL OR LOWER(v.userId)   LIKE LOWER(CONCAT('%', :userId, '%'))) AND " +
        "(:courseId      IS NULL OR LOWER(v.courseId) LIKE LOWER(CONCAT('%', :courseId, '%'))) AND " +
        "(:fromDate      IS NULL OR v.createdAt >= :fromDate) AND " +
        "(:toDate        IS NULL OR v.createdAt <  :toDate) " +
        "ORDER BY v.createdAt DESC"
    )
    Page<CourseVerification> searchReviewVerifications(
            @Param("reviewStatus") String reviewStatus,
            @Param("minConfidence") Float minConfidence,
            @Param("userId") String userId,
            @Param("courseId") String courseId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    /**
     * 리뷰 인증 세부 상태별 건수 — 관리자 화면 상단 KPI 카드용.
     *
     * @param reviewStatus 리뷰 인증 상태 문자열
     * @return 해당 상태의 REVIEW 타입 건수
     */
    @Query(
        "SELECT COUNT(v) FROM CourseVerification v WHERE " +
        "v.verificationType = 'REVIEW' AND v.reviewStatus = :reviewStatus"
    )
    long countReviewByStatus(@Param("reviewStatus") String reviewStatus);

    /**
     * 인증 기록에 연결된 CourseReview 본문을 조회한다 (상세 조회 보조).
     *
     * <p>상세 API 에서 {@code CourseVerification} + 영화 + 리뷰 본문 3 개 조각을 모아 내려야 하는데,
     * CourseReview 는 (course_id, movie_id, user_id) 복합 UNIQUE 로 단일 행이 보장된다.
     * 본 쿼리는 연결된 CourseReview.reviewText 하나만 문자열로 꺼내오는 경량 쿼리다.</p>
     *
     * <p>일부 레거시 인증 기록에는 course_review 레코드가 없을 수 있으므로 {@link java.util.Optional}
     * 대신 nullable 문자열로 반환한다 — 관리자 UI 는 "(리뷰 본문 없음)" 안내를 표시한다.</p>
     *
     * @param userId   사용자 ID
     * @param courseId 코스 ID
     * @param movieId  영화 ID
     * @return 리뷰 본문 문자열 (미존재 시 null)
     */
    @Query(
        "SELECT cr.reviewText FROM CourseReview cr WHERE " +
        "cr.userId = :userId AND cr.courseId = :courseId AND cr.movieId = :movieId"
    )
    String findCourseReviewText(
            @Param("userId") String userId,
            @Param("courseId") String courseId,
            @Param("movieId") String movieId
    );
}
